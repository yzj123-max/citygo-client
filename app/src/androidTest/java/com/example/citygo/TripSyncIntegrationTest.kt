package com.example.citygo

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.citygo.skeleton.data.datastore.*
import com.example.citygo.skeleton.data.local.app.AppDatabase
import com.example.citygo.skeleton.data.model.*
import com.example.citygo.skeleton.data.remote.auth.AuthTokenStore
import com.example.citygo.skeleton.data.sync.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class TripSyncIntegrationTest {
    private fun plan(name: String): TravelPlan {
        val place = Place(1, name, PlaceType.SPOT, 1, 30.0, 104.0, "", false)
        return TravelPlan("成都",1,listOf(DayPlan(1,listOf(place))),listOf(place))
    }
    private suspend fun until(check: suspend () -> Boolean) = withTimeout(15_000) {
        while (!check()) delay(20)
    }

    private suspend fun fixture(block: suspend (AppDatabase, SessionSnapshot, FakeCloud) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AuthTokenStore.init(context)
        AccountSession.update(AccountEntity("sync-test-${System.nanoTime()}","test-token"))
        val session = AccountSession.snapshot()
        val db = AppDatabase.get(context, session.storageKey)
        try { block(db,session,FakeCloud()) }
        finally {
            db.close()
            context.deleteDatabase(AccountStorage.databaseName(session.storageKey))
            AccountSession.clear(session)
        }
    }

    @Test fun editingDuringUploadDoesNotDisappearAfterOldAcknowledgement() = runBlocking {
        fixture { db,session,cloud ->
            val parent = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                cloud.gate = CompletableDeferred()
                val sync = TripSyncCoordinator(db,cloud,session); sync.start(parent)
                sync.repository.startNewTravelPlan(plan("第一版"))
                until { cloud.requests.isNotEmpty() }
                sync.repository.saveTravelPlan(plan("上传期间修改"))
                cloud.gate!!.complete(Unit)
                until { db.tripSyncDao().all().singleOrNull()?.let { !it.dirty && it.pendingRequestJson == null } == true }
                val remote = cloud.current()!!
                assertEquals("上传期间修改",remote.document.plan!!.allPlaceList.single().name)
                assertEquals("上传期间修改",sync.repository.observeCurrentTravelPlan().first()!!.allPlaceList.single().name)
            } finally { parent.cancel(); parent.coroutineContext[Job]!!.join() }
        }
    }

    @Test fun restartingAfterLostAcknowledgementReusesPersistedMutation() = runBlocking {
        fixture { db,session,cloud ->
            var parent = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                cloud.loseAck = true
                var sync = TripSyncCoordinator(db,cloud,session); sync.start(parent)
                sync.repository.startNewTravelPlan(plan("待重试"))
                until { sync.state.value.message.startsWith("暂未同步") }
                val pending = db.tripSyncDao().all().single().pendingRequestJson
                assertNotNull(pending)
                parent.cancel(); parent.coroutineContext[Job]!!.join()
                parent = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                sync = TripSyncCoordinator(db,cloud,session); sync.start(parent)
                until { sync.state.value.message == "已与云端同步" }
                assertEquals(1,cloud.current()!!.version)
                assertEquals(cloud.requests.first().mutationId,cloud.requests.last().mutationId)
                assertNull(db.tripSyncDao().all().single().pendingRequestJson)
            } finally { parent.cancel(); parent.coroutineContext[Job]!!.join() }
        }
    }

    @Test fun conflictResolutionKeepsRemoteAndLocalAsSeparateTrips() = runBlocking {
        fixture { db,session,cloud ->
            val parent = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val sync = TripSyncCoordinator(db,cloud,session); sync.start(parent)
                sync.repository.startNewTravelPlan(plan("原始安排"))
                until { sync.state.value.message == "已与云端同步" && sync.state.value.activeTripId != null }
                val original = cloud.current()!!
                cloud.advance(original.copy(version=original.version+1,document=TripDocument(plan("另一设备"))))
                sync.repository.saveTravelPlan(plan("本地修改"))
                until { sync.state.value.conflicts.isNotEmpty() }
                sync.resolveConflict(original.tripId,false)
                until { sync.state.value.message == "已与云端同步" && sync.state.value.activeTripId != original.tripId }
                assertEquals("另一设备",cloud.get(original.tripId).document.plan!!.allPlaceList.single().name)
                assertEquals("本地修改",cloud.current()!!.document.plan!!.allPlaceList.single().name)
                assertEquals(2,cloud.list().size)
            } finally { parent.cancel(); parent.coroutineContext[Job]!!.join() }
        }
    }

    @Test fun migrationFromV8PreservesPlanAndChat() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val key = AccountStorage.keyFor("migration-${System.nanoTime()}")
        val name = AccountStorage.databaseName(key)
        val db = AppDatabase.get(context,key)
        val local = com.example.citygo.skeleton.data.repository.LocalRepository.create(db)
        local.saveTravelPlan(plan("升级前景点"))
        val chat = com.example.citygo.skeleton.data.repository.chat.RoomChatHistoryRepository.create(db)
        chat.replaceMessages(1,listOf(com.example.citygo.skeleton.data.model.chat.ChatMessage(1,"升级前对话",true,1)))
        db.close()
        // 仅随机测试数据库：移除 v9 新表，模拟真实的 v8 schema 后再执行迁移。
        android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path,null,0).use {
            it.execSQL("DROP TABLE trip_sync_state")
            it.execSQL("DROP TABLE trip_sync_active")
            it.version = 8
        }
        val reopened = Room.databaseBuilder(context,AppDatabase::class.java,name)
            .addMigrations(AppDatabase.MIGRATION_8_9).build()
        try {
            assertEquals("升级前景点",com.example.citygo.skeleton.data.repository.LocalRepository.create(reopened)
                .observeCurrentTravelPlan().first()!!.allPlaceList.single().name)
            assertEquals("升级前对话",com.example.citygo.skeleton.data.repository.chat.RoomChatHistoryRepository.create(reopened)
                .loadMessages(1).single().content)
            assertTrue(reopened.tripSyncDao().all().isEmpty())
        } finally { reopened.close(); context.deleteDatabase(name) }
    }

    private class FakeCloud : TripCloud {
        private val mutex = Mutex()
        private val docs = mutableMapOf<String,CloudTrip>()
        private val receipts = mutableMapOf<String,CloudTrip>()
        private var active: String? = null
        val requests = java.util.concurrent.CopyOnWriteArrayList<TripWrite>()
        @Volatile var gate: CompletableDeferred<Unit>? = null
        @Volatile var loseAck = false
        override suspend fun list() = mutex.withLock { docs.values.map { TripSummary(it.tripId,it.version,it.title,it.updatedAt) } }
        override suspend fun current() = mutex.withLock { active?.let { docs[it] } }
        override suspend fun get(id: String) = mutex.withLock { checkNotNull(docs[id]) }
        override suspend fun activate(id: String) { mutex.withLock { check(docs.containsKey(id)); active=id } }
        suspend fun advance(doc: CloudTrip) { mutex.withLock { docs[doc.tripId]=doc } }
        override suspend fun save(id: String,write: TripWrite,serializedRequest: String?): CloudTrip {
            requests.add(write)
            gate?.await()
            return mutex.withLock {
                receipts[write.mutationId]?.let { return@withLock it }
                val old = docs[id]
                if ((old?.version ?: 0) != write.baseVersion) throw TripConflictException(checkNotNull(old))
                val next = CloudTrip(id,write.baseVersion+1,write.title,write.document,System.currentTimeMillis())
                docs[id]=next;receipts[write.mutationId]=next
                if (loseAck) { loseAck=false;throw IOException("模拟已提交但响应丢失") }
                next
            }
        }
    }
}
