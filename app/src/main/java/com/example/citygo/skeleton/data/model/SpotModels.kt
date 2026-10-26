package com.example.citygo.skeleton.data.model

/* =====================================================================================
 * 景点推荐领域模型（纯 Kotlin 数据层，不依赖 Compose / 高德 SDK）
 *
 * 与后端约定的 Mock JSON 结构保持一致（首页景点推荐 + 景点搜索 + 景点详情共用）：
 * {
 *   "spotId": "s001", "city": "长沙", "name": "岳麓山", "tag": "热门",
 *   "coverImg": "...", "score": 4.7, "reviewCount": 1258, "shortDesc": "...",
 *   "imgUrls": ["..."], "ticketInfo": "...", "playGuide": "...", "traffic": "...",
 *   "pitTips": "...", "userReviews": [{"user":"游客1","score":4.8,"content":"..."}]
 * }
 *
 * 说明：
 * - tag 在 JSON 中为「热门 / 小众」字符串，App 内用 [SpotTag] 枚举承载；
 * - playGuide 在 JSON 中为整句攻略，详情页需分区展示「建议游玩时长 / 最佳游玩季节」，
 *   故数据层拆分为 duration / bestSeason / traffic 三个字段（均为中文文案）；
 * - 门票板块详情页需要「票价 / 免票政策 / 购票提示」三块，ticketInfo 保留票价文案，
 *   另加 freePolicy / ticketTip（Mock 直接给全，后端字段就位后直接映射）；
 * - lat / lng 为 GCJ-02 高德坐标，供「去导航」路线规划使用，JSON 调试时一并下发。
 * ===================================================================================== */

/**
 * 景点类型标签。
 *
 * @property label 中文标签名（卡片角标、详情页展示，直接对应 JSON 中的 tag 字符串）
 */
enum class SpotTag(val label: String) {
    /** 热门景点 */
    HOT("热门"),

    /** 小众景点 */
    NICHE("小众");

    companion object {
        /** 由后端 JSON 的 tag 字符串解析为枚举，无法识别时默认归为 [HOT] */
        fun fromLabel(label: String?): SpotTag =
            entries.firstOrNull { it.label == label } ?: HOT
    }
}

/**
 * 用户评价条目（景点详情页「用户评价」板块）。
 *
 * @property user 评价用户昵称（Mock 脱敏展示，如「游客1」）
 * @property score 用户打分（0.0-5.0，支持小数）
 * @property content 评价正文
 */
data class SpotReview(
    val user: String,
    val score: Double,
    val content: String,
)

/**
 * 景点实体（首页推荐卡片 / 搜索结果 / 详情页的唯一数据模型）。
 */
data class ScenicSpot(
    /** 景点唯一 id，字符串型，如 "s001"（详情页路由参数） */
    val spotId: String,
    /** 所在城市名，如「长沙」（首页城市切换筛选用） */
    val city: String,
    /** 景点名称 */
    val name: String,
    /** 热门 / 小众标签 */
    val tag: SpotTag,
    /** 封面图地址（Mock 阶段为 mock:// 本地场景图，上线后替换为 https 图片 URL） */
    val coverImg: String,
    /** 综合评分（0.0-5.0，一位小数） */
    val score: Double,
    /** 用户评价总数量 */
    val reviewCount: Int,
    /** 一句话简介（推荐卡片展示） */
    val shortDesc: String,
    /** 详情页顶部轮播图地址列表（至少 1 张，首张即封面） */
    val imgUrls: List<String>,
    /** 门票票价文案，如「免费，观光车20元」 */
    val ticketInfo: String,
    /** 免票政策，如「65 周岁以上老人、1.3 米以下儿童免票」 */
    val freePolicy: String,
    /** 购票提示，如「建议提前一天在官方公众号预约」 */
    val ticketTip: String,
    /** 建议游玩时长，如「3 小时」 */
    val duration: String,
    /** 最佳游玩季节，如「春秋两季，清晨人少」 */
    val bestSeason: String,
    /** 交通方式，如「地铁 4 号线湖南大学站步行上山」 */
    val traffic: String,
    /** 避坑提示列表（详情页独立板块，逐条展示） */
    val pitTips: List<String>,
    /** 用户评价列表 */
    val userReviews: List<SpotReview>,
    /** 纬度（GCJ-02 高德坐标，路线规划起点/终点用） */
    val lat: Double,
    /** 经度（GCJ-02） */
    val lng: Double,
    val backendId: Long? = null,
    val amapPoiId: String? = null,
    val address: String = "",
    val phone: String = "",
    val description: String = "",
    val highlights: String = "",
    val descriptionSourceName: String? = null,
    val descriptionSourceUrl: String? = null,
    val descriptionVerifiedAt: String? = null,
    val policies: List<com.example.citygo.skeleton.data.remote.attraction.AttractionPolicyDto> = emptyList(),
    val ticketStatus: String = "UNKNOWN",
    val petPolicy: String = "UNKNOWN",
    val distanceMeters: Long? = null,
    val coordinateAvailable: Boolean = false,
    val canNavigate: Boolean = false,
    val backendData: Boolean = false,
    val level: String = "",
    val collectedInfo: com.example.citygo.skeleton.data.remote.attraction.AttractionCollectedInfo? = null,
)
