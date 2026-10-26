package com.example.citygo.skeleton.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp

/* =====================================================================================
 * 本地 Mock 封面「风景场景」
 *
 * 不依赖网络 / 不打包图片资源：用 Canvas 按场景配色程序化绘制天空、太阳、山峦与水面，
 * 瀑布流卡片高度靠 [MockCoverScenes.aspectOf]（宽高比）错落。
 *
 * Mock 数据中的图片地址形如 `mock://scene/3`；后端联调替换为 https 直链后，
 * 本文件与 CommunityImage 中的 mock 分支即不再被走到。
 * ===================================================================================== */

object MockCoverScenes {

    /** 场景数量（MockArticleRepository 循环取色时对齐） */
    const val SCENE_COUNT = 12

    /**
     * 取场景宽高比（宽 / 高），瀑布流据此产生不等高封面。
     * 比例在 0.60 ~ 0.85 间循环错落。
     */
    fun aspectOf(sceneId: Int): Float {
        val patterns = floatArrayOf(0.72f, 0.62f, 0.80f, 0.68f, 0.76f, 0.60f, 0.84f, 0.70f, 0.66f, 0.78f, 0.74f, 0.64f)
        return patterns[sceneId.coerceIn(0, SCENE_COUNT - 1) % patterns.size]
    }

    /** 从 mock:// 地址解析场景 id */
    fun sceneIdOf(url: String): Int =
        url.substringAfterLast('/').toIntOrNull()?.coerceIn(0, SCENE_COUNT - 1) ?: 0

    private val palettes = listOf(
        // 天空上 / 天空下 / 远山 / 近山 / 水面 / 太阳 / 装饰 emoji
        ScenePalette(0xFF6C5CE7, 0xFFA29BFE, 0x663D3A8C, 0xFF2D2B6B, 0xFF483E8C, 0xFFFFF3B0, "🏔"),
        ScenePalette(0xFF00B4DB, 0xFF7ED6F5, 0x661C6E94, 0xFF0E5C73, 0xFF0B6E88, 0xFFFFF6C9, "🌊"),
        ScenePalette(0xFFFF6A88, 0xFFFFC371, 0x669C4B3B, 0xFF6E3527, 0xFFC96A49, 0xFFFFF9DC, "🌆"),
        ScenePalette(0xFF11998E, 0xFF7BE7B0, 0x661F6B57, 0xFF124A3D, 0xFF1C7761, 0xFFFFF3B0, "🏝"),
        ScenePalette(0xFFE0569B, 0xFFFFB6C8, 0x668E3D63, 0xFF662A47, 0xFFB6457C, 0xFFFFF2D7, "🏮"),
        ScenePalette(0xFF56AB2F, 0xFFC9EE92, 0x663F7A34, 0xFF2E5A25, 0xFF4E8F38, 0xFFFFF8C8, "🌿"),
        ScenePalette(0xFFEF473A, 0xFFFFB15E, 0x6693402F, 0xFF672B22, 0xFFD15A3C, 0xFFFFF1B8, "🌶"),
        ScenePalette(0xFF2193B0, 0xFF9EDCF0, 0x66205E78, 0xFF17495E, 0xFF2B82A3, 0xFFFFF8D6, "⛵"),
        ScenePalette(0xFF8E2DE2, 0xFFFF8FB1, 0x665E2B88, 0xFF431F63, 0xFF7A39B5, 0xFFFFF4C2, "🌸"),
        ScenePalette(0xFF136A8A, 0xFF7FE9DE, 0x661A4F5E, 0xFF0E3C48, 0xFF157085, 0xFFFFFAD2, "🏞"),
        ScenePalette(0xFFF2994A, 0xFFFBD786, 0x668F5A2A, 0xFF68401C, 0xFFD4853E, 0xFFFFFBE0, "🍜"),
        ScenePalette(0xFF3973C7, 0xFFB6D8F5, 0x662C5285, 0xFF1E3C63, 0xFF3565A8, 0xFFFFF6CC, "❄"),
    )

    fun paletteOf(sceneId: Int): ScenePalette =
        palettes[sceneId.coerceIn(0, SCENE_COUNT - 1) % palettes.size]
}

/** 单个场景的配色 */
data class ScenePalette(
    val skyTop: Long,
    val skyBottom: Long,
    val farMountain: Long,
    val nearMountain: Long,
    val water: Long,
    val sun: Long,
    val emoji: String,
)

/**
 * 绘制一张「风景照片」：渐变天空 + 太阳 + 远近两层山 + 水面 + 场景 emoji 水印。
 * 直接铺满调用方给定的区域（卡片 / 轮播均可复用）。
 */
@Composable
fun MockSceneCover(
    sceneId: Int,
    modifier: Modifier = Modifier,
    showEmoji: Boolean = true,
) {
    val palette = MockCoverScenes.paletteOf(sceneId)
    Canvas(modifier = modifier.fillMaxSize()) {
        drawScene(palette, sceneId)
        if (showEmoji) drawEmojiBadge(palette.emoji, sceneId)
    }
}

private fun DrawScope.drawScene(p: ScenePalette, seed: Int) {
    val w = size.width
    val h = size.height

    // 天空
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(p.skyTop), Color(p.skyBottom)),
            startY = 0f,
            endY = h * 0.78f,
        ),
        size = Size(w, h),
    )

    // 太阳（位置随 seed 小幅变化）
    val sunCx = w * (0.70f + (seed % 3) * 0.06f)
    val sunCy = h * (0.20f + (seed % 2) * 0.04f)
    val sunR = w * 0.10f
    drawCircle(color = Color(p.sun).copy(alpha = 0.35f), radius = sunR * 1.55f, center = Offset(sunCx, sunCy))
    drawCircle(color = Color(p.sun), radius = sunR, center = Offset(sunCx, sunCy))

    // 远山（锯齿折线，随 seed 起伏）
    val farPath = Path().apply {
        moveTo(0f, h * 0.66f)
        val peaks = 5
        repeat(peaks) { i ->
            val x1 = w * (i + 0.5f) / peaks
            val y1 = h * (0.46f + 0.05f * ((i + seed) % 3))
            lineTo(x1, y1)
            lineTo(w * (i + 1f) / peaks, h * 0.66f)
        }
        lineTo(w, h * 0.74f)
        lineTo(0f, h * 0.74f)
        close()
    }
    drawPath(farPath, Color(p.farMountain))

    // 水面
    drawRect(
        color = Color(p.water),
        topLeft = Offset(0f, h * 0.72f),
        size = Size(w, h * 0.28f),
    )
    // 水面高光横线
    repeat(3) { i ->
        val y = h * (0.78f + i * 0.06f)
        drawLine(
            color = Color.White.copy(alpha = 0.18f),
            start = Offset(w * 0.12f, y),
            end = Offset(w * (0.88f - i * 0.12f), y),
            strokeWidth = 2.dp.toPx(),
        )
    }

    // 近山（更大的三角山脊，压在水面之上）
    val nearPath = Path().apply {
        moveTo(-w * 0.05f, h * 0.78f)
        val peaks = 3
        repeat(peaks) { i ->
            val cx = w * (i + 0.5f) / peaks
            val peakY = h * (0.56f - 0.06f * ((i + seed) % 2))
            lineTo(cx, peakY)
            lineTo(w * (i + 1.05f) / peaks, h * 0.78f)
        }
        lineTo(w * 1.05f, h * 0.9f)
        lineTo(-w * 0.05f, h * 0.9f)
        close()
    }
    drawPath(nearPath, Color(p.nearMountain))

    // 近山雪顶 / 高光（只在最高的峰尖画小三角）
    val highlight = Path().apply {
        val cx = w * 0.5f
        val peakY = h * (0.56f - 0.06f * ((1 + seed) % 2))
        moveTo(cx, peakY)
        lineTo(cx - w * 0.05f, peakY + h * 0.05f)
        lineTo(cx + w * 0.05f, peakY + h * 0.05f)
        close()
    }
    drawPath(highlight, Color.White.copy(alpha = 0.5f))
}

/** 右下角大号半透明 emoji，明确「本地 Mock 配图」身份 */
private fun DrawScope.drawEmojiBadge(emoji: String, seed: Int) {
    val paint = android.graphics.Paint().apply {
        textSize = size.width * 0.30f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
        alpha = 210
        // 让不同场景的 emoji 落点略有变化，避免千篇一律
    }
    val cx = size.width * (0.78f - (seed % 2) * 0.12f)
    val cy = size.height * 0.40f
    drawContext.canvas.nativeCanvas.drawText(emoji, cx, cy, paint)
}
