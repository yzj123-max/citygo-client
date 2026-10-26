package com.example.citygo.skeleton.screens.common

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.citygo.R

/**
 * 头像图片选择统一入口（初始资料设置页 / 编辑资料页共用）：
 *
 * - **Android 13+（API >= 33，TIRAMISU）**：优先启动系统 **Photo Picker**
 *   （PickVisualMedia / ImageOnly）。它是系统沙盒选择器，通过临时 Uri 授权访问所选图片，
 *   **不需要任何存储权限，也不弹权限框**；
 * - **Android 12 及更低版本**：点击头像先**动态申请运行时存储权限**，
 *   用户在系统弹窗中授权后才打开相册；拒绝则吐司友好引导，不静默失败。
 *
 * 权限名称必须按系统版本区分（Manifest 中两者都已声明）：
 * - READ_MEDIA_IMAGES 是 **Android 13（API33）才新增**的权限，在低版本系统上不存在，
 *   低版本去申请它会被系统直接判为拒绝、永远不会弹授权框；
 * - 因此低版本正确的运行时权限是 READ_EXTERNAL_STORAGE（Manifest 已加 maxSdkVersion=32）。
 *
 * @param onImagePicked 选中图片后的回调，参数为系统返回的临时授权可读 content:// Uri
 * @return 绑定到头像点击事件的无参 lambda，点击即按上述规则分流
 */
@Composable
fun rememberAvatarImagePicker(onImagePicked: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current

    // ① API 33+：系统 Photo Picker（PickVisualMedia）——沙盒选择器，不需要存储权限
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onImagePicked(uri)
    }

    // ② API < 33：系统相册（ACTION_GET_CONTENT，返回临时读授权的 content:// Uri）
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) onImagePicked(uri)
    }

    // ③ API < 33：运行时存储权限申请，授权结果决定是否继续打开相册
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            galleryLauncher.launch("image/*")
        } else {
            Toast
                .makeText(
                    context,
                    R.string.avatar_permission_denied,
                    Toast.LENGTH_LONG,
                ).show()
        }
    }

    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+：优先 Photo Picker，不弹权限框
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        } else {
            // Android 12 及以下：READ_MEDIA_IMAGES 在该系统不存在，
            // 必须申请 READ_EXTERNAL_STORAGE；已授予直接开相册，未授予弹系统授权框
            val permission = Manifest.permission.READ_EXTERNAL_STORAGE
            val alreadyGranted = ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
            if (alreadyGranted) {
                galleryLauncher.launch("image/*")
            } else {
                permissionLauncher.launch(permission)
            }
        }
    }
}
