package com.example.citygo.skeleton.screens

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditLocation
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.citygo.skeleton.data.TravelTaskState
import com.example.citygo.skeleton.data.model.AiPlanOptions
import com.example.citygo.skeleton.data.AppViewModel
import com.example.citygo.skeleton.data.AppViewModel.Companion.coverKey
import com.example.citygo.skeleton.share.CommunityImage
import com.example.citygo.skeleton.share.ImageFit
import com.example.citygo.skeleton.data.WeatherViewModel
import com.example.citygo.skeleton.data.model.Place
import com.example.citygo.skeleton.data.model.PlaceType
import com.example.citygo.skeleton.screens.agent.PlanChatAssistantDialog
import com.example.citygo.skeleton.screens.plan.HotelPickerDialog
import com.example.citygo.skeleton.screens.plan.PlanCityPickerDialog
import com.example.citygo.skeleton.screens.plan.TripConfigDialog
import com.example.citygo.skeleton.screens.weather.WeatherPage

/* =====================================================================================
 * 出行计划 Tab
 *
 * 页面顶部内置 Material3 TabRow，包含两个子页面：
 *   子 Tab 1【出行计划】：订阅全局 [AppViewModel.currentTravelPlan]（Room 持久化，
 *                        与景点详情页「加入行程」写入的是同一份行程），按 D1/D2…多日分组
 *                        展示地点；支持清空行程、空状态引导、点击点位 / 「查看地图点位」
 *                        携带点位信息跳转地图 Tab。
 *   子 Tab 2【天气预报】：通过 [WeatherViewModel] → WeatherRepository 获取高德天气预报。
 *
 * 注意：本页不包含任何高德导航 / 天气预报请求逻辑，天气子页代码保持原样。
 * ===================================================================================== */

/**
 * @param onViewMapPoints 点击「查看地图点位」回调：由导航层切换到底部地图 Tab
 * @param appViewModel 全局共享 VM：当前行程数据源 + 清空行程 + 点位聚焦事件
 * @param weatherViewModel 天气预报 ViewModel（由导航层注入，本步不改动其任何逻辑）
 * @param onWeatherDayClick 点击天气 7 日预报某条：由导航层跳转单日天气详情页，参数为天数下标
 * @param onPlanPointClick 点击行程中某个地点条目：把点位传递给地图页面并切换地图 Tab
 * @param onGoRecommend 某天卡片「加入景点」回调：暂存目标天后由导航层切到「推荐」Tab 选景点
 */
@Composable
fun PlanScreen(
    onViewMapPoints: () -> Unit,
    appViewModel: AppViewModel,
    weatherViewModel: WeatherViewModel,
    onWeatherDayClick: (Int) -> Unit,
    onPlanPointClick: (Place) -> Unit,
    onGoRecommend: () -> Unit,
    modifier: Modifier = Modifier,
    requestPlanning: Boolean = false,
    onPlanningRequestHandled: () -> Unit = {},
) {
    // 子 Tab 下标：0=出行计划，1=天气预报；rememberSaveable 保证切 Tab / 转屏后保留
    var selectedSubIndex by rememberSaveable { mutableIntStateOf(0) }
    val subTabs = listOf("我的行程", "天气预报")
    LaunchedEffect(requestPlanning) { if (requestPlanning) selectedSubIndex = 0 }

    // 从地图 Marker 信息卡点「查看行程卡片」跳回时，强制切回「出行计划」子 Tab
    val planFocusPlace by appViewModel.planFocusPlace.collectAsStateWithLifecycle()
    LaunchedEffect(planFocusPlace?.id) {
        if (planFocusPlace != null) selectedSubIndex = 0
    }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedSubIndex, containerColor = MaterialTheme.colorScheme.background) {
            subTabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedSubIndex == index,
                    onClick = { selectedSubIndex = index },
                    text = { Text(title) },
                )
            }
        }
        when (selectedSubIndex) {
            0 -> TripPlanSubPage(
                appViewModel = appViewModel,
                onViewMapPoints = onViewMapPoints,
                onPlanPointClick = onPlanPointClick,
                onGoRecommend = onGoRecommend,
                requestPlanning = requestPlanning,
                onPlanningRequestHandled = onPlanningRequestHandled,
            )
            1 -> WeatherForecastSubPage(
                weatherViewModel = weatherViewModel,
                onWeatherDayClick = onWeatherDayClick,
            )
        }
    }
}

/* ================================ 子 Tab 1：出行计划 ================================ */

/**
 * 出行计划子页：订阅全局当前行程，按天分组罗列全部地点。
 */
@Composable
private fun TripPlanSubPage(
    appViewModel: AppViewModel,
    onViewMapPoints: () -> Unit,
    onPlanPointClick: (Place) -> Unit,
    onGoRecommend: () -> Unit,
    requestPlanning: Boolean,
    onPlanningRequestHandled: () -> Unit,
) {
    val context = LocalContext.current
    // 全局行程（景点详情页「加入行程」写入；Room 持久化，App 重启不丢失）
    // 与地图页订阅同一个全局 TripPlan（城市 / 天数 / 预算 / 偏好 / 按天景点），两页数据一致
    val tripPlan by appViewModel.tripPlan.collectAsStateWithLifecycle()
    // 当前城市（我的页城市选择器写入；作为 AI 攻略生成的目的地）
    val currentCity by appViewModel.currentCity.collectAsStateWithLifecycle()
    // AI 地点封面图地址表（确认采纳攻略后异步填充，图片到达后卡片自动刷新；无图的地点不显示图）
    val placeCoverMap by appViewModel.placeCoverMap.collectAsStateWithLifecycle()
    // 「优先宠物友好地点」复选框勾选状态
    val aiPetFriendly by appViewModel.aiPetFriendlyPreferred.collectAsStateWithLifecycle()

    // 行程配置弹窗 → 后端攻略异步任务状态（POST 拿 taskId → 后台 1.5s 轮询 → 自动同步）
    val travelTaskState by appViewModel.travelTaskState.collectAsStateWithLifecycle()
    // 「正在生成攻略」提示弹窗显隐：点【我知道了】只关弹窗，后台轮询继续
    val showGeneratingPrompt by appViewModel.showGeneratingPrompt.collectAsStateWithLifecycle()
    // 仅 POST 提交期间让概览头与配置弹窗按钮显示加载态；轮询在后台进行，不锁页面
    val isAiGenerating = travelTaskState is TravelTaskState.Submitting

    // 每日行程严格按第 1 天 → 第 N 天排列（景点按 dayIndex 分好组）
    val dailyPlans = tripPlan.dailyPlans
    val hasPlaces = tripPlan.allPlaces.isNotEmpty()

    // 地图 Marker 信息卡「查看行程卡片」→ 滚动到对应天卡片并短暂高亮该点位
    val planListState = rememberLazyListState()
    val focusPoint by appViewModel.planFocusPlace.collectAsStateWithLifecycle()
    var highlightPointId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(focusPoint?.id) {
        val focus = focusPoint ?: return@LaunchedEffect
        // 前面固定 4 个 item（trip_sync / city_step / title_row / overview）。
        val targetIndex = 4 + (focus.dayIndex - 1).coerceAtLeast(0)
        runCatching { planListState.animateScrollToItem(targetIndex) }
        highlightPointId = focus.id
        appViewModel.consumePlanFocusPlace()
        kotlinx.coroutines.delay(HIGHLIGHT_VISIBLE_MS)
        highlightPointId = null
    }

    // AI 助手对话状态（聊天记录 / loading / 弹窗显隐；仅内存，关闭弹窗不丢记录）
    val chatMessages by appViewModel.chatMessageList.collectAsStateWithLifecycle()
    val chatLoading by appViewModel.chatLoading.collectAsStateWithLifecycle()
    val showChatDialog by appViewModel.showChatDialog.collectAsStateWithLifecycle()
    val proposalSyncingMessageId by appViewModel.proposalSyncingMessageId.collectAsStateWithLifecycle()

    // 对话提议同步成功 / 失败的一次性 Toast
    LaunchedEffect(Unit) {
        appViewModel.chatToastEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // 「规划第一步」弹窗状态：城市选择弹窗 → 行程配置表单（选城市只弹表单，不直接生成行程）
    var showCityPicker by remember { mutableStateOf(false) }
    var showTripConfig by remember { mutableStateOf(false) }
    LaunchedEffect(requestPlanning) {
        if (requestPlanning) { showTripConfig = true; onPlanningRequestHandled() }
    }
    val scrollScope = rememberCoroutineScope()

    // 「为第 X 天添加酒店」弹窗：null 表示关闭；非 null 时记住要加入的天数
    var hotelPickerDay by remember { mutableStateOf<Int?>(null) }
    val hotelSearchState by appViewModel.hotelSearchState.collectAsStateWithLifecycle()

    // 行程配置表单字段：以 currentCity 为 remember 键——切换城市时自动重置
    // （天数回 1、预算清空、偏好清空），对应「换城市清空天数配置」要求。
    var tripDays by remember(currentCity) { mutableStateOf(AiPlanOptions.MIN_DAYS) }
    var tripBudgetText by remember(currentCity) { mutableStateOf("") }
    var tripPrefs by remember(currentCity) { mutableStateOf(emptySet<String>()) }
    // 额外备注（选填，切换城市一并清空）
    var tripRemark by remember(currentCity) { mutableStateOf("") }

    // 进入轮询 / 失败 / 成功（含同步直接成功）/ 地点无法匹配时关闭配置表单，
    // 具体结果由各自独立弹窗承接（成功弹窗展示后端 data 攻略原文）。
    // 成功时 VM 已自动把行程写入 currentTravelPlan，DAY 列表直接刷新，无需这里处理。
    LaunchedEffect(travelTaskState) {
        when (travelTaskState) {
            is TravelTaskState.Polling,
            is TravelTaskState.Failed,
            is TravelTaskState.Succeeded,
            TravelTaskState.GuideReadyButNoMatch,
            -> showTripConfig = false
            else -> Unit
        }
    }

    // Box 只用于叠加悬浮按钮与挂载对话弹窗；内部 LazyColumn 的 item 结构 / 顺序完全不动
    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        state = planListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 94.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // —— 规划第一步：选择目的地城市（入口由「我的」页迁移至此，全量城市搜索） ——
        item(key = "trip_sync") {
            com.example.citygo.skeleton.screens.plan.TripSyncPanel(appViewModel)
        }
        item(key = "city_step") {
            CityStepCard(
                cityName = currentCity,
                hasPlaces = hasPlaces,
                onPickCity = { showCityPicker = true },
            )
        }

        // —— 标题行 + 右上角「清空行程」（仅有行程时展示） ——
        item(key = "title_row") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "我的行程",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (hasPlaces) {
                    TextButton(onClick = {
                        appViewModel.clearPlan()
                        Toast.makeText(context, "行程已清空", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("清空行程")
                    }
                }
            }
        }

        // —— 行程概览头（目的地 / 天数 / 点位数 + 查看地图点位） ——
        item(key = "overview") {
            PlanOverviewHeader(
                cityName = tripPlan.city.ifBlank { currentCity },
                days = dailyPlans.size,
                pointCount = tripPlan.allPlaces.size,
                hasPlaces = hasPlaces,
                onViewMapPoints = onViewMapPoints,
                isAiGenerating = isAiGenerating,
                isPetFriendly = aiPetFriendly,
                onPetFriendlyChange = appViewModel::setAiPetFriendlyPreferred,
                onGenerateAiPlan = {
                    // 不再前端本地生成攻略：仅唤起既有的行程配置弹窗，
                    // 用户在弹窗里填好参数点「生成 AI 攻略」后，由 ViewModel 请求后端唯一接口。
                    showTripConfig = true
                },
            )
            if (dailyPlans.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    dailyPlans.forEach { day ->
                        FilterChip(selected = false, onClick = {
                            scrollScope.launch { planListState.animateScrollToItem(4 + dailyPlans.indexOf(day)) }
                        }, label = { Text("第 ${day.day} 天") })
                    }
                }
            }
        }

        // —— 空行程引导 ——
        if (!hasPlaces) {
            item(key = "empty") {
                EmptyPlanState()
            }
        }

        // —— 逐日卡片：D1 / D2… 每天一张卡，卡内罗列当天全部景点，可单独移除 / 为该天加景点 ——
        items(dailyPlans, key = { it.day }) { dayPlan ->
            DayPlanCard(
                day = dayPlan.day,
                points = dayPlan.places,
                cityName = tripPlan.city.ifBlank { currentCity },
                coverMap = placeCoverMap,
                highlightPointId = highlightPointId,
                onPointClick = onPlanPointClick,
                onRemovePoint = { place ->
                    // 删除某天景点：ViewModel 立即更新全局 TripPlan（含落库），地图页同步
                    appViewModel.removePlaceFromPlan(place.id)
                    Toast.makeText(context, "已移除「${place.name}」", Toast.LENGTH_SHORT).show()
                },
                onAddSpotToDay = { day ->
                    // 为第 N 天加入景点：暂存目标天后跳到「推荐」页，景点详情点「加入行程」即写入该天
                    appViewModel.setPendingAddDay(day)
                    onGoRecommend()
                },
                onAddHotelToDay = { day ->
                    // 为第 N 天添加酒店：打开真实酒店 POI 搜索弹窗（同天已有酒店将被替换）
                    hotelPickerDay = day
                },
            )
        }
    }

        // —— 唯一新增 UI：右下角【AI助手】悬浮按钮（不改动上方任何既有组件位置） ——
        ExtendedFloatingActionButton(
            onClick = appViewModel::openChatDialog,
            icon = {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            },
            text = { Text("想调整？告诉 AI 你的想法") },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .fillMaxWidth(),
        )

        // 豆包对话弹窗（聊天记录保存在内存；模型回传的新行程以「待确认提议」挂在助手消息上，
        // 用户点击「同步修改行程」后才更新 DAY 列表）
        PlanChatAssistantDialog(
            visible = showChatDialog,
            messages = chatMessages,
            loading = chatLoading,
            syncingMessageId = proposalSyncingMessageId,
            onDismiss = appViewModel::closeChatDialog,
            onSend = appViewModel::sendChatMessage,
            onApplyProposal = appViewModel::applyChatProposal,
            onDeclineProposal = appViewModel::declineChatProposal,
        )

        // 规划第一步：城市选择弹窗（全国城市库 + 任意关键字；选定后只弹配置表单，不生成行程）
        PlanCityPickerDialog(
            visible = showCityPicker,
            currentCityName = currentCity,
            onCitySelected = { name ->
                // 更新全局目的地城市（DataStore 持久化），随后进入行程配置表单
                appViewModel.selectCity(name)
                showCityPicker = false
                showTripConfig = true
            },
            onDismiss = { showCityPicker = false },
        )

        // 行程配置表单：填写天数 / 预算 / 偏好 / 宠物 / 备注后，点「生成 AI 攻略」
        // POST 后端 /travel/generateTask 拿 taskId（baseUrl 取 ServerConfig，当前 adb reverse
        // http://127.0.0.1:8080/）；拿到 taskId 后由 VM 在后台轮询，取消不做任何事
        TripConfigDialog(
            visible = showTripConfig,
            cityName = currentCity,
            // 仅 POST 提交期间禁用按钮防重复提交；后台轮询不锁该弹窗（弹窗此时已关闭）
            isGenerating = travelTaskState is TravelTaskState.Submitting,
            days = tripDays,
            onDaysChange = { tripDays = it },
            budgetText = tripBudgetText,
            onBudgetChange = { tripBudgetText = it },
            selectedPrefs = tripPrefs,
            onTogglePref = { pref ->
                tripPrefs = if (pref in tripPrefs) tripPrefs - pref else tripPrefs + pref
            },
            petFriendly = aiPetFriendly,
            onPetFriendlyChange = appViewModel::setAiPetFriendlyPreferred,
            remark = tripRemark,
            onRemarkChange = { tripRemark = it },
            onConfirm = {
                // 全部字段（含额外备注）打包，提交后端任务接口；JWT 由网络拦截器自动带 Bearer
                val options = AiPlanOptions(
                    days = tripDays,
                    budgetYuan = tripBudgetText.trim().toIntOrNull(),
                    preferences = tripPrefs.toList(),
                    petFriendly = aiPetFriendly || AiPlanOptions.PREF_PET in tripPrefs,
                    remark = tripRemark.trim().ifBlank { null },
                )
                appViewModel.submitTravelGuideTask(currentCity, options)
            },
            onChangeCity = {
                showTripConfig = false
                showCityPicker = true
            },
            onDismiss = { showTripConfig = false },
        )

        // 拿到 taskId 后弹出「正在生成攻略」提示：
        // 点【我知道了】（或点遮罩 / 返回键）只关闭本弹窗，后台 1.5s 轮询继续执行、不中断；
        // 用户可自由切 Tab / 退出本页，完成后 VM 自动同步行程并全局 Toast「已成功生成攻略」。
        if (travelTaskState is TravelTaskState.Polling && showGeneratingPrompt) {
            AlertDialog(
                onDismissRequest = appViewModel::dismissGeneratingPrompt,
                title = { Text("AI 攻略生成任务", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("正在生成攻略", fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "后台生成大约需要几十秒。点击「我知道了」关闭本提示后，" +
                                "攻略仍会继续生成，完成后会自动同步到【我的行程】并提醒你，" +
                                "无需停留在本页等待。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = appViewModel::dismissGeneratingPrompt) {
                        Text("我知道了")
                    }
                },
            )
        }

        // 提交失败 / status 业务失败 / 300s 本地安全上限超时：弹窗展示【具体原因】，不用笼统提示。
        // 超时时 message 即「攻略生成超时，请重试」（单次轮询网络抖动不弹窗，后台继续轮询）。
        (travelTaskState as? TravelTaskState.Failed)?.let { failed ->
            AlertDialog(
                onDismissRequest = appViewModel::resetTravelGuideTask,
                title = { Text("攻略生成失败", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = failed.message,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    )
                },
                confirmButton = {
                    TextButton(onClick = appViewModel::resetTravelGuideTask) {
                        Text("我知道了")
                    }
                },
            )
        }

        // 攻略生成 + 行程同步成功：弹窗展示后端返回的 data 攻略原文（识别换行、可纵向滚动）。
        // 此时行程点位已自动导入【我的行程】并在地图打点；点【我知道了】关闭弹窗。
        (travelTaskState as? TravelTaskState.Succeeded)?.let { succeeded ->
            AlertDialog(
                onDismissRequest = appViewModel::resetTravelGuideTask,
                title = { Text("✅ 已成功生成攻略", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = succeeded.guide.ifBlank { "（攻略内容为空）" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    )
                },
                confirmButton = {
                    TextButton(onClick = appViewModel::resetTravelGuideTask) {
                        Text("我知道了")
                    }
                },
            )
        }

        // 地理解析降级（不是接口失败）：攻略文字已生成，但提取到的地点在高德地图一个都没
        // 匹配上（0 个「- 」地点或全部地理编码失败）。既有行程保持不变，提示可手动添加景点。
        if (travelTaskState is TravelTaskState.GuideReadyButNoMatch) {
            AlertDialog(
                onDismissRequest = appViewModel::resetTravelGuideTask,
                title = { Text("攻略已生成", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "✅ 攻略生成成功！识别的地点无法在地图匹配，" +
                            "文字攻略已生成，可手动添加景点。",
                    )
                },
                confirmButton = {
                    TextButton(onClick = appViewModel::resetTravelGuideTask) {
                        Text("我知道了")
                    }
                },
            )
        }

        // 为第 X 天添加酒店：高德真实住宿 POI 搜索；选中即写入当天（已有酒店自动替换）
        HotelPickerDialog(
            visible = hotelPickerDay != null,
            day = hotelPickerDay,
            cityName = tripPlan.city.ifBlank { currentCity },
            state = hotelSearchState,
            onSearch = appViewModel::searchHotels,
            onSelect = { hotel ->
                val targetDay = hotelPickerDay
                if (targetDay != null) {
                    val replaced = appViewModel.addHotelToPlan(targetDay, hotel)
                    Toast.makeText(
                        context,
                        if (replaced) "已替换第 $targetDay 天酒店为「${hotel.name}」"
                        else "已为第 $targetDay 天添加酒店「${hotel.name}」",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                hotelPickerDay = null
                appViewModel.resetHotelSearch()
            },
            onDismiss = {
                hotelPickerDay = null
                appViewModel.resetHotelSearch()
            },
        )
    }
}

/**
 * 规划第一步：选择目的地城市入口卡。
 *
 * 入口已从「我的」页迁移到出行计划页首页；点击打开全国城市选择弹窗，
 * 选定城市后进入行程配置表单，而不是直接生成 AI 行程。
 */
@Composable
private fun CityStepCard(
    cityName: String,
    hasPlaces: Boolean,
    onPickCity: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${cityName}漫游", style = MaterialTheme.typography.headlineSmall)
            Text("让每一天，都按自己的节奏", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onPickCity) {
            Icon(Icons.Filled.EditLocation, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp)); Text("换城市")
        }
    }
}

/**
 * 空行程状态：暂无行程，可从景点详情页「加入行程」。
 */
@Composable
private fun EmptyPlanState() {
    Card(
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "暂无行程，可从景点加入行程",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "去「发现」页打开任意景点详情，点击「加入行程」\n加入后可在此按天查看，并在地图上定位点位",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 攻略概览头：目的地、天数、点位数 + 查看地图点位按钮（无行程时禁用）+ AI 生成新计划按钮。
 */
@Composable
private fun PlanOverviewHeader(
    cityName: String,
    days: Int,
    pointCount: Int,
    hasPlaces: Boolean,
    onViewMapPoints: () -> Unit,
    isAiGenerating: Boolean,
    isPetFriendly: Boolean,
    onPetFriendlyChange: (Boolean) -> Unit,
    onGenerateAiPlan: () -> Unit,
) {
    Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (hasPlaces) "$days 天 · $pointCount 个地点" else "你的下一段旅行", style = MaterialTheme.typography.titleMedium)
                    Text(if (hasPlaces) "查看路线，或让 AI 调整安排" else "选择偏好，生成专属城市行程",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (hasPlaces) IconButton(onClick = onViewMapPoints) { Icon(Icons.Filled.Map, "查看地图点位", tint = MaterialTheme.colorScheme.primary) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("优先宠物友好地点", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = isPetFriendly, onCheckedChange = onPetFriendlyChange, enabled = !isAiGenerating)
            }
            Button(onClick = onGenerateAiPlan, enabled = !isAiGenerating, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(if (isAiGenerating) "正在提交…" else if (hasPlaces) "重新规划旅行" else "AI 规划我的旅行")
            }
        }
    }
}

/**
 * 单日行程卡片：头部显示 D{n} 与点位数，下方时间线式罗列景点，底部可「为该天加入景点」。
 *
 * @param coverMap AI 景点封面图地址表（城市|地点名 → 图片 URL）；
 *        仅 AI 生成且搜到实拍图的地点有图，普通「加入行程」的地点渲染与以前完全一致。
 * @param onRemovePoint 移除该天某个景点（同步全局 TripPlan / 地图）
 * @param onAddSpotToDay 点击「加入景点」：去推荐页选景点并加入到第 [day] 天
 * @param onAddHotelToDay 点击「添加酒店」：打开真实酒店 POI 搜索弹窗加入到第 [day] 天
 */
@Composable
private fun DayPlanCard(
    day: Int,
    points: List<Place>,
    cityName: String,
    coverMap: Map<String, String>,
    highlightPointId: Long?,
    onPointClick: (Place) -> Unit,
    onRemovePoint: (Place) -> Unit,
    onAddSpotToDay: (Int) -> Unit,
    onAddHotelToDay: (Int) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 日期头
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = "D$day",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "第 $day 天 · ${points.size} 个点位",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))

            // 当日景点列表（时间线样式：左侧序号圆点 + 竖线；整行可点击跳地图定位；右侧可移除）
            points.forEachIndexed { index, point ->
                PointTimelineRow(
                    point = point,
                    showLine = index != points.lastIndex,
                    highlighted = highlightPointId == point.id,
                    // 优先用已落库封面（重启可恢复），其次用内存增量配图 Map
                    coverUrl = point.coverUrl ?: coverMap[coverKey(cityName, point.name)],
                    onClick = { onPointClick(point) },
                    onRemove = { onRemovePoint(point) },
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onAddSpotToDay(day) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.AddCircleOutline, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("添加景点")
                }
                OutlinedButton(onClick = { onAddHotelToDay(day) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Hotel, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("添加酒店")
                }
            }

        }
    }
}

/**
 * 时间线中的单个景点行：类型图标 + 名称 / 分类 + 游玩提示 + 经纬度；
 * AI 生成并搜到实拍图的景点，在文字下方追加店铺图片（加载失败自动显示默认占位图）。
 * 整行可点击：把该点位信息（名称 / GCJ-02 经纬度 / 宠物友好等）传递给地图页面。
 * 右侧「移除」按钮：从该天删除此景点并同步全局 TripPlan（独立点击，不触发行跳转）。
 */
@Composable
private fun PointTimelineRow(
    point: Place,
    showLine: Boolean,
    coverUrl: String?,
    highlighted: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (highlighted) {
                    Modifier
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            RoundedCornerShape(12.dp),
                        )
                        .border(
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                            RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
    ) {
        // 左侧时间线：类型圆点 + 连接竖线
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(point.placeType.color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = point.placeType.emoji, style = MaterialTheme.typography.titleMedium)
            }
            if (showLine) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(34.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // 右侧景点信息
        Column(modifier = Modifier
            .weight(1f)
            .padding(bottom = if (showLine) 10.dp else 0.dp)) {
            // 仅 UI 层字符串拆分（不改数据 / 接口）：后端可能把「名称 + 时间 + 门票 + 介绍…」
            // 塞在同一条文本里，这里把纯地点名剥给标题，其余内容全部切到下方独立描述块。
            val cardContent = remember(point) { splitPointCardText(point) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    // 主标题【仅地点名称】：已剔除上午/中午/下午前缀与时间/门票/介绍等详情
                    text = cardContent.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                // 来源标签：数据库预置点位【本地库推荐】/ AI 额外拓展点位【AI生成推荐】
                // Tags sit on their own line below the title.
                // 移除该景点：独立点击区域，不触发整行的地图跳转
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "移除该景点",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            // ② 独立描述块（在标题下方，不是标题！）：
            //    从原拼接长文本拆出的全部详情 + 结构化字段，逐条分行、自动换行、不省略；
            //    没有的字段自动隐藏，不留空行；图片在描述块之后（见下方）。
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SourceTag(fromLocalDb = point.fromLocalDb)
                PointTypeTag(placeType = point.placeType)
            }
            var detailsExpanded by remember(point.id) { mutableStateOf(false) }
            TextButton(onClick = { detailsExpanded = !detailsExpanded }, contentPadding = PaddingValues(0.dp)) {
                Text(if (detailsExpanded) "收起地点详情" else "查看介绍与游玩提示", style = MaterialTheme.typography.labelMedium)
            }
            if (detailsExpanded) SpotExtraInfoRows(point = point, parsed = cardContent)
            // AI 地点实拍图：只有搜到图的地点才渲染（无图 / 手动加入的地点布局保持原样）；
            // CommunityImage 内置加载中转圈与「图片加载失败」默认占位图
            if (coverUrl != null) {
                Spacer(Modifier.height(6.dp))
                CommunityImage(
                    data = coverUrl,
                    fit = ImageFit.Fill,
                    cornerShape = RoundedCornerShape(12.dp),
                    contentDescription = cardContent.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                )
            }
        }
    }
}

/** 地点类型小标签：严格四类【景点】/【餐饮】/【住宿】/【购物】，随类型配色 */
@Composable
private fun PointTypeTag(placeType: PlaceType) {
    Surface(
        shape = RoundedCornerShape(50),
        color = placeType.color.copy(alpha = 0.15f),
    ) {
        Text(
            text = placeType.tagLabel,
            style = MaterialTheme.typography.labelSmall,
            color = placeType.color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * 点位来源小标签：数据库预置点位 →【本地库推荐】（青色）；AI 额外拓展点位 →【AI生成推荐】
 * （紫色）。禁止统一写成「来自 AI 生成攻略的推荐地点」。
 */
@Composable
private fun SourceTag(fromLocalDb: Boolean) {
    val color = if (fromLocalDb) Color(0xFF0E9488) else Color(0xFF53795C)
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
    ) {
        Text(
            text = if (fromLocalDb) "本地库推荐" else "AI生成推荐",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * 卡片文本拆分结果（仅 UI 层使用，不写回数据模型）：
 * @property title 纯地点名称（已剔除时段前缀与全部详情）
 * @property details 从拼接长文本里拆出的详情：标准标签（时间/门票/介绍…）→ 原文内容
 */
private data class ParsedPointCard(
    val title: String,
    val details: Map<String, String>,
)

/**
 * 【纯 UI 字符串拆分，不改接口 / 数据 / 解析】
 * 后端可能把「地点名 + 时间：… + 门票：… + 介绍：…」塞在同一条字符串里，
 * 直接当标题会因过长被单行省略号折叠。这里：
 * 1. 扫描「标签：值」锚点，第一个锚点之前的文本 = 纯地点名；
 * 2. 地点名剔除行首「上午：/中午：/下午：」等时段前缀与【】标签；
 * 3. 锚点之后的内容按标签切段，全部交给标题下方独立描述块分行展示。
 */
private fun splitPointCardText(point: Place): ParsedPointCard {
    val raw = point.name.replace(Regex("\\s+"), " ").trim()
    if (raw.isBlank()) return ParsedPointCard(point.name, emptyMap())

    // 标准显示标签 → 原文可能出现的标签词（长词在前，避免「门票」抢先匹配「门票信息」）
    val labelWords: List<Pair<String, List<String>>> = listOf(
        "时间" to listOf("时间段", "游玩时间", "营业时间", "开放时间", "时间"),
        "门票" to listOf("门票信息", "门票价格", "门票费用", "门票", "票价"),
        "人均" to listOf("人均消费", "人均价格", "人均费用", "人均", "客单价"),
        "特色菜" to listOf("特色菜品", "招牌菜品", "特色菜", "招牌菜", "推荐菜"),
        "介绍" to listOf("景点介绍", "详细介绍", "介绍", "简介"),
        "打卡提示" to listOf(
            "打卡提示", "打卡要点", "打卡攻略", "打卡点", "拍照打卡",
            "拍照提示", "拍照点", "拍照",
        ),
        "出行要点" to listOf(
            "出行要点", "注意事项", "出行贴士", "游玩贴士", "温馨提示", "贴士",
        ),
        "定位参考" to listOf("定位参考", "定位信息", "定位", "坐标", "经纬度"),
    )

    /** 一次锚点命中：原文起始位置 / 标准标签 / 值的起始位置 */
    data class Anchor(val start: Int, val label: String, val valueStart: Int)

    // 找全部锚点；同一标准标签只保留最早一次；「时间」不允许出现在地点名中间误匹配
    val anchors = mutableListOf<Anchor>()
    val seenLabels = mutableSetOf<String>()
    for ((stdLabel, words) in labelWords) {
        var earliest: Anchor? = null
        for (word in words) {
            val regex = Regex(Regex.escape(word) + """\s*[:：]""")
            val m = regex.find(raw) ?: continue
            val valueStart = m.range.last + 1
            val candidate = Anchor(start = m.range.first, label = stdLabel, valueStart = valueStart)
            if (earliest == null || candidate.start < earliest.start) earliest = candidate
        }
        if (earliest != null && seenLabels.add(stdLabel)) anchors += earliest
    }
    if (anchors.isEmpty()) {
        // 文本里没有任何「标签：」锚点：整体就是地点名，详情由结构化字段提供
        return ParsedPointCard(cleanTitle(raw), emptyMap())
    }

    val sorted = anchors.sortedBy { it.start }
    // 标题 = 第一个详情锚点之前的文本，再做时段前缀 / 标签清洗
    val title = cleanTitle(raw.substring(0, sorted.first().start))
        .ifBlank { cleanTitle(raw) }

    val details = LinkedHashMap<String, String>()
    sorted.forEachIndexed { index, anchor ->
        val segEnd = if (index + 1 < sorted.size) sorted[index + 1].start else raw.length
        val value = raw.substring(anchor.valueStart, segEnd)
            .trim()
            .trim('，', ',', ';', '；', '|', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        if (value.isNotBlank() && details[anchor.label].isNullOrBlank()) {
            details[anchor.label] = value
        }
    }
    return ParsedPointCard(title = title, details = details)
}

/** 标题清洗：去【】标签、行首时段前缀（上午：/ 中午：/ 午餐：…）与残留标点 */
private fun cleanTitle(rawTitle: String): String {
    var t = rawTitle.replace(BRACKET_TAG_REGEX, " ")
    // 去掉括号内纯时段片段，如「同盛祥（午餐12:30）」→「同盛祥」
    t = t.replace(BRACKET_TIME_REGEX, " ")
    // 去掉名称中段残留的裸时段，如「西安城墙 上午9:00-12:00」→「西安城墙」
    t = t.replace(INLINE_TIME_FRAGMENT_REGEX, " ")
    t = TITLE_TIME_PREFIX_REGEX.replace(t, " ")
    return t
        .replace(Regex("""[|｜/\\]+"""), " ")
        .trim(' ', ':', '：', '-', '—', '，', ',', '（', '(')
        .replace(Regex("\\s+"), " ")
        .trim()
}

/** 标题里的【…】标签（来源 / 类型已在标签组单独渲染，标题内不再重复） */
private val BRACKET_TAG_REGEX = Regex("""【\s*[^】]*\s*】""")

/** 括号内是时段 / 用餐时刻的片段 */
private val BRACKET_TIME_REGEX =
    Regex("""[（(]\s*(?:凌晨|早上|上午|中午|下午|傍晚|晚上|早餐|午餐|晚餐|夜宵|早茶|下午茶)?\s*\d{1,2}:\d{2}\s*(?:[-—~～]\s*\d{1,2}:\d{2})?\s*[）)]""")

/** 名称中段残留的裸时段文本（不在括号内） */
private val INLINE_TIME_FRAGMENT_REGEX =
    Regex("""(?:凌晨|早上|上午|中午|下午|傍晚|晚上|早餐|午餐|晚餐|夜宵|早茶|下午茶)\s*\d{1,2}:\d{2}\s*(?:[-—~～]\s*\d{1,2}:\d{2})?""")

/** 标题行首时段前缀：上午 / 中午 / 下午 / 午餐 12:30 等（冒号可选） */
private val TITLE_TIME_PREFIX_REGEX =
    Regex("""^\s*(?:凌晨|早上|上午|中午|下午|傍晚|晚上|早餐|午餐|晚餐|夜宵|早茶|下午茶)\s*\d{0,2}\s*[:：]?\s*\d{0,2}\s*[-—~～]?\s*\d{0,2}\s*[:：]?\s*\d{0,2}\s*""")

/**
 * 点位附属信息区（仅 UI 排版，不做任何数据加工）：
 * 数据来源 = ① 从拼接长标题里拆出的详情（[parsed]）② Place 上已有的结构化字段（① 缺失时补充）；
 * 严格按「时间 → 门票 → 人均 → 特色菜 → 介绍 → 打卡提示 → 出行要点 → 定位参考」分行；
 * 只渲染实际存在的字段，缺失自动隐藏、不留空行；每条文本【允许自动换行、不设 maxLines】，
 * 绝不出现省略号；值为「暂无相关信息」时原样弱化展示。
 */
@Composable
private fun SpotExtraInfoRows(point: Place, parsed: ParsedPointCard) {
    // 介绍取已有 intro 原文；空白视为无此字段
    val intro = point.intro.takeIf { it.isNotBlank() }
    // 定位参考始终由坐标生成（保留 mockedCoord「未精确命中」提示），不采用标题里拆出的旧文案
    val locationValue = if (point.mockedCoord) {
        "高德未精确命中，点位已保留（城市参考坐标）"
    } else {
        "${point.lat}, ${point.lng}"
    }

    data class InfoRow(val label: String, val value: String, val weak: Boolean)

    /** 优先用从长标题拆出的原文；拆不出来时回退到结构化字段 */
    fun pick(label: String, structured: String?): String? =
        parsed.details[label]?.takeIf { it.isNotBlank() }
            ?: structured?.takeIf { it.isNotBlank() }

    val rows = buildList {
        pick("时间", point.timeSlot)?.let { add(InfoRow("时间", it, false)) }
        pick("门票", point.ticket)?.let { add(InfoRow("门票", it, false)) }
        pick("人均", point.avgPrice)?.let { add(InfoRow("人均", it, false)) }
        pick("特色菜", point.signatureDish)?.let { add(InfoRow("特色菜", it, false)) }
        pick("介绍", intro)?.let { add(InfoRow("介绍", it, false)) }
        pick("打卡提示", point.photoScene)?.let { add(InfoRow("打卡提示", it, false)) }
        pick("出行要点", point.travelTip)?.let { add(InfoRow("出行要点", it, false)) }
        add(InfoRow("定位参考", locationValue, point.mockedCoord))
    }

    Spacer(Modifier.height(4.dp))
    Column {
        rows.forEach { row ->
            val noInfo = row.value.trim() == NO_INFO_TEXT
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
            ) {
                Text(
                    text = "${row.label}：",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.width(INFO_LABEL_WIDTH),
                )
                Text(
                    // 详情文本：不设置 maxLines / 不省略，长内容自动换行完整展示
                    text = row.value,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (noInfo || row.weak) {
                        MaterialTheme.colorScheme.outline
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 附属信息行标签列固定宽度（保证多行长文本对齐） */
private val INFO_LABEL_WIDTH = 64.dp

/** 后端对缺失扩展属性的统一占位文案，命中时原样展示、不做任何替换 */
private const val NO_INFO_TEXT = "暂无相关信息"

/** 地图 Marker 跳回行程卡片时，目标点位高亮持续时长（毫秒） */
private const val HIGHLIGHT_VISIBLE_MS = 4000L

/* ================================ 子 Tab 2：天气预报 ================================ */

/**
 * 天气预报子页：承载单页垂直滚动版天气页 [WeatherPage]
 * （深色 UI、无横向分页、7 日预报条目可点击进入单日详情页）。
 * 数据来自 [WeatherViewModel] → WeatherRepository（当前模拟 JSON，预留真实天气 API）。
 *
 * 本子页逻辑与请求代码完全保持原样，计划页改造不触碰天气预报任何代码。
 */
@Composable
private fun WeatherForecastSubPage(
    weatherViewModel: WeatherViewModel,
    onWeatherDayClick: (Int) -> Unit,
) {
    WeatherPage(
        weatherViewModel = weatherViewModel,
        onDayClick = onWeatherDayClick,
        modifier = Modifier.fillMaxSize(),
    )
}
