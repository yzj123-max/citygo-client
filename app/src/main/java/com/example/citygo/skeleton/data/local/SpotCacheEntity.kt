package com.example.citygo.skeleton.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/* =====================================================================================
 * 景点推荐本地缓存表（Room）
 *
 * 缓存粒度：
 * - 城市推荐：cacheKey = "city:{adCode}"，payload 为该城市 8-12 个景点 JSON；
 * - 全国搜索：cacheKey = "search:{关键词}"，payload 为搜索结果 JSON。
 *
 * 每条记录带 fetchedAt 拉取时间戳，由仓库层按 TTL 判断是否过期
 * （城市缓存 24h、搜索缓存 6h，见 PoiConfig），过期后重新请求高德 POI。
 * ===================================================================================== */

/**
 * 景点缓存记录。
 *
 * @property cacheKey 缓存键（city:adCode / search:keyword）
 * @property payloadJson 景点列表 JSON（Gson 序列化的 List<ScenicSpot>）
 * @property fetchedAt 拉取时间（System.currentTimeMillis()）
 */
@Entity(tableName = "spot_cache")
data class SpotCacheEntity(
    @PrimaryKey val cacheKey: String,
    val payloadJson: String,
    val fetchedAt: Long,
)

/** 景点缓存表 DAO */
@Dao
interface SpotCacheDao {

    /** 按 key 读取一条缓存（不存在返回 null），过期判断交给仓库层 */
    @Query("SELECT * FROM spot_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getByKey(cacheKey: String): SpotCacheEntity?

    /** 读取全部缓存（深链进入详情页时按 spotId 兜底扫描） */
    @Query("SELECT * FROM spot_cache")
    suspend fun getAll(): List<SpotCacheEntity>

    /** 写入 / 覆盖一条缓存 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SpotCacheEntity)

    /** 删除过期缓存（fetchedAt 早于 [threshold] 的全部记录） */
    @Query("DELETE FROM spot_cache WHERE fetchedAt < :threshold")
    suspend fun deleteOlderThan(threshold: Long)
}
