package com.example.citygo.skeleton.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/* =====================================================================================
 * 全局城市选择器
 *
 * - 「📍 城市名 ▾」胶囊按钮，固定在「我的」页面左上角（头部渐变区上方）；
 * - 点击弹出城市选择弹窗：搜索 + 最近访问 + 热门城市 + 全部城市；
 * - 城市数据来自 [AppViewModel]，切换后全局生效，但不改动用户个人资料。
 * ===================================================================================== */

/**
 * 左上角城市选择胶囊（放在头部渐变区时使用白色半透明样式）。
 *
 * @param currentCity 当前城市
 * @param recentCities 最近访问城市
 * @param onCitySelected 选中城市回调（交由 ViewModel 更新全局状态）
 */
@Composable
fun CitySelector(
    currentCity: CityInfo,
    recentCities: List<CityInfo>,
    onCitySelected: (CityInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        // 白色 18% 透明底，保证在品牌紫渐变头部上清晰可读
        color = Color.White.copy(alpha = 0.18f),
        contentColor = Color.White,
        tonalElevation = 0.dp,
        onClick = { showDialog = true },
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "📍", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.size(4.dp))
            Text(
                text = currentCity.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "选择城市",
            )
        }
    }

    if (showDialog) {
        CityPickerDialog(
            currentCityId = currentCity.id,
            recentCities = recentCities,
            onCitySelected = { city ->
                onCitySelected(city)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

/**
 * 城市选择弹窗：搜索框 + 最近访问 + 热门城市 + 全部城市。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CityPickerDialog(
    currentCityId: String,
    recentCities: List<CityInfo>,
    onCitySelected: (CityInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    var keyword by remember { mutableStateOf("") }
    // 搜索为空时展示「最近 / 热门 / 全部」，有输入时按城市名 / 省份过滤
    val searching = keyword.isNotBlank()
    val resultCities = remember(keyword) {
        val kw = keyword.trim()
        if (kw.isEmpty()) {
            emptyList()
        } else {
            mockCities.filter {
                it.name.contains(kw, ignoreCase = true) ||
                    it.province.contains(kw, ignoreCase = true) ||
                    it.id.contains(kw, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // 标题栏
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "选择目标城市",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 搜索框（Mock：仅按城市名 / 省份 / id 过滤本地数据）
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索城市名，如 成都 / 杭州") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )

                Spacer(Modifier.height(16.dp))

                if (searching) {
                    // —— 搜索结果 ——
                    if (resultCities.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "未找到「$keyword」相关城市",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(resultCities) { city ->
                                CityRow(
                                    city = city,
                                    selected = city.id == currentCityId,
                                    onClick = { onCitySelected(city) },
                                )
                            }
                        }
                    }
                } else {
                    // —— 默认：最近访问 / 热门 / 全部 ——
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (recentCities.isNotEmpty()) {
                            item { PickerSectionTitle(icon = Icons.Filled.History, text = "最近访问") }
                            item {
                                CityFlowRow(
                                    cities = recentCities,
                                    currentCityId = currentCityId,
                                    onCitySelected = onCitySelected,
                                )
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }
                        item { PickerSectionTitle(icon = Icons.Filled.Whatshot, text = "热门城市") }
                        item {
                            CityFlowRow(
                                cities = mockCities.filter { it.hot },
                                currentCityId = currentCityId,
                                onCitySelected = onCitySelected,
                            )
                        }
                        item {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "全部城市",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        items(mockCities) { city ->
                            CityRow(
                                city = city,
                                selected = city.id == currentCityId,
                                onClick = { onCitySelected(city) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 弹窗内分区标题（图标 + 文案）。 */
@Composable
private fun PickerSectionTitle(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 横向换行排列的城市 Chip（最近访问 / 热门城市）。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CityFlowRow(
    cities: List<CityInfo>,
    currentCityId: String,
    onCitySelected: (CityInfo) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cities.forEach { city ->
            FilterChip(
                selected = city.id == currentCityId,
                onClick = { onCitySelected(city) },
                label = { Text(city.name) },
                shape = RoundedCornerShape(50),
                leadingIcon = if (city.id == currentCityId) {
                    {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier.heightIn(min = 32.dp),
            )
        }
    }
}

/** 城市列表行（全部城市 / 搜索结果）。 */
@Composable
private fun CityRow(
    city: CityInfo,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = city.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                )
                if (city.hot) {
                    Text(
                        text = "🔥 热门",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                if (selected) {
                    Text(
                        text = "当前",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            Text(
                text = city.province,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(
                modifier = Modifier.padding(top = 10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}
