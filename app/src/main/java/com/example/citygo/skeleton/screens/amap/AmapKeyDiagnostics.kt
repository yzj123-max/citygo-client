package com.example.citygo.skeleton.screens.amap

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest

/**
 * 高德 Key / 包名 / 签名 SHA-256 诊断工具。
 *
 * 真机 release 包上地图或算路异常（鉴权失败 7/INVALID_USER_KEY/SIGNATURE_NOT_MATCH 等）时，
 * 通过 `adb logcat -s CityGoNavi` 可直接核对：
 *  1) AndroidManifest 中 com.amap.api.v2.apikey 是否成功注入（非空、非 ${...} 占位）；
 *  2) 当前包名是否与高德后台绑定包名一致；
 *  3) 当前签名证书的 SHA-256 是否与高德后台配置一致（debug/release 签名不同需分别配置）。
 */
object AmapKeyDiagnostics {

    private const val TAG = "CityGoNavi"
    private const val META_KEY = "com.amap.api.v2.apikey"

    /** 打印一次 Key / 包名 / SHA-256；重复调用安全（仅日志，无副作用）。 */
    fun logDiagnostics(context: Context) {
        runCatching {
            val app = context.applicationContext
            val packageName = app.packageName
            val appInfo = app.packageManager.getApplicationInfo(
                packageName,
                PackageManager.GET_META_DATA,
            )
            val apiKey = appInfo.metaData?.getString(META_KEY).orEmpty()
            val keyMasked = when {
                apiKey.isBlank() -> "<EMPTY>"
                apiKey.startsWith("$") -> "<NOT_INJECTED: $apiKey>"
                apiKey.length <= 8 -> apiKey
                else -> apiKey.take(4) + "****" + apiKey.takeLast(4)
            }
            Log.i(
                TAG,
                "[key-diag] package=$packageName apikey=$keyMasked(len=${apiKey.length})",
            )
            getSignatureSha256List(app).forEachIndexed { i, sha ->
                Log.i(TAG, "[key-diag] signature[$i] SHA-256=$sha")
            }
        }.onFailure {
            Log.e(TAG, "[key-diag] read package/meta-data/signature failed", it)
        }
    }

    /** 兼容新旧版本获取签名证书 SHA-256（hex 冒号分隔，与高德控制台格式一致）。 */
    private fun getSignatureSha256List(context: android.content.Context): List<String> {
        val pm = context.packageManager
        val sigBytes: List<ByteArray> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
            val signingInfo = info.signingInfo ?: return emptyList()
            val signatures = when {
                signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners
                else -> signingInfo.signingCertificateHistory
            }
            signatures.map { it.toByteArray() }
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                .signatures.orEmpty().map { it.toByteArray() }
        }
        val md = MessageDigest.getInstance("SHA-256")
        return sigBytes.map { bytes ->
            md.digest(bytes).joinToString(":") { b -> "%02X".format(b) }
        }
    }
}
