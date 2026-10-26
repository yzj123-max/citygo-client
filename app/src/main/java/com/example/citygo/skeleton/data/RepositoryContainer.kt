package com.example.citygo.skeleton.data

import android.content.Context
import com.example.citygo.BuildConfig
import com.example.citygo.skeleton.data.remote.AmapWeatherApi
import com.example.citygo.skeleton.data.repository.MockWeatherRepository
import com.example.citygo.skeleton.data.repository.RemoteWeatherRepository
import com.example.citygo.skeleton.data.repository.WeatherRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 数据层手动依赖装配点（轻量 Service Locator，工程未引入 Hilt/Koin）。
 *
 * - 天气已接入高德 Web 服务真实接口（Web Key 为空时回退 Mock，保证无 Key 环境仍可演示）；
 * - 景点推荐通过自有后端加载，保留后端维护的介绍和政策；
 * - 社区通过自有后端加载；行程统一由 AppServiceLocator 的业务仓库持久化。
 *
 * 使用前需在 Application.onCreate 调用 [init] 注入 Context。
 */
object RepositoryContainer {

    /** Application Context（Application.onCreate 注入） */
    private lateinit var appContext: Context

    /** 天气专用 OkHttp（Key 为空时不会创建） */
    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    /** 高德天气仓库：配置了 Web Key 用真实接口，否则回退本地 Mock */
    val weatherRepository: WeatherRepository by lazy { createWeatherRepository() }

    /**
     * 景点推荐仓库（景点推荐 Tab）。
     * 推荐、搜索、详情、附近与导航目的地均通过自有后端读取；
     * 手机端不再为本模块直接调用高德Web服务或补全政策。
     */
    val spotRepository by lazy { createSpotRepository() }

    /** 社区真实后端仓库；复用现有登录鉴权与服务地址。 */
    val articleRepository by lazy {
        com.example.citygo.skeleton.data.repository.RemoteArticleRepository { appContext }
    }

    /** 由 CityGoApplication.onCreate 调用，注入全局 Context */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun createWeatherRepository(): WeatherRepository {
        if (BuildConfig.AMAP_WEB_KEY.isBlank()) return MockWeatherRepository()
        val api = Retrofit.Builder()
            .baseUrl(AmapWeatherApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AmapWeatherApi::class.java)
        return RemoteWeatherRepository(webKey = BuildConfig.AMAP_WEB_KEY, api = api)
    }

    /** 景点数据统一走自有后端，避免绕过人工维护的介绍和政策。 */
    private fun createSpotRepository(): com.example.citygo.skeleton.data.repository.BackendSpotRepository =
        com.example.citygo.skeleton.data.repository.BackendSpotRepository()
}
