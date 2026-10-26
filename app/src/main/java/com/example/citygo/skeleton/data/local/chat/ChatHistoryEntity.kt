package com.example.citygo.skeleton.data.local.chat

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.citygo.skeleton.data.model.chat.ChatMessage
import com.example.citygo.skeleton.data.model.chat.ProposalStatus

/* =====================================================================================
 * AI 对话聊天记录表（chat_history）——全新增文件，不改动任何既有实体
 *
 * 自 App 数据库 v3 起并入主库 citygo_app.db，与业务表同库不同表；
 * 高德 POI 缓存库、高德导航 / 天气预报代码完全不受影响。
 *
 * 绑定关系：每条消息通过 [ChatHistoryEntity.planId] 逻辑绑定一份行程
 * （App 当前同时只维护一份行程，固定主键 AppDatabase.CURRENT_PLAN_ID），
 * 同一行程的对话按 timestamp、id 升序还原。
 *
 * v5 起新增 planJson / proposalStatus 两列：承载「先答复、再确认、后同步」的
 * 行程提议方案原文与确认状态；旧行两列为 NULL，等价于 ProposalStatus.NONE。
 * ===================================================================================== */

/**
 * 一条 AI 助手聊天消息的持久化实体。
 *
 * @property id 自增主键（仅库内使用，与内存 [ChatMessage.id] 不互通）
 * @property planId 绑定的行程 id（travel_plan.id，当前恒为 AppDatabase.CURRENT_PLAN_ID）
 * @property content 消息文本内容
 * @property isUser true=用户消息；false=AI 助手回复（Boolean 落库为 INTEGER 0/1）
 * @property timestamp 消息时间戳，同时用于对话顺序还原
 * @property planJson 助手提议的行程方案 JSON 原文（v5，可空；普通消息为 null）
 * @property proposalStatus 提议状态枚举名（v5，可空；null/空等价于 NONE）
 */
@Entity(
    tableName = "chat_history",
    indices = [Index(value = ["planId"], name = "index_chat_history_planId")],
)
data class ChatHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val planId: Long,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long,
    val planJson: String? = null,
    val proposalStatus: String? = null,
)

/** 库实体 → 对话领域模型（内存 id 由调用方按顺序重排，保证会话内自增连续） */
internal fun ChatHistoryEntity.toChatMessage(messageId: Long): ChatMessage =
    ChatMessage(
        id = messageId,
        content = content,
        isUser = isUser,
        timestamp = timestamp,
        planJson = planJson,
        proposalStatus = ProposalStatus.fromStorage(proposalStatus),
    )

/** 对话领域模型 → 库实体（库内自增 id 不保留，id 置 0 由 Room 分配） */
internal fun ChatMessage.toChatHistoryEntity(planId: Long): ChatHistoryEntity =
    ChatHistoryEntity(
        id = 0L,
        planId = planId,
        content = content,
        isUser = isUser,
        timestamp = timestamp,
        // NONE 落 NULL 与历史行保持一致；其余状态落枚举名
        planJson = planJson,
        proposalStatus = proposalStatus.takeUnless { it == ProposalStatus.NONE }?.name,
    )
