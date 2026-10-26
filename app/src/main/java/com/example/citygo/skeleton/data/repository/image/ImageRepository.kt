package com.example.citygo.skeleton.data.repository.image

import com.example.citygo.BuildConfig
import com.example.citygo.skeleton.data.model.PlaceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/* =====================================================================================
 * 地点图片仓库（全新增独立文件，不修改高德导航 / 天气预报任何既有代码）
 *
 * 用途：AI 生成攻略 / 手动添加酒店后，根据「城市 + 地点名称」搜索真实店铺 / 景点图片
 * URL，异步填充到计划卡片；搜不到或网络失败时返回空列表，由 UI 显示默认占位图。
 *
 * 实现说明：[AmapPoiImageRepository] 复用项目已配置的高德 Web 服务 Key
 * （BuildConfig.AMAP_WEB_KEY）调用高德 POI 文本搜索 REST 接口的 photos 字段——
 * 这是**新写的独立网络调用**，不依赖、不改动任何既有高德导航 / 地图 / 天气代码。
 *
 * 命中率优化：
 * - 按点位类型附高德行业 typecode（餐饮 / 购物 / 景点 / 住宿）+ citylimit 城市限定，
 *   大幅减少同名异地、跨行业 POI 干扰；
 * - 多结果时优选名称最匹配的 POI（完全一致 > 互相包含），优先取该 POI 的实拍图。
 *
 * 后端对接：未来服务端提供图片搜索接口后，新增一个本接口的实现并在
 * AppServiceLocator 替换实例即可，上层 ViewModel / UI 无需改动。
 * ===================================================================================== */

/**
 * 地点图片仓库顶层接口。
 */
interface ImageRepository {

    /**
     * 按地点名称搜索真实图片 URL 列表。
     *
     * @param query 地点名称（如「洪崖洞民俗风貌区」「储奇门豆花鱼」）
     * @param city 城市名，用于缩小搜索范围、提高命中率；可为 null
     * @param limit 最多返回的图片数量（去重后）
     * @param placeType 点位类型；非空时附加对应高德行业分类，提升饭店 / 探店 / 酒店命中率
     * @return 可直接加载的 https 图片地址；**任何失败都返回空列表，不抛异常**
     *         （图片是攻略的附加信息，不能因图片服务失败影响主流程）
     */
    suspend fun searchPlaceImages(
        query: String,
        city: String? = null,
        limit: Int = 6,
        placeType: PlaceType? = null,
    ): List<String>
}

/**
 * 基于高德 Web 服务「POI 文本搜索」的图片仓库实现。
 *
 * 接口：GET https://restapi.amap.com/v3/place/text
 * 关键字命中 POI 后，取其 photos 数组中的真实实拍图（store.is.autonavi.com）。
 */
class AmapPoiImageRepository(
    private val webKey: String = BuildConfig.AMAP_WEB_KEY,
    private val client: OkHttpClient = defaultClient(),
) : ImageRepository {

    private val logger = Logger.getLogger("AmapPoiImageRepository")

    override suspend fun searchPlaceImages(
        query: String,
        city: String?,
        limit: Int,
        placeType: PlaceType?,
    ): List<String> = withContext(Dispatchers.IO) {
        val keyword = query.trim()
        if (keyword.isEmpty() || webKey.isBlank()) return@withContext emptyList()

        // 去掉模型可能自带的城市前缀（如「杭州XX酒店」），避免拼成「杭州杭州XX酒店」
        val cityName = city?.trim().orEmpty()
        val bareKeyword = if (cityName.isNotEmpty() && keyword.startsWith(cityName)) {
            keyword.removePrefix(cityName).trim()
        } else {
            keyword
        }
        // 城市 + 名称一起作为关键词，POI 名称为知名店铺 / 景点时命中率最高
        val fullKeyword = cityName.takeIf { it.isNotEmpty() }?.let { "$it$bareKeyword" } ?: bareKeyword
        val url = buildString {
            append(POI_TEXT_SEARCH_URL)
            append("?keywords=").append(URLEncoder.encode(fullKeyword, "UTF-8"))
            if (cityName.isNotEmpty()) {
                append("&city=").append(URLEncoder.encode(cityName, "UTF-8"))
                // 严格限定在目标城市，避免同名店铺异地结果
                append("&citylimit=true")
            }
            placeType?.toAmapTypeCode()?.let { append("&types=").append(it) }
            // extensions=all 才返回 photos / biz_ext 等扩展字段
            append("&extensions=all&offset=8&page=1&output=JSON")
            append("&key=").append(webKey)
        }

        // 模拟器弱网 / 高德偶发限流（infocode!=10000）时重试一次，顺序配图下整体仍很快
        var lastError: String? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastError = "HTTP ${response.code}"
                    } else {
                        val body = response.body?.string().orEmpty()
                        val infocode = runCatching {
                            org.json.JSONObject(body).optString("infocode")
                        }.getOrNull()
                        if (infocode == "10000") {
                            // 酒店必须名称相关：模型可能虚构不存在的酒店名，宁缺毋滥，不配无关酒店图
                            return@withContext parsePhotoUrls(
                                keyword = bareKeyword,
                                cityName = cityName,
                                json = body,
                                limit = limit,
                                allowUnrelated = placeType != PlaceType.HOTEL,
                            )
                        }
                        lastError = "infocode=$infocode"
                    }
                }
            } catch (e: Exception) {
                lastError = e.message
            }
            if (attempt < MAX_ATTEMPTS - 1) Thread.sleep(RETRY_DELAY_MS)
        }

        logger.warning("图片搜索失败（$keyword）：$lastError")
        emptyList()
    }

    private data class PoiEntry(val name: String, val photos: List<String>)

    /**
     * 解析高德 POI 响应：
     * 1. 收集全部 POI 的 photos；2. 优选名称最匹配的 POI 先取图，不足再用其他 POI 补足。
     *
     * @param allowUnrelated 是否允许名称不相关 POI 的图片兜底（景点同片区实景可兜底；
     *        酒店必须严格相关，虚构酒店名返回空列表走占位图）
     */
    private fun parsePhotoUrls(
        keyword: String,
        cityName: String,
        json: String,
        limit: Int,
        allowUnrelated: Boolean,
    ): List<String> {
        if (json.isBlank()) return emptyList()
        return try {
            val root = JSONObject(json)
            // 高德接口业务错误码：10000 为成功；其余直接视为无图
            if (root.optString("status") != "1" || root.optString("infocode") != "10000") {
                return emptyList()
            }
            val pois = root.optJSONArray("pois") ?: return emptyList()
            val entries = ArrayList<PoiEntry>()
            for (i in 0 until pois.length()) {
                val poi = pois.optJSONObject(i) ?: continue
                val name = poi.optString("name").trim()
                val photos = poi.optJSONArray("photos")?.let { arr ->
                    (0 until arr.length()).mapNotNull { j ->
                        arr.optJSONObject(j)?.optString("url").orEmpty().trim()
                            .takeIf { it.isNotEmpty() }
                            ?.let { raw ->
                                // 高德返回 http 明文地址，统一升级 https（该域名已验证支持 https）
                                if (raw.startsWith("http://")) raw.replaceFirst("http://", "https://") else raw
                            }
                            ?.takeIf { it.startsWith("https://") }
                    }
                }.orEmpty()
                if (name.isNotEmpty() && photos.isNotEmpty()) entries.add(PoiEntry(name, photos))
            }
            if (entries.isEmpty()) return emptyList()

            // 去括号分店名、去城市前缀后的核心名（POI 常叫「杭州西溪喜来登度假大酒店」）
            val coreOf: (String) -> String = { raw ->
                var n = raw.substringBefore("(").substringBefore("（").trim()
                if (cityName.isNotEmpty() && n.startsWith(cityName)) n = n.removePrefix(cityName)
                n
            }
            // 名称相关性分档：完全一致 → 核心名一致 → 互相包含 → 高相似度（如差一个「大」字）
            val kCore = coreOf(keyword)
            val exact = entries.firstOrNull { it.name == keyword }
            val exactBare = entries.firstOrNull { e ->
                e !== exact && kCore.isNotEmpty() && coreOf(e.name) == kCore
            }
            val fuzzy = entries.firstOrNull { e ->
                e !== exact && e !== exactBare &&
                    (e.name.contains(keyword) || keyword.contains(e.name) ||
                        (kCore.isNotEmpty() &&
                            (coreOf(e.name).contains(kCore) || kCore.contains(coreOf(e.name)))))
            }
            val similar = entries.firstOrNull { e ->
                e !== exact && e !== exactBare && e !== fuzzy &&
                    kCore.isNotEmpty() && lcsSimilarity(kCore, coreOf(e.name)) >= NAME_SIMILARITY_THRESHOLD
            }
            val prioritized = listOfNotNull(exact, exactBare, fuzzy, similar)
            val orderedPois = prioritized +
                entries.filterNot { e -> prioritized.any { it === e } }

            // 酒店等严格场景：没有名称相关 POI 时宁可无图，不用无关图片兜底
            if (!allowUnrelated && prioritized.isEmpty()) return emptyList()

            val result = LinkedHashSet<String>()
            outer@ for (entry in orderedPois) {
                for (photo in entry.photos) {
                    result.add(photo)
                    if (result.size >= limit) break@outer
                }
            }
            result.toList()
        } catch (e: Exception) {
            logger.warning("图片搜索响应解析失败：${e.message}")
            emptyList()
        }
    }

    /**
     * 两个名称核心串的最长公共子序列相似度（LCS 长度 / 较长串长度，0~1）。
     * 用于容忍「度假酒店 / 度假大酒店」这类一字之差的真实 POI 名称，
     * 同时对完全不相干的名称给出低分（避免给虚构酒店配无关图）。
     */
    private fun lcsSimilarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1] + 1
                } else {
                    maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }
        return dp[a.length][b.length].toDouble() / maxOf(a.length, b.length)
    }

    companion object {
        /** 高德 Web 服务 POI 文本搜索接口 */
        private const val POI_TEXT_SEARCH_URL = "https://restapi.amap.com/v3/place/text"

        /** 最多尝试次数（含首次）；高德偶发 QPS 限流 / 模拟器弱网时重试一次 */
        private const val MAX_ATTEMPTS = 2

        /** 重试间隔（毫秒） */
        private const val RETRY_DELAY_MS = 400L

        /** 酒店名称 LCS 相似度阈值：高于此值视为同一家酒店（容忍一字之差） */
        private const val NAME_SIMILARITY_THRESHOLD = 0.75

        /** 点位类型 → 高德 POI 行业大类 typecode（限定分类可显著提升小店命中率） */
        private fun PlaceType.toAmapTypeCode(): String = when (this) {
            PlaceType.RESTAURANT -> "050000" // 餐饮服务
            PlaceType.SHOP -> "060000"       // 购物服务（探店店铺 / 商场 / 网红店）
            PlaceType.HOTEL -> "100000"      // 住宿服务
            PlaceType.SPOT -> "110000"       // 风景名胜
        }

        /** 图片请求轻量、可并发，独立连接池并放宽一点超时 */
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
