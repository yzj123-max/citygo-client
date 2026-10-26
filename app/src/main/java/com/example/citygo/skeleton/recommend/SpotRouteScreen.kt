package com.example.citygo.skeleton.recommend

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.citygo.skeleton.data.RecommendViewModel
import com.example.citygo.skeleton.data.datastore.AccountSession
import com.example.citygo.skeleton.data.remote.attraction.AttractionNavigationDto
import com.example.citygo.skeleton.screens.amap.RouteTravelMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

fun isAttractionFixFresh(fix: AttractionLocationFix?, now: Long): Boolean =
    fix != null && now - fix.receivedAt in 0L..30_000L &&
        fix.latitude.isFinite() && fix.longitude.isFinite() &&
        fix.latitude in -90.0..90.0 && fix.longitude in -180.0..180.0 &&
        !(fix.latitude == 0.0 && fix.longitude == 0.0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotRouteScreen(
    spotId: String,
    onBack: () -> Unit,
    onStartNavigation: (RouteTravelMode, Double, Double, Double, Double, String) -> Unit,
    recommendViewModel: RecommendViewModel,
    modifier: Modifier = Modifier,
) {
    val account by AccountSession.currentAccount.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(RouteTravelMode.WALK) }
    var target by remember(spotId, account?.token) { mutableStateOf<AttractionNavigationDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    var fix by remember(spotId, account?.token) { mutableStateOf<AttractionLocationFix?>(null) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while (true) { now = SystemClock.elapsedRealtime(); delay(1000) } }
    LaunchedEffect(spotId, mode, retry, account?.token) {
        target = null
        loading = true
        error = null
        try {
            target = recommendViewModel.navigationTarget(spotId, if (mode == RouteTravelMode.WALK) "WALKING" else "DRIVING")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "目的地加载失败，请重试" }
        finally { loading = false }
    }
    Scaffold(modifier = modifier, topBar = {
        TopAppBar(title = { Text("从当前位置出发") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(selected = mode == RouteTravelMode.WALK, onClick = { mode = RouteTravelMode.WALK }, label = { Text("步行") })
                FilterChip(selected = mode == RouteTravelMode.DRIVE, onClick = { mode = RouteTravelMode.DRIVE }, label = { Text("驾车") })
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { retry++ }) { Text("重试目的地") } }
            target?.let { destination ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(destination.destinationName ?: destination.attractionName.orEmpty(), style = MaterialTheme.typography.titleLarge)
                        destination.address?.takeIf { it.isNotBlank() }?.let { Text(it) }
                        destination.notice?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                        if (destination.fallback) Text("当前使用景点位置，请留意实际游客入口。")
                        destination.sourceName?.let { Text("目的地来源：$it", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
            Text("出发位置", style = MaterialTheme.typography.titleMedium)
            AttractionLocationControl(autoStart = true) { fix = it; now = SystemClock.elapsedRealtime() }
            if (fix != null && !isAttractionFixFresh(fix, now)) Text("位置已过期，请点击重新定位。", color = MaterialTheme.colorScheme.error)
            Text("下一步查看路线，开始导航后将按手机实时位置引导。", style = MaterialTheme.typography.bodyMedium)
            Button(modifier = Modifier.fillMaxWidth(), enabled = !loading && target != null && isAttractionFixFresh(fix, now), onClick = {
                val origin = fix
                val destination = target
                if (destination != null && origin != null && isAttractionFixFresh(origin, SystemClock.elapsedRealtime())) {
                    onStartNavigation(mode, origin.latitude, origin.longitude, destination.latitude!!, destination.longitude!!,
                        destination.destinationName ?: destination.attractionName.orEmpty())
                } else { now = SystemClock.elapsedRealtime() }
            }) { Text("查看路线并导航") }
        }
    }
}
