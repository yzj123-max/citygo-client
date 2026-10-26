package com.example.citygo.skeleton.data.local.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.Transaction
import androidx.room.Embedded
import androidx.room.Relation
import kotlinx.coroutines.flow.Flow

/* =====================================================================================
 * App 业务库 DAO 集合（8 个）
 *
 * 统一约定：
 * - 所有「查询 / 监听」类方法返回 Room 冷 Flow，表数据变化时自动发射新值，
 *   页面 / ViewModel 无需手动刷新；
 * - 写方法均为 suspend，在 IO 调度器由 Repository 调用；
 * - 主键冲突策略按业务区分：收藏 / 浏览记录用 REPLACE（重复操作=置顶），
 *   编辑保存用 @Update / @Upsert。
 * ===================================================================================== */

/* -------------------------------------- 宠物 -------------------------------------- */

/** 宠物档案 DAO */
@Dao
interface PetDao {

    /** 监听全部宠物（按 id 升序，即创建顺序） */
    @Query("SELECT * FROM pet ORDER BY id ASC")
    fun observePets(): Flow<List<PetEntity>>

    /** 新增宠物（id=0 自增），返回新行 id */
    @Insert
    suspend fun insertPet(pet: PetEntity): Long

    /** 按主键更新宠物信息 */
    @Update
    suspend fun updatePet(pet: PetEntity)

    /** 删除指定 id 的宠物 */
    @Query("DELETE FROM pet WHERE id = :petId")
    suspend fun deletePetById(petId: Long)
}

/* -------------------------------------- 行程 -------------------------------------- */

/** 行程主表 DAO（App 同时只维护一份「当前行程」，主键固定为 1） */
@Dao
interface TravelPlanDao {

    @Transaction
    @Query("SELECT * FROM travel_plan WHERE id=:planId LIMIT 1")
    fun observePlanWithItems(planId: Long): Flow<TravelPlanWithItems?>

    /** 监听当前行程主表行，无行程时发射 null */
    @Query("SELECT * FROM travel_plan WHERE id = :planId LIMIT 1")
    fun observePlanById(planId: Long): Flow<TravelPlanEntity?>

    /** 一次性读取当前行程主表行（事务内部使用） */
    @Query("SELECT * FROM travel_plan WHERE id = :planId LIMIT 1")
    suspend fun getPlanById(planId: Long): TravelPlanEntity?

    /** 插入或更新当前行程主表行 */
    @Upsert
    suspend fun upsertPlan(plan: TravelPlanEntity)

    /** 删除当前行程（子项由外键级联删除） */
    @Query("DELETE FROM travel_plan WHERE id = :planId")
    suspend fun deletePlanById(planId: Long)
}

data class TravelPlanWithItems(
    @Embedded val plan: TravelPlanEntity,
    @Relation(parentColumn = "id", entityColumn = "planId") val items: List<PlanItemEntity>,
)

/** 行程子项 DAO */
@Dao
interface PlanItemDao {

    @Query("SELECT * FROM plan_item WHERE planId=:planId ORDER BY day ASC, sortOrder ASC")
    suspend fun getItemsByPlan(planId: Long): List<PlanItemEntity>

    /** 监听某行程的全部子项（按天、天内顺序排序） */
    @Query("SELECT * FROM plan_item WHERE planId = :planId ORDER BY day ASC, sortOrder ASC")
    fun observeItemsByPlan(planId: Long): Flow<List<PlanItemEntity>>

    /** 全量覆盖写入某行程的子项（先清空再批量插入，由 Repository 包在事务里） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<PlanItemEntity>)

    /** 删除某行程的全部子项 */
    @Query("DELETE FROM plan_item WHERE planId = :planId")
    suspend fun deleteItemsByPlan(planId: Long)
}

/* ------------------------------------- 收藏景点 ------------------------------------ */

/** 景点收藏 DAO */
@Dao
interface SpotCollectDao {

    /** 监听全部收藏景点（最新收藏在前） */
    @Query("SELECT * FROM spot_collect ORDER BY collectedAt DESC")
    fun observeAll(): Flow<List<SpotCollectEntity>>

    /** 新增收藏（同一 spotId 再收藏时覆盖并刷新收藏时间，等价置顶） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SpotCollectEntity)

    /** 按景点 id 取消收藏 */
    @Query("DELETE FROM spot_collect WHERE spotId = :spotId")
    suspend fun deleteBySpotId(spotId: String)
}

/* ------------------------------------- 收藏帖子 ------------------------------------ */

/** 帖子收藏 DAO */
@Dao
interface PostCollectDao {

    /** 监听全部收藏帖子（最新收藏在前） */
    @Query("SELECT * FROM post_collect ORDER BY collectedAt DESC")
    fun observeAll(): Flow<List<PostCollectEntity>>

    /** 新增收藏（同一 postId 再收藏时覆盖并刷新收藏时间） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PostCollectEntity)

    /** 按帖子 id 取消收藏 */
    @Query("DELETE FROM post_collect WHERE postId = :postId")
    suspend fun deleteByPostId(postId: Long)
}

/* ------------------------------------- 浏览记录 ------------------------------------ */

/** 景点浏览记录 DAO */
@Dao
interface BrowseHistoryDao {

    /** 监听全部浏览记录（最近浏览在前） */
    @Query("SELECT * FROM browse_history ORDER BY viewedAt DESC")
    fun observeAll(): Flow<List<BrowseHistoryEntity>>

    /** 写入一条浏览记录（同一 spotId 覆盖 viewedAt，自动去重置顶） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BrowseHistoryEntity)

    /** 清空全部浏览记录 */
    @Query("DELETE FROM browse_history")
    suspend fun clear()
}

/* -------------------------------------- 日记 -------------------------------------- */

/** 旅行日记 DAO */
@Dao
interface DiaryDao {

    /** 监听全部日记（最新保存在前） */
    @Query("SELECT * FROM diary ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DiaryEntity>>

    /**
     * 保存日记（新增 / 编辑通用）：
     * id=0 自增插入并返回新 id；id 已存在则 REPLACE 覆盖，返回原 id。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(diary: DiaryEntity): Long

    /** 删除指定 id 的日记 */
    @Query("DELETE FROM diary WHERE id = :diaryId")
    suspend fun deleteById(diaryId: Long)
}

/* ------------------------------------- 社区帖子 ------------------------------------ */

/** 社区游记帖子 DAO */
@Dao
interface PostDao {

    /** 监听全部帖子（最新发布在前） */
    @Query("SELECT * FROM post ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PostEntity>>

    /** 发布帖子（id=0 自增），返回新帖子 id */
    @Insert
    suspend fun insert(post: PostEntity): Long

    /** 本地模拟点赞：更新点赞状态与点赞数 */
    @Query("UPDATE post SET liked = :liked, likeCount = :likeCount WHERE id = :postId")
    suspend fun updateLike(postId: Long, liked: Boolean, likeCount: Int)

    /** 本地模拟收藏：更新帖子上的收藏标记（收藏列表另由 PostCollectDao 维护） */
    @Query("UPDATE post SET collected = :collected WHERE id = :postId")
    suspend fun updateCollected(postId: Long, collected: Boolean)
}
