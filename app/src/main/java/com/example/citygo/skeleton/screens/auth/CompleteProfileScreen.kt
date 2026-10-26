package com.example.citygo.skeleton.screens.auth

import android.app.Application
import android.app.DatePickerDialog
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.citygo.R
import com.example.citygo.skeleton.data.datastore.AccountEntity
import com.example.citygo.skeleton.data.datastore.AccountSession
import com.example.citygo.skeleton.data.datastore.AccountStore
import com.example.citygo.skeleton.data.remote.user.UpdateUserInfoRequest
import com.example.citygo.skeleton.data.repository.auth.AuthResult
import com.example.citygo.skeleton.data.repository.user.UserRepository
import com.example.citygo.skeleton.data.repository.user.mergeInto
import com.example.citygo.skeleton.screens.common.rememberAvatarImagePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

/* =====================================================================================
 * 完善资料页（新注册用户 isNewUser=true 时进入）
 *
 * - 头像：系统相册选择（Photo Picker），未选择时显示默认占位；
 * - 昵称：必填，提交时非空校验；
 * - 性别：男 / 女单选（1=男 2=女，可空）；
 * - 生日：系统日期选择器，yyyy-MM-dd（可空）；
 * - 提交：调用预留 Retrofit 接口 POST /updateUserInfo（后端开发中，失败有完整提示）；
 *   成功后组装 [AccountEntity] 写入 DataStore、更新 AccountSession，导航进首页。
 *
 * 深色风格与设置 / 切换账号页保持一致。
 * ===================================================================================== */

private val ProfileBg: Color
    @Composable get() = MaterialTheme.colorScheme.background
private val ProfileCardBg: Color
    @Composable get() = MaterialTheme.colorScheme.surface
private val ProfileTextPrimary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface
private val ProfileTextSecondary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val ProfileFieldBg: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val ProfileAccentRed: Color
    @Composable get() = MaterialTheme.colorScheme.primary

/** 性别编码（与后端约定） */
private const val GENDER_MALE = 1
private const val GENDER_FEMALE = 2

/**
 * 完善资料 ViewModel：提交资料请求 + 账号入库 + 全局会话更新。
 */
class CompleteProfileViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val accountStore = AccountStore(application)

    /** 本轮新契约：POST /uploadAvatar 单独传头像、PUT /updateInfo 存资料 */
    private val userRepository = UserRepository()

    private val _submitting = mutableStateOf(false)
    val submitting = _submitting

    private val toastChannel = Channel<String>(Channel.BUFFERED)
    val toastEvent = toastChannel.receiveAsFlow()

    /**
     * 提交初始资料（严格对接后端）：
     * 1. 校验当前登录会话（登录成功后 AccountSession 持有 phone+token 的临时账号）；
     * 2. 选了头像 → 先 POST /uploadAvatar（multipart 字段 file）拿到头像 URL；
     * 3. PUT /updateInfo 提交 昵称/性别/生日（简介/地区/职业/学校本页不采集，传 null）；
     * 4. 再 GET /info 拿服务端完整资料 merge，组装实体写入 DataStore（同号去重 / 置活跃）
     *    并更新全局 AccountSession → 回调进首页；
     * 5. 任一步失败吐司后端 msg 或差异化友好文案并停留本页可重试。
     */
    fun submit(
        nickname: String,
        avatarUri: String?,
        gender: Int?,
        birthday: String?,
        onSuccess: () -> Unit,
    ) {
        if (_submitting.value) return
        val context = getApplication<Application>()
        val current = AccountSession.currentAccount.value

        if (current == null || current.token.isBlank()) {
            Log.w(TAG, "submit aborted: no active login session")
            toastChannel.trySend(context.getString(R.string.complete_profile_session_missing))
            return
        }

        val expected = AccountSession.snapshot()
        _submitting.value = true
        viewModelScope.launch {
            // ① 头像单独上传（content:// 本地图片）
            var uploadedAvatarUrl: String? = null
            if (!avatarUri.isNullOrBlank()) {
                Log.i(TAG, "uploadAvatar start: phone=${current.phone}")
                when (val up = userRepository.uploadAvatar(context, Uri.parse(avatarUri))) {
                    is AuthResult.Success -> {
                        uploadedAvatarUrl = up.data
                        Log.i(TAG, "uploadAvatar success: $uploadedAvatarUrl")
                    }
                    is AuthResult.BusinessError -> {
                        Log.w(TAG, "uploadAvatar business error: ${up.code} ${up.message}")
                        toastChannel.trySend(
                            up.message.ifBlank {
                                context.getString(R.string.user_profile_avatar_upload_failed)
                            },
                        )
                        _submitting.value = false
                        return@launch
                    }
                    is AuthResult.NetworkError -> {
                        Log.w(TAG, "uploadAvatar network error")
                        toastChannel.trySend(
                            context.getString(R.string.switch_account_info_network_failed),
                        )
                        _submitting.value = false
                        return@launch
                    }
                }
            }

            // ② 提交文字资料
            Log.i(
                TAG,
                "updateInfo request: phone=${current.phone}, nickname=$nickname, " +
                    "gender=$gender, birthday=$birthday, avatar=$uploadedAvatarUrl",
            )
            when (
                val up = userRepository.updateInfo(
                    UpdateUserInfoRequest(
                        nickname = nickname,
                        desc = null,
                        gender = gender,
                        birthday = birthday,
                        region = null,
                        job = null,
                        school = null,
                    ),
                )
            ) {
                is AuthResult.BusinessError -> {
                    Log.w(TAG, "updateInfo business error: ${up.code} ${up.message}")
                    toastChannel.trySend(
                        up.message.ifBlank {
                            context.getString(R.string.complete_profile_save_failed)
                        },
                    )
                    _submitting.value = false
                    return@launch
                }
                is AuthResult.NetworkError -> {
                    Log.w(TAG, "updateInfo network error")
                    toastChannel.trySend(context.getString(R.string.auth_info_network_failed))
                    _submitting.value = false
                    return@launch
                }
                is AuthResult.Success -> Unit
            }

            // ③ 成功：以服务端 /info 为准 merge 本地实体（失败则用表单值兜底）
            var entity = current.copy(
                nickname = nickname,
                gender = gender,
                birthday = birthday,
                isActive = true,
            )
            when (val info = userRepository.fetchUserInfo()) {
                is AuthResult.Success -> {
                    info.data?.let { dto ->
                        entity = dto.mergeInto(current)
                        // merge 后头像以本次上传结果优先
                        uploadedAvatarUrl?.let { entity = entity.copy(avatarUrl = it) }
                    }
                }
                else -> {
                    uploadedAvatarUrl?.let { entity = entity.copy(avatarUrl = it) }
                }
            }

            accountStore.saveAccount(entity, expected)
            AccountSession.update(entity, expected)
            Log.i(TAG, "updateInfo success, account persisted: ${entity.phone}")
            toastChannel.trySend(context.getString(R.string.complete_profile_save_success))
            _submitting.value = false
            if (AccountSession.isCurrent(expected)) onSuccess()
        }
    }

    private companion object {
        const val TAG = "CompleteProfile"
    }
}

/**
 * 完善资料页入口。
 *
 * @param onCompleted 提交成功：导航层清空栈进入首页
 * @param viewModel 页面级 ViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompleteProfileScreen(
    onCompleted: () -> Unit,
    viewModel: CompleteProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val context = LocalContext.current

    // 一次性 Toast
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // 表单状态
    var avatarUri by remember { mutableStateOf<String?>(null) }
    var nickname by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf<Int?>(null) }
    var birthday by remember { mutableStateOf<String?>(null) }
    val submitting by viewModel.submitting

    // 头像选择：API33+ 系统 Photo Picker；API < 33 先动态申请存储权限再打开相册
    val pickAvatar = rememberAvatarImagePicker { uri ->
        avatarUri = uri.toString()
        Log.i("CompleteProfile", "avatar picked: $avatarUri")
    }

    Scaffold(
        containerColor = ProfileBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.complete_profile_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ProfileTextPrimary,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ProfileBg),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.complete_profile_subtitle),
                fontSize = 13.sp,
                color = ProfileTextSecondary,
            )
            Spacer(modifier = Modifier.height(24.dp))

            // ---------- 头像（相册选择 / 默认占位） ----------
            AvatarPicker(
                avatarUri = avatarUri,
                enabled = !submitting,
                onClick = {
                    // API33+ Photo Picker / 低版本申请权限后开相册
                    pickAvatar()
                },
            )
            Spacer(modifier = Modifier.height(28.dp))

            // ---------- 昵称（必填） ----------
            ProfileFieldLabel(stringResource(R.string.complete_profile_nickname_label))
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it.take(20) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !submitting,
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(R.string.complete_profile_nickname_hint),
                        color = ProfileTextSecondary,
                    )
                },
                keyboardOptions = KeyboardOptions.Default,
                colors = darkTextFieldColors(),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(modifier = Modifier.height(20.dp))

            // ---------- 性别单选 ----------
            ProfileFieldLabel(stringResource(R.string.complete_profile_gender_label))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                GenderOption(
                    text = stringResource(R.string.complete_profile_gender_male),
                    selected = gender == GENDER_MALE,
                    enabled = !submitting,
                    onSelect = { gender = GENDER_MALE },
                )
                GenderOption(
                    text = stringResource(R.string.complete_profile_gender_female),
                    selected = gender == GENDER_FEMALE,
                    enabled = !submitting,
                    onSelect = { gender = GENDER_FEMALE },
                )
                GenderOption(
                    text = stringResource(R.string.complete_profile_gender_none),
                    selected = gender == null,
                    enabled = !submitting,
                    onSelect = { gender = null },
                )
            }
            Spacer(modifier = Modifier.height(20.dp))

            // ---------- 生日（日期选择器） ----------
            ProfileFieldLabel(stringResource(R.string.complete_profile_birthday_label))
            BirthdayField(
                birthday = birthday,
                enabled = !submitting,
                onPicked = { birthday = it },
            )

            Spacer(modifier = Modifier.weight(1f))

            // ---------- 提交 ----------
            Button(
                onClick = {
                    // 昵称非空校验
                    if (nickname.isBlank()) {
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.complete_profile_nickname_required),
                                Toast.LENGTH_SHORT,
                            )
                            .show()
                        return@Button
                    }
                    viewModel.submit(
                        nickname = nickname.trim(),
                        avatarUri = avatarUri,
                        gender = gender,
                        birthday = birthday,
                        onSuccess = onCompleted,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(50.dp),
                enabled = !submitting,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ProfileAccentRed,
                    disabledContainerColor = ProfileAccentRed.copy(alpha = 0.5f),
                ),
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.complete_profile_submit),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/** 头像选择区：有选中图显示图片，否则灰色圆形 + 人像占位 */
@Composable
private fun AvatarPicker(
    avatarUri: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    // contentUri → Bitmap（IO 线程读取，失败保持占位）
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, avatarUri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                avatarUri?.let {
                    context.contentResolver.openInputStream(Uri.parse(it))?.use { input ->
                        BitmapFactory.decodeStream(input)?.asImageBitmap()
                    }
                }
            }.getOrNull()
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(ProfileCardBg)
                .border(width = 1.dp, color = ProfileTextSecondary.copy(alpha = 0.4f), shape = CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            // bitmap 为 produceState 委托属性，无法智能转换，先取局部值
            val avatarBitmap = bitmap
            if (avatarBitmap != null) {
                Image(
                    bitmap = avatarBitmap,
                    contentDescription = stringResource(R.string.complete_profile_avatar),
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = stringResource(R.string.complete_profile_avatar),
                    tint = ProfileTextSecondary,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.complete_profile_avatar),
            fontSize = 12.sp,
            color = ProfileTextSecondary,
        )
    }
}

/** 表单分区小标题 */
@Composable
private fun ProfileFieldLabel(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = ProfileTextPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    )
}

/** 性别单选项 */
@Composable
private fun GenderOption(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(enabled = enabled, onClick = onSelect),
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(
                selectedColor = ProfileAccentRed,
                unselectedColor = ProfileTextSecondary,
            ),
        )
        Text(text = text, fontSize = 15.sp, color = ProfileTextPrimary)
    }
}

/** 生日选择字段：点击弹出系统 DatePickerDialog */
@Composable
private fun BirthdayField(
    birthday: String?,
    enabled: Boolean,
    onPicked: (String) -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ProfileFieldBg)
            .clickable(enabled = enabled) {
                val cal = Calendar.getInstance()
                // 已选过则回显到对应日期
                birthday?.let { runCatching { parseBirthday(it, cal) } }
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        onPicked(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth))
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH),
                ).show()
            }
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(
            text = birthday ?: stringResource(R.string.complete_profile_birthday_hint),
            fontSize = 15.sp,
            color = if (birthday == null) ProfileTextSecondary else ProfileTextPrimary,
        )
    }
}

/** 解析 yyyy-MM-dd 到日历控件（格式异常时保持今天） */
private fun parseBirthday(value: String, cal: Calendar) {
    val parts = value.split("-")
    if (parts.size == 3) {
        cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
    }
}

/** 深色 OutlinedTextField 配色统一收口 */
@Composable
private fun darkTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = ProfileFieldBg,
    unfocusedContainerColor = ProfileFieldBg,
    disabledContainerColor = ProfileFieldBg,
    focusedTextColor = ProfileTextPrimary,
    unfocusedTextColor = ProfileTextPrimary,
    focusedBorderColor = ProfileAccentRed,
    unfocusedBorderColor = ProfileTextSecondary.copy(alpha = 0.5f),
    cursorColor = ProfileAccentRed,
)
