package com.example.citygo.skeleton.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 全国城市目录与拼音索引的兜底测试：
 * - 目录中每个汉字都必须有拼音（缺失会降级为原字符，可被检出）；
 * - 典型全拼 / 首字母 / 省份拼音 / adCode 搜索结果正确。
 */
class CityPinyinCoverageTest {

    /** 汉字字典缺失时拼音变体会保留原汉字，借此检查覆盖率 */
    @Test
    fun everyCatalogCharHasPinyin() {
        val chars = ALL_WEATHER_CITY_ENTRIES
            .flatMap { (it.name + it.province).toList() }
            .toSet()
        val missing = chars.filter { ch ->
            pinyinFullVariants(ch.toString()).any { variant ->
                variant.any { c -> c.code > 127 }
            }
        }
        assertTrue("以下汉字缺少拼音，请补入 CityPinyin.kt：$missing", missing.isEmpty())
    }

    @Test
    fun fullPinyinSearch() {
        // 苏州 / 宿州 拼音都是 suzhou，应同时命中且苏州（adCode 小）在前
        assertEquals(listOf("苏州", "宿州"), searchWeatherCities("suzhou").take(2).map { it.name })
        // 多音字城市
        assertEquals("长沙", searchWeatherCities("changsha").first().name)
        assertEquals("重庆", searchWeatherCities("chongqing").first().name)
        assertEquals("厦门", searchWeatherCities("xiamen").first().name)
        assertEquals("百色", searchWeatherCities("baise").first().name)
        // ü → v
        assertEquals("吕梁", searchWeatherCities("lvliang").first().name)
        // 多字读音展开：鄂尔多斯、喀什（什 shen/shi 两种）
        assertEquals("鄂尔多斯", searchWeatherCities("eerduosi").first().name)
        assertEquals("喀什", searchWeatherCities("kashen").first().name)
        assertEquals("喀什", searchWeatherCities("kashi").first().name)
    }

    @Test
    fun initialsSearch() {
        // cs 仅命中长沙（昌吉 cj、楚雄 cx、长春 cc 等不应混入）
        assertEquals("长沙", searchWeatherCities("cs").first().name)
        // sz 命中：深圳 / 苏州 / 宿州 / 随州
        val sz = searchWeatherCities("sz").map { it.name }.toSet()
        assertTrue(sz.toString(), setOf("深圳", "苏州", "宿州", "随州").all { it in sz })
    }

    @Test
    fun provincePinyinSearch() {
        // yunnan 前缀匹配云南省全部 16 个地级行政区
        val yn = searchWeatherCities("yunnan")
        assertEquals(16, yn.size)
        assertTrue(yn.all { it.province == "云南省" })

        // neimenggu 前缀匹配内蒙古全部 12 个地级行政区
        val nm = searchWeatherCities("neimenggu")
        assertEquals(12, nm.size)
        assertTrue(nm.all { it.province == "内蒙古自治区" })

        // guangxi 命中广西全部 14 个地级市
        val gx = searchWeatherCities("guangxi")
        assertEquals(14, gx.size)
        assertTrue(gx.all { it.province == "广西壮族自治区" })
    }

    @Test
    fun adCodeSearch() {
        val result = searchWeatherCities("3205")
        assertEquals(1, result.size)
        assertEquals("苏州", result.first().name)
    }

    @Test
    fun emptyKeywordReturnsHotCities() {
        assertEquals(HOT_WEATHER_CITIES.size, searchWeatherCities("   ").size)
    }
}
