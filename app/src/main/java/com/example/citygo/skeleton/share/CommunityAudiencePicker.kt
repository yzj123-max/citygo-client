package com.example.citygo.skeleton.share

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.citygo.skeleton.data.RepositoryContainer
import com.example.citygo.skeleton.data.remote.community.CommunityUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun CommunityAudiencePicker(selected: Set<Long>, onDismiss: () -> Unit, onConfirm: (Set<Long>) -> Unit) {
    val repository = RepositoryContainer.articleRepository
    var draft by remember { mutableStateOf(selected) }
    var source by remember { mutableStateOf("search") }
    var input by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(1) }
    var rows by remember { mutableStateOf<List<CommunityUser>>(emptyList()) }
    var more by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(source, submitted, page, retry) {
        loading = true
        error = null
        if (page == 1) rows = emptyList()
        try {
            if (source == "search") {
                more = false
                if (submitted.isNotBlank()) {
                    val id = submitted.toLongOrNull()
                    require(id != null && id > 0) { "请输入有效的用户ID" }
                    rows = repository.search(id)
                }
            } else {
                val result = repository.users(source, page)
                rows = (if (page == 1) result.records else rows + result.records).distinctBy { it.userId }
                more = result.hasMore
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "加载失败" }
        finally { loading = false }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(16.dp)) {
                Text("选择可见范围用户（${draft.size}/200）", style = MaterialTheme.typography.titleMedium)
                Text("已选ID：${draft.joinToString().ifBlank { "暂无" }}", maxLines = 2, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("search" to "所有用户", "following" to "关注", "followers" to "粉丝", "friends" to "好友").forEach { (key, label) ->
                        TextButton(onClick = { if (source != key) { source = key; page = 1 } }) { Text(if (source == key) "·$label" else label) }
                    }
                }
                if (source == "search") {
                    OutlinedTextField(value = input, onValueChange = { input = it.filter(Char::isDigit) }, label = { Text("按用户ID精确搜索") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    TextButton(onClick = { submitted = input; page = 1; retry++ }) { Text("搜索") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { retry++ }) { Text("重试") } }
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(rows, key = { it.userId }) { user ->
                        Row(Modifier.fillMaxWidth()) {
                            Checkbox(checked = user.userId in draft, onCheckedChange = { checked ->
                                if (!checked) draft = draft - user.userId
                                else if (draft.size < 200) draft = draft + user.userId
                                else error = "最多选择200人"
                            })
                            Column(Modifier.weight(1f).padding(top = 8.dp)) {
                                Text(user.nickname ?: "用户${user.userId}")
                                Text("ID：${user.userId}${if (user.friend) " · 互关好友" else ""}", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    try {
                                        val updated = repository.follow(user.userId, user.following)
                                        rows = rows.map { if (it.userId == updated.userId) updated else it }
                                    } catch (e: CancellationException) { throw e }
                                    catch (e: Exception) { error = e.message ?: "操作失败" }
                                }
                            }) { Text(if (user.following) "取消关注" else "关注") }
                        }
                    }
                    if (rows.isEmpty() && !loading && error == null) item { Text(if (source == "search" && submitted.isBlank()) "输入用户ID搜索任意已注册用户" else "暂无用户") }
                    if (more && !loading) item { TextButton(onClick = { page++ }) { Text("加载更多") } }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { draft = emptySet() }) { Text("清空") }
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(onClick = { onConfirm(draft) }) { Text("确定") }
                }
            }
        }
    }
}
