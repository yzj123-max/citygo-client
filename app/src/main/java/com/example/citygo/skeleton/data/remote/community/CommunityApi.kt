package com.example.citygo.skeleton.data.remote.community

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

data class CommunityResponse<T>(val code: Int = 0, val msg: String? = null, val data: T? = null)
data class CommunityPage<T>(val page: Long = 1, val pageSize: Long = 20, val total: Long = 0,
    val hasMore: Boolean = false, val records: List<T> = emptyList())
data class CommunityImage(val mediaId: Long = 0, val previewUrl: String? = null, val sortOrder: Int = 0)
data class CommunityPost(val postId: Long = 0, val authorId: Long = 0, val authorName: String? = null,
    val title: String? = null, val content: String? = null, val images: List<CommunityImage> = emptyList(),
    val visibility: String = "PRIVATE", val liked: Boolean = false, val collected: Boolean = false,
    val likeCount: Long = 0, val collectCount: Long = 0, val commentCount: Long = 0,
    val canEdit: Boolean = false, val canComment: Boolean = false, val version: Long = 0,
    val audienceUserIds: List<Long>? = null)
data class CommunityComment(val commentId: Long = 0, val authorName: String? = null,
    val content: String? = null, val rootId: Long? = null, val replyToUserName: String? = null,
    val canDelete: Boolean = false)
data class CommunityUser(val userId: Long = 0, val nickname: String? = null,
    val following: Boolean = false, val followedBy: Boolean = false, val friend: Boolean = false)
data class CommunityMedia(val mediaId: Long = 0)
data class CommunityWrite(val title: String, val content: String, val mediaIds: List<Long>,
    val visibility: String, val audienceUserIds: List<Long>, val version: Long? = null)
data class CommunityPublished(val postId: Long = 0, val authorId: Long = 0, val version: Long = 0)
data class CommunityCommentWrite(val content: String, val replyToCommentId: Long? = null)

interface CommunityApi {
    @GET("api/posts") suspend fun feed(@Query("page") page: Int, @Query("pageSize") size: Int): Response<CommunityResponse<CommunityPage<CommunityPost>>>
    @GET("api/me/posts") suspend fun mine(@Query("page") page: Int, @Query("pageSize") size: Int): Response<CommunityResponse<CommunityPage<CommunityPost>>>
    @GET("api/me/collections") suspend fun collections(@Query("page") page: Int, @Query("pageSize") size: Int): Response<CommunityResponse<CommunityPage<CommunityPost>>>
    @GET("api/posts/{id}") suspend fun detail(@Path("id") id: Long): Response<CommunityResponse<CommunityPost>>
    @Multipart @POST("api/posts/media") suspend fun upload(@Part file: MultipartBody.Part): Response<CommunityResponse<CommunityMedia>>
    @POST("api/posts") suspend fun publish(@Body body: CommunityWrite): Response<CommunityResponse<CommunityPublished>>
    @PUT("api/posts/{id}") suspend fun edit(@Path("id") id: Long, @Body body: CommunityWrite): Response<CommunityResponse<CommunityPost>>
    @DELETE("api/posts/{id}") suspend fun delete(@Path("id") id: Long): Response<CommunityResponse<com.google.gson.JsonObject>>
    @PUT("api/posts/{id}/like") suspend fun like(@Path("id") id: Long): Response<CommunityResponse<com.google.gson.JsonObject>>
    @DELETE("api/posts/{id}/like") suspend fun unlike(@Path("id") id: Long): Response<CommunityResponse<com.google.gson.JsonObject>>
    @PUT("api/posts/{id}/collect") suspend fun collect(@Path("id") id: Long): Response<CommunityResponse<com.google.gson.JsonObject>>
    @DELETE("api/posts/{id}/collect") suspend fun uncollect(@Path("id") id: Long): Response<CommunityResponse<com.google.gson.JsonObject>>
    @GET("api/posts/{id}/comments") suspend fun comments(@Path("id") id: Long, @Query("rootId") root: Long?, @Query("page") page: Int, @Query("pageSize") size: Int = 20): Response<CommunityResponse<CommunityPage<CommunityComment>>>
    @POST("api/posts/{id}/comments") suspend fun comment(@Path("id") id: Long, @Body body: CommunityCommentWrite): Response<CommunityResponse<CommunityComment>>
    @DELETE("api/posts/{id}/comments/{comment}") suspend fun deleteComment(@Path("id") id: Long, @Path("comment") comment: Long): Response<CommunityResponse<com.google.gson.JsonObject>>
    @GET("api/users/search") suspend fun search(@Query("userId") id: Long): Response<CommunityResponse<List<CommunityUser>>>
    @GET("api/me/{relation}") suspend fun users(@Path("relation") relation: String, @Query("page") page: Int, @Query("pageSize") size: Int = 20): Response<CommunityResponse<CommunityPage<CommunityUser>>>
    @PUT("api/users/{id}/follow") suspend fun follow(@Path("id") id: Long): Response<CommunityResponse<CommunityUser>>
    @DELETE("api/users/{id}/follow") suspend fun unfollow(@Path("id") id: Long): Response<CommunityResponse<CommunityUser>>
}
