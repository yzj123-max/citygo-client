package com.example.citygo.skeleton.screens.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditLocation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.citygo.skeleton.data.model.AiPlanOptions
import com.example.citygo.skeleton.data.model.ALL_WEATHER_CITY_ENTRIES
import com.example.citygo.skeleton.data.model.HOT_WEATHER_CITIES
import com.example.citygo.skeleton.data.model.WeatherCityEntry
import com.example.citygo.skeleton.data.model.searchWeatherCities

/* =====================================================================================
 * 出行计划页「规划第一步」配套弹窗（全新增文件，不改动任何既有页面业务逻辑）：
 *
 * 1. [PlanCityPickerDialog]：城市选择弹窗
 *    - 顶部搜索框：复用天气模块的全国城市目录（约 340 个地级行政区，
 *      支持汉字 / 全拼 / 首字母 / adCode 检索，见 searchWeatherCities）；
 *    - 热门城市快捷 Chip（成都、重庆等）；
 *    - 下方为完整城市库，可自由滚动浏览，不再只列 12 个预设城市；
 *    - 目录未命中的任意关键字（含海外城市，如 Paris / 曼谷）可作为自定义目的地直接选用。
 *
 * 2. [TripConfigDialog]：选定城市后的行程配置表单
 *    - 字段：出行天数（1-15 天下拉）、人均预算（输入框，选填）、
 *      游玩偏好（多选：宠物友好 / 休闲 / 打卡拍照 / 美食 / 自然风光）、
 *      优先宠物友好地点开关（沿用既有开关）；
 *    - 选完城市只弹本表单，绝不直接生成 AI 行程；点击「生成 AI 攻略」后才回调 [onConfirm]。
 * ===================================================================================== */

/**
 * 计划页城市选择弹窗。
 *
 * @param visible 是否展示
 * @param currentCityName 当前城市名（用于列表高亮）
 * @param onCitySelected 选定城市回调（城市名，可能是目录城市或用户输入的任意城市）
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanCityPickerDialog(
    visible: Boolean,
    currentCityName: String,
    onCitySelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    var keyword by remember { mutableStateOf("") }

    val kw = keyword.trim()
    val searching = kw.isNotEmpty()
    // 复用全国目录检索：汉字 / 拼音 / 首字母 / adCode，相关度排序
    val searchResults = remember(kw) { if (searching) searchWeatherCities(kw, limit = 50) else emptyList() }
    // 目录中没有任何匹配时，允许把关键字本身作为自定义目的地（支持海外 / 小众地名）
    val customKeyword = kw.takeIf { searching && searchResults.isEmpty() && it.length >= 2 }
    // 热门城市（全部能在全国目录中找到对应条目）
    val hotEntries = HOT_WEATHER_CITIES.mapNotNull { hot ->
        ALL_WEATHER_CITY_ENTRIES.firstOrNull { it.adCode == hot.adCode }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // 标题栏
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "选择目的地城市",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                }

                Spacer(Modifier.height(8.dp))

                // 搜索框：国内城市名 / 拼音 / 首字母，或任意海外城市名
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索城市，如 成都 / 杭州 / suzhou / Paris") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )

                Spacer(Modifier.height(12.dp))

                if (searching) {
                    // —— 搜索结果 ——
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (customKeyword != null) {
                            item(key = "custom_city") {
                                CustomCityRow(keyword = customKeyword, onClick = {
                                    onCitySelected(customKeyword)
                                })
                            }
                        }
                        items(searchResults, key = { it.adCode }) { city ->
                            CatalogCityRow(
                                city = city,
                                selected = city.name == currentCityName,
                                onClick = { onCitySelected(city.name) },
                            )
                        }
                        if (customKeyword == null && searchResults.isNotEmpty()) {
                            item(key = "custom_city_tail") {
                                CustomCityRow(keyword = kw, onClick = { onCitySelected(kw) })
                            }
                        }
                    }
                } else {
                    // —— 默认：热门快捷 + 完整城市库（可滚动浏览全国城市） ——
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item(key = "hot_title") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Whatshot,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.size(6.dp))
                                Text(
                                    text = "热门城市",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        item(key = "hot_chips") {
                            HotCityChips(
                                cities = hotEntries,
                                currentCityName = currentCityName,
                                onCitySelected = { onCitySelected(it.name) },
                            )
                        }
                        item(key = "all_title") {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "全国城市（${ALL_WEATHER_CITY_ENTRIES.size} 个，上下滑动浏览）",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        items(ALL_WEATHER_CITY_ENTRIES, key = { it.adCode }) { city ->
                            CatalogCityRow(
                                city = city,
                                selected = city.name == currentCityName,
                                onClick = { onCitySelected(city.name) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 热门城市横向换行 Chip 组 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun HotCityChips(
    cities: List<WeatherCityEntry>,
    currentCityName: String,
    onCitySelected: (WeatherCityEntry) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cities.forEach { city ->
            FilterChip(
                selected = city.name == currentCityName,
                onClick = { onCitySelected(city) },
                label = { Text(city.name) },
                shape = RoundedCornerShape(50),
                leadingIcon = if (city.name == currentCityName) {
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

/** 全国城市目录行（城市名 + 所属省份） */
@Composable
private fun CatalogCityRow(
    city: WeatherCityEntry,
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
                if (selected) {
                    Text(
                        text = "当前",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
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

/** 目录未命中时的自定义城市行（支持任意国内小众地名 / 海外城市） */
@Composable
private fun CustomCityRow(keyword: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.EditLocation,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "使用「$keyword」作为目的地",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "目录中未收录，按该名称直接规划（支持海外城市 / 小众地名）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/**
 * 选定城市后的行程配置表单弹窗。
 *
 * 选城市 → 仅弹本表单，不会直接生成行程；点击「生成 AI 攻略」后才回调 [onConfirm]。
 *
 * @param cityName 已选目的地城市
 * @param isGenerating 是否正在生成（控制按钮 loading / 禁用）
 * @param days 出行天数（1-15）
 * @param onDaysChange 修改天数
 * @param budgetText 预算输入框文本（空串表示未填；只允许数字）
 * @param onBudgetChange 修改预算
 * @param selectedPrefs 已选游玩偏好标签（[AiPlanOptions.PREFERENCE_CHOICES] 的子集）
 * @param onTogglePref 切换某个偏好
 * @param petFriendly 优先宠物友好地点开关状态（沿用既有开关）
 * @param onPetFriendlyChange 切换宠物偏好
 * @param remark 额外备注文本（选填，多行；随任务请求体 remark 提交给后端并透传 Agent）
 * @param onRemarkChange 修改备注
 * @param onConfirm 确认生成
 * @param onChangeCity 返回城市选择弹窗更换目的地
 * @param onDismiss 取消（不生成任何行程）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TripConfigDialog(
    visible: Boolean,
    cityName: String,
    isGenerating: Boolean,
    days: Int,
    onDaysChange: (Int) -> Unit,
    budgetText: String,
    onBudgetChange: (String) -> Unit,
    selectedPrefs: Set<String>,
    onTogglePref: (String) -> Unit,
    petFriendly: Boolean,
    onPetFriendlyChange: (Boolean) -> Unit,
    remark: String,
    onRemarkChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onChangeCity: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    var daysMenuExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!isGenerating) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "行程配置",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "确认目的地与出行偏好，再为你生成 AI 攻略",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))

                // 目的地行：展示已选城市，可点「更换」回到城市选择
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "目的地",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = cityName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        TextButton(onClick = onChangeCity, enabled = !isGenerating) {
                            Text("更换")
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 出行天数：下拉 1-15 天
                Text(
                    text = "出行天数",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                ExposedDropdownMenuBox(
                    expanded = daysMenuExpanded && !isGenerating,
                    onExpandedChange = { if (!isGenerating) daysMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = "$days 天",
                        onValueChange = {},
                        readOnly = true,
                        enabled = !isGenerating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = daysMenuExpanded)
                        },
                        shape = RoundedCornerShape(14.dp),
                    )
                    ExposedDropdownMenu(
                        expanded = daysMenuExpanded,
                        onDismissRequest = { daysMenuExpanded = false },
                    ) {
                        (AiPlanOptions.MIN_DAYS..AiPlanOptions.MAX_DAYS).forEach { option ->
                            DropdownMenuItem(
                                text = { Text("$option 天") },
                                onClick = {
                                    onDaysChange(option)
                                    daysMenuExpanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 出行预算：数字输入（人均，选填）
                Text(
                    text = "人均预算（元，选填）",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { raw ->
                        // 只接受数字，去掉非数字字符（含空格 / 小数点），避免非法金额
                        onBudgetChange(raw.filter { it.isDigit() }.take(8))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("如 2000，不填则不限制预算") },
                    suffix = { Text("元") },
                    singleLine = true,
                    enabled = !isGenerating,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                )

                Spacer(Modifier.height(14.dp))

                // 游玩偏好：多选 Chip
                Text(
                    text = "游玩偏好（可多选）",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiPlanOptions.PREFERENCE_CHOICES.forEach { pref ->
                        FilterChip(
                            selected = pref in selectedPrefs,
                            onClick = { onTogglePref(pref) },
                            label = { Text(pref) },
                            enabled = !isGenerating,
                            shape = RoundedCornerShape(50),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 宠物友好偏好（沿用既有开关；与偏好 Chip 联动生效，互不影响勾选状态）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Pets,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(6.dp).size(20.dp),
                        )
                    }
                    Spacer(Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "优先宠物友好地点",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "优先推荐可携宠进入的景点、餐厅与街区",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = petFriendly,
                        onCheckedChange = onPetFriendlyChange,
                        enabled = !isGenerating,
                    )
                }

                Spacer(Modifier.height(14.dp))

                // 额外备注（可选）：多行输入；作为请求体 remark 与其它参数一起提交，
                // 后端入库并透传给 Agent 作为生成攻略的附加指令
                Text(
                    text = "额外备注（可选）",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = remark,
                    onValueChange = onRemarkChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("可以输入个性化要求，例如节奏慢一点、避开人流、推荐本地小吃等")
                    },
                    minLines = 3,
                    maxLines = 6,
                    enabled = !isGenerating,
                    shape = RoundedCornerShape(14.dp),
                )

                Spacer(Modifier.height(20.dp))

                // 操作区：取消不生成；确认后才调用 AI 生成
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isGenerating,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = !isGenerating,
                        modifier = Modifier.weight(1.4f),
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.size(6.dp))
                            Text("生成中…", maxLines = 1)
                        } else {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(6.dp))
                            Text("生成 AI 攻略", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
