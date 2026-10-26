package com.example.citygo.skeleton.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.citygo.skeleton.data.model.Post
import com.example.citygo.skeleton.data.model.Spot
import com.example.citygo.skeleton.share.CommunityImage
import com.example.citygo.skeleton.share.ImageFit

/* =====================================================================================
 * 「我的」模块二级页面：
 * - [FavoriteGuidesScreen] 我的收藏攻略
 * - [FavoritePostsScreen] 我的收藏帖子
 * - [BrowseHistoryScreen] 浏览记录（可清空）
 * - [PrivacyScreen] 隐私设置（纯 UI Mock 开关）
 * - [AboutScreen] 关于我们
 *
 * 均为独立路由页面：自带 Scaffold + TopAppBar（状态栏安全区自行适配），
 * 不显示底部 Tab 栏；数据来自 Activity 作用域的 [AppViewModel]。
 * ===================================================================================== */

/* ---------------------------------- 公共脚手架 ---------------------------------- */

/**
 * 二级页通用脚手架：返回箭头 + 标题 + 可选右侧操作 + 纵向内容区。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSubScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = { actions() },
            )
        },
        content = content,
    )
}

/** 列表空态占位。 */
@Composable
private fun EmptyTips(text: String, emoji: String = "📭") {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "$emoji\n$text",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/* ---------------------------------- 收藏景点 ---------------------------------- */

/**
 * 我的收藏攻略（收藏景点）列表页。
 *
 * 数据来自全局 AppViewModel 的 collectSpotList（Room 持久化），
 * @param spots 已收藏景点列表
 * @param onRemove 取消收藏（参数为景点对象），由导航层接到 ViewModel.removeCollectSpot
 * @param onOpenSpot 点击卡片跳转景点详情（参数为景点 id）
 */
@Composable
fun FavoriteGuidesScreen(
    spots: List<Spot>,
    onBack: () -> Unit,
    onRemove: (Spot) -> Unit,
    onOpenSpot: (String) -> Unit,
) {
    ProfileSubScaffold(title = "我的收藏攻略", onBack = onBack) { innerPadding ->
        if (spots.isEmpty()) {
            Box(Modifier.padding(innerPadding)) {
                EmptyTips(text = "还没有收藏景点\n去「推荐」页发现好去处吧", emoji = "📍")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(spots, key = { it.id }) { spot ->
                    CollectedSpotCard(
                        spot = spot,
                        onRemove = { onRemove(spot) },
                        onClick = { onOpenSpot(spot.id) },
                    )
                }
            }
        }
    }
}

/**
 * 单条收藏景点卡片：封面 + 名称 / 城市 / 评分 / 简介 + 取消收藏按钮。
 * 整卡可点进详情；右上星标为独立 IconButton，点击事件自行消费、不会触发卡片跳转。
 */
@Composable
private fun CollectedSpotCard(spot: Spot, onRemove: () -> Unit, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 城市角标
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "📍 ${spot.city.ifBlank { "未知城市" }}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                // 取消收藏（实心星，点击即从 Room 删除）
                IconButton(onClick = {
                    onRemove()
                    Toast.makeText(context, "已取消收藏", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "取消收藏",
                        tint = Color(0xFFFFB300),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                // 景点封面（加载失败时组件内部自带占位）
                CommunityImage(
                    data = spot.coverImg,
                    modifier = Modifier
                        .size(86.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    fit = ImageFit.Fill,
                    cornerShape = RoundedCornerShape(12.dp),
                    contentDescription = spot.name,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = spot.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (spot.score > 0.0) {
                        Text(
                            text = "评分 ${"%.1f".format(spot.score)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFFFB300),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text = spot.shortDesc.ifBlank { "暂无简介" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/* ---------------------------------- 收藏帖子 ---------------------------------- */

/**
 * 我的收藏帖子列表页。
 *
 * 数据来自全局 AppViewModel 的 collectPostList（Room 持久化），
 * @param posts 已收藏帖子列表
 * @param onRemove 取消收藏（参数为帖子对象），由导航层接到 ViewModel.removeCollectPost
 */
@Composable
fun FavoritePostsScreen(
    posts: List<Post>,
    onBack: () -> Unit,
    onRemove: (Post) -> Unit,
) {
    ProfileSubScaffold(title = "我的收藏帖子", onBack = onBack) { innerPadding ->
        if (posts.isEmpty()) {
            Box(Modifier.padding(innerPadding)) {
                EmptyTips(text = "还没有收藏帖子\n去「分享」页逛逛吧", emoji = "📝")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(posts, key = { it.id }) { post ->
                    CollectedPostRow(post = post, onRemove = { onRemove(post) })
                }
            }
        }
    }
}

/** 单条收藏帖子行：小封面 + 标题 / 作者 + 取消收藏按钮。 */
@Composable
private fun CollectedPostRow(post: Post, onRemove: () -> Unit) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            // 小封面
            CommunityImage(
                data = post.coverImg,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp)),
                fit = ImageFit.Fill,
                cornerShape = RoundedCornerShape(12.dp),
                contentDescription = post.title,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = post.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = post.authorName.ifBlank { "旅友" } +
                        if (post.likeCount > 0) " · ${post.likeCount} 赞" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            // 取消收藏（实心星，点击即从 Room 删除）
            IconButton(onClick = {
                onRemove()
                Toast.makeText(context, "已取消收藏", Toast.LENGTH_SHORT).show()
            }) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "取消收藏",
                    tint = Color(0xFFFFB300),
                )
            }
        }
    }
}

/* ---------------------------------- 浏览记录 ---------------------------------- */

/**
 * 浏览记录页。
 *
 * 数据来自全局 AppViewModel 的 browseHistoryList（Room 持久化），
 * DAO 已按浏览时间 viewedAt 倒序返回（最近浏览在前），同一景点重复进入只置顶不重复。
 *
 * @param records 最近浏览的景点列表（已按时间倒序）
 * @param onClearHistory 清空全部浏览记录，由导航层接到 ViewModel.clearBrowseHistory
 * @param onOpenSpot 点击记录跳转景点详情（参数为景点 id）
 */
@Composable
fun BrowseHistoryScreen(
    records: List<Spot>,
    onBack: () -> Unit,
    onClearHistory: () -> Unit,
    onOpenSpot: (String) -> Unit,
) {
    val context = LocalContext.current
    ProfileSubScaffold(
        title = "浏览记录",
        onBack = onBack,
        actions = {
            if (records.isNotEmpty()) {
                IconButton(onClick = {
                    onClearHistory()
                    Toast.makeText(context, "浏览记录已清空", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Filled.Delete, contentDescription = "清空浏览记录")
                }
            }
        },
    ) { innerPadding ->
        if (records.isEmpty()) {
            Box(Modifier.padding(innerPadding)) {
                EmptyTips(text = "暂无浏览记录\n去「推荐」页逛逛吧", emoji = "🕘")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(records, key = { it.id }) { spot ->
                    BrowseHistoryRow(spot = spot, onClick = { onOpenSpot(spot.id) })
                }
            }
        }
    }
}

/** 单条浏览记录行：小封面 + 景点名 + 城市 / 评分；整行可点进景点详情。 */
@Composable
private fun BrowseHistoryRow(spot: Spot, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            // 小封面
            CommunityImage(
                data = spot.coverImg,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp)),
                fit = ImageFit.Fill,
                cornerShape = RoundedCornerShape(12.dp),
                contentDescription = spot.name,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = spot.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append("📍 ")
                        append(spot.city.ifBlank { "未知城市" })
                        if (spot.score > 0.0) append(" · ⭐${"%.1f".format(spot.score)}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/* ---------------------------------- 隐私设置 ---------------------------------- */

/**
 * 隐私设置页（纯 UI Mock：开关仅本地状态，不写真实业务）。
 */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // 本地 Mock 开关状态（不进全局 VM：仅本页展示用）
    var personalRecommend by remember { mutableStateOf(true) }
    var strangerView by remember { mutableStateOf(false) }
    var showLocation by remember { mutableStateOf(true) }

    ProfileSubScaffold(title = "隐私设置", onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp),
        ) {
            SettingGroupCard {
                SwitchRow(
                    title = "个性化推荐",
                    description = "根据浏览记录推荐攻略与帖子（Mock）",
                    checked = personalRecommend,
                    onCheckedChange = { personalRecommend = it },
                )
                CardDivider()
                SwitchRow(
                    title = "允许陌生人查看我的笔记",
                    description = "关闭后仅互相关注好友可见（Mock）",
                    checked = strangerView,
                    onCheckedChange = { strangerView = it },
                )
                CardDivider()
                SwitchRow(
                    title = "允许获取位置信息",
                    description = "用于推荐附近景点与城市服务（Mock）",
                    checked = showLocation,
                    onCheckedChange = { showLocation = it },
                )
            }
            Spacer(Modifier.height(12.dp))
            SettingGroupCard {
                ArrowRow(title = "账号与安全") {
                    Toast.makeText(context, "账号与安全开发中（Mock）", Toast.LENGTH_SHORT).show()
                }
                CardDivider()
                ArrowRow(title = "黑名单管理") {
                    Toast.makeText(context, "黑名单管理开发中（Mock）", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

/* ---------------------------------- 关于我们 ---------------------------------- */

/**
 * 关于我们页（Mock 信息）。
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    ProfileSubScaffold(title = "关于我们", onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            // Logo 占位
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .background(
                        brush = Brush.linearGradient(listOf(0xFF6C5CE7, 0xFFB8B0FF).map { Color(it.toInt()) }),
                        shape = RoundedCornerShape(22.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("🧭", fontSize = 40.sp)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "智旅方途",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "版本 v1.0.0（Mock 演示版）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            SettingGroupCard(modifier = Modifier.fillMaxWidth()) {
                ArrowRow(title = "用户协议") {
                    Toast.makeText(context, "用户协议（Mock）", Toast.LENGTH_SHORT).show()
                }
                CardDivider()
                ArrowRow(title = "隐私政策") {
                    Toast.makeText(context, "隐私政策（Mock）", Toast.LENGTH_SHORT).show()
                }
                CardDivider()
                ArrowRow(title = "检查更新") {
                    Toast.makeText(context, "当前已是最新版本（Mock）", Toast.LENGTH_SHORT).show()
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "让每一次出发都有方向\nCopyright © 2026 智旅方途",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

/* ---------------------------------- 小组件 ---------------------------------- */

/** 设置项分组卡片。 */
@Composable
private fun SettingGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column { content() }
    }
}

/** 带开关的设置行。 */
@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 带右箭头的普通设置行。 */
@Composable
private fun ArrowRow(title: String, onClick: () -> Unit) {
    Surface(color = Color.Transparent, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** 卡片内分隔线。 */
@Composable
private fun CardDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .height(0.8.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}
