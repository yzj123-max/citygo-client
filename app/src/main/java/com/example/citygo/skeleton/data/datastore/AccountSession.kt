package com.example.citygo.skeleton.data.datastore

import android.content.Context
import android.util.Log
import com.example.citygo.skeleton.data.remote.auth.AuthTokenStore
import com.example.citygo.skeleton.data.remote.auth.AuthHttpClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/* =====================================================================================
 * 全局当前账号会话（进程内单例）——全 App 账号 / token 的【唯一内存来源】
 *
 * - [currentAccount]：StateFlow，UI（我的页、切换账号页等）collect 后自动重组，
 *   彻底解决「切换账号后界面不变」的问题；
 * - [currentToken]：同步读取当前 token，OkHttp / Retrofit 拦截器只从这里取 token，
 *   禁止拦截器直接读 DataStore（DataStore 只负责持久化），保证 token 来源统一；
 * - App 启动时在 Application.onCreate 调 [hydrate]，把 DataStore 中的活跃账号恢复进内存；
 * - 登录 / 切换 / 退出 / 完善资料均通过 [update] / [clear] 改变状态，日志打印前后值。
 * ===================================================================================== */
object AccountSession {

    private const val TAG = "AccountSession"

    private val _currentAccount = MutableStateFlow<AccountEntity?>(null)
    private val fence = SessionFence()
    private val _identity = MutableStateFlow(fence.snapshot())
    val identity: StateFlow<SessionSnapshot> = _identity.asStateFlow()

    fun snapshot(): SessionSnapshot = fence.snapshot()
    fun isCurrent(snapshot: SessionSnapshot): Boolean = fence.isCurrent(snapshot)

    private fun publishIdentity(account: AccountEntity?) {
        val before = fence.snapshot()
        val next = fence.update(account?.phone, account?.token)
        if (next != before) {
            AuthHttpClient.cancelAccountCalls()
            _identity.value = next
        }
    }

    /** 当前账号（被观察）：null 表示未登录 */
    val currentAccount: StateFlow<AccountEntity?> = _currentAccount.asStateFlow()

    /** 当前 token（拦截器同步读取）：未登录为 null，拦截器不附加 Authorization 头 */
    val currentToken: String?
        get() = _currentAccount.value?.token

    /** 是否已登录（启动路由判断使用） */
    fun isLoggedIn(): Boolean = !_currentAccount.value?.token.isNullOrBlank()

    /**
     * App 启动时从 DataStore 恢复当前活跃账号到内存（在 Application.onCreate 调用）。
     * DataStore 中没有活跃账号时保持 null（进登录页）。
     */
    suspend fun hydrate(context: Context) {
        val active = AccountStore(context).getActiveAccount()
        _currentAccount.value = active
        publishIdentity(active)
        // 同步 JWT 到 SharedPreferences（拦截器的持久化来源），保证冷启动后鉴权头立即可用
        active?.token?.takeIf { it.isNotBlank() }
            ?.let { AuthTokenStore.saveToken(it) }
            ?: AuthTokenStore.clear()
        Log.i(
            TAG,
            "hydrate: ${if (active == null) "no active account" else "active=" + active.phone}",
        )
    }

    /**
     * 更新当前账号（登录成功 / 切换账号 / 完善资料成功后调用）。
     * 打印切换前后的手机号与 token，方便排查。
     */
    fun update(account: AccountEntity?, expected: SessionSnapshot? = null) {
        if (expected != null && !isCurrent(expected)) return
        val before = _currentAccount.value
        Log.i(
            TAG,
            "update: BEFORE ${before?.phone ?: "null"}(${before?.token?.take(8)}...) " +
                "-> AFTER ${account?.phone ?: "null"}(${account?.token?.take(8)}...) " +
                "nickname=${account?.nickname}",
        )
        _currentAccount.value = account
        publishIdentity(account)
        // 登录 / 切换账号：JWT 同步写入 SharedPreferences，供拦截器自动带 Bearer
        account?.token?.takeIf { it.isNotBlank() }
            ?.let { AuthTokenStore.saveToken(it) }
            ?: AuthTokenStore.clear()
    }

    /** 清空当前账号（退出登录 / 401 强制登出），不影响 DataStore 中其他已保存账号 */
    fun clear(expected: SessionSnapshot? = null) {
        if (expected != null && !isCurrent(expected)) return
        val before = _currentAccount.value
        Log.i(TAG, "clear: logged-out account=${before?.phone ?: "null"}")
        _currentAccount.value = null
        publishIdentity(null)
        // 退出 / 401 失效：同步清除 SharedPreferences 中的 JWT
        AuthTokenStore.clear()
    }

    // ---------------- 401 / token 失效全局事件（拦截器 → UI 层） ----------------

    private val _authInvalidation = MutableSharedFlow<SessionSnapshot>(
        extraBufferCapacity = 1,
    )

    /**
     * token 失效事件：OkHttp 拦截器发现 HTTP 401 / 业务体 code=401 时发射。
     * App 根 Composable 收集后：吐司「登录已过期」→ 本地取消活跃 → 清会话 → 清栈跳登录页。
     * SharedFlow 无状态、只在有人监听时才有意义，避免 App 冷启动重复弹。
     */
    val authInvalidation: SharedFlow<SessionSnapshot> = _authInvalidation.asSharedFlow()

    /**
     * 由拦截器调用：通知 UI 当前 token 已失效。
     * 仅在登录态下发射；并发请求同时 401 时，首个事件清会话后 [isLoggedIn] 即为 false，
     * 后续事件自然被挡掉，避免重复吐司 / 重复跳转。
     */
    fun notifyTokenInvalid(reason: String, expected: SessionSnapshot = snapshot()) {
        if (!isCurrent(expected)) return
        if (!isLoggedIn()) {
            Log.i(TAG, "notifyTokenInvalid ignored (already logged out): $reason")
            return
        }
        Log.w(TAG, "notifyTokenInvalid: $reason, phone=${_currentAccount.value?.phone}")
        _authInvalidation.tryEmit(expected)
    }
}
