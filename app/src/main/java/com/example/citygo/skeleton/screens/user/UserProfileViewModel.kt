package com.example.citygo.skeleton.screens.user

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.citygo.R
import com.example.citygo.skeleton.data.datastore.AccountEntity
import com.example.citygo.skeleton.data.datastore.AccountSession
import com.example.citygo.skeleton.data.datastore.AccountStore
import com.example.citygo.skeleton.data.datastore.SessionSnapshot
import com.example.citygo.skeleton.data.remote.user.UpdateUserInfoRequest
import com.example.citygo.skeleton.data.repository.auth.AuthResult
import com.example.citygo.skeleton.data.repository.user.UserRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/* =====================================================================================
 * 用户资料主页 / 编辑资料页共用 ViewModel（本轮新增）
 *
 * 单一数据源：全局 [AccountSession.currentAccount]（StateFlow）。
 * - 进入资料主页：refresh() 调 GET /user/info，成功后 merge 进 DataStore 当前账号，
 *   StateFlow 更新 → 两个页面 + 主页昵称自动重组刷新；
 * - 编辑页保存：PUT /user/updateInfo 成功后同样 merge；
 * - 头像：POST /user/uploadAvatar 独立上传，成功后只 merge avatarUrl，
 *   不与文本资料一起提交。
 *
 * token / 401 统一由拦截器处理；本 VM 只关心业务三态并给 UI 吐 Toast。
 * ===================================================================================== */

class UserProfileViewModel(
    app: Application,
) : AndroidViewModel(app) {

    private val accountStore = AccountStore(app)
    private val repository = UserRepository()

    /** 当前账号（资料字段观察这一份；任何 merge 后全 App 自动刷新） */
    val currentAccount = AccountSession.currentAccount

    /** 资料主页初次 / 手动刷新加载中 */
    var loading by mutableStateOf(false)
        private set

    /** 编辑资料保存中 */
    var saving by mutableStateOf(false)
        private set

    /** 头像上传中（独立于文本保存） */
    var uploadingAvatar by mutableStateOf(false)
        private set

    private val toastChannel = Channel<String>(Channel.BUFFERED)
    val toastEvent = toastChannel.receiveAsFlow()

    private fun toast(text: String) {
        toastChannel.trySend(text)
    }

    /** 拉取最新用户资料并回写本地缓存（进入资料主页时自动调用，也可手动刷新） */
    fun refresh() {
        if (loading) return
        loading = true
        val expected = AccountSession.snapshot()
        viewModelScope.launch {
            Log.i(TAG, "refresh user info: start, phone=${AccountSession.currentAccount.value?.phone}")
            when (val result = repository.fetchUserInfo()) {
                is AuthResult.Success -> {
                    val info = result.data
                    if (info == null) {
                        toast(getString(R.string.user_profile_load_failed))
                    } else {
                        val merged = merge(expected) { current ->
                            current.copy(
                                phone = info.phone?.takeIf { it.isNotBlank() } ?: current.phone,
                                nickname = info.nickname?.trimToNull(),
                                avatarUrl = info.avatarUrl?.trimToNull(),
                                bgImageUrl = info.bgImageUrl?.trimToNull(),
                                desc = info.desc?.trimToNull(),
                                gender = info.gender,
                                birthday = info.birthday?.trimToNull(),
                                region = info.region?.trimToNull(),
                                job = info.job?.trimToNull(),
                                school = info.school?.trimToNull(),
                            )
                        }
                        if (merged) {
                            Log.i(TAG, "refresh user info merged into DataStore")
                        } else {
                            Log.w(TAG, "refresh user info skipped: no active account in session")
                        }
                    }
                }
                is AuthResult.BusinessError -> {
                    Log.w(TAG, "refresh business error: code=${result.code}")
                    toast(result.message.ifBlank { getString(R.string.user_profile_load_failed) })
                }
                is AuthResult.NetworkError -> {
                    Log.w(TAG, "refresh network error: ${result.detail}")
                    toast(getString(R.string.auth_network_error))
                }
            }
            loading = false
        }
    }

    /**
     * 保存文本资料（一次性提交）。
     * @return true 表示业务成功，UI 可返回上一页
     */
    fun saveInfo(
        nickname: String,
        desc: String?,
        gender: Int?,
        birthday: String?,
        region: String?,
        job: String?,
        school: String?,
        onFinished: (success: Boolean) -> Unit,
    ) {
        if (saving) return
        // 昵称必填（非空校验）
        if (nickname.isBlank()) {
            toast(getString(R.string.user_profile_nickname_required))
            onFinished(false)
            return
        }
        saving = true
        val expected = AccountSession.snapshot()
        viewModelScope.launch {
            val body = UpdateUserInfoRequest(
                nickname = nickname.trim(),
                desc = desc?.trimToNull(),
                gender = gender,
                birthday = birthday?.trimToNull(),
                region = region?.trimToNull(),
                job = job?.trimToNull(),
                school = school?.trimToNull(),
            )
            Log.i(TAG, "saveInfo: nickname=${body.nickname}, gender=$gender, birthday=$birthday")
            var success = false
            when (val result = repository.updateInfo(body)) {
                is AuthResult.Success -> {
                    merge(expected) { current ->
                        current.copy(
                            nickname = body.nickname,
                            desc = body.desc,
                            gender = body.gender,
                            birthday = body.birthday,
                            region = body.region,
                            job = body.job,
                            school = body.school,
                        )
                    }
                    toast(getString(R.string.user_profile_save_success))
                    success = true
                }
                is AuthResult.BusinessError ->
                    toast(result.message.ifBlank { getString(R.string.user_profile_save_failed) })
                is AuthResult.NetworkError ->
                    toast(getString(R.string.auth_network_error))
            }
            saving = false
            onFinished(success)
        }
    }

    /**
     * 上传头像（独立接口，不随文本资料提交）。
     * 成功后把后端返回的新 avatarUrl merge 进 DataStore 与会话，UI 立即刷新。
     */
    fun uploadAvatar(uri: Uri) {
        if (uploadingAvatar) return
        uploadingAvatar = true
        val expected = AccountSession.snapshot()
        viewModelScope.launch {
            Log.i(TAG, "uploadAvatar: uri=$uri")
            when (val result = repository.uploadAvatar(getApplication(), uri)) {
                is AuthResult.Success -> {
                    val newUrl = result.data
                    if (newUrl.isNullOrBlank()) {
                        toast(getString(R.string.user_profile_avatar_failed))
                    } else {
                        merge(expected) { current -> current.copy(avatarUrl = newUrl) }
                        toast(getString(R.string.user_profile_avatar_success))
                    }
                }
                is AuthResult.BusinessError ->
                    toast(result.message.ifBlank { getString(R.string.user_profile_avatar_failed) })
                is AuthResult.NetworkError ->
                    toast(getString(R.string.auth_network_error))
            }
            uploadingAvatar = false
        }
    }

    /**
     * 把资料变更 merge 到当前活跃账号并落盘。
     * token 不变、isActive 保持 true；DataStore 同手机号去重覆盖。
     * @return false 表示当前没有登录账号（异常流程，理论不会发生）
     */
    private suspend fun merge(expected: SessionSnapshot, transform: (AccountEntity) -> AccountEntity): Boolean {
        if (!AccountSession.isCurrent(expected)) return false
        val current = AccountSession.currentAccount.value ?: return false
        val updated = transform(current)
        accountStore.saveAccount(updated, expected)
        AccountSession.update(updated, expected)
        return true
    }

    private fun getString(resId: Int): String = getApplication<Application>().getString(resId)

    /** 空串 / 纯空白统一按 null 存储，UI 层统一展示占位，杜绝出现 "null" */
    private fun String.trimToNull(): String? = trim().takeIf { it.isNotEmpty() }

    private companion object {
        const val TAG = "UserProfileVM"
    }
}
