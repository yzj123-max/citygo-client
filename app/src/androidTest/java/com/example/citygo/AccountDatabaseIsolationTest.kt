package com.example.citygo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import com.example.citygo.skeleton.data.datastore.AccountStorage
import com.example.citygo.skeleton.data.local.app.AppDatabase
import com.example.citygo.skeleton.data.model.DayPlan
import com.example.citygo.skeleton.data.model.Place
import com.example.citygo.skeleton.data.model.PlaceType
import com.example.citygo.skeleton.data.model.TravelPlan
import com.example.citygo.skeleton.data.model.chat.ChatMessage
import com.example.citygo.skeleton.data.repository.LocalRepository
import com.example.citygo.skeleton.data.repository.chat.RoomChatHistoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountDatabaseIsolationTest {
    @Test fun planAndChatStayWithTheirAccountAndSurviveReopening() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // 每次独立测试文件；不接触真实账号及无归属的旧共享库。
        val suffix = System.nanoTime().toString()
        val aKey = AccountStorage.keyFor("test-a-$suffix")
        val bKey = AccountStorage.keyFor("test-b-$suffix")
        val a = AppDatabase.get(context, aKey)
        val b = AppDatabase.get(context, bKey)
        try {
            val place = Place(id = 1, name = "A 的景点", placeType = PlaceType.SPOT, dayIndex = 1,
                lat = 30.0, lng = 104.0, intro = "", petFriendly = false)
            val plan = TravelPlan("成都", 1, listOf(DayPlan(1, listOf(place))), listOf(place))
            LocalRepository.create(a).saveTravelPlan(plan)
            val chatA = RoomChatHistoryRepository.create(a)
            chatA.replaceMessages(1, listOf(ChatMessage(1, "A 的对话", true, 1)))
            assertNull(LocalRepository.create(b).observeCurrentTravelPlan().first())
            assertTrue(RoomChatHistoryRepository.create(b).loadMessages(1).isEmpty())
            assertEquals("成都", LocalRepository.create(a).observeCurrentTravelPlan().first()?.destination)
            assertEquals("A 的对话", chatA.loadMessages(1).single().content)
            // 清理 B 不应影响 A。
            LocalRepository.create(b).clearTravelPlan()
            RoomChatHistoryRepository.create(b).replaceMessages(1, emptyList())
            assertEquals(1, chatA.loadMessages(1).size)
            a.close()
            val reopened = Room.databaseBuilder(context, AppDatabase::class.java,
                AccountStorage.databaseName(aKey)).build()
            try {
                assertEquals("成都", LocalRepository.create(reopened).observeCurrentTravelPlan().first()?.destination)
                assertEquals("A 的对话", RoomChatHistoryRepository.create(reopened).loadMessages(1).single().content)
            } finally {
                reopened.close()
            }
        } finally {
            a.close()
            b.close()
            context.deleteDatabase(AccountStorage.databaseName(aKey))
            context.deleteDatabase(AccountStorage.databaseName(bKey))
        }
    }
}
