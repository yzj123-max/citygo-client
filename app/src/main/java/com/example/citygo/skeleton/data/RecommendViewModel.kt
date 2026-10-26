package com.example.citygo.skeleton.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.citygo.skeleton.data.datastore.AccountSession
import com.example.citygo.skeleton.data.model.ALL_WEATHER_CITY_ENTRIES
import com.example.citygo.skeleton.data.model.ScenicSpot
import com.example.citygo.skeleton.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class RecommendUiState(val loading: Boolean = false, val cities: List<String> = listOf(CITY_ALL),
    val currentCity: String = CITY_ALL, val spots: List<ScenicSpot> = emptyList(), val errorMessage: String? = null,
    val loadingMore: Boolean = false, val hasMore: Boolean = false, val source: String = "LOCAL",
    val total: Long? = null, val categoryCode: String? = null, val ticketStatus: String? = null,
    val petPolicy: String? = null, val cityQuery: String? = null)
data class SpotSearchUiState(val keyword: String = "", val loading: Boolean = false, val searched: Boolean = false,
    val results: List<ScenicSpot> = emptyList(), val suggestions: List<String> = emptyList(),
    val errorMessage: String? = null, val loadingMore: Boolean = false, val hasMore: Boolean = false)
data class SpotDetailLoadState(val loading: Boolean = false, val error: String? = null)

class RecommendViewModel(private val spotRepository: BackendSpotRepository = RepositoryContainer.spotRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(RecommendUiState())
    val uiState = _uiState.asStateFlow()
    private val _searchState = MutableStateFlow(SpotSearchUiState())
    val searchState = _searchState.asStateFlow()
    private val _spotMap = MutableStateFlow<Map<String, ScenicSpot>>(emptyMap())
    val spotMap = _spotMap.asStateFlow()
    private val _detailLoads = MutableStateFlow<Map<String, SpotDetailLoadState>>(emptyMap())
    val detailLoads = _detailLoads.asStateFlow()
    private var accountScope: CoroutineScope? = null
    private var cityJob: Job? = null
    private var searchJob: Job? = null
    private val detailJobs = mutableMapOf<String, Job>()
    private val photoJobs = mutableMapOf<String, Job>()
    private val photoPermits = Semaphore(2)
    private var feedPage = 0
    private var searchPage = 0
    private var submittedKeyword = ""
    private var nearbyCenter: Pair<Double, Double>? = null

    init {
        viewModelScope.launch {
            AccountSession.currentAccount.map { it?.token }.distinctUntilChanged().collectLatest { token ->
                coroutineScope {
                    accountScope = this
                    _spotMap.value = emptyMap()
                    _detailLoads.value = emptyMap()
                    _searchState.value = SpotSearchUiState()
                    _uiState.value = RecommendUiState(cities = listOf(CITY_ALL) + spotRepository.getCities())
                    nearbyCenter = null
                    feedPage = 0
                    searchPage = 0
                    detailJobs.clear()
                    photoJobs.clear()
                    if (!token.isNullOrBlank()) refresh()
                    awaitCancellation()
                }
            }
        }
    }
    fun selectCity(city: String) {
        _uiState.update { it.copy(currentCity = city, source = "LOCAL", cityQuery = null) }
        refresh()
    }
    fun setFilters(category: String?, ticket: String?, pet: String?) {
        _uiState.update { it.copy(categoryCode = category, ticketStatus = ticket, petPolicy = pet,
            source = "LOCAL", cityQuery = null) }
        refresh()
    }
    fun showNearby(longitude: Double, latitude: Double) {
        nearbyCenter = longitude to latitude
        _uiState.update { it.copy(source = "NEARBY", categoryCode = null, ticketStatus = null, petPolicy = null) }
        refresh()
    }
    fun refresh() = loadFeed(reset = true)
    fun loadMore() = loadFeed(reset = false)
    private fun loadFeed(reset: Boolean) {
        val scope = accountScope ?: return
        val state = _uiState.value
        if (!reset && (state.loading || state.loadingMore || !state.hasMore)) return
        cityJob?.cancel()
        val next = if (reset) 1 else feedPage + 1
        _uiState.update { it.copy(loading = reset, loadingMore = !reset, errorMessage = null,
            spots = if (reset) emptyList() else it.spots) }
        cityJob = scope.launch {
            try {
                val result = if (state.source == "NEARBY") {
                    val center = nearbyCenter ?: throw IllegalStateException("请先获取当前位置")
                    spotRepository.nearby(center.first, center.second, page = next)
                } else spotRepository.recommendations(state.currentCity, next, state.categoryCode,
                    state.ticketStatus, state.petPolicy, if (reset) null else state.source,
                    if (reset) null else state.cityQuery)
                index(result.spots)
                feedPage = result.nextPage?.minus(1) ?: next
                _uiState.update { it.copy(loading = false, loadingMore = false,
                    spots = (if (reset) result.spots else it.spots + result.spots).distinctBy(ScenicSpot::spotId),
                    hasMore = result.hasMore, source = result.source, total = result.total,
                    cityQuery = result.cityQuery, errorMessage = null) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.update { it.copy(loading = false, loadingMore = false, errorMessage = friendlyError(e)) } }
        }
    }
    fun ensureSpotLoaded(spotId: String) {
        val scope = accountScope ?: return
        detailJobs.remove(spotId)?.cancel()
        _detailLoads.update { it + (spotId to SpotDetailLoadState(loading = true)) }
        detailJobs[spotId] = scope.launch {
            try {
                val loaded = spotRepository.getSpotById(spotId)
                val previous = _spotMap.value[spotId]
                val spot = if (loaded.coverImg.isBlank() && !previous?.coverImg.isNullOrBlank())
                    loaded.copy(coverImg = previous!!.coverImg, imgUrls = loaded.imgUrls.ifEmpty { previous.imgUrls }) else loaded
                _spotMap.update { it + (spotId to spot) + (spot.spotId to spot) }
                _uiState.update { it.copy(spots = replace(it.spots, spotId, spot)) }
                _searchState.update { it.copy(results = replace(it.results, spotId, spot)) }
                _detailLoads.update { it + (spotId to SpotDetailLoadState()) }
                ensurePhoto(spotId)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _spotMap.update { it - spotId }
                _detailLoads.update { it + (spotId to SpotDetailLoadState(error = friendlyError(e))) }
            }
        }
    }
    private fun replace(list: List<ScenicSpot>, requestedId: String, spot: ScenicSpot) = list.map {
        if (it.spotId == requestedId || it.spotId == spot.spotId ||
            (!spot.amapPoiId.isNullOrBlank() && it.amapPoiId == spot.amapPoiId)) spot else it
    }.distinctBy(ScenicSpot::spotId)
    private fun index(spots: List<ScenicSpot>) { _spotMap.update { it + spots.associateBy(ScenicSpot::spotId) } }
    /** 仅对可见卡片补图，限制并发；不会因为缺图阻塞详情正文。 */
    fun ensurePhoto(id: String) {
        val scope = accountScope ?: return
        val spot = _spotMap.value[id] ?: return
        if (spot.coverImg.isNotBlank() || photoJobs.containsKey(id)) return
        photoJobs[id] = scope.launch {
            try {
                val enriched = photoPermits.withPermit { spotRepository.findPhotos(spot) }
                if (enriched.coverImg.isBlank()) return@launch
                fun merge(current: ScenicSpot) = current.copy(coverImg = enriched.coverImg,
                    imgUrls = current.imgUrls.ifEmpty { enriched.imgUrls })
                _spotMap.update { entries -> entries.mapValues { (_, current) -> if (current.spotId == spot.spotId) merge(current) else current } }
                _uiState.update { it.copy(spots = it.spots.map { current -> if (current.spotId == spot.spotId) merge(current) else current }) }
                _searchState.update { it.copy(results = it.results.map { current -> if (current.spotId == spot.spotId) merge(current) else current }) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* 缺图或图片查询失败不影响景点资料。 */ }
        }
    }
    fun matchCityKeyword(rawKeyword: String): String? {
        val keyword = rawKeyword.trim().removeSuffix("市")
        ALL_WEATHER_CITY_ENTRIES.firstOrNull { it.name == keyword }?.let { return it.name }
        if (keyword.length >= 2 && keyword.all { it.isLetter() && it.code < 128 }) {
            val matches = ALL_WEATHER_CITY_ENTRIES.filter { entry -> entry.namePinyin.any { it == keyword.lowercase() } }
            if (matches.size == 1) return matches.first().name
        }
        return null
    }
    fun onSearchInputChanged(keyword: String) {
        val suggestions = if (keyword.isBlank()) emptyList() else _spotMap.value.values
            .map(ScenicSpot::name).distinct().filter { it.contains(keyword.trim()) }.take(6)
        _searchState.update { it.copy(keyword = keyword, suggestions = suggestions) }
    }
    fun submitSearch(rawKeyword: String) {
        val keyword = rawKeyword.trim()
        if (keyword.isBlank()) return
        submittedKeyword = keyword
        searchPage = 0
        _searchState.value = SpotSearchUiState(keyword = keyword, searched = true)
        loadSearch(reset = true)
    }
    fun loadMoreSearch() = loadSearch(reset = false)
    private fun loadSearch(reset: Boolean) {
        val scope = accountScope ?: return
        val state = _searchState.value
        if (!reset && (state.loading || state.loadingMore || !state.hasMore)) return
        searchJob?.cancel()
        val next = if (reset) 1 else searchPage + 1
        _searchState.update { it.copy(loading = reset, loadingMore = !reset, errorMessage = null, suggestions = emptyList()) }
        searchJob = scope.launch {
            try {
                val result = spotRepository.searchPage(submittedKeyword, next)
                index(result.spots)
                searchPage = next
                _searchState.update { it.copy(loading = false, loadingMore = false,
                    results = (if (reset) result.spots else it.results + result.spots).distinctBy(ScenicSpot::spotId),
                    hasMore = result.hasMore, errorMessage = null) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _searchState.update { it.copy(loading = false, loadingMore = false, errorMessage = friendlyError(e)) } }
        }
    }
    fun retrySearch() = submitSearch(submittedKeyword.ifBlank { _searchState.value.keyword })
    fun applySuggestion(name: String) = submitSearch(name)
    fun resetSearch() { searchJob?.cancel(); _searchState.value = SpotSearchUiState() }
    suspend fun navigationTarget(id: String, mode: String) = spotRepository.navigation(id, mode)
}

private fun friendlyError(e: Exception): String = when (e) {
    is AttractionRequestException -> e.message ?: "服务暂不可用，请重试"
    is java.net.SocketTimeoutException -> "服务响应超时，请稍后重试"
    is java.io.IOException -> "连接失败，请确认后端运行及USB端口转发"
    else -> e.message ?: "景点加载失败，请重试"
}
