package com.example.citygo.skeleton.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/* =====================================================================================
 * 「我的」页 ViewModel（Activity 作用域单例，在 MainActivity 创建并下传）
 *
 * 统一维护「我的」页本地状态：
 * - 当前城市 / 最近访问城市（左上角全局 CitySelector，切换城市不影响用户资料）；
 * - 登录态 / 游客模式、Mock 用户信息；
 * - Mock 缓存大小（「清除缓存」后归零）。
 *
 * 注意：深色主题已迁移到 skeleton.data.AppViewModel（DataStore 持久化）；
 * 宠物 / 收藏 / 浏览记录 / 日记等业务数据统一订阅全局 Room ViewModel。
 * ===================================================================================== */

/**
 * 全局 UI 状态。
 */
data class AppUiState(
    /** 当前选择的目标城市 */
    val currentCity: CityInfo = mockDefaultCity,
    /** 最近访问城市（去重、最多 6 个，最新切换的排最前） */
    val recentCities: List<CityInfo> = mockRecentCities,
    /** 是否游客模式：游客显示「游客用户」，并隐藏收藏相关入口 */
    val isGuest: Boolean = false,
    /** Mock 登录用户信息（游客模式下不展示） */
    val user: AppUser = MOCK_LOGIN_USER,
    /** Mock 缓存大小（MB），「清除缓存」后归零 */
    val cacheSizeMb: Float = MOCK_CACHE_SIZE_MB,
    /** 切换城市时的全局刷新态：驱动短时加载提示，模拟数据重新拉取 */
    val isCityRefreshing: Boolean = false,
)

/** Mock 初始缓存大小（MB） */
private const val MOCK_CACHE_SIZE_MB = 38.6f

/** 最近访问城市上限 */
private const val MAX_RECENT_CITIES = 6

/** 切城市刷新遮罩展示时长（毫秒） */
private const val CITY_REFRESH_DELAY_MS = 600L

class AppViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    /* ---------------------------------- 城市 ---------------------------------- */

    /**
     * 切换目标城市：
     * 1. 更新当前城市与最近访问列表；
     * 2. 用户资料等状态原样保留（切城市不修改个人资料）；
     * 3. 短暂打开刷新态，模拟各页面按城市重新加载。
     */
    fun selectCity(city: CityInfo) {
        val current = _uiState.value
        if (current.currentCity.id == city.id) return
        _uiState.update { state ->
            state.copy(
                currentCity = city,
                recentCities = (listOf(city) + state.recentCities.filterNot { it.id == city.id })
                    .take(MAX_RECENT_CITIES),
                isCityRefreshing = true,
            )
        }
        viewModelScope.launch {
            delay(CITY_REFRESH_DELAY_MS)
            _uiState.update { it.copy(isCityRefreshing = false) }
        }
    }

    /* ---------------------------------- 账号 ---------------------------------- */

    /** 进入游客模式：仅可浏览，个人中心显示「游客用户」，收藏入口隐藏 */
    fun enterGuestMode() {
        _uiState.update { it.copy(isGuest = true) }
    }

    /** 切换回 Mock 登录账号（城市等资料保持不变） */
    fun loginMockAccount() {
        _uiState.update { it.copy(isGuest = false, user = MOCK_LOGIN_USER) }
    }

    /* ---------------------------------- 缓存 ---------------------------------- */

    /** 清除缓存（Mock：缓存大小归零；再次模拟使用后可重新累积） */
    fun clearCache() {
        _uiState.update { it.copy(cacheSizeMb = 0f) }
    }
}
