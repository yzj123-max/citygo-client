package com.example.citygo.ui.theme

import androidx.compose.ui.graphics.Color

/* ===================== 品牌色与语义强调色（深浅色通用） =====================
 * 整体定位「清新旅游风」：森林绿为主色，天空蓝为辅，暖阳橙为点缀；
 * 语义色（点赞红 / 评分黄 / 危险红等）保持稳定，不受品牌色切换影响。
 * ====================================================================== */

/** 品牌主色：森林绿（Logo / 主按钮 / 选中态，全 App 唯一品牌色来源） */
val BrandTeal = Color(0xFF205B46)
/** 深森林绿（渐变末端 / 按压态） */
val BrandTealDeep = Color(0xFF184735)
/** 浅草绿（渐变高光端） */
val BrandTealLight = Color(0xFFACC6AC)
/** 天空蓝（收藏攻略 / 信息提示） */
val AccentBlue = Color(0xFF597562)
/** 青翠绿（浏览记录 / 成功 / 景点辅助） */
val AccentTeal = Color(0xFF53795C)
/** 暖橙色（切换账号 / 餐厅点位 / 暖阳点缀） */
val AccentOrange = Color(0xFFD98A45)
/** 粉色（收藏帖子 / 宠物 / tertiary 语义点缀） */
val AccentPink = Color(0xFF9A7756)
/** 溪 cyan（宠物档案图标） */
val AccentCyan = Color(0xFF6A8C73)
/** 点赞红心 */
val LikeRed = Color(0xFFFF4D6D)
/** 危险 / 差评 / 删除红 */
val DangerRed = Color(0xFFD05A5A)
/** 好评 / 成功绿 / 免费标签 */
val SuccessGreen = Color(0xFF2E9E5B)
/** 评分星标黄 */
val StarYellow = Color(0xFFFFB300)
/** 收费标签橙 */
val PaidOrange = Color(0xFFEF7C47)

/* ===================== 浅色主题色板（森林绿 + 天空蓝） ===================== */

val LightPrimary = BrandTeal
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE3EDDF)
val LightOnPrimaryContainer = Color(0xFF183D2A)

val LightSecondary = AccentBlue
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFEDF0E6)
val LightOnSecondaryContainer = Color(0xFF293C30)

val LightTertiary = AccentOrange
val LightTertiaryContainer = Color(0xFFF6E8D8)
val LightOnTertiaryContainer = Color(0xFF523720)

val LightError = DangerRed
val LightErrorContainer = Color(0xFFFFE3E1)

val LightBackground = Color(0xFFF7F7F2)
val LightOnBackground = Color(0xFF182F29)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF182F29)
val LightSurfaceVariant = Color(0xFFEEF1E9)
val LightOnSurfaceVariant = Color(0xFF647269)
val LightOutline = Color(0xFF8B9B8D)
val LightOutlineVariant = Color(0xFFDDE4D9)

/* ===================== 深色主题色板（夜间护眼墨青） ===================== */

val DarkPrimary = Color(0xFFAACDAE)
val DarkOnPrimary = Color(0xFF183D2A)
val DarkPrimaryContainer = Color(0xFF2C4F38)
val DarkOnPrimaryContainer = Color(0xFFD2E9CF)

val DarkSecondary = Color(0xFFB9CBB5)
val DarkOnSecondary = Color(0xFF223B2B)
val DarkSecondaryContainer = Color(0xFF3B5140)
val DarkOnSecondaryContainer = Color(0xFFDAE7D3)

val DarkTertiary = Color(0xFFFFBE82)
val DarkTertiaryContainer = Color(0xFF663C10)
val DarkOnTertiaryContainer = Color(0xFFFFDDBE)

val DarkError = Color(0xFFFFB3AF)
val DarkErrorContainer = Color(0xFF5C2624)

val DarkBackground = Color(0xFF101A15)
val DarkOnBackground = Color(0xFFE1E9DE)
val DarkSurface = Color(0xFF19261D)
val DarkOnSurface = Color(0xFFE1E9DE)
val DarkSurfaceVariant = Color(0xFF2E3E30)
val DarkOnSurfaceVariant = Color(0xFFBFCDBD)
val DarkOutline = Color(0xFF8D9F8C)
val DarkOutlineVariant = Color(0xFF3B4B3B)
