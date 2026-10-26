package com.example.citygo.skeleton.screens

import coil.compose.AsyncImage
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.citygo.R
import com.example.citygo.skeleton.data.model.Diary
import com.example.citygo.skeleton.data.model.Pet
import com.example.citygo.skeleton.profile.AppViewModel
import com.example.citygo.skeleton.profile.DiaryEditDialog
import com.example.citygo.skeleton.profile.MOCK_LOGIN_USER
import com.example.citygo.skeleton.profile.PetEditDialog
import com.example.citygo.skeleton.data.AppViewModel as GlobalAppViewModel

/* =====================================================================================
 * 我的 Tab（个人中心）
 *
 * - 用户信息区：头像 + 昵称，游客模式显示「游客用户」；
 *   （全局城市选择入口已迁移至「计划」页首页的「第 1 步 · 选择目的地」）
 * - 列表模块：我的收藏攻略 / 我的收藏帖子 / 浏览记录（游客模式隐藏收藏入口）；
 * - 宠物档案：支持新增 / 编辑 / 删除多条宠物（名字、品种、体重），由全局 AppViewModel 持久化到 Room；
 * - 设置：深色主题开关（全局生效）、隐私设置、切换账号、清除缓存（Mock）、关于我们；
 * - 收藏 / 历史 / 隐私 / 关于均通过回调跳转到对应二级路由页。
 * ===================================================================================== */

/** 头部海岛青渐变（固定品牌视觉，不受深浅主题影响） */
private val HeaderGradient = listOf(0xFF184735, 0xFF205B46, 0xFFACC6AC)

@Composable
fun ProfileScreen(
    appViewModel: AppViewModel,
    // 全局 VM（Repository + Room）：宠物档案的增删改查与持久化由此驱动
    globalAppViewModel: GlobalAppViewModel,
    onOpenFavoriteGuides: () -> Unit,
    onOpenFavoritePosts: () -> Unit,
    onOpenBrowseHistory: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
    // 通用设置页（参考小红书风格：切换账号 / 退出登录固定在页面底部）；null 时不显示入口
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    // 深色开关的持久化值 / 回调：由全局 VM（DataStore）注入
    darkModeChecked: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    // 城市选择后同步持久化到 DataStore（新全局 VM），null 时只走旧 VM 内存态
    onPersistCity: ((String) -> Unit)? = null,
    // JWT 账号中心（登录 / 注册 / 个人信息）入口：由导航层注入；null 时弹窗不显示该入口
    onOpenAccountCenter: (() -> Unit)? = null,
    // 点击头部昵称进入【用户资料主页】（本轮新增，可空，不影响既有调用方）
    onOpenUserProfile: (() -> Unit)? = null,
    // 我的页最底部【切换账号】（弹窗）/【退出登录】入口（本轮新增，仅登录态展示）
    onOpenSwitchAccount: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null,
    // 多账号体系下当前活跃账号（DataStore 持久化）：切换账号后头部昵称随之刷新；null 时沿用 Mock 数据
    activeAccount: com.example.citygo.skeleton.data.datastore.AccountEntity? = null,
) {
    val context = LocalContext.current
    val state by appViewModel.uiState.collectAsStateWithLifecycle()
    // 宠物档案来自全局 VM（Room 持久化，重启后自动回填）
    val petList by globalAppViewModel.petList.collectAsStateWithLifecycle()
    // 收藏景点 / 收藏帖子同样来自全局 VM（Room 持久化），头部数字与菜单角标实时同步
    val collectSpotList by globalAppViewModel.collectSpotList.collectAsStateWithLifecycle()
    val collectPostList by globalAppViewModel.collectPostList.collectAsStateWithLifecycle()
    // 浏览记录数同样取全局 Room 列表
    val browseHistoryList by globalAppViewModel.browseHistoryList.collectAsStateWithLifecycle()
    // 旅行日记列表（Room 持久化，最新保存在前）
    val diaryList by globalAppViewModel.diaryList.collectAsStateWithLifecycle()

    // 弹窗状态：宠物新增 / 编辑（null 表示未打开；编辑时为待修改对象，新增时用空标记）
    var petDialogTarget by remember { mutableStateOf<Pet?>(null) }
    var showPetAddDialog by remember { mutableStateOf(false) }
    // 待删除确认的宠物
    var pendingDeletePet by remember { mutableStateOf<Pet?>(null) }
    // 弹窗状态：日记新增 / 编辑
    var diaryDialogTarget by remember { mutableStateOf<Diary?>(null) }
    var showDiaryAddDialog by remember { mutableStateOf(false) }
    var showAllPets by remember { mutableStateOf(false) }
    var showAllDiaries by remember { mutableStateOf(false) }
    // 待删除确认的日记
    var pendingDeleteDiary by remember { mutableStateOf<Diary?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // ==================== 顶部用户信息区 ====================
        ProfileHeader(
            isGuest = state.isGuest,
            // 多账号体系下以当前活跃 JWT 账号为准（切换账号后此处立即重组刷新）；
            // nickname 可空（后端资料字段未就绪），空值用「用户 + 手机尾号」占位
            nickname = activeAccount?.nickname?.takeIf { it.isNotBlank() }
                ?: activeAccount?.phone?.let { "用户${it.takeLast(4)}" }
                ?: state.user.nickname,
            phone = activeAccount?.phone,
            avatarUrl = activeAccount?.avatarUrl,
            avatarEmoji = state.user.avatarEmoji,
            currentCityName = state.currentCity.name,
            visitedCityCount = state.recentCities.size,
            guideCount = collectSpotList.size,
            postCount = collectPostList.size,
            historyCount = browseHistoryList.size,
            // 城市选择入口已迁移至「计划」页首页（规划第一步），「我的」页不再提供该入口
            citySelector = {},
            // 点击昵称进入用户资料主页（游客 / 未注入时不可点）
            onOpenUserProfile = onOpenUserProfile,
        )

        Spacer(Modifier.height(14.dp))

        // ==================== 列表模块：收藏 / 历史 ====================
        SectionContainer(title = "我的旅行") {
            // 游客模式隐藏两个收藏入口，仅保留浏览记录
            if (!state.isGuest) {
                MenuItem(
                    icon = Icons.Filled.MenuBook,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "我的收藏攻略",
                    trailingText = "${collectSpotList.size} 篇",
                    onClick = onOpenFavoriteGuides,
                )
                ItemDivider()
                MenuItem(
                    icon = Icons.Filled.Favorite,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "我的收藏帖子",
                    trailingText = "${collectPostList.size} 条",
                    onClick = onOpenFavoritePosts,
                )
                ItemDivider()
            }
            MenuItem(
                icon = Icons.Filled.History,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "浏览记录",
                trailingText = "${browseHistoryList.size} 条",
                onClick = onOpenBrowseHistory,
            )
            if (state.isGuest) {
                Text(
                    text = "游客模式下收藏入口已隐藏，切换账号登录后可见",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 12.dp),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ==================== 宠物档案模块 ====================
        SectionContainer(
            title = "宠物档案",
            headerAction = {
                // 新增宠物（打开新增弹窗前先清掉可能残留的编辑目标，保证同一时刻只有一个弹窗）
                TextButton(onClick = {
                    petDialogTarget = null
                    showPetAddDialog = true
                }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("添加宠物")
                }
            },
        ) {
            if (petList.isEmpty()) {
                // 空状态：整行可点，直接拉起新增弹窗
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPetAddDialog = true }
                        .padding(horizontal = 14.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Pets, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "添加你的宠物",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "建立名字、品种、体重档案，出行携宠更省心",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                (if (showAllPets) petList else petList.take(1)).forEachIndexed { index, pet ->
                    PetItem(
                        pet = pet,
                        onEdit = {
                            showPetAddDialog = false
                            petDialogTarget = pet
                        },
                        onDelete = { pendingDeletePet = pet },
                    )
                    if (showAllPets && index != petList.lastIndex) ItemDivider(startIndent = 62.dp)
                }
                if (petList.size > 1) TextButton(onClick = { showAllPets = !showAllPets }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showAllPets) "收起档案" else "查看全部 ${petList.size} 个宠物档案")
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ==================== 旅行日记模块 ====================
        SectionContainer(
            title = "旅行日记",
            headerAction = {
                // 写日记（新增；打开前先清掉可能残留的编辑目标，保证同一时刻只有一个弹窗）
                TextButton(onClick = {
                    diaryDialogTarget = null
                    showDiaryAddDialog = true
                }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("写日记")
                }
            },
        ) {
            if (diaryList.isEmpty()) {
                // 空状态：整行可点，直接拉起编辑弹窗
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDiaryAddDialog = true }
                        .padding(horizontal = 14.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "写下第一篇旅行日记",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "记录路上的风景与心情",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (if (showAllDiaries) diaryList else diaryList.take(1)).forEach { diary ->
                        DiaryItem(
                            diary = diary,
                            onEdit = {
                                showDiaryAddDialog = false
                                diaryDialogTarget = diary
                            },
                            onDelete = { pendingDeleteDiary = diary },
                        )
                    }
                    if (diaryList.size > 1) TextButton(onClick = { showAllDiaries = !showAllDiaries }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (showAllDiaries) "收起日记" else "查看全部 ${diaryList.size} 篇日记")
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ==================== 设置列表 ====================
        SectionContainer(title = "设置与帮助") {
            MenuItem(icon = Icons.Filled.Settings, iconTint = MaterialTheme.colorScheme.primary,
                title = "设置", trailingText = "外观 · 隐私 · 账号",
                onClick = { onOpenSettings?.invoke() }, isLast = true)
        }

        // ==================== 底部：切换账号 / 退出登录 ====================
        // 本轮按需求放在「我的」页最底部（登录态才展示；游客仅保留登录引导）
        if (onOpenSettings == null && !state.isGuest && (onOpenSwitchAccount != null || onLogout != null)) {
            var showLogoutConfirm by remember { mutableStateOf(false) }
            AccountActionsCard(
                onSwitchAccount = onOpenSwitchAccount,
                onLogout = { showLogoutConfirm = true },
            )
            if (showLogoutConfirm) {
                AlertDialog(
                    onDismissRequest = { showLogoutConfirm = false },
                    title = { Text(stringResource(R.string.settings_logout)) },
                    text = { Text(stringResource(R.string.settings_logout_confirm_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showLogoutConfirm = false
                            onLogout?.invoke()
                        }) {
                            Text(stringResource(R.string.settings_logout), color = Color(0xFFFF3B47))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLogoutConfirm = false }) {
                            Text(stringResource(R.string.switch_account_delete_cancel))
                        }
                    },
                )
            }
        }

        // 底部安全区留白（避让手势导航条）
        Spacer(Modifier.height(24.dp))
    }

    /* ---------------------------------- 弹窗 ---------------------------------- */

    // 新增宠物
    if (showPetAddDialog) {
        PetEditDialog(
            initial = null,
            onDismiss = { showPetAddDialog = false },
            onSave = { pet ->
                globalAppViewModel.addPet(pet)
                showPetAddDialog = false
                Toast.makeText(context, "已添加宠物「${pet.name}」", Toast.LENGTH_SHORT).show()
            },
        )
    }

    // 编辑宠物
    petDialogTarget?.let { target ->
        PetEditDialog(
            initial = target,
            onDismiss = { petDialogTarget = null },
            onSave = { pet ->
                globalAppViewModel.updatePet(pet)
                petDialogTarget = null
                Toast.makeText(context, "宠物「${pet.name}」资料已更新", Toast.LENGTH_SHORT).show()
            },
        )
    }

    // 删除宠物确认
    pendingDeletePet?.let { pet ->
        AlertDialog(
            onDismissRequest = { pendingDeletePet = null },
            title = { Text("删除宠物档案") },
            text = { Text("确定删除「${pet.name}」的档案吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    globalAppViewModel.deletePet(pet.id)
                    pendingDeletePet = null
                    Toast.makeText(context, "已删除「${pet.name}」", Toast.LENGTH_SHORT).show()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePet = null }) { Text("取消") }
            },
        )
    }

    // 新增日记
    if (showDiaryAddDialog) {
        DiaryEditDialog(
            initial = null,
            onDismiss = { showDiaryAddDialog = false },
            onSave = { diary ->
                globalAppViewModel.saveDiary(diary)
                showDiaryAddDialog = false
                Toast.makeText(context, "日记已保存", Toast.LENGTH_SHORT).show()
            },
        )
    }

    // 编辑日记
    diaryDialogTarget?.let { target ->
        DiaryEditDialog(
            initial = target,
            onDismiss = { diaryDialogTarget = null },
            onSave = { diary ->
                globalAppViewModel.saveDiary(diary)
                diaryDialogTarget = null
                Toast.makeText(context, "日记已更新", Toast.LENGTH_SHORT).show()
            },
        )
    }

    // 删除日记确认
    pendingDeleteDiary?.let { diary ->
        AlertDialog(
            onDismissRequest = { pendingDeleteDiary = null },
            title = { Text("删除日记") },
            text = { Text("确定删除「${diary.title}」吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    globalAppViewModel.deleteDiary(diary.id)
                    pendingDeleteDiary = null
                    Toast.makeText(context, "日记已删除", Toast.LENGTH_SHORT).show()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteDiary = null }) { Text("取消") }
            },
        )
    }
}

/* ---------------------------------- 头部 ---------------------------------- */

/**
 * 顶部用户信息区：左上角城市选择器 + 头像 / 昵称 / 身份 + 内容统计（游客展示提示）。
 */
@Composable
private fun ProfileHeader(
    isGuest: Boolean,
    nickname: String,
    /** 当前活跃 JWT 账号手机号（多账号体系）；非空时昵称下方展示一行 */
    phone: String?,
    /** 头像地址（可空：当前为相册 contentUri，后端上线后为 http URL），空值用 emoji 占位 */
    avatarUrl: String?,
    avatarEmoji: String,
    currentCityName: String,
    /** 最近访问过的城市数（来自 ViewModel，不再写死） */
    visitedCityCount: Int,
    guideCount: Int,
    postCount: Int,
    historyCount: Int,
    citySelector: @Composable () -> Unit,
    /** 点击昵称进入用户资料主页（仅已登录且导航层注入时可点，null 保持原样） */
    onOpenUserProfile: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(76.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Person, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                    if (!isGuest && !avatarUrl.isNullOrBlank()) AsyncImage(model = avatarUrl, contentDescription = "个人头像",
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f).clickable(enabled = !isGuest && onOpenUserProfile != null) { onOpenUserProfile?.invoke() }) {
                Text(if (isGuest) "游客用户" else nickname, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text(if (isGuest) "登录后，收藏你的旅行灵感" else "把日常过成旅行", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!isGuest && onOpenUserProfile != null) IconButton(onClick = onOpenUserProfile) {
                Icon(Icons.Filled.Edit, "个人资料", tint = MaterialTheme.colorScheme.primary)
            }
        }
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                HeaderStat("$guideCount", "收藏攻略")
                HeaderStat("$postCount", "收藏帖子")
                HeaderStat("$historyCount", "浏览记录")
            }
        }
    }
}

@Composable
private fun HeaderStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/* ---------------------------------- 分区 / 列表项 ---------------------------------- */

/** 模块分区：标题（+ 可选右侧操作）+ 白色圆角卡片容器。 */
@Composable
private fun SectionContainer(
    title: String,
    headerAction: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            )
            headerAction?.invoke()
        }
        Spacer(Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column { content() }
        }
    }
}

/**
 * 我的页最底部账号操作区：【切换账号】（进入全屏切换页，可直接添加账号）+【退出登录】。
 * 配色全部取 MaterialTheme.colorScheme，跟随 App 深浅色主题（不再出现黑色按钮块）。
 */
@Composable
private fun AccountActionsCard(
    onSwitchAccount: (() -> Unit)?,
    onLogout: (() -> Unit)?,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(18.dp))
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                if (onSwitchAccount != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onSwitchAccount)
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.settings_switch_account),
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (onSwitchAccount != null && onLogout != null) {
                    Box(
                        modifier = Modifier
                            .padding(start = 18.dp)
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
                if (onLogout != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onLogout)
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Logout,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.settings_logout),
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/** 普通功能行：彩色图标 + 标题 + 右侧文案 + 箭头。 */
@Composable
private fun MenuItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    trailingText: String?,
    onClick: () -> Unit,
    isLast: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuIcon(icon = icon, tint = iconTint)
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (trailingText != null) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 带开关的功能行（深色主题）。 */
@Composable
private fun SwitchItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuIcon(icon = icon, tint = iconTint)
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 功能行左侧圆角彩色图标。 */
@Composable
private fun MenuIcon(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(21.dp),
        )
    }
}

/** 列表项分隔线，可自定义左侧缩进。 */
@Composable
private fun ItemDivider(startIndent: androidx.compose.ui.unit.Dp = 62.dp) {
    Box(
        modifier = Modifier
            .padding(start = startIndent, end = 14.dp)
            .fillMaxWidth()
            .height(0.8.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

/* ---------------------------------- 宠物行 ---------------------------------- */

/**
 * 单条宠物档案行：爪印头像 + 名字 / 品种体重 + 编辑 / 删除按钮。
 */
@Composable
private fun PetItem(
    pet: Pet,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 宠物头像
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "🐾", fontSize = 20.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pet.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${pet.type} · ${formatWeight(pet.weightKg)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "编辑${pet.name}",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.DeleteOutline,
                contentDescription = "删除${pet.name}",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/* ---------------------------------- 日记卡片 ---------------------------------- */

/**
 * 单条旅行日记卡片：标题 + 日期 + 正文摘要（最多 3 行）+ 编辑 / 删除按钮。
 */
@Composable
private fun DiaryItem(
    diary: Diary,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "📓", fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = diary.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "编辑日记",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "删除日记",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (diary.dateText.isNotBlank()) {
                Text(
                    text = diary.dateText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 26.dp),
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = diary.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 26.dp, end = 4.dp),
            )
        }
    }
}

/* ---------------------------------- 切换账号弹窗 ---------------------------------- */

/**
 * 切换账号弹窗：Mock 登录账号 / 游客模式二选一，当前身份打勾。
 */
@Composable
private fun SwitchAccountDialog(
    isGuest: Boolean,
    onDismiss: () -> Unit,
    onLoginMock: () -> Unit,
    onEnterGuest: () -> Unit,
    // 打开 JWT 账号中心入口；为 null 时（如预览）不渲染该选项
    onOpenAccountCenter: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("切换账号") },
        text = {
            Column {
                AccountOption(
                    emoji = MOCK_LOGIN_USER.avatarEmoji,
                    title = MOCK_LOGIN_USER.nickname,
                    subtitle = MOCK_LOGIN_USER.phone,
                    selected = !isGuest,
                    onClick = onLoginMock,
                )
                Spacer(Modifier.height(8.dp))
                AccountOption(
                    emoji = "🙋",
                    title = "游客模式",
                    subtitle = "仅浏览，收藏等功能受限",
                    selected = isGuest,
                    onClick = onEnterGuest,
                )
                // JWT 账号中心：登录 / 注册 / 查看个人信息（后端对接真实接口）
                if (onOpenAccountCenter != null) {
                    Spacer(Modifier.height(8.dp))
                    AccountOption(
                        emoji = "🔐",
                        title = "账号中心",
                        subtitle = "手机号登录 / 注册 / 个人信息",
                        selected = false,
                        onClick = onOpenAccountCenter,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

/** 切换账号弹窗中的单个账号选项。 */
@Composable
private fun AccountOption(
    emoji: String,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(emoji, fontSize = 26.sp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/* ---------------------------------- 工具方法 ---------------------------------- */

/** 体重展示：整数不带小数，否则保留 1 位，单位 kg。 */
private fun formatWeight(kg: Double): String =
    if (kg % 1.0 == 0.0) "${kg.toLong()} kg" else "%.1f kg".format(kg)

/** 缓存大小展示：0 显示「暂无缓存」。 */
private fun formatCacheSize(mb: Float): String =
    if (mb <= 0f) "暂无缓存" else "%.1f MB".format(mb)
