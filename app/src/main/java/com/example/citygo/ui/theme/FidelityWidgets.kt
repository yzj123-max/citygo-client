package com.example.citygo.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.example.citygo.skeleton.screens.amap.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.citygo.skeleton.data.model.Place
import com.example.citygo.skeleton.data.model.ScenicSpot
import com.example.citygo.skeleton.recommend.AttractionImage

@Composable
fun FidelityDiscovery(city: String, photo: String?, petFriendly: Boolean,
    onPetChange: (Boolean) -> Unit, onCity: () -> Unit, weather: String,
    onWeather: () -> Unit, onPlan: () -> Unit, onOutfit: () -> Unit,
    onNearby: () -> Unit, currentTrip: String?, onViewPlan: () -> Unit,
    photoLabel: String? = null, heroHeight: androidx.compose.ui.unit.Dp = 390.dp) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // The city image owns half the discovery viewport; the compact planner overlaps its lower edge.
        Column(verticalArrangement = Arrangement.spacedBy((-28).dp)) {
            Box(Modifier.fillMaxWidth().height(heroHeight)) {
                CityLandscape(Modifier.matchParentSize())
                if (!photo.isNullOrBlank()) AsyncImage(photo, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                    0f to MaterialTheme.colorScheme.background.copy(alpha = .98f),
                    .20f to MaterialTheme.colorScheme.background.copy(alpha = .68f),
                    .40f to MaterialTheme.colorScheme.background.copy(alpha = .08f),
                    .73f to Color.Black.copy(alpha = .08f),
                    1f to Color.Black.copy(alpha = .64f))))
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("CityGo", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        TextButton(onClick = onCity, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Icon(Icons.Outlined.LocationOn, null, Modifier.size(14.dp))
                            Text(if (city == "全部城市") "全国" else city, fontSize = 12.sp)
                            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onWeather, contentPadding = PaddingValues(0.dp)) {
                            Icon(Icons.Outlined.WbSunny, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(4.dp)); Text(weather, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("下一站，\n按你的方式出发", fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
                    Text("AI 让每一次出发，都更合你心意", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
                Text("把想去的地方，\n变成一段好行程", fontSize = 23.sp, lineHeight = 30.sp,
                    fontWeight = FontWeight.Medium, color = Color.White,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 60.dp))
                if (!photoLabel.isNullOrBlank()) Surface(color = Color.Black.copy(alpha = .32f),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = 38.dp).widthIn(max = 150.dp)) {
                    Text(photoLabel, fontSize = 9.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp), shadowElevation = 1.dp) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).fillMaxHeight().clickable(onClick = onCity), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.LocationOn, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(5.dp))
                            Text(if (city == "全部城市") "选择目的地 · 开始一段旅行"
                                else currentTrip?.takeIf { it.startsWith("$city · ") } ?: city,
                                fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.Outlined.ExpandMore, "选择城市", Modifier.size(16.dp))
                        }
                        if (currentTrip != null) Text("查看行程 ›", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(onClick = onViewPlan).padding(horizontal = 6.dp, vertical = 8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(modifier = Modifier.weight(1f).clickable(onClick = onPlan),
                            color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Spa, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(5.dp)); Text("按偏好规划", fontSize = 11.sp)
                            }
                        }
                        Surface(modifier = Modifier.weight(1f).clickable { onPetChange(!petFriendly) },
                            color = if (petFriendly) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp)) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Pets, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(5.dp)); Text("携宠同行", fontSize = 11.sp)
                                if (petFriendly) { Spacer(Modifier.weight(1f)); Icon(Icons.Outlined.Check, null, Modifier.size(13.dp)) }
                            }
                        }
                    }
                    Button(onClick = onPlan, modifier = Modifier.fillMaxWidth().height(42.dp), shape = RoundedCornerShape(10.dp)) {
                        Text("AI 规划我的旅行", fontSize = 13.sp); Spacer(Modifier.width(10.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(17.dp))
                    }
                }
            }
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DiscoveryToolCard("AI 穿搭", "出游怎么穿更合适", Icons.Outlined.Checkroom, onOutfit, Modifier.weight(1f))
            DiscoveryToolCard("附近灵感", "发现周边好去处", Icons.Outlined.LocationOn, onNearby, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DiscoveryToolCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 56.dp), color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, fontSize = 9.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(icon, null, Modifier.size(25.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun DiscoverySpotTile(spot: ScenicSpot, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.aspectRatio(1.30f), shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant) {
        Box {
            AttractionImage((listOf(spot.coverImg) + spot.imgUrls).filter { it.isNotBlank() }.distinct(),
                Modifier.matchParentSize(), description = spot.name)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                0f to Color.Transparent, .38f to Color.Transparent, 1f to Color.Black.copy(alpha = .8f))))
            Column(Modifier.align(Alignment.BottomStart).padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(spot.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(spot.shortDesc.ifBlank { spot.address }, fontSize = 9.sp, color = Color.White.copy(alpha = .86f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (spot.petPolicy == "ALLOWED" || spot.petPolicy == "CONDITIONAL") Surface(
                shape = RoundedCornerShape(6.dp), color = Color.Black.copy(alpha = .4f),
                modifier = Modifier.align(Alignment.TopStart).padding(7.dp)) {
                Text(if (spot.petPolicy == "ALLOWED") "允许携宠 · 已核实" else "有条件携宠", fontSize = 9.sp,
                    color = Color.White, modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp))
            }
        }
    }
}

/** Read-only geographic overview; the full map owns search, selection and navigation. */
@Composable
fun PlanMiniRoute(points: List<Place>, onMap: () -> Unit) {
    val valid = remember(points) { points.filter { !it.mockedCoord && it.lat.isFinite() && it.lng.isFinite() } }
    val context = LocalContext.current
    val mapState = remember { AmapMapState() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(valid, mapState.mapLoaded, size) {
        if (mapState.mapLoaded) mapState.renderItineraryPreview(valid, size.width, size.height)
    }
    Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant).onSizeChanged { size = it }) {
        if (valid.isNotEmpty() && isArmAbiSupported(context)) {
            AmapMapBox(mapState, Modifier.matchParentSize())
        }
        if (valid.isEmpty() || mapState.initFailed || !isArmAbiSupported(context)) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Map, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                Text(if (valid.isEmpty()) "地点位置待完善" else "打开地图查看行程", style = MaterialTheme.typography.bodySmall)
            }
        }
        // Overlay owns taps so this compact preview never consumes vertical list gestures.
        Box(Modifier.matchParentSize().clickable(onClick = onMap))
        Surface(onClick = onMap, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
            Text("地图查看 ›", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
        }
    }
}
