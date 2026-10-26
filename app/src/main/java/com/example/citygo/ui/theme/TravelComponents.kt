package com.example.citygo.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

/** Decorative local artwork: works offline and does not imply live destination data. */
@Composable
fun CityLandscape(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.primary
    val mist = MaterialTheme.colorScheme.primaryContainer
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(mist, ink.copy(alpha = .12f))))
        val hills = Path().apply {
            moveTo(0f, size.height * .67f)
            cubicTo(size.width * .22f, size.height * .12f, size.width * .3f, size.height * .96f, size.width * .61f, size.height * .42f)
            cubicTo(size.width * .8f, size.height * .12f, size.width * .92f, size.height * .47f, size.width, size.height * .34f)
            lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        drawPath(hills, ink.copy(alpha = .12f))
        val widths = listOf(.06f, .1f, .055f, .09f, .045f, .08f, .07f)
        widths.forEachIndexed { i, w ->
            val x = size.width * (.43f + i * .075f)
            val h = size.height * (.22f + (i % 3) * .12f)
            drawRoundRect(ink.copy(alpha = .15f), Offset(x, size.height * .84f - h), Size(size.width * w, h))
        }
        val trail = Path().apply {
            moveTo(size.width * .12f, size.height * .92f)
            cubicTo(size.width * .75f, size.height * .68f, size.width * .18f, size.height * .6f, size.width * .9f, size.height * .4f)
        }
        drawPath(trail, ink.copy(alpha = .35f), style = Stroke(2.dp.toPx()))
        drawCircle(ink, 4.dp.toPx(), Offset(size.width * .9f, size.height * .4f))
    }
}

@Composable
fun DiscoveryIntro(city: String, petFriendly: Boolean, onPetFriendlyChange: (Boolean) -> Unit,
    onPlan: () -> Unit, onOutfit: () -> Unit, onNearby: () -> Unit, heroPhoto: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("CityGo", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            Text("城市漫游 · 按你的方式", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))) {
            CityLandscape(Modifier.matchParentSize())
            if (!heroPhoto.isNullOrBlank()) {
                AsyncImage(model = heroPhoto, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize())
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
                    MaterialTheme.colorScheme.background.copy(alpha = .94f),
                    MaterialTheme.colorScheme.background.copy(alpha = .38f),
                    MaterialTheme.colorScheme.background.copy(alpha = .9f)))))
            }
            Column(Modifier.padding(22.dp)) {
                Text("下一站，\n按你的方式出发", style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(12.dp))
                Text("AI 行程 · 携宠出游 · 穿搭灵感", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(30.dp))
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .94f)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(if (city == "全部城市") "选择一座城市，开始新的旅程" else "$city · 让灵感成为行程",
                            style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("优先宠物友好地点", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Switch(checked = petFriendly, onCheckedChange = onPetFriendlyChange)
                        }
                        Button(onClick = onPlan, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp)); Text("AI 规划我的旅行")
                            Spacer(Modifier.weight(1f)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TravelToolCard("AI 穿搭", "照片分析与行程评估", Icons.Outlined.Checkroom, onOutfit, Modifier.weight(1f))
            TravelToolCard("附近灵感", "发现身边的好去处", Icons.Outlined.Explore, onNearby, Modifier.weight(1f))
        }
    }
}

@Composable
fun TravelToolCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit,
    modifier: Modifier = Modifier) {
    Surface(modifier = modifier.clickable(onClick = onClick), color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp), tonalElevation = 0.dp) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(icon, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}
