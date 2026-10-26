package com.example.citygo.skeleton.recommend

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.citygo.skeleton.data.model.ScenicSpot
import com.example.citygo.skeleton.data.model.SpotTag
import com.example.citygo.skeleton.share.CommunityImage
import com.example.citygo.skeleton.share.ImageFit
import com.example.citygo.ui.theme.AccentTeal
import com.example.citygo.ui.theme.PaidOrange
import com.example.citygo.ui.theme.StarYellow

/* =====================================================================================
 * 景点模块公共 UI 组件：热门/小众标签、星级评分、推荐卡片
 * 首页推荐列表与搜索结果页复用同一卡片样式。
 * ===================================================================================== */

/** 评价数中文格式化：≥1 万显示「x.x 万」 */
fun formatReviewCount(count: Int): String =
    if (count >= 10000) "%.1f万".format(count / 10000.0) else count.toString()

/**
 * 热门 / 小众标签小胶囊。
 */
@Composable
fun SpotTagBadge(tag: SpotTag, modifier: Modifier = Modifier) {
    val color = if (tag == SpotTag.HOT) PaidOrange else AccentTeal
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = tag.label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * 五星评分展示（支持半星）+ 分数 / 评价数。
 *
 * @param score 0.0-5.0 评分
 * @param showScore 是否在星星右侧显示数字分数
 * @param reviewCount 评价总数（非 null 时显示「N 条评价」）
 */
@Composable
fun ScoreStars(
    score: Double,
    modifier: Modifier = Modifier,
    starSize: androidx.compose.ui.unit.Dp = 13.dp,
    showScore: Boolean = true,
    reviewCount: Int? = null,
    starColor: Color = StarYellow,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        repeat(5) { index ->
            val residual = (score - index).toFloat()
            val icon = when {
                residual >= 1f -> Icons.Filled.Star
                residual >= 0.5f -> Icons.Filled.StarHalf
                else -> Icons.Filled.StarBorder
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = starColor,
                modifier = Modifier.size(starSize),
            )
        }
        if (showScore) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = "%.1f".format(score),
                style = MaterialTheme.typography.labelMedium,
                color = starColor,
                fontWeight = FontWeight.Bold,
            )
        }
        if (reviewCount != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = "${formatReviewCount(reviewCount)}条评价",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 景点推荐卡片：左封面 + 右信息（名称 / 标签 / 星级评分 / 简介 / 门票 / 避坑摘要 / 城市）。
 * 首页列表与搜索结果列表共用，点击由 [onClick] 回调，跳转详情页时传 spotId。
 */
@Composable
fun SpotCard(
    spot: ScenicSpot,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onClick,
    ) {
        // IntrinsicSize.Min：右侧信息行增高时，左封面同步撑满卡片高度
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            // 封面图：CommunityImage 自带加载中转圈 / 加载失败断图占位
            Box(
                modifier = Modifier
                    .width(118.dp)
                    .heightIn(min = 118.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)),
            ) {
                AttractionImage(
                    urls = (listOf(spot.coverImg) + spot.imgUrls).distinct(),
                    modifier = Modifier.fillMaxWidth().height(118.dp),
                    description = spot.name,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = spot.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(6.dp))
                    if (spot.backendData) Text(if (spot.backendId != null) "已收录" else "景点", style = MaterialTheme.typography.labelSmall)
                    else SpotTagBadge(tag = spot.tag)
                }

                Spacer(Modifier.height(5.dp))
                if (spot.score > 0.0) {
                    ScoreStars(score = spot.score)
                } else {
                    // POI 暂无评分数据时展示占位文案，避免空星 + 0.0 被误解为差评
                    Text(
                        text = "暂无评分",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = spot.shortDesc.ifBlank { spot.address.ifBlank { "暂无介绍" } },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(4.dp))
                // 门票摘要
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.ConfirmationNumber,
                        contentDescription = null,
                        tint = PaidOrange,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = if (spot.backendData) when (spot.ticketStatus) {
                            "FREE" -> "免费（已核实）"
                            "PAID" -> "收费（详见政策）"
                            "PARTIAL" -> "部分项目收费"
                            else -> spot.collectedInfo?.ticketPrice?.takeIf { it.isNotBlank() }?.let { "票价参考：$it" } ?: "门票待核实"
                        } else ticketShort(spot.ticketInfo),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(3.dp))
                if (spot.backendData) Text(when (spot.petPolicy) {
                    "ALLOWED" -> "允许携宠（已核实）"
                    "CONDITIONAL" -> "携宠有条件，请查看详情"
                    "NOT_ALLOWED" -> "不允许携宠（已核实）"
                    else -> spot.collectedInfo?.petFriendlyLevel?.takeIf { it.isNotBlank() }?.let { "采集携宠信息：$it" } ?: "宠物政策待核实"
                }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // 避坑提示摘要（取第一条，完整列表在详情页展示）
                spot.pitTips.firstOrNull()?.let { tip ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Lightbulb,
                            contentDescription = null,
                            tint = AccentTeal,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = "避坑：$tip",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = spot.city + (spot.distanceMeters?.let { " · 直线${if (it < 1000) "${it}米" else "%.1f公里".format(it / 1000.0)}" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // POI 免费接口不返回评价数，为 0 时不展示「N 人去过」
                    if (spot.reviewCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${formatReviewCount(spot.reviewCount)}人去过",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 门票长文案 → 卡片摘要短文案（免费优先，其余截断到 10 字内） */
private fun ticketShort(ticketInfo: String): String = when {
    ticketInfo.isBlank() -> "票价以景区公示为准"
    ticketInfo.contains("免费") -> "免费开放"
    else -> ticketInfo.substringBefore("（").substringBefore("，").take(10)
}

/* ---------------------------------- 加载占位骨架卡 ---------------------------------- */

/**
 * 景点卡片加载骨架：城市切换 / 搜索请求期间展示的占位卡片（灰条 + 呼吸动画）。
 * 结构与 [SpotCard] 保持一致，让加载完成后列表无跳动感。
 */
@Composable
fun SpotCardSkeleton(modifier: Modifier = Modifier) {
    // 呼吸动画：灰条透明度在 0.35 ~ 0.7 之间循环
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton-alpha",
    )
    val barColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            // 封面占位
            Box(
                modifier = Modifier
                    .width(118.dp)
                    .heightIn(min = 118.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                    .background(barColor),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                SkeletonBar(widthFraction = 0.55f, color = barColor, height = 14.dp)
                Spacer(Modifier.height(8.dp))
                SkeletonBar(widthFraction = 0.4f, color = barColor, height = 11.dp)
                Spacer(Modifier.height(8.dp))
                SkeletonBar(widthFraction = 0.9f, color = barColor, height = 9.dp)
                Spacer(Modifier.height(8.dp))
                SkeletonBar(widthFraction = 0.5f, color = barColor, height = 9.dp)
                Spacer(Modifier.height(7.dp))
                SkeletonBar(widthFraction = 0.7f, color = barColor, height = 9.dp)
            }
        }
    }
}

/** 骨架卡内的灰色占位条（宽度按父容器比例） */
@Composable
private fun SkeletonBar(
    widthFraction: Float,
    color: Color,
    height: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(3.dp))
            .background(color),
    )
}
