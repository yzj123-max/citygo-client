package com.example.citygo.skeleton.data.remote.travel

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST

/**
 * AI 旅游攻略接口（Retrofit + Kotlin suspend 协程）——后端【异步任务】流程。
 *
 * 鉴权：本 Service 通过 AppRetrofit.createAuthedLongRead 创建，OkHttp 拦截器自动附加
 * `Authorization: Bearer <JWT_TOKEN>`（JWT 登录后存于 SharedPreferences）；
 * 读超时放宽到 300s（与后端 RestTemplate 对齐），OkHttp BODY 日志打印完整请求体 / 每一次轮询响应体（tag=AuthHttpClient）。
 *
 * 流程：
 * 1. [generate]：POST /travel/generateTask 提交配置，返回 taskId；
 * 2. [getTaskResult]：GET /travel/task/{taskId} 每 1.5s 轮询，
 *    PROCESSING 继续、响应包含 data 字段时拿到 Markdown 攻略正文。
 */
interface TravelApi {

    /**
     * 步骤 1：提交攻略生成任务。
     * JSON 请求体（字段名与后端逐字符对齐）：
     * city / dayNum / budget / preferenceList / petFriendlyFlag / remark。
     *
     * 响应为 JSON（含 taskId），用 [ResponseBody] 承接后由上层手动容错解析，
     * 兼容 {"success":true,"taskId":".."} / {"code":200,"data":{"taskId":".."}}
     * / {"data":"taskId"} 等包裹形式。
     * 路径不带前导 "/"，baseUrl 末尾必须带 "/"。
     */
    @POST("travel/generateTask")
    suspend fun generate(
        @Body request: GenerateTaskRequest,
    ): ResponseBody

    /**
     * 步骤 2：按 taskId 轮询任务结果。
     *
     * 注意：taskId 拼接在【URL 路径】中（@Path），不是 query 参数：
     * 实际请求 GET /travel/task/{taskId}。
     *
     * 典型响应：
     * - 处理中：{"success":true,"status":"PROCESSING"}；
     * - 已完成：{"success":true,"status":"SUCCESS","data":"# Markdown 攻略原文…"}；
     * 同样用 [ResponseBody] 承接、上层手动解析，避免不同环境字段差异导致 Gson 失败。
     */
    @GET("travel/task/{taskId}")
    suspend fun getTaskResult(
        @Path("taskId") taskId: String,
    ): ResponseBody
}
