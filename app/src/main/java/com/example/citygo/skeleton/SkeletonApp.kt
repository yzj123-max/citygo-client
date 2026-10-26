package com.example.citygo.skeleton

import androidx.compose.foundation.layout.padding
import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.citygo.skeleton.data.RecommendViewModel
import com.example.citygo.skeleton.data.ShareViewModel
import com.example.citygo.skeleton.data.WeatherViewModel
import com.example.citygo.skeleton.data.AppViewModel as GlobalAppViewModel
import com.example.citygo.skeleton.navigation.AppDestination
import com.example.citygo.skeleton.profile.AboutScreen
import com.example.citygo.skeleton.profile.AppViewModel
import com.example.citygo.skeleton.profile.BrowseHistoryScreen
import com.example.citygo.skeleton.profile.FavoriteGuidesScreen
import com.example.citygo.skeleton.profile.FavoritePostsScreen
import com.example.citygo.skeleton.profile.PrivacyScreen
import com.example.citygo.skeleton.recommend.SpotDetailScreen
import com.example.citygo.skeleton.recommend.SpotRouteScreen
import com.example.citygo.skeleton.recommend.SpotSearchScreen
import com.example.citygo.skeleton.screens.ootd.OotdAnalyzeScreen
import com.example.citygo.skeleton.screens.stylist.OutfitHubScreen
import com.example.citygo.skeleton.screens.MapScreen
import com.example.citygo.skeleton.screens.NaviScreen
import com.example.citygo.skeleton.screens.PlanScreen
import com.example.citygo.skeleton.screens.ProfileScreen
import com.example.citygo.skeleton.screens.stylist.StylistScreen
import com.example.citygo.skeleton.screens.RecommendScreen
import com.example.citygo.skeleton.screens.RoutePlanScreen
import com.example.citygo.skeleton.screens.RouteStepPreviewScreen
import com.example.citygo.skeleton.screens.SettingsScreen
import com.example.citygo.skeleton.screens.ShareScreen
import com.example.citygo.skeleton.screens.SwitchAccountScreen
import com.example.citygo.skeleton.screens.AccountViewModel
import com.example.citygo.skeleton.screens.amap.RouteTravelMode
import com.example.citygo.skeleton.screens.amap.NaviMode
import com.example.citygo.skeleton.screens.auth.AuthDestination
import com.example.citygo.skeleton.screens.auth.CompleteProfileScreen
import com.example.citygo.skeleton.screens.auth.LoginScreen
import com.example.citygo.skeleton.screens.auth.RegisterScreen
import com.example.citygo.skeleton.screens.auth.UserInfoScreen
import com.example.citygo.skeleton.screens.user.EditProfileScreen
import com.example.citygo.skeleton.screens.user.UserProfileRoutes
import com.example.citygo.skeleton.screens.user.UserProfileScreen
import com.example.citygo.skeleton.screens.weather.WeatherDayDetailScreen
import com.example.citygo.skeleton.share.ArticleDetailScreen
import com.example.citygo.skeleton.share.ArticlePublishScreen
import com.example.citygo.skeleton.data.datastore.AccountSession

/**
 * 新版 App 导航骨架入口。
 *
 * 架构说明：
 * - 单个 Activity + [Scaffold] + [NavHost]，5 个 Tab 各自是 NavHost 中的一级目的地；
 * - 底部栏点击使用官方推荐的 saveState / restoreState 跳转参数：
 *   切换 Tab 时保存原 Tab 返回栈、恢复目标 Tab 历史，且不会重复创建实例；
 * - 回退键在非首页 Tab 时先回到首页 Tab，在首页时退出 App（系统默认行为）；
 * - 状态栏 / 底部导航栏安全区由 [Scaffold] 的 innerPadding 统一处理，
 *   页面内容通过 Modifier.padding(innerPadding) 避开系统栏与底部导航栏。
 */
@Composable
fun SkeletonApp(
    appViewModel: AppViewModel,
    // 新全局 VM（Repository + DataStore）：深色开关 / 当前城市等可持久化配置由此驱动；
    // 过渡期可空，未接入时「我的」页开关回退到旧 VM 内存态
    globalAppViewModel: GlobalAppViewModel? = null,
) {
    val navController = rememberNavController()
    var requestPlanning by remember { mutableStateOf(false) }
    // 全局行程状态由 globalAppVm（AppViewModel + Room）持有：
    // 【出行计划】展示的按天行程与【地图】渲染的 Marker / 相机联动是同一份 TravelPlan
    // 天气 ViewModel：出行计划页「天气预报」子页使用（数据走数据层 WeatherRepository）
    val weatherViewModel: WeatherViewModel = viewModel()
    // 游记社区 ViewModel：在导航层唯一持有（Activity 作用域），
    // 社区首页 / 发布页 / 详情页共享同一份数据——发布返回即时刷新、点赞收藏评论三处同步
    val shareViewModel: ShareViewModel = viewModel()
    // 景点推荐 ViewModel：首页推荐 / 搜索结果 / 景点详情 / 路线预览共享同一份景点缓存
    val recommendViewModel: RecommendViewModel = viewModel()
    // 多账号 ViewModel（Activity 作用域）：切换账号页、我的页头部共享同一份 DataStore 账号列表
    val accountViewModel: AccountViewModel = viewModel()
    // 全局状态（城市 / 账号 / 宠物 / 收藏 / 浏览记录等）：由 MainActivity 创建下传，
    // 「我的」页及其二级页共享同一份数据
    val appState by appViewModel.uiState.collectAsStateWithLifecycle()
    // 新全局 VM（Repository + DataStore）：MainActivity 已下传；
    // 预览等未下传场景按同一工厂取 Activity 作用域同一实例
    val globalAppVm: GlobalAppViewModel =
        globalAppViewModel ?: viewModel(factory = GlobalAppViewModel.factory())
    // 全局收藏（Room 持久化）：「我的收藏攻略 / 帖子」与详情页、社区卡片同源
    val collectSpots by globalAppVm.collectSpotList.collectAsStateWithLifecycle()
    val collectPosts by globalAppVm.collectPostList.collectAsStateWithLifecycle()
    // 全局浏览记录（Room 持久化，DAO 按浏览时间倒序）
    val browseHistory by globalAppVm.browseHistoryList.collectAsStateWithLifecycle()
    // 多账号：当前活跃账号直接 collect 全局 AccountSession(StateFlow)，
    // 登录 / 切换 / 退出 / 完善资料后「我的」页等所有观察处自动重组刷新
    val activeAccount by accountViewModel.currentAccount.collectAsStateWithLifecycle()

    // AccountViewModel 的一次性 Toast（切换 /info 失败、退出结果等）
    val sheetContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        accountViewModel.toastEvent.collect {
            android.widget.Toast.makeText(sheetContext, it, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // 观察当前返回栈栈顶：用于 Tab 选中态，以及判断是否为二级页（隐藏底部导航栏）
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination
    // 仅当目的地属于 5 个底部 Tab 之一时显示底部导航栏
    val showBottomBar = AppDestination.bottomTabs.any { it.route == currentDestination?.route }

    // 切换到底部地图 Tab 的统一跳转（saveState/restoreState 保持各 Tab 状态）
    val switchToMapTab: () -> Unit = {
        navController.navigate(AppDestination.ROUTE_MAP) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // 切换到「计划」Tab 的统一跳转（景点详情「加入行程」后可选跳转使用）
    val switchToPlanTab: () -> Unit = {
        navController.navigate(AppDestination.ROUTE_PLAN) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // 切换到「推荐」Tab 的统一跳转（计划页某天卡片「加入景点」时使用）
    val switchToRecommendTab: () -> Unit = {
        navController.navigate(AppDestination.ROUTE_RECOMMEND) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // 景点详情「查看游记」：先在社区 VM 写入城市关键词，再切换到分享 Tab 自动筛选
    val viewArticlesByCity: (String) -> Unit = { keyword ->
        shareViewModel.setCommunityKeyword(keyword)
        navController.navigate(AppDestination.ROUTE_SHARE) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // 账号模块：清空整个返回栈后跳转（登录成功后进首页、退出登录 / 401 回登录页时，
    // 保证系统回退键不会再回到受保护的账号页面）
    val clearStackAndNavigate: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    // 清掉旧账号保存的 Tab 返回栈，不能在新账号下 restoreState 恢复旧页面/编辑草稿。
    val identity by AccountSession.identity.collectAsStateWithLifecycle()
    val entryIdentity = remember(currentBackStackEntry) { identity }
    // 添加账号时登录流程仍需继续，但不能从已变更身份的登录页退回旧账号页面。
    BackHandler(enabled = currentDestination?.route?.startsWith("auth/") == true && entryIdentity != identity) {
        clearStackAndNavigate(AuthDestination.LOGIN)
    }
    var previousIdentity by remember { mutableStateOf(identity) }
    LaunchedEffect(identity) {
        if (identity != previousIdentity) {
            AppDestination.bottomTabs.forEach { navController.clearBackStack(it.route) }
            val route = navController.currentBackStackEntry?.destination?.route
            // 登录流程会先建立最小身份再读取资料；不销毁其 AuthViewModel。
            if (route != null && !route.startsWith("auth/")) {
                clearStackAndNavigate(
                    if (identity.phone == null) AuthDestination.LOGIN else AppDestination.ROUTE_RECOMMEND,
                )
            }
            previousIdentity = identity
        }
    }

    // 全局 token 失效处置（拦截器发现 HTTP 401 / 业务体 code=401 → 发射事件）：
    // Toast 提示 → DataStore 全部取消活跃（账号记录保留，便于重新登录）→ 清全局会话
    // → 清栈跳登录页。已在 auth/* 页面时（如登录中拉 userInfo 失败）不吐司不跳转，
    // 由该页面自身的 ViewModel 处理，避免重复提示。
    val appContext = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.example.citygo.skeleton.data.datastore.AccountSession.authInvalidation
            .collect { expected ->
                if (!AccountSession.isCurrent(expected)) return@collect
                val route = navController.currentBackStackEntry?.destination?.route
                if (route != null && !route.startsWith("auth/")) {
                    android.widget.Toast
                        .makeText(
                            appContext,
                            com.example.citygo.R.string.auth_token_expired,
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    com.example.citygo.skeleton.data.datastore.AccountStore(appContext)
                        .deactivateAll(expected.phone)
                    if (!AccountSession.isCurrent(expected)) return@collect
                    com.example.citygo.skeleton.data.datastore.AccountSession.clear()
                    clearStackAndNavigate(AuthDestination.LOGIN)
                }
            }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // 二级页（如笔记详情）不显示底部 5 Tab 导航栏
            if (showBottomBar) {
                AppBottomBar(navController = navController, currentDestinationRoute = currentDestination?.route)
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            // 启动路由按全局 AccountSession 是否有当前账号决定：有 token 进首页，否则进登录页
            // （Application.onCreate 已从 DataStore hydrate，全 App token 唯一来源为 StateFlow）
            startDestination = if (com.example.citygo.skeleton.data.datastore.AccountSession.isLoggedIn()) {
                AppDestination.ROUTE_RECOMMEND
            } else {
                AuthDestination.LOGIN
            },
        ) {
            // ---------- 账号模块：登录 / 注册 / 个人信息（二级全屏页，无底部导航栏） ----------
            // 登录页（无 token 时的启动页；也用于「设置-切换账号」场景，此时有上级页可返回）
            composable(AuthDestination.LOGIN) {
                // 从「设置-切换账号-添加账号」进入时显示返回箭头；冷启动直达登录页不显示
                val prevRoute = navController.previousBackStackEntry?.destination?.route
                val canBack = entryIdentity == identity && (prevRoute == AppDestination.ROUTE_SETTINGS ||
                    prevRoute == AppDestination.ROUTE_SWITCH_ACCOUNT)
                LoginScreen(
                    onLoginSuccess = { isNewUser ->
                        // isNewUser=true（新注册用户）→ 完善资料页（提交成功后才入多账号列表）；
                        // isNewUser=false（老用户）→ 已在 VM 拉取资料并入库，直接清空栈进首页。
                        if (isNewUser) {
                            navController.navigate(AuthDestination.COMPLETE_PROFILE) {
                                launchSingleTop = true
                            }
                        } else {
                            clearStackAndNavigate(AppDestination.ROUTE_RECOMMEND)
                        }
                    },
                    onGoRegister = { navController.navigate(AuthDestination.REGISTER) },
                    onBack = if (canBack) {
                        { navController.popBackStack() }
                    } else {
                        null
                    },
                )
            }
            // 完善资料页（新用户首次登录）：提交成功 VM 已入库 + 更新全局会话，清空栈进首页
            composable(AuthDestination.COMPLETE_PROFILE) {
                CompleteProfileScreen(
                    onCompleted = { clearStackAndNavigate(AppDestination.ROUTE_RECOMMEND) },
                )
            }
            // 注册页：注册成功 / 顶部返回都回到登录页
            composable(AuthDestination.REGISTER) {
                RegisterScreen(
                    onRegisterSuccess = {
                        navController.popBackStack(AuthDestination.LOGIN, inclusive = false)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            // 个人信息页：进入自动拉取用户信息；401 由页面回调清栈回登录
            composable(AuthDestination.USER_INFO) {
                // 从主 App「我的」跳入时栈内有上一页才显示返回箭头；登录后直达时不显示
                val canBack = navController.previousBackStackEntry != null
                UserInfoScreen(
                    onBackToLogin = { clearStackAndNavigate(AuthDestination.LOGIN) },
                    onTokenInvalid = { clearStackAndNavigate(AuthDestination.LOGIN) },
                    onEnterApp = { clearStackAndNavigate(AppDestination.ROUTE_RECOMMEND) },
                    onBack = if (canBack) {
                        { navController.popBackStack() }
                    } else {
                        null
                    },
                )
            }

            // 一级 Tab 页：套用 Scaffold innerPadding 避开状态栏与底部导航栏
            // 地图页（首页）
            composable(AppDestination.ROUTE_MAP) {
                MapScreen(
                    appViewModel = globalAppVm,
                    modifier = Modifier.padding(innerPadding),
                    onStartNavigation = { mode, oLat, oLng, dLat, dLng, destName, naviMode, city ->
                        // 统一先进入多路线选择页（导航前置页），由卡片再进入导航；
                        // naviMode 为显式分支：SIMULATION=条件A 模拟导航，REAL_GPS=条件B 真实 GPS 导航
                        navController.navigate(
                            AppDestination.routePlanRoute(
                                originLat = oLat,
                                originLng = oLng,
                                destLat = dLat,
                                destLng = dLng,
                                destName = destName,
                                mode = mode.name.lowercase(),
                                originType = if (naviMode == NaviMode.REAL_GPS) {
                                    AppDestination.ORIGIN_TYPE_MY
                                } else {
                                    AppDestination.ORIGIN_TYPE_SPOT
                                },
                                city = city,
                                naviType = naviMode.wireValue,
                            ),
                        )
                    },
                    // Marker 信息卡「在行程卡片中查看」：写入定位事件并切到出行计划 Tab
                    onViewInPlan = { place ->
                        globalAppVm.focusPlaceInPlan(place)
                        switchToPlanTab()
                    },
                )
            }
            // 出行计划页（内部嵌套 TabRow：出行计划 / 天气预报）
            composable(AppDestination.ROUTE_PLAN) {
                PlanScreen(
                    onViewMapPoints = switchToMapTab,
                    appViewModel = globalAppVm,
                    weatherViewModel = weatherViewModel,
                    onWeatherDayClick = { dayIndex ->
                        navController.navigate(AppDestination.weatherDayRoute(dayIndex))
                    },
                    // 点击行程地点条目：点位信息经全局 VM 传递给地图页，并切换地图 Tab
                    onPlanPointClick = { place ->
                        globalAppVm.focusPlaceOnMap(place)
                        switchToMapTab()
                    },
                    // 某天卡片「加入景点」：目标天已暂存进全局 VM，跳到「推荐」Tab 选景点
                    onGoRecommend = switchToRecommendTab,
                    requestPlanning = requestPlanning,
                    onPlanningRequestHandled = { requestPlanning = false },
                    modifier = Modifier.padding(innerPadding),
                )
            }
            // 旅行社区首页（双列瀑布流；点击卡片进游记详情，右上角进发布页）
            composable(AppDestination.ROUTE_SHARE) {
                ShareScreen(
                    onArticleClick = { articleId ->
                        navController.navigate(AppDestination.articleDetailRoute(articleId))
                    },
                    modifier = Modifier.padding(innerPadding),
                    shareViewModel = shareViewModel,
                    appViewModel = globalAppVm,
                    onPublish = { navController.navigate(AppDestination.ROUTE_PUBLISH_ARTICLE) },
                    onOpenOutfit = { navController.navigate(AppDestination.ROUTE_OUTFIT_TOOLS) },
                )
            }
            // 景点推荐页：顶部城市切换 + 搜索，点击卡片进景点详情
            composable(AppDestination.ROUTE_RECOMMEND) {
                RecommendScreen(
                    onPlan = { city ->
                        if (city != "全部城市") globalAppVm.selectCity(city)
                        requestPlanning = true
                        switchToPlanTab()
                    },
                    onOpenOutfit = { navController.navigate(AppDestination.ROUTE_OUTFIT_TOOLS) },
                    petFriendly = globalAppVm.aiPetFriendlyPreferred.collectAsStateWithLifecycle().value,
                    onPetFriendlyChange = globalAppVm::setAiPetFriendlyPreferred,
                    onSpotClick = { spotId ->
                        navController.navigate(AppDestination.spotDetailRoute(spotId))
                    },
                    onSearch = { keyword ->
                        navController.navigate(AppDestination.spotSearchRoute(keyword))
                    },
                    recommendViewModel = recommendViewModel,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            // 我的页面（个人中心：城市选择 / 用户信息 / 收藏与历史 / 宠物档案 / 设置）
            composable(AppDestination.ROUTE_PROFILE) {
                // 深色开关的持久化状态（全局 VM + DataStore）
                val persistedDarkMode by globalAppVm.isDarkMode.collectAsStateWithLifecycle()
                ProfileScreen(
                    appViewModel = appViewModel,
                    globalAppViewModel = globalAppVm,
                    darkModeChecked = persistedDarkMode,
                    onDarkModeChange = globalAppVm::setDarkMode,
                    onPersistCity = globalAppVm::selectCity,
                    // 多账号体系下当前活跃账号（DataStore）：头部昵称 / 身份以 JWT 账号为准
                    activeAccount = activeAccount,
                    onOpenFavoriteGuides = {
                        navController.navigate(AppDestination.ROUTE_FAVORITE_GUIDES)
                    },
                    onOpenFavoritePosts = {
                        navController.navigate(AppDestination.ROUTE_FAVORITE_POSTS)
                    },
                    onOpenBrowseHistory = {
                        navController.navigate(AppDestination.ROUTE_BROWSE_HISTORY)
                    },
                    onOpenPrivacy = {
                        navController.navigate(AppDestination.ROUTE_PRIVACY)
                    },
                    onOpenAbout = {
                        navController.navigate(AppDestination.ROUTE_ABOUT)
                    },
                    // 通用设置页（参考小红书风格：退出登录 / 切换账号在页面底部）
                    onOpenSettings = {
                        navController.navigate(AppDestination.ROUTE_SETTINGS)
                    },
                    // 「账号中心」：以全局会话是否登录判断（token 唯一来源 AccountSession）
                    onOpenAccountCenter = {
                        if (com.example.citygo.skeleton.data.datastore.AccountSession.isLoggedIn()) {
                            navController.navigate(AuthDestination.USER_INFO)
                        } else {
                            clearStackAndNavigate(AuthDestination.LOGIN)
                        }
                    },
                    // 点击昵称进入【用户资料主页】（未登录则去登录页）
                    onOpenUserProfile = {
                        if (com.example.citygo.skeleton.data.datastore.AccountSession.isLoggedIn()) {
                            navController.navigate(UserProfileRoutes.USER_PROFILE) {
                                launchSingleTop = true
                            }
                        } else {
                            clearStackAndNavigate(AuthDestination.LOGIN)
                        }
                    },
                    // 我的页最底部：切换账号（进入全屏切换页，可直接添加账号）/ 退出登录
                    onOpenSwitchAccount = {
                        navController.navigate(AppDestination.ROUTE_SWITCH_ACCOUNT) {
                            launchSingleTop = true
                        }
                    },
                    onLogout = {
                        accountViewModel.logoutCurrent {
                            clearStackAndNavigate(AuthDestination.LOGIN)
                        }
                    },
                    modifier = Modifier.padding(innerPadding),
                )
            }

            // ---------- 二级页面（无底部导航栏，安全区由页面自行处理） ----------
            // 游记发布页（无参；先于 article/{articleId} 注册，避免被路径参数路由匹配）
            composable(AppDestination.ROUTE_PUBLISH_ARTICLE) {
                ArticlePublishScreen(
                    shareViewModel = shareViewModel,
                    onBack = { navController.popBackStack() },
                )
            }

            // 游记详情页：article/{articleId}
            composable(
                route = AppDestination.ROUTE_ARTICLE_DETAIL,
                arguments = listOf(
                    navArgument(AppDestination.ARG_ARTICLE_ID) { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val articleId = backStackEntry.arguments
                    ?.getString(AppDestination.ARG_ARTICLE_ID)
                    .orEmpty()
                ArticleDetailScreen(
                    articleId = articleId,
                    shareViewModel = shareViewModel,
                    onBack = { navController.popBackStack() },
                )
            }

            // 景点搜索结果页：spot_search?keyword=xx（query 参数可空，预留联想能力）
            composable(
                route = AppDestination.ROUTE_SPOT_SEARCH,
                arguments = listOf(
                    navArgument(AppDestination.ARG_SPOT_KEYWORD) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                val keyword = backStackEntry.arguments?.getString(AppDestination.ARG_SPOT_KEYWORD)
                SpotSearchScreen(
                    initialKeyword = keyword,
                    onBack = { navController.popBackStack() },
                    onSpotClick = { spotId ->
                        navController.navigate(AppDestination.spotDetailRoute(spotId))
                    },
                    recommendViewModel = recommendViewModel,
                )
            }

            // 景点详情页：spot_detail/{spotId}
            composable(
                route = AppDestination.ROUTE_SPOT_DETAIL,
                arguments = listOf(
                    navArgument(AppDestination.ARG_SPOT_ID) { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val spotId = backStackEntry.arguments
                    ?.getString(AppDestination.ARG_SPOT_ID)
                    .orEmpty()
                SpotDetailScreen(
                    spotId = spotId,
                    onBack = { navController.popBackStack() },
                    onViewArticles = viewArticlesByCity,
                    onNavigate = { id ->
                        navController.navigate(AppDestination.spotRouteRoute(id))
                    },
                    recommendViewModel = recommendViewModel,
                    appViewModel = globalAppVm,
                    // 跳转「计划」Tab 的逻辑已在详情页预留（onGoToPlan）；
                    // 待计划页迁移到全局 AppViewModel 行程后，改为 onGoToPlan = switchToPlanTab 即可开启
                    onGoToPlan = null,
                )
            }

            // 景点路线预览页：spot_route?spotId=xx（多交通方式，开始语音导航进 NaviScreen）
            composable(
                route = AppDestination.ROUTE_SPOT_ROUTE,
                arguments = listOf(
                    navArgument(AppDestination.ARG_SPOT_ROUTE_ID) { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val routeSpotId = backStackEntry.arguments
                    ?.getString(AppDestination.ARG_SPOT_ROUTE_ID)
                    .orEmpty()
                SpotRouteScreen(
                    spotId = routeSpotId,
                    onBack = { navController.popBackStack() },
                    recommendViewModel = recommendViewModel,
                    onStartNavigation = { mode, originLat, originLng, destLat, destLng, destName ->
                            navController.navigate(
                                AppDestination.routePlanRoute(
                                    originLat = originLat,
                                    originLng = originLng,
                                    destLat = destLat,
                                    destLng = destLng,
                                    destName = destName,
                                    mode = mode.name.lowercase(),
                                originType = AppDestination.ORIGIN_TYPE_MY,
                                city = recommendViewModel.spotMap.value[routeSpotId]?.city.orEmpty(),
                                naviType = AppDestination.NAVI_TYPE_REAL_GPS,
                            ),
                            )
                    },
                )
            }

            // 多路线选择页（导航前置页）：参数与导航页一致，卡片进入导航 / 右侧按钮进步骤预览
            composable(
                route = AppDestination.ROUTE_ROUTE_PLAN,
                arguments = listOf(
                    navArgument(AppDestination.ARG_NAVI_O_LAT) { type = NavType.StringType; nullable = true },
                    navArgument(AppDestination.ARG_NAVI_O_LNG) { type = NavType.StringType; nullable = true },
                    navArgument(AppDestination.ARG_NAVI_D_LAT) { type = NavType.StringType; nullable = true },
                    navArgument(AppDestination.ARG_NAVI_D_LNG) { type = NavType.StringType; nullable = true },
                    navArgument(AppDestination.ARG_NAVI_NAME) { type = NavType.StringType; nullable = true },
                    navArgument(AppDestination.ARG_NAVI_MODE) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = "drive"
                    },
                    navArgument(AppDestination.ARG_NAVI_O_TYPE) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = AppDestination.ORIGIN_TYPE_MY
                    },
                    navArgument(AppDestination.ARG_NAVI_CITY) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = ""
                    },
                    navArgument(AppDestination.ARG_NAVI_NAVI_TYPE) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = AppDestination.NAVI_TYPE_REAL_GPS
                    },
                ),
            ) { backStackEntry ->
                val args = backStackEntry.arguments
                val travelMode = runCatching {
                    RouteTravelMode.valueOf(
                        (args?.getString(AppDestination.ARG_NAVI_MODE) ?: "drive").uppercase(),
                    )
                }.getOrDefault(RouteTravelMode.DRIVE)
                val originIsMyLocation =
                    args?.getString(AppDestination.ARG_NAVI_O_TYPE) != AppDestination.ORIGIN_TYPE_SPOT
                val naviMode = NaviMode.forOrigin(originIsMyLocation)
                val oLat = args?.getString(AppDestination.ARG_NAVI_O_LAT)?.toDoubleOrNull() ?: 0.0
                val oLng = args?.getString(AppDestination.ARG_NAVI_O_LNG)?.toDoubleOrNull() ?: 0.0
                val dLat = args?.getString(AppDestination.ARG_NAVI_D_LAT)?.toDoubleOrNull() ?: 0.0
                val dLng = args?.getString(AppDestination.ARG_NAVI_D_LNG)?.toDoubleOrNull() ?: 0.0
                val destName = args?.getString(AppDestination.ARG_NAVI_NAME) ?: "目的地"
                val city = args?.getString(AppDestination.ARG_NAVI_CITY).orEmpty()
                RoutePlanScreen(
                    originLat = oLat,
                    originLng = oLng,
                    destLat = dLat,
                    destLng = dLng,
                    destName = destName,
                    travelMode = travelMode,
                    originIsMyLocation = originIsMyLocation,
                    cityName = city,
                    onBack = { navController.popBackStack() },
                    onStartNavigation = { routeId ->
                        navController.navigate(
                            AppDestination.navigationRoute(
                                originLat = oLat,
                                originLng = oLng,
                                destLat = dLat,
                                destLng = dLng,
                                destName = destName,
                                mode = travelMode.name.lowercase(),
                                originType = if (originIsMyLocation) {
                                    AppDestination.ORIGIN_TYPE_MY
                                } else {
                                    AppDestination.ORIGIN_TYPE_SPOT
                                },
                                city = city,
                                routeId = routeId,
                                naviType = naviMode.wireValue,
                            ),
                        )
                    },
                    onPreviewSteps = { routeId ->
                        navController.navigate(AppDestination.stepPreviewRoute(routeId))
                    },
                )
            }

            // 路线步骤预览页：只展示步骤；底部主按钮从当前位置进入真实 GPS 导航
            composable(
                route = AppDestination.ROUTE_STEP_PREVIEW,
                arguments = listOf(
                    navArgument(AppDestination.ARG_STEP_ROUTE_ID) { type = NavType.IntType },
                ),
            ) { backStackEntry ->
                val routeId = backStackEntry.arguments
                    ?.getInt(AppDestination.ARG_STEP_ROUTE_ID)
                    ?: -1
                RouteStepPreviewScreen(
                    routeId = routeId,
                    onBack = { navController.popBackStack() },
                    onStartNavigation = { id ->
                        // 起终点 / 方式取自最近一次算路缓存（NaviPath 不可序列化的折中）
                        val plan = com.example.citygo.skeleton.screens.amap
                            .NaviRouteChoiceStore.latest
                        if (plan != null) {
                            navController.navigate(
                                AppDestination.navigationRoute(
                                    originLat = plan.origin.latitude,
                                    originLng = plan.origin.longitude,
                                    destLat = plan.destination.latitude,
                                    destLng = plan.destination.longitude,
                                    destName = plan.destName,
                                    mode = plan.mode.name.lowercase(),
                                    originType = if (plan.originIsMyLocation) {
                                        AppDestination.ORIGIN_TYPE_MY
                                    } else {
                                        AppDestination.ORIGIN_TYPE_SPOT
                                    },
                                    city = plan.city,
                                    simulate = !plan.originIsMyLocation,
                                    // 起点会取导航页最新定位，旧路线 id 不能跨起点复用。
                                    routeId = if (plan.originIsMyLocation) -1 else id,
                                    naviType = NaviMode.forOrigin(plan.originIsMyLocation).wireValue,
                                ),
                            )
                        } else {
                            // 进程回收等导致缓存失效：退回选线页重新算路
                            navController.popBackStack()
                        }
                    },
                )
            }

            // 全屏实时导航页：起终点经纬度 + 终点名称 + 交通方式（由选线页卡片跳入）
            composable(
                route = AppDestination.ROUTE_NAVIGATION,
                arguments = listOf(
                    navArgument(AppDestination.ARG_NAVI_O_LAT) { type = NavType.StringType; nullable = true },
                    navArgument(AppDestination.ARG_NAVI_O_LNG) { type = NavType.StringType; nullable = true },
                    navArgument(AppDestination.ARG_NAVI_D_LAT) { type = NavType.StringType; nullable = true },
                    navArgument(AppDestination.ARG_NAVI_D_LNG) { type = NavType.StringType; nullable = true },
                    navArgument(AppDestination.ARG_NAVI_NAME) { type = NavType.StringType; nullable = true },
                    navArgument(AppDestination.ARG_NAVI_MODE) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = "drive"
                    },
                    navArgument(AppDestination.ARG_NAVI_O_TYPE) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = AppDestination.ORIGIN_TYPE_MY
                    },
                    navArgument(AppDestination.ARG_NAVI_CITY) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = ""
                    },
                    navArgument(AppDestination.ARG_NAVI_SIMULATE) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = "0"
                    },
                    navArgument(AppDestination.ARG_NAVI_ROUTE_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = "-1"
                    },
                    navArgument(AppDestination.ARG_NAVI_NAVI_TYPE) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = AppDestination.NAVI_TYPE_REAL_GPS
                    },
                ),
            ) { backStackEntry ->
                val args = backStackEntry.arguments
                val travelMode = runCatching {
                    RouteTravelMode.valueOf(
                        (args?.getString(AppDestination.ARG_NAVI_MODE) ?: "drive").uppercase(),
                    )
                }.getOrDefault(RouteTravelMode.DRIVE)
                // 起点类型：只有显式 spot 才按「两景点间导航」处理，其余均为设备当前位置
                val originIsMyLocation =
                    args?.getString(AppDestination.ARG_NAVI_O_TYPE) != AppDestination.ORIGIN_TYPE_SPOT
                val naviMode = NaviMode.forOrigin(originIsMyLocation)
                NaviScreen(
                    originLat = args?.getString(AppDestination.ARG_NAVI_O_LAT)?.toDoubleOrNull() ?: 0.0,
                    originLng = args?.getString(AppDestination.ARG_NAVI_O_LNG)?.toDoubleOrNull() ?: 0.0,
                    destLat = args?.getString(AppDestination.ARG_NAVI_D_LAT)?.toDoubleOrNull() ?: 0.0,
                    destLng = args?.getString(AppDestination.ARG_NAVI_D_LNG)?.toDoubleOrNull() ?: 0.0,
                    destName = args?.getString(AppDestination.ARG_NAVI_NAME) ?: "目的地",
                    onBack = { navController.popBackStack() },
                    travelMode = travelMode,
                    originIsMyLocation = originIsMyLocation,
                    cityName = args?.getString(AppDestination.ARG_NAVI_CITY).orEmpty(),
                    emulatorMode = args?.getString(AppDestination.ARG_NAVI_SIMULATE) == "1",
                    naviMode = naviMode,
                    preferredRouteId = args?.getString(AppDestination.ARG_NAVI_ROUTE_ID)
                        ?.toIntOrNull() ?: -1,
                )
            }

            // 单日天气详情页：weather_day/{dayIndex}（由天气页 7 日预报条目点击进入）
            composable(
                route = AppDestination.ROUTE_WEATHER_DAY,
                arguments = listOf(
                    navArgument(AppDestination.ARG_WEATHER_DAY_INDEX) { type = NavType.IntType },
                ),
            ) { backStackEntry ->
                val dayIndex = backStackEntry.arguments
                    ?.getInt(AppDestination.ARG_WEATHER_DAY_INDEX)
                    ?: 0
                WeatherDayDetailScreen(
                    dayIndex = dayIndex,
                    weatherViewModel = weatherViewModel,
                    onBack = { navController.popBackStack() },
                )
            }

            // ---------- 「我的」模块二级页（数据来自全局 AppViewModel，返回即 pop） ----------
            // 我的收藏攻略
            composable(AppDestination.ROUTE_FAVORITE_GUIDES) {
                FavoriteGuidesScreen(
                    spots = collectSpots,
                    onBack = { navController.popBackStack() },
                    onRemove = globalAppVm::removeCollectSpot,
                    onOpenSpot = { spotId ->
                        navController.navigate(AppDestination.spotDetailRoute(spotId))
                    },
                )
            }
            // 我的收藏帖子
            composable(AppDestination.ROUTE_FAVORITE_POSTS) {
                com.example.citygo.skeleton.share.CommunityLibraryScreen(
                    shareViewModel = shareViewModel,
                    appViewModel = globalAppVm,
                    onBack = { navController.popBackStack() },
                    onOpen = { navController.navigate(AppDestination.articleDetailRoute(it)) },
                    onPublish = { navController.navigate(AppDestination.ROUTE_PUBLISH_ARTICLE) },
                )
            }
            // 浏览记录（全局 Room，支持清空）
            composable(AppDestination.ROUTE_BROWSE_HISTORY) {
                BrowseHistoryScreen(
                    records = browseHistory,
                    onBack = { navController.popBackStack() },
                    onClearHistory = globalAppVm::clearBrowseHistory,
                    onOpenSpot = { spotId ->
                        navController.navigate(AppDestination.spotDetailRoute(spotId))
                    },
                )
            }
            // 隐私设置
            composable(AppDestination.ROUTE_PRIVACY) {
                PrivacyScreen(onBack = { navController.popBackStack() })
            }
            // 关于我们
            composable(AppDestination.ROUTE_ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
            // AI 穿搭评估（仅新增模块：多图上传 + SSE 流式评估，行程数据取全局当前行程）
            composable(AppDestination.ROUTE_OUTFIT_TOOLS) {
                OutfitHubScreen(onBack = { navController.popBackStack() },
                    onSingle = { navController.navigate(AppDestination.ROUTE_OOTD_ANALYZE) },
                    onMulti = { navController.navigate(AppDestination.ROUTE_STYLIST) })
            }
            composable(AppDestination.ROUTE_OOTD_ANALYZE) {
                OotdAnalyzeScreen(onBack = { navController.popBackStack() })
            }
            composable(AppDestination.ROUTE_STYLIST) {
                StylistScreen(
                    appViewModel = globalAppVm,
                    onBack = { navController.popBackStack() },
                )
            }
            // 设置（参考小红书风格）：
            // - 切换账号：进入独立的多账号切换页（不是弹窗）；
            // - 退出登录：后端成功后已清 token 与账号记录，清空整个返回栈跳登录页。
            composable(AppDestination.ROUTE_SETTINGS) {
                val darkMode by globalAppVm.isDarkMode.collectAsStateWithLifecycle()
                SettingsScreen(
                    darkMode = darkMode,
                    onDarkModeChange = globalAppVm::setDarkMode,
                    onPrivacy = { navController.navigate(AppDestination.ROUTE_PRIVACY) },
                    onAbout = { navController.navigate(AppDestination.ROUTE_ABOUT) },
                    onAccountCenter = { navController.navigate(AuthDestination.USER_INFO) },
                    onClearCache = {
                        appViewModel.clearCache()
                        android.widget.Toast.makeText(appContext, "缓存清理入口当前为演示功能", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onBack = { navController.popBackStack() },
                    onSwitchAccount = {
                        navController.navigate(AppDestination.ROUTE_SWITCH_ACCOUNT) {
                            launchSingleTop = true
                        }
                    },
                    onLoggedOut = { clearStackAndNavigate(AuthDestination.LOGIN) },
                )
            }
            // 切换账号页：点击已有账号本地切 token 后回「我的」Tab；添加账号进登录页
            composable(AppDestination.ROUTE_SWITCH_ACCOUNT) {
                SwitchAccountScreen(
                    viewModel = accountViewModel,
                    onBack = { navController.popBackStack() },
                    onAddAccount = {
                        navController.navigate(AuthDestination.LOGIN) {
                            launchSingleTop = true
                        }
                    },
                    // 切换完成：弹掉切换页 / 设置页，回到「我的」Tab（头部随 DataStore 自动刷新）
                    onSwitched = {
                        navController.popBackStack(AppDestination.ROUTE_PROFILE, inclusive = false)
                    },
                    // 长按删除了当前在用账号：已无登录身份，清栈跳登录页
                    onActiveAccountDeleted = {
                        clearStackAndNavigate(AuthDestination.LOGIN)
                    },
                )
            }
            // 用户资料主页：进入自动 GET /user/info；点头像上传头像；点编辑进编辑页
            composable(UserProfileRoutes.USER_PROFILE) {
                UserProfileScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = {
                        navController.navigate(UserProfileRoutes.EDIT_PROFILE) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            // 编辑资料页：保存成功回写本地后返回，资料主页 / 主页昵称自动刷新
            composable(UserProfileRoutes.EDIT_PROFILE) {
                EditProfileScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

/**
 * 底部导航栏：根据当前路由高亮选中项。
 *
 * @param currentDestinationRoute 当前返回栈栈顶路由（由外层统一观察传入）
 */
@Composable
private fun AppBottomBar(
    navController: androidx.navigation.NavHostController,
    currentDestinationRoute: String?,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        AppDestination.bottomTabs.forEach { destination ->
            // hierarchy 匹配可兼容后续 Tab 内再嵌套导航图的情况
            val selected = currentDestinationRoute == destination.route

            NavigationBarItem(
                selected = selected,
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = destination.label,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                label = { Text(destination.label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                onClick = {
                    navController.navigate(destination.route) {
                        // 弹出到图起点但保留其状态，避免按回退键时堆叠多个 Tab
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // 重复点击同一 Tab 不重复创建实例
                        launchSingleTop = true
                        // 恢复该 Tab 之前保存的返回栈与页面状态
                        restoreState = true
                    }
                },
            )
        }
    }
}
