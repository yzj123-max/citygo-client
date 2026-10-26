package com.example.citygo.skeleton.data.remote

import com.example.citygo.skeleton.data.model.AmapWeatherResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 高德天气 Web 服务接口。
 *
 * 文档：https://lbs.amap.com/api/webservice/guide/api/weatherinfo
 * - extensions=base：实时天气（lives）
 * - extensions=all：未来 4 天预报（forecasts）
 *
 * Key 为「Web服务」类型 Key（与 Android SDK Key 不同），由 BuildConfig.AMAP_WEB_KEY 注入。
 */
interface AmapWeatherApi {

    @GET("v3/weather/weatherInfo")
    suspend fun getWeather(
        @Query("key") key: String,
        @Query("city") adCode: String,
        @Query("extensions") extensions: String,
        @Query("output") output: String = "JSON",
    ): AmapWeatherResponse

    companion object {
        const val BASE_URL = "https://restapi.amap.com/"
        const val EXTENSIONS_BASE = "base"
        const val EXTENSIONS_ALL = "all"
    }
}
