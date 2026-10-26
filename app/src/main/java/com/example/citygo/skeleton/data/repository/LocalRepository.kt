package com.example.citygo.skeleton.data.repository

import androidx.room.withTransaction
import com.example.citygo.skeleton.data.local.app.AppDatabase
import com.example.citygo.skeleton.data.local.app.BrowseHistoryDao
import com.example.citygo.skeleton.data.local.app.DiaryDao
import com.example.citygo.skeleton.data.local.app.PetDao
import com.example.citygo.skeleton.data.local.app.PlanItemDao
import com.example.citygo.skeleton.data.local.app.PostCollectDao
import com.example.citygo.skeleton.data.local.app.PostDao
import com.example.citygo.skeleton.data.local.app.SpotCollectDao
import com.example.citygo.skeleton.data.local.app.TravelPlanDao
import com.example.citygo.skeleton.data.local.app.toDiary
import com.example.citygo.skeleton.data.local.app.toEntity
import com.example.citygo.skeleton.data.local.app.toCollectEntity
import com.example.citygo.skeleton.data.local.app.toBrowseEntity
import com.example.citygo.skeleton.data.local.app.toItemEntities
import com.example.citygo.skeleton.data.local.app.toPet
import com.example.citygo.skeleton.data.local.app.toPlanEntity
import com.example.citygo.skeleton.data.local.app.toPost
import com.example.citygo.skeleton.data.local.app.toSpot
import com.example.citygo.skeleton.data.local.app.withItems
import com.example.citygo.skeleton.data.model.Diary
import com.example.citygo.skeleton.data.model.Pet
import com.example.citygo.skeleton.data.model.Post
import com.example.citygo.skeleton.data.model.Spot
import com.example.citygo.skeleton.data.model.TravelPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/* =====================================================================================
 * Repository 的本地实现：全部业务数据走 Room（citygo_app.db）
 *
 * - 入参 / 返回值只使用领域模型，Room 实体在本类内部完成转换；
 * - 写操作中「行程主表 + 子项」的覆盖写入放在同一个 Room 事务内，避免半截数据；
 * - Room 的 suspend / Flow 查询自带后台调度，本类不再额外切线程。
 *
 * ViewModel 只持有 [Repository] 接口类型，不感知本类与 Room 的存在。
 * ===================================================================================== */

class LocalRepository(
    private val db: AppDatabase,
    private val petDao: PetDao,
    private val travelPlanDao: TravelPlanDao,
    private val planItemDao: PlanItemDao,
    private val spotCollectDao: SpotCollectDao,
    private val postCollectDao: PostCollectDao,
    private val browseHistoryDao: BrowseHistoryDao,
    private val diaryDao: DiaryDao,
    private val postDao: PostDao,
) : Repository {

    /* -------------------------------------- 宠物 -------------------------------------- */

    override fun observePets(): Flow<List<Pet>> =
        petDao.observePets().map { rows -> rows.map { it.toPet() } }

    override suspend fun addPet(pet: Pet): Long =
        petDao.insertPet(pet.toEntity())

    override suspend fun updatePet(pet: Pet) {
        petDao.updatePet(pet.toEntity())
    }

    override suspend fun deletePet(petId: Long) {
        petDao.deletePetById(petId)
    }

    /* -------------------------------------- 行程 -------------------------------------- */

    override fun observeCurrentTravelPlan(): Flow<TravelPlan?> =
        travelPlanDao.observePlanWithItems(AppDatabase.CURRENT_PLAN_ID).map { row ->
            // 在同一 Room 事务中读取主子表，恢复云端快照时不发射主表/地点不匹配的中间态。
            row?.plan?.withItems(row.items)
        }

    override suspend fun saveTravelPlan(plan: TravelPlan) {
        val now = System.currentTimeMillis()
        // 主表 upsert + 子项全量覆盖必须在同一事务内，失败一起回滚
        db.withTransaction {
            travelPlanDao.upsertPlan(
                plan.toPlanEntity(AppDatabase.CURRENT_PLAN_ID, now),
            )
            planItemDao.deleteItemsByPlan(AppDatabase.CURRENT_PLAN_ID)
            val items = plan.toItemEntities(AppDatabase.CURRENT_PLAN_ID)
            if (items.isNotEmpty()) planItemDao.insertItems(items)
        }
    }

    override suspend fun clearTravelPlan() {
        // 删主表行即可，子项由外键 ON DELETE CASCADE 自动清除
        travelPlanDao.deletePlanById(AppDatabase.CURRENT_PLAN_ID)
    }

    /* ------------------------------------- 收藏景点 ------------------------------------ */

    override fun observeCollectedSpots(): Flow<List<Spot>> =
        spotCollectDao.observeAll().map { rows -> rows.map { it.toSpot() } }

    override suspend fun addCollectedSpot(spot: Spot) {
        spotCollectDao.insert(spot.toCollectEntity(System.currentTimeMillis()))
    }

    override suspend fun removeCollectedSpot(spotId: String) {
        spotCollectDao.deleteBySpotId(spotId)
    }

    /* ------------------------------------- 收藏帖子 ------------------------------------ */

    override fun observeCollectedPosts(): Flow<List<Post>> =
        postCollectDao.observeAll().map { rows -> rows.map { it.toPost() } }

    override suspend fun addCollectedPost(post: Post) {
        postCollectDao.insert(post.toCollectEntity(System.currentTimeMillis()))
    }

    override suspend fun removeCollectedPost(postId: Long) {
        postCollectDao.deleteByPostId(postId)
    }

    /* ------------------------------------- 浏览记录 ------------------------------------ */

    override fun observeBrowseHistory(): Flow<List<Spot>> =
        browseHistoryDao.observeAll().map { rows -> rows.map { it.toSpot() } }

    override suspend fun addBrowseHistory(spot: Spot) {
        browseHistoryDao.insert(spot.toBrowseEntity(System.currentTimeMillis()))
    }

    override suspend fun clearBrowseHistory() {
        browseHistoryDao.clear()
    }

    /* -------------------------------------- 日记 -------------------------------------- */

    override fun observeDiaries(): Flow<List<Diary>> =
        diaryDao.observeAll().map { rows -> rows.map { it.toDiary() } }

    override suspend fun saveDiary(diary: Diary): Long =
        diaryDao.save(diary.toEntity())

    override suspend fun deleteDiary(diaryId: Long) {
        diaryDao.deleteById(diaryId)
    }

    /* ------------------------------------- 社区帖子 ------------------------------------ */

    override fun observePosts(): Flow<List<Post>> =
        postDao.observeAll().map { rows -> rows.map { it.toPost() } }

    override suspend fun addPost(post: Post): Long =
        postDao.insert(post.toEntity())

    override suspend fun updatePostLike(postId: Long, liked: Boolean, likeCount: Int) {
        postDao.updateLike(postId, liked, likeCount)
    }

    override suspend fun updatePostCollected(postId: Long, collected: Boolean) {
        postDao.updateCollected(postId, collected)
    }

    companion object {
        /**
         * 基于 App 业务库构建本地 Repository（依赖注入的最简手工入口，
         * 供后续 ServiceLocator / Application 初始化使用；本步不接入任何页面）。
         */
        fun create(db: AppDatabase): LocalRepository = LocalRepository(
            db = db,
            petDao = db.petDao(),
            travelPlanDao = db.travelPlanDao(),
            planItemDao = db.planItemDao(),
            spotCollectDao = db.spotCollectDao(),
            postCollectDao = db.postCollectDao(),
            browseHistoryDao = db.browseHistoryDao(),
            diaryDao = db.diaryDao(),
            postDao = db.postDao(),
        )
    }
}
