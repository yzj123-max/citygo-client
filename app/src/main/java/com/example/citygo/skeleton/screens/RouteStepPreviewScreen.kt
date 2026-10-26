package com.example.citygo.skeleton.screens

import android.util.Log
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.amap.api.navi.view.NextTurnTipView
import com.example.citygo.skeleton.screens.amap.NaviMode
import com.example.citygo.skeleton.screens.amap.NaviRouteChoiceStore
import com.example.citygo.skeleton.screens.amap.NaviTurnStep
import com.example.citygo.skeleton.screens.amap.formatMeters
import com.example.citygo.skeleton.screens.amap.formatSeconds

/* =====================================================================================
 * 路线步骤预览页
 *
 * 入参只有路线 id，路线数据来自 NaviRouteChoiceStore（NaviPath 不可序列化，不能直接传）。
 * 页面只做步骤预览，【绝不自动开始导航】；底部按钮按原始起点类型启动 GPS 或两点模拟导航。
 *
 * 转向箭头使用导航 SDK 原生控件 NextTurnTipView.setIconType(iconType)，
 * iconType 来自 AMapNaviStep.getIconType()，与高德导航内显示的箭头完全一致。
 * ===================================================================================== */

private const val TAG = "CityGoNavi"

@Composable
fun RouteStepPreviewScreen(
    routeId: Int,
    onBack: () -> Unit,
    /** 底部主按钮：从当前位置进入真实 GPS 导航 */
    onStartNavigation: (routeId: Int) -> Unit,
) {
    val plan = NaviRouteChoiceStore.latest
    val choice = NaviRouteChoiceStore.findChoice(routeId)
    val naviMode = NaviMode.forOrigin(plan?.originIsMyLocation != false)
    Log.i(
        TAG,
        "[step-preview] routeId=$routeId planFound=${plan != null} " +
            "choiceFound=${choice != null} steps=${choice?.steps?.size ?: 0}",
    )

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF2F4F7))) {
        // ---------- 顶部栏 ----------
        Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回路线选择",
                    modifier = Modifier
                        .size(36.dp)
                        .clickable { onBack() }
                        .padding(6.dp),
                )
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "路线步骤",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    val sub = if (choice != null) {
                        "${plan?.destName ?: "目的地"} · ${formatSeconds(choice.durationSeconds)} · " +
                            "${formatMeters(choice.distanceMeters)} · 红绿灯${choice.trafficLights}个"
                    } else {
                        "路线数据"
                    }
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }

        // ---------- 内容 ----------
        when {
            choice == null -> StepEmptyState(
                text = "路线数据已失效，请返回重新算路后再查看步骤",
                showNavigation = false,
                onBack = onBack,
                buttonText = naviMode.buttonText,
                onStartNavigation = {},
            )

            choice.steps.isEmpty() -> StepEmptyState(
                text = "暂无路线步骤",
                showNavigation = true,
                onBack = onBack,
                buttonText = naviMode.buttonText,
                onStartNavigation = { onStartNavigation(routeId) },
            )

            else -> StepList(
                steps = choice.steps,
                routeId = routeId,
                buttonText = naviMode.buttonText,
                onStartNavigation = onStartNavigation,
            )
        }
    }
}

/** 步骤列表 + 底部实时导航按钮（仅预览，不自动导航） */
@Composable
private fun StepList(
    steps: List<NaviTurnStep>,
    routeId: Int,
    buttonText: String,
    onStartNavigation: (Int) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 10.dp,
                bottom = 84.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(steps, key = { it.index }) { step ->
                StepRow(step)
            }
        }
        // 底部主操作：开始实时导航（不影响纯预览，按需点击）
        Surface(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            tonalElevation = 6.dp,
        ) {
            Button(
                onClick = {
                    Log.i(TAG, "[step-preview] $buttonText routeId=$routeId")
                    onStartNavigation(routeId)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(buttonText)
            }
        }
    }
}

/** 单行步骤：原生转向箭头 + 指引文案 + 道路/距离副文案 */
@Composable
private fun StepRow(step: NaviTurnStep) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 高德原生转向箭头（直行/左转/右转/掉头/环岛等，由 iconType 决定）
            AndroidView(
                modifier = Modifier.size(34.dp),
                factory = { ctx -> NextTurnTipView(ctx) },
                update = { tip ->
                    runCatching { tip.setIconType(step.iconType) }
                        .onFailure { Log.e(TAG, "NextTurnTipView setIconType failed", it) }
                },
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${step.index}. ${step.instruction}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                val sub = buildList {
                    step.roadName.takeIf { it.isNotBlank() }?.let { add(it) }
                    step.lengthMeters.takeIf { it > 0 }?.let { add(formatMeters(it)) }
                    step.timeSeconds.takeIf { it > 0 }?.let { add(formatSeconds(it)) }
                }.joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 空数据 / 数据失效占位（严禁因 NaviPath/NaviSteps 为空崩溃） */
@Composable
private fun StepEmptyState(
    text: String,
    showNavigation: Boolean,
    buttonText: String,
    onBack: () -> Unit,
    onStartNavigation: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🗺️", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onBack) { Text("返回路线选择") }
            if (showNavigation) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onStartNavigation) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(buttonText)
                }
            }
        }
    }
}
