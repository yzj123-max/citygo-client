package com.example.citygo.skeleton.data.repository

import android.content.Context
import android.net.Uri
import com.example.citygo.skeleton.data.model.*
import com.example.citygo.skeleton.data.remote.AppRetrofit
import com.example.citygo.skeleton.data.remote.community.*
import com.example.citygo.skeleton.util.OotdImageUtils
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

/** 每次创建API时沿用AppRetrofit当前地址及原有鉴权客户端。 */
class RemoteArticleRepository(private val context: () -> Context) : ArticleRepository {
    private val api get() = AppRetrofit.createAuthed<CommunityApi>()
    private val interactionLock = Mutex()

    private fun <T> Response<CommunityResponse<T>>.data(): T {
        val body = if (isSuccessful) body() else runCatching {
            Gson().fromJson(errorBody()?.string(), CommunityResponse::class.java)
        }.getOrNull()
        if (!isSuccessful || body?.code != 200) {
            throw IllegalStateException(body?.msg ?: "请求失败（HTTP ${code()}），请重试")
        }
        @Suppress("UNCHECKED_CAST")
        return body.data as? T ?: throw IllegalStateException("服务未返回数据")
    }

    suspend fun page(source: String, page: Int, size: Int): CommunityPage<CommunityPost> = when (source) {
        "mine" -> api.mine(page, size).data()
        "collections" -> api.collections(page, size).data()
        else -> api.feed(page, size).data()
    }
    override suspend fun getArticles(page: Int, pageSize: Int) = page("feed", page, pageSize).records.map { it.article() }
    override suspend fun getArticleById(articleId: String): TravelArticle = api.detail(articleId.toLong()).data().article()

    suspend fun write(request: PublishArticleRequest, original: TravelArticle? = null): TravelArticle {
        val ids = mutableListOf<Long>()
        request.imgUrls.forEach { uri ->
            val existing = original?.imgUrls?.indexOf(uri) ?: -1
            if (existing >= 0) {
                ids += original!!.mediaIds[existing]
            } else {
                require(uri.startsWith("content://") || uri.startsWith("file://")) { "请选择本机图片" }
                val bytes = withContext(Dispatchers.IO) {
                    OotdImageUtils.compressToBytes(context(), Uri.parse(uri))
                }
                val part = MultipartBody.Part.createFormData("file", "community.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
                ids += api.upload(part).data().mediaId
            }
        }
        val body = CommunityWrite(request.title, request.content, ids, request.visibility,
            request.audienceUserIds, original?.version)
        return if (original == null) {
            val published = api.publish(body).data()
            // 发布成功后不再依赖第二次详情请求判定是否成功，
            // 避免帖子已保存却因详情超时提示失败，导致用户重复发布。
            TravelArticle(articleId = published.postId.toString(), authorId = published.authorId,
                authorName = com.example.citygo.skeleton.data.datastore.AccountSession.currentAccount.value?.nickname ?: "我",
                title = request.title, content = request.content, previewText = request.content.take(60),
                imgUrls = request.imgUrls, mediaIds = ids, visibility = request.visibility,
                audienceUserIds = request.audienceUserIds, version = published.version,
                canEdit = true, likeCount = 0, collectCount = 0)
        } else api.edit(original.articleId.toLong(), body).data().article()
    }
    override suspend fun publishArticle(request: PublishArticleRequest) = write(request)
    override suspend fun toggleLike(articleId: String): TravelArticle = interactionLock.withLock {
        val id = articleId.toLong()
        val before = api.detail(id).data()
        if (before.liked) api.unlike(id).data() else api.like(id).data()
        api.detail(id).data().article()
    }
    override suspend fun toggleCollect(articleId: String): TravelArticle = interactionLock.withLock {
        val id = articleId.toLong()
        val before = api.detail(id).data()
        if (before.collected) api.uncollect(id).data() else api.collect(id).data()
        api.detail(id).data().article()
    }
    override suspend fun addComment(articleId: String, comment: ArticleComment): TravelArticle {
        sendComment(articleId, comment.content, null)
        return getArticleById(articleId)
    }
    suspend fun comments(id: String, root: Long?, page: Int) = api.comments(id.toLong(), root, page).data()
    suspend fun sendComment(id: String, text: String, reply: Long?) = api.comment(id.toLong(), CommunityCommentWrite(text, reply)).data()
    suspend fun deleteComment(id: String, comment: Long) { api.deleteComment(id.toLong(), comment).data() }
    suspend fun delete(id: String) { api.delete(id.toLong()).data() }
    suspend fun users(source: String, page: Int) = api.users(source, page).data()
    suspend fun search(id: Long) = api.search(id).data()
    suspend fun follow(id: Long, followed: Boolean) = if (followed) api.unfollow(id).data() else api.follow(id).data()

    fun CommunityPost.article() = TravelArticle(
        articleId = postId.toString(), authorName = authorName ?: "用户$authorId",
        title = title.orEmpty(), previewText = content.orEmpty().take(60), content = content.orEmpty(),
        imgUrls = images.map { it.previewUrl.orEmpty() }, mediaIds = images.map { it.mediaId },
        likeCount = likeCount.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
        collectCount = collectCount.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
        isLiked = liked, isCollected = collected, commentCount = commentCount,
        canEdit = canEdit, canComment = canComment, visibility = visibility,
        audienceUserIds = audienceUserIds.orEmpty(), version = version, authorId = authorId,
    )
    fun CommunityComment.comment() = ArticleComment(authorName ?: "用户", content.orEmpty(),
        commentId, rootId, canDelete, replyToUserName)
}
