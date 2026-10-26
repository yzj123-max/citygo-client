package com.example.citygo.skeleton.data.model.chat

/* =====================================================================================
 * AI 对话 Agent 模块：聊天消息领域模型
 *
 * 内存中由 AppViewModel 的 StateFlow 持有（关闭弹窗 / 切换 Tab 不丢失）；
 * 同时通过 ChatHistoryRepository 绑定当前行程持久化到 Room（chat_history 表，
 * App 数据库 v3 起），关闭弹窗 / 每轮对话完成自动保存，App 重启后自动恢复。
 * ===================================================================================== */

/**
 * 助手消息携带的「行程同步提议」状态。
 *
 * 协议：模型在回复中输出 `<PLAN_JSON>` 时代表一份修改方案，客户端不立即改行程，
 * 而是挂到助手消息上等待用户确认。
 *
 * - [NONE]：普通消息（纯咨询 / 用户消息），无方案、无操作区；
 * - [PENDING]：方案待用户确认，气泡下方显示「同步修改行程 / 暂不」；
 * - [APPLIED]：用户已点击同步，方案已应用到当前行程（终态）；
 * - [DECLINED]：用户点击「暂不」放弃方案（终态）；
 * - [SUPERSEDED]：后续对话产生了更新的待确认方案，本方案自动作废、不可再采用。
 */
enum class ProposalStatus {
    NONE,
    PENDING,
    APPLIED,
    DECLINED,
    SUPERSEDED,
    ;

    companion object {
        /** 持久化列还原：null / 空串 / 未知值一律按 [NONE] 处理，保证旧库与脏数据不崩。 */
        fun fromStorage(raw: String?): ProposalStatus =
            entries.firstOrNull { it.name == raw?.trim()?.uppercase() } ?: NONE
    }
}

/**
 * 一条聊天消息。
 *
 * @property id 消息唯一 ID（由 ViewModel 按会话内自增分配）
 * @property content 消息文本内容（已剔除 `<PLAN_JSON>` 协议块的纯展示文本）
 * @property isUser true=用户发送（右侧气泡）；false=AI 助手回复（左侧气泡）
 * @property timestamp 消息生成时间戳（System.currentTimeMillis）
 * @property planJson 助手提议的行程方案原文（`<PLAN_JSON>` 块内的 JSON 文本）；
 *   仅助手消息且模型本轮回传了可成功解析的方案时非空，其余情况为 null
 * @property proposalStatus 方案提议状态，普通消息恒为 [ProposalStatus.NONE]
 */
data class ChatMessage(
    val id: Long,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long,
    val planJson: String? = null,
    val proposalStatus: ProposalStatus = ProposalStatus.NONE,
)
