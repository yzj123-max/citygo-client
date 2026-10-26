package com.example.citygo.skeleton.recommend

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.citygo.skeleton.data.AppViewModel
import com.example.citygo.skeleton.data.RecommendViewModel
import com.example.citygo.skeleton.data.SpotDetailLoadState
import com.example.citygo.skeleton.data.datastore.AccountSession
import com.example.citygo.skeleton.data.model.ScenicSpot
import com.example.citygo.skeleton.data.model.Spot
import com.example.citygo.skeleton.data.remote.attraction.AttractionPolicyDto
import com.example.citygo.skeleton.data.remote.attraction.AttractionCollectedInfo
import com.example.citygo.skeleton.data.repository.AttractionDisplayMapper
import com.example.citygo.skeleton.share.CommunityImage
import com.example.citygo.skeleton.share.ImageFit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotDetailScreen(spotId: String, onBack: () -> Unit, onViewArticles: (String) -> Unit,
    onNavigate: (String) -> Unit, recommendViewModel: RecommendViewModel,
    appViewModel: AppViewModel, onGoToPlan: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val account by AccountSession.currentAccount.collectAsStateWithLifecycle()
    LaunchedEffect(spotId, account?.token) { recommendViewModel.ensureSpotLoaded(spotId) }
    val spots by recommendViewModel.spotMap.collectAsStateWithLifecycle()
    val loads by recommendViewModel.detailLoads.collectAsStateWithLifecycle()
    val load = loads[spotId] ?: SpotDetailLoadState(loading = true)
    val spot = spots[spotId]?.takeUnless { load.loading || load.error != null }
    val favorites by appViewModel.collectSpotList.collectAsStateWithLifecycle()
    val plan by appViewModel.currentTravelPlan.collectAsStateWithLifecycle()
    val pendingDay by appViewModel.pendingAddDay.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(spot?.spotId) { spot?.let { appViewModel.addBrowseHistory(it.toSharedSpot()) } }
    Scaffold(modifier = modifier.fillMaxSize(), topBar = {
        TopAppBar(title = { Text(spot?.name ?: "景点详情") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        }, actions = {
            if (spot != null) {
                val collected = favorites.any { it.id == spot.spotId }
                IconButton(onClick = {
                    if (collected) appViewModel.removeCollectSpot(spot.toSharedSpot()) else appViewModel.addCollectSpot(spot.toSharedSpot())
                    Toast.makeText(context, if (collected) "已取消收藏" else "已收藏", Toast.LENGTH_SHORT).show()
                }) { Icon(if (collected) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, "收藏景点") }
            }
        })
    }, bottomBar = {
        if (spot != null) Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val added = plan.allPlaceList.any { it.name == spot.name && it.lat == spot.lat && it.lng == spot.lng }
                OutlinedButton(modifier = Modifier.weight(1f), enabled = spot.coordinateAvailable && !added, onClick = {
                    appViewModel.addSpotToPlan(spot.toSharedSpot())
                    Toast.makeText(context, pendingDay?.let { "已加入第${it}天行程" } ?: "已加入行程", Toast.LENGTH_SHORT).show()
                    onGoToPlan?.invoke()
                }) { Text(if (added) "已加入" else "加入行程") }
                Button(modifier = Modifier.weight(1f), enabled = spot.canNavigate, onClick = { onNavigate(spot.spotId) }) { Text("去导航") }
            }
        }
    }) { padding ->
        if (spot == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                if (load.loading) { CircularProgressIndicator(); Text("正在获取景点详情…") }
                else { Text(load.error ?: "景点暂不可查看", color = MaterialTheme.colorScheme.error); TextButton(onClick = { recommendViewModel.ensureSpotLoaded(spotId) }) { Text("重新加载") } }
            }
        } else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                if (spot.imgUrls.isEmpty()) Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { Text("暂无景点图片") }
                else {
                    val pager = rememberPagerState(pageCount = { spot.imgUrls.size })
                    HorizontalPager(pager, Modifier.fillMaxWidth().height(260.dp)) { index ->
                        AttractionImage(listOf(spot.imgUrls[index]), Modifier.fillMaxSize(), description = "${spot.name}配图${index + 1}")
                    }
                    Text("${pager.currentPage + 1}/${spot.imgUrls.size}", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            item { AttractionSection("基础信息") {
                Text(spot.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("城市：${spot.city.ifBlank { "暂缺" }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (spot.level.isNotBlank()) Text("景区等级：${spot.level}")
                Text("地址：${spot.address.ifBlank { "暂缺" }}")
                if (spot.phone.isNotBlank()) Text("联系电话：${spot.phone}")
                if (!spot.canNavigate) Text("暂缺可用导航坐标，仍可查看其他资料。", style = MaterialTheme.typography.bodySmall)
            } }
            item { AttractionSection("景点介绍") {
                if (spot.shortDesc.isNotBlank()) Text(spot.shortDesc, fontWeight = FontWeight.Medium)
                Text(spot.description.ifBlank { "暂无详细介绍" })
                if (spot.highlights.isNotBlank()) { Text("特色与看点", fontWeight = FontWeight.Medium); Text(spot.highlights) }
                Text("来源：${spot.descriptionSourceName ?: "待补充"}", style = MaterialTheme.typography.labelSmall)
                Text(spot.descriptionVerifiedAt?.let { "资料核对：${formatAttractionTime(it)}" } ?: "尚未资料核对", style = MaterialTheme.typography.labelSmall)
                AttractionSourceLink(spot.descriptionSourceUrl)
            } }
            listOf("TICKET" to "门票与优惠", "OPENING_HOURS" to "开放时间", "RESERVATION" to "预约信息", "PET" to "宠物政策").forEach { (type, title) ->
                item(key = type) { AttractionPolicyCard(title, type, spot.policies.firstOrNull { it.policyType == type }, spot.collectedInfo) }
            }
            item { AttractionSection("旅行者的灵感") {
                TextButton(onClick = { onViewArticles(spot.city) }) { Text("查看相关游记 →") }
            } }
            item { AttractionSection("游玩与交通建议") {
                Text("建议时长：${spot.duration.ifBlank { "暂无资料" }}")
                Text("建议季节：${spot.bestSeason.ifBlank { "暂无资料" }}")
                Text("交通建议：${spot.traffic.ifBlank { "暂无资料" }}")
                if (spot.pitTips.isNotEmpty()) { Text("注意事项", fontWeight = FontWeight.Medium); spot.pitTips.forEach { Text(it) } }
            } }
        }
    }
}

@Composable
private fun AttractionSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
@Composable
private fun AttractionPolicyCard(title: String, type: String, policy: AttractionPolicyDto?, collected: AttractionCollectedInfo?) {
    val value = AttractionDisplayMapper.policyValue(policy)
    val reference = when (type) {
        "TICKET" -> collected?.ticketPrice
        "OPENING_HOURS" -> collected?.openTime
        "PET" -> collected?.petPolicy
        else -> null
    }?.takeIf { it.isNotBlank() }
    AttractionSection(title) {
        Text(when (value) {
            "FREE" -> "免费"; "PAID" -> "收费"; "PARTIAL" -> "部分项目收费"
            "SCHEDULED" -> "已有开放时间安排"; "TEMPORARILY_CLOSED" -> "临时关闭"
            "REQUIRED" -> "必须预约"; "OPTIONAL" -> "可预约"; "NOT_REQUIRED" -> "无需预约"
            "ALLOWED" -> "允许携宠"; "CONDITIONAL" -> "有条件允许携宠"; "NOT_ALLOWED" -> "不允许携宠"
            else -> if (reference != null) "已收录参考资料" else "当前政策待核实"
        }, fontWeight = FontWeight.Medium)
        if (value != "UNKNOWN") Text(policy?.content ?: "请查看资料来源，确认适用范围和条件。")
        else if (reference != null) {
            Text(reference)
            Text("原表采集资料${collected?.recordedReviewTime?.let { " · $it" }.orEmpty()}，当前安排以景区公告为准。", style = MaterialTheme.typography.labelSmall)
        } else Text(when {
            policy?.temporalStatus == "EXPIRED" -> "已有资料已过有效期，请联系景区确认。"
            policy?.temporalStatus == "UPCOMING" -> "已有资料尚未生效，当前安排请联系景区确认。"
            policy?.reviewRequired == true -> "已有资料需要重新核实，请联系景区确认。"
            else -> "暂未核实，建议联系景区确认。"
        })
        if (type == "TICKET") collected?.discountPolicy?.takeIf { it.isNotBlank() }?.let {
            Text("原表优惠参考", fontWeight = FontWeight.Medium)
            Text(it)
            Text("采集时间：${collected.recordedReviewTime ?: "未记录"}", style = MaterialTheme.typography.labelSmall)
        }
        if (type == "PET" && collected != null) {
            collected.petFriendlyLevel?.takeIf { it.isNotBlank() }?.let { Text("采集友好度：$it") }
            collected.petFriendlinessScore?.takeIf { it.isNotBlank() }?.let { Text("采集评分：$it") }
            if (value != "UNKNOWN" && reference != null && reference != policy?.content) Text("原表说明：$reference")
            collected.petPolicySource?.takeIf { it.isNotBlank() }?.let { Text("宠物资料来源：$it", style = MaterialTheme.typography.labelSmall) }
            collected.recordedReviewTime?.let { Text("原表记录时间：$it", style = MaterialTheme.typography.labelSmall) }
        }
        if (value != "UNKNOWN" || reference == null) {
            Text("来源：${policy?.sourceName ?: "待补充"}", style = MaterialTheme.typography.labelSmall)
            Text(policy?.verifiedAt?.let { "资料核对：${formatAttractionTime(it)}" } ?: "尚未核对当前政策", style = MaterialTheme.typography.labelSmall)
        }
        policy?.effectiveFrom?.let { Text("生效时间：${formatAttractionTime(it)}", style = MaterialTheme.typography.labelSmall) }
        policy?.effectiveUntil?.let { Text("结束时间：${formatAttractionTime(it)}", style = MaterialTheme.typography.labelSmall) }
        AttractionSourceLink(policy?.sourceUrl)
    }
}
@Composable
private fun AttractionSourceLink(url: String?) {
    val handler = LocalUriHandler.current
    val context = LocalContext.current
    if (url != null && (url.startsWith("https://") || url.startsWith("http://"))) TextButton(onClick = {
        runCatching { handler.openUri(url) }.onFailure { Toast.makeText(context, "无法打开来源链接", Toast.LENGTH_SHORT).show() }
    }) { Text("查看资料来源") }
}
private fun formatAttractionTime(value: String): String = runCatching {
    val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.ROOT)
    val date = parser.parse(value) ?: return@runCatching value
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA).apply {
        timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
    }.format(date)
}.getOrDefault(value)
private fun ScenicSpot.toSharedSpot() = Spot(id = spotId, name = name, city = city, coverImg = coverImg,
    score = score, shortDesc = shortDesc, address = address, lat = lat, lng = lng, petFriendly = petPolicy == "ALLOWED")

