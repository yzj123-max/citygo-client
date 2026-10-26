package com.example.citygo.skeleton.data.repository.chat

import androidx.room.withTransaction
import com.example.citygo.skeleton.data.local.app.AppDatabase
import com.example.citygo.skeleton.data.local.chat.toChatHistoryEntity
import com.example.citygo.skeleton.data.local.chat.toChatMessage
import com.example.citygo.skeleton.data.model.chat.ChatMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/* =====================================================================================
 * AI 助手聊天记录仓库（全新增文件）
 *
 * 独立于既有 [com.example.citygo.skeleton.data.repository.Repository] 体系，
 * 只服务对话 Agent 的聊天记录持久化，不改动任何既有实体 / DAO / Repository，
 * 高德导航 / 天气预报代码完全不受影响。
 *
 * 领域模型 [ChatMessage] 与 Room 实体在本文件内互转，ViewModel 只依赖接口、不感知 Room。
 * ===================================================================================== */

/** 聊天记录持久化入口：按行程 id 维度读取 / 整段覆盖保存 */
interface ChatHistoryRepository {
    fun observeMessages(planId: Long): Flow<List<ChatMessage>> = flow { emit(loadMessages(planId)) }

    /** 读取某份行程绑定的全部聊天记录（按对话顺序，内存 id 从 1 重排） */
    suspend fun loadMessages(planId: Long): List<ChatMessage>

    /**
     * 整段覆盖保存某份行程的聊天记录（同一事务内先清空再插入，失败整体回滚）。
     * 空列表等价于清空该行程的聊天记录。
     */
    suspend fun replaceMessages(planId: Long, messages: List<ChatMessage>)
}

/**
 * 基于 Room（citygo_app.db 的 chat_history 表，v3 新增）的实现。
 *
 * @param appDatabase App 主业务库（v3+，含聊天记录表）
 * @param ioDispatcher Room 为阻塞操作，统一切到 IO 调度器
 */
class RoomChatHistoryRepository(
    private val appDatabase: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher = IO,
) : ChatHistoryRepository {

    override fun observeMessages(planId: Long): Flow<List<ChatMessage>> =
        chatHistoryDao.observeByPlan(planId).map { rows ->
            rows.mapIndexed { i, row -> row.toChatMessage(i + 1L) }
        }

    private val chatHistoryDao
        get() = appDatabase.chatHistoryDao()

    override suspend fun loadMessages(planId: Long): List<ChatMessage> =
        withContext(ioDispatcher) {
            chatHistoryDao.getByPlan(planId)
                .mapIndexed { index, entity -> entity.toChatMessage(index + 1L) }
        }

    override suspend fun replaceMessages(planId: Long, messages: List<ChatMessage>) {
        withContext(ioDispatcher) {
            // 先删后插必须在同一事务内，避免异常后出现半截 / 重复记录
            appDatabase.withTransaction {
                chatHistoryDao.deleteByPlan(planId)
                if (messages.isNotEmpty()) {
                    chatHistoryDao.insertAll(messages.map { it.toChatHistoryEntity(planId) })
                }
            }
        }
    }

    companion object {
        /** 工厂：与既有 LocalXxxRepository.create(db) 风格保持一致 */
        fun create(appDatabase: AppDatabase): ChatHistoryRepository =
            RoomChatHistoryRepository(appDatabase)
    }
}
