package com.example.citygo.skeleton.data.sync

import androidx.room.withTransaction
import com.example.citygo.skeleton.data.datastore.AccountSession
import com.example.citygo.skeleton.data.datastore.SessionSnapshot
import com.example.citygo.skeleton.data.local.app.*
import com.example.citygo.skeleton.data.local.chat.toChatMessage
import com.example.citygo.skeleton.data.model.AiPlanOptions
import com.example.citygo.skeleton.data.model.TravelPlan
import com.example.citygo.skeleton.data.model.chat.ChatMessage
import com.example.citygo.skeleton.data.repository.LocalRepository
import com.example.citygo.skeleton.data.repository.Repository
import com.example.citygo.skeleton.data.repository.chat.ChatHistoryRepository
import com.example.citygo.skeleton.data.repository.chat.RoomChatHistoryRepository
import com.google.gson.GsonBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Room 是编辑日志；网络期间不持有数据库锁。任何失败都不删除本地草稿或待确认请求。 */
class TripSyncCoordinator(
    private val db: AppDatabase,
    private val cloud: TripCloud,
    private val session: SessionSnapshot,
) {
    private val gson = GsonBuilder().serializeNulls().create()
    private val dao = db.tripSyncDao()
    private val local = LocalRepository.create(db)
    private val chat = RoomChatHistoryRepository.create(db)
    private val syncMutex = Mutex()
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private var worker: Job? = null
    private var scope: CoroutineScope? = null
    private var remoteList = emptyList<TripSummary>()
    @Volatile private var editingTripId: String? = null
    @Volatile var canApplyRemote: () -> Boolean = { true }
    private val _state = MutableStateFlow(TripSyncUiState())
    val state: StateFlow<TripSyncUiState> = _state.asStateFlow()
    val activeDocument: Flow<TripDocument?> = dao.observeActive().map { it?.let(::document) }
    val activeTripId: Flow<String?> = dao.observeActive().map { it?.tripId }.distinctUntilChanged()

    val repository: Repository = object : Repository by local {
        override suspend fun saveTravelPlan(plan: TravelPlan) = edit { copy(plan = plan) }
        override suspend fun clearTravelPlan() = edit { copy(plan = null) }
        override suspend fun startNewTravelPlan(plan: TravelPlan, options: AiPlanOptions?) {
            startNew(plan, options)
        }
    }
    val chatRepository: ChatHistoryRepository = object : ChatHistoryRepository by chat {
        override suspend fun replaceMessages(planId: Long, messages: List<ChatMessage>) {
            require(planId == AppDatabase.CURRENT_PLAN_ID)
            edit { copy(messages = messages) }
        }
    }

    private fun checkSession() {
        if (!AccountSession.isCurrent(session)) throw CancellationException("账号已切换")
    }
    private fun document(row: TripSyncEntity): TripDocument = gson.fromJson(row.documentJson, TripDocument::class.java)
    private suspend fun activeRow(): TripSyncEntity? = dao.activeId()?.let { dao.get(it) }
    private suspend fun materialize(doc: TripDocument) {
        if (doc.plan == null) local.clearTravelPlan() else local.saveTravelPlan(doc.plan)
        chat.replaceMessages(AppDatabase.CURRENT_PLAN_ID, doc.messages)
    }
    private fun newRow(doc: TripDocument, title: String? = null): TripSyncEntity = TripSyncEntity(
        tripId = UUID.randomUUID().toString(),
        title = (title ?: doc.plan?.destination?.takeIf { it.isNotBlank() } ?: "我的行程").take(120),
        documentJson = gson.toJson(doc), updatedAt = System.currentTimeMillis(),
    )

    /** 仅接管本账号分库中的已有数据；不访问归属不明的旧共享库。 */
    private suspend fun bootstrap() = db.withTransaction {
        dao.activeId()?.let { editingTripId = it; return@withTransaction }
        val plan = db.travelPlanDao().getPlanById(AppDatabase.CURRENT_PLAN_ID)?.withItems(
            db.planItemDao().getItemsByPlan(AppDatabase.CURRENT_PLAN_ID),
        )
        val messages = db.chatHistoryDao().getByPlan(AppDatabase.CURRENT_PLAN_ID)
            .mapIndexed { i, row -> row.toChatMessage(i + 1L) }
        if (plan != null || messages.isNotEmpty()) {
            val row = newRow(TripDocument(plan, messages))
            dao.save(row)
            dao.setActive(ActiveTripEntity(tripId = row.tripId))
            editingTripId = row.tripId
        }
    }

    private suspend fun edit(change: TripDocument.() -> TripDocument) {
        checkSession()
        val targetTrip = editingTripId
        db.withTransaction {
            bootstrap()
            val current = if (targetTrip == null) activeRow() else checkNotNull(dao.get(targetTrip))
            val next = (current?.let(::document) ?: TripDocument()).change()
            val row = current?.copy(documentJson = gson.toJson(next), dirty = true,
                localRevision = current.localRevision + 1, updatedAt = System.currentTimeMillis()) ?: newRow(next)
            dao.save(row)
            if (current == null) {
                dao.setActive(ActiveTripEntity(tripId = row.tripId))
                editingTripId = row.tripId
            }
            // 切换发生前开始的保存只更新原行程，不能回写新行程的展示表。
            if (dao.activeId() == row.tripId) materialize(next)
        }
        refreshState()
        retry()
    }

    private suspend fun startNew(plan: TravelPlan, options: AiPlanOptions?) {
        checkSession()
        db.withTransaction {
            bootstrap()
            val doc = TripDocument(plan = plan, options = options)
            val current = activeRow()
            val draft = current?.let(::document)
            val row = if (current != null && draft?.plan?.allPlaceList.orEmpty().isEmpty() && draft?.messages.orEmpty().isEmpty()) {
                current.copy(title = plan.destination.take(120), documentJson = gson.toJson(doc),
                    localRevision = current.localRevision + 1, dirty = true, updatedAt = System.currentTimeMillis())
            } else newRow(doc)
            dao.save(row)
            dao.setActive(ActiveTripEntity(tripId = row.tripId))
            materialize(doc)
            editingTripId = row.tripId
        }
        refreshState()
        retry()
    }

    fun start(parent: CoroutineScope) {
        if (worker != null || session.phone == null) return
        scope = CoroutineScope(parent.coroutineContext + Dispatchers.IO)
        worker = scope!!.launch {
            bootstrap()
            refreshState()
            while (isActive) {
                try {
                    syncMutex.withLock { syncRound() }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    refreshState("暂未同步：${e.message ?: "网络不可用"}。本地数据已保留", false)
                }
                // 本地写入即时唤醒；失败及跨设备更新每分钟重试，进程重启从日志继续。
                withTimeoutOrNull(60_000) { requests.receive() }
                delay(300) // 合并连续的封面、提议状态等写入。
            }
        }
    }
    fun retry() { requests.trySend(Unit) }

    private suspend fun syncRound() {
        checkSession()
        refreshState("正在同步", true)
        for (row in dao.all()) {
            if (row.conflictJson != null || (!row.dirty && row.pendingRequestJson == null)) continue
            val pending = db.withTransaction {
                val latest = checkNotNull(dao.get(row.tripId))
                if (latest.pendingRequestJson != null) latest else latest.copy(
                    pendingRequestJson = gson.toJson(TripWrite(latest.remoteVersion, UUID.randomUUID().toString(),
                        latest.title, document(latest), false)),
                    pendingRevision = latest.localRevision,
                ).also { dao.save(it) }
            }
            val request = gson.fromJson(pending.pendingRequestJson, TripWrite::class.java)
            try {
                val ack = cloud.save(row.tripId, request, pending.pendingRequestJson)
                checkSession()
                require(ack.tripId == row.tripId && ack.version > request.baseVersion)
                db.withTransaction {
                    val latest = checkNotNull(dao.get(row.tripId))
                    dao.save(latest.copy(remoteVersion = ack.version,
                        dirty = TripSyncRules.stillDirty(latest.localRevision, checkNotNull(pending.pendingRevision)),
                        pendingRequestJson = null, pendingRevision = null))
                }
            } catch (e: TripConflictException) {
                checkSession()
                require(e.remote.tripId == row.tripId)
                db.withTransaction {
                    val latest = checkNotNull(dao.get(row.tripId))
                    dao.save(latest.copy(conflictJson = gson.toJson(e.remote), pendingRequestJson = null, pendingRevision = null))
                }
            }
        }
        val active = dao.active()
        if (active?.needsActivation == true) {
            val row = checkNotNull(dao.get(active.tripId))
            if (!row.dirty && row.conflictJson == null && row.pendingRequestJson == null) {
                cloud.activate(active.tripId)
                checkSession()
                db.withTransaction {
                    if (dao.active() == active) dao.setActive(active.copy(needsActivation = false))
                }
            }
        }
        // 明确选择了本地行程但尚未上传时，不让云端当前指针覆盖它。
        if (dao.active()?.needsActivation != true) {
            val remote = cloud.current()
            checkSession()
            if (remote != null) db.withTransaction {
                val current = activeRow()
                val cached = dao.get(remote.tripId)
                if (canApplyRemote() && (current == null || (isClean(current) && (cached == null || isClean(cached)))) &&
                    (current?.tripId != remote.tripId || cached == null || cached.remoteVersion < remote.version)) {
                    accept(remote)
                    dao.setActive(ActiveTripEntity(tripId = remote.tripId, needsActivation = false))
                    materialize(remote.document)
                    editingTripId = remote.tripId
                }
            }
        }
        remoteList = cloud.list()
        checkSession()
        val rows = dao.all()
        val conflicts = rows.count { it.conflictJson != null }
        val pending = rows.count { it.dirty || it.pendingRequestJson != null }
        refreshState(when {
            conflicts > 0 -> "有 $conflicts 份行程发生版本冲突，请选择保留方式"
            pending > 0 || dao.active()?.needsActivation == true -> "本地修改已保存，等待继续同步"
            else -> "已与云端同步"
        }, false)
    }

    private fun isClean(row: TripSyncEntity): Boolean = TripSyncRules.canAcceptRemote(
        row.dirty, row.pendingRequestJson != null, row.conflictJson != null)

    private suspend fun accept(remote: CloudTrip) {
        dao.save(TripSyncEntity(remote.tripId, remote.title, gson.toJson(remote.document),
            remoteVersion = remote.version, dirty = false, updatedAt = remote.updatedAt))
    }

    fun selectTrip(id: String) = action {
        val cached = dao.get(id)
        val remote = if (cached == null) cloud.get(id) else null
        checkSession()
        db.withTransaction {
            bootstrap()
            if (remote != null) accept(remote)
            val row = checkNotNull(dao.get(id))
            dao.setActive(ActiveTripEntity(tripId = id))
            materialize(document(row))
            editingTripId = id
        }
        retry()
    }

    /** 无论选哪一份，先把本地分歧保存为独立行程；之后不会强行覆盖云端已有版本。 */
    fun resolveConflict(id: String, useCloud: Boolean) = action {
        db.withTransaction {
            val row = checkNotNull(dao.get(id))
            val remote = row.conflictJson?.let { gson.fromJson(it, CloudTrip::class.java) } ?: return@withTransaction
            val copy = newRow(document(row), row.title.take(100) + " · 本地副本")
            dao.save(copy)
            accept(remote)
            if (dao.activeId() == id) {
                val chosen = if (useCloud) remote.tripId else copy.tripId
                dao.setActive(ActiveTripEntity(tripId = chosen))
                materialize(if (useCloud) remote.document else document(copy))
                editingTripId = chosen
            }
        }
        retry()
    }

    private fun action(block: suspend () -> Unit) {
        scope?.launch {
            try {
                syncMutex.withLock { checkSession(); block(); refreshState() }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { refreshState("操作未完成：${e.message}；本地数据已保留", false) }
        }
    }
    private suspend fun refreshState(message: String = _state.value.message, busy: Boolean = _state.value.busy) {
        val rows = dao.all()
        val localSummaries = rows.map { TripSummary(it.tripId, it.remoteVersion, it.title, it.updatedAt) }
        _state.value = TripSyncUiState(message, busy, dao.activeId(), rows.filter { it.conflictJson != null }.map { it.tripId },
            (localSummaries + remoteList.filter { remote -> rows.none { it.tripId == remote.tripId } }).sortedByDescending { it.updatedAt })
    }
}
