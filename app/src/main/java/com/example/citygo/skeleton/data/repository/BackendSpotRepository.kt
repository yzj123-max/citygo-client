package com.example.citygo.skeleton.data.repository

import com.example.citygo.skeleton.data.model.*
import com.example.citygo.skeleton.data.remote.AppRetrofit
import com.example.citygo.skeleton.data.remote.attraction.*
import com.example.citygo.skeleton.data.remote.auth.AuthHttpClient
import com.google.gson.Gson
import retrofit2.Response
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class BackendSpotPage(val spots: List<ScenicSpot>, val hasMore: Boolean,
    val total: Long? = null, val source: String = "LOCAL", val cityQuery: String? = null, val nextPage: Int? = null)
class AttractionRequestException(val code: Int, message: String) : IllegalStateException(message)

fun <T> Response<AttractionResponse<T>>.attractionData(): T {
    if (!isSuccessful) {
        val failure = runCatching { Gson().fromJson(errorBody()?.string(), AttractionResponse::class.java) }.getOrNull()
        throw AttractionRequestException(code(), failure?.msg ?: "服务请求失败（${code()}），请重试")
    }
    val body = body() ?: throw AttractionRequestException(code(), "服务没有返回数据")
    if (body.code != 200) throw AttractionRequestException(body.code, body.msg ?: "操作失败，请重试")
    return body.data ?: throw AttractionRequestException(body.code, "服务返回内容为空")
}

/** 推荐模块专用超时：保留鉴权拦截器，不修改登录客户端和全局服务地址。 */
object AttractionBackend {
    private val client by lazy { AuthHttpClient.client.newBuilder()
        .readTimeout(25, TimeUnit.SECONDS).callTimeout(35, TimeUnit.SECONDS).build() }
    fun api(): AttractionApi = AppRetrofit.create(AttractionApi::class.java, client)
}

class BackendSpotRepository(private val apiFactory: () -> AttractionApi = { AttractionBackend.api() }) : SpotRepository {
    private val api get() = apiFactory()
    private var recommendationCache: Pair<String, List<ScenicSpot>>? = null
    override suspend fun getScenicSpots(city: String?) = recommendations(city).spots
    override suspend fun getSpotById(spotId: String): ScenicSpot {
        val id = localId(spotId)
        return AttractionDisplayMapper.spot(api.detail(id).attractionData())
    }
    suspend fun localId(spotId: String): Long {
        spotId.toLongOrNull()?.takeIf { it > 0 }?.let { return it }
        require(spotId.startsWith("poi_")) { "这是旧版景点记录，请重新搜索后打开" }
        val poi = spotId.removePrefix("poi_")
        require(poi.matches(Regex("[A-Za-z0-9]{1,64}"))) { "景点标识无效" }
        val resolved = api.resolve(AttractionResolveRequest(poi)).attractionData()
        require(resolved.attractionId > 0) { "服务未返回有效的景点编号" }
        return resolved.attractionId
    }
    override suspend fun searchSpots(keyword: String) = searchPage(keyword).spots
    override suspend fun getSearchSuggestions(keyword: String): List<String> = emptyList()
    override suspend fun getCities() = ALL_WEATHER_CITY_ENTRIES.map { it.name }.distinct()

    suspend fun recommendations(city: String?, page: Int = 1, category: String? = null,
        ticket: String? = null, pet: String? = null, source: String? = null,
        cityQuery: String? = null): BackendSpotPage {
        val chosen = city?.takeUnless { it == CITY_ALL || it.isBlank() }

        var query = cityQuery ?: chosen
        val cacheKey = listOf(chosen, category, ticket, pet).joinToString("|")
        if (page > 1 && recommendationCache?.first == cacheKey) return cachedRecommendations(page, query)
        var result = api.list(query, category = category, ticket = ticket, pet = pet, size = 50).attractionData()
        // 兼容本地目录“贵阳”与数据库“贵阳市”的命名差异，不修改后端记录。
        if (source == null && page == 1 && result.total == 0L && chosen != null && !chosen.endsWith("市")) {
            val alternate = "${chosen}市"
            val alternateResult = api.list(alternate, category = category, ticket = ticket, pet = pet, size = 50).attractionData()
            if ((alternateResult.total ?: 0) > 0) { query = alternate; result = alternateResult }
        }
        val pool = result.records.toMutableList()
        var remotePage = 1
        while (result.hasMore && remotePage < 10) {
            remotePage++
            result = api.list(query, category = category, ticket = ticket, pet = pet, page = remotePage, size = 50).attractionData()
            pool.addAll(result.records)
        }
        val limit = Semaphore(4)
        val candidates = AttractionRecommendationPolicy.select(pool).filter { !it.coverUrl.isNullOrBlank() && it.attractionId != null }
        val complete = coroutineScope {
            candidates.map { card -> async {
                limit.withPermit {
                    try {
                        AttractionDisplayMapper.spot(api.detail(card.attractionId!!).attractionData()).takeIf(AttractionRecommendationPolicy::complete)
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { null }
                }
            } }.awaitAll().filterNotNull()
        }
        recommendationCache = cacheKey to complete
        return cachedRecommendations(page, query)
    }
    private fun cachedRecommendations(page: Int, query: String?): BackendSpotPage {
        val pool = recommendationCache?.second.orEmpty()
        val start = ((page - 1) * 20).coerceAtMost(pool.size)
        val end = (start + 20).coerceAtMost(pool.size)
        return BackendSpotPage(pool.subList(start, end), end < pool.size,
            pool.size.toLong(), "LOCAL", query)
    }
    suspend fun findPhotos(spot: ScenicSpot): ScenicSpot {
        if (spot.coverImg.isNotBlank()) return spot
        val keyword = AttractionRecommendationPolicy.photoKeyword(spot.name).take(80)
        val result = api.search(keyword, spot.city.takeIf(String::isNotBlank), size = 20).attractionData()
        val candidate = result.records.firstOrNull { AttractionRecommendationPolicy.samePlace(spot, it) && !it.coverUrl.isNullOrBlank() }
            ?: return spot
        val url = candidate.coverUrl!!
        return spot.copy(coverImg = url, imgUrls = spot.imgUrls.ifEmpty { listOf(url) })
    }
    suspend fun searchPage(keyword: String, page: Int = 1, city: String? = null): BackendSpotPage {
        val result = api.search(keyword, city, page = page).attractionData()
        return BackendSpotPage(result.records.map(AttractionDisplayMapper::spot), result.hasMore, source = "SEARCH")
    }
    suspend fun nearby(longitude: Double, latitude: Double, radius: Int = 20000, page: Int = 1): BackendSpotPage {
        require(AttractionDisplayMapper.validCoordinates(longitude, latitude, "GCJ02")) { "当前位置无效，请重新定位" }
        // 附近推荐使用与城市推荐相同的完整资料库，不再把原始POI直接当作精选。
        recommendations(CITY_ALL, 1)
        fun distance(spot: ScenicSpot): Long {
            val a1 = Math.toRadians(latitude); val a2 = Math.toRadians(spot.lat)
            val da = a2 - a1; val dl = Math.toRadians(spot.lng - longitude)
            val h = kotlin.math.sin(da / 2).let { it * it } + kotlin.math.cos(a1) * kotlin.math.cos(a2) * kotlin.math.sin(dl / 2).let { it * it }
            return (6371000.0 * 2 * kotlin.math.asin(kotlin.math.sqrt(h.coerceIn(0.0, 1.0)))).toLong()
        }
        val spots = recommendationCache?.second.orEmpty().map { it.copy(distanceMeters = distance(it)) }
            .filter { it.distanceMeters!! <= radius }.sortedBy { it.distanceMeters }
        val start = ((page - 1) * 20).coerceAtMost(spots.size)
        val end = (start + 20).coerceAtMost(spots.size)
        return BackendSpotPage(spots.subList(start, end), end < spots.size, spots.size.toLong(), "NEARBY")
    }
    suspend fun navigation(spotId: String, mode: String): AttractionNavigationDto {
        require(mode == "WALKING" || mode == "DRIVING") { "请选择步行或驾车" }
        val result = api.navigation(localId(spotId), mode).attractionData()
        require(AttractionDisplayMapper.validCoordinates(result.longitude, result.latitude, result.coordSystem)) { "服务未返回可用导航坐标" }
        require(result.travelMode == mode) { "导航目的地与出行方式不一致，请重试" }
        return result
    }
}

/** 纯数据映射，不用规则或评分模板推断门票、宠物政策。 */
object AttractionDisplayMapper {
    fun validCoordinates(longitude: Double?, latitude: Double?, system: String?): Boolean =
        system == "GCJ02" && longitude != null && latitude != null && longitude.isFinite() && latitude.isFinite() &&
            longitude in -180.0..180.0 && latitude in -90.0..90.0 && !(longitude == 0.0 && latitude == 0.0)

    fun policyValue(policy: AttractionPolicyDto?): String {
        if (policy == null || policy.verifiedAt.isNullOrBlank() || policy.reviewRequired ||
            policy.temporalStatus in setOf("UPCOMING", "EXPIRED")) return "UNKNOWN"
        return when (policy.policyType) {
            "TICKET" -> policy.policyValue.takeIf { it in setOf("FREE", "PAID", "PARTIAL") }
            "PET" -> policy.policyValue.takeIf { it in setOf("ALLOWED", "CONDITIONAL", "NOT_ALLOWED") }
            "OPENING_HOURS" -> policy.policyValue.takeIf { it in setOf("SCHEDULED", "TEMPORARILY_CLOSED") }
            "RESERVATION" -> policy.policyValue.takeIf { it in setOf("REQUIRED", "OPTIONAL", "NOT_REQUIRED") }
            else -> null
        } ?: "UNKNOWN"
    }
    fun spot(dto: AttractionDto): ScenicSpot {
        val id = dto.attractionId?.takeIf { it > 0 }?.toString()
            ?: dto.amapPoiId?.takeIf { it.isNotBlank() }?.let { "poi_$it" }
            ?: throw IllegalStateException("景点资料缺少有效标识")
        val policies = dto.policies.orEmpty()
        val ticket = if (dto.policies != null) policyValue(policies.firstOrNull { it.policyType == "TICKET" }) else dto.ticketStatus ?: "UNKNOWN"
        val pet = if (dto.policies != null) policyValue(policies.firstOrNull { it.policyType == "PET" }) else dto.petPolicy ?: "UNKNOWN"
        val photos = dto.images.orEmpty().mapNotNull { it.url?.takeIf(String::isNotBlank) }
        val cover = dto.coverUrl?.takeIf(String::isNotBlank) ?: photos.firstOrNull().orEmpty()
        val coordinates = validCoordinates(dto.longitude, dto.latitude, dto.coordSystem)
        return ScenicSpot(spotId = id, city = dto.city.orEmpty(), name = dto.name ?: "景点",
            tag = SpotTag.HOT, coverImg = cover, score = 0.0, reviewCount = 0,
            shortDesc = dto.summary.orEmpty(), imgUrls = photos.ifEmpty { listOfNotNull(cover.takeIf(String::isNotBlank)) },
            ticketInfo = policies.firstOrNull { it.policyType == "TICKET" }?.content ?: "门票信息待核实",
            freePolicy = "", ticketTip = "", duration = dto.suggestedDuration.orEmpty(),
            bestSeason = dto.bestSeason.orEmpty(), traffic = dto.trafficTips.orEmpty(),
            pitTips = listOfNotNull(dto.visitTips?.takeIf(String::isNotBlank)), userReviews = emptyList(),
            lat = dto.latitude?.takeIf { coordinates } ?: 0.0, lng = dto.longitude?.takeIf { coordinates } ?: 0.0,
            backendId = dto.attractionId, amapPoiId = dto.amapPoiId, address = dto.address.orEmpty(),
            phone = dto.phone.orEmpty(), description = dto.description.orEmpty(), highlights = dto.highlights.orEmpty(),
            descriptionSourceName = dto.descriptionSourceName, descriptionSourceUrl = dto.descriptionSourceUrl,
            descriptionVerifiedAt = dto.descriptionVerifiedAt, policies = policies,
            ticketStatus = ticket, petPolicy = pet, distanceMeters = dto.distanceMeters,
            coordinateAvailable = coordinates, canNavigate = dto.canNavigate || coordinates, backendData = true,
            level = dto.level.orEmpty(), collectedInfo = dto.collectedInfo)
    }
}


