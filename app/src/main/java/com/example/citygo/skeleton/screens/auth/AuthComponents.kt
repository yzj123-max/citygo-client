package com.example.citygo.skeleton.screens.auth

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.citygo.R

/**
 * 收集 [AuthViewModel] 的一次性吐司事件（三个账号页面通用）。
 * 成功提示、后端业务 msg、网络异常固定提示都通过该通道以 Toast 展示。
 */
@Composable
fun CollectAuthToast(viewModel: AuthViewModel) {
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}

/**
 * 账号模块通用 Scaffold：顶部返回栏 + 居中内容。
 * 登录页为起始页时 [onBack] 传 null 不显示返回箭头。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScaffold(
    title: String,
    onBack: (() -> Unit)?,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.auth_back),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        content(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
        )
    }
}

/**
 * 手机号输入框：数字键盘，只保留数字且最多 11 位（长度/非空的最终校验在点击登录时进行）。
 */
@Composable
fun PhoneField(
    phone: String,
    onPhoneChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = phone,
        onValueChange = { raw ->
            onPhoneChange(raw.filter { it.isDigit() }.take(PHONE_MAX_LENGTH))
        },
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.auth_phone)) },
        placeholder = { Text(stringResource(R.string.auth_phone_hint)) },
        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
    )
}

/**
 * 密码输入框：末尾带「小眼睛」显示/隐藏密码切换。
 * label / placeholder 由调用方传入字符串资源，默认取登录页密码文案。
 */
@Composable
fun PasswordField(
    password: String,
    onPasswordChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.auth_password),
    placeholder: String = stringResource(R.string.auth_password_hint),
) {
    var passwordVisible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (passwordVisible) {
                        stringResource(R.string.auth_password_hide)
                    } else {
                        stringResource(R.string.auth_password_show)
                    },
                )
            }
        },
    )
}

/**
 * 账号模块主操作按钮：请求中显示 loading 并禁用，防止重复点击。
 */
@Composable
fun AuthPrimaryButton(
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        enabled = !loading,
        shape = RoundedCornerShape(12.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** 登录 / 注册表单的统一列布局（顶部留白后依次排列内容） */
@Composable
fun AuthFormColumn(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // 账号页新增后端地址框后内容变高：允许纵向滚动，避免小屏键盘弹出时遮挡按钮
            .verticalScroll(rememberScrollState())
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** 手机号长度常量（11 位数字） */
const val PHONE_MAX_LENGTH = 11

/** 短信验证码长度常量（6 位数字） */
const val CODE_MAX_LENGTH = 6
