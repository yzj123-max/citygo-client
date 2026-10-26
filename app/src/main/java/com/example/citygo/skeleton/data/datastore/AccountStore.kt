package com.example.citygo.skeleton.data.datastore

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/* =====================================================================================
 * 多账号本地持久化（Preferences DataStore + JSON）
 *
 * 业务规则：
 * - 每条账号 [AccountEntity]：手机号 phone（唯一键）、登录 token、
 *   昵称 / 头像 / 性别 / 生日（后端暂未返回，全部可空，UI 已做空值兼容）、isActive；
 * - 最多缓存 [MAX_ACCOUNTS] 个账号，超出淘汰尾部最旧账号；
 * - 同一手机号自动去重：再次登录 / 更新资料 = 覆盖更新并置活跃；
 * - 任意时刻最多一个 isActive。
 *
 * 注意：本类只负责「落盘」。全 App 当前账号 / token 的唯一内存来源是
 * AccountSession（StateFlow），网络拦截器只从 AccountSession 取 token，
 * 不直接读本 DataStore，保证 token 来源统一。
 *
 * 文件：citygo_accounts.preferences_pb。
 * ===================================================================================== */

/** Application 级账号 DataStore 单例（文件名 citygo_accounts） */
private val Context.accountDataStore: DataStore<Preferences> by preferencesDataStore(name = "citygo_accounts")

/**
 * 本地账号实体（一条 = 一个可切换的登录身份）。
 *
 * 后端 /user/info 返回的资料字段（本轮追加，全部可空，旧缓存 JSON 自动兼容为 null）：
 *
 * @param nickname 昵称（可空；完善资料页提交后有值）
 * @param avatarUrl 头像地址（可空；相册上传后为后端 http URL，历史数据可能是 contentUri）
 * @param bgImageUrl 个人主页背景图 URL（可空，空时资料主页显示默认渐变底色）
 * @param desc 个人简介（可空）
 * @param gender 性别：1=男，2=女（可空，未设置）
 * @param birthday 生日 yyyy-MM-dd（可空）
 * @param region 地区（可空）
 * @param job 职业（可空）
 * @param school 学校（可空）
 */
data class AccountEntity(
    val phone: String,
    val token: String,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val bgImageUrl: String? = null,
    val desc: String? = null,
    val gender: Int? = null,
    val birthday: String? = null,
    val region: String? = null,
    val job: String? = null,
    val school: String? = null,
    val isActive: Boolean = false,
)

/**
 * 账号列表读写仓库：屏蔽 DataStore / JSON 细节。
 * 列表以 JSON 字符串整体存放在一个 Preferences Key 中（最多 5 条，体量极小）。
 */
class AccountStore(private val context: Context) {

    private val gson = Gson()

    /** 账号列表响应式流（最近登录的账号排在最前） */
    val accountsFlow: Flow<List<AccountEntity>> =
        context.accountDataStore.data.map { prefs -> decode(prefs[KEY_ACCOUNTS]) }

    /** 当前活跃账号流（无账号时为 null） */
    val activeAccountFlow: Flow<AccountEntity?> =
        accountsFlow.map { list -> list.firstOrNull { it.isActive } }

    /** 一次性读取当前账号列表 */
    suspend fun getAccounts(): List<AccountEntity> = accountsFlow.first()

    /** 一次性读取当前活跃账号（App 启动 hydrate 时使用） */
    suspend fun getActiveAccount(): AccountEntity? =
        getAccounts().firstOrNull { it.isActive }

    /**
     * 写入 / 覆盖账号（登录成功或完善资料成功后调用）。
     *
     * - 手机号已存在：以 [account] 为准覆盖 token / 资料字段，移动到列表头部，置为活跃；
     * - 新手机号：插到头部并置活跃，其余账号取消活跃；
     * - 超过 [MAX_ACCOUNTS]：丢弃尾部最旧账号。
     *
     * @return 保存后的完整账号列表
     */
    suspend fun saveAccount(
        account: AccountEntity,
        expected: SessionSnapshot = AccountSession.snapshot(),
    ): List<AccountEntity> {
        var result: List<AccountEntity> = emptyList()
        context.accountDataStore.edit { prefs ->
            if (!AccountSession.isCurrent(expected) || expected.phone != account.phone ||
                expected.token != account.token) {
                throw kotlinx.coroutines.CancellationException("账号已切换，忽略旧资料")
            }
            val current = decode(prefs[KEY_ACCOUNTS])
            val others = current.filterNot { it.phone == account.phone }.map { it.copy(isActive = false) }
            result = (listOf(account.copy(isActive = true)) + others).take(MAX_ACCOUNTS)
            prefs[KEY_ACCOUNTS] = gson.toJson(result)
        }
        return result
    }

    /**
     * 切换到已保存账号：目标 isActive=true，其余 false。**不修改 token 本身**。
     * @return 切换后的活跃账号；手机号不存在返回 null
     */
    suspend fun switchActive(phone: String): AccountEntity? {
        val before = getAccounts()
        if (before.none { it.phone == phone }) {
            Log.w(TAG, "switchActive failed: phone=$phone not found")
            return null
        }
        Log.i(TAG, "switchActive: BEFORE ${before.activeLog()}")

        val updated = before.map { it.copy(isActive = it.phone == phone) }
        persist(updated)

        val active = updated.first { it.isActive }
        Log.i(TAG, "switchActive: AFTER ${active.logWithMaskedToken()}")
        return active
    }

    /** 全部账号取消活跃（token 失效强制登出时使用，账号记录保留以便重新登录） */
    suspend fun deactivateAll(expectedPhone: String? = null) {
        context.accountDataStore.edit { prefs ->
            val current = decode(prefs[KEY_ACCOUNTS])
            if (expectedPhone != null && current.none { it.isActive && it.phone == expectedPhone }) {
                return@edit
            }
            prefs[KEY_ACCOUNTS] = gson.toJson(current.map { it.copy(isActive = false) })
        }
    }

    /**
     * 退出登录（本轮契约）：清空当前活跃账号的 token 并取消活跃，
     * **账号记录保留**（昵称 / 头像等资料仍在，重新登录即可恢复）；
     * 其他账号记录与资料原样保留。
     * @return 被退出的手机号；当前无活跃账号返回 null
     */
    suspend fun clearActiveToken(expected: SessionSnapshot = AccountSession.snapshot()): String? {
        var removedPhone: String? = null
        context.accountDataStore.edit { prefs ->
            if (!AccountSession.isCurrent(expected)) return@edit
            val current = decode(prefs[KEY_ACCOUNTS])
            val active = current.firstOrNull { it.isActive && it.phone == expected.phone } ?: return@edit
            prefs[KEY_ACCOUNTS] = gson.toJson(current.map {
                if (it.phone == active.phone) it.copy(token = "", isActive = false) else it
            })
            removedPhone = active.phone
        }
        return removedPhone
    }

    /**
     * 移除指定手机号账号（仅删本地缓存，不请求后端）：
     * - 删除的是当前活跃账号（退出登录）：其余账号全部取消活跃；
     * - 删除的是普通账号（切换页长按删除）：保留其余账号原有活跃标记。
     */
    suspend fun removeAccount(phone: String, expected: SessionSnapshot? = null): AccountEntity? {
        var removed: AccountEntity? = null
        context.accountDataStore.edit { prefs ->
            if (expected != null && !AccountSession.isCurrent(expected)) return@edit
            val current = decode(prefs[KEY_ACCOUNTS])
            val target = current.firstOrNull { it.phone == phone } ?: return@edit
            val remaining = current.filterNot { it.phone == phone }.map {
                if (target.isActive) it.copy(isActive = false) else it
            }
            prefs[KEY_ACCOUNTS] = gson.toJson(remaining)
            removed = target
        }
        return removed
    }

    // ---------------------------------- 内部工具 ----------------------------------

    /** 整体写入账号列表（DataStore 原子写） */
    private suspend fun persist(list: List<AccountEntity>) {
        context.accountDataStore.edit { prefs ->
            prefs[KEY_ACCOUNTS] = gson.toJson(list)
        }
    }

    /** 解析 JSON；空 / 损坏数据容错为空列表 */
    private fun decode(json: String?): List<AccountEntity> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<AccountEntity>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "decode accounts failed, fallback to empty list", e)
            emptyList()
        }
    }

    /** 日志用：活跃账号 */
    private fun List<AccountEntity>.activeLog(): String =
        firstOrNull { it.isActive }?.logWithMaskedToken() ?: "null"

    /** 日志输出：手机号 + 资料字段有无 + 脱敏 token（不打印完整 JWT） */
    private fun AccountEntity.logWithMaskedToken(): String {
        val masked = when {
            token.length <= 12 -> "***"
            else -> "${token.take(8)}...${token.takeLast(4)}(len=${token.length})"
        }
        return "phone=$phone, nickname=$nickname, hasAvatar=${!avatarUrl.isNullOrBlank()}, " +
            "gender=$gender, birthday=$birthday, token=$masked"
    }

    private companion object {
        const val TAG = "AccountStore"

        /** 本机最多缓存的账号数量 */
        const val MAX_ACCOUNTS = 5

        /** 账号列表 JSON 的 Preferences Key */
        val KEY_ACCOUNTS = stringPreferencesKey("accounts_json")
    }
}
