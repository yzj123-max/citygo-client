package com.example.citygo.skeleton.recommend

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import java.net.URI

/** 高德旧图片优先使用同地址HTTPS；签名地址保持原样，不改共享图片客户端。 */
fun attractionImageCandidates(urls: List<String>): List<String> = urls.filter(String::isNotBlank).flatMap { raw ->
    val uri = runCatching { URI(raw) }.getOrNull()
    if (uri?.scheme == "http" && uri.host in setOf("store.is.autonavi.com", "aos-cdn-image.amap.com"))
        listOf("https://" + raw.removePrefix("http://"), raw) else listOf(raw)
}.distinct()

@Composable
fun AttractionImage(urls: List<String>, modifier: Modifier = Modifier, description: String? = null) {
    val candidates = remember(urls) { attractionImageCandidates(urls) }
    var index by remember(candidates) { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            candidates.isEmpty() -> Text("暂无景点配图", style = MaterialTheme.typography.labelSmall)
            index >= candidates.size -> Text("图片暂不可用 · 点击重试", Modifier.clickable { index = 0; retry++ }, style = MaterialTheme.typography.labelSmall)
            else -> key(candidates[index], retry) {
                SubcomposeAsyncImage(model = candidates[index], contentDescription = description,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
                    error = { LaunchedEffect(candidates[index]) { index++ } })
            }
        }
    }
}
