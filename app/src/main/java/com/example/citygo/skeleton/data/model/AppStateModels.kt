package com.example.citygo.skeleton.data.model

/* =====================================================================================
 * 全局 App 共享状态领域模型（纯 Kotlin 数据层，不依赖 Compose / Room / 高德 SDK）
 *
 * 这四类模型由全局 AppViewModel 统一持有，跨底部 5 个 Tab 共享：
 * - [Pet]：我的-宠物档案（宠物弹窗新增 / 编辑 / 删除）；
 * - [Spot]：景点收藏、景点浏览记录、「加入行程」的统一轻量景点模型；
 * - [Post]：社区游记帖子列表 + 我的收藏帖子共用；
 * - [Diary]：旅行日记。
 *
 * 与推荐模块的 [ScenicSpot]、社区模块的 [TravelArticle] 区别：
 * 后两者是「详情页接口模型」字段多；这里只保留跨页面收藏 / 列表所需的轻量字段，
 * 后续页面迁移到全局 AppViewModel 时，由各 Repository 做一次模型映射即可。
 * 本步骤仅实现内存状态，不涉及 Room 数据库。
 * ===================================================================================== */

/**
 * 宠物档案。
 *
 * @property id 自增 id，新增时传 0，由 AppViewModel 分配
 * @property name 宠物名字
 * @property type 宠物类型中文名（狗狗 / 猫咪 / 其他）
 * @property weightKg 体重（千克）
 * @property note 备注（性格 / 注意事项等，可空）
 */
data class Pet(
    val id: Long = 0L,
    val name: String,
    val type: String,
    val weightKg: Double,
    val note: String = "",
)

/**
 * 轻量景点实体（收藏景点 / 浏览记录 / 加入行程通用）。
 *
 * @property id 景点唯一 id（与 ScenicSpot.spotId 保持一致，作为去重依据）
 * @property name 景点名称
 * @property city 所在城市名
 * @property coverImg 封面图地址
 * @property score 综合评分（0.0-5.0，0 表示暂无评分）
 * @property shortDesc 一句话简介（同时作为行程点位的游玩提示）
 * @property address 详细地址
 * @property lat 纬度（GCJ-02 高德坐标，同步到地图 Marker）
 * @property lng 经度（GCJ-02）
 * @property petFriendly 是否宠物友好
 */
data class Spot(
    val id: String,
    val name: String,
    val city: String = "",
    val coverImg: String = "",
    val score: Double = 0.0,
    val shortDesc: String = "",
    val address: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val petFriendly: Boolean = false,
)

/**
 * 社区游记帖子（社区信息流 + 收藏帖子共用）。
 *
 * @property id 帖子唯一 id（自增，新增时由 AppViewModel 分配）
 * @property title 帖子标题
 * @property content 帖子正文
 * @property authorName 作者昵称
 * @property coverImg 封面图地址
 * @property cityName 关联城市名
 * @property likeCount 点赞数
 * @property liked 当前用户是否已点赞
 * @property collected 当前用户是否已收藏
 * @property createdAt 创建时间戳（毫秒）
 * @property timeText 创建时间的中文展示文案，如「刚刚」「2 小时前」
 */
data class Post(
    val id: Long = 0L,
    val title: String,
    val content: String = "",
    val authorName: String = "旅友",
    val coverImg: String = "",
    val cityName: String = "",
    val likeCount: Int = 0,
    val liked: Boolean = false,
    val collected: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val timeText: String = "",
)

/**
 * 旅行日记。
 *
 * @property id 日记唯一 id（自增，新增传 0 由 AppViewModel 分配；更新时必传）
 * @property title 日记标题
 * @property content 日记正文
 * @property cityName 记录的城市 / 目的地名
 * @property coverImg 封面图地址
 * @property imgUrls 配图地址列表
 * @property dateText 日期中文展示文案，如「2026-09-26」
 * @property createdAt 创建 / 更新时间戳（毫秒）
 */
data class Diary(
    val id: Long = 0L,
    val title: String,
    val content: String = "",
    val cityName: String = "",
    val coverImg: String = "",
    val imgUrls: List<String> = emptyList(),
    val dateText: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
