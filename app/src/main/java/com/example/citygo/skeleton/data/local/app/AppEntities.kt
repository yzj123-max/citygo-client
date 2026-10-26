package com.example.citygo.skeleton.data.local.app

import androidx.room.TypeConverter
import com.example.citygo.skeleton.data.model.Diary
import com.example.citygo.skeleton.data.model.DayPlan
import com.example.citygo.skeleton.data.model.Pet
import com.example.citygo.skeleton.data.model.Place
import com.example.citygo.skeleton.data.model.PlaceType
import com.example.citygo.skeleton.data.model.Post
import com.example.citygo.skeleton.data.model.Spot
import com.example.citygo.skeleton.data.model.TravelPlan
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/* =====================================================================================
 * App 业务库 Room 实体层（citygo_app.db）
 *
 * 与 POI 缓存库 citygo.db（CityGoDatabase）相互独立：
 * 本库存放跨 Tab 的用户业务数据（宠物 / 行程 / 收藏 / 浏览记录 / 日记 / 社区帖子），
 * 实体只描述表结构，与页面使用的领域模型（Pet / Spot / Post / Diary / TravelPlan）
 * 通过文件底部的扩展函数互转，保证 Room 不泄漏到 ViewModel / UI 层。
 * ===================================================================================== */

/**
 * Room 类型转换器：复杂类型与数据库列类型互转。
 * 当前仅需处理日记配图 List<String> ↔ JSON 字符串。
 */
class AppConverters {

    private val gson = Gson()
    private val stringListType = object : TypeToken<List<String>>() {}.type

    @TypeConverter
    fun listToJson(value: List<String>?): String =
        if (value.isNullOrEmpty()) "" else gson.toJson(value, stringListType)

    @TypeConverter
    fun jsonToList(value: String?): List<String> =
        if (value.isNullOrBlank()) emptyList() else gson.fromJson(value, stringListType)
}

/* -------------------------------------- 宠物 -------------------------------------- */

/** 宠物档案表 */
@androidx.room.Entity(tableName = "pet")
data class PetEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: String,
    val weightKg: Double,
    val note: String,
)

/* ------------------------------------- 行程主表 ------------------------------------ */

/**
 * 行程主表：App 同时只有一份「当前行程」，固定主键 [AppDatabase.CURRENT_PLAN_ID]=1，
 * 子项见 [PlanItemEntity]（一对多，外键级联删除）。
 */
@androidx.room.Entity(tableName = "travel_plan")
data class TravelPlanEntity(
    @androidx.room.PrimaryKey val id: Long = 1L,
    val destination: String,
    val totalDays: Int,
    val updatedAt: Long,
)

/* ------------------------------------- 行程子项 ------------------------------------ */

/**
 * 行程子项表：行程中的单个点位（名称 / 经纬度 / 宠物友好 / 游玩提示）。
 * 联合主键 (planId, id)；planId 外键关联行程主表，主表删除时级联清空子项。
 */
@androidx.room.Entity(
    tableName = "plan_item",
    primaryKeys = ["planId", "id"],
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = TravelPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = androidx.room.ForeignKey.CASCADE,
        ),
    ],
    indices = [androidx.room.Index("planId")],
)
data class PlanItemEntity(
    val id: Long,
    val planId: Long,
    val name: String,
    /** 点位类型，存 PlaceType 枚举名（HOTEL/SHOP/RESTAURANT/SPOT） */
    val placeType: String,
    val day: Int,
    val lat: Double,
    val lng: Double,
    /** 游玩提示 */
    val intro: String,
    val petFriendly: Boolean,
    /** 同一天内的排序序号（小在前） */
    val sortOrder: Int,
    /** 真实实拍封面图 URL（高德 POI photos）；v4 新增列，旧行为 null */
    val coverUrl: String? = null,
    /** 景点门票原文（数据库预置扩展属性，可能是「暂无相关信息」）；v6 新增列，旧行为 null */
    val ticket: String? = null,
    /** 餐厅人均价格原文；v6 新增列，旧行为 null */
    val avgPrice: String? = null,
    /** 餐厅特色菜品原文；v6 新增列，旧行为 null */
    val signatureDish: String? = null,
    /** 点位来源：「LOCAL_DB」数据库预置 / 「AI」AI 拓展；v6 新增列，旧行默认按 AI 处理 */
    val source: String? = null,
    /** 拍照场景 / 出片点原文；v7 新增列，旧行为 null */
    val photoScene: String? = null,
    /** 出行要点 / 注意事项原文；v7 新增列，旧行为 null */
    val travelTip: String? = null,
    /** 时间段原文（上午9:00-12:00 / 午餐12:30）；v8 新增列，旧行为 null */
    val timeSlot: String? = null,
    /** 是否兜底坐标（0/1）；v7 新增列，旧行默认 0 */
    val coordMocked: Boolean = false,
)

/* ------------------------------------- 收藏景点 ------------------------------------ */

/** 景点收藏表：spotId 即领域模型 Spot.id */
@androidx.room.Entity(tableName = "spot_collect")
data class SpotCollectEntity(
    @androidx.room.PrimaryKey val spotId: String,
    val name: String,
    val city: String,
    val coverImg: String,
    val score: Double,
    val shortDesc: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val petFriendly: Boolean,
    /** 收藏时间戳（用于按最新收藏排序） */
    val collectedAt: Long,
)

/* ------------------------------------- 收藏帖子 ------------------------------------ */

/** 帖子收藏表：postId 即领域模型 Post.id（收藏快照与帖子表分开存，帖子被删不影响收藏） */
@androidx.room.Entity(tableName = "post_collect")
data class PostCollectEntity(
    @androidx.room.PrimaryKey val postId: Long,
    val title: String,
    val content: String,
    val authorName: String,
    val coverImg: String,
    val cityName: String,
    val likeCount: Int,
    val liked: Boolean,
    val collected: Boolean,
    val createdAt: Long,
    val timeText: String,
    /** 收藏时间戳（用于按最新收藏排序） */
    val collectedAt: Long,
)

/* ------------------------------------- 浏览记录 ------------------------------------ */

/** 景点浏览记录表：再次浏览同一景点 REPLACE 覆盖 viewedAt，实现自动置顶去重 */
@androidx.room.Entity(tableName = "browse_history")
data class BrowseHistoryEntity(
    @androidx.room.PrimaryKey val spotId: String,
    val name: String,
    val city: String,
    val coverImg: String,
    val score: Double,
    val shortDesc: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val petFriendly: Boolean,
    /** 最近浏览时间戳（用于按最近浏览排序） */
    val viewedAt: Long,
)

/* -------------------------------------- 日记 -------------------------------------- */

/** 旅行日记表；imgUrls 以 JSON 字符串存储（见 [AppConverters]） */
@androidx.room.Entity(tableName = "diary")
data class DiaryEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val content: String,
    val cityName: String,
    val coverImg: String,
    val imgUrls: List<String>,
    val dateText: String,
    val createdAt: Long,
)

/* ------------------------------------- 社区帖子 ------------------------------------ */

/** 社区游记帖子表（本地模拟信息流，点赞 / 收藏状态直接更新本表） */
@androidx.room.Entity(tableName = "post")
data class PostEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val content: String,
    val authorName: String,
    val coverImg: String,
    val cityName: String,
    val likeCount: Int,
    val liked: Boolean,
    val collected: Boolean,
    val createdAt: Long,
    val timeText: String,
)

/* =====================================================================================
 * 实体 ↔ 领域模型映射（internal，仅数据层内部使用）
 * ===================================================================================== */

/** 宠物实体 → 领域模型 */
internal fun PetEntity.toPet(): Pet = Pet(
    id = id,
    name = name,
    type = type,
    weightKg = weightKg,
    note = note,
)

/** 宠物领域模型 → 实体（id=0 时由 Room 自动生成） */
internal fun Pet.toEntity(): PetEntity = PetEntity(
    id = id,
    name = name,
    type = type,
    weightKg = weightKg,
    note = note,
)

/** 收藏景点实体 → 领域模型 */
internal fun SpotCollectEntity.toSpot(): Spot = Spot(
    id = spotId,
    name = name,
    city = city,
    coverImg = coverImg,
    score = score,
    shortDesc = shortDesc,
    address = address,
    lat = lat,
    lng = lng,
    petFriendly = petFriendly,
)

/** 景点领域模型 → 收藏实体（收藏时间由调用方传入） */
internal fun Spot.toCollectEntity(collectedAt: Long): SpotCollectEntity = SpotCollectEntity(
    spotId = id,
    name = name,
    city = city,
    coverImg = coverImg,
    score = score,
    shortDesc = shortDesc,
    address = address,
    lat = lat,
    lng = lng,
    petFriendly = petFriendly,
    collectedAt = collectedAt,
)

/** 浏览记录实体 → 领域模型 */
internal fun BrowseHistoryEntity.toSpot(): Spot = Spot(
    id = spotId,
    name = name,
    city = city,
    coverImg = coverImg,
    score = score,
    shortDesc = shortDesc,
    address = address,
    lat = lat,
    lng = lng,
    petFriendly = petFriendly,
)

/** 景点领域模型 → 浏览记录实体（浏览时间由调用方传入） */
internal fun Spot.toBrowseEntity(viewedAt: Long): BrowseHistoryEntity = BrowseHistoryEntity(
    spotId = id,
    name = name,
    city = city,
    coverImg = coverImg,
    score = score,
    shortDesc = shortDesc,
    address = address,
    lat = lat,
    lng = lng,
    petFriendly = petFriendly,
    viewedAt = viewedAt,
)

/** 收藏帖子实体 → 领域模型 */
internal fun PostCollectEntity.toPost(): Post = Post(
    id = postId,
    title = title,
    content = content,
    authorName = authorName,
    coverImg = coverImg,
    cityName = cityName,
    likeCount = likeCount,
    liked = liked,
    collected = collected,
    createdAt = createdAt,
    timeText = timeText,
)

/** 帖子领域模型 → 收藏实体（收藏时间由调用方传入） */
internal fun Post.toCollectEntity(collectedAt: Long): PostCollectEntity = PostCollectEntity(
    postId = id,
    title = title,
    content = content,
    authorName = authorName,
    coverImg = coverImg,
    cityName = cityName,
    likeCount = likeCount,
    liked = liked,
    collected = true,
    createdAt = createdAt,
    timeText = timeText,
    collectedAt = collectedAt,
)

/** 社区帖子实体 → 领域模型 */
internal fun PostEntity.toPost(): Post = Post(
    id = id,
    title = title,
    content = content,
    authorName = authorName,
    coverImg = coverImg,
    cityName = cityName,
    likeCount = likeCount,
    liked = liked,
    collected = collected,
    createdAt = createdAt,
    timeText = timeText,
)

/** 帖子领域模型 → 表实体（id=0 时由 Room 自动生成） */
internal fun Post.toEntity(): PostEntity = PostEntity(
    id = id,
    title = title,
    content = content,
    authorName = authorName,
    coverImg = coverImg,
    cityName = cityName,
    likeCount = likeCount,
    liked = liked,
    collected = collected,
    createdAt = createdAt,
    timeText = timeText,
)

/** 日记实体 → 领域模型 */
internal fun DiaryEntity.toDiary(): Diary = Diary(
    id = id,
    title = title,
    content = content,
    cityName = cityName,
    coverImg = coverImg,
    imgUrls = imgUrls,
    dateText = dateText,
    createdAt = createdAt,
)

/** 日记领域模型 → 表实体（id=0 时由 Room 自动生成） */
internal fun Diary.toEntity(): DiaryEntity = DiaryEntity(
    id = id,
    title = title,
    content = content,
    cityName = cityName,
    coverImg = coverImg,
    imgUrls = imgUrls,
    dateText = dateText,
    createdAt = createdAt,
)

/** 安全解析点位类型枚举，库里出现未知值时回退到景点类型 */
private fun String.toPlaceType(): PlaceType =
    runCatching { PlaceType.valueOf(this) }.getOrDefault(PlaceType.SPOT)

/** plan_item.source 列中表示「数据库预置点位」的稳定编码（其余值 / null 视为 AI 拓展） */
private const val SOURCE_LOCAL_DB = "LOCAL_DB"

/** 行程主表 + 子项实体 → 领域行程（按 day、sortOrder 还原多日结构） */
internal fun TravelPlanEntity.withItems(items: List<PlanItemEntity>): TravelPlan {
    val places = items
        .sortedWith(compareBy({ it.day }, { it.sortOrder }))
        .map { item ->
            Place(
                id = item.id,
                name = item.name,
                placeType = item.placeType.toPlaceType(),
                dayIndex = item.day,
                lat = item.lat,
                lng = item.lng,
                intro = item.intro,
                petFriendly = item.petFriendly,
                coverUrl = item.coverUrl,
                ticket = item.ticket,
                avgPrice = item.avgPrice,
                signatureDish = item.signatureDish,
                // 旧行 source 为 null，按 AI 拓展处理；仅显式 LOCAL_DB 才标记为库内推荐
                fromLocalDb = item.source == SOURCE_LOCAL_DB,
                photoScene = item.photoScene,
                travelTip = item.travelTip,
                timeSlot = item.timeSlot,
                mockedCoord = item.coordMocked,
            )
        }
    val daily = places.groupBy { it.dayIndex }
        .toSortedMap()
        .map { (day, dayPlaces) -> DayPlan(day = day, places = dayPlaces) }
    return TravelPlan(
        destination = destination,
        totalDays = totalDays.coerceAtLeast(daily.size),
        dailyPlans = daily,
        allPlaceList = places,
    )
}

/** 领域行程 → 主表实体（固定使用当前行程主键） */
internal fun TravelPlan.toPlanEntity(planId: Long, updatedAt: Long): TravelPlanEntity =
    TravelPlanEntity(
        id = planId,
        destination = destination,
        totalDays = totalDays.coerceAtLeast(dailyPlans.size),
        updatedAt = updatedAt,
    )

/** 领域行程 → 全部子项实体（sortOrder 按各天列表下标生成） */
internal fun TravelPlan.toItemEntities(planId: Long): List<PlanItemEntity> =
    dailyPlans.flatMap { dayPlan ->
        dayPlan.places.mapIndexed { index, place ->
            PlanItemEntity(
                id = place.id,
                planId = planId,
                name = place.name,
                placeType = place.placeType.name,
                day = place.dayIndex,
                lat = place.lat,
                lng = place.lng,
                intro = place.intro,
                petFriendly = place.petFriendly,
                sortOrder = index,
                coverUrl = place.coverUrl,
                ticket = place.ticket,
                avgPrice = place.avgPrice,
                signatureDish = place.signatureDish,
                source = if (place.fromLocalDb) SOURCE_LOCAL_DB else "AI",
                photoScene = place.photoScene,
                travelTip = place.travelTip,
                timeSlot = place.timeSlot,
                coordMocked = place.mockedCoord,
            )
        }
    }
