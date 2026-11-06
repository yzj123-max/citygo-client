package com.example.citygo.skeleton.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.citygo.skeleton.data.AppViewModel
import com.example.citygo.skeleton.data.ShareViewModel
import com.example.citygo.skeleton.share.CommunityImage
import com.example.citygo.skeleton.share.ImageFit
import com.example.citygo.ui.theme.TravelToolCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(onArticleClick: (String) -> Unit, modifier: Modifier = Modifier,
    shareViewModel: ShareViewModel, appViewModel: AppViewModel, onPublish: () -> Unit = {},
    onOpenOutfit: () -> Unit = {}, showSourceControls: Boolean = false) {
    val state by shareViewModel.uiState.collectAsStateWithLifecycle()
    val articles by shareViewModel.visibleArticles.collectAsStateWithLifecycle()
    val keyword by shareViewModel.communityKeyword.collectAsStateWithLifecycle()
    var search by remember(keyword) { mutableStateOf(keyword.orEmpty()) }
    var theme by remember { mutableStateOf(0) }
    val visible = remember(articles, theme, showSourceControls) {
        if (showSourceControls || theme == 0) articles else articles.filter { article ->
            val words = if (theme == 1) listOf("宠物", "携宠", "狗", "猫", "毛孩子") else listOf("穿搭", "搭配", "OOTD", "衣服")
            words.any { (article.title + article.previewText).contains(it, ignoreCase = true) }
        }
    }
    LaunchedEffect(showSourceControls) { if (!showSourceControls && state.source != "feed") shareViewModel.selectSource("feed") }
    val grid = rememberLazyStaggeredGridState()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snack.showSnackbar(it); shareViewModel.consumeErrorMessage() }
    }
    val nearEnd by remember { derivedStateOf {
        val info = grid.layoutInfo
        info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 4
    } }
    LaunchedEffect(nearEnd, state.articles.size) { if (nearEnd && !state.initialLoading) shareViewModel.loadMore() }
    Box(modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = shareViewModel::refresh) {
            LazyVerticalStaggeredGrid(columns = StaggeredGridCells.Fixed(2), state = grid,
                modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalItemSpacing = 14.dp) {
                item(span = StaggeredGridItemSpan.FullLine, key = "community-header") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("旅行里的好灵感", style = MaterialTheme.typography.titleLarge)
                            }
                            Button(onClick = onPublish, shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE97838), contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 12.dp)) {
                                Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Text("发布")
                            }
                        }
                        OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("搜索当前已加载的游记") }, singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Transparent, focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant),
                            leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = RoundedCornerShape(28.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                if (search.isBlank()) shareViewModel.clearCommunityKeyword() else shareViewModel.setCommunityKeyword(search.trim())
                            }), trailingIcon = {
                                TextButton(onClick = {
                                    if (search.isBlank()) shareViewModel.clearCommunityKeyword() else shareViewModel.setCommunityKeyword(search.trim())
                                }) { Text("搜索") }
                            })
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (showSourceControls) {
                                listOf("mine" to "我的发布", "collections" to "我的收藏").forEach { (key, label) ->
                                    FilterChip(selected = state.source == key, onClick = { shareViewModel.selectSource(key) }, label = { Text(label) })
                                }
                            } else listOf("推荐", "携宠旅行", "旅行穿搭").forEachIndexed { index, label ->
                                Column(Modifier.weight(1f).clickable { theme = index }.padding(top = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(label, style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (theme == index) FontWeight.Bold else FontWeight.Normal,
                                        color = if (theme == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(10.dp))
                                    Box(Modifier.fillMaxWidth(.7f).height(2.dp).background(
                                        if (theme == index) MaterialTheme.colorScheme.primary else Color.Transparent))
                                }
                            }
                        }
                        if (!showSourceControls) Surface(onClick = onOpenOutfit,
                            shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Checkroom, null, Modifier.size(46.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("AI 穿搭分析", style = MaterialTheme.typography.titleMedium)
                                    Text("上传照片，看看出游怎么穿", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("→", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (!showSourceControls && theme != 0) Text("按当前已加载游记筛选，可继续加载更多内容", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!keyword.isNullOrBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("筛选：$keyword", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = shareViewModel::clearCommunityKeyword) { Text("清除") }
                        }
                    }
                }
                items(visible, key = { it.articleId }) { article ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { onArticleClick(article.articleId) },
                        shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
                        Box(Modifier.fillMaxWidth().height(if (article.articleId.hashCode() % 2 == 0) 205.dp else 235.dp)) {
                            CommunityImage(data = article.imgUrls.firstOrNull().orEmpty(), fit = ImageFit.Fill, contentDescription = article.title)
                        }
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(article.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(article.authorName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = { shareViewModel.toggleLike(article.articleId) }, contentPadding = PaddingValues(0.dp)) {
                                    Text("${if (article.isLiked) "♥" else "♡"} ${article.likeCount}", color = if (article.isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { shareViewModel.toggleCollect(article.articleId) }, contentPadding = PaddingValues(0.dp)) {
                                    Text("${if (article.isCollected) "★" else "☆"} ${article.collectCount}")
                                }
                            }
                        }
                    }
                }
                item(span = StaggeredGridItemSpan.FullLine, key = "community-status") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        when {
                            state.initialLoading || state.loadingMore -> CircularProgressIndicator(Modifier.size(26.dp))
                            visible.isEmpty() -> {
                                Text(if (keyword.isNullOrBlank()) "还没有旅行分享" else "暂时没有找到相关内容", style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = shareViewModel::refresh) { Text("重新加载") }
                                if (state.canLoadMore) TextButton(onClick = shareViewModel::loadMore) { Text("继续查找下一页") }
                            }
                            state.canLoadMore -> TextButton(onClick = shareViewModel::loadMore) { Text("加载更多") }
                            else -> Text("已经看到最后啦", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter))
    }
}
