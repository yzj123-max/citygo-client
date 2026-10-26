package com.example.citygo.skeleton.recommend

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.citygo.skeleton.screens.amap.*

data class AttractionLocationFix(val latitude: Double, val longitude: Double, val receivedAt: Long,
    val accuracyMeters: Float?)

@Composable
fun AttractionLocationControl(autoStart: Boolean = false, onLocation: (AttractionLocationFix) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val locator = remember { AmapLocator(context, allowMockLocations = false, useCachedLocation = false) }
    val callback by rememberUpdatedState(onLocation)
    var requested by remember { mutableStateOf(false) }
    var delivered by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    val requestFresh = {
        requested = true
        delivered = false
        denied = false
        locator.restartFresh()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasPreciseLocationPermission(context)) requestFresh() else denied = true
    }
    fun locate() {
        if (hasPreciseLocationPermission(context)) requestFresh()
        else permission.launch(navigationLocationPermissions())
    }
    LaunchedEffect(Unit) { if (autoStart) locate() }
    DisposableEffect(owner, locator) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) locator.stop()
            if (event == Lifecycle.Event.ON_START && requested && hasPreciseLocationPermission(context)) requestFresh()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); locator.destroy() }
    }
    LaunchedEffect(locator.currentLocation, delivered) {
        val location = locator.currentLocation
        if (location != null && !delivered) {
            delivered = true
            callback(AttractionLocationFix(location.latitude, location.longitude, SystemClock.elapsedRealtime(), locator.accuracyMeters))
            locator.stop()
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row {
            TextButton(onClick = { locate() }, enabled = !locator.locating) {
                Text(if (locator.locating) "正在定位…" else if (delivered) "重新定位" else "获取当前位置")
            }
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text("定位设置") }
        }
        if (locator.locating) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (denied) Text("请允许精确位置权限；没有定位也可以按城市和名称搜索。", color = MaterialTheme.colorScheme.error)
        locator.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (delivered) Text("已获取本次位置${locator.accuracyMeters?.let { "，精度约${it.toInt()}米" }.orEmpty()}", style = MaterialTheme.typography.labelSmall)
    }
}
