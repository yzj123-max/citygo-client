package com.example.citygo.skeleton.screens.auth

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.citygo.R

/** 登录页选项卡索引：0=密码登录，1=验证码登录 */
private const val TAB_PASSWORD = 0
private const val TAB_CODE = 1

/**
 * 登录页（顶部 Tab 切换两种登录方式）。
 *
 * - 密码登录：手机号 + 密码（密码框带显示/隐藏小眼睛），POST /login（JSON Body 传参）；
 * - 验证码登录：手机号 + 验证码 +「获取验证码」按钮：
 *   · 点击调 POST /sendCode（Query 传 phone），成功后按钮进入 60s 倒计时并置灰；
 *   · 登录调 POST /verifyCodeLogin（**表单提交** application/x-www-form-urlencoded：phone、code）；
 * - 输入校验（不通过直接吐司，不发请求）：手机号非空且 11 位数字；
 *   密码非空；验证码非空且 6 位数字；
 * - 登录成功保存 token 并吐司「登录成功」；业务失败吐司后端 msg；网络异常吐司固定提示。
 */
@Composable
fun LoginScreen(
    // 登录成功回调：isNewUser=true 新用户跳「完善资料」页；false 老用户直接进首页
    onLoginSuccess: (isNewUser: Boolean) -> Unit,
    onGoRegister: () -> Unit,
    // 从「设置-切换账号」跳入时传入：顶部显示返回箭头，可放弃切换回到设置页；
    // App 冷启动直达登录页时为 null，不显示返回
    onBack: (() -> Unit)? = null,
    viewModel: AuthViewModel = viewModel(),
) {
    val context = LocalContext.current
    // 收集一次性吐司事件（成功 / 后端 msg / 网络异常）
    CollectAuthToast(viewModel)

    var selectedTab by remember { mutableIntStateOf(TAB_PASSWORD) }
    // 手机号在两个 Tab 间共享，切换不丢失
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    AuthScaffold(title = stringResource(R.string.auth_title_login), onBack = onBack) { modifier ->
        AuthFormColumn(modifier = modifier) {
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.Surface(shape = RoundedCornerShape(22.dp)) {
                com.example.citygo.ui.theme.CityLandscape(Modifier.fillMaxWidth().height(120.dp))
            }
            Text("CityGo", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Text("去发现，属于你的城市故事", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.auth_welcome),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            // ---------- 登录方式 Tab ----------
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Tab(
                    selected = selectedTab == TAB_PASSWORD,
                    onClick = { selectedTab = TAB_PASSWORD },
                    text = { Text(stringResource(R.string.auth_tab_password)) },
                )
                Tab(
                    selected = selectedTab == TAB_CODE,
                    onClick = { selectedTab = TAB_CODE },
                    text = { Text(stringResource(R.string.auth_tab_code)) },
                )
            }

            PhoneField(phone = phone, onPhoneChange = { phone = it })

            when (selectedTab) {
                // ---------- 密码登录 ----------
                TAB_PASSWORD -> {
                    PasswordField(password = password, onPasswordChange = { password = it })

                    Spacer(Modifier.height(8.dp))
                    AuthPrimaryButton(
                        text = stringResource(R.string.auth_btn_login),
                        loading = viewModel.loginLoading,
                        onClick = {
                            if (!validatePhone(context, phone)) return@AuthPrimaryButton
                            if (password.isBlank()) {
                                toast(context, R.string.auth_password_empty)
                                return@AuthPrimaryButton
                            }
                            viewModel.login(phone, password, onSuccess = onLoginSuccess)
                        },
                    )
                }

                // ---------- 验证码登录 ----------
                TAB_CODE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { raw ->
                                // 只保留数字，最多 6 位
                                code = raw.filter { it.isDigit() }.take(CODE_MAX_LENGTH)
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.auth_code)) },
                            placeholder = { Text(stringResource(R.string.auth_code_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )

                        // 倒计时剩余秒数 / 发送中状态来自 ViewModel，旋屏重组不丢
                        val countdown = viewModel.codeCountdown
                        val sending = viewModel.sendCodeLoading
                        val getCodeEnabled =
                            phone.length == PHONE_MAX_LENGTH && countdown == 0 && !sending
                        TextButton(
                            onClick = {
                                if (!validatePhone(context, phone)) return@TextButton
                                viewModel.sendCode(phone)
                            },
                            enabled = getCodeEnabled,
                            modifier = Modifier.width(112.dp),
                        ) {
                            Text(
                                text = when {
                                    countdown > 0 ->
                                        stringResource(R.string.auth_code_resend, countdown)
                                    sending -> stringResource(R.string.auth_code_sending)
                                    else -> stringResource(R.string.auth_get_code)
                                },
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    AuthPrimaryButton(
                        text = stringResource(R.string.auth_btn_login),
                        loading = viewModel.codeLoginLoading,
                        onClick = {
                            if (!validatePhone(context, phone)) return@AuthPrimaryButton
                            // 验证码必须 6 位数字
                            if (code.isBlank() || code.length != CODE_MAX_LENGTH) {
                                toast(context, R.string.auth_code_invalid)
                                return@AuthPrimaryButton
                            }
                            viewModel.codeLogin(phone, code, onSuccess = onLoginSuccess)
                        },
                    )
                }
            }

            // 去注册
            Text(
                text = stringResource(R.string.auth_go_register),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { onGoRegister() },
            )
        }
    }
}

/**
 * 校验手机号：非空且必须 11 位数字；不通过弹吐司并返回 false。
 * 输入框已限制只能输入数字，这里兜底非空与长度。
 */
private fun validatePhone(context: android.content.Context, phone: String): Boolean {
    if (phone.length != PHONE_MAX_LENGTH) {
        toast(context, R.string.auth_phone_invalid)
        return false
    }
    return true
}

/** 内部小工具：按字符串资源弹短吐司 */
private fun toast(context: android.content.Context, resId: Int) {
    Toast.makeText(context, context.getString(resId), Toast.LENGTH_SHORT).show()
}
