package com.example.citygo.skeleton.data.sync

import com.example.citygo.skeleton.data.remote.AppRetrofit
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Response
import retrofit2.http.*

data class TripEnvelope<T>(val code: Int, val msg: String?, val data: T?)
interface TripCloudApi {
    @GET("api/me/trips") suspend fun list(): TripEnvelope<List<TripSummary>>
    @GET("api/me/trips/current") suspend fun current(): TripEnvelope<CloudTrip>
    @GET("api/me/trips/{id}") suspend fun get(@Path("id") id: String): TripEnvelope<CloudTrip>
    @PUT("api/me/trips/{id}") suspend fun save(@Path("id") id: String, @Body write: RequestBody): Response<TripEnvelope<CloudTrip>>
    @POST("api/me/trips/{id}/activate") suspend fun activate(@Path("id") id: String): TripEnvelope<CloudTrip>
}

class TripConflictException(val remote: CloudTrip) : Exception("云端已有更新")
interface TripCloud {
    suspend fun list(): List<TripSummary>
    suspend fun current(): CloudTrip?
    suspend fun get(id: String): CloudTrip
    suspend fun save(id: String, write: TripWrite, serializedRequest: String? = null): CloudTrip
    suspend fun activate(id: String)
}

class RemoteTripCloud(private val api: TripCloudApi = AppRetrofit.createAuthed()) : TripCloud {
    private fun <T> TripEnvelope<T>.unwrap(): T? {
        check(code == 200) { msg ?: "云端同步失败" }
        return data
    }
    override suspend fun list() = api.list().unwrap().orEmpty()
    override suspend fun current() = api.current().unwrap()
    override suspend fun get(id: String) = checkNotNull(api.get(id).unwrap()) { "云端行程不存在" }
    override suspend fun activate(id: String) { api.activate(id).unwrap() }
    override suspend fun save(id: String, write: TripWrite, serializedRequest: String?): CloudTrip {
        // 使用日志中的原始正文；避免默认 Gson 丢弃 plan:null，也避免重试时改写请求内容。
        val raw = serializedRequest ?: GsonBuilder().serializeNulls().create().toJson(write)
        val response = api.save(id, raw.toRequestBody("application/json; charset=utf-8".toMediaType()))
        if (response.code() == 409) {
            val raw = response.errorBody()?.use { it.string() }.orEmpty()
            val node = JsonParser.parseString(raw).asJsonObject.get("data")
            throw TripConflictException(Gson().fromJson(node, CloudTrip::class.java))
        }
        check(response.isSuccessful) { "同步失败（HTTP ${response.code()}），本地数据已保留" }
        return checkNotNull(response.body()?.unwrap()) { "云端返回内容为空" }
    }
}
