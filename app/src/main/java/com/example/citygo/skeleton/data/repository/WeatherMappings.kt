package com.example.citygo.skeleton.data.repository

import com.example.citygo.skeleton.data.model.AmapForecastWeather
import com.example.citygo.skeleton.data.model.AmapLiveWeather
import com.example.citygo.skeleton.data.model.AmapWeatherCast
import com.example.citygo.skeleton.data.model.DailyForecast
import com.example.citygo.skeleton.data.model.LiveWeather
import com.example.citygo.skeleton.data.model.WeatherCity
import com.example.citygo.skeleton.data.model.WeatherDay
import com.example.citygo.skeleton.data.model.WeatherForecast
import com.example.citygo.skeleton.data.model.WeatherLifeTips
import com.example.citygo.skeleton.data.model.WeatherOverview
import java.util.Calendar

/* =====================================================================================
 * 高德天气 DTO → UI 领域模型映射（Mock 仓库与真实网络仓库共用，保证两边展示一致）
 * ===================================================================================== */

/** 高德预报 DTO → UI 使用的 [WeatherForecast] */
internal fun AmapForecastWeather.toDomain(city: WeatherCity): WeatherForecast =
    WeatherForecast(
        city = city,
        reportTime = reporttime,
        dailyForecasts = casts.mapIndexed { index, cast -> cast.toDomain(index) },
    )

/** 高德单日 DTO → [DailyForecast]；天气图标用 emoji 占位 */
internal fun AmapWeatherCast.toDomain(index: Int): DailyForecast =
    DailyForecast(
        dayLabel = dayLabelOf(week, index),
        dateText = date.substringAfter('-'), // 2026-09-21 → 09-21
        weather = dayweather,
        iconEmoji = iconEmojiOf(dayweather),
        tempHigh = daytemp.toIntOrNull() ?: 0,
        tempLow = nighttemp.toIntOrNull() ?: 0,
        windDir = daywind,
        windPower = "$daypower 级",
    )

/** 高德实时天气 DTO → [LiveWeather] */
internal fun AmapLiveWeather.toDomain(): LiveWeather =
    LiveWeather(
        city = city,
        weather = weather,
        temperature = temperature.toIntOrNull() ?: 0,
        windDirection = winddirection,
        windPower = "$windpower 级",
        humidity = humidity.toIntOrNull() ?: 0,
        reportTime = reporttime,
    )

/** 第 1 天固定展示「今天」，其余按高德 week 字段映射周几 */
private fun dayLabelOf(week: String, index: Int): String {
    if (index == 0) return "今天"
    return when (week) {
        "1" -> "周一"
        "2" -> "周二"
        "3" -> "周三"
        "4" -> "周四"
        "5" -> "周五"
        "6" -> "周六"
        else -> "周日"
    }
}

/** 天气现象 → emoji 图标 */
internal fun iconEmojiOf(weather: String): String = when {
    "小雨" in weather || "中雨" in weather || "大雨" in weather || "暴雨" in weather -> "🌧️"
    "雷" in weather -> "⛈️"
    "雪" in weather -> "❄️"
    "多云转晴" in weather || "阴转多云" in weather -> "🌥️"
    "多云" in weather -> "⛅"
    "阴" in weather -> "☁️"
    "晴" in weather -> "☀️"
    else -> "🌡️"
}

/* =====================================================================================
 * 天气页全量模拟数据（对应产品调试 JSON：长沙市 7 日预报模板）
 *
 * 周几 / 日期按设备当前日历动态推算，避免硬编码「今天/周六…」随时间失效；
 * 温度 / 天气 / 降雨概率沿用调试 JSON，切换其他城市时数值模板不变、仅替换城市，
 * 后续接入真实天气服务时由 RemoteWeatherRepository 覆盖对应字段。
 * ===================================================================================== */

/** 7 日模板：天气现象 / 最低温 / 最高温 / 降雨概率 */
private val MOCK_WEEK_TEMPLATE: List<Array<Any>> = listOf(
    arrayOf("多云", 25, 35, 0),
    arrayOf("小雨", 26, 34, 40),
    arrayOf("多云转晴", 25, 34, 0),
    arrayOf("晴", 26, 35, 0),
    arrayOf("多云", 24, 28, 0),
    arrayOf("多云", 21, 25, 0),
    arrayOf("大雨", 16, 21, 85),
)

/** 构造天气页全量模拟数据 */
internal fun buildMockOverview(city: WeatherCity): WeatherOverview {
    val calendar = Calendar.getInstance()
    val weekNames = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
    val days = MOCK_WEEK_TEMPLATE.mapIndexed { index, row ->
        @Suppress("UNCHECKED_CAST")
        val weatherText = row[0] as String
        val low = row[1] as Int
        val high = row[2] as Int
        val rain = row[3] as Int
        val dayCalendar = calendar.clone() as Calendar
        dayCalendar.add(Calendar.DAY_OF_YEAR, index)
        val month = dayCalendar.get(Calendar.MONTH) + 1
        val dayOfMonth = dayCalendar.get(Calendar.DAY_OF_MONTH)
        val dateText = "${month.toString().padStart(2, '0')}-${dayOfMonth.toString().padStart(2, '0')}"
        WeatherDay(
            week = if (index == 0) "今天" else weekNames[dayCalendar.get(Calendar.DAY_OF_WEEK) - 1],
            dateText = dateText,
            weatherText = weatherText,
            iconEmoji = iconEmojiOf(weatherText),
            lowTemp = low,
            highTemp = high,
            rainProb = rain,
            dayWeather = weatherText,
            nightWeather = nightWeatherOf(weatherText),
            windDir = "南东南风",
            windPower = if (rain >= 50) "4 级" else "2 级",
            uvIndex = uvOf(weatherText),
        )
    }

    return WeatherOverview(
        city = city,
        nowTemp = 30,
        nowWeather = "大部多云",
        days = days,
        airQuality = 60,
        feelTemp = 32,
        windSpeed = 3,
        gustSpeed = 9,
        windDirection = "南东南风",
        windAngle = windAngleOf("南东南风"),
        uvIndex = 0,
        sunrise = "06:18",
        sunset = "18:20",
        precipitation = 0.05,
        tomorrowPrecipText = "明日小雨，预计降水 2.4 mm，降雨概率 40%",
        visibility = 11,
        moonPhase = "盈凸月",
        moonIcon = "🌔",
        moonIllumination = 78,
        moonSet = "23:42",
        nextFullMoon = "10月04日",
        humidity = 71,
        dewPoint = 24,
        pressure = 29.83,
        lifeTips = WeatherLifeTips(
            humidityText = "当前相对湿度 71%，体感略闷热",
            dress = "温度偏高，建议短袖，早晚备薄外套",
            petTip = "白天温度高，尽量避免正午带宠物长时间外出",
            travelTip = "部分日期有降雨，出门建议携带雨伞，户外景点注意防滑",
            pitTip = "白天最高 35℃，不建议正午逛户外景点；周四有大雨，山路/步道注意防滑",
        ),
    )
}

/** 白天天气 → 夜间天气兜底文案 */
private fun nightWeatherOf(dayWeather: String): String = when {
    "大雨" in dayWeather -> "中雨"
    "小雨" in dayWeather -> "阴"
    "多云" in dayWeather -> "晴"
    else -> "晴"
}

/** 天气现象 → 当日紫外线指数（模拟） */
private fun uvOf(weather: String): Int = when {
    "大雨" in weather || "小雨" in weather || "中雨" in weather -> 1
    "多云" in weather -> 3
    "多云转晴" in weather -> 5
    "晴" in weather -> 7
    else -> 2
}

/**
 * 中文风向文案 → 角度（0=正北，顺时针）。覆盖 16 方位 + 常见简称。
 */
internal fun windAngleOf(direction: String): Float {
    val d = direction.trim().removeSuffix("风")
    return when (d) {
        "北" -> 0f
        "北东北", "东北偏北" -> 22.5f
        "东北" -> 45f
        "东东北", "东北偏东" -> 67.5f
        "东" -> 90f
        "东东南", "东南偏东" -> 112.5f
        "东南" -> 135f
        "南东南", "东南偏南" -> 157.5f
        "南" -> 180f
        "南西南", "西南偏南" -> 202.5f
        "西南" -> 225f
        "西西南", "西南偏西" -> 247.5f
        "西" -> 270f
        "西西北", "西北偏西" -> 292.5f
        "西北" -> 315f
        "北西北", "西北偏北" -> 337.5f
        else -> 0f
    }
}
