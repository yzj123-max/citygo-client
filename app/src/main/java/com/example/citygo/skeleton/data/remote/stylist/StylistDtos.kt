package com.example.citygo.skeleton.data.remote.stylist

import com.google.gson.annotations.SerializedName

/** 多图行程穿搭请求中的 travelSchedule JSON；图片由 multipart 单独提交。
 * 正式接口：POST /api/stylist/chat/multiImgChatSse。
 */

/**
 * SSE 搭配接口请求体中的「单日景点」描述。
 *
 * @param dayName   第几天文案，如「第一天」
 * @param spotName  景点名称，如「岳麓山」
 * @param spotStyle 景点风格，如「山林自然」
 * @param spotTone  景点色调，如「清新绿色调」
 * @param spotDesc  景点补充描述（门票 / 简介等）
 */
data class StylistDailyPlanDto(
    @SerializedName("dayName") val dayName: String,
    @SerializedName("spotName") val spotName: String,
    @SerializedName("spotStyle") val spotStyle: String,
    @SerializedName("spotTone") val spotTone: String,
    @SerializedName("spotDesc") val spotDesc: String,
)

/** 行程包裹层：{"dailyPlanList": [ ... ]} */
data class StylistScheduleDto(
    @SerializedName("dailyPlanList") val dailyPlanList: List<StylistDailyPlanDto>,
)

