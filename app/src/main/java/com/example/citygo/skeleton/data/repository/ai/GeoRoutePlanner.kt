package com.example.citygo.skeleton.data.repository.ai

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/* =====================================================================================
 * 地理就近聚类 + 顺路排序（纯 Kotlin 工具，不依赖 Android / 高德 SDK，可直接 JVM 单测）
 *
 * 背景：大模型多日攻略即使在 Prompt 中被要求「同区县、顺路」，仍可能把相距很远的点位
 * 排进同一天。本工具在行程入库前做一次**确定性**的二次编排：
 *
 * 1. 选种子：优先采用模型每天给的第一个点位作为各天「片区种子」，不足时用最远点遍历
 *    （farthest-first）补满，使不同天天然落在城市不同片区；
 * 2. 分配：非酒店点位按「离哪个种子最近」归入对应天，带容量上限保证天数与均衡，
 *    从「最确定归属」（到最近种子距离最小）的点开始处理；
 * 3. 酒店：同名去重，每店只挂一天；挂到最近的种子所在天（模型 day 提示且足够近时尊重），
 *    每天至多保留 1 个酒店，酒店固定排在当天最后；
 * 4. 排序：每天以种子为起点做最近邻路径（greedy nearest-neighbor），保证游览顺路不折返。
 *
 * 坐标按 GCJ-02 经纬度做 Haversine 距离，公里级精度足够支撑「同区县 / 跨片区」判断。
 * ===================================================================================== */

/**
 * 参与聚类编排的单个点位。
 *
 * @property id 业务点位 id（仅用于结果回溯）
 * @property name 名称（同名酒店去重用）
 * @property lat 纬度
 * @property lng 经度
 * @property isHotel 是否酒店（酒店参与挂天但不参与景点聚类种子 / 容量分配）
 * @property hintDay 模型建议的天数（1 起）；null 表示模型未指定
 */
data class GeoItem(
    val id: Long,
    val name: String,
    val lat: Double,
    val lng: Double,
    val isHotel: Boolean,
    val hintDay: Int?,
)

/** 一天的编排结果：[day] 从 1 起，[items] 已按顺路顺序排列（酒店在末尾）。 */
data class GeoDayPlan(
    val day: Int,
    val items: List<GeoItem>,
)

object GeoRoutePlanner {

    /** 地球平均半径（公里） */
    private const val EARTH_RADIUS_KM = 6371.0088

    /**
     * 把全部点位编排为 [requestedDays] 天（实际天数 = min(requestedDays, 非酒店点位数)）。
     *
     * 契约：
     * - 非酒店点位一个不丢；每个结果天至少含 1 个非酒店点位（即种子）；
     * - 同一天酒店至多 1 个（模型异常多给时，多余酒店按就近 + 最少占用原则兜底放入）；
     * - 同名酒店视为连住同一家，只保留 1 个；
     * - 每天 items：非酒店点（种子起、最近邻序） + 0/1 个酒店（末尾）。
     */
    fun plan(items: List<GeoItem>, requestedDays: Int): List<GeoDayPlan> {
        if (items.isEmpty()) return emptyList()

        // 酒店同名去重（模型多天推荐同一家酒店连住时只保留一处）
        val hotels = items.filter { it.isHotel }.distinctBy { it.name.trim() }
        val spots = items.filterNot { it.isHotel }

        // 没有非酒店点位属于异常数据：退化把全部内容塞给第 1 天，不丢点
        if (spots.isEmpty()) {
            return listOf(GeoDayPlan(1, spots.ifEmpty { hotels }))
        }

        val k = requestedDays.coerceIn(1, spots.size)

        // 1. 选种子：模型每日首个点位 → 最远点遍历补齐
        val seeds = pickSeeds(spots, k)
        val buckets = Array(k) { mutableListOf(seeds[it]) }
        val seededIds = seeds.map { it.id }.toHashSet()

        // 2. 非酒店点位分配：按到最近种子的距离升序，归入最近且未满的桶
        //    容量 = 向上取整(n/k) + 1，既防止个别天无限膨胀，又保证所有点放得下
        val capacity = spots.size / k + 1 + 1
        spots.asSequence()
            .filterNot { it.id in seededIds }
            .sortedBy { item -> seeds.minOf { seed -> haversineKm(item, seed) } }
            .forEach { item ->
                val target = seeds.indices
                    .filter { buckets[it].size < capacity }
                    .minByOrNull { dayIdx -> haversineKm(item, seeds[dayIdx]) }
                    ?: seeds.indices.minByOrNull { buckets[it].size }
                    ?: 0
                buckets[target].add(item)
            }

        // 3. 酒店挂天：距离最近优先处理，优先放入没有酒店的最近桶
        val orderedHotels = hotels.sortedBy { hotel ->
            seeds.minOf { seed -> haversineKm(hotel, seed) }
        }
        val hotelByDay = HashMap<Int, GeoItem>()
        orderedHotels.forEach { hotel ->
            // 模型提示天：足够近（最近距离的 1.3 倍且多 2km 以内）时尊重模型
            val hinted = hotel.hintDay?.takeIf { it in 1..k }?.let { it - 1 }
            val nearest = seeds.indices.minByOrNull { haversineKm(hotel, seeds[it]) } ?: 0
            val nearestDist = haversineKm(hotel, seeds[nearest])
            val respectHint = hinted != null &&
                hotelByDay[hinted] == null &&
                haversineKm(hotel, seeds[hinted]) <= maxOf(nearestDist * 1.3, nearestDist + 2.0)
            val preferred = if (respectHint) hinted!! else nearest

            val target = when {
                hotelByDay[preferred] == null -> preferred
                else -> seeds.indices
                    .filter { hotelByDay[it] == null }
                    .minByOrNull { haversineKm(hotel, seeds[it]) }
                    ?: preferred // 每天都已有酒店（模型异常）：放回偏好桶，保证不丢酒店
            }
            if (hotelByDay[target] == null) hotelByDay[target] = hotel
        }

        // 4. 每天内部：种子起最近邻排序，酒店固定末尾
        return (0 until k).map { dayIdx ->
            val route = nearestNeighborOrder(seeds[dayIdx], buckets[dayIdx])
            val ordered = hotelByDay[dayIdx]?.let { route + it } ?: route
            GeoDayPlan(day = dayIdx + 1, items = ordered)
        }
    }

    /**
     * 选片区种子：先取模型每个 hintDay 的第一个非酒店点位（hint 天按种子到既有种子的
     * 最远点遍历补齐到 [k] 个，保证种子彼此尽量分散（对应城市不同片区）。
     */
    private fun pickSeeds(spots: List<GeoItem>, k: Int): List<GeoItem> {
        val seeds = mutableListOf<GeoItem>()
        val usedIds = HashSet<Long>()

        // 模型明确建议了天的，取每天出现的第一个点位代表该天片区
        for (day in 1..k) {
            val seed = spots.firstOrNull { it.hintDay == day && it.id !in usedIds } ?: continue
            seeds.add(seed)
            usedIds.add(seed.id)
        }

        // 不足 k 个：farthest-first —— 首次无种子时先取列表首点，之后每次选离已选种子集合最远的点
        val candidates = spots.filterNot { it.id in usedIds }
        if (seeds.isEmpty()) {
            candidates.firstOrNull()?.let {
                seeds.add(it)
                usedIds.add(it.id)
            }
        }
        while (seeds.size < k) {
            val next = candidates
                .filterNot { it.id in usedIds }
                .maxByOrNull { candidate -> seeds.minOf { haversineKm(candidate, it) } }
                ?: break
            seeds.add(next)
            usedIds.add(next.id)
        }
        return seeds
    }

    /**
     * 从 [start]（种子）出发的最近邻路径：每一步从剩余点中选离当前点最近的；
     * 返回序列第 0 个即种子。距离相同取原顺序靠前的，保证结果稳定。
     */
    private fun nearestNeighborOrder(start: GeoItem, dayItems: List<GeoItem>): List<GeoItem> {
        if (dayItems.size <= 1) return dayItems
        val remaining = dayItems.filterNot { it.id == start.id }.toMutableList()
        val route = mutableListOf(start)
        var current = start
        while (remaining.isNotEmpty()) {
            val nextIndex = remaining.indices.minBy { idx ->
                val d = haversineKm(current, remaining[idx])
                // 距离放大 1e6 后叠加原下标作为确定性 tie-breaker
                (d * 1_000_000).roundToInt().toDouble() + idx * 1e-9
            }
            current = remaining.removeAt(nextIndex)
            route.add(current)
        }
        return route
    }

    /** Haversine 大圆距离（公里）；两点经纬度相同时为 0。 */
    fun haversineKm(a: GeoItem, b: GeoItem): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val h = sin(dLat / 2).pow(2.0) +
            cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2.0)
        return 2 * EARTH_RADIUS_KM * atan2(sqrt(h), sqrt(1 - h))
    }
}
