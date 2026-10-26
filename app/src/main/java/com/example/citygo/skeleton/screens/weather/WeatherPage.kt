package com.example.citygo.skeleton.screens.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.citygo.skeleton.data.WeatherViewModel
import com.example.citygo.skeleton.data.model.HOT_WEATHER_CITIES
import com.example.citygo.skeleton.data.model.WeatherCity
import com.example.citygo.skeleton.data.model.WeatherCityEntry
import com.example.citygo.skeleton.data.model.WeatherDay
import com.example.citygo.skeleton.data.model.WeatherOverview
import com.example.citygo.skeleton.data.model.searchWeatherCities
import kotlin.math.cos
import kotlin.math.sin

/* =====================================================================================
 * 天气预报页面（单页垂直滚动版，无横向分页）
 *
 * 模块顺序（严格按需求从上至下）：
 *   头部（城市切换/实时温度/天气文字）→ A 7 日预报（点击进单日详情）
 *   → B 空气质量 + 平均/体感温度 → C 风力风向罗盘 → D 紫外线 + 日出日落
 *   → E 降水 + 能见度 → F 月相 → G 湿度 + 气压 → H 生活出行提示
 *
 * 视觉统一为深色卡片风格（独立色板，不跟随全局明暗主题）；
 * 数据全部来自 WeatherViewModel → WeatherRepository（当前模拟 JSON，预留真实 API）。
 * ===================================================================================== */

/* --------------------------------- 深色视觉色板 --------------------------------- */

private val WeatherBg = Color(0xFF0D1320)          // 页面底色
private val WeatherCard = Color(0xFF1A2336)        // 卡片底色
private val WeatherCardAlt = Color(0xFF232F49)     // 强调卡片 / 下拉菜单
private val TextPrimary = Color(0xFFF2F5FA)
private val TextSecondary = Color(0xFF9AA7BD)
private val AccentCyan = Color(0xFF205B46)
private val TempWarm = Color(0xFFFF8A65)
private val TempCool = Color(0xFF64B5F6)
private val TrackColor = Color(0x26FFFFFF)

/**
 * 天气页入口（由出行计划 Tab 的「天气预报」子页承载）。
 *
 * @param onDayClick 点击 7 日预报中某一天，参数为该天在 7 日列表中的下标
 */
@Composable
fun WeatherPage(
    weatherViewModel: WeatherViewModel,
    onDayClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val WeatherBg = MaterialTheme.colorScheme.background
    val AccentCyan = MaterialTheme.colorScheme.primary
    val TrackColor = MaterialTheme.colorScheme.outlineVariant

    val state by weatherViewModel.uiState.collectAsStateWithLifecycle()
    val overview = state.overview

    // 城市搜索覆盖页开关
    var showCitySearch by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WeatherBg),
    ) {
        when {
            // —— 首次加载中：顶部 loading 占位 ——
            overview == null && state.loading -> WeatherLoadingState()

            // —— 加载失败：错误提示 + 点击重试 ——
            overview == null && state.errorMessage != null -> WeatherErrorState(
                message = state.errorMessage ?: "天气加载失败",
                onRetry = weatherViewModel::retry,
            )

            // —— 全量数据：单页垂直滚动 ——
            overview != null -> WeatherOverviewContent(
                overview = overview,
                refreshing = state.loading,
                onCitySelected = weatherViewModel::switchCity,
                onDayClick = onDayClick,
                onOpenCitySearch = { showCitySearch = true },
            )
        }

        // 城市切换 / 重试时的顶部细进度条（保留已有内容，不清空页面）
        if (state.loading && overview != null) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                color = AccentCyan,
                trackColor = TrackColor,
            )
        }

        // 全国城市搜索覆盖页（选中城市后全量刷新并关闭）
        if (showCitySearch) {
            WeatherCitySearchOverlay(
                currentAdCode = state.city.adCode,
                onCitySelected = { city ->
                    showCitySearch = false
                    weatherViewModel.switchCity(city)
                },
                onBack = { showCitySearch = false },
            )
        }
    }
}

/* --------------------------------- 加载 / 失败状态 --------------------------------- */

@Composable
private fun WeatherLoadingState() {
    val TextSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val AccentCyan = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = AccentCyan)
        Spacer(Modifier.height(12.dp))
        Text("天气数据加载中…", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun WeatherErrorState(message: String, onRetry: () -> Unit) {
    val TextSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("📡", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("点击重试")
        }
    }
}

/* --------------------------------- 正常内容：垂直滚动全模块 --------------------------------- */

@Composable
private fun WeatherOverviewContent(
    overview: WeatherOverview,
    refreshing: Boolean,
    onCitySelected: (com.example.citygo.skeleton.data.model.WeatherCity) -> Unit,
    onDayClick: (Int) -> Unit,
    onOpenCitySearch: () -> Unit,
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()) // 整体页面唯一垂直滚动，无任何横向分页
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(if (refreshing) 6.dp else 14.dp))

        // 页面头部：城市切换 + 实时温度 + 天气状况
        WeatherHeaderBlock(
            overview = overview,
            onCitySelected = onCitySelected,
            onOpenCitySearch = onOpenCitySearch,
        )

        // 模块 A：7 天天气预报
        SectionLabel("7 日预报")
        Forecast7Card(days = overview.days, onDayClick = onDayClick)

        // 模块 B：空气质量（色块地图 + AQI）+ 平均温度 / 体感温度
        SectionLabel("空气质量")
        AirQualityCard(aqi = overview.airQuality)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            AverageTempCard(modifier = Modifier.weight(1f), overview = overview)
            FeelTempCard(modifier = Modifier.weight(1f), overview = overview)
        }

        // 模块 C：风力风向（风速 / 阵风 / 罗盘）
        SectionLabel("风力风向")
        WindCard(overview = overview)

        // 模块 D：紫外线 + 日出日落
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            UvCard(modifier = Modifier.weight(1f), uvIndex = overview.uvIndex)
            SunCard(modifier = Modifier.weight(1f), sunrise = overview.sunrise, sunset = overview.sunset)
        }

        // 模块 E：降水 + 能见度
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            PrecipCard(
                modifier = Modifier.weight(1f),
                today = overview.precipitation,
                tomorrow = overview.tomorrowPrecipText,
            )
            VisibilityCard(modifier = Modifier.weight(1f), visibilityKm = overview.visibility)
        }

        // 模块 F：月相
        SectionLabel("月相")
        MoonCard(overview = overview)

        // 模块 G：湿度 + 气压
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            HumidityCard(modifier = Modifier.weight(1f), humidity = overview.humidity, dewPoint = overview.dewPoint)
            PressureCard(modifier = Modifier.weight(1f), pressureInHg = overview.pressure)
        }

        // 模块 H：生活出行提示（下滑到底部可见）
        SectionLabel("生活出行提示")
        LifeTipsCard(overview = overview)

        Spacer(Modifier.height(8.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun SectionLabel(text: String) {
    val AccentCyan = MaterialTheme.colorScheme.primary

    Text(
        text = text,
        color = AccentCyan,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
    )
}

/** 深色圆角卡片基础容器 */
@Composable
private fun DarkCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val WeatherCard = MaterialTheme.colorScheme.surface

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = WeatherCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
    }
}

/* --------------------------------- 头部：城市 / 实时温度 --------------------------------- */

@Composable
private fun WeatherHeaderBlock(
    overview: WeatherOverview,
    onCitySelected: (com.example.citygo.skeleton.data.model.WeatherCity) -> Unit,
    onOpenCitySearch: () -> Unit,
) {
    val WeatherCardAlt = MaterialTheme.colorScheme.surfaceVariant
    val TextPrimary = MaterialTheme.colorScheme.onSurface
    val TextSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val AccentCyan = MaterialTheme.colorScheme.primary
    val TrackColor = MaterialTheme.colorScheme.outlineVariant

    Row(verticalAlignment = Alignment.CenterVertically) {
        // 城市切换下拉：展示热门城市，底部入口可打开全国城市搜索
        var expanded by remember { mutableStateOf(false) }
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = overview.city.name,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = "切换城市",
                    tint = TextSecondary,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = WeatherCardAlt,
            ) {
                HOT_WEATHER_CITIES.forEach { city ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                city.name + if (city.adCode == overview.city.adCode) "  ✓" else "",
                                color = TextPrimary,
                            )
                        },
                        onClick = {
                            expanded = false
                            onCitySelected(city)
                        },
                    )
                }
                // 分隔线 + 全国城市搜索入口
                androidx.compose.material3.HorizontalDivider(color = TrackColor)
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = AccentCyan)
                    },
                    text = { Text("搜索其他城市…", color = AccentCyan) },
                    onClick = {
                        expanded = false
                        onOpenCitySearch()
                    },
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // 头部右侧独立搜索按钮，直接进入全国城市搜索
        IconButton(onClick = onOpenCitySearch) {
            Icon(
                Icons.Filled.Search,
                contentDescription = "搜索城市",
                tint = TextPrimary,
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            // 实时温度
            Text(
                text = "${overview.nowTemp}°",
                color = TextPrimary,
                fontSize = 76.sp,
                fontWeight = FontWeight.Light,
            )
            // 天气状况文字（大部多云）
            Text(
                text = overview.nowWeather,
                color = TextSecondary,
                fontSize = 16.sp,
            )
        }
        Text(text = weatherEmojiOf(overview.nowWeather), fontSize = 72.sp)
    }
}

/** 天气文字 → 头部大 emoji 占位 */
private fun weatherEmojiOf(text: String): String = when {
    "多云" in text && "晴" in text -> "🌥️"
    "多云" in text -> "⛅"
    "晴" in text -> "☀️"
    "大雨" in text || "暴雨" in text -> "🌧️"
    "雨" in text -> "🌦️"
    "阴" in text -> "☁️"
    else -> "🌡️"
}

/* --------------------------------- 全国城市搜索覆盖页 --------------------------------- */

/**
 * 全屏城市搜索页：
 * - 输入关键字时，在全国城市目录中按城市名 / 省份名 / adCode 实时本地过滤；
 * - 关键字为空时展示热门城市；
 * - 选中任意城市后回调，由外层触发整页天气数据全量刷新。
 *
 * @param currentAdCode 当前已选城市 adCode，用于在结果中打勾标记
 */
@Composable
private fun WeatherCitySearchOverlay(
    currentAdCode: String,
    onCitySelected: (WeatherCity) -> Unit,
    onBack: () -> Unit,
) {
    val WeatherBg = MaterialTheme.colorScheme.background
    val WeatherCard = MaterialTheme.colorScheme.surface
    val TextPrimary = MaterialTheme.colorScheme.onSurface
    val TextSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val AccentCyan = MaterialTheme.colorScheme.primary
    val TrackColor = MaterialTheme.colorScheme.outlineVariant

    var keyword by remember { mutableStateOf("") }
    // 本地目录过滤，输入即搜，无需网络
    val result = remember(keyword) { searchWeatherCities(keyword) }
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WeatherBg)
            .statusBarsPadding()
            .imePadding(), // 键盘弹出时列表内容自适应上抬
    ) {
        // 顶栏：返回 + 搜索输入框 + 清空
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = TextPrimary,
                )
            }
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(
                        if (keyword.isEmpty()) "搜索城市 / 拼音，如 苏州、suzhou、sz" else "",
                        color = TextSecondary,
                        fontSize = 14.sp,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary)
                },
                trailingIcon = {
                    if (keyword.isNotEmpty()) {
                        IconButton(onClick = { keyword = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "清空", tint = TextSecondary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = WeatherCard,
                    unfocusedContainerColor = WeatherCard,
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = TrackColor,
                    cursorColor = AccentCyan,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                ),
                keyboardOptions = KeyboardOptions.Default,
            )
        }

        // 结果统计 / 空态标题
        Text(
            text = if (keyword.isBlank()) "热门城市" else "搜索结果（${result.size}）",
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 18.dp, top = 6.dp, bottom = 4.dp),
        )

        if (keyword.isNotBlank() && result.isEmpty()) {
            // 无匹配结果
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Text(
                    text = "未找到「$keyword」相关城市\n试试输入城市名或省份名",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 60.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                    vertical = 4.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(result, key = { it.adCode }) { entry ->
                    CityResultRow(
                        entry = entry,
                        selected = entry.adCode == currentAdCode,
                        onClick = { onCitySelected(entry.toWeatherCity()) },
                    )
                }
            }
        }
    }

    // 进入页面自动聚焦输入框并唤起键盘
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }
}

/** 搜索结果单行：城市名 + 省份归属 + 选中勾 */
@Composable
private fun CityResultRow(
    entry: WeatherCityEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val WeatherCard = MaterialTheme.colorScheme.surface
    val TextPrimary = MaterialTheme.colorScheme.onSurface
    val TextSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val AccentCyan = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WeatherCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name + if (selected) "  ✓" else "",
                color = if (selected) AccentCyan else TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = entry.province + " · " + entry.adCode,
                color = TextSecondary,
                fontSize = 12.sp,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextSecondary,
        )
    }
}

/* --------------------------------- 模块 A：7 日预报（LazyColumn，条目可点） --------------------------------- */

@Composable
private fun Forecast7Card(days: List<WeatherDay>, onDayClick: (Int) -> Unit) {

    // 整页已由外层 verticalScroll 承载滚动；7 条数据量固定，LazyColumn 关闭自身滚动并给确定高度，
    // 避免与外层垂直滚动嵌套冲突，同时满足「7 天预报列表使用 LazyColumn」的约定
    val rowHeight = 56
    val rowGap = 6
    DarkCard {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height((days.size * rowHeight + (days.size - 1) * rowGap).dp),
            userScrollEnabled = false,
            verticalArrangement = Arrangement.spacedBy(rowGap.dp),
        ) {
            // 7 天温度区间条需要全周最低 / 最高温做归一化
            val weekMin = days.minOf { it.lowTemp }
            val weekMax = days.maxOf { it.highTemp }
            itemsIndexed(days, key = { index, day -> "${day.dateText}-$index" }) { index, day ->
                DayForecastRow(
                    day = day,
                    weekMin = weekMin,
                    weekMax = weekMax,
                    onClick = { onDayClick(index) },
                )
            }
        }
    }
}

@Composable
private fun DayForecastRow(
    day: WeatherDay,
    weekMin: Int,
    weekMax: Int,
    onClick: () -> Unit,
) {
    val TextPrimary = MaterialTheme.colorScheme.onSurface
    val TextSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val AccentCyan = MaterialTheme.colorScheme.primary
    val TempWarm = MaterialTheme.colorScheme.tertiary
    val TempCool = MaterialTheme.colorScheme.secondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 星期 / 日期
        Column(modifier = Modifier.width(46.dp)) {
            Text(day.week, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(day.dateText, color = TextSecondary, fontSize = 11.sp)
        }
        // 天气图标占位
        Text(text = day.iconEmoji, fontSize = 22.sp, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
        // 最低温
        Text(
            "${day.lowTemp}°",
            color = TempCool,
            fontSize = 14.sp,
            modifier = Modifier.width(38.dp),
            textAlign = TextAlign.End,
        )
        // 温度区间进度条
        TempRangeBar(
            low = day.lowTemp,
            high = day.highTemp,
            weekMin = weekMin,
            weekMax = weekMax,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        )
        // 最高温
        Text(
            "${day.highTemp}°",
            color = TempWarm,
            fontSize = 14.sp,
            modifier = Modifier.width(38.dp),
        )
        // 降雨概率（无雨不占位以外的文字）
        Box(modifier = Modifier.width(52.dp), contentAlignment = Alignment.Center) {
            if (day.rainProb > 0) {
                Text("💧${day.rainProb}%", color = AccentCyan, fontSize = 11.sp)
            }
        }
        // 点击进入详情的箭头提示
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "查看${day.week}详情",
            tint = TextSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 单条 7 日预报的最低-最高温区间彩条（以全周温区归一化） */
@Composable
private fun TempRangeBar(
    low: Int,
    high: Int,
    weekMin: Int,
    weekMax: Int,
    modifier: Modifier = Modifier,
) {
    val AccentCyan = MaterialTheme.colorScheme.primary
    val TempWarm = MaterialTheme.colorScheme.tertiary
    val TempCool = MaterialTheme.colorScheme.secondary
    val TrackColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier.height(6.dp)) {
        val range = (weekMax - weekMin).coerceAtLeast(1).toFloat()
        val startFraction = (low - weekMin) / range
        val endFraction = (high - weekMin) / range
        // 底色轨道
        drawRoundRect(
            color = TrackColor,
            size = androidx.compose.ui.geometry.Size(size.width, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2, size.height / 2),
        )
        // 当日温度区间（冷色→暖色渐变）
        val startX = size.width * startFraction.coerceIn(0f, 1f)
        val endX = size.width * endFraction.coerceIn(0f, 1f)
        if (endX > startX) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(TempCool, AccentCyan, TempWarm),
                    startX = startX,
                    endX = endX,
                ),
                topLeft = Offset(startX, 0f),
                size = androidx.compose.ui.geometry.Size(endX - startX, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2, size.height / 2),
            )
        }
    }
}

/* --------------------------------- 模块 B：空气质量 / 平均温度 / 体感温度 --------------------------------- */

private fun aqiLevel(aqi: Int): Pair<String, Color> = when {
    aqi <= 50 -> "优" to Color(0xFF66BB6A)
    aqi <= 100 -> "良" to Color(0xFFFFCA28)
    aqi <= 150 -> "轻度污染" to Color(0xFFFFB300)
    aqi <= 200 -> "中度污染" to Color(0xFFEF5350)
    aqi <= 300 -> "重度污染" to Color(0xFFAB47BC)
    else -> "严重污染" to Color(0xFF8D6E63)
}

@Composable
private fun AirQualityCard(aqi: Int) {
    val TextPrimary = MaterialTheme.colorScheme.onSurface
    val TextSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    val (level, levelColor) = aqiLevel(aqi)
    DarkCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 空气质量简易地图色块（6 个色块模拟城市区域分布）
            Box(
                modifier = Modifier
                    .size(width = 118.dp, height = 80.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val blockColors = listOf(
                        Color(0xFF66BB6A), Color(0xFFFFCA28), Color(0xFF43A047),
                        Color(0xFF9CCC65), Color(0xFFFFB300), Color(0xFF7CB342),
                    )
                    val gap = 4.dp.toPx()
                    val cols = 3
                    val rows = 2
                    val w = (size.width - gap * (cols + 1)) / cols
                    val h = (size.height - gap * (rows + 1)) / rows
                    blockColors.forEachIndexed { i, color ->
                        val c = i % cols
                        val r = i / cols
                        drawRoundRect(
                            color = color.copy(alpha = 0.85f),
                            topLeft = Offset(gap + c * (w + gap), gap + r * (h + gap)),
                            size = androidx.compose.ui.geometry.Size(w, h),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                        )
                    }
                }
                // 当前城市 AQI 覆盖在色块地图中央
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$aqi", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(level, color = Color.White, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("空气质量指数 AQI", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        aqi <= 100 -> "空气质量可接受，可正常安排户外活动"
                        aqi <= 150 -> "敏感人群应减少长时间户外暴露"
                        else -> "空气污染明显，建议减少户外活动并佩戴口罩"
                    },
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "当前：$aqi · $level",
                    color = levelColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun AverageTempCard(modifier: Modifier = Modifier, overview: WeatherOverview) {
    val TextPrimary = MaterialTheme.colorScheme.onSurface
    val TextSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    val today = overview.days.firstOrNull()
    val avg = today?.let { (it.lowTemp + it.highTemp) / 2 } ?: overview.nowTemp
    val diff = today?.let { avg - it.highTemp } ?: 0
    DarkCard(modifier = modifier) {
        Text("平均温度", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text("$avg°", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (diff <= 0) "低于今日最高温 ${-diff}℃" else "高于今日最高温 $diff℃",
            color = TextSecondary,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun FeelTempCard(modifier: Modifier = Modifier, overview: WeatherOverview) {
    val TextPrimary = MaterialTheme.colorScheme.onSurface
    val TextSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    DarkCard(modifier = modifier) {
        Text("体感温度", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text("${overview.feelTemp}°", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            text = feelText(overview.feelTemp),
            color = TextSecondary,
            fontSize = 11.sp,
        )
    }
}

private fun feelText(feel: Int): String = when {
    feel >= 32 -> "闷热，注意防暑补水"
    feel >= 26 -> "温暖舒适"
    feel >= 18 -> "体感偏凉"
    else -> "寒冷，注意保暖"
}

/* --------------------------------- 模块 C：风力风向罗盘 --------------------------------- */

@Composable
private fun WindCard(overview: WeatherOverview) {

    DarkCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 风向罗盘：箭头指向风的来向（N 在上，顺时针角度）
            WindCompass(angle = overview.windAngle)
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                WindInfoRow(label = "风速", value = "${overview.windSpeed} m/s")
                Spacer(Modifier.height(8.dp))
                WindInfoRow(label = "阵风", value = "${overview.gustSpeed} m/s")
                Spacer(Modifier.height(8.dp))
                WindInfoRow(label = "风向", value = overview.windDirection)
                Spacer(Modifier.height(8.dp))
                WindInfoRow(label = "风向角度", value = "${overview.windAngle.toInt()}°")
            }
        }
    }
}

@Composable
private fun WindInfoRow(label: String, value: String) {
    val TextPrimary = MaterialTheme.colorScheme.onSurface
    val TextSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Text(value, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun WindCompass(angle: Float) {
    val TextSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val TempWarm = MaterialTheme.colorScheme.tertiary
    val TrackColor = MaterialTheme.colorScheme.outlineVariant

    Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2 - 4.dp.toPx()
            // 外圈
            drawCircle(
                color = TrackColor,
                radius = radius,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
            // 刻度（每 45°）
            for (deg in 0 until 360 step 45) {
                val rad = Math.toRadians(deg.toDouble() - 90.0)
                val inner = radius - if (deg % 90 == 0) 10.dp.toPx() else 5.dp.toPx()
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset(
                        center.x + inner * cos(rad).toFloat(),
                        center.y + inner * sin(rad).toFloat(),
                    ),
                    end = Offset(
                        center.x + radius * cos(rad).toFloat(),
                        center.y + radius * sin(rad).toFloat(),
                    ),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }
            // 风向箭头：0° 指北（正上），按角度顺时针旋转
            rotate(degrees = angle, pivot = center) {
                drawWindArrow(radius)
            }
        }
        // 方位字母（覆盖在罗盘四周，具体风向数值在右侧信息区展示）
        Text("N", color = TempWarm, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopCenter))
        Text("S", color = TextSecondary, fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomCenter))
        Text("W", color = TextSecondary, fontSize = 10.sp,
            modifier = Modifier.align(Alignment.CenterStart) then Modifier.padding(start = 1.dp))
        Text("E", color = TextSecondary, fontSize = 10.sp,
            modifier = Modifier.align(Alignment.CenterEnd) then Modifier.padding(end = 1.dp))
    }
}

/** 在罗盘中心绘制一枚指北箭头（默认指向正上） */
private fun DrawScope.drawWindArrow(radius: Float) {
    val tip = Offset(center.x, center.y - radius * 0.72f)
    val tail = Offset(center.x, center.y + radius * 0.45f)
    val arrowPath = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(center.x - 5.dp.toPx(), center.y - radius * 0.35f)
        lineTo(center.x - 2.dp.toPx(), center.y - radius * 0.35f)
        lineTo(tail.x - 4.dp.toPx(), tail.y)
        lineTo(tail.x + 4.dp.toPx(), tail.y)
        lineTo(center.x + 2.dp.toPx(), center.y - radius * 0.35f)
        lineTo(center.x + 5.dp.toPx(), center.y - radius * 0.35f)
        close()
    }
    drawPath(arrowPath, color = AccentCyan)
}

/* --------------------------------- 模块 D：紫外线 / 日出日落 --------------------------------- */

private fun uvLevel(uv: Int): Pair<String, Color> = when {
    uv <= 2 -> "弱" to Color(0xFF66BB6A)
    uv <= 5 -> "中等" to Color(0xFFFFCA28)
    uv <= 7 -> "强" to Color(0xFFFFB300)
    uv <= 10 -> "很强" to Color(0xFFEF5350)
    else -> "极强" to Color(0xFFAB47BC)
}

@Composable
private fun UvCard(modifier: Modifier = Modifier, uvIndex: Int) {
    val TextPrimary = MaterialTheme.colorScheme.onSurface
    val TextSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val TrackColor = MaterialTheme.colorScheme.outlineVariant

    val (level, color) = uvLevel(uvIndex)
    DarkCard(modifier = modifier) {
        Text("紫外线", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text("$uvIndex", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        // 彩色进度条（指数区间 0-11）
        Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)) {
            drawRoundRect(
                color = TrackColor,
                size = androidx.compose.ui.geometry.Size(size.width, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            )
            drawRoundRect(
                color = color,
                size = androidx.compose.ui.geometry.Size(
                    size.width * (uvIndex / 11f).coerceIn(0f, 1f),
                    size.height,
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "$level · ${if (uvIndex <= 2) "无需特别防护" else "外出建议涂抹防晒霜"}",
            color = TextSecondary,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun SunCard(modifier: Modifier = Modifier, sunrise: String, sunset: String) {
    val TextPrimary = MaterialTheme.colorScheme.onSurface
    val TextSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val AccentCyan = MaterialTheme.colorScheme.primary

    DarkCard(modifier = modifier) {
        Text("日出日落", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🌅", fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("日出 $sunrise", color = TextPrimary, fontSize = 14.sp)
                Text("日落 $sunset", color = TextSecondary, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        // 白昼弧线占位（示意太阳在白天的运行轨迹：开口朝下的上半圆）
        Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)) {
            drawArc(
                color = AccentCyan.copy(alpha = 0.5f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

/* --------------------------------- 模块 E：降水 / 能见度 --------------------------------- */

@Composable
private fun PrecipCard(modifier: Modifier = Modifier, today: Double, tomorrow: String) {
    val TextPrimary = MaterialTheme.colorScheme.onSurface
    val TextSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val AccentCyan = MaterialTheme.colorScheme.primary

    DarkCard(modifier = modifier) {
        Text("降水", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text("${today} mm", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("今日降水量", color = TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Text("🔮 $tomorrow", color = AccentCyan, fontSize = 11.sp)
    }
}

@Composable
private fun VisibilityCard(modifier: Modifier = Modifier, visibilityKm: Int) {
    val TextPrimary = MaterialTheme.colorScheme.onSurface
    val TextSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    val eval = when {
        visibilityKm >= 10 -> "能见度很好，视野清晰"
        visibilityKm >= 4 -> "能见度一般，远眺受限"
        else -> "能见度较差，出行注意安全"
    }
    DarkCard(modifier = modifier) {
        Text("能见度", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text("$visibilityKm km", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("可视距离", color = TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Text(eval, color = TextSecondary, fontSize = 11.sp)
    }
}

/* --------------------------------- 模块 F：月相 --------------------------------- */

@Composable
private fun MoonCard(overview: WeatherOverview) {
    val TextPrimary = MaterialTheme.colorScheme.onSurface
    val TextSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    DarkCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 月相图占位（emoji；接入真实服务后可替换月相图片资源）
            Text(overview.moonIcon, fontSize = 52.sp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${overview.moonPhase} · 照射范围 ${overview.moonIllumination}%",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text("🌙 下次月落：${overview.moonSet}", color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text("🌕 下次满月：${overview.nextFullMoon}", color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

/* --------------------------------- 模块 G：湿度 / 气压 --------------------------------- */

@Composable
private fun HumidityCard(modifier: Modifier = Modifier, humidity: Int, dewPoint: Int) {
    val TextPrimary = MaterialTheme.colorScheme.onSurface
    val TextSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val AccentCyan = MaterialTheme.colorScheme.primary
    val TrackColor = MaterialTheme.colorScheme.outlineVariant

    DarkCard(modifier = modifier) {
        Text("湿度", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text("$humidity%", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)) {
            drawRoundRect(
                color = TrackColor,
                size = androidx.compose.ui.geometry.Size(size.width, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            )
            drawRoundRect(
                color = AccentCyan,
                size = androidx.compose.ui.geometry.Size(
                    size.width * (humidity / 100f).coerceIn(0f, 1f),
                    size.height,
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text("露点温度 $dewPoint℃", color = TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun PressureCard(modifier: Modifier = Modifier, pressureInHg: Double) {
    val TextPrimary = MaterialTheme.colorScheme.onSurface
    val TextSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val AccentCyan = MaterialTheme.colorScheme.primary
    val TrackColor = MaterialTheme.colorScheme.outlineVariant

    // 仪表盘量程 28.5 ~ 31.0 inHg（标准大气压 29.92）
    val fraction = ((pressureInHg - 28.5) / (31.0 - 28.5)).toFloat().coerceIn(0f, 1f)
    val hpa = (pressureInHg * 33.8639).toInt()
    DarkCard(modifier = modifier) {
        Text("气压", color = TextSecondary, fontSize = 12.sp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val sweep = 180f * fraction
                // 表盘轨道（上半圆：Android 画布 0°在东、顺时针为正，180°起画 180° 经过上方到达 0°）
                drawArc(
                    color = TrackColor,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                )
                // 当前气压值弧
                drawArc(
                    color = AccentCyan,
                    startAngle = 180f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                )
                // 指针（最左 180°，顺时针经过正上 270°，最右 360°/0°）
                val angle = Math.toRadians(180.0 + sweep.toDouble())
                val r = size.minDimension / 2 - 10.dp.toPx()
                drawLine(
                    color = Color.White,
                    start = center,
                    end = Offset(
                        center.x + r * cos(angle).toFloat(),
                        center.y + r * sin(angle).toFloat(),
                    ),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawCircle(color = Color.White, radius = 3.dp.toPx(), center = center)
            }
        }
        Text(
            "$pressureInHg inHg · $hpa hPa",
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

/* --------------------------------- 模块 H：生活出行提示 --------------------------------- */

@Composable
private fun LifeTipsCard(overview: WeatherOverview) {
    val TextPrimary = MaterialTheme.colorScheme.onSurface

    val tips = overview.lifeTips
    DarkCard {
        Text("出行小贴士", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        TipRow(emoji = "💧", text = tips.humidityText)
        TipRow(emoji = "👕", text = "穿衣推荐：${tips.dress}")
        TipRow(emoji = "🧳", text = "出行提示：${tips.travelTip}")
        TipRow(emoji = "⚠️", text = "旅游避坑：${tips.pitTip}")
        TipRow(emoji = "🐾", text = "宠物出行：${tips.petTip}")
    }
}

@Composable
private fun TipRow(emoji: String, text: String) {
    val TextSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    Row(modifier = Modifier.padding(vertical = 5.dp)) {
        Text(emoji, fontSize = 15.sp)
        Spacer(Modifier.width(8.dp))
        Text(text, color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

/* ===================================================================================== */
/*                                      单日天气详情页                                      */
/* ===================================================================================== */

/**
 * 7 日预报条目点击进入的单日详情页（二级页，无底部导航栏）。
 *
 * @param dayIndex 该天在 7 日列表中的下标
 */
@Composable
fun WeatherDayDetailScreen(
    dayIndex: Int,
    weatherViewModel: WeatherViewModel,
    onBack: () -> Unit,
) {
    val WeatherBg = MaterialTheme.colorScheme.background
    val WeatherCardAlt = MaterialTheme.colorScheme.surfaceVariant
    val TextPrimary = MaterialTheme.colorScheme.onSurface
    val TextSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    val state by weatherViewModel.uiState.collectAsStateWithLifecycle()
    val overview = state.overview
    val day = overview?.days?.getOrNull(dayIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WeatherBg),
    ) {
        // 顶部导航栏（返回 + 标题）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = TextPrimary,
                )
            }
            Text(
                text = day?.let { "${it.week}天气详情" } ?: "单日天气详情",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        when {
            state.loading && overview == null -> WeatherLoadingState()
            day == null -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("该日天气数据暂不可用", color = TextSecondary)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onBack) { Text("返回") }
                }
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // —— 当日总览 ——
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = WeatherCardAlt),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("${day.week} · ${day.dateText}", color = TextSecondary, fontSize = 13.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(day.weatherText, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text("${day.lowTemp}° ~ ${day.highTemp}°", color = TextSecondary, fontSize = 14.sp)
                        }
                        Text(day.iconEmoji, fontSize = 60.sp)
                    }
                }

                // 高降雨概率预警
                if (day.rainProb >= 50) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0x33FFB300)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Text(
                            "🌧️ 当日降雨概率 ${day.rainProb}%，出门请带伞，户外行程注意防滑",
                            color = Color(0xFFFFCC80),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }

                // —— 昼夜温度 ——
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailValueCard(modifier = Modifier.weight(1f), title = "白天最高温", value = "${day.highTemp}°", sub = day.dayWeather)
                    DetailValueCard(modifier = Modifier.weight(1f), title = "夜间最低温", value = "${day.lowTemp}°", sub = day.nightWeather)
                }
                // —— 降水概率 / 紫外线 ——
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailValueCard(modifier = Modifier.weight(1f), title = "降水概率", value = "${day.rainProb}%", sub = if (day.rainProb > 0) "建议携带雨具" else "降水可能性低")
                    val (uvText, _) = uvLevel(day.uvIndex)
                    DetailValueCard(modifier = Modifier.weight(1f), title = "紫外线", value = "${day.uvIndex} · $uvText", sub = "白天 ${day.dayWeather}")
                }

                // —— 风力 ——
                DarkCard {
                    Text("风力风向", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    // 风向数据可能自带「风」字（模拟数据）也可能不带（高德接口），展示时去重
                    val windText = if (day.windDir.endsWith("风")) day.windDir else "${day.windDir}风"
                    Text("$windText ${day.windPower}", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                }

                // —— 日出日落（取当日所在城市通用值） ——
                DarkCard {
                    Text("日出日落", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("🌅 日出 ${overview.sunrise}", color = TextPrimary, fontSize = 14.sp)
                        Text("🌇 日落 ${overview.sunset}", color = TextPrimary, fontSize = 14.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun DetailValueCard(modifier: Modifier = Modifier, title: String, value: String, sub: String) {
    val TextPrimary = MaterialTheme.colorScheme.onSurface
    val TextSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    DarkCard(modifier = modifier) {
        Text(title, color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(sub, color = TextSecondary, fontSize = 11.sp)
    }
}
