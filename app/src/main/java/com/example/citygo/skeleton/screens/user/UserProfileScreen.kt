package com.example.citygo.skeleton.screens.user

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.citygo.R
import com.example.citygo.skeleton.data.datastore.AccountEntity

/* =====================================================================================
 * 用户资料主页（本轮新增，参考小红书个人主页风格）
 *
 * - 顶部 240dp 背景区：bgImageUrl 有值显示网络图片（随页面一起滚动），
 *   无值显示品牌渐变底色；
 * - 底部叠放圆形头像（点击直接唤起相册 → 单独调 /user/uploadAvatar）、昵称、手机号；
 * - 下方卡片：个人简介 + 性别 / 生日 / 地区 / 职业 / 学校，全部做空值占位；
 * - 【编辑资料】按钮跳编辑页；
 * - 进入页面自动 GET /user/info，成功回写 DataStore + AccountSession，全局自动刷新。
 * ===================================================================================== */

/** 资料模块路由（常量集中在此，导航层直接引用，避免改动既有路由文件） */
object UserProfileRoutes {
    const val USER_PROFILE = "profile/user_profile"
    const val EDIT_PROFILE = "profile/edit_profile"
}

/** 背景默认渐变（与「我的」页头部品牌色一致） */
private val BgFallbackGradient = Brush.verticalGradient(
    listOf(Color(0xFF184735), Color(0xFF205B46), Color(0xFF073B39)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    viewModel: UserProfileViewModel = viewModel(),
) {
    val context = LocalContext.current
    val account by viewModel.currentAccount.collectAsStateWithLifecycle()
    val loading = viewModel.loading
    val uploadingAvatar = viewModel.uploadingAvatar

    // 一次性 Toast 事件
    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    // 进入页面自动拉取最新资料（merge 成功后本页 / 主页 / 编辑页全部自动刷新）
    LaunchedEffect(Unit) { viewModel.refresh() }

    // 头像选择：相册（invoke 时系统选择器内部自带权限申请，无需额外权限）
    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.uploadAvatar(uri)
    }

    Box(modifier = Modifier.fillMaxSize().background(UserProfileBg)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.user_profile_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.auth_back),
                                tint = Color.White,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    modifier = Modifier.statusBarsPadding(),
                )
            },
        ) { innerPadding ->
            ProfileContent(
                account = account,
                modifier = Modifier.padding(innerPadding),
                onAvatarClick = {
                    avatarPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onEdit = onEdit,
            )
        }

        if (loading) LoadingOverlay(stringResource(R.string.user_profile_loading))
        if (uploadingAvatar) LoadingOverlay(stringResource(R.string.user_profile_avatar_uploading))
    }
}

@Composable
private fun ProfileContent(
    account: AccountEntity?,
    modifier: Modifier,
    onAvatarClick: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // ==================== 顶部背景 + 头像 / 昵称（随页面滚动） ====================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
        ) {
            // 背景图：有值网络图，无值渐变底
            val bgUrl = account?.bgImageUrl?.takeIf { it.isNotBlank() }
            if (bgUrl != null) {
                AsyncImage(
                    model = bgUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(BgFallbackGradient))
            }
            // 底部压暗渐变，保证白字可读
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.05f),
                            0.55f to Color.Black.copy(alpha = 0.15f),
                            1f to Color.Black.copy(alpha = 0.62f),
                        ),
                    ),
            )

            // 头像 + 昵称 / 手机号 叠在背景底部
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                AvatarWithCamera(
                    avatarUrl = account?.avatarUrl,
                    size = 80.dp,
                    onClick = onAvatarClick,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = account?.nickname?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.user_profile_nickname_placeholder),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = account?.phone?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.user_profile_phone_placeholder),
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }

        // ==================== 下方资料区 ====================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 编辑资料按钮
            Button(
                onClick = onEdit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(23.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = UserProfileAccentRed,
                    contentColor = Color.White,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = stringResource(R.string.user_profile_edit_button),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            // 个人简介卡片
            SurfaceCard {
                Text(
                    text = stringResource(R.string.user_profile_section_desc),
                    fontSize = 13.sp,
                    color = UserProfileTextSecondary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                )
                Text(
                    text = account?.desc?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.user_profile_desc_placeholder),
                    fontSize = 15.sp,
                    color = if (account?.desc.isNullOrBlank()) {
                        UserProfileTextSecondary
                    } else {
                        UserProfileTextPrimary
                    },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
                )
            }

            // 基础信息卡片
            SurfaceCard {
                InfoLine(stringResource(R.string.user_profile_field_gender), genderText(account?.gender))
                LineDivider()
                InfoLine(stringResource(R.string.user_profile_field_birthday), account?.birthday)
                LineDivider()
                InfoLine(stringResource(R.string.user_profile_field_region), account?.region)
                LineDivider()
                InfoLine(stringResource(R.string.user_profile_field_job), account?.job)
                LineDivider()
                InfoLine(stringResource(R.string.user_profile_field_school), account?.school)
            }

            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

/** 圆形头像 + 右下角相机角标；无头像时灰底人像占位（http 图片由 Coil 加载） */
@Composable
private fun AvatarWithCamera(
    avatarUrl: String?,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size + 8.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFF3A3A3C))
                .border(width = 2.dp, color = Color.White.copy(alpha = 0.85f), shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val url = avatarUrl?.takeIf { it.isNotBlank() }
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = stringResource(R.string.user_profile_avatar),
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = UserProfileTextSecondary,
                    modifier = Modifier.size(size * 0.5f),
                )
            }
        }
        // 相机角标
        Box(
            modifier = Modifier
                .size(26.dp)
                .offset(x = 2.dp, y = 2.dp)
                .clip(CircleShape)
                .background(UserProfileAccentRed)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoCamera,
                contentDescription = stringResource(R.string.user_profile_avatar_change),
                tint = Color.White,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun SurfaceCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = UserProfileCardBg,
    ) {
        Column(content = content)
    }
}

@Composable
private fun InfoLine(label: String, value: String?) {
    ProfileInfoRow(label = label, value = value)
}

@Composable
private fun LineDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 100.dp),
        thickness = 0.5.dp,
        color = UserProfileDivider,
    )
}
