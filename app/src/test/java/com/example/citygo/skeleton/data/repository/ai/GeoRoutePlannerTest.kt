package com.example.citygo.skeleton.data.repository.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GeoRoutePlanner] 就近聚类 / 顺路排序契约测试：
 * 两个相距约 30km 的片区各 3 个游玩点 + 各自 1 个酒店，编排为 2 天时必须：
 * 跨片不混、不丢点、日内为最近邻序、每天恰好 1 个酒店且在末尾、同名酒店去重。
 */
class GeoRoutePlannerTest {

    // 片区 A（约 30.25N,120.15E）与片区 B（约 30.00N,120.30E）相距约 30km
    private val clusterA = listOf(
        GeoItem(1, "A1", 30.250, 120.150, false, null),
        GeoItem(2, "A2", 30.255, 120.155, false, null),
        GeoItem(3, "A3", 30.245, 120.160, false, null),
    )
    private val clusterB = listOf(
        GeoItem(4, "B1", 30.000, 120.300, false, null),
        GeoItem(5, "B2", 30.005, 120.305, false, null),
        GeoItem(6, "B3", 29.995, 120.295, false, null),
    )
    private val hotelA = GeoItem(7, "A 片区酒店", 30.252, 120.152, true, null)
    private val hotelB = GeoItem(8, "B 片区酒店", 30.002, 120.302, true, null)

    @Test
    fun clustersStayPure_routeOrdered_hotelLast() {
        val days = GeoRoutePlanner.plan(clusterA + clusterB + hotelA + hotelB, requestedDays = 2)

        assertEquals(2, days.size)
        val allIds = days.flatMap { it.items.map(GeoItem::id) }.toSet()
        // 不丢点（同名酒店去重场景外，8 个点全保留）
        assertEquals((1L..8L).toSet(), allIds)

        // 找到 A 种子（A1 是首个点，必为种子）所在天
        val dayWithA = days.first { d -> d.items.any { it.id == 1L } }
        val dayWithB = days.first { d -> d !== dayWithA && d.items.any { it.id in 4L..6L } }

        // 跨片纯度 100%
        assertTrue(dayWithA.items.all { it.id in 1L..3L || it.id == 7L })
        assertTrue(dayWithB.items.all { it.id in 4L..6L || it.id == 8L })

        listOf(dayWithA, dayWithB).forEach { day ->
            val spots = day.items.filterNot { it.isHotel }
            assertEquals(3, spots.size)
            // 每天恰好 1 个酒店且固定末尾
            assertEquals(1, day.items.count { it.isHotel })
            assertTrue(day.items.last().isHotel)
            // 最近邻序：每个点到下一点的距离，必须等于它到所有后续点的最小距离
            for (i in 0 until spots.size - 1) {
                val current = spots[i]
                val tail = spots.subList(i + 1, spots.size)
                val actual = GeoRoutePlanner.haversineKm(current, spots[i + 1])
                val expected = tail.minOf { GeoRoutePlanner.haversineKm(current, it) }
                assertEquals(
                    "第 ${day.day} 天顺序不是最近邻序（index=$i）",
                    expected, actual, 1e-6,
                )
            }
        }
    }

    @Test
    fun sameNameHotel_dedupedToOne() {
        val dupHotelA = hotelA.copy(id = 100, name = "A 片区酒店")
        val days = GeoRoutePlanner.plan(clusterA + clusterB + listOf(hotelA, dupHotelA, hotelB), 2)
        val hotels = days.flatMap { it.items }.filter { it.isHotel }
        assertEquals(2, hotels.size)
        assertEquals(hotels.distinctBy { it.name }.size, hotels.size)
    }

    @Test
    fun emptyInput_returnsEmpty() {
        assertTrue(GeoRoutePlanner.plan(emptyList(), 3).isEmpty())
    }
}
