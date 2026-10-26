package com.example.citygo.skeleton.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/* =====================================================================================
 * 简单配置项 DataStore 工具（Preferences DataStore）
 *
 * 只存轻量键值配置（不适合放 Room 的数据）：
 * - 深色模式开关；
 * - 当前选中城市。
 *
 * 文件：citygo_settings.preferences_pb，由顶层 [Context.dataStore] 委托保证
 * 整个 App 单例（Preferences DataStore 强制同名单例，重复创建会崩溃）。
 * 与高德导航 / 天气预报无任何耦合。
 * ===================================================================================== */

/** Application 级 Preferences DataStore 单例（文件名 citygo_settings） */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "citygo_settings")

/**
 * 配置读写工具：对外只暴露业务需要的两个配置，
 * 屏蔽 Preferences Key 细节，ViewModel 不直接接触 DataStore API。
 */
class SettingsDataStore(private val context: Context, accountKey: String) {

    private val currentCityKey = stringPreferencesKey("current_city_$accountKey")

    /** 深色模式开关流：未设置过（首次安装）时默认 false=浅色 */
    val darkModeFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_DARK_MODE] ?: false }

    /** 当前选中城市流：未设置过时发射 null，由调用方决定默认城市 */
    val currentCityFlow: Flow<String?> =
        context.dataStore.data.map { prefs -> prefs[currentCityKey]?.takeIf { it.isNotBlank() } }

    /** 写入深色模式开关（DataStore 保证原子写盘，App 重启后保留） */
    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_DARK_MODE] = enabled }
    }

    /** 写入当前选中城市（空字符串不写入，避免脏值） */
    suspend fun setCurrentCity(city: String) {
        val name = city.trim()
        if (name.isBlank()) return
        context.dataStore.edit { prefs -> prefs[currentCityKey] = name }
    }

    private companion object {
        val KEY_DARK_MODE = booleanPreferencesKey("dark_mode_enabled")
    }
}
