package com.example.citygo.skeleton.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.citygo.skeleton.data.model.DEFAULT_WEATHER_CITY
import com.example.citygo.skeleton.data.model.WeatherCity
import com.example.citygo.skeleton.data.model.WeatherOverview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 天气预报页面状态（计划 Tab「天气预报」子页 + 单日天气详情页订阅）。
 *
 * @property city 当前查询城市；切换城市时页面全部模块数据一次性刷新
 * @property loading 是否加载中（真实 API 请求期间展示加载占位）
 * @property overview 全量天气数据；null 表示尚未加载完成
 * @property errorMessage 加载失败提示；null 表示无错误
 */
data class WeatherUiState(
    val city: WeatherCity = DEFAULT_WEATHER_CITY,
    val loading: Boolean = false,
    val overview: WeatherOverview? = null,
    val errorMessage: String? = null,
)

/**
 * 天气 ViewModel：通过 [com.example.citygo.skeleton.data.repository.WeatherRepository]
 * 获取天气页全量数据，UI 不直接调用仓库。
 */
class WeatherViewModel : ViewModel() {

    private val repository = RepositoryContainer.weatherRepository

    private val _uiState = MutableStateFlow(WeatherUiState(loading = true))
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    init {
        // 默认加载长沙天气（与调试模拟 JSON 一致）
        loadOverview(DEFAULT_WEATHER_CITY)
    }

    /**
     * 拉取指定城市的全量天气数据（头部实时 + 7 日预报 + 各指标卡片 + 生活提示）。
     * 切换城市调用本方法，页面全部模块随返回结果一次性刷新。
     */
    fun loadOverview(city: WeatherCity) {
        _uiState.update { it.copy(city = city, loading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.getWeatherOverview(city) }
                .onSuccess { overview ->
                    _uiState.update {
                        it.copy(loading = false, overview = overview, errorMessage = null)
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(loading = false, errorMessage = e.message ?: "天气加载失败")
                    }
                }
        }
    }

    /** 切换城市（页面头部城市下拉调用） */
    fun switchCity(city: WeatherCity) {
        if (city.adCode == _uiState.value.city.adCode) return
        loadOverview(city)
    }

    /** 使用当前城市重新加载（网络失败页点击重试） */
    fun retry() {
        loadOverview(_uiState.value.city)
    }
}
