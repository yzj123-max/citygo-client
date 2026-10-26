package com.example.citygo.skeleton.data.repository

import com.example.citygo.skeleton.data.model.LiveWeather
import com.example.citygo.skeleton.data.model.WeatherCity
import com.example.citygo.skeleton.data.model.WeatherForecast
import com.example.citygo.skeleton.data.model.WeatherOverview
import com.example.citygo.skeleton.data.remote.AmapWeatherApi

/**
 * 天气仓库真实实现：Retrofit 请求高德 Web 服务 weatherInfo，
 * Gson 直接解析 AmapWeatherResponse（DTO 字段名与高德 JSON 一致）。
 *
 * @param webKey 高德「Web服务」类型 Key（BuildConfig.AMAP_WEB_KEY）
 * @param api Retrofit 服务接口
 */
class RemoteWeatherRepository(
    private val webKey: String,
    private val api: AmapWeatherApi,
) : WeatherRepository {

    override suspend fun getWeatherForecast(city: WeatherCity): WeatherForecast {
        val response = request(city, AmapWeatherApi.EXTENSIONS_ALL)
        val amapForecast = response.forecasts.firstOrNull()
            ?: return WeatherForecast(city = city, reportTime = "", dailyForecasts = emptyList())
        return amapForecast.toDomain(city)
    }

    override suspend fun getLiveWeather(city: WeatherCity): LiveWeather? {
        val response = request(city, AmapWeatherApi.EXTENSIONS_BASE)
        return response.lives.firstOrNull()?.toDomain()
    }

    /**
     * 全量天气页数据：
     * - 实时天气 / 7 日温度与天气现象走高德 weatherInfo 真实接口；
     * - 空气 / 紫外 / 月相 / 能见度 / 降水概率等扩展指标高德免费接口不提供，
     *   暂沿用模拟模板（TODO：后续接入专用气象服务后在此处整体替换为真实响应映射）。
     * 任一步骤网络失败直接抛异常，由 ViewModel 展示失败重试页。
     */
    override suspend fun getWeatherOverview(city: WeatherCity): WeatherOverview {
        val mock = buildMockOverview(city)
        val live = getLiveWeather(city)
        val forecast = getWeatherForecast(city)

        // 用高德真实预报覆盖模拟 7 日条目的温度 / 天气 / 风力（降雨概率、紫外指数高德缺失，保留模拟值）
        val mergedDays = mock.days.mapIndexed { index, mockDay ->
            val real = forecast.dailyForecasts.getOrNull(index) ?: return@mapIndexed mockDay
            mockDay.copy(
                week = real.dayLabel,
                dateText = real.dateText,
                weatherText = real.weather,
                iconEmoji = real.iconEmoji,
                highTemp = real.tempHigh,
                lowTemp = real.tempLow,
                dayWeather = real.weather,
                windDir = real.windDir.removeSuffix("风"),
                windPower = real.windPower,
            )
        }
        return mock.copy(
            nowTemp = live?.temperature ?: mock.nowTemp,
            nowWeather = live?.weather ?: mock.nowWeather,
            humidity = live?.humidity ?: mock.humidity,
            days = mergedDays,
            // 生活提示中的湿度文案跟随真实实时湿度
            lifeTips = live?.humidity?.let { realHumidity ->
                mock.lifeTips.copy(
                    humidityText = "当前相对湿度 $realHumidity%，" +
                        if (realHumidity >= 70) "体感略闷热" else "湿度较舒适",
                )
            } ?: mock.lifeTips,
        )
    }

    /** 发起请求并统一处理高德业务层错误（status != "1" 时抛出含 info 的异常供 UI 展示） */
    private suspend fun request(city: WeatherCity, extensions: String) = runCatching {
        if (webKey.isBlank()) {
            throw IllegalStateException("未配置高德 Web 服务 Key（amap.properties 的 AMAP_WEB_KEY）")
        }
        val response = api.getWeather(key = webKey, adCode = city.adCode, extensions = extensions)
        if (response.status != "1") {
            // 常见：10001 key 不正确 / 10003 访问超限 / 10009 无该城市编码
            throw IllegalStateException("高德天气接口错误：${response.info}（${response.infocode}）")
        }
        response
    }.getOrElse { e ->
        // 把网络异常 / 业务异常统一包装成中文可读信息
        throw IllegalStateException(e.message ?: "天气数据请求失败，请检查网络后重试", e)
    }
}
