package com.example.citygo.skeleton.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.citygo.skeleton.data.RecommendViewModel
import com.example.citygo.skeleton.data.model.searchWeatherCities
import com.example.citygo.skeleton.data.repository.CITY_ALL
import com.example.citygo.skeleton.recommend.SpotCard
import com.example.citygo.skeleton.recommend.SpotCardSkeleton
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import com.example.citygo.skeleton.recommend.AttractionLocationControl

/* =====================================================================================
 * 首页 · 景点推荐
 *
 * 顶部：城市选择下拉（切换城市自动刷新该城市推荐）+ 景点搜索框（输入名称、一键清空、
 *       键盘搜索 / 搜索按钮跳转到搜索结果列表页）。
 * 列表：景点卡片（封面 / 名称 / 星级评分 / 简介 / 热门·小众标签），点击进入景点详情。
 *
 * 全部数据当前来自本地 Mock（RecommendViewModel → SpotRepository），
 * 后端接入时只需替换 Repository 实现，UI 无需改动。
 * ===================================================================================== */

/**
 * @param onSpotClick 点击景点卡片，参数为景点 id（导航层跳转景点详情页）
 * @param onSearch 提交搜索，参数为关键词（导航层跳转搜索结果页）
 * @param recommendViewModel Activity 作用域共享的推荐 ViewModel（由 SkeletonApp 注入）
 */
@Composable
fun RecommendScreen(
    onSpotClick: (String) -> Unit,
    onSearch: (String) -> Unit,
    recommendViewModel: RecommendViewModel = viewModel(),
    modifier: Modifier = Modifier,
    onPlan: (String) -> Unit = {},
    onOpenOutfit: () -> Unit = {},
    petFriendly: Boolean = false,
    onPetFriendlyChange: (Boolean) -> Unit = {},
) {
    val uiState by recommendViewModel.uiState.collectAsStateWithLifecycle()
    // 首页搜索框输入（跳转后由搜索结果页接管关键词）
    var keyword by remember { mutableStateOf("") }
    // 全国城市搜索覆盖页开关
    var showCitySearch by remember { mutableStateOf(false) }
    var showNearbyLocation by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
      LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 24.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
          item(key = "intro") {
              com.example.citygo.ui.theme.DiscoveryIntro(
                  city = uiState.currentCity, petFriendly = petFriendly,
                  onPetFriendlyChange = onPetFriendlyChange,
                  onPlan = { onPlan(uiState.currentCity) }, onOutfit = onOpenOutfit,
                  onNearby = { showNearbyLocation = true },
                  heroPhoto = uiState.spots.firstOrNull()?.coverImg,
              )
          }
          item(key = "search") {
              Row(verticalAlignment = Alignment.CenterVertically) {
                  CitySelectorDropdown(uiState.currentCity, uiState.cities,
                      recommendViewModel::selectCity, { showCitySearch = true })
                  Spacer(Modifier.width(8.dp))
                  HomeSearchBar(keyword, { keyword = it }, { query ->
                      val text = query.trim()
                      if (text.isNotEmpty()) {
                          val city = recommendViewModel.matchCityKeyword(text)
                          if (city != null) { keyword = ""; recommendViewModel.selectCity(city) }
                          else { recommendViewModel.submitSearch(text); onSearch(text) }
                      }
                  }, Modifier.weight(1f))
              }
          }
          item(key = "section") {
              Row(verticalAlignment = Alignment.CenterVertically) {
                  Text("为你精选", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                  TextButton(onClick = recommendViewModel::refresh) { Text("刷新") }
              }
              Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  FilterChip(uiState.source != "NEARBY", { recommendViewModel.selectCity(uiState.currentCity) }, { Text("城市推荐") })
                  FilterChip(uiState.source == "NEARBY", { showNearbyLocation = !showNearbyLocation }, { Text("附近精选 · 20公里") })
              }
          }
          if (showNearbyLocation) item(key = "location") {
              AttractionLocationControl(autoStart = true) { fix ->
                  recommendViewModel.showNearby(fix.longitude, fix.latitude)
                  showNearbyLocation = false
              }
          }
          if (uiState.source != "NEARBY") item(key = "filters") {
              Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  listOf(null to "全部分类", "110202" to "国家级景点", "110205" to "寺庙道观").forEach { (code, label) ->
                      FilterChip(uiState.categoryCode == code,
                          { recommendViewModel.setFilters(code, uiState.ticketStatus, uiState.petPolicy) }, { Text(label) })
                  }
                  FilterChip(uiState.ticketStatus == "FREE",
                      { recommendViewModel.setFilters(uiState.categoryCode, if (uiState.ticketStatus == "FREE") null else "FREE", uiState.petPolicy) }, { Text("已核实免费") })
                  FilterChip(uiState.petPolicy == "ALLOWED",
                      { recommendViewModel.setFilters(uiState.categoryCode, uiState.ticketStatus, if (uiState.petPolicy == "ALLOWED") null else "ALLOWED") }, { Text("已核实允许携宠") })
                  FilterChip(uiState.petPolicy == "CONDITIONAL",
                      { recommendViewModel.setFilters(uiState.categoryCode, uiState.ticketStatus, if (uiState.petPolicy == "CONDITIONAL") null else "CONDITIONAL") }, { Text("有条件携宠") })
              }
          }
          when {
              uiState.loading -> items(SKELETON_CARD_COUNT) { SpotCardSkeleton() }
              uiState.errorMessage != null && uiState.spots.isEmpty() -> item {
                  ErrorRetryColumn(message = uiState.errorMessage!!, onRetry = recommendViewModel::refresh)
              }
              uiState.spots.isEmpty() -> item {
                  Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                      Text("暂无符合条件的精选地点", style = MaterialTheme.typography.titleMedium)
                      Text("可以搜索地点名称，或调整筛选后再看看。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                      TextButton(onClick = { recommendViewModel.setFilters(null, null, null) }) { Text("清除筛选") }
                      if (uiState.hasMore) TextButton(onClick = recommendViewModel::loadMore) { Text("继续查询下一页") }
                  }
              }
              else -> {
                  item(key = "count") {
                      Text("${uiState.currentCity} · 已加载 ${uiState.spots.size} 个地点", style = MaterialTheme.typography.labelMedium,
                          color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  items(uiState.spots, key = { it.spotId }) { spot ->
                      LaunchedEffect(spot.spotId) { recommendViewModel.ensurePhoto(spot.spotId) }
                      SpotCard(spot = spot, onClick = { onSpotClick(spot.spotId) })
                  }
                  item(key = "pagination") {
                      Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                          uiState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                          if (uiState.loadingMore) CircularProgressIndicator(Modifier.size(24.dp))
                          else if (uiState.hasMore) TextButton(onClick = recommendViewModel::loadMore) { Text("加载更多") }
                          else Text("已加载完毕", style = MaterialTheme.typography.labelSmall)
                      }
                  }
              }
          }
      }

      // 全国城市搜索覆盖页：支持汉字 / 全拼 / 首字母 / adCode，选中城市后刷新推荐
      if (showCitySearch) {
          RecommendCitySearchOverlay(
              currentCity = uiState.currentCity,
              onCitySelected = { city ->
                  showCitySearch = false
                  recommendViewModel.selectCity(city)
              },
              onBack = { showCitySearch = false },
          )
      }
    }
}

/* ---------------------------------- 城市选择下拉 ---------------------------------- */

/**
 * 城市选择下拉控件：胶囊样式，展开后选择城市并联动刷新推荐列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CitySelectorDropdown(
    selectedCity: String,
    cities: List<String>,
    onCitySelected: (String) -> Unit,
    onOpenSearch: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        Surface(
            modifier = Modifier.menuAnchor(
                androidx.compose.material3.MenuAnchorType.PrimaryNotEditable,
            ),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = "选择城市",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = selectedCity,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            cities.forEach { city ->
                DropdownMenuItem(
                    text = { Text(city) },
                    onClick = {
                        onCitySelected(city)
                        expanded = false
                    },
                    trailingIcon = if (city == selectedCity) {
                        {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "当前选中",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
            // 分隔线 + 全国城市搜索入口（支持拼音搜索，无需中文输入法）
            HorizontalDivider()
            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                text = { Text("搜索其他城市…", color = MaterialTheme.colorScheme.primary) },
                onClick = {
                    expanded = false
                    onOpenSearch()
                },
            )
        }
    }
}

/* ------------------------------ 全国城市搜索覆盖页 ------------------------------ */

/**
 * 推荐页全屏城市搜索（浅色主题，与天气页深色搜索共用同一份全国目录与拼音索引）：
 * - 空态：「全部城市」+ 热门城市；
 * - 输入时按 城市名汉字 / 全拼 / 首字母 / 省份拼音 / adCode 本地实时过滤，
 *   纯英文键盘（suzhou、sz）也能搜，不依赖中文输入法；
 * - 选中城市后回调，ViewModel 刷新该城市推荐；mock 暂无数据的城市展示空态。
 */
@Composable
private fun RecommendCitySearchOverlay(
    currentCity: String,
    onCitySelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    var keyword by remember { mutableStateOf("") }
    // 本地目录过滤，输入即搜，无需网络
    val result = remember(keyword) { searchWeatherCities(keyword) }
    val focusRequester = remember { FocusRequester() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
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
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (keyword.isNotEmpty()) {
                            IconButton(onClick = { keyword = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "清空")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }

            Text(
                text = if (keyword.isBlank()) "热门城市" else "搜索结果（${result.size}）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 18.dp, top = 6.dp, bottom = 4.dp),
            )

            if (keyword.isNotBlank() && result.isEmpty()) {
                // 无匹配结果
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Text(
                        text = "未找到「$keyword」相关城市\n可输入城市名、拼音或首字母",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 60.dp),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // 空态首项固定提供「全部城市」，一键回到全部推荐
                    if (keyword.isBlank()) {
                        item(key = "city-all") {
                            CityPickRow(
                                title = CITY_ALL,
                                subtitle = "浏览全部城市的精选景点",
                                selected = currentCity == CITY_ALL,
                                onClick = { onCitySelected(CITY_ALL) },
                            )
                        }
                    }
                    items(result, key = { it.adCode }) { entry ->
                        CityPickRow(
                            title = entry.name,
                            subtitle = entry.province + " · " + entry.adCode,
                            selected = entry.name == currentCity,
                            onClick = { onCitySelected(entry.name) },
                        )
                    }
                }
            }
        }
    }

    // 进入页面自动聚焦输入框并唤起键盘
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }
}

/** 城市搜索结果单行 */
@Composable
private fun CityPickRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title + if (selected) "  ✓" else "",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/* ----------------------------------- 首页搜索框 ----------------------------------- */

/** 加载态骨架卡片数量（与首页推荐数量级一致） */
private const val SKELETON_CARD_COUNT = 6

/**
 * 首页顶部搜索框：支持一键清空，键盘 Search 动作提交。
 * 提交后跳转独立的搜索结果列表页（由外层 [onSearch] 回调处理导航）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeSearchBar(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = keyword,
        onValueChange = onKeywordChange,
        modifier = modifier,
        placeholder = { Text("搜索城市或景点名称") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
            )
        },
        trailingIcon = {
            if (keyword.isNotEmpty()) {
                IconButton(onClick = { onKeywordChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "清空输入",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = ImeAction.Search,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onSearch = { onSubmit(keyword) },
        ),
    )
}

/* --------------------------------- 状态：空 / 错误 -------------------------------- */

@Composable
private fun EmptyCityColumn() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.TravelExplore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "该城市暂无景点推荐",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "切换其他城市看看吧",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorRetryColumn(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRetry) {
            Text("点击重试")
        }
    }
}

