package com.example.citygo.skeleton.data.repository

import com.example.citygo.skeleton.data.enrich.SpotContentEnricher
import com.example.citygo.skeleton.data.local.SpotCacheDao
import com.example.citygo.skeleton.data.local.SpotCacheEntity
import com.example.citygo.skeleton.data.model.HOT_WEATHER_CITIES
import com.example.citygo.skeleton.data.model.ALL_WEATHER_CITY_ENTRIES
import com.example.citygo.skeleton.data.model.ScenicSpot
import com.example.citygo.skeleton.data.model.SpotTag
import com.example.citygo.skeleton.data.remote.poi.AmapPoiApi
import com.example.citygo.skeleton.data.remote.poi.PoiConfig
import com.example.citygo.skeleton.data.remote.poi.PoiDto
import com.example.citygo.skeleton.data.remote.poi.PoiJson
import com.example.citygo.skeleton.data.remote.poi.PoiTextResponse
import com.example.citygo.skeleton.data.remote.poi.isWorthySpot
import com.example.citygo.skeleton.data.remote.poi.toScenicSpot
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/* =====================================================================================
 * 高德 POI 版景点仓库（全国任意城市可加载）
 *
 * 数据链路：
 *   城市切换 / 搜索
 *     → 先读 Room 缓存（未过期直接返回，零网络）
 *     → 未命中则调高德 POI Web API（城市检索 / 全国检索两套逻辑）
 *     → POI 映射为领域模型（坐标 GCJ-02，可直接同步地图标记）
 *     → SpotContentEnricher 补门票 / 攻略 / 避坑文案（本地规则兜底，RAG 预留）
 *     → 按评分排序 + 热门 / 小众混排，取 8-12 条写回缓存
 *
 * 快速连续切换城市时的「取消上一次无效请求」由 ViewModel 层用可取消 Job 控制，
 * Retrofit suspend 请求随协程取消而中断；本仓库只关注单次加载逻辑。
 * ===================================================================================== */

class PoiSpotRepository(
    private val webKey: String,
    private val api: AmapPoiApi,
    private val cacheDao: SpotCacheDao,
    private val enricher: SpotContentEnricher,
) : SpotRepository {

    private val gson = PoiJson.gson
    private val spotListType = object : TypeToken<List<ScenicSpot>>() {}.type

    /**
     * QPS 限流闸：高德个人 Web Key 有每秒调用次数限制（并发 8 请求会触发
     * CUQPS_HAS_EXCEEDED_THE_LIMIT）。所有 POI 请求经此串行化，保证两次调用
     * 至少间隔 [POI_MIN_INTERVAL_MS]，城市切换 / 并发召回都不会超限。
     */
    private val rateGate = Mutex()
    private var lastCallAt = 0L

    /** 进程内景点索引（id → 景点）：详情页秒开，深链兜底时再扫描 Room 缓存 */
    private val spotIndex = ConcurrentHashMap<String, ScenicSpot>()

    /* -------------------------------- 首页城市推荐 -------------------------------- */

    /**
     * 获取推荐景点。
     * - [city] 为 null 或「全部城市」：并发加载 4 个热门城市并混排（首次走网络，之后命中城市缓存）；
     * - 具体城市名：走 [loadCitySpots] 城市检索逻辑。
     */
    override suspend fun getScenicSpots(city: String?): List<ScenicSpot> {
        if (city.isNullOrBlank() || city == CITY_ALL) return loadHotCityMix()
        return loadCitySpots(city)
    }

    /**
     * 城市检索（逻辑一）：Room 缓存 → 高德多关键词 POI 聚合 → 不足补充小众点位。
     */
    private suspend fun loadCitySpots(cityName: String): List<ScenicSpot> {
        val adCode = resolveAdCode(cityName)

        // ① 缓存命中且未过期：直接反序列化返回（二次进入该城市无网络请求）
        cacheDao.getByKey(cityCacheKey(adCode))?.takeIf { it.isFresh(PoiConfig.CITY_CACHE_TTL_MS) }
            ?.let { entity ->
                val cached = entity.toSpotList()
                indexAll(cached)
                return cached
            }

        // ② 缓存未命中：两组关键词经限流闸串行发出（同一 Key 全局 QPS 受限）
        //    a) 正规风景名胜类（大众知名景点）
        //    b) 小众打卡 / 步行街巷 / 特色饭店等休闲点位
        val (scenicResp, nicheResp) = coroutineScope {
            val scenic = async {
                gatedPoiCall {
                    api.textSearch(
                        key = webKey,
                        keywords = KEY_SCENIC,
                        types = AmapPoiApi.TYPE_SCENIC,
                        city = adCode,
                        citylimit = true,
                        offset = 25,
                    )
                }
            }
            val niche = async {
                gatedPoiCall {
                    api.textSearch(
                        key = webKey,
                        keywords = KEY_NICHE,
                        types = null,
                        city = adCode,
                        citylimit = true,
                        offset = 25,
                    )
                }
            }
            scenic.await().ensureSuccess() to niche.await().ensureSuccess()
        }

        val merged = LinkedHashMap<String, ScenicSpot>()
        scenicResp.pois.orEmpty().mapInto(merged, cityName)
        nicheResp.pois.orEmpty().mapInto(merged, cityName)

        // ③ 景点数量过少：补充召回城市公园广场类小众点位
        if (merged.size < PoiConfig.MIN_CITY_POI_COUNT) {
            val parkResp = gatedPoiCall {
                api.textSearch(
                    key = webKey,
                    keywords = null,
                    types = AmapPoiApi.TYPE_PARK,
                    city = adCode,
                    citylimit = true,
                    offset = 20,
                )
            }.ensureSuccess()
            parkResp.pois.orEmpty().mapInto(merged, cityName)
        }

        // ④ 文案补充（门票 / 攻略 / 避坑）→ 评分排序 + 大小众混排 → 取推荐数量
        val enriched = merged.values.map { enricher.enrich(it) }
        val result = rankAndMix(enriched, PoiConfig.recommendLimit)

        // ⑤ 写回 Room（后续进入该城市直接走缓存）并更新内存索引
        if (result.isNotEmpty()) {
            cacheDao.upsert(
                SpotCacheEntity(
                    cacheKey = cityCacheKey(adCode),
                    payloadJson = gson.toJson(result),
                    fetchedAt = System.currentTimeMillis(),
                ),
            )
        }
        indexAll(result)
        // 顺手清理过期缓存（按最长 TTL，成本极低）
        cacheDao.deleteOlderThan(System.currentTimeMillis() - PoiConfig.CITY_CACHE_TTL_MS)
        return result
    }

    /** 「全部城市」：并发取 4 个热门城市（各自命中缓存），全局混排一屏精选 */
    private suspend fun loadHotCityMix(): List<ScenicSpot> {
        val hotNames = HOT_WEATHER_CITIES.take(HOT_MIX_CITY_COUNT).map { it.name }
        // 单个热门城市失败不影响整体（降级用其余城市补齐），异常已由 ViewModel 统一提示
        val all = coroutineScope {
            hotNames.map { name ->
                async {
                    runCatching { loadCitySpots(name) }.getOrDefault(emptyList())
                }
            }.awaitAll()
        }.flatten()
        return rankAndMix(all, PoiConfig.recommendLimit)
    }

    /* -------------------------------- 全国景点名搜索（逻辑二） -------------------------------- */

    /**
     * 全国景点名检索（逻辑二）：不限定城市，按景点名在全国范围匹配。
     * 结果同样进 Room 短期缓存（6h），避免翻页 / 返回时重复请求。
     */
    override suspend fun searchSpots(keyword: String): List<ScenicSpot> {
        val kw = keyword.trim()
        if (kw.isEmpty()) return emptyList()

        cacheDao.getByKey(searchCacheKey(kw))?.takeIf { it.isFresh(PoiConfig.SEARCH_CACHE_TTL_MS) }
            ?.let { entity ->
                val cached = entity.toSpotList()
                indexAll(cached)
                return cached
            }

        // 先按风景名胜类型精确召回；命中过少时放开类型限制再搜一次（兼容老街 / 小店等非景区类目）
        val scenicResp = gatedPoiCall {
            api.textSearch(
                key = webKey,
                keywords = kw,
                types = AmapPoiApi.TYPE_SCENIC,
                city = null,
                citylimit = false,
                offset = 20,
            )
        }.ensureSuccess()

        val merged = LinkedHashMap<String, ScenicSpot>()
        scenicResp.pois.orEmpty().mapInto(merged)

        if (merged.size < NATIONWIDE_RELAX_THRESHOLD) {
            val broadResp = gatedPoiCall {
                api.textSearch(
                    key = webKey,
                    keywords = kw,
                    types = null,
                    city = null,
                    citylimit = false,
                    offset = 20,
                )
            }.ensureSuccess()
            broadResp.pois.orEmpty().mapInto(merged)
        }

        // 注意：Sequence.map 为 crossinline 惰性执行，不能在其中调用 suspend 补充器，
        // 先物化成 List 再 enrich
        val result = merged.values
            .filter { it.name.contains(kw) || kw.length >= 2 } // 二次防护：过短关键词避免纯类型噪声
            .map { enricher.enrich(it) }
            .sortedByDescending { it.score }
            .take(PoiConfig.SEARCH_RESULT_LIMIT)

        if (result.isNotEmpty()) {
            cacheDao.upsert(
                SpotCacheEntity(
                    cacheKey = searchCacheKey(kw),
                    payloadJson = gson.toJson(result),
                    fetchedAt = System.currentTimeMillis(),
                ),
            )
        }
        indexAll(result)
        return result
    }

    /* ----------------------------------- 详情 / 联想 / 城市表 ----------------------------------- */

    /**
     * 按 spotId 取详情：进程内索引 → Room 全量缓存扫描 → null。
     * 高德 v3 无公开的 POI id 单查 REST 接口（v5 detail 需要额外权限，预留），
     * 实际用户路径均从列表 / 搜索结果进入，前两级命中率接近 100%。
     */
    override suspend fun getSpotById(spotId: String): ScenicSpot? {
        spotIndex[spotId]?.let { return it }
        cacheDao.getAll().forEach { entity ->
            val hit = entity.toSpotList().firstOrNull { it.spotId == spotId }
            if (hit != null) {
                spotIndex[hit.spotId] = hit
                return hit
            }
        }
        return null
    }

    /**
     * 搜索联想：从已加载（内存索引）的景点名中做包含匹配。
     * 城市名的双模判断在 ViewModel 层完成，这里只返回景点名，避免语义混淆。
     * 后续可替换为远程 suggest 接口，UI 无需改动。
     */
    override suspend fun getSearchSuggestions(keyword: String): List<String> {
        val kw = keyword.trim()
        if (kw.isEmpty()) return emptyList()
        return spotIndex.values
            .asSequence()
            .filter { it.name.contains(kw) }
            .sortedByDescending { it.score }
            .map { it.name }
            .distinct()
            .take(SUGGEST_LIMIT)
            .toList()
    }

    /** 首页城市下拉数据源：热门城市；全国其余城市通过「搜索其他城市…」覆盖页选择 */
    override suspend fun getCities(): List<String> = HOT_WEATHER_CITIES.map { it.name }

    /* ------------------------------------- 私有工具方法 ------------------------------------- */

    /** 城市名 → adCode（目录精确匹配）；目录缺失时兜底直接传城市名，高德也支持 */
    private fun resolveAdCode(cityName: String): String =
        ALL_WEATHER_CITY_ENTRIES.firstOrNull { it.name == cityName }?.adCode ?: cityName

    /** POI 列表 → 去重合并进目标 Map（按 poi id 去重，城市检索时用于聚合多组关键词结果） */
    private fun List<PoiDto>.mapInto(
        target: LinkedHashMap<String, ScenicSpot>,
        fallbackCity: String? = null,
    ) {
        this.asSequence()
            .filter { it.isWorthySpot() }
            .mapNotNull { dto ->
                // 直辖市 cityname 常为空，用省份 / 调用方城市名兜底
                val city = fallbackCity
                    ?: dto.cityname?.takeIf { it.isNotBlank() }
                    ?: dto.pname?.takeIf { it.isNotBlank() }
                    ?: ""
                dto.toScenicSpot(city)
            }
            .forEach { spot -> target.putIfAbsent(spot.spotId, spot) }
    }

    /**
     * 评分排序 + 热门 / 小众混排：
     * 两组各自按评分降序（无评分排末尾），再交替取 H、N、H、N…，保证两类都有曝光；
     * 一组取空后用另一组补齐，最终截断到 [limit]（首页推荐 8-12 个）。
     */
    private fun rankAndMix(spots: List<ScenicSpot>, limit: Int): List<ScenicSpot> {
        val scored: (ScenicSpot) -> Double = { if (it.score > 0.0) it.score else -1.0 }
        val hot = spots.filter { it.tag == SpotTag.HOT }.sortedByDescending(scored)
        val niche = spots.filter { it.tag == SpotTag.NICHE }.sortedByDescending(scored)

        val mixed = ArrayList<ScenicSpot>(minOf(limit, hot.size + niche.size))
        var hi = 0
        var ni = 0
        var takeHot = true
        while (mixed.size < limit && (hi < hot.size || ni < niche.size)) {
            if (takeHot && hi < hot.size) {
                mixed.add(hot[hi++])
            } else if (!takeHot && ni < niche.size) {
                mixed.add(niche[ni++])
            }
            takeHot = !takeHot
        }
        return mixed
    }

    private fun indexAll(spots: List<ScenicSpot>) {
        spots.forEach { spot -> spotIndex.putIfAbsent(spot.spotId, spot) }
    }

    private fun cityCacheKey(adCode: String) = "city:$adCode"
    private fun searchCacheKey(keyword: String) = "search:$keyword"

    /** 缓存记录是否仍在 [ttlMs] 有效期内 */
    private fun SpotCacheEntity.isFresh(ttlMs: Long): Boolean =
        System.currentTimeMillis() - fetchedAt < ttlMs

    /** 缓存记录 JSON → 景点列表（数据损坏时视为空，触发重新请求） */
    private fun SpotCacheEntity.toSpotList(): List<ScenicSpot> =
        runCatching {
            gson.fromJson<List<ScenicSpot>>(payloadJson, spotListType).orEmpty()
        }.getOrDefault(emptyList())

    /**
     * 经全局 QPS 闸执行 POI 请求：所有调用串行排队，且两次请求起始间隔不短于
     * [POI_MIN_INTERVAL_MS]，规避高德个人 Key 的 CUQPS_HAS_EXCEEDED_THE_LIMIT。
     * 网络等待期间持锁是有意为之——限流统计的是请求发起节奏。
     */
    private suspend fun <T> gatedPoiCall(block: suspend () -> T): T = rateGate.withLock {
        val elapsed = System.currentTimeMillis() - lastCallAt
        if (elapsed < POI_MIN_INTERVAL_MS) delay(POI_MIN_INTERVAL_MS - elapsed)
        try {
            block()
        } finally {
            lastCallAt = System.currentTimeMillis()
        }
    }

    /** 高德响应状态校验：status 非 "1" 直接抛错，由 ViewModel 进入失败重试 UI */
    private fun PoiTextResponse.ensureSuccess(): PoiTextResponse {
        if (status != "1") {
            throw IOException("景点服务暂不可用（${info ?: infocode ?: "请求失败"}）")
        }
        return this
    }

    private companion object {
        /**
         * POI 请求最小间隔（毫秒）。个人 Web Key QPS 上限约 3，
         * 350ms ≈ 2.8 QPS，留出天气接口等余量。
         */
        const val POI_MIN_INTERVAL_MS = 350L

        /** 大众景点召回关键词 */
        const val KEY_SCENIC = "景点|风景区|名胜古迹|旅游景点"

        /** 小众 / 探店召回关键词（打卡地 + 特色饭店 + 文旅街区点位） */
        const val KEY_NICHE = "网红打卡|步行街|老街|夜市|美食街|文创园|艺术区|特色饭店|老字号|特色美食"

        /** 全国搜索首次命中数低于该值时，放开 POI 类型限制补充检索 */
        const val NATIONWIDE_RELAX_THRESHOLD = 5

        /** 「全部城市」混排的热门城市数量 */
        const val HOT_MIX_CITY_COUNT = 4

        const val SUGGEST_LIMIT = 6
    }
}
