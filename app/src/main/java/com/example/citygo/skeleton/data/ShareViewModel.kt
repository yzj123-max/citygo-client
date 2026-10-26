package com.example.citygo.skeleton.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.citygo.skeleton.data.datastore.AccountSession
import com.example.citygo.skeleton.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class CommunityUiState(val initialLoading: Boolean = true, val refreshing: Boolean = false,
    val loadingMore: Boolean = false, val articles: List<TravelArticle> = emptyList(),
    val canLoadMore: Boolean = true, val errorMessage: String? = null,
    val source: String = "feed", val total: Long = 0)
data class CommentThread(val records: List<ArticleComment> = emptyList(), val page: Int = 0,
    val loading: Boolean = false, val hasMore: Boolean = true, val error: String? = null)

class ShareViewModel : ViewModel() {
    private val repository = RepositoryContainer.articleRepository
    private val _uiState = MutableStateFlow(CommunityUiState())
    val uiState = _uiState.asStateFlow()
    private val _articles = MutableStateFlow<Map<String, TravelArticle>>(emptyMap())
    val articleMap = _articles.asStateFlow()
    private val _threads = MutableStateFlow<Map<String, CommentThread>>(emptyMap())
    val threads = _threads.asStateFlow()
    private val _communityKeyword = MutableStateFlow<String?>(null)
    val communityKeyword = _communityKeyword.asStateFlow()
    val visibleArticles = combine(_uiState, _communityKeyword) { state, keyword ->
        state.articles.filter { keyword.isNullOrBlank() || it.title.contains(keyword) || it.content.contains(keyword) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private var accountScope: CoroutineScope? = null
    private var page = 0
    private var feedJob: Job? = null
    private val busy = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            AccountSession.currentAccount.map { it?.token }.distinctUntilChanged().collectLatest { token ->
                coroutineScope {
                    accountScope = this
                    busy.clear()
                    _articles.value = emptyMap()
                    _threads.value = emptyMap()
                    _communityKeyword.value = null
                    _uiState.value = CommunityUiState(initialLoading = !token.isNullOrBlank())
                    page = 0
                    if (!token.isNullOrBlank()) refresh()
                    awaitCancellation()
                }
            }
        }
    }
    fun setCommunityKeyword(keyword: String?) { _communityKeyword.value = keyword?.trim()?.ifBlank { null } }
    fun clearCommunityKeyword() { _communityKeyword.value = null }
    fun consumeErrorMessage() { _uiState.update { it.copy(errorMessage = null) } }
    fun selectSource(source: String) {
        if (source == _uiState.value.source) return
        _uiState.update { it.copy(source = source, articles = emptyList()) }
        refresh()
    }
    fun refresh() {
        feedJob?.cancel()
        val source = _uiState.value.source
        _uiState.update { it.copy(initialLoading = it.articles.isEmpty(), refreshing = it.articles.isNotEmpty(), loadingMore = false, errorMessage = null) }
        feedJob = accountScope?.launch {
            try {
                val result = repository.page(source, 1, 20)
                val articles = with(repository) { result.records.map { it.article() } }
                _articles.update { it + articles.associateBy { a -> a.articleId } }
                page = 1
                _uiState.update { it.copy(initialLoading = false, refreshing = false, articles = articles, canLoadMore = result.hasMore, total = result.total) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.update { it.copy(initialLoading = false, refreshing = false, errorMessage = message(e)) } }
        }
    }
    fun loadMore() {
        val state = _uiState.value
        if (state.initialLoading || state.refreshing || state.loadingMore || !state.canLoadMore) return
        _uiState.update { it.copy(loadingMore = true) }
        feedJob = accountScope?.launch {
            try {
                val result = repository.page(state.source, page + 1, 20)
                val articles = with(repository) { result.records.map { it.article() } }
                _articles.update { it + articles.associateBy { a -> a.articleId } }
                page++
                _uiState.update { it.copy(loadingMore = false, articles = (it.articles + articles).distinctBy { a -> a.articleId }, canLoadMore = result.hasMore, total = result.total) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.update { it.copy(loadingMore = false, errorMessage = message(e)) } }
        }
    }
    private fun action(key: String, block: suspend () -> Unit) {
        if (accountScope == null || !busy.add(key)) return
        accountScope?.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.update { it.copy(errorMessage = message(e)) } }
            finally { busy.remove(key) }
        }
    }
    fun ensureArticleLoaded(id: String) {
        _articles.update { it - id }
        action("detail:$id") { replaceArticle(repository.getArticleById(id)); loadComments(id, reset = true) }
    }
    fun toggleLike(id: String) = action("like:$id") { replaceArticle(repository.toggleLike(id)) }
    fun toggleCollect(id: String) = action("collect:$id") {
        val updated = repository.toggleCollect(id)
        replaceArticle(updated)
        if (_uiState.value.source == "collections" && !updated.isCollected) refresh()
    }
    fun loadComments(id: String, root: Long? = null, reset: Boolean = false) {
        val key = "$id:$root"
        val before = if (reset) CommentThread() else _threads.value[key] ?: CommentThread()
        if (!reset && (before.loading || !before.hasMore)) return
        action("comments:$key") {
            _threads.update { it + (key to before.copy(loading = true, error = null)) }
            try {
                val result = repository.comments(id, root, before.page + 1)
                val rows = with(repository) { result.records.map { it.comment() } }
                _threads.update { it + (key to CommentThread((before.records + rows).distinctBy { c -> c.commentId }, before.page + 1, false, result.hasMore)) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _threads.update { it + (key to before.copy(loading = false, error = message(e))) } }
        }
    }
    fun addComment(id: String, userName: String, content: String, reply: ArticleComment? = null, onSuccess: () -> Unit = {}) {
        if (content.isBlank()) return
        action("send:$id") {
            repository.sendComment(id, content.trim(), reply?.commentId)
            replaceArticle(repository.getArticleById(id))
            loadComments(id, reset = true)
            reply?.let { loadComments(id, it.rootId ?: it.commentId, true) }
            onSuccess()
        }
    }
    fun deleteComment(id: String, comment: ArticleComment) = action("deleteComment:$id") {
        repository.deleteComment(id, comment.commentId)
        replaceArticle(repository.getArticleById(id))
        if (comment.rootId == null) _threads.update { it - "$id:${comment.commentId}" }
        loadComments(id, reset = true)
        comment.rootId?.let { loadComments(id, it, true) }
    }
    fun deletePost(id: String, onSuccess: () -> Unit) = action("delete:$id") {
        repository.delete(id)
        _articles.update { it - id }
        refresh()
        onSuccess()
    }
    fun publishArticle(title: String, content: String, imgUrls: List<String>,
        visibility: String = "PRIVATE", audience: List<Long> = emptyList(), original: TravelArticle? = null,
        onFailure: (String) -> Unit = {}, onSuccess: () -> Unit): Boolean {
        if (title.isBlank() || title.length > 100 || content.length > 1000 || imgUrls.size !in 1..9) return false
        action("publish") {
            try {
                val article = repository.write(PublishArticleRequest("", title.trim(), content.trim(), imgUrls, visibility, audience), original)
                replaceArticle(article)
                refresh()
                onSuccess()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { onFailure(message(e)) }
        }
        return true
    }
    private fun replaceArticle(article: TravelArticle) {
        _articles.update { it + (article.articleId to article) }
        _uiState.update { state -> state.copy(articles = state.articles.map { if (it.articleId == article.articleId) article else it }) }
    }
    private fun message(e: Exception) = when (e) {
        is java.net.SocketTimeoutException -> "服务响应超时，请重试"
        is java.io.IOException -> "连接失败，请确认后端运行及USB端口转发"
        else -> e.message ?: "操作失败，请重试"
    }
}
