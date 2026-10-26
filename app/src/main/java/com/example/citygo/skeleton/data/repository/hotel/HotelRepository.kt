package com.example.citygo.skeleton.data.repository.hotel

import com.example.citygo.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/* =====================================================================================
 * 酒店 POI 仓库（全新增独立文件，不修改高德导航 / 天气预报任何既有代码）
 *
 * 用途：计划页「为第 X 天添加酒店」时，按城市 + 关键字实时搜索高德真实住宿 POI，
 * 返回名称 / 地址 / GCJ-02 坐标 / 首张实拍图，供用户挑选加入当天行程。
 *
 * 实现：高德 Web 服务 POI 文本搜索 GET https://restapi.amap.com/v3/place/text
 * 固定 types=100000（住宿服务）+ citylimit 城市限定；任何网络 / 解析失败返回空列表，
 * 由上层 UI 展示「未搜到酒店」，绝不影响行程主流程。
 * ===================================================================================== */

/**
 * 搜索结果中的酒店 POI。
 *
 * @property id 高德 POI 唯一 id
 * @property name 酒店名称
 * @property address 详细地址（区 + 道路门牌）
 * @property lat 纬度（GCJ-02）
 * @property lng 经度（GCJ-02）
 * @property coverUrl 首张实拍图 https 地址；无图为 null（UI 用 emoji 占位）
 */
data class HotelPoi(
    val id: String,
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val coverUrl: String?,
)

/** 酒店搜索仓库顶层接口。 */
interface HotelRepository {

    /**
     * 在 [city] 内搜索酒店。
     *
     * @param keyword 关键字（商圈 / 地标 / 酒店名，如「西湖」「亚朵」）；空白时按「酒店」泛搜
     * @param limit 最多返回条数
     * @return 有坐标的酒店列表（保持高德返回顺序）；失败 / 无结果返回空列表，不抛异常
     */
    suspend fun searchHotels(city: String, keyword: String, limit: Int = 20): List<HotelPoi>
}

/** 基于高德 Web 服务 POI 文本搜索（住宿大类 typecode=100000）的实现。 */
class AmapHotelPoiRepository(
    private val webKey: String = BuildConfig.AMAP_WEB_KEY,
    private val client: OkHttpClient = defaultClient(),
) : HotelRepository {

    private val logger = Logger.getLogger("AmapHotelPoiRepository")

    override suspend fun searchHotels(city: String, keyword: String, limit: Int): List<HotelPoi> =
        withContext(Dispatchers.IO) {
            val cityName = city.trim()
            if (cityName.isEmpty() || webKey.isBlank()) return@withContext emptyList()
            val queryKeyword = keyword.trim().ifEmpty { "酒店" }

            val url = buildString {
                append(POI_TEXT_SEARCH_URL)
                append("?keywords=").append(URLEncoder.encode(queryKeyword, "UTF-8"))
                append("&types=100000")
                append("&city=").append(URLEncoder.encode(cityName, "UTF-8"))
                append("&citylimit=true")
                append("&extensions=all&offset=").append(limit.coerceIn(1, 25))
                append("&page=1&sort=weight&output=JSON")
                append("&key=").append(webKey)
            }

            try {
                client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        logger.warning("酒店搜索 HTTP ${response.code}：$cityName/$queryKeyword")
                        return@withContext emptyList()
                    }
                    parseHotels(response.body?.string().orEmpty())
                }
            } catch (e: Exception) {
                logger.warning("酒店搜索失败（$cityName/$queryKeyword）：${e.message}")
                emptyList()
            }
        }

    /** 解析高德 POI 响应为酒店列表；location 非法 / 缺失坐标的条目直接跳过。 */
    private fun parseHotels(json: String): List<HotelPoi> {
        if (json.isBlank()) return emptyList()
        return try {
            val root = JSONObject(json)
            if (root.optString("status") != "1" || root.optString("infocode") != "10000") {
                return emptyList()
            }
            val pois = root.optJSONArray("pois") ?: return emptyList()
            val result = ArrayList<HotelPoi>()
            for (i in 0 until pois.length()) {
                val poi = pois.optJSONObject(i) ?: continue
                val location = poi.optString("location").orEmpty()
                val parts = location.split(",")
                if (parts.size != 2) continue
                val lng = parts[0].toDoubleOrNull() ?: continue
                val lat = parts[1].toDoubleOrNull() ?: continue
                if (lat == 0.0 && lng == 0.0) continue

                val name = poi.optString("name").trim()
                if (name.isEmpty()) continue

                val address = listOf(
                    poi.optString("adname").orEmpty().trim(),
                    poi.optString("address").orEmpty().trim(),
                ).filter { it.isNotEmpty() }.joinToString("")

                val cover = poi.optJSONArray("photos")?.optJSONObject(0)
                    ?.optString("url").orEmpty().trim()
                    .takeIf { it.startsWith("http") }
                    ?.let { if (it.startsWith("http://")) it.replaceFirst("http://", "https://") else it }

                result.add(
                    HotelPoi(
                        id = poi.optString("id").ifBlank { name },
                        name = name,
                        address = address.ifBlank { "详细地址以到店为准" },
                        lat = lat,
                        lng = lng,
                        coverUrl = cover,
                    ),
                )
            }
            result
        } catch (e: Exception) {
            logger.warning("酒店搜索响应解析失败：${e.message}")
            emptyList()
        }
    }

    companion object {
        private const val POI_TEXT_SEARCH_URL = "https://restapi.amap.com/v3/place/text"

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
