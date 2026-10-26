package com.example.citygo.skeleton.screens

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.citygo.R
import com.example.citygo.skeleton.data.datastore.AccountEntity
import com.example.citygo.skeleton.data.datastore.AccountSession
import com.example.citygo.skeleton.data.datastore.AccountStore
import com.example.citygo.skeleton.data.repository.auth.AuthResult
import com.example.citygo.skeleton.data.repository.user.UserRepository
import com.example.citygo.skeleton.data.repository.user.mergeInto
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/* =====================================================================================
 * 切换账号页（参考小红书：独立全屏页，非弹窗）
 *
 * - 深色主题 + 圆角卡片，顶部 TopAppBar「切换账号」；
 * - 账号列表：头像占位 + 昵称 + 手机号，当前活跃账号右侧红色 ✓；
 * - 点击已有账号：直接复用本地 token（不请求登录接口）→ 刷新用户信息 → 自动回「我的」；
 * - 列表末尾「添加账号」：跳登录页，登录成功后把新账号写入 DataStore；
 * - 底部灰色提示：添加账号后，可在本设备支持账号切换。
 * ===================================================================================== */

// ---------------------------------- 配色（跟随 App 当前深浅色主题，不再写死黑色） ----------------------------------

internal val SwitchBg: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
internal val SwitchCardBg: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceVariant
internal val SwitchTextPrimary: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
internal val SwitchTextSecondary: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
internal val SwitchDivider: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outlineVariant
internal val SwitchAccentRed: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error
internal val SwitchAvatarBg: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondaryContainer

/**
 * 账号数据 ViewModel：
 * - [accounts] 直接观察 DataStore 账号列表（新增 / 删除 / 活跃标记自动刷新 UI）；
 * - [switchAccount] 执行本地切 token + 调 /getUserInfo 刷新；
 * 同时作为 Activity 作用域 VM 供导航层观察当前活跃账号（刷新「我的」页头部）。
 */
class AccountViewModel(
    app: Application,
) : AndroidViewModel(app) {

    private val accountStore = AccountStore(app)
    private val userRepository = UserRepository()
    private val authRepository = com.example.citygo.skeleton.data.repository.auth.AuthRepository()

    /** DataStore 账号列表（冷流转热态，UI 直接观察） */
    val accounts = accountStore.accountsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * 全局当前账号（StateFlow 单例）：切换后全 App 观察处自动重组刷新。
     * 直接暴露 [AccountSession]，我的页面等所有展示用户信息处都 collect 这一份。
     */
    val currentAccount = AccountSession.currentAccount

    /** 切换请求进行中（弹窗内显示转圈，防重复点击） */
    private val _switching = mutableStateOf(false)
    val switching = _switching

    /** 一次性 Toast 事件（切换 / 退出 / 删除结果文案） */
    private val toastChannel = Channel<String>(Channel.BUFFERED)
    val toastEvent = toastChannel.receiveAsFlow()

    private fun toast(text: String) {
        toastChannel.trySend(text)
    }

    /**
     * 切换到已保存账号（本地快速切换，不走登录 / 验证码，也不调 /logout
     * ——旧账号 token 保留有效，方便随时切回）：
     * 1. DataStore：旧账号取消活跃，目标 isActive=true，立即更新全局 [AccountSession]，
     *    拦截器后续请求自动带新 token；
     * 2. **重点**：携带新 token 重新请求 GET /info，把最新用户资料 merge 回
     *    DataStore + StateFlow，全局用户数据彻底刷新为目标账号（不会残留旧账号资料）；
     * 3. /info 失败不阻塞本地切换，吐司友好提示，用户可下拉 / 再次进入刷新。
     *
     * @return 结果交 UI 决定是否关闭弹窗；回调参数为提示文案（null 表示成功无需特殊文案）
     */
    fun switchAccount(target: AccountEntity, onFinished: (message: String?) -> Unit) {
        if (_switching.value) return
        if (target.isActive) {
            Log.i(TAG, "switchAccount: ${target.phone} is already active, skip")
            onFinished(null)
            return
        }
        // 该账号记录的 token 已为空（之前退出过）：不能直接切，需要重新登录
        if (target.token.isBlank()) {
            Log.i(TAG, "switchAccount: ${target.phone} token blank, need re-login")
            onFinished(getApplication<Application>().getString(R.string.switch_account_need_relogin))
            return
        }
        _switching.value = true
        viewModelScope.launch {
            var message: String? = null
            try {
                val before = AccountSession.currentAccount.value
                Log.i(
                    TAG,
                    "switchAccount START: before=${before?.phone} -> target=${target.phone}",
                )

                // 步骤 1：本地切活跃 + 更新全局会话（拦截器之后自动使用新 token）
                val active = accountStore.switchActive(target.phone)
                if (active == null) {
                    Log.w(TAG, "switchAccount aborted: account missing in DataStore")
                    onFinished(null)
                    return@launch
                }
                AccountSession.update(active)
                val expected = AccountSession.snapshot()

                // 步骤 2：用新 token 重新拉 /info 并 merge —— 全局用户信息刷新的核心
                when (val result = userRepository.fetchUserInfo()) {
                    is AuthResult.Success -> {
                        val info = result.data
                        if (info != null) {
                            val merged = info.mergeInto(active)
                            accountStore.saveAccount(merged, expected)
                            AccountSession.update(merged, expected)
                            Log.i(
                                TAG,
                                "switchAccount /info merged: phone=${merged.phone}, " +
                                    "nickname=${merged.nickname}, region=${merged.region}",
                            )
                        }
                    }
                    is AuthResult.BusinessError -> {
                        Log.w(TAG, "switchAccount /info business error: code=${result.code}")
                        message = result.message.takeIf { it.isNotBlank() }
                            ?: getApplication<Application>().getString(R.string.user_profile_load_failed)
                    }
                    is AuthResult.NetworkError -> {
                        Log.w(TAG, "switchAccount /info network error: ${result.detail}")
                        message = getApplication<Application>()
                            .getString(R.string.switch_account_info_network_failed)
                    }
                }

                Log.i(TAG, "switchAccount DONE: active=${AccountSession.currentAccount.value?.phone}")
                if (AccountSession.isCurrent(expected)) onFinished(message)
            } finally {
                _switching.value = false
            }
        }
    }

    /**
     * 退出登录（本轮契约）：
     * 1. 携带当前 token 调 POST /logout（成功 / 业务失败 / 网络异常都继续本地退出）；
     * 2. 清空当前账号本地 token、取消活跃，**保留该账号记录及其他账号记录**；
     * 3. 清空全局 [AccountSession]；
     * 4. 回调由导航层清栈跳登录页。
     */
    fun logoutCurrent(onFinished: () -> Unit) {
        if (_switching.value) return
        _switching.value = true
        viewModelScope.launch {
            val app = getApplication<Application>()
            val phone = AccountSession.currentAccount.value?.phone
            val expected = AccountSession.snapshot()
            Log.i(TAG, "logoutCurrent START: phone=$phone")
            try {
                when (val result = authRepository.logout()) {
                    is AuthResult.Success ->
                        toast(app.getString(R.string.settings_logout_success))
                    is AuthResult.BusinessError ->
                        toast(
                            result.message.takeIf { it.isNotBlank() }
                                ?: app.getString(R.string.settings_logout_failed),
                        )
                    is AuthResult.NetworkError ->
                        toast(app.getString(R.string.switch_account_logout_network_local))
                }
            } finally {
                // 无论接口结果如何，本地退出都要完成；记录保留、仅清 token
                accountStore.clearActiveToken(expected)
                val stillCurrent = AccountSession.isCurrent(expected)
                AccountSession.clear(expected)
                _switching.value = false
                Log.i(TAG, "logoutCurrent DONE: phone=$phone (record kept)")
                if (stillCurrent) onFinished()
            }
        }
    }

    /**
     * 长按删除本地账号记录（仅删本地缓存，不调后端、不删后端用户）。
     * - 删除普通账号：保留当前活跃账号，回调 [onFinished] 的 wasActive=false；
     * - 删除当前活跃账号：DataStore 中已无活跃账号，同时清空 [AccountSession]，
     *   回调 wasActive=true，由导航层清栈跳登录页。
     */
    fun deleteAccount(phone: String, onFinished: (wasActive: Boolean) -> Unit) {
        if (_switching.value) return
        viewModelScope.launch {
            val wasActive = AccountSession.currentAccount.value?.phone == phone
            Log.i(TAG, "deleteAccount: phone=$phone, wasActive=$wasActive")
            val removed = accountStore.removeAccount(phone)
            if (removed == null) {
                Log.w(TAG, "deleteAccount: phone=$phone not found")
                onFinished(false)
                return@launch
            }
            if (wasActive) {
                AccountSession.clear()
            }
            onFinished(wasActive)
        }
    }

    private companion object {
        const val TAG = "AccountSwitch"
    }
}

/**
 * 切换账号页入口。
 *
 * @param onBack 返回上一页（设置页）
 * @param onAddAccount 添加账号：跳登录页
 * @param onSwitched 切换完成：导航层清掉设置 / 本页，回到「我的」Tab 展示刷新后的账号
 * @param onActiveAccountDeleted 长按删除了当前正在使用的账号：已无活跃账号，
 *        导航层清栈跳登录页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwitchAccountScreen(
    onBack: () -> Unit,
    onAddAccount: () -> Unit,
    onSwitched: () -> Unit,
    onActiveAccountDeleted: () -> Unit,
    viewModel: AccountViewModel,
) {
    val context = LocalContext.current
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val switching by viewModel.switching

    // 待删除账号（长按行后置位，非空时展示确认弹窗）
    var pendingDelete by remember { mutableStateOf<AccountEntity?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = SwitchBg,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.switch_account_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SwitchTextPrimary,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.auth_back),
                                tint = SwitchTextPrimary,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SwitchBg),
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                // ---------- 可滚动区：账号卡片 + 添加账号卡片 ----------
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
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = SwitchCardBg,
                        ) {
                            Column {
                                accounts.forEachIndexed { index, account ->
                                    AccountRow(
                                        account = account,
                                        switching = switching,
                                        onClick = {
                                            // token 已被清空（退出过）的记录：需重新登录，不能直接切
                                            if (account.token.isBlank()) {
                                                Toast
                                                    .makeText(
                                                        context,
                                                        R.string.switch_account_need_relogin,
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                onAddAccount()
                                            } else {
                                                viewModel.switchAccount(account) { message ->
                                                    // message 非空 = /info 刷新失败但本地已切换的友好提示
                                                    val text = message
                                                        ?: context.getString(
                                                            R.string.switch_account_done,
                                                            accountDisplayName(account),
                                                        )
                                                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                                                    onSwitched()
                                                }
                                            }
                                        },
                                        onLongClick = { pendingDelete = account },
                                    )
                                    if (index != accounts.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 66.dp),
                                            thickness = 0.5.dp,
                                            color = SwitchDivider,
                                        )
                                    }
                                }

                                // 空列表时不画账号分割线，直接展示添加账号
                                if (accounts.isNotEmpty()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 66.dp),
                                        thickness = 0.5.dp,
                                        color = SwitchDivider,
                                    )
                                }
                                AddAccountRow(enabled = !switching, onClick = onAddAccount)
                            }
                        }
                    }
                }

                // ---------- 底部固定灰色提示 ----------
                Text(
                    text = stringResource(R.string.switch_account_footer_tip),
                    fontSize = 12.sp,
                    color = SwitchTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                )
            }
        }

        // ---------- 切换中全屏加载弹窗（拦截触摸，防重复操作） ----------
        if (switching) {
            SwitchingLoadingOverlay()
        }

        // ---------- 长按删除确认弹窗（仅删本地缓存，不调后端） ----------
        pendingDelete?.let { target ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = {
                    Text(
                        text = stringResource(R.string.switch_account_delete_title),
                        color = SwitchTextPrimary,
                    )
                },
                text = {
                    Text(
                        text = stringResource(
                            R.string.switch_account_delete_message,
                            target.phone,
                        ),
                        color = SwitchTextSecondary,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        pendingDelete = null
                        viewModel.deleteAccount(target.phone) { wasActive ->
                            if (wasActive) {
                                // 删除的是当前账号：已无登录身份，清栈跳登录页
                                Toast
                                    .makeText(
                                        context,
                                        R.string.switch_account_delete_current_done,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                onActiveAccountDeleted()
                            } else {
                                Toast
                                    .makeText(
                                        context,
                                        R.string.switch_account_delete_done,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }
                        }
                    }) {
                        Text(
                            text = stringResource(R.string.switch_account_delete_confirm),
                            color = SwitchAccentRed,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text(
                            text = stringResource(R.string.switch_account_delete_cancel),
                            color = SwitchTextSecondary,
                        )
                    }
                },
                containerColor = SwitchCardBg,
            )
        }
    }
}

/** 账号展示名：nickname 可空（后端资料字段未就绪），空值用「用户 + 手机尾号」占位 */
internal fun accountDisplayName(account: AccountEntity): String =
    account.nickname?.takeIf { it.isNotBlank() }
        ?: "用户${account.phone.takeLast(4)}"

/**
 * 切换账号加载遮罩：半透明黑底 + 圆角卡片 + 转圈 + 文案，拦截所有触摸事件。
 */
@Composable
private fun SwitchingLoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(enabled = false) { /* 吞掉触摸，防切换中误操作 */ },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SwitchCardBg,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                    color = SwitchAccentRed,
                )
                Spacer(modifier = Modifier.size(14.dp))
                Text(
                    text = stringResource(R.string.switch_account_switching),
                    fontSize = 15.sp,
                    color = SwitchTextPrimary,
                )
            }
        }
    }
}

/**
 * 单个账号行：头像占位 + 昵称 / 手机号 + 右侧活跃对勾 / 切换中转圈。
 * 长按弹删除确认（仅删本地缓存）；切换进行中禁用一切点击。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountRow(
    account: AccountEntity,
    switching: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = !switching,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 头像占位（灰色圆底 + 人像图标）
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SwitchAvatarBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = SwitchTextSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                // nickname 可空（后端资料字段未就绪）：空值用「用户 + 手机尾号」占位
                text = account.nickname?.takeIf { it.isNotBlank() }
                    ?: "用户${account.phone.takeLast(4)}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = SwitchTextPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = account.phone,
                fontSize = 13.sp,
                color = SwitchTextSecondary,
            )
        }
        // 当前活跃账号：红色对勾；其它账号被点击切换中：转圈
        when {
            account.isActive ->
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.switch_account_active),
                    tint = SwitchAccentRed,
                    modifier = Modifier.size(22.dp),
                )
            switching ->
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = SwitchAccentRed,
                )
        }
    }
}

/**
 * 添加账号行：红色加号 + 文案。
 */
@Composable
private fun AddAccountRow(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = SwitchAccentRed,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(modifier = Modifier.size(14.dp))
        Text(
            text = stringResource(R.string.switch_account_add),
            fontSize = 16.sp,
            color = if (enabled) SwitchTextPrimary else SwitchTextPrimary.copy(alpha = 0.4f),
        )
    }
}
