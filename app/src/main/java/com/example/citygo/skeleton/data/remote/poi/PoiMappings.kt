package com.example.citygo.skeleton.data.remote.poi

import com.example.citygo.skeleton.data.model.ScenicSpot
import com.example.citygo.skeleton.data.model.SpotTag

/* =====================================================================================
 * POI 检索配置开关 + PoiDto → ScenicSpot 领域模型映射
 *
 * 高德 POI 只提供「点位基础信息」（名称 / 地址 / 坐标 / 类型 / 少量照片 / 评分），
 * 门票、简介、避坑、攻略等文案字段在映射阶段给空或默认值，
 * 再交由 SpotContentEnricher（本地规则 / 预留 RAG 大模型）统一补充。
 * ===================================================================================== */

/**
 * POI 数据源配置（接口开关）。
 *
 * 需要调整推荐数量或缓存时长时直接改这里，无需改动仓库 / UI 代码。
 */
object PoiConfig {
    /** 总开关：false 时回退 Mock 仓库（调试用） */
    const val POI_ENABLED: Boolean = true

    /** 首页每个城市返回的景点数量（需求约定 8-12 个，默认 10） */
    var recommendLimit: Int = 10

    /** 全国景点名搜索的最大返回条数 */
    const val SEARCH_RESULT_LIMIT: Int = 20

    /** 城市景点缓存有效期：24 小时（毫秒） */
    const val CITY_CACHE_TTL_MS: Long = 24L * 60 * 60 * 1000

    /** 景点名搜索结果缓存有效期：6 小时（毫秒） */
    const val SEARCH_CACHE_TTL_MS: Long = 6L * 60 * 60 * 1000

    /** 城市 POI 少于该数量时，自动发起同城市小众点位补充检索 */
    const val MIN_CITY_POI_COUNT: Int = 6
}

/** 小众 / 城市休闲点位关键词（命中名称或类型即归为「小众」标签） */
private val NICHE_KEYWORDS = listOf(
    "打卡", "文创", "书店", "咖啡", "茶馆", "茶室", "老街", "巷子", "巷", "夜市",
    "步行街", "美食街", "特色", "市集", "集市", "公园", "广场", "湿地", "绿道",
    "露营", "艺术区", "创意园", "创意园", "公社", "小店", "观景台", "栈道",
)

/** 明显不是「可游玩景点」的噪声 POI 关键词（关键词召回里可能混入，统一过滤） */
private val NOISE_KEYWORDS = listOf(
    "公厕", "公共厕所", "厕所", "卫生间", "停车场", "停车", "售票处", "游客中心",
    "游客服务中心", "地铁站", "地铁口", "公交站", "加油站", "加气站", "充电站",
    "收费站", "服务区", "ATM", "银行", "医院", "卫生院", "诊所", "药店", "药房",
    "小区", "公寓", "大厦", "写字楼", "酒店", "宾馆", "民宿", "旅馆", "招待所",
    "便利店", "超市", "烟酒店", "彩票", "维修", "快递", "洗衣", "批发", "菜市场",
    "办事处", "居委会", "派出所", "物业",
    // 纯商业 / 专业市场不是旅游景点（避免「宝华楼购物中心」混入推荐）
    "购物中心", "购物广场", "商场", "百货", "综合市场", "商贸城", "建材城",
    "家具城", "家电", "数码城", "电脑城", "汽配", "4S", "售楼", "驾校",
    "营业厅", "工业园", "产业园",
)

/**
 * 判断 POI 是否值得进入景点推荐列表（过滤关键词召回的噪声点位）。
 */
fun PoiDto.isWorthySpot(): Boolean {
    val name = name?.trim().orEmpty()
    if (name.isEmpty()) return false
    return NOISE_KEYWORDS.none { name.contains(it) }
}

/**
 * 将高德 POI 映射为 App 景点模型。
 *
 * @param fallbackCity POI 所属城市名（高德在直辖市 / 省直辖县级市常把 cityname 返回空，由调用方兜底）
 * @return 缺 id / 名称 / 合法坐标时返回 null，调用方直接丢弃
 */
fun PoiDto.toScenicSpot(fallbackCity: String): ScenicSpot? {
    val id = id?.takeIf { it.isNotBlank() } ?: return null
    val name = name?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val (lng, lat) = parseLocation(location) ?: return null

    val typeText = type?.trim().orEmpty()
    val area = listOfNotNull(adname?.takeIf { it.isNotBlank() }, address?.takeIf { it.isNotBlank() })
        .distinct()
        .joinToString(" · ")
    val score = bizExt?.rating?.toDoubleOrNull()?.takeIf { it in 0.0..5.0 } ?: 0.0

    // 封面：高德 photos 首图（协议补全）；无图时按 poi id 稳定轮换本地场景占位图
    val cover = photos?.firstOrNull()?.url?.normalizeImageUrl()
        ?: "mock://scene/${(id.hashCode().toLong() and 0x7FFFFFFFL) % 12}"

    // 公园广场类 / 餐饮特色店 / 命中休闲关键词 → 小众；其余风景名胜 → 热门
    val tag = when {
        typeCode.orEmpty().startsWith("1102") -> SpotTag.NICHE
        typeCode.orEmpty().startsWith("05") -> SpotTag.NICHE
        NICHE_KEYWORDS.any { name.contains(it) || typeText.contains(it) } -> SpotTag.NICHE
        else -> SpotTag.HOT
    }

    // 门票初始文案：仅餐饮类（05 开头）取人均消费；其余一律留空，
     // 由 LocalRuleSpotEnricher 按名称 / 类型关键词判断（公园广场→免费，
     // 收费景区→以官方公示为准），不能仅凭 typecode 1102 就写死「免费开放」
     // （拙政园等收费园林的 typecode 也在 1102 段）。
    val cost = bizExt?.cost?.takeIf { it.isNotBlank() }
    val ticketInfo = when {
        !cost.isNullOrBlank() && typeCode.orEmpty().startsWith("05") -> "人均约 ¥$cost"
        else -> ""
    }

    return ScenicSpot(
        spotId = id,
        city = cityname?.takeIf { it.isNotBlank() } ?: fallbackCity,
        name = name,
        tag = tag,
        coverImg = cover,
        score = score,
        reviewCount = 0, // 免费 POI 接口不返回评价数，0 表示暂无，卡片 UI 自适应隐藏
        shortDesc = buildDefaultDesc(typeText, area),
        imgUrls = listOf(cover),
        ticketInfo = ticketInfo,
        freePolicy = "",
        ticketTip = "",
        duration = "",
        bestSeason = "",
        traffic = "",
        pitTips = emptyList(),
        userReviews = emptyList(), // POI 不提供评价正文，空列表时详情页展示「暂无评价」占位
        lat = lat,
        lng = lng,
    )
}

/** 解析高德坐标字符串「经度,纬度」为 (lng, lat)；非法返回 null */
private fun parseLocation(location: String?): Pair<Double, Double>? {
    if (location.isNullOrBlank()) return null
    val parts = location.split(",")
    if (parts.size != 2) return null
    val lng = parts[0].trim().toDoubleOrNull() ?: return null
    val lat = parts[1].trim().toDoubleOrNull() ?: return null
    // 中国境内经纬度粗校验，剔除异常 0,0
    if (lng !in 73.0..136.0 || lat !in 3.0..54.0) return null
    return lng to lat
}

/**
 * 封面 URL 归一化：
 * - // 开头的协议相对地址补 https；
 * - 高德部分图片是 http:// 明文地址，Android 9+ 默认禁止明文流量，统一升级为 https；
 * - 其余原样返回。
 */
private fun String.normalizeImageUrl(): String {
    val raw = trim()
    return when {
        raw.startsWith("//") -> "https:$raw"
        raw.startsWith("http://") -> "https://" + raw.removePrefix("http://")
        else -> raw
    }
}

/** 由 POI 类型 + 所在区域拼一条占位简介（Enricher 可再覆盖为更具体的文案） */
private fun buildDefaultDesc(typeText: String, area: String): String {
    val type = typeText.substringAfterLast(";").ifBlank { typeText }
    return when {
        type.isNotBlank() && area.isNotBlank() -> "$type，位于$area"
        type.isNotBlank() -> type
        area.isNotBlank() -> "位于$area"
        else -> "当地值得一去的打卡点位"
    }
}
