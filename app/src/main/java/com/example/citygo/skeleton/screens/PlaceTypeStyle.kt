package com.example.citygo.skeleton.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.citygo.skeleton.data.model.PlaceType

/* =====================================================================================
 * PlaceType 的 UI 表现样式（Marker / 标签的 emoji 与配色）
 *
 * 数据层枚举 [PlaceType] 只含中文 label，不依赖 Compose；
 * 颜色与 emoji 属于表现层，统一在此扩展，供地图页、出行计划页复用。
 * ===================================================================================== */

/** 类型符号（Marker 圆点内的 emoji；接入高德后可替换为对应 BitmapDescriptor） */
internal val PlaceType.emoji: String
    get() = when (this) {
        PlaceType.HOTEL -> "🏨"
        PlaceType.SHOP -> "🛍️"
        PlaceType.RESTAURANT -> "🍜"
        PlaceType.SPOT -> "🏛️"
    }

/** 类型主色（Marker 底色、标签色），与清新旅游主题色板协调 */
internal val PlaceType.color: Color
    get() = when (this) {
        PlaceType.HOTEL -> Color(0xFF4A7CF7)
        PlaceType.SHOP -> Color(0xFFE0569B)
        PlaceType.RESTAURANT -> Color(0xFFEF7C47)
        PlaceType.SPOT -> Color(0xFF2F9E6E)
    }

/** 同色的 Android ARGB int（高德原生 Marker Bitmap 绘制用） */
internal val PlaceType.colorArgb: Int
    get() = color.toArgb()

/**
 * 行程卡片上的严格类型标签文案（四类，区别于数据层枚举的 label「酒店/探店/饭店/景点」）：
 * 景点 / 餐饮 / 住宿 / 购物。地图 Marker 与卡片都据此严格区分，不能全部标成景点。
 */
internal val PlaceType.tagLabel: String
    get() = when (this) {
        PlaceType.HOTEL -> "住宿"
        PlaceType.SHOP -> "购物"
        PlaceType.RESTAURANT -> "餐饮"
        PlaceType.SPOT -> "景点"
    }
