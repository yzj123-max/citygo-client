package com.example.citygo.skeleton.screens.auth

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.citygo.R

/**
 * 注册页（仅手机号 + 密码，不需要验证码）。
 *
 * 表单校验：手机号非空且 11 位数字、密码非空；不通过直接吐司，不发起请求。
 * 结果处理：成功吐司后端 msg 并回登录页；业务失败（如手机号已注册）吐司后端 msg；
 * 网络异常吐司固定「网络请求失败，请检查服务」。
 */
@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onBack: () -> Unit,
    viewModel: AuthViewModel = viewModel(),
) {
    val context = LocalContext.current
    CollectAuthToast(viewModel)

    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AuthScaffold(title = stringResource(R.string.auth_title_register), onBack = onBack) { modifier ->
        AuthFormColumn(modifier = modifier) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.auth_create_account),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            PhoneField(phone = phone, onPhoneChange = { phone = it })
            PasswordField(
                password = password,
                onPasswordChange = { password = it },
                label = stringResource(R.string.auth_password),
                placeholder = stringResource(R.string.auth_register_pwd_hint),
            )

            Spacer(Modifier.height(8.dp))
            AuthPrimaryButton(
                text = stringResource(R.string.auth_btn_register),
                loading = viewModel.registerLoading,
                onClick = {
                    // 手机号 11 位数字（输入框已限数字）
                    if (phone.length != PHONE_MAX_LENGTH) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.auth_phone_invalid),
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@AuthPrimaryButton
                    }
                    // 密码非空
                    if (password.isBlank()) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.auth_password_empty),
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@AuthPrimaryButton
                    }
                    viewModel.register(phone, password, onSuccess = onRegisterSuccess)
                },
            )
        }
    }
}
