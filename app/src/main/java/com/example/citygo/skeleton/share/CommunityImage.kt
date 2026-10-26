package com.example.citygo.skeleton.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

/* =====================================================================================
 * 社区统一图片组件
 *
 * 三种数据来源：
 * - `mock://scene/{id}`：本地 Canvas 场景（Mock 数据，零网络依赖，瀑布流靠宽高比自适应）；
 * - `https://…`：后端真实图片（后端联调时直接用，自带加载中 / 加载失败占位）；
 * - `content://…`：发布页从系统相册选择的本地图片（PhotoPicker 返回的只读 Uri，
 *    仅本次安装会话内可读，符合 Mock 阶段「发布后立即在列表看到」的需求；
 *    接入后端时应先上传文件换取 https URL 再入库）。
 *
 * @param fit 图片填充方式：
 *   [ImageFit.Adaptive] 卡片用——宽度填满、高度随图片宽高比自适应；
 *   [ImageFit.Fill] 轮播 / 固定高度容器用——铺满调用方区域，居中裁剪。
 * ===================================================================================== */

enum class ImageFit { Adaptive, Fill }

/** 地址是否为本地 mock 场景（卡片需要据此提前拿到宽高比，避免加载跳动） */
fun isMockImage(url: String): Boolean = url.startsWith("mock://")

/** mock 场景宽高比（宽 / 高）；非 mock 地址返回 null */
fun mockAspectOrNull(url: String): Float? =
    if (isMockImage(url)) MockCoverScenes.aspectOf(MockCoverScenes.sceneIdOf(url)) else null

@Composable
fun CommunityImage(
    data: String,
    modifier: Modifier = Modifier,
    fit: ImageFit = ImageFit.Fill,
    cornerShape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(0.dp),
    contentDescription: String? = null,
) {
    Box(modifier = modifier.clip(cornerShape)) {
        when {
            isMockImage(data) -> {
                val sceneId = MockCoverScenes.sceneIdOf(data)
                if (fit == ImageFit.Adaptive) {
                    // 用固定宽高比撑出与真实图片一致的自适应高度（瀑布流错落来源）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(MockCoverScenes.aspectOf(sceneId))
                            .clip(cornerShape),
                    ) {
                        MockSceneCover(sceneId = sceneId, modifier = Modifier.fillMaxSize())
                    }
                } else {
                    MockSceneCover(sceneId = sceneId, modifier = Modifier.fillMaxSize())
                }
            }

            else -> {
                val context = LocalContext.current
                // 真实网络图 / 本地 content Uri 都走 Coil；crossfade 让加载完成更柔和
                val request = ImageRequest.Builder(context)
                    .data(data)
                    .crossfade(true)
                    .build()

                if (fit == ImageFit.Adaptive) {
                    // 自适应模式：加载 / 失败期间给 160dp 最小高度占位，成功后按图片真实宽高比排版
                    SubcomposeAsyncImage(
                        model = request,
                        contentDescription = contentDescription,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp),
                        loading = { ImageLoadingPlaceholder() },
                        error = { ImageErrorPlaceholder() },
                        success = { state ->
                            val intrinsic = state.painter.intrinsicSize
                            val ratio = if (intrinsic.width > 0f && intrinsic.height > 0f) {
                                intrinsic.width / intrinsic.height
                            } else {
                                0.75f
                            }
                            Image(
                                painter = state.painter,
                                contentDescription = contentDescription,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(ratio),
                            )
                        },
                    )
                } else {
                    SubcomposeAsyncImage(
                        model = request,
                        contentDescription = contentDescription,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = { ImageLoadingPlaceholder() },
                        error = { ImageErrorPlaceholder() },
                    )
                }
            }
        }
    }
}

/** 加载中占位：浅灰底 + 居中转圈 */
@Composable
internal fun ImageLoadingPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFECEFF1)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(26.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 加载失败占位：灰底 + 断图图标 + 提示 */
@Composable
internal fun ImageErrorPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE8E8E8)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.BrokenImage,
                contentDescription = null,
                tint = Color(0xFF9AA5AB),
                modifier = Modifier.size(30.dp),
            )
            Text(
                text = "图片加载失败",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF9AA5AB),
                textAlign = TextAlign.Center,
            )
        }
    }
}
