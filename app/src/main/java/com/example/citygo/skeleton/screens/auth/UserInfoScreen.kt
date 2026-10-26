package com.example.citygo.skeleton.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.citygo.R

/**
 * 个人信息页。
 *
 * - 进入页面自动调用 GET /getUserInfo 加载并展示 id、phone；
 * - Authorization 头由 OkHttp 认证拦截器自动携带；
 * - code=401（或 HTTP 401/403）：ViewModel 吐司过期提示、清空本地 token，
 *   本页收集 forceLogoutEvent 后通过 [onTokenInvalid] 强制返回登录页；
 * - 「退出登录」：先弹确认框，确认后调用 POST /logout；后端成功才清空本地 token
 *   与用户信息并通过 [onBackToLogin] 回登录页，失败吐司后端提示且保留登录态；
 * - 「进入首页」：保留登录态进入主 App。
 */
@Composable
fun UserInfoScreen(
    onBackToLogin: () -> Unit,
    onTokenInvalid: () -> Unit,
    onEnterApp: () -> Unit,
    // 从主 App「我的」进入时提供返回；登录后直达时为 null（无历史可回）
    onBack: (() -> Unit)? = null,
    viewModel: AuthViewModel = viewModel(),
) {
    CollectAuthToast(viewModel)
    val infoLoading = viewModel.infoLoading
    // userInfo 是 ViewModel 中的 Compose 可观察状态，直接读取即可触发重组
    val userInfo = viewModel.userInfo

    // 是否展示退出登录确认弹窗
    var showLogoutDialog by remember { mutableStateOf(false) }

    // 进入即加载用户信息（ViewModel 内部有 loading 防重入）
    LaunchedEffect(Unit) { viewModel.loadUserInfo() }

    // 401 强制登出事件：ViewModel 已吐司过期提示并清 token，这里只负责跳回登录页
    LaunchedEffect(viewModel) {
        viewModel.forceLogoutEvent.collect { onTokenInvalid() }
    }

    AuthScaffold(title = stringResource(R.string.auth_title_user_info), onBack = onBack) { modifier ->
        Column(
            modifier = modifier.padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 头像占位
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape),
                tint = MaterialTheme.colorScheme.primary,
            )

            if (infoLoading && userInfo == null) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.auth_loading_info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 用户信息卡片：id / phone
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    if (userInfo != null) {
                        InfoRow(
                            label = stringResource(R.string.auth_user_id),
                            value = userInfo.id?.toString()
                                ?: stringResource(R.string.auth_placeholder_dash),
                        )
                        Spacer(Modifier.height(12.dp))
                        InfoRow(
                            label = stringResource(R.string.auth_label_phone),
                            value = userInfo.phone
                                ?: stringResource(R.string.auth_placeholder_dash),
                        )
                    } else if (!infoLoading) {
                        Text(
                            text = stringResource(R.string.auth_info_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            // 重试按钮（加载失败时使用）
            if (userInfo == null && !infoLoading) {
                OutlinedButton(onClick = { viewModel.loadUserInfo() }) {
                    Text(stringResource(R.string.auth_reload))
                }
            }

            // 进入主 App（保留登录态）
            Button(
                onClick = onEnterApp,
                modifier = Modifier.fillMaxWidth(),
                enabled = !infoLoading,
            ) { Text(stringResource(R.string.auth_enter_app)) }

            // 退出登录：先弹确认框，确认后由 ViewModel 调后端 /logout
            OutlinedButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.logoutLoading,
            ) { Text(stringResource(R.string.auth_logout)) }

            // 退出登录确认弹窗（AlertDialog 是独立弹窗，放在 Column 内不占布局位置）
            if (showLogoutDialog) {
                LogoutConfirmDialog(
                    loading = viewModel.logoutLoading,
                    onDismiss = {
                        // 请求进行中不允许关闭，避免重复操作
                        if (!viewModel.logoutLoading) showLogoutDialog = false
                    },
                    onConfirm = {
                        viewModel.logout(
                            onSuccess = {
                                showLogoutDialog = false
                                onBackToLogin()
                            },
                        )
                    },
                )
            }
        }
    }
}

/**
 * 退出登录确认弹窗。
 * @param loading 正在请求后端 /logout 时为 true：两个按钮禁用、点击弹窗外不关闭。
 */
@Composable
private fun LogoutConfirmDialog(
    loading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auth_logout_dialog_title)) },
        text = { Text(stringResource(R.string.auth_logout_dialog_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.auth_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) {
                Text(stringResource(R.string.auth_cancel))
            }
        },
    )
}

/** 信息行：左标签右值 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
