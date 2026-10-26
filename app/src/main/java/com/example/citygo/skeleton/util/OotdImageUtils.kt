package com.example.citygo.skeleton.util

// OOTD 图片处理工具类
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * OOTD 图片工具类（纯工具，不依赖任何 Compose / Activity 代码，与 UI 页面完全分离）。
 *
 * 职责：
 * 1. 相机拍照输出 Uri（FileProvider 临时授权）；
 * 2. 相册 / 拍照得到的 content:// 图片 → 等比采样压缩 → JPEG [ByteArray]；
 * 3. 读取 EXIF 方向并自动旋正（部分机型相机拍照自带旋转角，不处理会横图）；
 * 4. 相机 / 存储权限判断（权限弹窗本身由页面的 Activity Result API 发起）；
 * 5. 关键节点全部打 Logcat（TAG=[OotdImage]），方便 USB 真机联调过滤日志。
 *
 * 压缩策略（毕设链路：手机原图通常 3-8MB，后端 + TOS + 大模型无需超高分辨率）：
 * - 先 inJustDecodeBounds 只读尺寸，按 [MAX_IMAGE_EDGE] 计算 inSampleSize 降采样；
 * - 再按短边目标二次微调，最后 JPEG [JPEG_QUALITY] 质量压缩为字节数组。
 */
object OotdImageUtils {

    private const val TAG = "OotdImage"

    /** 压缩后图片最长边上限（px），超过则等比缩小 */
    private const val MAX_IMAGE_EDGE = 1280

    /** JPEG 压缩质量（0-100） */
    private const val JPEG_QUALITY = 82

    /** 相机 / 相册默认按 JPEG 上传 */
    const val MIME_JPEG = "image/jpeg"

    /** FileProvider authority，与 AndroidManifest 中 ${applicationId}.fileprovider 对应 */
    fun fileProviderAuthority(context: Context): String = "${context.packageName}.fileprovider"

    /* ============================== 权限判断 ============================== */

    /** 是否已授予相机权限 */
    fun hasCameraPermission(context: Context): Boolean =
        ContextCompatCheck.hasPermission(context, Manifest.permission.CAMERA)

    /**
     * 打开系统相册前是否需要存储权限：
     * Android 13+ 用系统 Photo Picker，不需要存储权限；低版本需 READ_EXTERNAL_STORAGE。
     */
    fun requiredGalleryPermission(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            null
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasGalleryPermission(context: Context): Boolean {
        val permission = requiredGalleryPermission() ?: return true
        return ContextCompatCheck.hasPermission(context, permission)
    }

    /** 小封装：统一走 ContextCompat.checkSelfPermission，便于本类内调用 */
    private object ContextCompatCheck {
        fun hasPermission(context: Context, permission: String): Boolean =
            androidx.core.content.ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
    }

    /* ============================== 相机输出文件 ============================== */

    /**
     * 创建本次拍照的输出文件，并返回经 FileProvider 授权的 content:// Uri。
     * 页面把该 Uri 传给 ActivityResultContracts.TakePicture，相机拍照结果直接写入此文件。
     */
    fun createCameraOutputUri(context: Context): Uri {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "ootd").apply {
            if (!exists()) mkdirs()
        }
        val file = File(dir, "ootd_${System.currentTimeMillis()}.jpg")
        Log.i(TAG, "createCameraOutputUri: ${file.absolutePath}")
        return FileProvider.getUriForFile(context, fileProviderAuthority(context), file)
    }

    /* ============================== 图片压缩 ============================== */

    /**
     * 读取 [uri] 指向的图片并压缩为 JPEG 字节数组。
     *
     * @param context 用于 contentResolver 读取临时授权 Uri
     * @param uri 相册 / 拍照返回的 content:// Uri
     * @return 压缩后的 JPEG 字节数组，可直接放入 multipart 上传
     * @throws IOException 读取 / 解码失败时抛出，由上层转成「图片处理失败」友好提示
     */
    @Throws(IOException::class)
    fun compressToBytes(context: Context, uri: Uri): ByteArray {
        val startMs = System.currentTimeMillis()
        val resolver = context.contentResolver

        val mime = resolver.getType(uri) ?: MIME_JPEG
        Log.i(TAG, "compress start: uri=$uri mime=$mime")

        // ① 只读边界，拿到原图宽高（不加载像素进内存）
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) {
            Log.e(TAG, "compress failed: cannot decode image bounds ($srcW x $srcH)")
            throw IOException("无法读取图片，请更换图片重试")
        }

        // ② 计算采样率：保证降采样后最长边仍 >= 目标值（再交给后续精确缩放）
        var sampleSize = 1
        var longestEdge = maxOf(srcW, srcH)
        while (longestEdge / 2 >= MAX_IMAGE_EDGE) {
            longestEdge /= 2
            sampleSize *= 2
        }

        // ③ 真正按采样率解码
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: throw IOException("图片解码失败，请更换图片重试")

        Log.i(
            TAG,
            "decode: origin=${srcW}x$srcH sampleSize=$sampleSize " +
                "decoded=${decoded.width}x${decoded.height}",
        )

        // ④ 按 EXIF 旋正 + 精确缩放到最长边上限
        val rotation = readExifRotation(context, uri)
        var bitmap = decoded
        if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            bitmap = rotated
            Log.i(TAG, "exif rotation applied: $rotation°")
        }
        bitmap = scaleDownIfNeeded(bitmap)

        // ⑤ JPEG 压缩输出
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        bitmap.recycle()

        val bytes = output.toByteArray()
        Log.i(
            TAG,
            "compress done: final=${bytes.size / 1024}KB cost=${System.currentTimeMillis() - startMs}ms",
        )
        return bytes
    }

    /** 最长边超过 [MAX_IMAGE_EDGE] 时等比缩小；否则原样返回 */
    private fun scaleDownIfNeeded(source: Bitmap): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= MAX_IMAGE_EDGE) return source
        val ratio = MAX_IMAGE_EDGE.toFloat() / longest
        val targetW = (source.width * ratio).toInt().coerceAtLeast(1)
        val targetH = (source.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, targetW, targetH, true)
        if (scaled != source) source.recycle()
        Log.i(TAG, "scaleDown: -> ${targetW}x$targetH")
        return scaled
    }

    /** 读取 EXIF 方向角（0/90/180/270）；android.media.ExifInterface(InputStream) minSdk24 可用 */
    private fun readExifRotation(context: Context, uri: Uri): Int =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                when (ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrElse {
            Log.w(TAG, "readExifRotation failed: ${it.message}")
            0
        }
}
