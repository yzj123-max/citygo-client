package com.example.citygo.skeleton.data.remote.poi

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/* =====================================================================================
 * 高德开放平台 · POI 关键词搜索 Web API（v3/place/text）
 *
 * 文档：https://lbs.amap.com/api/webservice/guide/api/search
 * 与天气接口共用同一个 Web 服务 Key（BuildConfig.AMAP_WEB_KEY）与 BASE_URL。
 *
 * 本项目区分两套检索逻辑（同一接口、参数不同）：
 * 1. 城市检索：city 传城市 adCode + citylimit=true，严格限定在该城市内，
 *    用于首页「切换城市 → 加载该城市景点推荐」（见 PoiSpotRepository.loadCitySpots）；
 * 2. 全国景点名检索：city 不传 + citylimit=false，在全国范围按景点名搜索，
 *    用于顶部搜索栏直接搜景点名（见 PoiSpotRepository.searchSpots）。
 *
 * extensions=all 时返回 photos / biz_ext（评分、人均消费）等扩展字段；
 * 注意：免费版 POI 不提供门票、简介、评价正文，这些字段由本地规则 / 预留 RAG 补充。
 * ===================================================================================== */

/** 高德 POI 关键词搜索 Retrofit 接口（城市检索 / 全国检索共用一个方法） */
interface AmapPoiApi {

    /**
     * POI 关键词搜索。
     *
     * @param key Web 服务 Key
     * @param keywords 关键词（多个关键词用 | 分隔，高德按「或」召回）；null 表示不带关键词、
     *                 仅按 [types] 类型召回（城市景点过少时补充小众点位用）
     * @param types POI 类型编码（如 110000=风景名胜；null 表示不限制类型）
     * @param city 城市 adCode（如 440300=深圳）或城市名；null 表示全国范围
     * @param citylimit 是否严格限定在 [city] 范围内（城市检索=true，全国检索=false）
     * @param offset 每页记录数（高德单页上限 25）
     * @param page 页码，从 1 开始
     */
    @GET("v3/place/text")
    suspend fun textSearch(
        @Query("key") key: String,
        @Query("keywords") keywords: String? = null,
        @Query("types") types: String? = null,
        @Query("city") city: String? = null,
        @Query("citylimit") citylimit: Boolean = false,
        @Query("offset") offset: Int = 20,
        @Query("page") page: Int = 1,
        @Query("extensions") extensions: String = "all",
        @Query("output") output: String = "JSON",
    ): PoiTextResponse

    companion object {
        /** 高德 Web 服务基址（与天气接口一致） */
        const val BASE_URL = "https://restapi.amap.com/"

        /** POI 类型：风景名胜（含风景名胜、公园广场等子类，景点推荐主力类型） */
        const val TYPE_SCENIC = "110000"

        /** POI 类型：公园广场（城市休闲小众点位补充召回） */
        const val TYPE_PARK = "110200"
    }
}

/* ----------------------------------- 响应 DTO ----------------------------------- */

/**
 * POI 文本搜索响应。
 *
 * @property status "1"=成功；"0"=失败（具体原因见 [info] / [infocode]）
 * @property count 本次结果总数（字符串，高德历史惯例）
 * @property pois POI 列表；无结果时为空数组
 */
data class PoiTextResponse(
    val status: String? = null,
    val info: String? = null,
    val infocode: String? = null,
    val count: String? = null,
    val pois: List<PoiDto>? = null,
)

/**
 * 单个 POI（extensions=all 返回完整字段）。
 *
 * @property location 坐标，格式为「经度，纬度」字符串，GCJ-02 火星坐标系
 * @property photos 现场照片列表（部分 POI 有，无照片时为 null）
 * @property bizExt 扩展信息（rating=评分，cost=人均消费，免费接口可能为空串）
 */
data class PoiDto(
    val id: String? = null,
    val name: String? = null,
    val type: String? = null,
    @SerializedName("typecode") val typeCode: String? = null,
    val address: String? = null,
    val pname: String? = null,
    val cityname: String? = null,
    val adname: String? = null,
    val location: String? = null,
    val tel: String? = null,
    val photos: List<PoiPhotoDto>? = null,
    @SerializedName("biz_ext") val bizExt: PoiBizExtDto? = null,
)

/**
 * POI 现场照片。
 *
 * 注意：高德 v3 返回的 title/provider 字段在无内容时是空数组 `[]` 而非字符串
 * （如 {"title":[],"url":"http://..."}），这里只声明我们需要的 url（String），
 * title 交由 Gson 忽略；整体解析配合 [PoiJson.safeStringAdapter] 容错。
 */
data class PoiPhotoDto(
    val url: String? = null,
)

/**
 * POI 扩展信息：评分 / 人均消费。
 * 高德在无数据时返回空数组 `[]`（如 {"rating":[],"cost":[]}），
 * 由 [PoiJson.safeStringAdapter] 统一容错为 null。
 */
data class PoiBizExtDto(
    val rating: String? = null,
    val cost: String? = null,
)
