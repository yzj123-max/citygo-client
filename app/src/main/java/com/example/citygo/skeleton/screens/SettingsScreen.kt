package com.example.citygo.skeleton.screens

import android.app.Application
import android.util.Log
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.citygo.R
import com.example.citygo.skeleton.data.datastore.AccountSession
import com.example.citygo.skeleton.data.datastore.AccountStore
import com.example.citygo.skeleton.data.remote.auth.ApiResponse
import com.example.citygo.skeleton.data.remote.auth.AuthApiConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.citygo.skeleton.data.remote.AppRetrofit
import retrofit2.http.POST
import java.io.IOException

/* ----------------------------------------------------------------------------------------------
 * 设置页（参考小红书深色风格）
 *
 * 页面结构：
 * - 顶部 [TopAppBar]：标题「设置」+ 返回箭头；
 * - 上半部分 [LazyColumn]：圆角卡片承载常规设置项（存储空间 / 内容偏好调节 /
 *   添加小组件 / 未成年人模式 / 新功能体验 / 帮助与客服），可滚动；
 * - 底部固定区域（不随列表滚动）：切换账号、退出登录两个独立圆角按钮 + 灰色协议小字。
 *
 * 退出登录：先弹确认框，确认后携带当前 Bearer 调 POST /logout（token 取自全局
 * AccountSession StateFlow），随后删除 DataStore 当前账号记录、清空 currentAccount，
 * 由导航层清栈跳转登录页；失败 / 断网也会完成本地退出并 Toast 提示。
 * ------------------------------------------------------------------------------------------- */

// ---------------------------------- 配色（跟随 App 当前深浅色主题，不再写死黑色） ----------------------------------
//
// 历史问题：本页曾写死一套小红书深色值（#0D0D0D / #262628…），浅色 App 下两个底部按钮
// 显示为黑色块。现统一改为 MaterialTheme.colorScheme 取值，深浅色模式自动适配，
// 调用处写法保持「SettingsXxx」不变（声明为 @Composable getter）。

/** 页面底色：App 背景色 */
private val SettingsBg: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background

/** 圆角卡片底色：surfaceVariant，浅色模式为浅灰、深色模式为深灰 */
private val SettingsCardBg: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface

/** 底部「切换账号 / 退出登录」按钮底色：次级容器色，浅色下是浅灰而不是黑色 */
private val SettingsButtonBg: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondaryContainer

/** 主文字色 */
private val SettingsTextPrimary: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface

/** 次要文字色（箭头 / 协议小字） */
private val SettingsTextSecondary: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant

/** 卡片内分割线 */
private val SettingsDivider: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outlineVariant

/** 退出登录红：跟随主题 error 色（浅色红 / 深色亮红） */
private val SettingsLogoutRed: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error

/** 弹窗底色：surface，与全局 AlertDialog 默认风格一致 */
private val SettingsDialogBg: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface

// ---------------------------------- 页面入口 ----------------------------------

/**
 * 设置页。
 *
 * @param onBack 顶部返回箭头回调
 * @param onSwitchAccount 切换账号：导航层负责清空返回栈并跳转登录页
 * @param onLoggedOut 退出登录成功：导航层负责清空返回栈并跳转登录页（本地 token 已由 VM 清除）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSwitchAccount: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
    darkMode: Boolean = false,
    onDarkModeChange: (Boolean) -> Unit = {},
    onPrivacy: () -> Unit = {},
    onAbout: () -> Unit = {},
    onClearCache: () -> Unit = {},
    onAccountCenter: () -> Unit = {},
) {
    val context = LocalContext.current

    // 是否展示退出确认弹窗
    var showLogoutDialog by remember { mutableStateOf(false) }
    // 退出请求进行中（按钮 / 弹窗防重入）
    val loggingOut by viewModel.loggingOutState

    // 收集 VM 的一次性 Toast 事件（成功 / 业务失败 / 网络异常）
    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = SettingsBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SettingsTextPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.auth_back),
                            tint = SettingsTextPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SettingsBg),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // ==================== 上半部分：可滚动的常规设置项 ====================
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = SettingsCardBg) {
                        Column {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("深色主题", Modifier.weight(1f), color = SettingsTextPrimary)
                                androidx.compose.material3.Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
                            }
                            SettingsLink("隐私设置", onPrivacy)
                            SettingsLink("清除缓存", onClearCache)
                            SettingsLink("账号中心", onAccountCenter)
                        }
                    }
                }
                item {
                    Text("更多设置", style = MaterialTheme.typography.labelLarge,
                        color = SettingsTextSecondary, modifier = Modifier.padding(horizontal = 4.dp))
                }
                item {
                    SettingsGroupCard(
                        onEntryClick = {
                            // 常规设置项当前均为占位入口
                            Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.settings_coming_soon),
                                    Toast.LENGTH_SHORT,
                                )
                                .show()
                        },
                    )
                }
                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = SettingsCardBg) {
                        SettingsLink("关于我们", onAbout)
                    }
                }
            }

            // ==================== 底部固定区域：不随 LazyColumn 滚动 ====================
            SettingsBottomBar(
                loggingOut = loggingOut,
                onSwitchAccount = onSwitchAccount,
                onLogoutClick = { showLogoutDialog = true },
            )
        }
    }

    // ==================== 退出登录确认弹窗 ====================
    if (showLogoutDialog) {
        LogoutConfirmDialog(
            loggingOut = loggingOut,
            onDismiss = { showLogoutDialog = false },
            onConfirm = {
                viewModel.logout(onSuccess = onLoggedOut)
            },
        )
    }
}

@Composable
private fun SettingsLink(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = SettingsTextPrimary)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SettingsTextSecondary)
    }
}

// ---------------------------------- 常规设置分组卡片 ----------------------------------

/**
 * 圆角卡片，承载 7 个常规设置行，行间为缩进细分割线。
 */
@Composable
private fun SettingsGroupCard(onEntryClick: () -> Unit) {
    // 常规设置项（文案资源 id；当前均为 Mock 入口）
    val entries = remember {
        listOf(
            R.string.settings_storage,
            R.string.settings_preference,
            R.string.settings_widget,
            R.string.settings_minor,
            R.string.settings_new_feature,
            R.string.settings_help,
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SettingsCardBg,
    ) {
        Column {
            entries.forEachIndexed { index, labelRes ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onEntryClick)
                        .padding(horizontal = 18.dp, vertical = 17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(labelRes),
                        fontSize = 16.sp,
                        color = SettingsTextPrimary,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text("开发中", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    Spacer(modifier = Modifier.size(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = SettingsTextSecondary,
                    )
                }
                // 最后一行下方不画分割线
                if (index != entries.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 18.dp),
                        thickness = 0.5.dp,
                        color = SettingsDivider,
                    )
                }
            }
        }
    }
}

// ---------------------------------- 底部固定区域 ----------------------------------

/**
 * 切换账号 / 退出登录两个独立圆角按钮（上下排列）+ 居中灰色协议小字。
 * 使用 [navigationBarsPadding] 避让手势导航条，固定在页面底部不参与滚动。
 */
@Composable
private fun SettingsBottomBar(
    loggingOut: Boolean,
    onSwitchAccount: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SettingsBg)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 按钮一：切换账号（文字与按钮底色配套：onSecondaryContainer）
        BottomActionButton(
            text = stringResource(R.string.settings_switch_account),
            textColor = MaterialTheme.colorScheme.onSecondaryContainer,
            enabled = !loggingOut,
            onClick = onSwitchAccount,
        )
        Spacer(modifier = Modifier.height(12.dp))
        // 按钮二：退出登录（红色文字）
        BottomActionButton(
            text = stringResource(R.string.settings_logout),
            textColor = SettingsLogoutRed,
            enabled = !loggingOut,
            onClick = onLogoutClick,
        )
        Spacer(modifier = Modifier.height(10.dp))
        // 灰色小号协议文字，居中
        Text(
            text = stringResource(R.string.settings_agreement),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = SettingsTextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 底部全宽圆角动作按钮。
 */
@Composable
private fun BottomActionButton(
    text: String,
    textColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SettingsButtonBg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) textColor else textColor.copy(alpha = 0.4f),
        )
    }
}

// ---------------------------------- 退出登录确认弹窗 ----------------------------------

/**
 * 退出确认 AlertDialog：正文「确定要退出登录吗？」，确定（红）/ 取消。
 * 请求进行中：确定按钮显示转圈，两个按钮禁用，点弹窗外 / 返回不允许关闭。
 */
@Composable
private fun LogoutConfirmDialog(
    loggingOut: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!loggingOut) onDismiss()
        },
        confirmButton = {
            TextButton(
                enabled = !loggingOut,
                onClick = onConfirm,
            ) {
                if (loggingOut) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = SettingsLogoutRed,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.auth_confirm),
                        color = SettingsLogoutRed,
                        fontSize = 15.sp,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !loggingOut,
                onClick = onDismiss,
            ) {
                Text(
                    text = stringResource(R.string.auth_cancel),
                    color = SettingsTextSecondary,
                    fontSize = 15.sp,
                )
            }
        },
        text = {
            Text(
                text = stringResource(R.string.settings_logout_confirm_message),
                fontSize = 16.sp,
                color = SettingsTextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        containerColor = SettingsDialogBg,
        shape = RoundedCornerShape(16.dp),
    )
}

// ===============================================================================================
// 网络层：退出登录 Retrofit 接口（本页内聚，不改动账号模块既有 OkHttp 代码）
//
// 复用账号模块已有基础设施：
// - [AuthApiConfig.BASE_URL]：统一后端地址；
// - [AuthHttpClient.client]：已内置 Bearer 认证拦截器、10s 超时与 BODY 级日志；
// - [ApiResponse]：后端统一返回体 {code,msg,data}，code==200 视为成功；
// - AccountSession：全局当前账号（StateFlow 唯一 token 源）；AccountStore：多账号列表。
//
// 退出流程按需求顺序：先携带当前 Bearer 调 /logout，再从 DataStore 删除当前账号记录、
// 清空 AccountSession 全局当前账号，最后清栈跳登录页。鉴权头在调用前从
// AccountSession(StateFlow) 缓存 token 并通过 @Header 显式传入。
// ===============================================================================================

/** 退出登录接口：POST /logout，无 Query / Body；Bearer 头由调用方显式传入 */
private interface SettingsLogoutApi {

    @POST(AuthApiConfig.PATH_LOGOUT)
    suspend fun logout(
        @retrofit2.http.Header(AuthApiConfig.HEADER_AUTH) authorization: String,
        // 退出时旧 token 即便 401 也不触发全局踢登录；该头由拦截器发出前移除
        @retrofit2.http.Header(AuthApiConfig.HEADER_SKIP_AUTH_EVENT) skipAuthEvent: String,
    ): ApiResponse<Any>
}

/**
 * Retrofit 实例入口：统一走 [AppRetrofit]，baseUrl 取全局动态地址
 * [com.example.citygo.skeleton.data.remote.ServerConfig]（经 AuthApiConfig.BASE_URL 同源），
 * 复用账号模块 OkHttp 客户端。getter 保证切换地址后即时生效。
 */
private object SettingsRetrofitProvider {

    val logoutApi: SettingsLogoutApi
        get() = AppRetrofit.createAuthed()
}

/**
 * 设置页 ViewModel：仅负责退出登录（本地账号清理 + 后端请求）与 Toast 事件。
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    /** 多账号本地存储 */
    private val accountStore = com.example.citygo.skeleton.data.datastore.AccountStore(application)

    /** 退出请求进行中（Compose 直接观察） */
    private val _loggingOut = mutableStateOf(false)
    val loggingOutState = _loggingOut

    /** 一次性 Toast 事件通道（成功 / 失败文案） */
    private val toastChannel = Channel<String>(Channel.BUFFERED)
    val toastEvent = toastChannel.receiveAsFlow()

    /**
     * 退出登录（严格按需求顺序执行）：
     * 1. 从全局 AccountSession 缓存当前 token；
     * 2. 携带 Bearer 调 POST /logout 通知后端 JWT 失效（失败不阻塞本地退出）；
     * 3. 从 DataStore 多账号列表删除当前账号整条记录；
     * 4. 清空 AccountSession 内 currentAccount（StateFlow，全局 UI 立即变为未登录）；
     * 5. 清栈跳转登录页。
     */
    fun logout(onSuccess: () -> Unit) {
        if (_loggingOut.value) return
        _loggingOut.value = true
        val expected = AccountSession.snapshot()
        viewModelScope.launch {
            val context = getApplication<Application>()
            val current = AccountSession.currentAccount.value
            val token = current?.token
            Log.i(TAG, "logout START: account=${current?.phone}, token=${token?.take(8)}...")

            try {
                // 步骤 2：先请求后端（此时本地仍是登录态，token 也来自 AccountSession）
                if (!token.isNullOrBlank()) {
                    val response: ApiResponse<Any> = withContext(Dispatchers.IO) {
                        SettingsRetrofitProvider.logoutApi.logout(
                            AuthApiConfig.BEARER_PREFIX + token,
                            "1",
                        )
                    }
                    if (response.isSuccess) {
                        Log.i(TAG, "logout: remote /logout success")
                    } else {
                        // 本地仍继续退出，仅把后端 msg 吐司给用户
                        Log.w(TAG, "logout: remote business error code=${response.code}")
                        toastChannel.trySend(
                            response.msg?.takeIf { it.isNotBlank() }
                                ?: context.getString(R.string.settings_logout_failed),
                        )
                    }
                }

                if (!AccountSession.isCurrent(expected)) return@launch

                // 步骤 3：DataStore 删除当前账号整条记录
                if (current != null) {
                    accountStore.removeAccount(current.phone, expected)
                }

                // 步骤 4：清空全局当前账号（StateFlow → 我的页等自动刷新为未登录）
                if (!AccountSession.isCurrent(expected)) return@launch
                AccountSession.clear(expected)

                // 步骤 5：清栈跳登录页
                toastChannel.trySend(context.getString(R.string.settings_logout_success))
                onSuccess()
            } catch (e: IOException) {
                if (!AccountSession.isCurrent(expected)) return@launch
                // 断网 / 超时：后端虽未确认，本地仍完成退出（删账号 + 清会话）
                Log.w(TAG, "logout: network error, clearing local session anyway", e)
                if (current != null) accountStore.removeAccount(current.phone, expected)
                if (!AccountSession.isCurrent(expected)) return@launch
                AccountSession.clear(expected)
                toastChannel.trySend(context.getString(R.string.auth_network_error))
                onSuccess()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (!AccountSession.isCurrent(expected)) return@launch
                Log.e(TAG, "logout: unexpected error, clearing local session anyway", e)
                if (current != null) accountStore.removeAccount(current.phone, expected)
                if (!AccountSession.isCurrent(expected)) return@launch
                AccountSession.clear(expected)
                toastChannel.trySend(context.getString(R.string.settings_logout_failed))
                onSuccess()
            } finally {
                _loggingOut.value = false
            }
        }
    }

    private companion object {
        const val TAG = "SettingsLogout"
    }
}

