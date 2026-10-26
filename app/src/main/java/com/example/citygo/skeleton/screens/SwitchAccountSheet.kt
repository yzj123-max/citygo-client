package com.example.citygo.skeleton.screens

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.citygo.R
import com.example.citygo.skeleton.data.datastore.AccountEntity

/* =====================================================================================
 * 切换账号【底部弹窗】（本轮按需求新增，小红书风格）
 *
 * - 展示本机已登录过的账号列表（DataStore 持久化），当前账号右侧红色 ✓；
 * - 选中其他账号：本地切 token → 重新请求 GET /info → merge 全局状态后关闭弹窗；
 * - token 已清空（退出过）的账号：提示并跳登录页重新登录；
 * - 「+ 添加账号」：跳登录页；
 * - 长按账号：确认后仅删除本地记录（删当前账号会清会话跳登录）。
 * ===================================================================================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwitchAccountSheet(
    viewModel: AccountViewModel,
    onDismiss: () -> Unit,
    onAddAccount: () -> Unit,
    /** 切换完成（全局状态已刷新）：关闭弹窗的回调 */
    onSwitched: () -> Unit,
    /** 删除 / 选中导致当前身份失效（如删的是当前账号）：清栈跳登录页 */
    onSessionLost: () -> Unit,
) {
    val context = LocalContext.current
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val switching by viewModel.switching
    var pendingDelete by remember { mutableStateOf<AccountEntity?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { if (!switching) onDismiss() },
        sheetState = sheetState,
        containerColor = SwitchBg,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp, max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 18.dp)
                .navigationBarsPadding(),
        ) {
            // 标题行
            Text(
                text = stringResource(R.string.switch_account_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = SwitchTextPrimary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(modifier = Modifier.size(14.dp))

            // 账号卡片
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = SwitchCardBg,
            ) {
                Column {
                    accounts.forEachIndexed { index, account ->
                        SheetAccountRow(
                            account = account,
                            switching = switching,
                            onClick = {
                                if (account.token.isBlank()) {
                                    // 该账号之前退出过（token 已清空）：需重新登录
                                    Toast
                                        .makeText(
                                            context,
                                            R.string.switch_account_need_relogin,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    onDismiss()
                                    onAddAccount()
                                } else {
                                    viewModel.switchAccount(account) { message ->
                                        Toast
                                            .makeText(
                                                context,
                                                message ?: context.getString(
                                                    R.string.switch_account_done,
                                                    accountDisplayName(account),
                                                ),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        onDismiss()
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
                    if (accounts.isNotEmpty()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 66.dp),
                            thickness = 0.5.dp,
                            color = SwitchDivider,
                        )
                    }
                    // 添加账号
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !switching) {
                                onDismiss()
                                onAddAccount()
                            }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
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
                            color = SwitchTextPrimary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.size(12.dp))

            // 切换中：弹窗底部内联转圈提示，防止重复选择
            if (switching) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = SwitchAccentRed,
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                    Text(
                        text = stringResource(R.string.switch_account_switching),
                        fontSize = 14.sp,
                        color = SwitchTextPrimary,
                    )
                }
            }

            Text(
                text = stringResource(R.string.switch_account_sheet_tip),
                fontSize = 12.sp,
                color = SwitchTextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
            Spacer(modifier = Modifier.size(10.dp))
        }
    }

    // 长按删除确认
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.switch_account_delete_title), color = SwitchTextPrimary) },
            text = {
                Text(
                    stringResource(R.string.switch_account_delete_message, target.phone),
                    color = SwitchTextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    viewModel.deleteAccount(target.phone) { wasActive ->
                        if (wasActive) {
                            Toast
                                .makeText(
                                    context,
                                    R.string.switch_account_delete_current_done,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            onDismiss()
                            onSessionLost()
                        } else {
                            Toast
                                .makeText(context, R.string.switch_account_delete_done, Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }) { Text(stringResource(R.string.switch_account_delete_confirm), color = SwitchAccentRed) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.switch_account_delete_cancel), color = SwitchTextSecondary)
                }
            },
            containerColor = SwitchCardBg,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SheetAccountRow(
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
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = accountDisplayName(account),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = SwitchTextPrimary,
            )
            Spacer(modifier = Modifier.size(2.dp))
            Text(
                // token 已清空（退出过）：手机号后提示需重新登录
                text = if (account.token.isBlank()) {
                    stringResource(R.string.switch_account_relogin_suffix, account.phone)
                } else {
                    account.phone
                },
                fontSize = 13.sp,
                color = SwitchTextSecondary,
            )
        }
        if (account.isActive) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.switch_account_active),
                tint = SwitchAccentRed,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
