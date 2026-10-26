package com.example.citygo.skeleton.data.repository

import com.example.citygo.skeleton.data.model.Diary
import com.example.citygo.skeleton.data.model.Pet
import com.example.citygo.skeleton.data.model.Post
import com.example.citygo.skeleton.data.model.Spot
import com.example.citygo.skeleton.data.model.TravelPlan
import com.example.citygo.skeleton.data.model.AiPlanOptions
import kotlinx.coroutines.flow.Flow

/* =====================================================================================
 * 全局业务 Repository 顶层接口
 *
 * ViewModel 只允许依赖本接口操作全部用户业务数据，禁止直接依赖 Room DAO / 数据库：
 * 这样本地实现（LocalRepository）未来可被「Room + 远端同步」实现替换而不动 ViewModel。
 *
 * 约定：
 * - 所有查询返回冷 [Flow]，底层数据变化时自动发射最新列表 / 对象；
 * - 所有写操作为 suspend，由调用方在协程中触发；
 * - 参数 / 返回值一律使用领域模型（Pet / Spot / Post / Diary / TravelPlan），
 *   Room 实体不出数据层。
 *
 * 业务范围：宠物、当前行程（主表+子项）、景点收藏、帖子收藏、
 *           浏览记录、旅行日记、社区帖子（含本地模拟点赞 / 收藏）。
 * ===================================================================================== */

interface Repository {

    /* -------------------------------------- 宠物 -------------------------------------- */

    /** 监听全部宠物档案（按创建顺序） */
    fun observePets(): Flow<List<Pet>>

    /** 新增宠物（pet.id 为 0 时由存储层分配），返回落库后的宠物 id */
    suspend fun addPet(pet: Pet): Long

    /** 更新宠物信息（按 pet.id 匹配） */
    suspend fun updatePet(pet: Pet)

    /** 删除指定 id 的宠物 */
    suspend fun deletePet(petId: Long)

    /* -------------------------------------- 行程 -------------------------------------- */

    /**
     * 监听当前行程：
     * - 从未创建 / 已清空时发射 null（ViewModel 可据此展示空态或转成 EMPTY_TRAVEL_PLAN）；
     * - 行程主表或任一子项变化都会自动重新发射完整 [TravelPlan]。
     */
    fun observeCurrentTravelPlan(): Flow<TravelPlan?>

    /**
     * 整体保存当前行程（主表 + 多日子项）：
     * 子项按领域行程的 day / 列表顺序全量覆盖写入，同一事务内完成。
     */
    suspend fun saveTravelPlan(plan: TravelPlan)
    suspend fun startNewTravelPlan(plan: TravelPlan, options: AiPlanOptions? = null) = saveTravelPlan(plan)

    /** 清空当前行程（删除主表行，子项级联删除） */
    suspend fun clearTravelPlan()

    /* ------------------------------------- 收藏景点 ------------------------------------ */

    /** 监听全部收藏景点（最新收藏在前） */
    fun observeCollectedSpots(): Flow<List<Spot>>

    /** 收藏景点（重复收藏同一景点时刷新排序，不产生重复项） */
    suspend fun addCollectedSpot(spot: Spot)

    /** 按景点 id 取消收藏 */
    suspend fun removeCollectedSpot(spotId: String)

    /* ------------------------------------- 收藏帖子 ------------------------------------ */

    /** 监听全部收藏帖子（最新收藏在前） */
    fun observeCollectedPosts(): Flow<List<Post>>

    /** 收藏帖子（重复收藏同一帖子时刷新排序，不产生重复项） */
    suspend fun addCollectedPost(post: Post)

    /** 按帖子 id 取消收藏 */
    suspend fun removeCollectedPost(postId: Long)

    /* ------------------------------------- 浏览记录 ------------------------------------ */

    /** 监听全部景点浏览记录（最近浏览在前） */
    fun observeBrowseHistory(): Flow<List<Spot>>

    /** 新增一条浏览记录（同一景点再次浏览时去重置顶） */
    suspend fun addBrowseHistory(spot: Spot)

    /** 清空全部浏览记录 */
    suspend fun clearBrowseHistory()

    /* -------------------------------------- 日记 -------------------------------------- */

    /** 监听全部旅行日记（最新保存在前） */
    fun observeDiaries(): Flow<List<Diary>>

    /** 保存日记（新增 / 编辑通用，diary.id 为 0 时分配新 id），返回落库 id */
    suspend fun saveDiary(diary: Diary): Long

    /** 删除指定 id 的日记 */
    suspend fun deleteDiary(diaryId: Long)

    /* ------------------------------------- 社区帖子 ------------------------------------ */

    /** 监听全部社区游记帖子（最新发布在前） */
    fun observePosts(): Flow<List<Post>>

    /** 发布社区帖子（post.id 为 0 时分配新 id），返回落库后的帖子 id */
    suspend fun addPost(post: Post): Long

    /**
     * 本地模拟点赞修改：直接更新帖子的点赞状态与点赞数。
     * @param liked true=已点赞，false=取消点赞
     * @param likeCount 更新后的点赞总数（由调用方算好，避免并发加减不一致）
     */
    suspend fun updatePostLike(postId: Long, liked: Boolean, likeCount: Int)

    /**
     * 本地模拟收藏修改：更新帖子自身的收藏标记。
     * 注意：收藏列表的增删另由 [addCollectedPost] / [removeCollectedPost] 维护，
     * 两处需配合调用，信息流与收藏页状态才能一致。
     */
    suspend fun updatePostCollected(postId: Long, collected: Boolean)
}
