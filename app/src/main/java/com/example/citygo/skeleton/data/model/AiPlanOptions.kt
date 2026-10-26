package com.example.citygo.skeleton.data.model

/* =====================================================================================
 * AI 行程生成参数（计划页「行程配置」表单提交内容）
 *
 * - 正式生成链路：计划页 → AppViewModel.submitTravelGuideTask → 后端 TravelApi。
 * - 表单参数作为 GenerateTaskRequest 提交，行程结果由业务仓库保存。
 * ===================================================================================== */

/**
 * 一次 AI 行程生成的全部配置参数。
 *
 * @property days 出行天数（1-15，用户在表单自行设定）
 * @property budgetYuan 人均预算（元）；null 表示用户未填写
 * @property preferences 游玩偏好标签（多选，取值见 [AiPlanOptions.PreferenceLabels]）
 * @property petFriendly 「优先宠物友好地点」开关（沿用既有开关；偏好中勾选宠物友好时同样生效）
 * @property remark 额外备注（选填）：个性化附加要求（如避开人流、节奏慢、推荐本地小吃），
 *           提交后端任务时作为请求体 remark 字段，由后端入库并透传给 Agent 作为附加指令
 */
data class AiPlanOptions(
    val days: Int = 1,
    val budgetYuan: Int? = null,
    val preferences: List<String> = emptyList(),
    val petFriendly: Boolean = false,
    val remark: String? = null,
) {
    companion object {
        /** 合法天数范围（表单下拉与生成链路统一约束） */
        const val MIN_DAYS = 1
        const val MAX_DAYS = 15

        /** 游玩偏好候选标签（表单与后端请求共用的稳定中文编码） */
        const val PREF_PET = "宠物友好"
        const val PREF_RELAX = "休闲"
        const val PREF_PHOTO = "打卡拍照"
        const val PREF_FOOD = "美食"
        const val PREF_NATURE = "自然风光"

        /** 表单多选项（展示顺序） */
        val PREFERENCE_CHOICES: List<String> = listOf(
            PREF_PET,
            PREF_RELAX,
            PREF_PHOTO,
            PREF_FOOD,
            PREF_NATURE,
        )
    }
}
