package com.example.citygo.skeleton.profile

/* =====================================================================================
 * 「我的」模块数据模型
 *
 * 仅保留全局城市选择器与 Mock 登录账号所需模型 / 种子；
 * 宠物、收藏、浏览记录、日记等业务数据已全部迁移到
 * skeleton.data（Repository + Room），旧 Mock 种子已删除。
 * ===================================================================================== */

/**
 * 城市信息（全局 CitySelector 使用）。
 *
 * @property id 城市唯一 id（拼音，便于检索）
 * @property name 城市中文名
 * @property province 所属省份 / 地区
 * @property hot 是否热门城市
 */
data class CityInfo(
    val id: String,
    val name: String,
    val province: String,
    val hot: Boolean = false,
)

/**
 * 登录用户信息（Mock 账号）；游客模式不使用此对象，UI 统一显示「游客用户」。
 */
data class AppUser(
    val phone: String,
    val nickname: String,
    val avatarEmoji: String,
)

/* -------------------------------------- 种子数据 -------------------------------------- */

/** Mock 登录账号（「切换账号」中回到登录态时使用） */
val MOCK_LOGIN_USER = AppUser(
    phone = "138****8888",
    nickname = "旅行的小方",
    avatarEmoji = "🐬",
)

/** 全部可选城市（本地城市目录） */
val mockCities: List<CityInfo> = listOf(
    CityInfo("chengdu", "成都", "四川", hot = true),
    CityInfo("chongqing", "重庆", "重庆", hot = true),
    CityInfo("shanghai", "上海", "上海", hot = true),
    CityInfo("beijing", "北京", "北京", hot = true),
    CityInfo("hangzhou", "杭州", "浙江", hot = true),
    CityInfo("xian", "西安", "陕西", hot = true),
    CityInfo("xiamen", "厦门", "福建"),
    CityInfo("sanya", "三亚", "海南", hot = true),
    CityInfo("dali", "大理", "云南"),
    CityInfo("lijiang", "丽江", "云南"),
    CityInfo("guangzhou", "广州", "广东"),
    CityInfo("shenzhen", "深圳", "广东"),
)

/** 默认当前城市（成都） */
val mockDefaultCity: CityInfo = mockCities.first { it.id == "chengdu" }

/** 最近访问城市初始列表（最多 6 个，切换城市后由 ViewModel 更新） */
val mockRecentCities: List<CityInfo> = listOf(
    mockCities.first { it.id == "chengdu" },
    mockCities.first { it.id == "chongqing" },
    mockCities.first { it.id == "dali" },
)
