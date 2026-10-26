package com.example.citygo.skeleton.recommend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.citygo.skeleton.data.RecommendViewModel

/* =====================================================================================
 * 景点搜索结果页（独立全屏页面，无底栏）
 *
 * - 顶部搜索框可二次搜索，输入中展示联想词（Repository.getSearchSuggestions 预留位，
 *   后端接入后替换为远程联想接口，UI 无需改动）；
 *   - 有结果：展示匹配的景点卡片（与首页推荐同款卡片）；
 *   - 无结果：友好空状态提示。
 * ===================================================================================== */

/**
 * @param initialKeyword 首页带入的关键词（路由参数，已 URL decode）
 * @param onBack 返回上一页
 * @param onSpotClick 点击结果卡片跳转景点详情，参数为 spotId
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotSearchScreen(
    initialKeyword: String?,
    onBack: () -> Unit,
    onSpotClick: (String) -> Unit,
    recommendViewModel: RecommendViewModel,
    modifier: Modifier = Modifier,
) {
    val searchState by recommendViewModel.searchState.collectAsStateWithLifecycle()

    // 从首页带入关键词时自动发起一次搜索（仅在与当前搜索词不一致时执行）
    LaunchedEffect(initialKeyword) {
        if (!initialKeyword.isNullOrBlank() && (!searchState.searched || initialKeyword != searchState.keyword)) {
            recommendViewModel.submitSearch(initialKeyword)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                title = {
                    OutlinedTextField(
                        value = searchState.keyword,
                        onValueChange = recommendViewModel::onSearchInputChanged,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("搜索景点名称 / 城市") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        trailingIcon = {
                            if (searchState.keyword.isNotEmpty()) {
                                IconButton(onClick = { recommendViewModel.onSearchInputChanged("") }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "清空",
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
                            onSearch = { recommendViewModel.submitSearch(searchState.keyword) },
                        ),
                    )
                },
                actions = {
                    TextButton(onClick = { recommendViewModel.submitSearch(searchState.keyword) }) {
                        Text("搜索")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            when {
                // ① 搜索请求中
                searchState.loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )

                // ② 搜索失败（网络异常等）：错误提示 + 点击重试
                searchState.errorMessage != null && searchState.results.isEmpty() -> {
                    val errorMessage = searchState.errorMessage!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = recommendViewModel::retrySearch) {
                            Text("点击重试")
                        }
                    }
                }

                // ③ 输入联想（有联想词且输入未提交搜索时展示；后端联想接口的预留 UI）
                searchState.suggestions.isNotEmpty() -> SuggestionList(
                    suggestions = searchState.suggestions,
                    onPick = recommendViewModel::applySuggestion,
                )

                // ④ 已搜索但无结果：友好空状态
                searchState.searched && searchState.results.isEmpty() -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("本页没有符合条件的景点")
                    if (searchState.hasMore) TextButton(onClick = recommendViewModel::loadMoreSearch) { Text("查询下一页") }
                }

                // ⑤ 搜索结果列表
                searchState.results.isNotEmpty() -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "count") {
                        Text(
                            text = "已加载 ${searchState.results.size} 个与「${searchState.keyword}」相关的景点",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(searchState.results, key = { it.spotId }) { spot ->
                        LaunchedEffect(spot.spotId) { recommendViewModel.ensurePhoto(spot.spotId) }
                        SpotCard(spot = spot, onClick = { onSpotClick(spot.spotId) })
                    }
                    item(key = "pagination") {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            searchState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            if (searchState.loadingMore) CircularProgressIndicator(Modifier.size(24.dp))
                            else if (searchState.hasMore) TextButton(onClick = recommendViewModel::loadMoreSearch) { Text("加载更多") }
                            else Text("已加载完毕", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // ⑤ 初始状态（尚未输入 / 搜索）
                else -> SearchInitialHint()
            }
        }
    }
}

/**
 * 搜索联想词列表（预留能力：Mock 阶段为本地景点名称匹配，
 * 后续接入后端 suggest 接口可支持拼音 / 别名 / 热门词）。
 */
@Composable
private fun SuggestionList(
    suggestions: List<String>,
    onPick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(suggestions, key = { it }) { name ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 复用整行点击：整条 Row 使用 IconButton 风格的点击
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(12.dp))
                TextButton(
                    onClick = { onPick(name) },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    Text(
                        text = name,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** 无结果友好空状态 */
@Composable
private fun SearchEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.SearchOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "未找到该景点，试试其他关键词",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "也可以在首页直接搜索城市名，如「深圳」「苏州」",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 搜索页初始提示 */
@Composable
private fun SearchInitialHint() {
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
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "输入景点名称或城市开始搜索",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "岳麓山、西湖、洪崖洞……发现下一个目的地",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
