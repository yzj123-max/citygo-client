package com.example.citygo.skeleton.data.repository

import com.example.citygo.skeleton.data.model.AmapForecastWeather
import com.example.citygo.skeleton.data.model.AmapLiveWeather
import com.example.citygo.skeleton.data.model.AmapWeatherCast
import com.example.citygo.skeleton.data.model.AmapWeatherResponse
import com.example.citygo.skeleton.data.model.LiveWeather
import com.example.citygo.skeleton.data.model.WeatherCity
import com.example.citygo.skeleton.data.model.WeatherForecast
import com.example.citygo.skeleton.data.model.WeatherOverview

/* =====================================================================================
 * 天气数据仓库（高德 weatherInfo 接口封装）
 *
 * UI（出行计划页天气预报子页）→ WeatherViewModel → 本接口；
 * 线上装配 [RemoteWeatherRepository]（Retrofit 真实请求）；
 * [MockWeatherRepository] 保留为离线 / 无 Key 时的后备实现。
 * DTO → 领域模型映射统一在 WeatherMappings.kt，两套实现共用。
 * ===================================================================================== */

/**
 * 天气仓库接口：封装高德实时天气与多日预报请求。
 */
interface WeatherRepository {

    /**
     * 获取未来多天预报（对应高德 extensions=all）。
     *
     * @param city 查询城市（携带 adCode 行政区划编码）
     */
    suspend fun getWeatherForecast(city: WeatherCity): WeatherForecast

    /**
     * 获取实时天气（对应高德 extensions=base）；无数据时返回 null。
     */
    suspend fun getLiveWeather(city: WeatherCity): LiveWeather?

    /**
     * 获取天气页全量数据（头部实时 + 7 日预报 + 空气 / 风力 / 紫外 / 月相 / 生活提示等）。
     *
     * 当前 Mock 实现返回调试 JSON 结构数据；接入真实天气服务后在实现类内替换，
     * UI 与 ViewModel 不感知数据来源。
     */
    suspend fun getWeatherOverview(city: WeatherCity): WeatherOverview
}

/**
 * 天气仓库 Mock 实现：构造高德响应结构的假数据，再映射为 UI 领域模型。
 */
class MockWeatherRepository : WeatherRepository {

    override suspend fun getWeatherForecast(city: WeatherCity): WeatherForecast {
        val response = mockAmapWeatherResponse(city)
        val amapForecast = response.forecasts.firstOrNull()
            ?: return WeatherForecast(city = city, reportTime = "", dailyForecasts = emptyList())
        return amapForecast.toDomain(city)
    }

    override suspend fun getLiveWeather(city: WeatherCity): LiveWeather? {
        val response = mockAmapWeatherResponse(city)
        return response.lives.firstOrNull()?.toDomain()
    }

    /** 全量天气页数据：直接返回调试 JSON 结构的模拟数据（周几/日期动态推算） */
    override suspend fun getWeatherOverview(city: WeatherCity): WeatherOverview =
        buildMockOverview(city)

    /* ------------------------------ Mock 高德原始响应 ------------------------------ */

    /** 构造一份城市 7 天预报 + 实时天气的高德风格响应（字段与高德 JSON 对齐） */
    private fun mockAmapWeatherResponse(city: WeatherCity): AmapWeatherResponse =
        AmapWeatherResponse(
            status = "1",
            info = "OK",
            infocode = "10000",
            count = "1",
            lives = listOf(
                AmapLiveWeather(
                    province = "四川",
                    city = city.name,
                    adcode = city.adCode,
                    weather = "多云转晴",
                    temperature = "27",
                    winddirection = "东北",
                    windpower = "2",
                    humidity = "62",
                    reporttime = "2026-09-21 08:00:00",
                ),
            ),
            forecasts = listOf(
                AmapForecastWeather(
                    city = city.name,
                    adcode = city.adCode,
                    province = "四川",
                    reporttime = "2026-09-21 08:00:00",
                    casts = listOf(
                        // week：1=周一 … 7=周日；温度 / 风力白天取 day* 字段
                        AmapWeatherCast("2026-09-21", "1", "多云转晴", "晴", "27", "19", "东北风", "东北风", "2", "1"),
                        AmapWeatherCast("2026-09-22", "2", "晴", "晴", "29", "20", "东风", "东风", "2", "2"),
                        AmapWeatherCast("2026-09-23", "3", "晴", "晴", "30", "21", "南风", "南风", "1", "1"),
                        AmapWeatherCast("2026-09-24", "4", "阴", "阴", "26", "20", "北风", "北风", "3", "3"),
                        AmapWeatherCast("2026-09-25", "5", "小雨", "中雨", "23", "18", "西北风", "西北风", "3", "2"),
                        AmapWeatherCast("2026-09-26", "6", "阴转多云", "阴", "25", "19", "东北风", "东北风", "2", "2"),
                        AmapWeatherCast("2026-09-27", "7", "多云", "多云", "27", "20", "东风", "东风", "2", "2"),
                    ),
                ),
            ),
        )
}
