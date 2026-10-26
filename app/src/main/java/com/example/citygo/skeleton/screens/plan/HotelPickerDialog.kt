package com.example.citygo.skeleton.screens.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.citygo.skeleton.data.AppViewModel
import com.example.citygo.skeleton.data.repository.hotel.HotelPoi
import com.example.citygo.skeleton.share.CommunityImage
import com.example.citygo.skeleton.share.ImageFit

/* =====================================================================================
 * 「为第 X 天添加酒店」选择弹窗（全新增文件）
 *
 * - 进入时按当前行程城市自动泛搜一批酒店；也可输入商圈 / 地标 / 酒店名二次搜索；
 * - 结果展示高德真实住宿 POI：实拍缩略图（无图走 🏨 占位）、名称、地址；
 * - 点「加入」即把酒店交给 ViewModel 写入对应天（同天已有酒店会被替换）；
 * - 加载 / 空结果 / 失败均有明确状态，任何情况不自动写入点位。
 * ===================================================================================== */

/**
 * @param day 要添加到第几天（1 起）；null 时弹窗不显示
 * @param state 酒店搜索 UiState（由 AppViewModel 持有）
 * @param onSearch 发起搜索（参数为关键字，空白表示泛搜）
 * @param onSelect 选中某家酒店
 * @param onDismiss 关闭弹窗
 */
@Composable
fun HotelPickerDialog(
    visible: Boolean,
    day: Int?,
    cityName: String,
    state: AppViewModel.HotelSearchState,
    onSearch: (String) -> Unit,
    onSelect: (HotelPoi) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible || day == null) return
    var keyword by remember { mutableStateOf("") }

    // 每次（重新）打开先按城市泛搜一批，避免空白起步
    LaunchedEffect(day) {
        keyword = ""
        onSearch("")
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
                        imageVector = Icons.Filled.Hotel,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "为第 $day 天添加酒店",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "$cityName · 真实酒店 POI",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 搜索框：商圈 / 地标 / 酒店名
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜商圈 / 地标 / 酒店名，如 西湖、亚朵") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { onSearch(keyword) },
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Search,
                    ),
                )

                Spacer(Modifier.height(12.dp))

                when {
                    state.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    state.hotels.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = state.errorMessage ?: "未搜到相关酒店",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(state.hotels, key = { it.id }) { hotel ->
                                HotelRow(day = day, hotel = hotel, onSelect = onSelect)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 单个酒店结果行：缩略图 + 名称 / 地址 + 加入按钮。 */
@Composable
private fun HotelRow(
    day: Int,
    hotel: HotelPoi,
    onSelect: (HotelPoi) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 实拍缩略图；无图时 🏨 占位
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (hotel.coverUrl != null) {
                    CommunityImage(
                        data = hotel.coverUrl,
                        fit = ImageFit.Fill,
                        cornerShape = RoundedCornerShape(12.dp),
                        contentDescription = hotel.name,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Hotel,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hotel.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = hotel.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }

            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { onSelect(hotel) }) {
                Text("加入第 $day 天")
            }
        }
    }
}
