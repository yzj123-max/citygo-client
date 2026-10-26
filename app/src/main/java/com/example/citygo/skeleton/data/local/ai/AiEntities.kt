package com.example.citygo.skeleton.data.local.ai

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.example.citygo.skeleton.data.model.AiSpot
import com.example.citygo.skeleton.data.model.AiSpotType
import com.example.citygo.skeleton.data.model.AiTravelPlan
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/* =====================================================================================
 * AI 旅行攻略 Room 缓存实体层（全部为独立新增文件，不修改任何既有 Entity）
 *
 * 表结构（主子两张表，与现有行程主/子表风格一致）：
 * - ai_travel_plan：一份 AI 生成的城市攻略（城市 + 生成时间），一个城市可缓存多份；
 * - ai_spot       ：攻略下的单个地点（景点 / 美食店铺），外键关联主表，主表删除级联删除。
 *
 * 缓存目的：同一城市的攻略生成后落本地，下次先查缓存，避免重复请求大模型。
 *
 * ⚠️ 后续接入步骤（本任务按约束不写 DAO / Repository，也不改动 AppDatabase）：
 * 1. 在 AppDatabase 的 @Database(entities = [...]) 中追加
 *    [AiTravelPlanEntity]、[AiSpotEntity]，并把 version +1、配置 Migration（或按项目策略）；
 * 2. 注册 [AiConverters]（若 @TypeConverters 已在 AppDatabase 上声明则合并）；
 * 3. 新增 AiTravelPlanDao：按 city 索引查询最新一份攻略、插入（主子表事务）、删除；
 * 4. 在 Repository 实现中先查缓存、未命中再请求大模型并写缓存。
 * 以上步骤不会影响任何高德导航 / 天气预报代码与现有页面。
 * ===================================================================================== */

/**
 * AI 攻略表的 Room 类型转换器：图片 url 列表 ↔ JSON 字符串。
 * 独立于既有 AppConverters，便于后续接入时单独注册，不改既有文件。
 */
class AiConverters {

    private val gson = Gson()
    private val stringListType = object : TypeToken<List<String>>() {}.type

    @TypeConverter
    fun imageUrlsToJson(value: List<String>?): String =
        if (value.isNullOrEmpty()) "" else gson.toJson(value, stringListType)

    @TypeConverter
    fun jsonToImageUrls(value: String?): List<String> =
        if (value.isNullOrBlank()) emptyList() else gson.fromJson(value, stringListType)
}

/* ------------------------------------ AI 攻略主表 ------------------------------------ */

/**
 * AI 生成攻略主表。
 *
 * @property id 自增主键
 * @property city 城市名称；建索引便于后续 DAO 按城市快速命中缓存（避免重复请求大模型）
 * @property generateTime 生成时间（毫秒时间戳）
 */
@Entity(
    tableName = "ai_travel_plan",
    indices = [Index(value = ["city"])],
)
data class AiTravelPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val city: String,
    val generateTime: Long,
)

/* ------------------------------------ AI 地点子表 ------------------------------------ */

/**
 * AI 攻略地点子表：一份攻略下的景点 / 美食店铺。
 *
 * 联合主键 (planId, sortOrder)：planId 关联主表，sortOrder 保存大模型推荐顺序；
 * planId 外键级联，删除主表攻略时子表地点一并清除；planId 建索引满足外键索引要求。
 *
 * @property planId 所属攻略主表 id
 * @property sortOrder 在攻略中的顺序下标（从 0 开始）
 * @property name 地点名称
 * @property type 类型中文名（[AiSpotType.label]：大众景点 / 小众景点 / 本地美食 / 探店网红店）
 * @property desc 小红书风格简介
 * @property latitude 纬度（GCJ-02）
 * @property longitude 经度（GCJ-02）
 * @property isPetFriendly 是否宠物友好
 * @property imageUrlsJson 图片 url 列表的 JSON 字符串（由 [AiConverters] 转换）
 * @property tip 游玩小贴士
 */
@Entity(
    tableName = "ai_spot",
    primaryKeys = ["planId", "sortOrder"],
    foreignKeys = [
        ForeignKey(
            entity = AiTravelPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["planId"])],
)
data class AiSpotEntity(
    val planId: Long,
    val sortOrder: Int,
    val name: String,
    val type: String,
    val desc: String,
    val latitude: Double,
    val longitude: Double,
    val isPetFriendly: Boolean,
    val imageUrlsJson: String,
    val tip: String,
)

/* ------------------------------ 实体 ↔ 领域模型互转（文件内聚） ------------------------------ */

/** 子表实体转为领域 [AiSpot]；类型中文名容错还原为枚举。 */
fun AiSpotEntity.toAiSpot(): AiSpot = AiSpot(
    name = name,
    type = AiSpotType.fromLabel(type),
    desc = desc,
    latitude = latitude,
    longitude = longitude,
    isPetFriendly = isPetFriendly,
    imageUrls = AiConverters().jsonToImageUrls(imageUrlsJson),
    tip = tip,
)

/**
 * 攻略主表实体 + 已查出的子表地点 → 领域 [AiTravelPlan]。
 * 调用方需保证 [spots] 属于该主表；此处再按 sortOrder 排序，保证推荐顺序与生成时一致。
 */
fun AiTravelPlanEntity.toAiTravelPlan(spots: List<AiSpotEntity>): AiTravelPlan = AiTravelPlan(
    city = city,
    generateTime = generateTime,
    spotList = spots.sortedBy { it.sortOrder }.map { it.toAiSpot() },
)

/** 领域攻略 → 主表实体（id 传 0 由 Room 自增分配）。 */
fun AiTravelPlan.toPlanEntity(): AiTravelPlanEntity = AiTravelPlanEntity(
    city = city,
    generateTime = generateTime,
)

/**
 * 领域攻略 → 子表实体列表。
 * @param planId 主表插入后 Room 回填的真实 id（后续 DAO 在同一事务内先插主表拿到 id 再插子表）
 */
fun AiTravelPlan.toSpotEntities(planId: Long): List<AiSpotEntity> {
    val converter = AiConverters()
    return spotList.mapIndexed { index, spot ->
        AiSpotEntity(
            planId = planId,
            sortOrder = index,
            name = spot.name,
            type = spot.type.label,
            desc = spot.desc,
            latitude = spot.latitude,
            longitude = spot.longitude,
            isPetFriendly = spot.isPetFriendly,
            imageUrlsJson = converter.imageUrlsToJson(spot.imageUrls),
            tip = spot.tip,
        )
    }
}
