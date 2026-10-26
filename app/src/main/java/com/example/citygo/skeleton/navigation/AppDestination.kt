package com.example.citygo.skeleton.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Recommend
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Recommend
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 底部 5 个 Tab 的路由统一管理。
 *
 * - [route]：NavHost 中使用的唯一路由字符串，禁止在别处硬编码；
 * - [label]：底部导航显示的中文标题；
 * - [selectedIcon] / [unselectedIcon]：选中 / 未选中状态图标。
 *
 * 使用密封类（sealed class）可以在 `when` 分支中穷举所有页面，
 * 新增 Tab 时编译器会提醒补齐所有引用处。
 */
sealed class AppDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    /** 地图页 */
    data object Map : AppDestination(
        route = ROUTE_MAP,
        label = "地图",
        selectedIcon = Icons.Filled.Map,
        unselectedIcon = Icons.Outlined.Map,
    )

    /** 出行计划页（页面内部嵌套 TabRow） */
    data object Plan : AppDestination(
        route = ROUTE_PLAN,
        label = "行程",
        selectedIcon = Icons.Filled.EditNote,
        unselectedIcon = Icons.Outlined.EditNote,
    )

    /** 照片分享页 */
    data object Share : AppDestination(
        route = ROUTE_SHARE,
        label = "社区",
        selectedIcon = Icons.Filled.PhotoLibrary,
        unselectedIcon = Icons.Outlined.PhotoLibrary,
    )

    /** 景点推荐页 */
    data object Recommend : AppDestination(
        route = ROUTE_RECOMMEND,
        label = "发现",
        selectedIcon = Icons.Filled.Recommend,
        unselectedIcon = Icons.Outlined.Recommend,
    )

    /** 我的页面 */
    data object Profile : AppDestination(
        route = ROUTE_PROFILE,
        label = "我的",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
    )

    companion object {
        // 路由字符串常量集中管理，NavHost 注册与底部跳转均引用此处
        const val ROUTE_MAP = "map"
        const val ROUTE_PLAN = "plan"
        const val ROUTE_SHARE = "share"
        const val ROUTE_RECOMMEND = "recommend"
        const val ROUTE_PROFILE = "profile"

        // ---------- 游记社区二级页（无底部导航栏） ----------
        /** 游记详情路由（二级页，携带 articleId 字符串路径参数） */
        const val ROUTE_ARTICLE_DETAIL = "article/{articleId}"
        /** 详情页路径参数名 */
        const val ARG_ARTICLE_ID = "articleId"
        /** 游记发布页路由（无参） */
        const val ROUTE_PUBLISH_ARTICLE = "article/publish"

        // ---------- OOTD 穿搭 AI 分析副页面（从「分享」主页进入，无底部导航栏） ----------
        /** OOTD 穿搭 AI 分析：拍照/相册选图 → 压缩上传 → SSE 流式分析（局域网 IP 联调） */
        const val ROUTE_OOTD_ANALYZE = "share/ootd"
        const val ROUTE_OUTFIT_TOOLS = "outfit_tools"

        /** 构造游记详情跳转路径（id 做 URL 编码，防止特殊字符破坏路由） */
        fun articleDetailRoute(articleId: String): String =
            "article/${android.net.Uri.encode(articleId)}"

        // ---------- 景点模块二级页（无底部导航栏） ----------
        /** 景点详情路由（携带 spotId 字符串路径参数） */
        const val ROUTE_SPOT_DETAIL = "spot_detail/{spotId}"
        const val ARG_SPOT_ID = "spotId"

        /** 景点搜索结果页路由（keyword 为 query 参数，可空） */
        const val ROUTE_SPOT_SEARCH = "spot_search?keyword={keyword}"
        const val ARG_SPOT_KEYWORD = "keyword"

        /** 景点路线预览页路由（携带 spotId） */
        const val ROUTE_SPOT_ROUTE = "spot_route?spotId={spotId}"
        const val ARG_SPOT_ROUTE_ID = "spotId"

        /** 构造景点详情跳转路径 */
        fun spotDetailRoute(spotId: String): String =
            "spot_detail/${android.net.Uri.encode(spotId)}"

        /** 构造景点搜索结果页跳转路径（关键词做 URL 编码） */
        fun spotSearchRoute(keyword: String): String =
            "spot_search?keyword=${android.net.Uri.encode(keyword)}"

        /** 构造景点路线预览页跳转路径 */
        fun spotRouteRoute(spotId: String): String =
            "spot_route?spotId=${android.net.Uri.encode(spotId)}"

        // ---------- 地图模块二级页：全屏实时导航（无底部导航栏） ----------
        /**
         * 导航页路由，携带起终点经纬度、终点名称、交通方式、起点类型与城市（query 参数形式）。
         * oType=my 表示起点为设备当前位置（导航页启动外挂 GPS、车头跟随）；
         * oType=spot 表示起点为自定义景点坐标（两景点间导航，禁止喂入真实 GPS）。
         */
        const val ROUTE_NAVIGATION =
            "navigation?oLat={oLat}&oLng={oLng}&dLat={dLat}&dLng={dLng}&name={name}" +
                "&mode={mode}&oType={oType}&city={city}&simulate={simulate}&routeId={routeId}" +
                "&naviType={naviType}"

        const val ARG_NAVI_O_LAT = "oLat"
        const val ARG_NAVI_O_LNG = "oLng"
        const val ARG_NAVI_D_LAT = "dLat"
        const val ARG_NAVI_D_LNG = "dLng"
        const val ARG_NAVI_NAME = "name"
        /** 交通方式：drive / walk / ride / bus，缺省 drive */
        const val ARG_NAVI_MODE = "mode"
        /** 起点类型：my=设备当前位置（缺省），spot=自定义景点坐标 */
        const val ARG_NAVI_O_TYPE = "oType"
        /** 终点所在城市名（公交步骤查询用，可空） */
        const val ARG_NAVI_CITY = "city"
        /** 是否模拟导航：1=模拟（步骤预览页底部按钮进入），缺省 0 真实导航 */
        const val ARG_NAVI_SIMULATE = "simulate"
        /** 选线页选中的引擎路线 id（缺省 -1 不指定） */
        const val ARG_NAVI_ROUTE_ID = "routeId"
        /**
         * 显式导航类型：simulation=模拟导航（条件A 自定义起终点），
         * real_gps=真实 GPS 导航（条件B 从我的位置出发）；缺省 real_gps
         */
        const val ARG_NAVI_NAVI_TYPE = "naviType"
        const val NAVI_TYPE_SIMULATION = "simulation"
        const val NAVI_TYPE_REAL_GPS = "real_gps"

        const val ORIGIN_TYPE_MY = "my"
        const val ORIGIN_TYPE_SPOT = "spot"

        // ---------- 多路线选择页（导航前置页，参数与导航页一致） ----------
        /**
         * 选线路由：进入导航前先在该页用导航引擎独立算路、展示多条路线卡片。
         * 参数语义与 [ROUTE_NAVIGATION] 完全相同。
         */
        const val ROUTE_ROUTE_PLAN =
            "route_plan?oLat={oLat}&oLng={oLng}&dLat={dLat}&dLng={dLng}&name={name}" +
                "&mode={mode}&oType={oType}&city={city}&naviType={naviType}"

        // ---------- 路线步骤预览页（只看步骤，不自动导航） ----------
        /** 路线步骤预览页：routeId 对应 NaviRouteChoiceStore 中的候选路线 */
        const val ROUTE_STEP_PREVIEW = "navi_steps?routeId={routeId}"
        const val ARG_STEP_ROUTE_ID = "routeId"

        /** 构造导航页跳转路径（终点名称与城市做 URL 编码） */
        fun navigationRoute(
            originLat: Double,
            originLng: Double,
            destLat: Double,
            destLng: Double,
            destName: String,
            mode: String = "drive",
            originType: String = ORIGIN_TYPE_MY,
            city: String = "",
            simulate: Boolean = false,
            routeId: Int = -1,
            naviType: String = NAVI_TYPE_REAL_GPS,
        ): String =
            "navigation?oLat=$originLat&oLng=$originLng&dLat=$destLat&dLng=$destLng" +
                "&name=${android.net.Uri.encode(destName)}&mode=$mode&oType=$originType" +
                "&city=${android.net.Uri.encode(city)}" +
                "&simulate=${if (simulate) 1 else 0}&routeId=$routeId&naviType=$naviType"

        /** 构造多路线选择页跳转路径 */
        fun routePlanRoute(
            originLat: Double,
            originLng: Double,
            destLat: Double,
            destLng: Double,
            destName: String,
            mode: String = "drive",
            originType: String = ORIGIN_TYPE_MY,
            city: String = "",
            naviType: String = NAVI_TYPE_REAL_GPS,
        ): String =
            "route_plan?oLat=$originLat&oLng=$originLng&dLat=$destLat&dLng=$destLng" +
                "&name=${android.net.Uri.encode(destName)}&mode=$mode&oType=$originType" +
                "&city=${android.net.Uri.encode(city)}&naviType=$naviType"

        /** 构造路线步骤预览页跳转路径 */
        fun stepPreviewRoute(routeId: Int): String = "navi_steps?routeId=$routeId"

        // ---------- 天气模块二级页：单日天气详情（无底部导航栏） ----------
        /** 单日天气详情路由，携带该天在 7 日预报中的下标 */
        const val ROUTE_WEATHER_DAY = "weather_day/{dayIndex}"
        const val ARG_WEATHER_DAY_INDEX = "dayIndex"

        /** 构造单日天气详情跳转路径 */
        fun weatherDayRoute(dayIndex: Int): String = "weather_day/$dayIndex"

        // ---------- 「我的」模块二级页路由（无底部导航栏） ----------
        /** 我的收藏攻略 */
        const val ROUTE_FAVORITE_GUIDES = "profile/favorite_guides"

        /** 我的收藏帖子 */
        const val ROUTE_FAVORITE_POSTS = "profile/favorite_posts"

        /** 浏览记录 */
        const val ROUTE_BROWSE_HISTORY = "profile/browse_history"

        /** 隐私设置 */
        const val ROUTE_PRIVACY = "profile/privacy"

        /** 关于我们 */
        const val ROUTE_ABOUT = "profile/about"

        /** 设置（参考小红书风格的通用设置页：底部固定切换账号 / 退出登录） */
        const val ROUTE_SETTINGS = "profile/settings"

        /** AI 穿搭评估（上传穿搭照片 + 基于当前行程的 SSE 流式评估） */
        const val ROUTE_STYLIST = "profile/stylist"

        /** 切换账号（多账号列表 + 添加账号） */
        const val ROUTE_SWITCH_ACCOUNT = "profile/switch_account"

        /** 底部导航栏的展示顺序（首页为 [Map]） */
        val bottomTabs: List<AppDestination> = listOf(Recommend, Plan, Map, Share, Profile)
    }
}
