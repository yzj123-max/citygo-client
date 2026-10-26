package com.example.citygo.skeleton.data.local.chat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/* =====================================================================================
 * AI 对话聊天记录 DAO（chat_history）—— 全新增文件，不改动任何既有 DAO
 *
 * 约定与既有 DAO 一致：写方法 suspend，由 Repository 切到 IO 调度器调用；
 * 「整段对话覆盖保存」= 先按 planId 清空再批量插入，由 Repository 包在同一事务内。
 * ===================================================================================== */
@Dao
interface ChatHistoryDao {

    @Query("SELECT * FROM chat_history WHERE planId = :planId ORDER BY timestamp ASC, id ASC")
    fun observeByPlan(planId: Long): Flow<List<ChatHistoryEntity>>

    /** 查某份行程的全部聊天记录（按时间、插入顺序升序还原对话） */
    @Query("SELECT * FROM chat_history WHERE planId = :planId ORDER BY timestamp ASC, id ASC")
    suspend fun getByPlan(planId: Long): List<ChatHistoryEntity>

    /** 批量插入聊天记录（id=0 自增；整段覆盖保存时使用） */
    @Insert
    suspend fun insertAll(messages: List<ChatHistoryEntity>)

    /** 删除某份行程的全部聊天记录（整段覆盖保存前先清空） */
    @Query("DELETE FROM chat_history WHERE planId = :planId")
    suspend fun deleteByPlan(planId: Long)
}
