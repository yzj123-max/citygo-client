package com.example.citygo.skeleton.data.remote.travel

import com.google.gson.annotations.SerializedName

/* =====================================================================================
 * AI 旅游攻略任务 —— 请求 DTO
 *
 * 后端契约（SpringBoot）：
 * POST /travel/generateTask（需 Authorization: Bearer JWT）
 *   请求 JSON：
 *   {
 *     "city": "长沙",
 *     "dayNum": 2,
 *     "budget": 1600,
 *     "preferenceList": ["美食", "拍照"],
 *     "petFriendlyFlag": true,
 *     "remark": "休闲慢节奏"
 *   }
 *   响应体：taskId；随后 GET /travel/task/{taskId} 查询状态与攻略正文。
 *   AppViewModel 额外兼容同步返回正文的服务版本，不另设生成入口。
 *
 * 字段名与后端 JSON 严格一致，一个字符都不能改
 * （@SerializedName 双保险，防止后续混淆/改名导致联调失败）。
 * ===================================================================================== */

/**
 * 请求体：提交攻略生成任务
 *
 * @param city             目的地城市，如「长沙」
 * @param dayNum           游玩天数
 * @param budget           预算（单位：元）；选填，null 代表不限预算
 * @param preferenceList   游玩偏好，字符串数组（如 ["美食","拍照"]），切勿传成逗号拼接字符串
 * @param petFriendlyFlag  是否宠物友好
 * @param remark           额外备注（个性化要求）
 */
data class GenerateTaskRequest(
    @SerializedName("city") val city: String,
    @SerializedName("dayNum") val dayNum: Int,
    @SerializedName("budget") val budget: Int? = null,
    @SerializedName("preferenceList") val preferenceList: List<String>,
    @SerializedName("petFriendlyFlag") val petFriendlyFlag: Boolean,
    @SerializedName("remark") val remark: String,
)
