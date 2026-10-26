package com.example.citygo.skeleton.data.model

/* =====================================================================================
 * 游记分享社区领域模型（纯 Kotlin 数据层）
 *
 * 对应社区模块调试用数据结构 articleList；
 * 图片字段 [TravelArticle.imgUrls] 当前 Mock 使用本地场景占位（mock:// 前缀，
 * 由 MockCoverScenes 渲染），后端联调时替换为 https 直链即可，UI 层无需改动
 * （NoteImage 已具备网络图的加载中 / 失败占位）。
 * ===================================================================================== */

/**
 * 一条游记评论。
 *
 * @property userName 评论用户昵称
 * @property content 评论内容
 */
data class ArticleComment(
    val userName: String,
    val content: String,
    val commentId: Long = 0,
    val rootId: Long? = null,
    val canDelete: Boolean = false,
    val replyToUserName: String? = null,
)

/**
 * 游记实体。
 *
 * @property articleId 游记唯一 id（字符串，详情路由携带）
 * @property authorName 作者昵称
 * @property title 游记标题
 * @property previewText 卡片简短预览文字
 * @property imgUrls 配图地址列表（Mock 为 mock:// 场景；后端为 https；发布页为本地 content://）
 * @property content 游记完整正文（旅游感受 / 旅行笔记）
 * @property likeCount 点赞数
 * @property collectCount 收藏数
 * @property isLiked 当前用户是否已点赞
 * @property isCollected 当前用户是否已收藏
 * @property commentList 评论列表
 */
data class TravelArticle(
    val articleId: String,
    val authorName: String,
    val title: String,
    val previewText: String,
    val imgUrls: List<String>,
    val content: String,
    val likeCount: Int,
    val collectCount: Int,
    val isLiked: Boolean = false,
    val isCollected: Boolean = false,
    val commentList: List<ArticleComment> = emptyList(),
    val commentCount: Long = 0,
    val canEdit: Boolean = false,
    val canComment: Boolean = true,
    val visibility: String = "PRIVATE",
    val audienceUserIds: List<Long> = emptyList(),
    val mediaIds: List<Long> = emptyList(),
    val version: Long = 0,
    val authorId: Long = 0,
)

/**
 * 发布新游记的入参（发布页 → ViewModel → Repository）。
 */
data class PublishArticleRequest(
    val authorName: String,
    val title: String,
    val content: String,
    val imgUrls: List<String>,
    val visibility: String = "PRIVATE",
    val audienceUserIds: List<Long> = emptyList(),
)
