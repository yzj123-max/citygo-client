package com.example.citygo.skeleton.screens.auth

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.citygo.R
import com.example.citygo.skeleton.data.datastore.AccountEntity
import com.example.citygo.skeleton.data.datastore.AccountSession
import com.example.citygo.skeleton.data.datastore.AccountStore
import com.example.citygo.skeleton.data.remote.auth.ApiResponse
import com.example.citygo.skeleton.data.remote.auth.UserInfo
import com.example.citygo.skeleton.data.repository.auth.AuthRepository
import com.example.citygo.skeleton.data.repository.auth.AuthResult
import com.example.citygo.skeleton.data.repository.auth.LoginPayload
import com.example.citygo.skeleton.data.repository.user.UserRepository
import com.example.citygo.skeleton.data.repository.user.mergeInto
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * 账号模块 ViewModel：为登录 / 注册 / 个人信息页提供状态与动作。
 *
 * 提示规则（与需求一一对应）：
 * - 登录成功：吐司固定文案「登录成功」；
 * - 业务失败（验证码错误 / 手机号未注册等 code=400）：吐司**后端返回的 msg**；
 * - 网络异常（断网 / 超时 / 连不上服务）：吐司固定文案「网络请求失败，请检查服务」；
 * - 401（token 失效）：清本地 token，发送一次性事件强制跳回登录页。
 *
 * loading / 倒计时 / userInfo 为 Compose 可观察状态；吐司与强制登出为 Channel 一次性事件。
 */
class AuthViewModel(
    app: Application,
) : AndroidViewModel(app) {

    private val repository = AuthRepository()

    /** 用户资料接口（GET /info、PUT /updateInfo、POST /uploadAvatar） */
    private val userRepository = UserRepository()

    /** 多账号本地持久化（登录成功写入 / 覆盖当前账号并标记活跃） */
    private val accountStore = AccountStore(getApplication())

    // ---- 各请求 loading 状态（防重复点击） ----
    var registerLoading by mutableStateOf(false)
        private set
    var loginLoading by mutableStateOf(false)
        private set
    var infoLoading by mutableStateOf(false)
        private set
    var codeLoginLoading by mutableStateOf(false)
        private set
    var sendCodeLoading by mutableStateOf(false)
        private set
    /** 退出登录请求中（确认弹窗的「确定」按钮 loading，防重复提交） */
    var logoutLoading by mutableStateOf(false)
        private set

    /**
     * 验证码倒计时剩余秒数：
     * 0 可点击获取验证码；>0 倒计时中，按钮置灰显示「xs 后重发」。
     */
    var codeCountdown by mutableStateOf(0)
        private set

    /** 倒计时协程，绑定 viewModelScope，页面销毁 / VM 清理自动取消 */
    private var countdownJob: Job? = null

    /** 个人信息页用户数据（id、phone） */
    var userInfo by mutableStateOf<UserInfo?>(null)
        private set

    // ---- 一次性吐司事件（页面 collect 后弹 Toast，避免重组重复弹出） ----
    private val _toastChannel = Channel<String>(Channel.BUFFERED)
    val toastEvent = _toastChannel.receiveAsFlow()

    /** getUserInfo 业务 code=401（或 HTTP 401/403）时发送，UI 收到后强制回登录页 */
    private val _forceLogoutChannel = Channel<Unit>(Channel.BUFFERED)
    val forceLogoutEvent = _forceLogoutChannel.receiveAsFlow()

    /** 发送一条文本吐司（后端 msg 等） */
    private fun toast(message: String) {
        viewModelScope.launch { _toastChannel.send(message) }
    }

    /** 发送一条字符串资源吐司（本地固定提示，如「登录成功」「网络请求失败，请检查服务」） */
    private fun toast(@StringRes resId: Int) {
        toast(getApplication<Application>().getString(resId))
    }

    /**
     * 网络失败吐司：优先展示仓库层给出的具体中文原因（连不上 / 超时 / 后端返回的 msg 已走业务失败分支），
     * 没有具体原因时才用本地兜底文案，避免笼统的「网络异常」。
     */
    private fun toastNetworkError(result: AuthResult.NetworkError, @StringRes fallbackRes: Int = R.string.auth_network_error) {
        toast(result.userMessage ?: getString(fallbackRes))
    }

    /** 注册：成功吐司后端 msg 并回调跳登录页；失败只提示不跳转 */
    fun register(phone: String, password: String, onSuccess: () -> Unit) {
        if (registerLoading) return
        registerLoading = true
        viewModelScope.launch {
            when (val result = repository.register(phone.trim(), password)) {
                is AuthResult.Success -> {
                    toast(result.message ?: getString(R.string.auth_register_success_default))
                    onSuccess()
                }
                is AuthResult.BusinessError ->
                    toast(result.message.ifBlank { getString(R.string.auth_register_failed_default) })
                is AuthResult.NetworkError -> toastNetworkError(result)
            }
            registerLoading = false
        }
    }

    /**
     * 密码登录：成功后由 [handleLoginSuccess] 统一分流（新用户完善资料 / 老用户进首页）。
     * @param onSuccess 参数 isNewUser=true 跳完善资料页，false 直接进首页
     */
    fun login(phone: String, password: String, onSuccess: (isNewUser: Boolean) -> Unit) {
        if (loginLoading) return
        loginLoading = true
        viewModelScope.launch {
            when (val result = repository.login(phone.trim(), password)) {
                is AuthResult.Success -> {
                    toast(R.string.auth_login_success)
                    handleLoginSuccess(phone.trim(), result.data, onSuccess)
                }
                is AuthResult.BusinessError ->
                    toast(result.message.ifBlank { getString(R.string.auth_login_failed_default) })
                is AuthResult.NetworkError -> toastNetworkError(result)
            }
            loginLoading = false
        }
    }

    /**
     * 发送模拟验证码。
     * 只有请求成功才启动 60s 倒计时；业务失败吐司后端 msg、网络异常吐司固定网络文案，
     * 两种失败均不启动倒计时，按钮保持可点，避免把用户锁死。
     */
    fun sendCode(phone: String) {
        if (sendCodeLoading || codeCountdown > 0) return
        sendCodeLoading = true
        viewModelScope.launch {
            when (val result = repository.sendCode(phone.trim())) {
                is AuthResult.Success -> {
                    // 后端 msg 可能直接携带模拟验证码，原样吐司便于联调
                    toast(result.message ?: getString(R.string.auth_code_sent_default))
                    startCountdown()
                }
                is AuthResult.BusinessError ->
                    toast(result.message.ifBlank { getString(R.string.auth_code_send_failed_default) })
                is AuthResult.NetworkError -> toastNetworkError(result)
            }
            sendCodeLoading = false
        }
    }

    /** 验证码登录：成功后与密码登录共用分流逻辑（不改动验证码发送 / 校验） */
    fun codeLogin(phone: String, code: String, onSuccess: (isNewUser: Boolean) -> Unit) {
        if (codeLoginLoading) return
        codeLoginLoading = true
        viewModelScope.launch {
            when (val result = repository.codeLogin(phone.trim(), code.trim())) {
                is AuthResult.Success -> {
                    toast(R.string.auth_login_success)
                    handleLoginSuccess(phone.trim(), result.data, onSuccess)
                }
                is AuthResult.BusinessError ->
                    toast(result.message.ifBlank { getString(R.string.auth_login_failed_default) })
                is AuthResult.NetworkError -> toastNetworkError(result)
            }
            codeLoginLoading = false
        }
    }

    /**
     * 登录成功统一分流（严格按本轮后端契约）：
     * 1. 先用最小账号实体（phone+token）更新全局 [AccountSession]，
     *    网络拦截器立刻拿到新 token（GET /info 也依赖它）；
     * 2. 调用 GET /info 获取用户信息；
     * 3. **data.nickname == null / 空串 → 新用户**：回调跳【初始资料设置页】，
     *    暂不落盘，资料提交成功后再入库进首页；
     * 4. 老用户：把完整资料 merge 进实体 → 写入 DataStore（同号覆盖、置活跃）
     *    → 更新 Session → 回调直接进首页。
     *
     * /info 失败：401 强制登出；业务 / 网络失败吐司后端 msg 或友好文案并停留登录页，
     * 不使用笼统的「网络异常」敷衍。
     */
    private suspend fun handleLoginSuccess(
        phone: String,
        payload: LoginPayload?,
        onSuccess: (isNewUser: Boolean) -> Unit,
    ) {
        if (payload == null) return
        val minimal = AccountEntity(phone = phone, token = payload.token, isActive = true)
        AccountSession.update(minimal)
        val expected = AccountSession.snapshot()

        var abort = false
        var newUser = false
        var entity = minimal

        when (val result = userRepository.fetchUserInfo()) {
            is AuthResult.Success -> {
                val info = result.data
                if (info == null) {
                    // data 为 null：按新用户处理，进入初始资料设置页
                    newUser = true
                    Log.i(TAG, "login: /info data null, treat as new user")
                } else {
                    entity = info.mergeInto(minimal)
                    newUser = entity.nickname.isNullOrBlank()
                    Log.i(
                        TAG,
                        "login: /info fetched phone=${entity.phone}, nickname=${entity.nickname}, " +
                            "newUser=$newUser",
                    )
                }
            }
            is AuthResult.BusinessError -> {
                if (result.code == ApiResponse.CODE_UNAUTHORIZED) {
                    // 新 token 直接被判无效：强制回登录页，不入库
                    handleTokenInvalid()
                    abort = true
                } else {
                    toast(result.message.ifBlank { getString(R.string.user_profile_load_failed) })
                    abort = true
                }
            }
            is AuthResult.NetworkError -> {
                toastNetworkError(result, R.string.auth_info_network_failed)
                abort = true
            }
        }

        if (!AccountSession.isCurrent(expected) || abort) return

        if (newUser) {
            // 新用户不落盘，初始资料设置页提交成功后再写 DataStore + 进首页
            Log.i(TAG, "login: new user (nickname empty) -> initial profile setup")
            onSuccess(true)
            return
        }

        accountStore.saveAccount(entity, expected)
        AccountSession.update(entity, expected)
        Log.i(TAG, "login: existing user persisted, go home: ${entity.phone}")
        onSuccess(false)
    }

    /** 启动 60s 倒计时：每秒减 1，归零自动恢复按钮可点 */
    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            codeCountdown = CODE_COUNTDOWN_SECONDS
            while (codeCountdown > 0) {
                delay(1000L)
                codeCountdown -= 1
            }
        }
    }

    /** 加载个人信息（进入页面即调用）；401 清 token 并强制回登录页 */
    fun loadUserInfo() {
        if (infoLoading) return
        infoLoading = true
        viewModelScope.launch {
            when (val result = repository.getUserInfo()) {
                is AuthResult.Success -> {
                    userInfo = result.data
                    Log.i("AuthViewModel", "userInfo loaded: id=${result.data?.id}")
                }
                is AuthResult.BusinessError -> {
                    // 401：用过期提示兜底并强制登出；其它业务错误用通用获取失败兜底
                    val fallback = if (result.code == ApiResponse.CODE_UNAUTHORIZED) {
                        R.string.auth_token_expired
                    } else {
                        R.string.auth_userinfo_failed_default
                    }
                    toast(result.message.ifBlank { getString(fallback) })
                    if (result.code == ApiResponse.CODE_UNAUTHORIZED) {
                        handleTokenInvalid()
                    }
                }
                is AuthResult.NetworkError -> toastNetworkError(result)
            }
            infoLoading = false
        }
    }

    /**
     * 退出登录（用户在确认弹窗点击「确定」后调用）：
     * - 请求后端 POST /logout 成功：Repository 已清空本地 token，这里再清内存用户信息并回调跳登录页；
     * - 业务失败（code!=200）：吐司后端返回的失败信息（msg 为空时用本地兜底文案）；
     * - 网络异常：吐司固定「网络请求失败，请检查服务」，本地登录态保留，用户可重试。
     */
    fun logout(onSuccess: () -> Unit) {
        if (logoutLoading) return
        logoutLoading = true
        viewModelScope.launch {
            when (val result = repository.logout()) {
                is AuthResult.Success -> {
                    userInfo = null
                    onSuccess()
                }
                is AuthResult.BusinessError ->
                    toast(result.message.ifBlank { getString(R.string.auth_logout_failed_default) })
                is AuthResult.NetworkError -> toastNetworkError(result)
            }
            logoutLoading = false
        }
    }

    /** token 失效统一处理：DataStore 全部取消活跃 + 清空全局会话，通知 UI 强制回登录页 */
    private suspend fun handleTokenInvalid() {
        Log.w(TAG, "token invalid(401), clear session and force logout")
        accountStore.deactivateAll()
        AccountSession.clear()
        userInfo = null
        _forceLogoutChannel.send(Unit)
    }

    private fun getString(@StringRes resId: Int): String =
        getApplication<Application>().getString(resId)

    companion object {
        private const val TAG = "AuthViewModel"

        /** 验证码重发倒计时秒数 */
        const val CODE_COUNTDOWN_SECONDS = 60
    }
}
