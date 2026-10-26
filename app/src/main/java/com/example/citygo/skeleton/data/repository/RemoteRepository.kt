package com.example.citygo.skeleton.data.repository

import com.example.citygo.skeleton.data.model.Diary
import com.example.citygo.skeleton.data.model.Pet
import com.example.citygo.skeleton.data.model.Post
import com.example.citygo.skeleton.data.model.Spot
import com.example.citygo.skeleton.data.model.TravelPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/* =====================================================================================
 * RemoteRepository —— 后端网络 Repository 空实现（预留对接位）
 *
 * 【当前状态】
 * 本类是 [Repository] 顶层接口的「空实现 / 桩实现」，方法签名与接口完全一致，
 * 但不包含任何真实网络逻辑：
 * - 查询类方法统一返回空数据（空列表 / null），保证 UI 订阅后直接进入空状态，不崩溃；
 * - 写入类方法为空实现（安全空转），返回 id 的方法暂返回 0L（表示未由后端分配）。
 *
 * 【后续如何对接后端】
 * 1. 在本类中注入 Retrofit / OkHttp 等网络服务（构造参数预留，见下方 class 声明）；
 * 2. 逐个把每个方法体内的「TODO 待对接后端」替换为真实网络请求：
 *    - 查询：把挂起接口包成 Flow（如 flow { emit(api.xxx()) }），
 *      或用 Room 做本地缓存 + 网络刷新（远端拉取后写库，UI 继续订阅库 Flow）；
 *    - 写入：调用后端 POST/PUT/DELETE，按后端返回结果决定是否同步本地缓存。
 * 3. 替换实例：全局只在 AppServiceLocator.init(...) 中创建了一次 Repository
 *    （当前为 LocalRepository）。后端就绪后，只需把该处的实例换成 RemoteRepository
 *    （或新增「Local + Remote 同步」的组合实现），AppViewModel.factory() 经由
 *    AppServiceLocator.requireRepository() 自动拿到新实现。
 *
 * 【为什么 UI / ViewModel 不需要大规模改动】
 * - AppViewModel 只依赖 [Repository] 接口，从不直接接触 Room / Retrofit；
 * - 所有 Compose 页面只订阅 AppViewModel 的 StateFlow、调用其业务方法，
 *   数据来自 Room 还是后端对页面完全透明；
 * - 因此切换实现后，UI 与 ViewModel 零改动（或仅微调加载态 / 错误态），
 *   符合「面向接口编程、依赖倒置」的分层约定。
 *
 * 注意：本类与高德导航、天气预报代码完全无关，不涉及任何地图 / 天气逻辑。
 * ===================================================================================== */
class RemoteRepository /* TODO 待对接后端：构造注入网络服务，例如：
    private val api: CityGoApiService,
*/ : Repository {

    /* -------------------------------------- 宠物 -------------------------------------- */

    // TODO 待对接后端：GET 宠物档案列表（按创建顺序）
    override fun observePets(): Flow<List<Pet>> = flowOf(emptyList())

    // TODO 待对接后端：POST 新增宠物，成功后应返回后端分配的真实 id（当前桩返回 0L）
    override suspend fun addPet(pet: Pet): Long = 0L

    // TODO 待对接后端：PUT 更新宠物信息（按 pet.id 匹配）
    override suspend fun updatePet(pet: Pet) = Unit

    // TODO 待对接后端：DELETE 删除指定 id 的宠物
    override suspend fun deletePet(petId: Long) = Unit

    /* -------------------------------------- 行程 -------------------------------------- */

    // TODO 待对接后端：GET 当前行程（无行程时发射 null；建议主表+子项一次聚合返回）
    override fun observeCurrentTravelPlan(): Flow<TravelPlan?> = flowOf(null)

    // TODO 待对接后端：PUT 整体保存当前行程（主表 + 多日子项，建议后端单事务覆盖）
    override suspend fun saveTravelPlan(plan: TravelPlan) = Unit

    // TODO 待对接后端：DELETE 清空当前行程（主表与子项一并删除）
    override suspend fun clearTravelPlan() = Unit

    /* ------------------------------------- 收藏景点 ------------------------------------ */

    // TODO 待对接后端：GET 收藏景点列表（最新收藏在前）
    override fun observeCollectedSpots(): Flow<List<Spot>> = flowOf(emptyList())

    // TODO 待对接后端：POST 收藏景点（后端按 spotId 幂等去重并刷新排序）
    override suspend fun addCollectedSpot(spot: Spot) = Unit

    // TODO 待对接后端：DELETE 按景点 id 取消收藏
    override suspend fun removeCollectedSpot(spotId: String) = Unit

    /* ------------------------------------- 收藏帖子 ------------------------------------ */

    // TODO 待对接后端：GET 收藏帖子列表（最新收藏在前）
    override fun observeCollectedPosts(): Flow<List<Post>> = flowOf(emptyList())

    // TODO 待对接后端：POST 收藏帖子（后端按 postId 幂等去重并刷新排序）
    override suspend fun addCollectedPost(post: Post) = Unit

    // TODO 待对接后端：DELETE 按帖子 id 取消收藏
    override suspend fun removeCollectedPost(postId: Long) = Unit

    /* ------------------------------------- 浏览记录 ------------------------------------ */

    // TODO 待对接后端：GET 景点浏览记录（最近浏览在前）
    override fun observeBrowseHistory(): Flow<List<Spot>> = flowOf(emptyList())

    // TODO 待对接后端：POST 上报一次浏览（后端按景点去重置顶，可做异步埋点上报）
    override suspend fun addBrowseHistory(spot: Spot) = Unit

    // TODO 待对接后端：DELETE 清空全部浏览记录
    override suspend fun clearBrowseHistory() = Unit

    /* -------------------------------------- 日记 -------------------------------------- */

    // TODO 待对接后端：GET 旅行日记列表（最新保存在前）
    override fun observeDiaries(): Flow<List<Diary>> = flowOf(emptyList())

    // TODO 待对接后端：POST 保存日记（新增 / 编辑通用，成功后返回后端真实 id，当前桩返回 0L）
    override suspend fun saveDiary(diary: Diary): Long = 0L

    // TODO 待对接后端：DELETE 删除指定 id 的日记
    override suspend fun deleteDiary(diaryId: Long) = Unit

    /* ------------------------------------- 社区帖子 ------------------------------------ */

    // TODO 待对接后端：GET 社区游记信息流（最新发布在前；建议支持分页）
    override fun observePosts(): Flow<List<Post>> = flowOf(emptyList())

    // TODO 待对接后端：POST 发布帖子（成功后返回后端分配的真实 id，当前桩返回 0L）
    override suspend fun addPost(post: Post): Long = 0L

    // TODO 待对接后端：POST 点赞 / 取消点赞（likeCount 建议以后端返回的权威计数为准）
    override suspend fun updatePostLike(postId: Long, liked: Boolean, likeCount: Int) = Unit

    // TODO 待对接后端：POST 帖子收藏标记变更（需与 addCollectedPost/removeCollectedPost 联动）
    override suspend fun updatePostCollected(postId: Long, collected: Boolean) = Unit
}
