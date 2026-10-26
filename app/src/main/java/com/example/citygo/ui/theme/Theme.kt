package com.example.citygo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color

/** 浅色主题：森林绿为主色的自定义 M3 色板（不使用动态取色，保证品牌一致） */
private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    errorContainer = LightErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    surfaceTint = LightPrimary,
    surfaceDim = Color(0xFFE9EDE3),
    surfaceBright = LightSurface,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = Color(0xFFF6F7F1),
    surfaceContainer = Color(0xFFF1F4EC),
    surfaceContainerHigh = Color(0xFFEDF1E6),
    surfaceContainerHighest = Color(0xFFE7EDDF),
)

/** 深色主题：同一套品牌色映射到暗色容器，保证切换后视觉统一 */
private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    errorContainer = DarkErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    surfaceTint = DarkPrimary,
    surfaceDim = DarkBackground,
    surfaceBright = Color(0xFF344536),
    surfaceContainerLowest = Color(0xFF0D1711),
    surfaceContainerLow = DarkSurface,
    surfaceContainer = Color(0xFF1D2D21),
    surfaceContainerHigh = Color(0xFF26392A),
    surfaceContainerHighest = DarkSurfaceVariant,
)

/**
 * 全局 M3 形状令牌：
 * - small：输入框 / 小标签（14dp）
 * - medium：卡片（18dp）
 * - large：弹窗 / 大卡片（22dp）
 * - extraLarge：城市选择弹窗（24dp）
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/**
 * App 唯一主题入口。
 * @param darkTheme 由全局 ViewModel 的 isDarkTheme 驱动，切换即时全局生效
 */
@Composable
fun CityGoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 保留参数以兼容既有调用；本项目不使用 Android 12 动态取色，始终使用品牌色板
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
