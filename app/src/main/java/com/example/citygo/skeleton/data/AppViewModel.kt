package com.example.citygo.skeleton.data

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.citygo.skeleton.data.datastore.SettingsDataStore
import com.example.citygo.skeleton.data.datastore.AccountStorage
import com.example.citygo.skeleton.data.datastore.AccountSession
import com.example.citygo.skeleton.data.model.DayPlan
import com.example.citygo.skeleton.data.model.AiPlanOptions
import com.example.citygo.skeleton.data.model.AiSpot
import com.example.citygo.skeleton.data.model.AiSpotSource
import com.example.citygo.skeleton.data.model.AiSpotType
import com.example.citygo.skeleton.data.model.AiTravelPlan
import com.example.citygo.skeleton.data.model.Diary
import com.example.citygo.skeleton.data.model.EMPTY_TRAVEL_PLAN
import com.example.citygo.skeleton.data.model.Pet
import com.example.citygo.skeleton.data.model.Place
import com.example.citygo.skeleton.data.model.PlaceType
import com.example.citygo.skeleton.data.model.Post
import com.example.citygo.skeleton.data.model.Spot
import com.example.citygo.skeleton.data.model.TravelPlan
import com.example.citygo.skeleton.data.model.TripPlan
import com.example.citygo.skeleton.data.model.chat.ChatMessage
import com.example.citygo.skeleton.data.model.chat.ProposalStatus
import com.example.citygo.skeleton.data.local.app.AppDatabase
import com.example.citygo.skeleton.data.repository.Repository
import com.example.citygo.skeleton.data.sync.TripSyncCoordinator
import com.example.citygo.skeleton.data.sync.RemoteTripCloud
import com.example.citygo.skeleton.data.sync.TripSyncUiState
import com.example.citygo.skeleton.data.repository.ai.AiGuideParser
import com.example.citygo.skeleton.data.repository.ai.GeoItem
import com.example.citygo.skeleton.data.repository.ai.GeoRoutePlanner
import com.example.citygo.skeleton.data.repository.ai.GuideSpotGeocodeImporter
import com.example.citygo.skeleton.data.repository.agent.AgentChatRequest
import com.example.citygo.skeleton.data.repository.agent.AgentException
import com.example.citygo.skeleton.data.repository.agent.AgentRepository
import com.example.citygo.skeleton.data.repository.chat.ChatHistoryRepository
import com.example.citygo.skeleton.data.repository.hotel.HotelPoi
import com.example.citygo.skeleton.data.repository.hotel.HotelRepository
import com.example.citygo.skeleton.data.repository.image.ImageRepository
import com.example.citygo.skeleton.data.remote.AmapGeocoder
import com.example.citygo.skeleton.data.remote.ServerConfig
import com.example.citygo.skeleton.data.remote.travel.GenerateTaskRequest
import com.example.citygo.skeleton.data.remote.travel.TravelRetrofit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import retrofit2.HttpException

/* =====================================================================================
 * 全局共享 AppViewModel（Activity 作用域单例，跨底部 5 个 Tab 共享同一份状态）
 *
 * 数据策略（内存 StateFlow + Room 持久化双写）：
 * - init 时通过构造注入的 [Repository] 订阅全部本地数据，Room 数据自动回填到
 *   StateFlow，App 重启后状态不丢失；
 * - 每个业务写方法都遵循「①先同步更新内存 StateFlow（UI 立刻响应）
 *   ②再调用 Repository 写入 Room」的顺序；Room 写入成功后其 Flow 会再次发射
 *   库内权威数据，与内存状态收敛一致（值相同不会触发重组）。
 *
 * ViewModel 只依赖 [Repository] 接口，不直接接触 Room DAO / 数据库；
 * 不包含任何高德导航、天气预报逻辑。
 * ===================================================================================== */

class AppViewModel(
    private val repository: Repository,
    private val settingsDataStore: SettingsDataStore,
    private val imageRepository: ImageRepository,
    private val agentRepository: AgentRepository,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val hotelRepository: HotelRepository,
    private val tripSync: TripSyncCoordinator? = null,
) : ViewModel() {

    /** 把 Repository 的 7 路本地数据流全部接入内部 StateFlow */
    private fun observeLocalData() {
        // 单路 Flow 收集异常不影响其他数据流：捕获后记录日志，避免某一路崩溃导致整个状态订阅终止
        viewModelScope.launch {
            runCatching { repository.observePets().collect { _petList.value = it } }
                .onFailure { Log.w(TAG, "订阅宠物数据失败", it) }
        }
        viewModelScope.launch {
            // 库里没有行程（null）时对外呈现为空行程 EMPTY_TRAVEL_PLAN
            runCatching {
                repository.observeCurrentTravelPlan().collect { plan ->
                    _currentTravelPlan.value = plan ?: EMPTY_TRAVEL_PLAN
                }
            }.onFailure { Log.w(TAG, "订阅行程数据失败", it) }
        }
        viewModelScope.launch {
            runCatching { repository.observeCollectedSpots().collect { _collectSpotList.value = it } }
                .onFailure { Log.w(TAG, "订阅收藏景点失败", it) }
        }
        viewModelScope.launch {
            runCatching { repository.observeCollectedPosts().collect { _collectPostList.value = it } }
                .onFailure { Log.w(TAG, "订阅收藏帖子失败", it) }
        }
        viewModelScope.launch {
            runCatching { repository.observeBrowseHistory().collect { _browseHistoryList.value = it } }
                .onFailure { Log.w(TAG, "订阅浏览记录失败", it) }
        }
        viewModelScope.launch {
            runCatching { repository.observeDiaries().collect { _diaryList.value = it } }
                .onFailure { Log.w(TAG, "订阅旅行日记失败", it) }
        }
        viewModelScope.launch {
            runCatching { repository.observePosts().collect { _communityPostList.value = it } }
                .onFailure { Log.w(TAG, "订阅社区帖子失败", it) }
        }
    }

    /** 订阅 DataStore 配置：磁盘值变化（含本类写回）即同步到内存 StateFlow */
    private fun observeSettings() {
        viewModelScope.launch {
            runCatching {
                settingsDataStore.darkModeFlow.collect { enabled ->
                    _isDarkMode.value = enabled
                }
            }.onFailure { Log.w(TAG, "读取深色模式配置失败", it) }
        }
        viewModelScope.launch {
            // 从未选择过城市时磁盘值为 null，保留默认城市
            runCatching {
                settingsDataStore.currentCityFlow.collect { city ->
                    if (!city.isNullOrBlank()) _currentCity.value = city
                }
            }.onFailure { Log.w(TAG, "读取当前城市配置失败", it) }
        }
    }

    /**
     * 持久化统一入口：内存状态已先更新，这里只负责异步落库；
     * 落库失败仅记录日志（不回滚 UI 的乐观更新，下次订阅 / 重启会与库内状态对齐）。
     */
    private fun persist(action: suspend Repository.() -> Unit) {
        viewModelScope.launch {
            runCatching { repository.action() }
                .onFailure { Log.w(TAG, "本地数据写入失败", it) }
        }
    }

    /* ---------------------------------- 简单配置 ---------------------------------- */

    private val _isDarkMode = MutableStateFlow(false)

    /** 深色模式开关（DataStore 持久化，重启恢复；驱动 MainActivity 的 CityGoTheme） */
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    /**
     * 切换深色 / 浅色（①内存先更新，主题即时切换 ②写入 DataStore，重启保留）。
     */
    fun setDarkMode(enabled: Boolean) {
        if (_isDarkMode.value == enabled) return
        _isDarkMode.value = enabled
        persist { settingsDataStore.setDarkMode(enabled) }
    }

    /* ---------------------------------- 当前城市 ---------------------------------- */

    private val _currentCity = MutableStateFlow(DEFAULT_CITY)

    /** 当前选中城市名（跨 Tab 共享；DataStore 持久化，重启后恢复上次选择） */
    val currentCity: StateFlow<String> = _currentCity.asStateFlow()

    /**
     * 切换当前城市（①内存先更新 ②写入 DataStore）。
     *
     * 城市真正发生变化时，同时清空旧城市的整套行程数据，满足「换城市重新规划」：
     * - 当前行程恢复 [EMPTY_TRAVEL_PLAN]（Room 主行删除、子项级联清除）；
     * - 旧地点封面图缓存、AI 助手对话记录一并清空，避免新城市界面残留旧城市内容。
     * （AI 攻略的城市缓存不受影响，下次生成各城市仍可离线命中各自缓存。）
     */
    fun selectCity(city: String) {
        val name = city.trim()
        if (name.isNotEmpty() && name != _currentCity.value) {
            _currentCity.value = name
            coverJob?.cancel()
            persist { settingsDataStore.setCurrentCity(name) }
            // —— 切换城市：清空全部旧行程 / 景点列表 / 封面 / 对话 ——
            _tripConfig.value = null
            _pendingAddDay.value = null
            _currentTravelPlan.value = EMPTY_TRAVEL_PLAN
            // 换目的地开启新草稿，旧行程及其对话留在历史列表；不要清空旧行程的聊天表。
            persist { startNewTravelPlan(TravelPlan(name, 0, emptyList(), emptyList())) }
            _placeCoverMap.value = emptyMap()
            _chatMessageList.value = emptyList()
        }
    }

    /* ---------------------------------- 当前行程 ---------------------------------- */

    private val _currentTravelPlan = MutableStateFlow(EMPTY_TRAVEL_PLAN)

    /** 当前整套行程（初始 / 清空后为 [EMPTY_TRAVEL_PLAN]） */
    val currentTravelPlan: StateFlow<TravelPlan> = _currentTravelPlan.asStateFlow()

    /**
     * 最近一次「行程配置」表单生效的参数（总天数 / 预算 / 游玩偏好 / 宠物开关）。
     *
     * 与 [_currentTravelPlan] 组合成对外唯一的 [tripPlan]；换城市 / 清空行程时重置为 null，
     * 保证旧城市配置不残留。手动「加入行程」不经过 AI，配置保持 null（预算 / 偏好视为未设置）。
     */
    private val _tripConfig = MutableStateFlow<AiPlanOptions?>(null)

    /**
     * 全局共享的出行计划 [TripPlan]：城市 + 天数 + 预算 + 偏好 + 宠物开关 + 按 dayIndex
     * 分好组的景点。出行计划页与地图页都只订阅这一个 StateFlow，因此两页数据必然一致。
     */
    val tripPlan: StateFlow<TripPlan> = combine(
        _currentTravelPlan,
        _currentCity,
        _tripConfig,
    ) { plan, city, cfg -> buildTripPlan(plan, city, cfg) }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = buildTripPlan(
            _currentTravelPlan.value,
            _currentCity.value,
            _tripConfig.value,
        ),
    )

    /** 持久化行程 [TravelPlan] + 配置 [AiPlanOptions] → 对外共享模型 [TripPlan] */
    private fun buildTripPlan(plan: TravelPlan, city: String, cfg: AiPlanOptions?): TripPlan {
        if (plan == EMPTY_TRAVEL_PLAN) return TripPlan.empty(city)
        return TripPlan(
            city = plan.destination.ifBlank { city },
            totalDays = plan.totalDays,
            budgetYuan = cfg?.budgetYuan,
            preferences = cfg?.preferences ?: emptyList(),
            petFriendly = cfg?.petFriendly ?: false,
            dailyPlans = plan.dailyPlans,
        )
    }

    /**
     * 计划页「为第 N 天加入景点」暂存的目标天（仅内存，不落库）：
     * 用户在某天卡片点「加入景点」后跳到推荐 / 详情页，[addSpotToPlan] 即把景点写入该天；
     * 消费一次后清空。null 表示默认追加到第 1 天（兼容景点详情页直接加入的旧入口）。
     */
    private val _pendingAddDay = MutableStateFlow<Int?>(null)
    val pendingAddDay: StateFlow<Int?> = _pendingAddDay.asStateFlow()

    /** 设置下一次「加入行程」要写入的天；传 null 恢复默认（第 1 天） */
    fun setPendingAddDay(day: Int?) {
        _pendingAddDay.value = day
    }

    /* ------------------------------ 按天添加酒店 ------------------------------ */

    /** 酒店选择弹窗的搜索状态（loading / 结果 / 空 / 错误均由 UI 据字段渲染） */
    data class HotelSearchState(
        val isLoading: Boolean = false,
        val keyword: String = "",
        val city: String = "",
        val hotels: List<HotelPoi> = emptyList(),
        val errorMessage: String? = null,
    )

    private val _hotelSearchState = MutableStateFlow(HotelSearchState())
    val hotelSearchState: StateFlow<HotelSearchState> = _hotelSearchState.asStateFlow()

    /**
     * 按城市 + 关键字搜索真实酒店 POI（供「为第 X 天添加酒店」弹窗）。
     * 城市取当前行程目的地，缺省回退当前选择城市；任何失败写入 errorMessage，不抛异常。
     */
    fun searchHotels(keyword: String) {
        val city = _currentTravelPlan.value.destination.ifBlank { _currentCity.value }
        val kw = keyword.trim()
        _hotelSearchState.value = HotelSearchState(isLoading = true, keyword = kw, city = city)
        viewModelScope.launch {
            val hotels = runCatching { hotelRepository.searchHotels(city, kw) }
                .getOrDefault(emptyList())
            _hotelSearchState.value = HotelSearchState(
                isLoading = false,
                keyword = kw,
                city = city,
                hotels = hotels,
                errorMessage = if (hotels.isEmpty()) "未在「$city」搜到相关酒店，换个商圈或酒店名试试" else null,
            )
        }
    }

    /** 重置酒店搜索状态（关闭弹窗时调用，避免下次打开闪现旧结果） */
    fun resetHotelSearch() {
        _hotelSearchState.value = HotelSearchState()
    }

    /**
     * 把选中的酒店加入指定天（①先移除该天已有酒店 ②追加新酒店到当天末尾 ③整份落库）。
     *
     * @return true 表示替换了该天原有酒店；false 表示当天原本无酒店（新增）。
     *         行程不存在 / 天数非法时返回 false 且不写入。
     */
    fun addHotelToPlan(day: Int, hotel: HotelPoi): Boolean {
        val plan = _currentTravelPlan.value
        if (plan == EMPTY_TRAVEL_PLAN || day !in 1..plan.totalDays) return false

        val replaced = plan.dailyPlans.any { dayPlan ->
            dayPlan.day == day && dayPlan.places.any { it.placeType == PlaceType.HOTEL }
        }
        val hotelPlace = Place(
            id = (plan.allPlaceList.maxOfOrNull { it.id } ?: 0L) + 1,
            name = hotel.name,
            placeType = PlaceType.HOTEL,
            dayIndex = day,
            lat = hotel.lat,
            lng = hotel.lng,
            intro = "住宿 · ${hotel.address}",
            petFriendly = false,
            coverUrl = hotel.coverUrl,
        )
        val newDaily = plan.dailyPlans.map { dayPlan ->
            if (dayPlan.day != day) {
                dayPlan
            } else {
                val withoutOldHotel = dayPlan.places.filterNot { it.placeType == PlaceType.HOTEL }
                dayPlan.copy(places = withoutOldHotel + hotelPlace)
            }
        }
        val newPlan = plan.copy(
            dailyPlans = newDaily,
            allPlaceList = newDaily.flatMap { it.places },
        )
        _currentTravelPlan.value = newPlan
        persist { saveTravelPlan(newPlan) }
        return replaced
    }

    /**
     * 景点加入行程（①内存先更新 ②整份行程落库）：
     * - 无行程时自动新建「1 日行程」，目的地取景点所在城市，缺省回退当前城市；
     * - 已有行程时追加到目标天（[pendingAddDay]，缺省第 1 天）末尾，allPlaceList 同步；
     * - 同名同坐标点位视为已加入，不重复添加；
     * - 加入成功后消费掉 [pendingAddDay]。
     *
     * 写入 [currentTravelPlan] 后，全局 [tripPlan] 自动重组，计划页 / 地图页同步刷新。
     */
    fun addSpotToPlan(spot: Spot) {
        val targetDay = (_pendingAddDay.value ?: 1).coerceAtLeast(1)
        val newPlan = buildPlanWithSpot(_currentTravelPlan.value, spot, targetDay) ?: return
        _currentTravelPlan.value = newPlan
        _pendingAddDay.value = null
        persist { saveTravelPlan(newPlan) }
    }

    /** 由旧行程 + 新景点计算下一份行程；重复点位返回 null；[targetDay] 为要加入的天（从 1 起） */
    private fun buildPlanWithSpot(plan: TravelPlan, spot: Spot, targetDay: Int): TravelPlan? {
        val duplicated = plan.allPlaceList.any {
            it.name == spot.name && it.lat == spot.lat && it.lng == spot.lng
        }
        if (duplicated) return null

        val place = Place(
            id = (plan.allPlaceList.maxOfOrNull { it.id } ?: 0L) + 1,
            name = spot.name,
            placeType = PlaceType.SPOT,
            dayIndex = targetDay,
            lat = spot.lat,
            lng = spot.lng,
            // 景点一句话简介作为该点位的游玩提示
            intro = spot.shortDesc,
            petFriendly = spot.petFriendly,
            // 推荐景点自带网络封面时一并带入（仅接受 http(s) 地址）
            coverUrl = spot.coverImg.trim().takeIf { it.startsWith("http") },
        )

        return if (plan.totalDays == 0 || plan.dailyPlans.isEmpty()) {
            TravelPlan(
                destination = spot.city.ifBlank { _currentCity.value },
                totalDays = 1,
                dailyPlans = listOf(DayPlan(day = 1, places = listOf(place))),
                allPlaceList = listOf(place),
            )
        } else {
            // 目标天夹在合法范围内，追加到该天末尾；其余天不变
            val day = targetDay.coerceIn(1, plan.totalDays)
            val newDays = plan.dailyPlans.map { dayPlan ->
                if (dayPlan.day == day) dayPlan.copy(places = dayPlan.places + place) else dayPlan
            }
            plan.copy(
                dailyPlans = newDays,
                allPlaceList = plan.allPlaceList + place,
            )
        }
    }

    /**
     * 从行程中删除指定景点（计划页景点卡片「移除」）：
     * - 从对应天移除该点位；该天变空则删除该天，并把后续天的 dayIndex 整体前移 1，
     *   保证不出现没有景点的空天板块、各天连续编号；
     * - 全部删光后恢复 [EMPTY_TRAVEL_PLAN] 并清库；
     * - ①内存先更新 ②整份行程落 Room；全局 [tripPlan] 自动重组，地图 Marker 同步消失。
     */
    fun removePlaceFromPlan(placeId: Long) {
        val plan = _currentTravelPlan.value
        if (plan == EMPTY_TRAVEL_PLAN) return
        if (plan.allPlaceList.none { it.id == placeId }) return

        // 去掉该点位，丢弃变空的天，再按剩余顺序重新连续编号 D1…DN
        val remainingDaily = plan.dailyPlans
            .map { dayPlan -> dayPlan.copy(places = dayPlan.places.filterNot { it.id == placeId }) }
            .filter { it.places.isNotEmpty() }

        if (remainingDaily.isEmpty()) {
            _tripConfig.value = null
            _currentTravelPlan.value = EMPTY_TRAVEL_PLAN
            persist { clearTravelPlan() }
            return
        }

        val renumberedDaily = remainingDaily.mapIndexed { index, dayPlan ->
            val newDay = index + 1
            DayPlan(
                day = newDay,
                places = dayPlan.places.map { it.copy(dayIndex = newDay) },
            )
        }
        val newPlan = TravelPlan(
            destination = plan.destination,
            totalDays = renumberedDaily.size,
            dailyPlans = renumberedDaily,
            allPlaceList = renumberedDaily.flatMap { it.places },
        )
        _currentTravelPlan.value = newPlan
        persist { saveTravelPlan(newPlan) }
    }

    /** 清空当前行程（①内存恢复空行程并重置行程配置 ②删除库内行程，子项随外键级联清除） */
    fun clearPlan() {
        _tripConfig.value = null
        _pendingAddDay.value = null
        if (_currentTravelPlan.value == EMPTY_TRAVEL_PLAN) return
        _currentTravelPlan.value = EMPTY_TRAVEL_PLAN
        persist { clearTravelPlan() }
    }

    /**
     * 待地图页聚焦的点位（跨 Tab 一次性事件载体，不落库）：
     * 计划页点击某地点条目时写入并切换到地图 Tab，地图页订阅后移动相机 / 高亮该 Marker，
     * 消费后调用 [consumeMapFocusPlace] 清空，避免切回地图时重复聚焦。
     */
    private val _mapFocusPlace = MutableStateFlow<Place?>(null)
    val mapFocusPlace: StateFlow<Place?> = _mapFocusPlace.asStateFlow()

    /** 计划页条目点击：把点位信息传递给地图页面 */
    fun focusPlaceOnMap(place: Place) {
        _mapFocusPlace.value = place
    }

    /** 地图页完成聚焦后消费事件 */
    fun consumeMapFocusPlace() {
        _mapFocusPlace.value = null
    }

    /**
     * 待计划页定位的点位（跨 Tab 一次性事件载体，不落库）：
     * 地图 Marker 信息卡点「查看行程卡片」时写入并切到计划 Tab，计划页订阅后滚动到
     * 对应天卡片并高亮该点位，消费后调用 [consumePlanFocusPlace] 清空。
     */
    private val _planFocusPlace = MutableStateFlow<Place?>(null)
    val planFocusPlace: StateFlow<Place?> = _planFocusPlace.asStateFlow()

    /** 地图 Marker 卡片点击：请求计划页定位到该点位所在天卡片 */
    fun focusPlaceInPlan(place: Place) {
        _planFocusPlace.value = place
    }

    /** 计划页完成滚动 / 高亮后消费事件 */
    fun consumePlanFocusPlace() {
        _planFocusPlace.value = null
    }

    /* ---------------------------------- 宠物档案 ---------------------------------- */

    private val _petList = MutableStateFlow<List<Pet>>(emptyList())

    /** 宠物档案列表（宠物弹窗展示 / 编辑 / 删除） */
    val petList: StateFlow<List<Pet>> = _petList.asStateFlow()

    /**
     * 新增宠物（①内存先更新并分配 id ②落库）：
     * 传入非 0 且已存在的 id 时按更新处理，避免重复项。
     */
    fun addPet(pet: Pet) {
        val list = _petList.value
        val target = when {
            pet.id != 0L && list.any { it.id == pet.id } -> pet
            pet.id == 0L -> pet.copy(id = (list.maxOfOrNull(Pet::id) ?: 0L) + 1)
            else -> pet
        }
        _petList.update { current ->
            when {
                current.any { it.id == target.id } -> current.map { if (it.id == target.id) target else it }
                else -> current + target
            }
        }
        persist {
            if (pet.id == 0L) addPet(target) else updatePet(target)
        }
    }

    /** 更新宠物（①内存按 id 整项替换 ②落库） */
    fun updatePet(pet: Pet) {
        if (_petList.value.none { it.id == pet.id }) return
        _petList.update { list -> list.map { if (it.id == pet.id) pet else it } }
        persist { updatePet(pet) }
    }

    /** 删除指定 id 的宠物（①内存移除 ②落库） */
    fun deletePet(petId: Long) {
        _petList.update { list -> list.filterNot { it.id == petId } }
        persist { deletePet(petId) }
    }

    /* ---------------------------------- 收藏景点 ---------------------------------- */

    private val _collectSpotList = MutableStateFlow<List<Spot>>(emptyList())

    /** 收藏的景点列表（最新收藏排最前） */
    val collectSpotList: StateFlow<List<Spot>> = _collectSpotList.asStateFlow()

    /** 收藏景点（①内存去重置顶 ②写收藏表） */
    fun addCollectSpot(spot: Spot) {
        _collectSpotList.update { list -> listOf(spot) + list.filterNot { it.id == spot.id } }
        persist { addCollectedSpot(spot) }
    }

    /** 取消收藏景点（①内存移除 ②删收藏表记录） */
    fun removeCollectSpot(spot: Spot) {
        _collectSpotList.update { list -> list.filterNot { it.id == spot.id } }
        persist { removeCollectedSpot(spot.id) }
    }

    /* ---------------------------------- 收藏帖子 ---------------------------------- */

    private val _collectPostList = MutableStateFlow<List<Post>>(emptyList())

    /** 收藏的社区帖子列表（最新收藏排最前） */
    val collectPostList: StateFlow<List<Post>> = _collectPostList.asStateFlow()

    /**
     * 收藏帖子（①内存去重置顶，并同步信息流帖子的收藏标记 ②写收藏表 + 更新帖子表）
     */
    fun addCollectPost(post: Post) {
        val marked = post.copy(collected = true)
        _collectPostList.update { list -> listOf(marked) + list.filterNot { it.id == marked.id } }
        _communityPostList.update { list ->
            list.map { if (it.id == marked.id) marked else it }
        }
        persist {
            addCollectedPost(marked)
            updatePostCollected(marked.id, true)
        }
    }

    /**
     * 取消收藏帖子（①内存移除，并清掉信息流帖子的收藏标记 ②删收藏表记录 + 更新帖子表）
     */
    fun removeCollectPost(post: Post) {
        _collectPostList.update { list -> list.filterNot { it.id == post.id } }
        _communityPostList.update { list ->
            list.map { if (it.id == post.id) it.copy(collected = false) else it }
        }
        persist {
            removeCollectedPost(post.id)
            updatePostCollected(post.id, false)
        }
    }

    /* ---------------------------------- 浏览记录 ---------------------------------- */

    private val _browseHistoryList = MutableStateFlow<List<Spot>>(emptyList())

    /** 景点浏览记录（最近浏览排最前，上限 [MAX_BROWSE_HISTORY] 条） */
    val browseHistoryList: StateFlow<List<Spot>> = _browseHistoryList.asStateFlow()

    /** 新增浏览记录（①内存去重置顶并截断上限 ②落库，REPLACE 自动去重） */
    fun addBrowseHistory(spot: Spot) {
        _browseHistoryList.update { list ->
            (listOf(spot) + list.filterNot { it.id == spot.id }).take(MAX_BROWSE_HISTORY)
        }
        persist { addBrowseHistory(spot) }
    }

    /** 清空全部浏览记录（①内存清空 ②删表） */
    fun clearBrowseHistory() {
        if (_browseHistoryList.value.isEmpty()) return
        _browseHistoryList.value = emptyList()
        persist { clearBrowseHistory() }
    }

    /* ---------------------------------- 旅行日记 ---------------------------------- */

    private val _diaryList = MutableStateFlow<List<Diary>>(emptyList())

    /** 旅行日记列表（最新保存排最前） */
    val diaryList: StateFlow<List<Diary>> = _diaryList.asStateFlow()

    /**
     * 保存日记（新增 / 编辑共用，①内存 upsert 并分配 id ②落库）：
     * id 为 0 或不存在 → 分配自增 id 插入最前；id 已存在 → 原位更新。
     */
    fun saveDiary(diary: Diary) {
        val list = _diaryList.value
        val target = when {
            diary.id != 0L && list.any { it.id == diary.id } -> diary
            diary.id == 0L -> diary.copy(id = (list.maxOfOrNull(Diary::id) ?: 0L) + 1)
            else -> diary
        }
        _diaryList.update { current ->
            when {
                current.any { it.id == target.id } -> current.map { if (it.id == target.id) target else it }
                else -> listOf(target) + current
            }
        }
        persist { saveDiary(target) }
    }

    /** 删除日记（①内存移除 ②删表） */
    fun deleteDiary(diaryId: Long) {
        if (_diaryList.value.none { it.id == diaryId }) return
        _diaryList.update { list -> list.filterNot { it.id == diaryId } }
        persist { deleteDiary(diaryId) }
    }

    /* ---------------------------------- 社区帖子 ---------------------------------- */

    private val _communityPostList = MutableStateFlow<List<Post>>(emptyList())

    /** 社区游记帖子信息流（新发布排最前） */
    val communityPostList: StateFlow<List<Post>> = _communityPostList.asStateFlow()

    /**
     * 发布社区帖子（①内存置顶并分配 id ②落库）。
     */
    fun addCommunityPost(post: Post) {
        val list = _communityPostList.value
        val target = if (post.id == 0L) {
            post.copy(id = (list.maxOfOrNull(Post::id) ?: 0L) + 1)
        } else {
            post
        }
        _communityPostList.update { current -> listOf(target) + current }
        persist { addPost(target) }
    }

    /**
     * 本地模拟点赞 / 取消点赞（①内存翻转 liked 并同步点赞数 ②更新帖子表，Room 持久化）。
     */
    fun togglePostLike(post: Post) {
        val target = post.copy(
            liked = !post.liked,
            likeCount = (post.likeCount + if (post.liked) -1 else 1).coerceAtLeast(0),
        )
        _communityPostList.update { list -> list.map { if (it.id == target.id) target else it } }
        persist { updatePostLike(target.id, target.liked, target.likeCount) }
    }

    /* ---------------------------------- AI 攻略 ---------------------------------- */
    // 攻略唯一数据来源：后端 POST /travel/generateTask 返回的 Markdown 文本（见 travelTaskState）。
    // 前端不再本地生成任何假攻略 / 假行程；旧的直连大模型生成状态机（AiGenerateState）已移除。

    /**
     * AI 地点封面图地址表：key = [coverKey]（城市|地点名），value = 图片搜索得到的首张实拍图 URL。
     *
     * AI 攻略生成成功后**异步**搜索图片，每搜到一张就增量更新，计划卡片逐张刷新；
     * 搜不到的地点不写入此表，卡片保持默认占位。图片只保存在内存（不落 Room），
     * 每次生成后重新异步获取，避免给业务库加图片字段 / 迁移。
     */
    private val _placeCoverMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val placeCoverMap: StateFlow<Map<String, String>> = _placeCoverMap.asStateFlow()

    /**
     * 「优先宠物友好地点」复选框状态（计划页生成新计划旁的小复选框）。
     * 勾选后生成攻略会在 Prompt 中要求优先推荐可携宠进入的景点 / 美食店。
     * 仅本次会话内存状态（不持久化，不影响其他业务）。
     */
    private val _aiPetFriendlyPreferred = MutableStateFlow(false)
    val aiPetFriendlyPreferred: StateFlow<Boolean> = _aiPetFriendlyPreferred.asStateFlow()

    /** 更新「优先宠物友好地点」勾选状态 */
    fun setAiPetFriendlyPreferred(enabled: Boolean) {
        _aiPetFriendlyPreferred.value = enabled
    }

    /* -------------------- 后端 AI 攻略任务（异步 taskId + 后台轮询） -------------------- */

    private val _travelTaskState = MutableStateFlow<TravelTaskState>(TravelTaskState.Idle)

    /**
     * 行程配置弹窗的后端任务状态：空闲 / 提交中 / 后台轮询中（带 taskId）/ 失败（带具体原因）。
     * UI 订阅后控制配置按钮 loading、「正在生成攻略」弹窗、失败信息弹窗。
     */
    val travelTaskState: StateFlow<TravelTaskState> = _travelTaskState.asStateFlow()

    /**
     * 「正在生成攻略」提示弹窗是否展示：进入轮询时置 true，用户点【我知道了】置 false。
     * 弹窗关闭【不影响后台轮询】——轮询只绑定在 ViewModel 的 [travelTaskJob] 上。
     */
    private val _showGeneratingPrompt = MutableStateFlow(false)
    val showGeneratingPrompt: StateFlow<Boolean> = _showGeneratingPrompt.asStateFlow()

    /**
     * 攻略任务的全局一次性通知（跨页面 / 跨 Tab 也能收到，由 Activity 根组件收集弹 Toast）：
     * 后台轮询完成并自动同步行程成功后发送「✅ 已成功生成攻略」。
     */
    private val _taskNoticeEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val taskNoticeEvents: SharedFlow<String> = _taskNoticeEvents.asSharedFlow()

    /**
     * 攻略任务协程，保存在【Activity 作用域的 ViewModel】字段里：
     * 用户在弹窗点【我知道了】、切换 Tab、退出计划页、旋转屏幕都不会取消它，
     * 轮询持续到拿到结果 / 超时 / 失败；只有整个 Activity（进程）销毁才随之结束。
     */
    private var travelTaskJob: Job? = null

    /** 本次任务使用的行程配置（天数 / 预算 / 偏好 / 宠物 / 备注），轮询成功后随行程一并落库 */
    private var lastGuideOptions: AiPlanOptions? = null

    /**
     * 攻略地点导入器：正则提取 Markdown「- 」地点名 + 高德 Android SDK 地理编码补齐坐标。
     * 用 Application Context 构造 GeocodeSearch，生命周期与进程一致，串行调用防限流。
     */
    private val guideSpotImporter: GuideSpotGeocodeImporter by lazy {
        GuideSpotGeocodeImporter(AmapGeocoder(AppServiceLocator.requireAppContext()))
    }

    /**
     * 行程配置弹窗点击「生成 AI 攻略」：
     * 1. 把目的地 / 天数 / 预算 / 游玩偏好 / 宠物友好 / 额外备注拼成 JSON，
     *    POST {当前 baseUrl}/travel/generateTask（JWT Bearer 头由拦截器自动附加，
     *    OkHttp BODY 日志打印完整请求体 / 响应体，tag=AuthHttpClient）；
     * 2. 成功拿到 taskId → 置 [TravelTaskState.Polling] 并弹出「正在生成攻略」提示；
     * 3. 后台每 1.5s 轮询 GET /travel/task/{taskId}（taskId 在 URL 路径上，非 query）：
     *    PROCESSING 继续；响应含 data 字段 → 解析 Markdown 自动同步写入出行计划；
     *    安全上限 300s；单次轮询的网络抖动【不终止任务】，只要 status 不是失败就持续轮询，
     *    只有「拿到 data」或「到达 300s 本地安全上限」才停止；
     *    后端明确业务失败（success=false / status=FAILED）弹具体错误。
     */
    fun submitTravelGuideTask(city: String, options: AiPlanOptions) {
        // 提交中 / 轮询中忽略重复点击（按钮也已置灰，双保险）；失败后允许重新提交
        if (_travelTaskState.value is TravelTaskState.Submitting ||
            _travelTaskState.value is TravelTaskState.Polling
        ) {
            return
        }
        val targetCity = city.trim()
        if (targetCity.isEmpty()) {
            _travelTaskState.value = TravelTaskState.Failed("城市名称不能为空，请重新选择目的地")
            return
        }
        lastGuideOptions = options
        _showGeneratingPrompt.value = false
        _travelTaskState.value = TravelTaskState.Submitting

        travelTaskJob?.cancel()
        // viewModelScope 绑定 Activity 级 ViewModel：页面 / Composable 销毁不取消，后台继续轮询
        travelTaskJob = viewModelScope.launch {
            val baseUrl = ServerConfig.get()
            try {
                val api = TravelRetrofit.create(baseUrl)

                // —— 步骤 1：提交任务，解析 taskId（ResponseBody + 手动 JSON 容错） ——
                val submitRaw = api.generate(
                    GenerateTaskRequest(
                        city = targetCity,
                        dayNum = options.days
                            .coerceIn(AiPlanOptions.MIN_DAYS, AiPlanOptions.MAX_DAYS),
                        budget = options.budgetYuan, // 空预算传 null = 不限
                        preferenceList = options.preferences, // 字符串 List
                        petFriendlyFlag = options.petFriendly,
                        remark = options.remark?.trim().orEmpty(), // 额外备注（可选）
                    ),
                ).use { body -> body.string() }
                Log.i(TAG, "攻略任务提交响应：$submitRaw")

                // —— 步骤 1：提交任务。后端当前契约为【同步返回】：
                // {"success":true,"status":"SUCCESS","data":"攻略文本"}；
                // 同时兼容旧的异步契约：返回 taskId 时走步骤 2 轮询（GET /travel/task/{taskId}）。
                when (val submit = parseSubmitResponse(submitRaw)) {
                    is SubmitOutcome.Done -> {
                        // 同步契约：POST 直接拿到完整攻略文本，无需轮询
                        Log.i(TAG, "攻略接口同步返回成功，城市=$targetCity，文本长度=${submit.markdown.length}")
                        finalizeGuide(submit.markdown, targetCity, options, taskId = null)
                        return@launch
                    }
                    is SubmitOutcome.TaskSubmitted -> {
                        val taskId = submit.taskId
                        Log.i(TAG, "攻略任务已提交（异步模式）taskId=$taskId，城市=$targetCity，开始后台轮询")

                        // 弹出「正在生成攻略」提示（用户点我知道了只关弹窗，轮询继续）
                        _travelTaskState.value = TravelTaskState.Polling(taskId)
                        _showGeneratingPrompt.value = true

                        // —— 步骤 2：后台轮询（本地安全上限 300s，间隔 1.5s）。
                        // 生命周期：协程挂在 Activity 级 ViewModel 上，切页面 / 返回 / 关掉「正在生成
                        // 攻略」弹窗都【不会取消】；只有两种情况停止——拿到 data 或到达安全上限。
                        // 单次轮询遇到网络抖动（adb 瞬断等）只记录日志并继续下一轮，不终止整个任务。
                        val markdown = withTimeoutOrNull(TASK_TOTAL_TIMEOUT_MS) {
                            var pollCount = 0
                            while (isActive) {
                                delay(POLL_INTERVAL_MS)
                                pollCount++
                                // 每次轮询的完整响应体 OkHttp BODY 日志都会打印；请求异常在此就地消化
                                val pollRaw = try {
                                    api.getTaskResult(taskId).use { body -> body.string() }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    // 非终止性错误：打印【具体原因】方便调试，随后继续轮询直到成功或超时
                                    Log.w(
                                        TAG,
                                        "第 $pollCount 次轮询请求异常，任务不终止、继续轮询 " +
                                            "taskId=$taskId，原因：${pollExceptionDetail(e)}",
                                        e,
                                    )
                                    null
                                }
                                if (pollRaw != null) {
                                    Log.i(TAG, "第 $pollCount 次轮询响应 taskId=$taskId：$pollRaw")
                                    // 业务明确失败（success=false / status=FAILED）会在此抛异常，
                                    // 由外层 catch 弹出具体错误；PROCESSING / 暂缺 data 一律继续
                                    when (val outcome = parsePollResponse(pollRaw)) {
                                        is PollOutcome.Processing -> Unit // 继续下一轮
                                        is PollOutcome.Done -> return@withTimeoutOrNull outcome.markdown
                                    }
                                }
                            }
                            // 理论不可达：isActive 失效时协程已被取消
                            null
                        }

                        if (markdown == null) {
                            Log.w(TAG, "攻略任务轮询总超时 taskId=$taskId，超时=${TASK_TOTAL_TIMEOUT_MS}ms")
                            _showGeneratingPrompt.value = false
                            _travelTaskState.value = TravelTaskState.Failed("攻略生成超时，请重试")
                            return@launch
                        }
                        // 异步模式拿到 data：走与同步模式相同的收口（解析地名 → 地理编码 → 同步行程）
                        finalizeGuide(markdown, targetCity, options, taskId)
                    }
                }
            } catch (e: CancellationException) {
                // 用户在失败弹窗确认 / 重新提交时取消：正常退出，不当成错误
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "攻略任务失败 baseUrl=$baseUrl", e)
                _showGeneratingPrompt.value = false
                _travelTaskState.value = TravelTaskState.Failed(
                    e.message ?: "攻略生成失败（地址 $baseUrl）：网络异常，请检查后端服务与网络",
                )
            }
        }
    }

    /**
     * 拿到完整攻略文本后的统一收口（同步 POST 与异步轮询两条路径共用）：
     * 1. 正则提取 Markdown「- 」地点名 → 高德 Android SDK 串行地理编码（带 city、单点失败跳过）；
     * 2. 至少 1 个点位成功 → 自动导入【我的行程】并在高德地图打点，同时把攻略原文展示在页面弹窗；
     * 3. 一个点位都没有 → [TravelTaskState.GuideReadyButNoMatch] 文字成功提示，行程不变。
     */
    private suspend fun finalizeGuide(
        markdown: String,
        targetCity: String,
        options: AiPlanOptions,
        taskId: String?,
    ) {
        if (markdown.isBlank()) {
            _showGeneratingPrompt.value = false
            _travelTaskState.value = TravelTaskState.Failed("任务已完成但后端返回的攻略内容为空，请重新生成")
            return
        }

        // 坐标策略：后端 AI 只输出地点名（不再输出经纬度），前端正则提取「- 」条目后，
        // 用高德 Android SDK 地理编码（city 限定城市、串行调用）补齐坐标。
        // 单点编码失败只跳过；全部失败不判定生成失败，走「文字攻略已生成」提示。
        val importResult = guideSpotImporter.import(
            city = targetCity,
            markdown = markdown,
            nowMillis = System.currentTimeMillis(),
        )
        val aiPlan = guideSpotImporter.toAiPlanOrNull(
            city = targetCity,
            result = importResult,
            nowMillis = System.currentTimeMillis(),
        )

        _showGeneratingPrompt.value = false
        if (aiPlan == null) {
            // 不是接口失败：攻略文字已生成，但一个地图点都没匹配上（0 个地点 / 全部编码失败）
            Log.w(
                TAG,
                "攻略文字生成成功但无可用地图点位：提取 ${importResult.extractedNames.size} 个，" +
                    "失败 ${importResult.failures.size} 个，taskId=$taskId",
            )
            _travelTaskState.value = TravelTaskState.GuideReadyButNoMatch
            return
        }

        // 至少 1 个点位解析到：全部点位（含餐厅 / 商圈 / 高德未命中的兜底坐标点）
        // 自动导入【我的行程】并在高德地图按类型渲染 Marker
        applyGeocodedAiPlanToTravel(aiPlan, targetCity, options, importResult.dayHeadersDetected)
        Log.i(
            TAG,
            "后台攻略任务完成并已自动同步，taskId=$taskId，Markdown 长度=${markdown.length}，" +
                "导入点位=${aiPlan.spotList.size}/${importResult.extractedNames.size}",
        )
        // 攻略原文展示在页面弹窗（用户点【我知道了】后关闭）；同时发全局成功提醒
        _travelTaskState.value = TravelTaskState.Succeeded(markdown)
        _taskNoticeEvents.emit("✅ 已成功生成攻略")
    }

    /**
     * 用户在「正在生成攻略」弹窗点【我知道了】：仅关闭提示弹窗，
     * 【不取消、不中断后台轮询】；轮询继续在 ViewModel 协程中执行，
     * 用户可以切 Tab / 退出计划页，完成后自动同步并全局 Toast 提醒。
     */
    fun dismissGeneratingPrompt() {
        _showGeneratingPrompt.value = false
    }

    /** 失败信息弹窗点【我知道了】：取消可能残留的任务协程并复位为空闲，之后可重新提交。 */
    fun resetTravelGuideTask() {
        travelTaskJob?.cancel()
        travelTaskJob = null
        _showGeneratingPrompt.value = false
        _travelTaskState.value = TravelTaskState.Idle
    }

    /**
     * 攻略点位写入出行计划（后端攻略文本【全量同步】，一个点位都不丢）。
     *
     * 分天严格跟随原文：
     * - 文本中有「第一天 / 第二天 / Day 2 / D2」日标题 → 按点位解析出的 day 分组，
     *   保持原文先后顺序，不丢、不合并、不乱序、不做地理重排；
     * - 没有日标题 → 按用户填写的天数把点位【按顺序连续切分】到各天（同样不做地理聚类）。
     * 随后内存 + Room 双写，【我的行程】订阅 StateFlow 自动刷新，高德地图按类型渲染 Marker。
     */
    private suspend fun applyGeocodedAiPlanToTravel(
        aiPlan: AiTravelPlan,
        targetCity: String,
        options: AiPlanOptions?,
        dayHeadersDetected: Boolean,
    ) {
        val days = (options?.days ?: 1).coerceIn(AiPlanOptions.MIN_DAYS, AiPlanOptions.MAX_DAYS)
        val newPlan = buildGuideTravelPlan(
            aiPlan = aiPlan,
            fallbackCity = targetCity,
            days = days,
            dayHeadersDetected = dayHeadersDetected,
        )

        // ①内存先更新（计划页 DAY 列表 / 地图 Marker 立即渲染）②整份行程落 Room 持久化
        repository.startNewTravelPlan(newPlan, options)
        _currentTravelPlan.value = newPlan
        _tripConfig.value = options

        // 新行程落库后恢复其绑定的历史聊天记录，并异步为每个地点搜索真实封面图
        restoreChatHistory()
        enrichPlaceCovers(aiPlan)
        Log.i(
            TAG,
            "AI 攻略全量同步完成：城市=$targetCity，天数=${newPlan.totalDays}，" +
                "点位数=${aiPlan.spotList.size}（含餐厅 / 商圈），按日标题分组=$dayHeadersDetected",
        )
    }

    /**
     * 攻略专用行程构建：严格保留 [aiPlan] 点位顺序（与原文完全一致），不使用地理聚类。
     * - [dayHeadersDetected]=true：按解析出的 [AiSpot.day] 分组（日标题先后映射为 D1..DN）；
     * - 否则把全部点位按 [days] 【连续、均匀】切块，点位相对顺序一字不动。
     */
    private fun buildGuideTravelPlan(
        aiPlan: AiTravelPlan,
        fallbackCity: String,
        days: Int,
        dayHeadersDetected: Boolean,
    ): TravelPlan {
        val spots = aiPlan.spotList
        val places = spots.mapIndexed { index, spot ->
            spot.toPlace(id = index.toLong() + 1L, dayIndex = 1)
        }

        val grouped: List<List<Place>> = if (dayHeadersDetected && spots.any { it.day != null }) {
            // 按日标题分组：LinkedHashMap 保持天的出现顺序，天内点位保持原文顺序
            val buckets = LinkedHashMap<Int, MutableList<Place>>()
            spots.forEachIndexed { index, spot ->
                // 出现在第一个日标题之前的点位归入 D1
                val d = (spot.day ?: 1).coerceAtLeast(1)
                buckets.getOrPut(d) { mutableListOf() }.add(places[index].copy(dayIndex = d))
            }
            // 重新连续编号 D1..DN（防止原文天编号不连续出现空卡片）
            buckets.entries.sortedBy { it.key }.mapIndexed { idx, entry ->
                val day = idx + 1
                entry.value.map { it.copy(dayIndex = day) }
            }
        } else {
            // 无日标题：按用户天数顺序连续切分（均匀分配，余数天多 1 个点），绝不重排
            val groupCount = days.coerceIn(1, places.size.coerceAtLeast(1))
            chunkInOrderEvenly(places, groupCount).mapIndexed { idx, list ->
                val day = idx + 1
                list.map { it.copy(dayIndex = day) }
            }
        }

        val dailyPlans = grouped.mapIndexed { idx, list -> DayPlan(day = idx + 1, places = list) }
        return TravelPlan(
            destination = aiPlan.city.ifBlank { fallbackCity },
            totalDays = dailyPlans.size,
            dailyPlans = dailyPlans,
            allPlaceList = dailyPlans.flatMap { it.places },
        )
    }

    /** 把 [items] 按原顺序连续、尽量均匀地切成 [count] 段（前 remainder 段多 1 个元素） */
    private fun <T> chunkInOrderEvenly(items: List<T>, count: Int): List<List<T>> {
        if (count <= 1 || items.size <= 1) return listOf(items)
        val base = items.size / count
        val remainder = items.size % count
        val result = ArrayList<List<T>>(count)
        var start = 0
        for (i in 0 until count) {
            val size = base + if (i < remainder) 1 else 0
            val end = (start + size).coerceAtMost(items.size)
            result += items.subList(start, end)
            start = end
        }
        // 整除 / 边界保护：尾部空段理论不出现，出现则过滤（不丢点位，只去空天）
        return result.filter { it.isNotEmpty() }
    }

    /* ------------------------- 异步任务 JSON 响应容错解析 ------------------------- */

    /** 轮询响应解析结果：处理中 / 已完成（携带 Markdown 正文） */
    private sealed interface PollOutcome {
        data object Processing : PollOutcome
        data class Done(val markdown: String) : PollOutcome
    }

    /** 提交响应解析结果：同步契约（直接带攻略文本）/ 异步契约（带 taskId，需轮询） */
    private sealed interface SubmitOutcome {
        /** 同步契约：POST 已直接返回完整 Markdown 攻略文本 */
        data class Done(val markdown: String) : SubmitOutcome

        /** 异步契约：返回 [taskId]，需继续 GET /travel/task/{taskId} 轮询 */
        data class TaskSubmitted(val taskId: String) : SubmitOutcome
    }

    /**
     * 解析 POST /travel/generateTask 的提交响应，同时兼容两种后端契约：
     *
     * 同步契约（当前）：
     *   {"success":true,"status":"SUCCESS","data":"攻略文本"}
     *   → 非空 data 字符串直接视为攻略正文（[SubmitOutcome.Done]），不再轮询。
     *
     * 异步契约（旧）：
     *   {"success":true,"taskId":"abc"} / {"code":200,"data":{"taskId":"abc"}}
     *   → 取 taskId（[SubmitOutcome.TaskSubmitted]），由调用方轮询。
     *
     * 业务失败 / 既无 data 又无 taskId 时抛 [IllegalStateException]（消息直接展示给用户排查）。
     */
    private fun parseSubmitResponse(raw: String): SubmitOutcome {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            throw IllegalStateException("提交任务成功但响应为空，请检查后端 /travel/generateTask 返回")
        }
        if (!trimmed.startsWith("{")) {
            // 纯文本（旧契约可能直接回 taskId）：按异步 taskId 处理
            return SubmitOutcome.TaskSubmitted(trimmed)
        }
        val json = runCatching { JSONObject(trimmed) }
            .getOrElse { throw IllegalStateException("提交任务响应不是合法 JSON：$trimmed") }
        checkBizError(json) { "任务提交被后端拒绝" }

        // 同步契约：data 为非空字符串时，它就是攻略正文
        if (json.has("data") && !json.isNull("data")) {
            when (val data = json.opt("data")) {
                is String -> if (data.isNotBlank()) return SubmitOutcome.Done(data)
                is JSONObject ->
                    // 异步包裹：data.taskId
                    data.optString("taskId").takeIf { it.isNotBlank() }
                        ?.let { return SubmitOutcome.TaskSubmitted(it) }
                else -> Unit
            }
        }

        // 异步契约：顶层 taskId
        json.optString("taskId").takeIf { it.isNotBlank() }
            ?.let { return SubmitOutcome.TaskSubmitted(it) }

        throw IllegalStateException("提交任务成功但响应中缺少 data 攻略文本或 taskId 字段：$trimmed")
    }

    /**
     * 解析轮询响应（严格按约定）：
     * - status == "PROCESSING"（或 PENDING/RUNNING、SUCCESS 但 data 尚未就绪、以及任何
     *   无法识别的【非失败】状态）→ 继续轮询，不因状态取值不认识而提前判失败；
     * - 响应包含非空 data 字段 → 任务完成，data 即 Markdown 攻略正文；
     * - success=false / 错误 code / status 明确为 FAILED/FAIL/ERROR → 这是【后端业务失败】，
     *   抛异常携带后端 msg，与 APP 本地超时是两回事。
     *
     * 注意：只要后端没明确说失败就一直轮询，由 300s 本地安全上限兜底，不提前截断。
     */
    private fun parsePollResponse(raw: String): PollOutcome {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            // 空响应不当错误，继续轮询，由 300s 安全上限兜底
            return PollOutcome.Processing
        }
        if (!trimmed.startsWith("{")) {
            // 纯文本：直接视为 Markdown 正文
            return PollOutcome.Done(trimmed)
        }
        val json = runCatching { JSONObject(trimmed) }
            .getOrElse { throw IllegalStateException("任务状态响应不是合法 JSON：$trimmed") }
        checkBizError(json) { "攻略生成任务被后端标记为失败" }

        // 约定：响应包含 data 字段（非空）即完成
        if (json.has("data") && !json.isNull("data")) {
            val markdown = when (val data = json.get("data")) {
                is String -> data
                else -> data.toString()
            }
            if (markdown.isNotBlank()) return PollOutcome.Done(markdown)
        }

        return when (val status = json.optString("status").trim().uppercase()) {
            // 后端明确的任务失败状态：业务失败（与本地超时区分），携带后端 msg
            "FAILED", "FAIL", "ERROR" -> {
                val msg = json.optString("msg").ifBlank { json.optString("message") }
                throw IllegalStateException(
                    "攻略生成失败：status=$status" + if (msg.isNotBlank()) "，$msg" else "",
                )
            }
            // PROCESSING / PENDING / RUNNING / SUCCESS 未带 data / 空串 / 未知非失败状态：
            // 一律继续轮询，直到拿到 data 或到达 300s 本地安全上限
            else -> PollOutcome.Processing
        }
    }

    /**
     * 校验统一业务错误字段：success=false 或 code 非 200/0 时抛异常（携带后端 msg）。
     */
    private fun checkBizError(json: JSONObject, stage: () -> String) {
        if (json.has("success") && !json.isNull("success") && !json.optBoolean("success", true)) {
            val msg = json.optString("msg").ifBlank { json.optString("message") }
            throw IllegalStateException("${stage()}: ${msg.ifBlank { "后端返回 success=false" }}")
        }
        if (json.has("code") && !json.isNull("code")) {
            val code = json.optInt("code", 200)
            if (code != 200 && code != 0) {
                val msg = json.optString("msg").ifBlank { json.optString("message") }
                throw IllegalStateException("${stage()}: ${msg.ifBlank { "后端返回错误码 $code" }}")
            }
        }
    }

    /** 提取网络异常的具体信息：HTTP 错误带状态码 + 后端错误正文，其余带异常类型与 message。 */
    private fun pollExceptionDetail(e: Exception): String =
        (e as? HttpException)?.let { httpError ->
            val body = runCatching { httpError.response()?.errorBody()?.string() }
                .getOrNull()?.takeIf { it.isNotBlank() }
            "HTTP ${httpError.code()}${body?.let { "，后端返回：$it" } ?: ""}"
        } ?: (e.message?.takeIf { it.isNotBlank() } ?: "${e.javaClass.simpleName}：网络异常")

    /**
     * AI 攻略生成成功后的异步配图：新行程先清空旧封面表，随后每个地点并发搜索
     * POI 实拍图（按点位行业类型过滤，提升饭店 / 探店 / 酒店命中率），搜到首张即：
     * ①增量写入 [placeCoverMap]（计划卡片立即逐张刷新）；
     * ②回写当前行程对应 Place.coverUrl 并整份落库（杀进程重启后图片仍在）。
     * 任何单个地点搜索失败都不影响其他地点与行程本身。
     */
    private var coverJob: Job? = null
    private var coverJobTripId: String? = null

    private fun enrichPlaceCovers(plan: AiTravelPlan) {
        coverJob?.cancel()
        val targetTripId = tripSync?.state?.value?.activeTripId
        coverJobTripId = targetTripId
        // 新城市 / 新行程替换旧封面，避免同名地点显示到上一份攻略的旧图
        _placeCoverMap.value = emptyMap()
        // 顺序逐个配图：一次性并发十几个 TLS 请求在模拟器弱网下容易超时 / 触发高德 QPS 限流；
        // 封面随搜随刷，用户先看到行程，图片在十几秒内陆续补齐
        coverJob = viewModelScope.launch {
            plan.spotList.forEach { spot ->
                val images = runCatching {
                    imageRepository.searchPlaceImages(
                        query = spot.name,
                        city = plan.city,
                        limit = 3,
                        placeType = spot.type.toPlaceType(),
                    )
                }.getOrDefault(emptyList())
                if (!isActive || tripSync?.state?.value?.activeTripId != targetTripId) return@launch
                val cover = images.firstOrNull() ?: return@forEach
                _placeCoverMap.update { it + (coverKey(plan.city, spot.name) to cover) }
                attachCoverToCurrentPlan(spot.name, cover)
            }
        }
    }

    /**
     * 把异步搜到的封面 URL 回写到当前行程的对应点位并落 Room（coverUrl 持久化）。
     * 按名称匹配当前行程；点位已有封面 / 行程已被清空 / 切城市后则忽略，避免旧图串入。
     */
    private fun attachCoverToCurrentPlan(placeName: String, coverUrl: String) {
        // 多个配图协程可能同时返回，用原子 update 防止互相覆盖；落库始终写最新整份行程
        _currentTravelPlan.update { plan ->
            if (plan == EMPTY_TRAVEL_PLAN) return@update plan
            if (plan.allPlaceList.none { it.name == placeName && it.coverUrl == null }) {
                return@update plan
            }
            val newDaily = plan.dailyPlans.map { dayPlan ->
                dayPlan.copy(
                    places = dayPlan.places.map { place ->
                        if (place.name == placeName && place.coverUrl == null) {
                            place.copy(coverUrl = coverUrl)
                        } else {
                            place
                        }
                    },
                )
            }
            plan.copy(
                dailyPlans = newDaily,
                allPlaceList = newDaily.flatMap { it.places },
            )
        }
        persist { saveTravelPlan(_currentTravelPlan.value) }
    }

    /** AI 地点类型 → 行程点位类型映射：景点类→景点，美食→饭店，网红探店→探店，酒店住宿→酒店 */
    private fun AiSpotType.toPlaceType(): PlaceType = when (this) {
        AiSpotType.POPULAR_SPOT -> PlaceType.SPOT
        AiSpotType.NICHE_SPOT -> PlaceType.SPOT
        AiSpotType.LOCAL_FOOD -> PlaceType.RESTAURANT
        AiSpotType.INTERNET_FAMOUS_SHOP -> PlaceType.SHOP
        AiSpotType.HOTEL -> PlaceType.HOTEL
    }

    /**
     * AI 攻略 / 对话 Agent 返回的 [AiTravelPlan] → 内部 [TravelPlan]：
     * 保持模型推荐顺序，并按 [days] 把地点分到对应天：
     * - 模型为每个地点返回了合法 day（1..days）时优先采用；
     * - 缺失 / 非法 day 的地点均匀补入点位最少的天；
     * - 模型分配严重不均（某天为空）时，从多点位的天末尾匀一个点位到空天；
     * - 全部缺失 day（如单日请求 / 旧缓存）退化为按顺序均匀分组；
     * - 实际天数不超过地点数，避免出现整天无地点的空卡片。
     *
     * 「一键生成攻略」与「对话中修改行程」共用，保证两条路径渲染一致。
     *
     * @param trustModelDays 对话同步场景为 true：模型已在协议中显式给出 day 分组
     *   （含跨城市转场、同酒店连住等语义），只要分组结构合法就【原样按天落库】，
     *   不再做地理重排，避免把「五天四晚」这类结构性改动被聚类重新聚合错；
     *   day 缺失 / 编号有空洞 / 某天无任何非酒店点时回退到地理聚类兜底。
     */
    private fun buildTravelPlanFromAi(
        aiPlan: AiTravelPlan,
        fallbackCity: String,
        days: Int = 1,
        trustModelDays: Boolean = false,
    ): TravelPlan {
        val places = aiPlan.spotList.mapIndexed { index, spot ->
            spot.toPlace(id = index.toLong() + 1L, dayIndex = 1)
        }

        // 对话同步优先路径：模型 day 分组合法时直接按天落库（同名连住酒店也按晚数保留）
        if (trustModelDays) {
            groupSpotsByModelDay(aiPlan.spotList)?.let { groups ->
                val placeByIndex = aiPlan.spotList.indices.associateWith { idx -> places[idx] }
                val directDailyPlans = groups.mapIndexed { dayIdx, indices ->
                    val day = dayIdx + 1
                    DayPlan(
                        day = day,
                        places = indices.map { idx ->
                            placeByIndex.getValue(idx).copy(dayIndex = day)
                        },
                    )
                }
                return TravelPlan(
                    destination = aiPlan.city.ifBlank { fallbackCity },
                    totalDays = directDailyPlans.size,
                    dailyPlans = directDailyPlans,
                    allPlaceList = directDailyPlans.flatMap { it.places },
                )
            }
        }

        // 单日请求（含默认 1 天 / 旧缓存）：全部归入第 1 天，保持原有行为
        if (days <= 1 || places.size <= 1) {
            return TravelPlan(
                destination = aiPlan.city.ifBlank { fallbackCity },
                totalDays = 1,
                dailyPlans = listOf(DayPlan(day = 1, places = places)),
                allPlaceList = places,
            )
        }

        // 多日：确定性地理就近聚类二次编排（不盲信模型 day），消除同日跨区县飞点并顺路排序。
        // 模型 day 仅作为「片区种子」提示；GeoItem 与 Place 通过 id 一一对应回溯。
        val placeById = places.associateBy { it.id }
        val geoItems = aiPlan.spotList.mapIndexed { index, spot ->
            GeoItem(
                id = places[index].id,
                name = spot.name,
                lat = spot.latitude,
                lng = spot.longitude,
                isHotel = spot.type == AiSpotType.HOTEL,
                hintDay = spot.day,
            )
        }
        val dailyPlans = GeoRoutePlanner.plan(geoItems, requestedDays = days).map { geoDay ->
            DayPlan(
                day = geoDay.day,
                places = geoDay.items.mapNotNull { geoItem ->
                    placeById[geoItem.id]?.copy(dayIndex = geoDay.day)
                },
            )
        }
        return TravelPlan(
            destination = aiPlan.city.ifBlank { fallbackCity },
            totalDays = dailyPlans.size,
            dailyPlans = dailyPlans,
            allPlaceList = dailyPlans.flatMap { it.places },
        )
    }

    /** [AiSpot] → 内部 [Place]；dayIndex 由调用方按最终分组赋值（此处先给 1）。 */
    private fun AiSpot.toPlace(id: Long, dayIndex: Int): Place = Place(
        id = id,
        name = name,
        placeType = type.toPlaceType(),
        dayIndex = dayIndex,
        lat = latitude,
        lng = longitude,
        intro = listOf(desc, tip)
            .filter { it.isNotBlank() }
            .joinToString("\n"),
        petFriendly = isPetFriendly,
        // 数据库预置扩展属性原样透传（含「暂无相关信息」），null 表示攻略文本无此字段
        ticket = ticket,
        avgPrice = avgPrice,
        signatureDish = signatureDish,
        photoScene = photoScene,
        travelTip = travelTip,
        timeSlot = timeSlot,
        // 来源标签：本地库预置点位 vs AI 拓展点位
        fromLocalDb = source == AiSpotSource.LOCAL_DB,
        // 高德未命中时的城市中心兜底坐标（点位不丢弃，UI 可弱提示）
        mockedCoord = mockedCoord,
    )

    /**
     * 校验模型给的 day 分组并按天直接分组（对话同步专用）：
     * - 返回长度 = max(day) 的列表，元素为该天地点在原 spotList 中的下标，
     *   非酒店点保持模型顺序在前、酒店固定在当天末尾；
     * - 任一地点 day 缺失/越界、day 编号出现空洞、或某天没有任何非酒店点时返回 null，
     *   由 [GeoRoutePlanner] 地理聚类兜底。
     */
    private fun groupSpotsByModelDay(spots: List<AiSpot>): List<List<Int>>? {
        if (spots.isEmpty()) return null
        val maxDay = spots.mapNotNull { it.day }.maxOrNull()
        if (maxDay == null || maxDay !in 1..AiPlanOptions.MAX_DAYS) return null
        val buckets = Array(maxDay) { mutableListOf<Int>() }
        spots.forEachIndexed { idx, spot ->
            val d = spot.day ?: return null
            if (d !in 1..maxDay) return null
            buckets[d - 1].add(idx)
        }
        // 每一天至少有 1 个非酒店点（禁止空天），bucket 连续非空即 day 编号无空洞
        if (buckets.any { indices -> indices.none { spots[it].type != AiSpotType.HOTEL } }) {
            return null
        }
        return buckets.map { indices ->
            val (nonHotels, hotels) = indices.partition { spots[it].type != AiSpotType.HOTEL }
            nonHotels + hotels
        }
    }

    /* ------------------------------ 豆包 AI 对话助手 ------------------------------ */

    /** 聊天记录（内存态驱动 UI；绑定当前行程持久化到 Room chat_history，重启自动恢复） */
    private val _chatMessageList = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessageList: StateFlow<List<ChatMessage>> = _chatMessageList.asStateFlow()

    /** 是否正在等待豆包回复（控制输入框 / 发送按钮与「正在思考」loading） */
    private val _chatLoading = MutableStateFlow(false)
    val chatLoading: StateFlow<Boolean> = _chatLoading.asStateFlow()

    /** 是否展示计划页的 AI 助手对话弹窗 */
    private val _showChatDialog = MutableStateFlow(false)
    val showChatDialog: StateFlow<Boolean> = _showChatDialog.asStateFlow()

    /** 打开 AI 助手弹窗（聊天记录保留） */
    fun openChatDialog() {
        _showChatDialog.value = true
    }

    /** 关闭 AI 助手弹窗：不清空聊天记录，并自动把当前对话整段保存到 Room */
    fun closeChatDialog() {
        _showChatDialog.value = false
        saveChatHistory()
    }

    /**
     * 正在执行「同步行程提议」的消息 id（非 null 时该消息按钮 loading/禁用，防重复点击导致
     * 行程被重复写入）；同一时刻只允许一个同步任务。
     */
    private val _proposalSyncingMessageId = MutableStateFlow<Long?>(null)
    val proposalSyncingMessageId: StateFlow<Long?> = _proposalSyncingMessageId.asStateFlow()

    /** 对话提议相关的一次性提示事件（成功 / 失败 Toast），UI 收集后展示 */
    private val _chatToastEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val chatToastEvents: SharedFlow<String> = _chatToastEvents.asSharedFlow()

    /**
     * 发送一条用户消息并请求豆包：
     * 1. 用户消息立即上屏；置 loading；
     * 2. 携带【当前城市 + 当前完整行程 + 历史聊天】调用 [AgentRepository]；
     * 3. 回复上屏：本轮带回可解析行程方案时，把方案原文挂到助手消息上、状态=待确认
     *    （PENDING），同时把此前所有待确认消息置为已过时（SUPERSEDED）；
     *    【不会立即修改行程】——必须等用户点击该消息上的「同步修改行程」；
     * 4. 任何失败以 AI 气泡形式提示，不崩溃、不吞消息。
     */
    fun sendChatMessage(message: String) {
        val content = message.trim()
        if (content.isEmpty() || _chatLoading.value) return

        val userMessage = ChatMessage(
            id = nextChatMessageId(),
            content = content,
            isUser = true,
            timestamp = System.currentTimeMillis(),
        )
        // 历史记录 = 上屏前的已有消息（不含本条），随请求带给模型做多轮上下文
        val history = _chatMessageList.value
        _chatMessageList.update { it + userMessage }
        _chatLoading.value = true
        saveChatHistory()

        viewModelScope.launch {
            // 仅暂存提议原文，行程在用户点击确认前保持不变
            var proposalJson: String? = null
            val replyText = try {
                val result = agentRepository.chat(
                    AgentChatRequest(
                        city = _currentCity.value,
                        currentPlan = _currentTravelPlan.value,
                        history = history,
                        userMessage = content,
                    ),
                )
                proposalJson = result.planProposalJson
                result.replyText
            } catch (e: AgentException) {
                Log.w(TAG, "豆包对话失败", e)
                "抱歉，${e.message}"
            } catch (e: Exception) {
                Log.w(TAG, "豆包对话发生未知异常", e)
                "抱歉，对话服务暂时不可用：${e.message ?: "请稍后重试"}"
            }

            val hasProposal = !proposalJson.isNullOrBlank()
            val assistantMessageId = nextChatMessageId()
            _chatMessageList.update { current ->
                // 新提议产生时，旧的待确认提议一律作废（已同步 / 已放弃为终态，不动）
                val withSuperseded = if (hasProposal) {
                    current.map { msg ->
                        if (msg.proposalStatus == ProposalStatus.PENDING) {
                            msg.copy(proposalStatus = ProposalStatus.SUPERSEDED)
                        } else {
                            msg
                        }
                    }
                } else {
                    current
                }
                withSuperseded + ChatMessage(
                    id = assistantMessageId,
                    content = replyText.ifBlank { "（助手未返回内容）" },
                    isUser = false,
                    timestamp = System.currentTimeMillis(),
                    planJson = proposalJson,
                    proposalStatus = if (hasProposal) ProposalStatus.PENDING else ProposalStatus.NONE,
                )
            }
            _chatLoading.value = false
            // 本轮对话完成即落库（崩溃 / 进程被杀也不丢；关闭弹窗时会再保存一次）
            saveChatHistory()
        }
    }

    /**
     * 用户点击某条助手消息的「同步修改行程」：
     * 仅 PENDING 消息可操作；解析方案原文 → 复用 [applyAgentUpdatedPlan] 替换当前行程
     * （天数以方案为准：用户把 4 天改成「五天四晚」时同步为 5 天；多日走地理聚类、
     * 异步配图）→ 消息置 APPLIED；失败保持 PENDING，发 Toast 事件，不崩溃。
     */
    fun applyChatProposal(messageId: Long) {
        if (_proposalSyncingMessageId.value != null) return
        val target = _chatMessageList.value.firstOrNull { it.id == messageId } ?: return
        val raw = target.planJson
        if (target.proposalStatus != ProposalStatus.PENDING || raw.isNullOrBlank()) return

        _proposalSyncingMessageId.value = messageId
        viewModelScope.launch {
            try {
                val aiPlan = AiGuideParser.parse(
                    city = _currentCity.value,
                    rawContent = raw,
                    nowMillis = System.currentTimeMillis(),
                )
                if (aiPlan.spotList.isEmpty()) {
                    _chatToastEvents.emit("方案内容为空，未同步行程")
                } else {
                    // 先改行程再翻消息状态，保证 APPLIED 与行程实际一致
                    applyAgentUpdatedPlan(aiPlan)
                    updateChatMessage(messageId) { it.copy(proposalStatus = ProposalStatus.APPLIED) }
                    saveChatHistory()
                    _chatToastEvents.emit("已同步到出行计划")
                }
            } catch (e: Exception) {
                Log.w(TAG, "同步行程提议失败，方案保持待确认", e)
                _chatToastEvents.emit("同步失败：${e.message ?: "请稍后重试"}")
            } finally {
                _proposalSyncingMessageId.value = null
            }
        }
    }

    /** 用户点击「暂不」：仅把待确认消息置为终态 DECLINED，行程不动。 */
    fun declineChatProposal(messageId: Long) {
        val target = _chatMessageList.value.firstOrNull { it.id == messageId } ?: return
        if (target.proposalStatus != ProposalStatus.PENDING) return
        updateChatMessage(messageId) { it.copy(proposalStatus = ProposalStatus.DECLINED) }
        saveChatHistory()
    }

    /** 按消息 id 局部更新一条聊天消息（找不到则不改动）。 */
    private fun updateChatMessage(messageId: Long, transform: (ChatMessage) -> ChatMessage) {
        _chatMessageList.update { list ->
            list.map { msg -> if (msg.id == messageId) transform(msg) else msg }
        }
    }

    /** 对话中模型返回新行程：内存 + Room 替换当前行程，并异步为新地点补实拍图。 */
    private suspend fun applyAgentUpdatedPlan(aiPlan: AiTravelPlan) {
        // 天数以方案为准：取方案中最大的合法 day（如用户要求「五天四晚」→ day 覆盖 1..5），
        // 方案完全没给 day（纯单日 / 旧格式）才回退当前行程天数；
        // GeoRoutePlanner 会再把天数收敛到不超过非酒店点位个数，杜绝空天卡片。
        val currentDays = _currentTravelPlan.value.totalDays.coerceAtLeast(1)
        val proposedDays = aiPlan.spotList.mapNotNull { it.day }.maxOrNull()?.coerceAtLeast(1)
        val days = proposedDays ?: currentDays
        // 模型给了合法 day（对话协议强制要求）时直接按模型的天/顺序/酒店晚数落库，
        // 不再地理重排；day 全缺（旧格式）才走聚类兜底
        val newPlan = buildTravelPlanFromAi(
            aiPlan, _currentCity.value, days = days, trustModelDays = proposedDays != null,
        )
        repository.saveTravelPlan(newPlan)
        _currentTravelPlan.value = newPlan
        // 复用攻略生成的异步配图：新地点封面逐张填充，失败保持占位
        enrichPlaceCovers(aiPlan)
    }

    /** 会话内自增消息 ID（内存态，用当前列表最大 id + 1 保证稳定唯一） */
    private fun nextChatMessageId(): Long =
        (_chatMessageList.value.maxOfOrNull { it.id } ?: 0L) + 1L

    /**
     * 启动一次聊天记录恢复（init / 非协程上下文用）；App 当前同时只维护一份行程，
     * 固定绑定 [AppDatabase.CURRENT_PLAN_ID]。
     */
    private fun loadChatHistory() {
        viewModelScope.launch {
            chatHistoryRepository.observeMessages(AppDatabase.CURRENT_PLAN_ID).collect { messages ->
                _chatMessageList.value = messages
            }
        }
    }

    /** 从 Room 读取指定行程绑定的聊天记录并覆盖到内存（库内按时间升序，id 已重排） */
    private suspend fun restoreChatHistory(planId: Long = AppDatabase.CURRENT_PLAN_ID) {
        runCatching { chatHistoryRepository.loadMessages(planId) }
            .onSuccess { _chatMessageList.value = it }
            .onFailure { Log.w(TAG, "读取聊天记录失败", it) }
    }

    /**
     * 把当前对话整段覆盖保存到 Room（绑定当前行程）；
     * 异步落库、失败仅记日志，不回滚 UI、不影响对话。
     */
    private fun saveChatHistory() {
        val snapshot = _chatMessageList.value
        viewModelScope.launch {
            runCatching {
                chatHistoryRepository.replaceMessages(AppDatabase.CURRENT_PLAN_ID, snapshot)
            }.onFailure { Log.w(TAG, "保存聊天记录失败", it) }
        }
    }

    val tripSyncState: StateFlow<TripSyncUiState> = tripSync?.state ?: MutableStateFlow(TripSyncUiState())

    fun retryTripSync() { tripSync?.retry() }
    fun selectSavedTrip(id: String) {
        if (_chatLoading.value || _travelTaskState.value is TravelTaskState.Submitting ||
            _travelTaskState.value is TravelTaskState.Polling || _proposalSyncingMessageId.value != null) {
            viewModelScope.launch { _chatToastEvents.emit("请等待当前生成或对话任务完成后切换行程") }
            return
        }
        _showChatDialog.value = false
        coverJob?.cancel()
        _pendingAddDay.value = null
        _mapFocusPlace.value = null
        _planFocusPlace.value = null
        _placeCoverMap.value = emptyMap()
        tripSync?.selectTrip(id)
    }
    fun resolveTripConflict(id: String, useCloud: Boolean) {
        if (_chatLoading.value || _proposalSyncingMessageId.value != null ||
            _travelTaskState.value is TravelTaskState.Submitting || _travelTaskState.value is TravelTaskState.Polling) return
        tripSync?.resolveConflict(id, useCloud)
    }

    init {
        // App 初始化启动：订阅本地库全部数据，首次订阅即触发首次加载，
        // 之后任何路径写入 Room（含本类自己的写操作）都会自动推送到这里。
        observeLocalData()
        // 读取 DataStore 中持久化的简单配置（深色开关 / 当前城市），重启后自动恢复
        observeSettings()
        // 恢复当前行程绑定的 AI 助手聊天记录（Room v3 chat_history），重启后对话不丢
        loadChatHistory()
    }

    init {
        tripSync?.canApplyRemote = {
            !_chatLoading.value && _proposalSyncingMessageId.value == null &&
                _travelTaskState.value !is TravelTaskState.Submitting && _travelTaskState.value !is TravelTaskState.Polling
        }
        tripSync?.start(viewModelScope)
        if (tripSync != null) viewModelScope.launch {
            tripSync.activeDocument.collect { doc ->
                _tripConfig.value = doc?.options
                doc?.plan?.destination?.takeIf { it.isNotBlank() }?.let { city ->
                    _currentCity.value = city
                    settingsDataStore.setCurrentCity(city)
                }
            }
        }
        if (tripSync != null) viewModelScope.launch {
            var previousId: String? = null
            tripSync.activeTripId.collect { id ->
                if (coverJobTripId != id) {
                    coverJob?.cancel()
                    _placeCoverMap.value = emptyMap()
                }
                if (previousId != null && previousId != id) {
                    _showChatDialog.value = false
                    _pendingAddDay.value = null
                    _mapFocusPlace.value = null
                    _planFocusPlace.value = null
                }
                previousId = id
            }
        }
    }

    /* ------------------------------------ 配套 ------------------------------------ */

    companion object {
        private const val TAG = "AppViewModel"

        /** 异步攻略任务轮询间隔：1.5s 一次 */
        private const val POLL_INTERVAL_MS = 1500L

        /**
         * 异步攻略任务本地安全保护上限（提交成功拿到 taskId 后开始计时）：300s。
         * 后端火山方舟生成约需 100~180s，只要 status 不是失败就持续轮询，不提前截断；
         * 仅到该上限仍未拿到 data 时才判本地超时。
         */
        private const val TASK_TOTAL_TIMEOUT_MS = 300_000L

        /** 默认当前城市（与 App 初始目的地保持一致：成都） */
        const val DEFAULT_CITY = "成都"

        /** 地点封面表 key：城市 + 地点名，避免不同城市同名地点的图片串用 */
        fun coverKey(city: String, placeName: String): String = "$city|$placeName"

        /** 浏览记录保留条数上限 */
        const val MAX_BROWSE_HISTORY = 50

        /**
         * 基于 [AppServiceLocator] 提供的 Repository 构造 ViewModel 的工厂，
         * 供后续页面以 Activity 作用域获取（本步不接入任何 Compose 页面）。
         */
        fun factory(accountKey: String = AccountStorage.keyFor(
            AccountSession.currentAccount.value?.takeIf { it.token.isNotBlank() }?.phone,
        )): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = AppDatabase.get(AppServiceLocator.requireAppContext(), accountKey)
                val sync = TripSyncCoordinator(db, RemoteTripCloud(), AccountSession.snapshot())
                return AppViewModel(
                    repository = sync.repository,
                    settingsDataStore = AppServiceLocator.requireSettingsDataStore(accountKey),
                    imageRepository = AppServiceLocator.requireImageRepository(),
                    agentRepository = AppServiceLocator.requireAgentRepository(),
                    chatHistoryRepository = sync.chatRepository,
                    hotelRepository = AppServiceLocator.requireHotelRepository(),
                    tripSync = sync,
                ) as T
            }
        }
    }
}

/**
 * 行程配置弹窗「后端 AI 攻略异步任务」UI 状态。
 *
 * 流程：提交 POST /travel/generateTask 拿 taskId（[Submitting]）→
 * 后台每 1.5s 轮询 GET /travel/task/{taskId}（[Polling]，切页面 / 返回 / 关闭弹窗都不停，
 * 只有拿到 data 或 300s 本地安全上限才停止；status 只要不是失败就持续轮询）→
 * 拿到 data 中的 Markdown 后自动解析并同步行程，成功只发全局 Toast（状态回 [Idle]）；
 * 300s 本地超时 / 提交失败 / 后端明确业务失败进入 [Failed]，弹窗展示具体原因。
 */
sealed interface TravelTaskState {
    /** 空闲：未提交 / 任务已完成 */
    data object Idle : TravelTaskState

    /** 步骤 1 进行中：POST 提交任务、等待 taskId 返回（配置弹窗按钮 loading） */
    data object Submitting : TravelTaskState

    /** 步骤 2 进行中：已拿到 [taskId]，后台正在轮询，不依赖任何页面存活 */
    data class Polling(val taskId: String) : TravelTaskState

    /**
     * 攻略文字已成功生成，但提取到的地点在高德地图【一个都没能匹配上】
     * （0 个「- 」地点，或全部地理编码失败）。这不是接口 / 网络失败，
     * 不改动既有行程，弹窗提示用户可手动添加景点。
     */
    data object GuideReadyButNoMatch : TravelTaskState

    /**
     * 攻略生成 + 行程同步均成功：[guide] 为后端返回的 data 攻略原文，
     * UI 弹窗展示该文本；用户点【我知道了】后由 [resetTravelGuideTask] 复位为 [Idle]。
     */
    data class Succeeded(val guide: String) : TravelTaskState

    /** 提交被拒 / 轮询网络异常 / 后端业务失败 / 300s 本地超时：[message] 为可直接展示的具体原因 */
    data class Failed(val message: String) : TravelTaskState
}
