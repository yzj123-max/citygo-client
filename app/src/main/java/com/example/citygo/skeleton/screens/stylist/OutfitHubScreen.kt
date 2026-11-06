package com.example.citygo.skeleton.screens.stylist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.citygo.R
import androidx.compose.ui.unit.dp
import com.example.citygo.ui.theme.CityLandscape
import com.example.citygo.ui.theme.TravelToolCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitHubScreen(onBack: () -> Unit, onSingle: () -> Unit, onMulti: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("AI 穿搭") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("去一座城，\n穿上喜欢的自己", style = MaterialTheme.typography.headlineMedium)
            Text("从日常搭配到旅行场景，找到适合你的出游穿搭。",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("AI 穿搭助手", style = MaterialTheme.typography.titleLarge)
            Text("从照片里的搭配细节，到旅行中的舒适与实用", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutfitEntryCard("单张穿搭分析", "上传一张穿搭照片，获取风格、颜色与搭配建议。",
                listOf(R.drawable.ui_outfit_sample), onSingle)
            OutfitEntryCard("结合行程的多图评估", "上传多张穿搭照片，结合当前旅行安排，获取出游建议。",
                listOf(R.drawable.ui_outfit_sample), onMulti)
            Text("配图为穿搭灵感示例", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OutfitEntryCard(title: String, detail: String, images: List<Int>, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().heightIn(min = 150.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.width(112.dp).height(150.dp).clip(RoundedCornerShape(18.dp))) {
                images.forEach { asset ->
                    Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp))) {
                        Image(painterResource(asset), null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = 1.14f, scaleY = 1.14f))
                    }
                }
            }
            Column(Modifier.weight(1f).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (title == "单张穿搭分析") Icons.Outlined.Checkroom else Icons.Outlined.Collections, null,
                    tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", modifier = Modifier.padding(end = 12.dp), color = MaterialTheme.colorScheme.primary)
        }
    }
}
