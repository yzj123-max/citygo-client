package com.example.citygo.skeleton.share

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.citygo.skeleton.data.*
import com.example.citygo.skeleton.data.datastore.AccountSession
import com.example.citygo.skeleton.data.model.ArticleComment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetailScreen(articleId: String, shareViewModel: ShareViewModel, onBack: () -> Unit) {
    val account by AccountSession.currentAccount.collectAsStateWithLifecycle()
    val articles by shareViewModel.articleMap.collectAsStateWithLifecycle()
    val threads by shareViewModel.threads.collectAsStateWithLifecycle()
    val ui by shareViewModel.uiState.collectAsStateWithLifecycle()
    val article = articles[articleId]
    var editing by remember(articleId, account?.token) { mutableStateOf(false) }
    var input by remember(articleId, account?.token) { mutableStateOf("") }
    var reply by remember(articleId, account?.token) { mutableStateOf<ArticleComment?>(null) }
    var deletePost by remember { mutableStateOf(false) }
    var deleteComment by remember { mutableStateOf<ArticleComment?>(null) }
    var followed by remember(articleId, account?.token) { mutableStateOf<Boolean?>(null) }
    var followError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(articleId, account?.token) { shareViewModel.ensureArticleLoaded(articleId) }
    LaunchedEffect(ui.errorMessage) { ui.errorMessage?.let { snack.showSnackbar(it); shareViewModel.consumeErrorMessage() } }
    LaunchedEffect(article?.authorId, account?.token) {
        if (article != null && !article.canEdit) {
            try { followed = RepositoryContainer.articleRepository.search(article.authorId).firstOrNull()?.following }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { followError = e.message }
        }
    }
    if (editing && article != null) {
        ArticlePublishScreen(shareViewModel, onBack = { editing = false; shareViewModel.ensureArticleLoaded(articleId) }, original = article)
        return
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("帖子详情") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }, actions = {
            if (article?.canEdit == true) {
                TextButton(onClick = { editing = true }) { Text("编辑") }
                TextButton(onClick = { deletePost = true }) { Text("删除") }
            }
        }) },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (article != null) Surface(tonalElevation = 4.dp) {
                Column(Modifier.navigationBarsPadding().imePadding().padding(8.dp)) {
                    Row {
                        TextButton(onClick = { shareViewModel.toggleLike(articleId) }) { Text("${if (article.isLiked) "♥" else "♡"} ${article.likeCount}") }
                        TextButton(onClick = { shareViewModel.toggleCollect(articleId) }) { Text("${if (article.isCollected) "★" else "☆"} ${article.collectCount}") }
                        TextButton(onClick = { shareViewModel.loadComments(articleId, reset = true) }) { Text("评论 ${article.commentCount} · 刷新") }
                    }
                    if (reply != null) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("回复 ${reply!!.userName}", modifier = Modifier.weight(1f))
                        TextButton(onClick = { reply = null }) { Text("取消回复") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(input, onValueChange = { if (it.length <= 1000) input = it },
                            modifier = Modifier.weight(1f).heightIn(max = 120.dp), label = { Text("说点什么…") }, enabled = article.canComment)
                        TextButton(enabled = article.canComment && input.isNotBlank(), onClick = {
                            shareViewModel.addComment(articleId, "", input, reply) { input = ""; reply = null }
                        }) { Text("发送") }
                    }
                }
            }
        }
    ) { padding ->
        if (article == null) Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("正在获取帖子；若加载失败请重试")
            TextButton(onClick = { shareViewModel.ensureArticleLoaded(articleId) }) { Text("重新加载") }
        } else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                if (article.imgUrls.isNotEmpty()) {
                    val pager = rememberPagerState(pageCount = { article.imgUrls.size })
                    HorizontalPager(pager, Modifier.fillMaxWidth().height(320.dp)) { index ->
                        CommunityImage(data = article.imgUrls[index], fit = ImageFit.Fill, contentDescription = "配图${index + 1}")
                    }
                    Text("${pager.currentPage + 1}/${article.imgUrls.size}", Modifier.padding(horizontal = 16.dp))
                }
                Column(Modifier.padding(16.dp)) {
                    Text(article.title, style = MaterialTheme.typography.headlineSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(article.authorName, Modifier.weight(1f))
                        if (!article.canEdit && followed != null) TextButton(onClick = {
                            scope.launch {
                                try { followed = RepositoryContainer.articleRepository.follow(article.authorId, followed == true).following; followError = null }
                                catch (e: CancellationException) { throw e }
                                catch (e: Exception) { followError = e.message ?: "操作失败" }
                            }
                        }) { Text(if (followed == true) "已关注 · 取消" else "关注作者") }
                    }
                    followError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text(when (article.visibility) { "PUBLIC" -> "公开"; "PRIVATE" -> "仅自己可见"; "ALLOW_LIST" -> "指定用户可见"; "DENY_LIST" -> "部分用户不可见"; else -> "互关好友可见" }, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(12.dp))
                    Text(article.content)
                    Spacer(Modifier.height(20.dp))
                    Text("评论与回复 ${article.commentCount}", style = MaterialTheme.typography.titleMedium)
                }
            }
            val rootThread = threads["$articleId:null"] ?: CommentThread()
            rootThread.records.forEach { root ->
                item(key = root.commentId) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        CommunityCommentRow(root, onReply = { reply = root }, onDelete = { deleteComment = root })
                        val replies = threads["$articleId:${root.commentId}"]
                        if (replies == null) TextButton(onClick = { shareViewModel.loadComments(articleId, root.commentId) }) { Text("查看回复") }
                        else Column(Modifier.padding(start = 20.dp)) {
                            replies.records.forEach { child ->
                                CommunityCommentRow(child, onReply = { reply = child }, onDelete = { deleteComment = child })
                            }
                            CommentThreadFooter(replies, onMore = { shareViewModel.loadComments(articleId, root.commentId) })
                        }
                        HorizontalDivider()
                    }
                }
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    if (rootThread.records.isEmpty() && !rootThread.loading && rootThread.error == null) Text("暂无评论")
                    CommentThreadFooter(rootThread, onMore = { shareViewModel.loadComments(articleId) })
                }
            }
        }
    }
    if (deletePost) AlertDialog(onDismissRequest = { deletePost = false }, title = { Text("删除这篇帖子？") },
        text = { Text("删除后，其他用户将无法查看或继续互动。") },
        confirmButton = { TextButton(onClick = { deletePost = false; shareViewModel.deletePost(articleId, onBack) }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { deletePost = false }) { Text("取消") } })
    deleteComment?.let { target -> AlertDialog(onDismissRequest = { deleteComment = null }, title = { Text("删除评论？") },
        text = { Text(if (target.rootId == null) "删除此评论也会删除其全部回复。" else "确认删除这条回复？") },
        confirmButton = { TextButton(onClick = { deleteComment = null; shareViewModel.deleteComment(articleId, target) }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { deleteComment = null }) { Text("取消") } }) }
}

@Composable
private fun CommunityCommentRow(comment: ArticleComment, onReply: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(comment.userName + (comment.replyToUserName?.let { " 回复 $it" } ?: ""), style = MaterialTheme.typography.labelMedium)
        Text(comment.content)
        Row {
            TextButton(onClick = onReply) { Text("回复") }
            if (comment.canDelete) TextButton(onClick = onDelete) { Text("删除") }
        }
    }
}
@Composable
private fun CommentThreadFooter(thread: CommentThread, onMore: () -> Unit) {
    if (thread.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    thread.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (!thread.loading && (thread.hasMore || thread.error != null)) TextButton(onClick = onMore) { Text(if (thread.error != null) "重试" else "加载更多") }
}
