package com.example.citygo.skeleton.screens.amap

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** Android 12+ 的精确定位必须与粗略定位在同一次请求中申请。 */
fun navigationLocationPermissions(): Array<String> = arrayOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION,
)

fun hasPreciseLocationPermission(context: Context): Boolean =
    navigationLocationPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
