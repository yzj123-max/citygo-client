package com.example.citygo.skeleton.screens.user

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* =====================================================================================
 * 用户资料模块公共 UI（深色配色与切换账号 / 设置页保持统一）+ 常量
 * ===================================================================================== */

/** 性别取值与账号体系既有约定一致：1=男，2=女；null=未设置 */
const val GENDER_MALE = 1
const val GENDER_FEMALE = 2

internal val UserProfileBg: Color
    @Composable get() = MaterialTheme.colorScheme.background
internal val UserProfileCardBg: Color
    @Composable get() = MaterialTheme.colorScheme.surface
internal val UserProfileFieldBg: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant
internal val UserProfileAccentRed: Color
    @Composable get() = MaterialTheme.colorScheme.primary
internal val UserProfileTextPrimary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface
internal val UserProfileTextSecondary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
internal val UserProfileDivider: Color
    @Composable get() = MaterialTheme.colorScheme.outlineVariant

/** 性别码 → 展示文案 */
fun genderText(gender: Int?): String = when (gender) {
    GENDER_MALE -> "男"
    GENDER_FEMALE -> "女"
    else -> "未填写"
}

/**
 * 全屏加载遮罩：半透明黑底 + 圆角卡片 + 转圈 + 文案；吞掉触摸防误操作。
 * 用于资料拉取 / 保存 / 头像上传。
 */
@Composable
internal fun LoadingOverlay(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(enabled = false) { /* 拦截触摸 */ },
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = RoundedCornerShape(14.dp), color = UserProfileCardBg) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                    color = UserProfileAccentRed,
                )
                Spacer(modifier = Modifier.size(14.dp))
                Text(text = text, fontSize = 15.sp, color = UserProfileTextPrimary)
            }
        }
    }
}

/**
 * 资料卡信息行：左侧标签、右侧值（值为空显示「未填写」灰色占位，绝不显示 null）。
 */
@Composable
internal fun ProfileInfoRow(label: String, value: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = UserProfileTextSecondary,
            modifier = Modifier.size(width = 72.dp, height = 20.dp),
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = value?.takeIf { it.isNotBlank() } ?: "未填写",
            fontSize = 15.sp,
            color = if (value.isNullOrBlank()) {
                UserProfileTextSecondary
            } else {
                UserProfileTextPrimary
            },
            fontWeight = FontWeight.Normal,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp)),
        )
    }
}
