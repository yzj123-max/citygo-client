package com.example.citygo.skeleton.data.repository.user

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.citygo.skeleton.data.remote.user.UpdateUserInfoRequest
import com.example.citygo.skeleton.data.remote.user.UserApi
import com.example.citygo.skeleton.data.remote.user.UserInfoDto
import com.example.citygo.skeleton.data.remote.user.UserRetrofitProvider
import com.example.citygo.skeleton.data.repository.auth.AuthResult
import com.google.gson.JsonElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/* =====================================================================================
 * 用户资料仓库（本轮新增）：
 * - 拉取资料 / 更新文本资料 / multipart 上传头像；
 * - 复用账号模块 AuthResult 三态结果（Success / BusinessError / NetworkError），
 *   UI 只面对业务语义；
 * - 不在此层碰 DataStore / AccountSession，落盘与全局态更新由 ViewModel 统一完成；
 * - Bearer 头与 401 全局事件由 AuthHttpClient 拦截器统一处理。
 * ===================================================================================== */

class UserRepository(
    private val api: UserApi = UserRetrofitProvider.userApi,
) {
    /** 拉取当前登录用户完整资料 */
    suspend fun fetchUserInfo(): AuthResult<UserInfoDto> = safeCall {
        val resp = api.getUserInfo()
        if (resp.isSuccess && resp.data != null) {
            Log.i(TAG, "fetchUserInfo ok: phone=${resp.data.phone}, nickname=${resp.data.nickname}")
            AuthResult.Success(resp.data, resp.msg)
        } else {
            AuthResult.BusinessError(resp.code ?: -1, resp.msg.orEmpty())
        }
    }

    /** 更新基础文本资料（昵称/简介/性别/生日/地区/职业/学校） */
    suspend fun updateInfo(body: UpdateUserInfoRequest): AuthResult<Unit> = safeCall {
        val resp = api.updateInfo(body)
        if (resp.isSuccess) {
            Log.i(TAG, "updateInfo ok: nickname=${body.nickname}, region=${body.region}")
            AuthResult.Success(Unit, resp.msg)
        } else {
            Log.w(TAG, "updateInfo business error: code=${resp.code}, msg=${resp.msg}")
            AuthResult.BusinessError(resp.code ?: -1, resp.msg.orEmpty())
        }
    }

    /**
     * 上传头像（multipart/form-data）。
     * 通过 ContentResolver 读取相册返回的 content:// 图片字节，文件名取系统展示名，
     * 取不到时用时间戳兜底；MIME 取系统类型，取不到按 image/jpeg。
     *
     * @return 成功时 data 为后端返回的新头像 URL（兼容字符串 / {url} / {avatarUrl} 三种形态）
     */
    suspend fun uploadAvatar(context: Context, imageUri: Uri): AuthResult<String> = safeCall {
        val newUrl = withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val bytes = resolver.openInputStream(imageUri)?.use { it.readBytes() }
                ?: throw IOException("无法读取所选图片")

            val fileName = resolver.query(imageUri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            } ?: "avatar_${System.currentTimeMillis()}.jpg"
            val mime = resolver.getType(imageUri) ?: "image/jpeg"

            Log.i(TAG, "uploadAvatar: file=$fileName, size=${bytes.size}, mime=$mime")
            val requestFile = bytes.toRequestBody(mime.toMediaTypeOrNull(), 0, bytes.size)
            val part = MultipartBody.Part.createFormData(UserApi.AVATAR_PART_NAME, fileName, requestFile)

            val resp = api.uploadAvatar(part)
            if (!resp.isSuccess) {
                throw BusinessException(resp.code ?: -1, resp.msg.orEmpty())
            }
            parseAvatarUrl(resp.data)
                ?: throw IOException("头像上传成功但响应中缺少 URL")
        }
        Log.i(TAG, "uploadAvatar ok: $newUrl")
        AuthResult.Success(newUrl, null)
    }

    /** 兼容解析头像上传响应 data：字符串 URL / {"url":...} / {"avatarUrl":...} */
    private fun parseAvatarUrl(data: JsonElement?): String? {
        if (data == null || data.isJsonNull) return null
        return when {
            data.isJsonPrimitive -> data.asJsonPrimitive.asString?.takeIf { it.isNotBlank() }
            data.isJsonObject -> {
                val obj = data.asJsonObject
                obj.get("avatarUrl")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
                    ?: obj.get("url")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
            }
            else -> null
        }
    }

    /** 仓库内部：把后端业务 code!=200 从 IO 块里带出来（避免被当成网络异常） */
    private class BusinessException(val code: Int, message: String) : Exception(message)

    /**
     * 统一异常归类（与账号模块 AuthRepository.safeCall 语义一致）：
     * - HttpException 且 code=401：业务 401（拦截器同时已发全局踢登录事件）；
     * - 其它 HttpException / 连不上 / 超时 / JSON 异常：统一 NetworkError，细节只进日志。
     */
    private suspend fun <T> safeCall(block: suspend () -> AuthResult<T>): AuthResult<T> {
        return try {
            block()
        } catch (e: BusinessException) {
            AuthResult.BusinessError(e.code, e.message.orEmpty())
        } catch (e: HttpException) {
            Log.e(TAG, "http error: code=${e.code()}", e)
            AuthResult.BusinessError(e.code(), e.message())
        } catch (e: UnknownHostException) {
            Log.e(TAG, "network: unknown host", e)
            AuthResult.NetworkError("unknown host: ${e.message}")
        } catch (e: ConnectException) {
            Log.e(TAG, "network: connect failed", e)
            AuthResult.NetworkError("connect failed: ${e.message}")
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "network: timeout", e)
            AuthResult.NetworkError("timeout: ${e.message}")
        } catch (e: IOException) {
            Log.e(TAG, "network io error", e)
            AuthResult.NetworkError(e.message ?: "io error")
        } catch (e: Exception) {
            Log.e(TAG, "unexpected error", e)
            AuthResult.NetworkError(e.message ?: "unknown error")
        }
    }

    private companion object {
        const val TAG = "UserRepository"
    }
}

/**
 * 把 GET /info 返回的资料合并到本地账号实体：
 * - token / isActive 保持本地值不变（切换账号 / 登录时 token 来自登录接口）；
 * - phone 以后端为准（为空则保留本地）；
 * - 空白字符串统一归一为 null，UI 层展示占位，杜绝出现 "null"；
 * - nickname 是否为 null/空白 是「新用户需初始资料设置」的唯一判断依据。
 */
fun UserInfoDto.mergeInto(base: com.example.citygo.skeleton.data.datastore.AccountEntity) =
    base.copy(
        phone = phone?.trim()?.takeIf { it.isNotEmpty() } ?: base.phone,
        nickname = nickname?.trim()?.takeIf { it.isNotEmpty() },
        avatarUrl = avatarUrl?.trim()?.takeIf { it.isNotEmpty() },
        bgImageUrl = bgImageUrl?.trim()?.takeIf { it.isNotEmpty() },
        desc = desc?.trim()?.takeIf { it.isNotEmpty() },
        gender = gender,
        birthday = birthday?.trim()?.takeIf { it.isNotEmpty() },
        region = region?.trim()?.takeIf { it.isNotEmpty() },
        job = job?.trim()?.takeIf { it.isNotEmpty() },
        school = school?.trim()?.takeIf { it.isNotEmpty() },
    )
