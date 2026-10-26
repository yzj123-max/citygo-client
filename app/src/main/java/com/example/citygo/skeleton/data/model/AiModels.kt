package com.example.citygo.skeleton.data.model

/* =====================================================================================
 * AI 旅行攻略领域模型（独立文件，不影响既有任何模型）
 *
 * 用途：承载后端攻略导入的地点和行程对话助手的结构化提议。
 * UI / ViewModel 只接触领域模型，不直接依赖 Room 实体。
 *
 * 历史 ai_travel_plan / ai_spot 缓存表仍保留用于数据库兼容；
 * 正式攻略生成统一走后端，不再按城市缓存触发客户端模型调用。
 * ===================================================================================== */

/**
 * AI 生成的单个地点（景点或美食店铺）。
 *
 * @property name 地点名称
 * @property type 地点类型（见 [AiSpotType]）
 * @property desc 小红书风格简介
 * @property latitude 纬度（GCJ-02 火星坐标，与地图 / 导航模块一致）
 * @property longitude 经度（GCJ-02 火星坐标）
 * @property isPetFriendly 是否宠物友好
 * @property imageUrls 图片 url 列表（可为空列表）
 * @property tip 游玩小贴士（避坑 / 推荐时段 / 人均等）
 * @property day 模型建议安排在第几天（从 1 开始；多日行程 Prompt 返回，单日 / 旧缓存可能为 null）
 * @property ticket 景点门票信息（后端数据库预置扩展属性原文，如「免费」「暂无相关信息」）；
 *   null 表示攻略文本里没有该字段，UI 不渲染这一行；非空时原样展示，禁止前端编造
 * @property avgPrice 餐厅人均价格原文（同上，可能是「暂无相关信息」）
 * @property signatureDish 餐厅特色菜品原文（同上，可能是「暂无相关信息」）
 * @property photoScene 拍照场景 / 出片点原文（可能是「暂无相关信息」）
 * @property travelTip 出行要点 / 注意事项原文（可能是「暂无相关信息」）
 * @property timeSlot 时间段原文（如「上午9:00-12:00」「午餐 12:30」；可能是「暂无相关信息」）
 * @property source 点位来源：数据库预置点位 [AiSpotSource.LOCAL_DB] 还是 AI 额外拓展
 *   [AiSpotSource.AI_GENERATED]，用于行程卡片显示【本地库推荐】/【AI生成推荐】
 */
data class AiSpot(
    val name: String,
    val type: AiSpotType,
    val desc: String,
    val latitude: Double,
    val longitude: Double,
    val isPetFriendly: Boolean = false,
    val imageUrls: List<String> = emptyList(),
    val tip: String = "",
    val day: Int? = null,
    val ticket: String? = null,
    val avgPrice: String? = null,
    val signatureDish: String? = null,
    val photoScene: String? = null,
    val travelTip: String? = null,
    val timeSlot: String? = null,
    val source: AiSpotSource = AiSpotSource.AI_GENERATED,
    /** 坐标是否为高德地理编码失败后的城市中心兜底坐标（true 时 UI 可弱提示，点位不丢弃） */
    val mockedCoord: Boolean = false,
)

/**
 * 攻略点位来源（后端优先读数据库预置点位，再由 Agent 补充拓展点位）。
 *
 * @property tag 行程卡片上展示的来源标签文案
 */
enum class AiSpotSource(val tag: String) {
    /** 数据库预置点位（含门票 / 人均 / 特色菜等扩展属性） */
    LOCAL_DB("本地库推荐"),

    /** AI Agent 在数据库之外额外拓展的点位 */
    AI_GENERATED("AI生成推荐"),
    ;

    companion object {
        /** 容错解析来源文本；无法识别时默认按 AI 拓展处理，避免把拓展点误标成库内推荐 */
        fun fromText(text: String?): AiSpotSource {
            val t = text.orEmpty().trim()
            return when {
                t.isBlank() -> AI_GENERATED
                LOCAL_KEYWORDS.any { t.contains(it) } -> LOCAL_DB
                AI_KEYWORDS.any { t.contains(it, ignoreCase = true) } -> AI_GENERATED
                else -> AI_GENERATED
            }
        }

        /** 明确表示「来自数据库预置点位」的关键词（来源字段 / 行内【】标签通用） */
        private val LOCAL_KEYWORDS =
            listOf("本地库", "数据库", "预置", "预设", "库内", "资料库", "官方库", "local", "preset")

        /** 明确表示「AI 额外拓展点位」的关键词 */
        private val AI_KEYWORDS =
            listOf("AI", "智能", "大模型", "模型", "拓展", "扩展", "额外", "生成", "agent")
    }
}

/**
 * AI 地点类型枚举。
 *
 * @property label 中文展示名（用于 UI 展示，同时作为持久化的稳定编码由实体层存取）
 */
enum class AiSpotType(val label: String) {
    POPULAR_SPOT("大众景点"),
    NICHE_SPOT("小众景点"),
    LOCAL_FOOD("本地美食"),
    INTERNET_FAMOUS_SHOP("探店网红店"),
    HOTEL("酒店住宿"),
    ;

    companion object {
        /**
         * 由中文名还原枚举；无法识别时（如模型返回了新类型）容错回退到「大众景点」，
         * 保证缓存反序列化不崩溃。
         */
        fun fromLabel(label: String?): AiSpotType =
            entries.firstOrNull { it.label == label } ?: POPULAR_SPOT
    }
}

/**
 * AI 生成的完整城市攻略对象。
 *
 * @property city 城市名称
 * @property generateTime 生成时间（毫秒时间戳，用于判断缓存是否过期 / 展示生成时间）
 * @property spotList 景点 & 美食店铺列表（保持大模型给出的推荐顺序）
 */
data class AiTravelPlan(
    val city: String,
    val generateTime: Long,
    val spotList: List<AiSpot>,
)
