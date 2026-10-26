package com.example.citygo.skeleton.data.remote.attraction

import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.*

data class AttractionResponse<T>(val code: Int = 0, val msg: String? = null, val data: T? = null)
data class AttractionPage(val page: Long = 1, val pageSize: Long = 20, val total: Long? = null,
    val returnedCount: Int = 0, val hasMore: Boolean = false, val records: List<AttractionDto> = emptyList())
data class AttractionDto(
    val attractionId: Long? = null, val amapPoiId: String? = null, val name: String? = null,
    val province: String? = null, val city: String? = null, val district: String? = null,
    val level: String? = null, val address: String? = null, val phone: String? = null,
    val longitude: Double? = null, val latitude: Double? = null, val coordSystem: String? = null,
    val categoryCode: String? = null, val categoryName: String? = null,
    val summary: String? = null, val description: String? = null, val highlights: String? = null,
    val suggestedDuration: String? = null, val bestSeason: String? = null,
    val trafficTips: String? = null, val visitTips: String? = null,
    val descriptionSourceName: String? = null, val descriptionSourceUrl: String? = null,
    val descriptionVerifiedAt: String? = null,
    val coverUrl: String? = null, val images: List<AttractionImageDto>? = null,
    val policies: List<AttractionPolicyDto>? = null, val ticketStatus: String? = null,
    val petPolicy: String? = null, val canNavigate: Boolean = false,
    val linked: Boolean = false, val distanceMeters: Long? = null,
    val collectedInfo: AttractionCollectedInfo? = null,
)

data class AttractionCollectedInfo(val ticketPrice: String? = null, val discountPolicy: String? = null,
    val openTime: String? = null, val feature: String? = null, val petFriendlyLevel: String? = null,
    val petFriendlinessScore: String? = null, val petPolicy: String? = null,
    val petPolicySource: String? = null, val recordedReviewTime: String? = null)
data class AttractionImageDto(val imageId: Long? = null, val url: String? = null,
    val cover: Boolean = false, val sourceName: String? = null)
data class AttractionPolicyDto(val policyType: String = "", val policyValue: String = "UNKNOWN",
    val content: String? = null, val structuredData: JsonElement? = null,
    val sourceType: String? = null, val sourceName: String? = null, val sourceUrl: String? = null,
    val verifiedAt: String? = null, val effectiveFrom: String? = null, val effectiveUntil: String? = null,
    val temporalStatus: String? = null, val reviewRequired: Boolean = false)
data class AttractionResolveRequest(val amapPoiId: String)
data class AttractionResolveDto(val attractionId: Long = 0, val attractionName: String? = null,
    val amapPoiId: String? = null, val created: Boolean = false)
data class AttractionNavigationDto(val attractionId: Long = 0, val attractionName: String? = null,
    val travelMode: String? = null, val entranceId: Long? = null, val destinationName: String? = null,
    val destinationType: String? = null, val address: String? = null, val longitude: Double? = null,
    val latitude: Double? = null, val coordSystem: String? = null, val amapPoiId: String? = null,
    val fallback: Boolean = false, val notice: String? = null, val sourceName: String? = null,
    val sourceUrl: String? = null, val verifiedAt: String? = null)

interface AttractionApi {
    @GET("api/attractions") suspend fun list(
        @Query("city") city: String?, @Query("keyword") keyword: String? = null,
        @Query("categoryCode") category: String? = null, @Query("ticketStatus") ticket: String? = null,
        @Query("petPolicy") pet: String? = null, @Query("page") page: Int = 1,
        @Query("pageSize") size: Int = 20): Response<AttractionResponse<AttractionPage>>
    @GET("api/attractions/search") suspend fun search(@Query("keyword") keyword: String,
        @Query("city") city: String? = null, @Query("categoryCode") category: String? = null,
        @Query("page") page: Int = 1, @Query("pageSize") size: Int = 20): Response<AttractionResponse<AttractionPage>>
    @GET("api/attractions/nearby") suspend fun nearby(@Query("longitude") longitude: Double,
        @Query("latitude") latitude: Double, @Query("radius") radius: Int = 5000,
        @Query("coordSystem") coordSystem: String = "GCJ02", @Query("page") page: Int = 1,
        @Query("pageSize") size: Int = 20): Response<AttractionResponse<AttractionPage>>
    @POST("api/attractions/resolve") suspend fun resolve(@Body body: AttractionResolveRequest): Response<AttractionResponse<AttractionResolveDto>>
    @GET("api/attractions/{id}") suspend fun detail(@Path("id") id: Long): Response<AttractionResponse<AttractionDto>>
    @GET("api/attractions/{id}/navigation-target") suspend fun navigation(@Path("id") id: Long,
        @Query("travelMode") mode: String): Response<AttractionResponse<AttractionNavigationDto>>
}
