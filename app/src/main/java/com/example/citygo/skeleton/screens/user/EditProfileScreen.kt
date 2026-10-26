package com.example.citygo.skeleton.screens.user

import android.widget.Toast
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.citygo.skeleton.screens.common.rememberAvatarImagePicker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/* =====================================================================================
 * 编辑资料页（本轮新增）
 *
 * - 头像独立：点头像唤起相册 → 立即调 POST /user/uploadAvatar，成功回写本地后头像刷新，
 *   不与文本资料混在一次提交里；
 * - 文本资料：昵称（必填，非空校验）/ 简介 / 性别单选 / 生日日期选择器 /
 *   地区 / 职业 / 学校，点保存一次性 PUT /user/updateInfo；
 * - 保存成功：ViewModel 已回写 DataStore + AccountSession，直接返回，资料主页自动刷新。
 * ===================================================================================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    viewModel: UserProfileViewModel = viewModel(),
) {
    val context = LocalContext.current
    val account by viewModel.currentAccount.collectAsStateWithLifecycle()
    val saving = viewModel.saving
    val uploadingAvatar = viewModel.uploadingAvatar

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // 表单初值取自当前账号缓存；切换账号进入时以手机号为 key 重新装填
    var nickname by remember(account?.phone) {
        mutableStateOf(account?.nickname.orEmpty())
    }
    var desc by remember(account?.phone) {
        mutableStateOf(account?.desc.orEmpty())
    }
    var gender by remember(account?.phone) {
        mutableStateOf(account?.gender)
    }
    var birthday by remember(account?.phone) {
        mutableStateOf(account?.birthday)
    }
    var region by remember(account?.phone) {
        mutableStateOf(account?.region.orEmpty())
    }
    var job by remember(account?.phone) {
        mutableStateOf(account?.job.orEmpty())
    }
    var school by remember(account?.phone) {
        mutableStateOf(account?.school.orEmpty())
    }
    var showDatePicker by remember { mutableStateOf(false) }

    // 头像选择：API33+ 系统 Photo Picker；低版本先动态申请存储权限再开相册；
    // 选完立即调 POST /uploadAvatar 上传（与文本保存解耦）
    val pickAvatar = rememberAvatarImagePicker { uri -> viewModel.uploadAvatar(uri) }

    Box(modifier = Modifier.fillMaxSize().background(UserProfileBg)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.edit_profile_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = UserProfileTextPrimary,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.auth_back),
                                tint = UserProfileTextPrimary,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = UserProfileBg),
                    modifier = Modifier.statusBarsPadding(),
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // ---------- 头像（点击立即上传独立接口） ----------
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3A3A3C))
                            .border(
                                width = 1.dp,
                                color = UserProfileTextSecondary.copy(alpha = 0.4f),
                                shape = CircleShape,
                            )
                            .clickable {
                                // API33+ Photo Picker / 低版本申请权限后开相册
                                pickAvatar()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        val url = account?.avatarUrl?.takeIf { it.isNotBlank() }
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
                                modifier = Modifier.size(44.dp),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(UserProfileAccentRed),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoCamera,
                            contentDescription = stringResource(R.string.user_profile_avatar_change),
                            tint = Color.White,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.edit_profile_avatar_tip),
                    fontSize = 12.sp,
                    color = UserProfileTextSecondary,
                )

                Spacer(modifier = Modifier.height(18.dp))

                // ---------- 文本资料表单 ----------
                DarkTextField(
                    value = nickname,
                    onValueChange = { if (it.length <= 20) nickname = it },
                    label = stringResource(R.string.user_profile_field_nickname),
                    singleLine = true,
                    required = true,
                )
                Spacer(modifier = Modifier.height(14.dp))
                DarkTextField(
                    value = desc,
                    onValueChange = { if (it.length <= 100) desc = it },
                    label = stringResource(R.string.user_profile_field_desc),
                    singleLine = false,
                    minLines = 3,
                )
                Spacer(modifier = Modifier.height(14.dp))

                // 性别单选
                FieldLabel(stringResource(R.string.user_profile_field_gender))
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GenderChoice(
                        text = stringResource(R.string.edit_profile_gender_male),
                        selected = gender == GENDER_MALE,
                        onSelect = { gender = GENDER_MALE },
                    )
                    Spacer(modifier = Modifier.size(24.dp))
                    GenderChoice(
                        text = stringResource(R.string.edit_profile_gender_female),
                        selected = gender == GENDER_FEMALE,
                        onSelect = { gender = GENDER_FEMALE },
                    )
                    Spacer(modifier = Modifier.size(24.dp))
                    GenderChoice(
                        text = stringResource(R.string.edit_profile_gender_none),
                        selected = gender == null,
                        onSelect = { gender = null },
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))

                // 生日（点击弹日期选择器，可清除）
                FieldLabel(stringResource(R.string.user_profile_field_birthday))
                Spacer(modifier = Modifier.height(6.dp))
                BirthdayField(
                    birthday = birthday,
                    onClick = { showDatePicker = true },
                    onClear = { birthday = null },
                )
                Spacer(modifier = Modifier.height(14.dp))

                DarkTextField(
                    value = region,
                    onValueChange = { if (it.length <= 30) region = it },
                    label = stringResource(R.string.user_profile_field_region),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(14.dp))
                DarkTextField(
                    value = job,
                    onValueChange = { if (it.length <= 30) job = it },
                    label = stringResource(R.string.user_profile_field_job),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(14.dp))
                DarkTextField(
                    value = school,
                    onValueChange = { if (it.length <= 30) school = it },
                    label = stringResource(R.string.user_profile_field_school),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(26.dp))

                // ---------- 保存（一次性提交文本资料） ----------
                Button(
                    onClick = {
                        viewModel.saveInfo(
                            nickname = nickname,
                            desc = desc,
                            gender = gender,
                            birthday = birthday,
                            region = region,
                            job = job,
                            school = school,
                        ) { success ->
                            if (success) onBack()
                        }
                    },
                    enabled = !saving && !uploadingAvatar,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UserProfileAccentRed,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.edit_profile_save),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }

        // 生日选择器
        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = birthday?.let(::parseDateToMillis),
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            birthday = formatMillisToDate(millis)
                        }
                        showDatePicker = false
                    }) {
                        Text(
                            text = stringResource(R.string.edit_profile_date_confirm),
                            color = UserProfileAccentRed,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text(
                            text = stringResource(R.string.switch_account_delete_cancel),
                            color = UserProfileTextSecondary,
                        )
                    }
                },
            ) {
                DatePicker(state = datePickerState)
            }
        }

        if (saving) LoadingOverlay(stringResource(R.string.edit_profile_saving))
        if (uploadingAvatar) LoadingOverlay(stringResource(R.string.user_profile_avatar_uploading))
    }
}

/** 表单字段标签（必填项带红色 *） */
@Composable
private fun FieldLabel(text: String, required: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = text, fontSize = 13.sp, color = UserProfileTextSecondary)
        if (required) {
            Text(text = " *", fontSize = 13.sp, color = UserProfileAccentRed)
        }
    }
}

/** 深色风格输入框 */
@Composable
private fun DarkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean,
    required: Boolean = false,
    minLines: Int = 1,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        FieldLabel(label, required)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            minLines = minLines,
            shape = RoundedCornerShape(12.dp),
            placeholder = {
                Text(
                    text = stringResource(R.string.edit_profile_field_placeholder, label),
                    color = UserProfileTextSecondary.copy(alpha = 0.7f),
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = UserProfileFieldBg,
                unfocusedContainerColor = UserProfileFieldBg,
                focusedBorderColor = UserProfileAccentRed,
                unfocusedBorderColor = UserProfileDivider,
                focusedTextColor = UserProfileTextPrimary,
                unfocusedTextColor = UserProfileTextPrimary,
                cursorColor = UserProfileAccentRed,
            ),
        )
    }
}

/** 性别单选项 */
@Composable
private fun GenderChoice(text: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = onSelect),
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = UserProfileAccentRed,
                unselectedColor = UserProfileTextSecondary,
            ),
        )
        Text(text = text, fontSize = 15.sp, color = UserProfileTextPrimary)
    }
}

/** 生日只读输入样式行：点击弹选择器，有值时右侧可清除 */
@Composable
private fun BirthdayField(birthday: String?, onClick: () -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(UserProfileFieldBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.DateRange,
            contentDescription = null,
            tint = UserProfileTextSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            text = birthday ?: stringResource(R.string.edit_profile_birthday_placeholder),
            fontSize = 15.sp,
            color = if (birthday.isNullOrBlank()) UserProfileTextSecondary else UserProfileTextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (!birthday.isNullOrBlank()) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.edit_profile_birthday_clear),
                tint = UserProfileTextSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onClear),
            )
        }
    }
}

/** yyyy-MM-dd → UTC 毫秒（供日期选择器初始值）；解析失败返回 null */
private fun parseDateToDateSafe(date: String): Date? =
    runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(date)
    }.getOrNull()

private fun parseDateToMillis(date: String): Long? = parseDateToDateSafe(date)?.time

/** UTC 毫秒 → yyyy-MM-dd（DatePicker 按 UTC 选取，避免时区偏移导致日期差一天） */
private fun formatMillisToDate(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(millis))
