package com.example.citygo.skeleton.screens.stylist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
            Surface(shape = MaterialTheme.shapes.large) { CityLandscape(Modifier.fillMaxWidth().height(150.dp)) }
            TravelToolCard("单张穿搭分析", "拍照或选择一张照片，查看搭配建议", Icons.Outlined.Checkroom, onSingle)
            TravelToolCard("结合行程的多图评估", "最多 9 张照片，结合当前行程综合评估", Icons.Outlined.Collections, onMulti)
        }
    }
}
