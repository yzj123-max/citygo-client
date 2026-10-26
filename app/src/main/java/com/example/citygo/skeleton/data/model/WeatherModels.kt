package com.example.citygo.skeleton.data.model

/* =====================================================================================
 * 天气预报数据模型（高德天气 REST API 预留）
 *
 * 接口（暂不发起真实请求，由 MockWeatherRepository 返回假数据）：
 *   实时天气：GET https://restapi.amap.com/v3/weather/weatherInfo
 *             ?key=YOUR_KEY&city=510100&extensions=base
 *   预报天气：GET .../weatherInfo?key=YOUR_KEY&city=510100&extensions=all
 *
 * 分层约定：
 * - Amap* 开头为高德原始响应 DTO（字段名与高德 JSON 保持一致，接入后可直接配 Gson 解析）；
 * - WeatherForecast / DailyForecast / LiveWeather 为 UI 使用的领域模型，
 *   由 Repository 负责 DTO → 领域模型映射，UI 不接触高德原始结构。
 * ===================================================================================== */

/**
 * 天气查询城市（高德天气接口以行政区划编码 [adCode] 为准，城市名仅用于展示）。
 */
data class WeatherCity(
    val name: String,
    val adCode: String,
)

/** 默认查询城市：长沙（adCode 430100，与调试用模拟 JSON 一致） */
val DEFAULT_WEATHER_CITY: WeatherCity = WeatherCity(name = "长沙", adCode = "430100")

/**
 * 单日预报（领域模型，计划页天气预报子页直接渲染）。
 *
 * @property dayLabel 周几 / 今天明天等展示文案
 * @property dateText 日期（MM-dd）
 * @property weather 天气现象文案（白天）
 * @property iconEmoji 天气图标占位（接入后可替换为高德图标资源）
 * @property tempHigh 最高温（℃）
 * @property tempLow 最低温（℃）
 * @property windDir 风向
 * @property windPower 风力等级（如「2 级」）
 */
data class DailyForecast(
    val dayLabel: String,
    val dateText: String,
    val weather: String,
    val iconEmoji: String,
    val tempHigh: Int,
    val tempLow: Int,
    val windDir: String,
    val windPower: String,
)

/**
 * 实时天气（领域模型，对应高德 extensions=base 的 lives）。
 */
data class LiveWeather(
    val city: String,
    val weather: String,
    val temperature: Int,
    val windDirection: String,
    val windPower: String,
    val humidity: Int,
    val reportTime: String,
)

/**
 * 一次天气预报查询结果（领域模型）。
 *
 * @property city 查询城市
 * @property reportTime 预报发布时间
 * @property dailyForecasts 未来多天预报
 */
data class WeatherForecast(
    val city: WeatherCity,
    val reportTime: String,
    val dailyForecasts: List<DailyForecast>,
)

/* =====================================================================================
 * 天气页增强版领域模型（单页垂直滚动版，对应调试模拟 JSON 的全模块数据）
 *
 * 覆盖：实时头部 / 7 日预报 / 空气质量 / 风力风向 / 紫外线 / 日出日落 / 降水 / 能见度 /
 *      月相 / 湿度气压 / 生活出行提示。
 * 高德免费天气 REST 仅提供实时 + 多日预报，空气 / 紫外 / 月相等扩展指标后续需接入
 * 专用天气服务（在 RemoteWeatherRepository 中替换即可，UI 与本模型结构不变）。
 * ===================================================================================== */

/**
 * 单日预报（7 日列表条目，同时承载详情页细化字段）。
 *
 * @property week 星期展示文案（今天 / 周六 …）
 * @property dateText 日期（MM-dd）
 * @property weatherText 天气现象文案（多云 / 小雨 …，对应 JSON weatherIcon 字段）
 * @property iconEmoji 占位天气图标
 * @property lowTemp 最低温 ℃
 * @property highTemp 最高温 ℃
 * @property rainProb 降雨概率 %（0 时列表不展示）
 * @property dayWeather 白天天气（详情页）
 * @property nightWeather 夜间天气（详情页）
 * @property windDir 当日风向
 * @property windPower 当日风力（如「2 级」）
 * @property uvIndex 当日紫外线指数（详情页）
 */
data class WeatherDay(
    val week: String,
    val dateText: String,
    val weatherText: String,
    val iconEmoji: String,
    val lowTemp: Int,
    val highTemp: Int,
    val rainProb: Int,
    val dayWeather: String,
    val nightWeather: String,
    val windDir: String,
    val windPower: String,
    val uvIndex: Int,
)

/**
 * 生活出行提示（页面底部模块 H）。
 *
 * @property humidityText 实时湿度文案
 * @property dress 穿衣推荐
 * @property travelTip 旅游出行提示
 * @property pitTip 旅游避坑小提示
 * @property petTip 宠物出行提示（本 App 宠物友好，附加展示）
 */
data class WeatherLifeTips(
    val humidityText: String,
    val dress: String,
    val travelTip: String,
    val pitTip: String,
    val petTip: String,
)

/**
 * 天气页全量数据（一次城市切换 / 一次加载返回，所有模块共用）。
 */
data class WeatherOverview(
    val city: WeatherCity,
    val nowTemp: Int,
    val nowWeather: String,
    val days: List<WeatherDay>,
    /** AQI 空气质量指数 */
    val airQuality: Int,
    /** 体感温度 ℃ */
    val feelTemp: Int,
    /** 风速 m/s */
    val windSpeed: Int,
    /** 阵风 m/s */
    val gustSpeed: Int,
    /** 风向文案（南东南风…） */
    val windDirection: String,
    /** 风向角度（0=正北，顺时针；罗盘 UI 使用） */
    val windAngle: Float,
    /** 紫外线指数 0-11+ */
    val uvIndex: Int,
    val sunrise: String,
    val sunset: String,
    /** 今日降水量 mm */
    val precipitation: Double,
    /** 明日降水预测文案 */
    val tomorrowPrecipText: String,
    /** 能见度 km */
    val visibility: Int,
    /** 月相文案（盈凸月…） */
    val moonPhase: String,
    /** 月相图标占位 */
    val moonIcon: String,
    /** 月亮照射范围 % */
    val moonIllumination: Int,
    /** 下次月落时间 */
    val moonSet: String,
    /** 下次满月时间 */
    val nextFullMoon: String,
    /** 相对湿度 % */
    val humidity: Int,
    /** 露点温度 ℃ */
    val dewPoint: Int,
    /** 气压 inHg（仪表盘；UI 同时换算 hPa） */
    val pressure: Double,
    val lifeTips: WeatherLifeTips,
)

/* --------------------------------- 高德响应 DTO --------------------------------- */

/**
 * 高德天气接口统一响应（同时覆盖 base 实时与 all 预报，未请求的字段为空列表）。
 * 字段命名与高德返回 JSON 一致（status / info / infocode / lives / forecasts）。
 */
data class AmapWeatherResponse(
    val status: String = "",
    val info: String = "",
    val infocode: String = "",
    val count: String? = null,
    val lives: List<AmapLiveWeather> = emptyList(),
    val forecasts: List<AmapForecastWeather> = emptyList(),
)

/**
 * 高德实时天气（lives 数组元素）。
 */
data class AmapLiveWeather(
    val province: String = "",
    val city: String = "",
    val adcode: String = "",
    val weather: String = "",
    val temperature: String = "",
    val winddirection: String = "",
    val windpower: String = "",
    val humidity: String = "",
    val reporttime: String = "",
)

/**
 * 高德预报天气（forecasts 数组元素）。
 */
data class AmapForecastWeather(
    val city: String = "",
    val adcode: String = "",
    val province: String = "",
    val reporttime: String = "",
    val casts: List<AmapWeatherCast> = emptyList(),
)

/**
 * 高德单日预报（casts 数组元素，温度 / 风力区分白天夜间，均为字符串）。
 */
data class AmapWeatherCast(
    val date: String = "",
    val week: String = "",
    val dayweather: String = "",
    val nightweather: String = "",
    val daytemp: String = "",
    val nighttemp: String = "",
    val daywind: String = "",
    val nightwind: String = "",
    val daypower: String = "",
    val nightpower: String = "",
    val daytemp_float: String = "",
    val nighttemp_float: String = "",
)
