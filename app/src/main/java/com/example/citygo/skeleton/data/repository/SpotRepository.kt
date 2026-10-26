package com.example.citygo.skeleton.data.repository

import com.example.citygo.skeleton.data.model.ScenicSpot
import com.example.citygo.skeleton.data.model.SpotReview
import com.example.citygo.skeleton.data.model.SpotTag
import kotlinx.coroutines.delay

/* =====================================================================================
 * 景点数据仓库（当前为本地 Mock 实现，后续替换为后端 REST 接口即可）
 *
 * 后端联调时仅需将 [MockSpotRepository] 替换为 Retrofit 实现，接口签名保持不变：
 *
 *   GET /v1/spots?city={city}            首页城市联动推荐列表
 *   GET /v1/spots/{spotId}               景点详情
 *   GET /v1/spots/search?keyword={kw}    关键词搜索（名称 / 城市 / 简介）
 *   GET /v1/spots/suggest?keyword={kw}   搜索联想（输入中实时补全，预留）
 *   GET /v1/cities                       可选城市列表
 *
 * 全部图片使用 mock://scene/{id} 本地场景图（CommunityImage 统一渲染），
 * 后端返回 https 直链后自动切换为 Coil 网络加载，无需改动 UI。
 * ===================================================================================== */

interface SpotRepository {

    /**
     * 获取推荐景点列表。
     *
     * @param city 城市名；null 或「全部城市」时返回全部景点
     */
    suspend fun getScenicSpots(city: String? = null): List<ScenicSpot>

    /** 按 id 获取景点详情；不存在返回 null */
    suspend fun getSpotById(spotId: String): ScenicSpot?

    /** 关键词搜索：匹配景点名称 / 城市 / 一句话简介（全部城市范围） */
    suspend fun searchSpots(keyword: String): List<ScenicSpot>

    /**
     * 搜索联想：输入过程中按景点名称前缀 / 包含匹配，返回景点名称建议。
     * 预留能力，后续接入后端联想接口（支持别名 / 拼音 / 热门词）。
     */
    suspend fun getSearchSuggestions(keyword: String): List<String>

    /** 获取有景点数据的城市列表（首页城市下拉控件数据源） */
    suspend fun getCities(): List<String>
}

/**
 * 本地 Mock 景点仓库：8 个国内热门旅游城市、23 个热门 / 小众景点。
 */
class MockSpotRepository : SpotRepository {

    override suspend fun getScenicSpots(city: String?): List<ScenicSpot> {
        // 模拟网络耗时，让首页城市切换时展示加载态
        delay(MOCK_LATENCY_MS)
        return if (city.isNullOrBlank() || city == CITY_ALL) {
            MOCK_SPOTS
        } else {
            MOCK_SPOTS.filter { it.city == city }
        }
    }

    override suspend fun getSpotById(spotId: String): ScenicSpot? {
        delay(MOCK_LATENCY_MS / 2)
        return MOCK_SPOTS.firstOrNull { it.spotId == spotId }
    }

    override suspend fun searchSpots(keyword: String): List<ScenicSpot> {
        delay(MOCK_LATENCY_MS)
        val kw = keyword.trim()
        if (kw.isEmpty()) return emptyList()
        return MOCK_SPOTS.filter {
            it.name.contains(kw) || it.city.contains(kw) || it.shortDesc.contains(kw)
        }
    }

    override suspend fun getSearchSuggestions(keyword: String): List<String> {
        // TODO 后续替换为 GET /v1/spots/suggest，Mock 阶段本地名称匹配
        val kw = keyword.trim()
        if (kw.isEmpty()) return emptyList()
        return MOCK_SPOTS
            .filter { it.name.contains(kw) || it.city.startsWith(kw) }
            .take(SUGGEST_LIMIT)
            .map { it.name }
            .distinct()
    }

    override suspend fun getCities(): List<String> =
        MOCK_SPOTS.map { it.city }.distinct()

    private companion object {
        const val MOCK_LATENCY_MS = 400L
        const val SUGGEST_LIMIT = 6
    }
}

/** 城市筛选「全部」选项文案 */
const val CITY_ALL = "全部城市"

/* =====================================================================================
 * Mock 种子数据
 *
 * 坐标均为 GCJ-02 高德坐标系的近似值（仅供路线规划演示，非精确门牌坐标）。
 * ===================================================================================== */

/** 快速构造一条 mock:// 场景图地址 */
private fun scene(sceneId: Int): String = "mock://scene/${sceneId.coerceIn(0, 11)}"

private fun review(user: String, score: Double, content: String) = SpotReview(user, score, content)

private val MOCK_SPOTS: List<ScenicSpot> = listOf(

    /* ---------------------------------- 长沙 ---------------------------------- */

    ScenicSpot(
        spotId = "s001",
        city = "长沙",
        name = "岳麓山",
        tag = SpotTag.HOT,
        coverImg = scene(0),
        score = 4.7,
        reviewCount = 12580,
        shortDesc = "长沙名山，山顶可以俯瞰城市全景与湘江",
        imgUrls = listOf(scene(0), scene(4), scene(8)),
        ticketInfo = "免费入园；景区观光车 20 元/人，索道单程 30 元",
        freePolicy = "65 周岁以上老人、1.3 米以下儿童免观光车票（凭有效证件）",
        ticketTip = "节假日建议官方公众号提前预约门票与观光车，现场窗口排队较长",
        duration = "3 小时",
        bestSeason = "春秋两季；夏季清晨登山人少凉爽",
        traffic = "地铁 4 号线到湖南大学站，步行 10 分钟到东方红广场上山",
        pitTips = listOf(
            "节假日索道 / 观光车排队 2 小时以上，不想排队建议步行上山",
            "不要在山脚路边买高价特产与香烛，正规商店在景区商业街内",
            "爱晚亭、橘子洲焰火日人流最大，错峰建议工作日前往",
        ),
        userReviews = listOf(
            review("游客1", 4.8, "风景很好，人很多，早点去空气特别好"),
            review("游客2", 4.5, "免费景点很划算，下山小吃很多，记得留肚子"),
            review("游客3", 4.6, "坐观光车上山步行下山，节奏刚好不累"),
        ),
        lat = 28.1857,
        lng = 112.9394,
    ),
    ScenicSpot(
        spotId = "s002",
        city = "长沙",
        name = "橘子洲",
        tag = SpotTag.HOT,
        coverImg = scene(1),
        score = 4.6,
        reviewCount = 9864,
        shortDesc = "湘江中心的长岛，青年毛泽东艺术雕像所在地",
        imgUrls = listOf(scene(1), scene(5), scene(9)),
        ticketInfo = "免费入园；洲内小火车 40 元/人（往返）",
        freePolicy = "1.3 米以下儿童免小火车票",
        ticketTip = "周六晚焰火晚会需提前预约，限流时段无预约不能登洲",
        duration = "2.5 小时",
        bestSeason = "春秋两季；夏季注意防晒，洲上遮阴较少",
        traffic = "地铁 2 号线橘子洲站直达，不建议自驾，节假日交通管制",
        pitTips = listOf(
            "焰火日散场地铁排队 1 小时起，可步行至溁湾镇站分流",
            "洲头到洲尾距离 5 公里，体力一般建议买小火车票",
            "江边风大，傍晚乘船注意加衣",
        ),
        userReviews = listOf(
            review("阿橙", 4.7, "焰火非常震撼，提前两小时占位才看得清"),
            review("旅行者", 4.4, "小火车有点贵但省腿，雕像比想象中壮观"),
        ),
        lat = 28.1901,
        lng = 112.9687,
    ),
    ScenicSpot(
        spotId = "s003",
        city = "长沙",
        name = "谢子龙影像艺术馆",
        tag = SpotTag.NICHE,
        coverImg = scene(2),
        score = 4.5,
        reviewCount = 2136,
        shortDesc = "洋湖边的白色建筑，出片率极高的影像艺术展馆",
        imgUrls = listOf(scene(2), scene(6), scene(10)),
        ticketInfo = "常设展免费；特展 60-120 元不等",
        freePolicy = "1.2 米以下儿童免票，特展不免",
        ticketTip = "周一闭馆，特展票建议官方小程序提前购买并预约时段",
        duration = "2 小时",
        bestSeason = "四季皆宜，室内展馆不受天气影响",
        traffic = "地铁 3 号线洋湖湿地站，步行约 12 分钟；建议打车到正门",
        pitTips = listOf(
            "馆内多数展厅禁止拍照或禁止闪光灯，注意标识",
            "节假日拍照需排队取号，保安会帮忙拍合影",
            "特展单独检票，常设展免费区内容也不少",
        ),
        userReviews = listOf(
            review("Luna", 4.6, "建筑本身就是艺术品，光影楼梯拍了两小时"),
            review("风信子", 4.3, "特展质量参差，去前先看当期主题评价"),
        ),
        lat = 28.1363,
        lng = 112.9425,
    ),

    /* ---------------------------------- 杭州 ---------------------------------- */

    ScenicSpot(
        spotId = "s004",
        city = "杭州",
        name = "西湖",
        tag = SpotTag.HOT,
        coverImg = scene(3),
        score = 4.8,
        reviewCount = 23870,
        shortDesc = "三面云山一面城，断桥残雪、苏堤春晓的经典湖光",
        imgUrls = listOf(scene(3), scene(7), scene(11)),
        ticketInfo = "免费开放；环湖游船 55-70 元/人",
        freePolicy = "湖区全天免费开放，湖中三岛（小瀛洲等）需购船票",
        ticketTip = "节假日游船排队久，建议早上 9 点前到码头",
        duration = "半天至 1 天",
        bestSeason = "春季 3-4 月、中秋前后；夏季可赏荷",
        traffic = "地铁 1 号线龙翔桥站步行 5 分钟到湖滨；环湖有公交与观光车",
        pitTips = listOf(
            "周末断桥人流密度极高，想安静游览建议走杨公堤一侧",
            "不要坐野导推荐的「西湖一日游」黑车，正规游船在官方码头",
            "节假日景区限行且车位紧张，公共交通最稳妥",
        ),
        userReviews = listOf(
            review("江南客", 4.9, "晴西湖不如雨西湖，随手拍都是水墨画"),
            review("小满", 4.6, "环湖骑行太舒服，就是节假日人从众"),
            review("向北", 4.7, "免费的顶级景区，杭州诚意拉满"),
        ),
        lat = 30.2461,
        lng = 120.1487,
    ),
    ScenicSpot(
        spotId = "s005",
        city = "杭州",
        name = "灵隐寺",
        tag = SpotTag.HOT,
        coverImg = scene(4),
        score = 4.6,
        reviewCount = 15203,
        shortDesc = "飞来峰下千年古刹，香火鼎盛的禅宗名寺",
        imgUrls = listOf(scene(4), scene(8), scene(0)),
        ticketInfo = "飞来峰景区 45 元；灵隐寺香花券 30 元（需先购飞来峰票）",
        freePolicy = "1.2 米以下儿童免票；持皈依证免灵隐寺香花券",
        ticketTip = "门票需在「杭州灵隐飞来峰」公众号提前预约，现场限流",
        duration = "3 小时",
        bestSeason = "春秋两季；雨天云雾缭绕另有氛围",
        traffic = "地铁 + 公交 7 路 / 灵隐专线；景区拥堵，不建议自驾",
        pitTips = listOf(
            "寺内免费赠三炷香，不要在门口买高价香",
            "飞来峰门票和灵隐寺门票是两笔，别只买一个",
            "初一十五与佛诞日人极多，尽量避开",
        ),
        userReviews = listOf(
            review("听雨", 4.7, "飞来峰造像值得细看，古寺氛围很好"),
            review("momo", 4.4, "人太多影响感受，建议开门就进"),
        ),
        lat = 30.2410,
        lng = 120.1007,
    ),
    ScenicSpot(
        spotId = "s006",
        city = "杭州",
        name = "杨梅岭村",
        tag = SpotTag.NICHE,
        coverImg = scene(5),
        score = 4.5,
        reviewCount = 1487,
        shortDesc = "龙井茶园深处的小村落，满觉陇上游的安静茶山",
        imgUrls = listOf(scene(5), scene(9), scene(1)),
        ticketInfo = "免费开放",
        freePolicy = "全村公共区域免费，个别茶室与茶园体验单独收费",
        ticketTip = "明前茶季建议工作日前往，周末村内道路易堵",
        duration = "2 小时",
        bestSeason = "春季采茶季（3 月下旬-4 月）、秋季桂花香时",
        traffic = "公交 87 路杨梅岭站；建议从龙井村沿步道步行翻山过来",
        pitTips = listOf(
            "村口拉客的「茶农直销」谨慎购买，认准正规茶社",
            "民宿与茶室价格差异大，先看菜单再落座",
            "山间小路傍晚无路灯，天黑前下山",
        ),
        userReviews = listOf(
            review("山月", 4.6, "比龙井村安静太多，喝茶看茶山很治愈"),
            review("豆豆", 4.4, "交通略不方便，适合专程慢逛半天"),
        ),
        lat = 30.2097,
        lng = 120.1069,
    ),

    /* ---------------------------------- 重庆 ---------------------------------- */

    ScenicSpot(
        spotId = "s007",
        city = "重庆",
        name = "洪崖洞",
        tag = SpotTag.HOT,
        coverImg = scene(6),
        score = 4.4,
        reviewCount = 30125,
        shortDesc = "11 层吊脚楼依山而建，夜景像走进《千与千寻》",
        imgUrls = listOf(scene(6), scene(10), scene(2)),
        ticketInfo = "免费开放；内部商铺与餐饮单独消费",
        freePolicy = "全区域免费，夜间亮灯时段同样免费",
        ticketTip = "旺季实行单向通行与限流，关注官方通告的入口安排",
        duration = "2 小时",
        bestSeason = "春秋夜晚舒适；夏季建议 20 点后前往",
        traffic = "地铁 1 号线小什字站步行可达；千厮门大桥观景台视角最佳",
        pitTips = listOf(
            "节假日 18-21 点限流排队 1 小时起，建议 21 点后再去",
            "楼内多数是同质化小商品与高价餐饮，不建议正餐",
            "1 楼和 11 楼都是马路，导航「楼层」会骗人，先找出口标识",
        ),
        userReviews = listOf(
            review("火锅少女", 4.6, "夜景真的震撼，对面大桥拍全景必去"),
            review("阿凯", 4.1, "人挤人，商业化偏重，看外观即可"),
            review("向北", 4.5, "魔幻 8D 城市代表，记得带充电宝"),
        ),
        lat = 29.5630,
        lng = 106.5812,
    ),
    ScenicSpot(
        spotId = "s008",
        city = "重庆",
        name = "李子坝轻轨站",
        tag = SpotTag.HOT,
        coverImg = scene(7),
        score = 4.3,
        reviewCount = 11650,
        shortDesc = "轻轨穿楼而过的名场面，楼下专门观景平台",
        imgUrls = listOf(scene(7), scene(11), scene(3)),
        ticketInfo = "免费（乘坐轨道交通按里程计费，2 元起）",
        freePolicy = "观景平台免费开放",
        ticketTip = "无需专门购票进楼，李子坝站 A 口下楼即是观景平台",
        duration = "30 分钟",
        bestSeason = "四季皆宜，雨天注意观景平台湿滑",
        traffic = "轨道交通 2 号线李子坝站；列车约 5-8 分钟一班",
        pitTips = listOf(
            "穿楼是日常运营，不是表演，掐点等待意义不大",
            "观景平台人多注意财物，拍照不要倚靠轨道侧",
            "周边餐饮选择一般，顺路打卡即可不必专程",
        ),
        userReviews = listOf(
            review("8D 爱好者", 4.4, "亲眼看到还是觉得神奇，山城太会了"),
            review("青柠", 4.2, "五分钟拍完，期望值别拉太高"),
        ),
        lat = 29.5542,
        lng = 106.5360,
    ),
    ScenicSpot(
        spotId = "s009",
        city = "重庆",
        name = "山城步道",
        tag = SpotTag.NICHE,
        coverImg = scene(8),
        score = 4.6,
        reviewCount = 3421,
        shortDesc = "悬崖上的老重庆栈道，保留吊脚楼与江景烟火气",
        imgUrls = listOf(scene(8), scene(0), scene(4)),
        ticketInfo = "免费开放",
        freePolicy = "步道全程免费，途中展馆单独预约",
        ticketTip = "傍晚亮灯后最美，建议 17 点左右进入",
        duration = "2 小时",
        bestSeason = "春秋两季；夏季傍晚后前往",
        traffic = "地铁 1 号线较场口站步行至中兴路入口",
        pitTips = listOf(
            "步道上下台阶多，穿防滑好走的鞋",
            "巷内老茶馆价格亲民，比江景网红店划算",
            "部分路段较窄，旺季错身注意安全",
        ),
        userReviews = listOf(
            review("老城慢游", 4.7, "比洪崖洞更有老重庆味道，强烈推荐"),
            review("一只阿柴", 4.5, "看江景发呆一下午，消费也便宜"),
        ),
        lat = 29.5527,
        lng = 106.5700,
    ),

    /* ---------------------------------- 大理 ---------------------------------- */

    ScenicSpot(
        spotId = "s010",
        city = "大理",
        name = "洱海生态廊道",
        tag = SpotTag.HOT,
        coverImg = scene(9),
        score = 4.7,
        reviewCount = 8742,
        shortDesc = "沿洱海的亲水骑行绿道，苍山洱海一路相伴",
        imgUrls = listOf(scene(9), scene(1), scene(5)),
        ticketInfo = "免费开放；廊道内共享单车 / 观光车按时计费",
        freePolicy = "步行进入免费，社会车辆不得进入廊道",
        ticketTip = "租车请走官方扫码点位，私人租电动车纠纷多",
        duration = "3-5 小时",
        bestSeason = "11 月-次年 4 月，天高云淡能见度高",
        traffic = "大理古城乘 C2 路 / 打车到才村码头入口",
        pitTips = listOf(
            "不要相信路边「洱海一日游包车」低价团，多为购物团",
            "洱海风大紫外线强，骑行务必防晒",
            "海西看日落、海东看日出，别跑反方向",
        ),
        userReviews = listOf(
            review("环海选手", 4.8, "骑一下午车吹海风，最治愈的大理体验"),
            review("南风", 4.6, "免费景点天花板，水杉红的时候绝美"),
        ),
        lat = 25.7860,
        lng = 100.1560,
    ),
    ScenicSpot(
        spotId = "s011",
        city = "大理",
        name = "大理古城",
        tag = SpotTag.HOT,
        coverImg = scene(10),
        score = 4.4,
        reviewCount = 16980,
        shortDesc = "苍山脚下的南诏古城，洋人街与人民路的慢生活",
        imgUrls = listOf(scene(10), scene(2), scene(6)),
        ticketInfo = "免费开放（部分景点如古城墙、蒋公祠单独收费）",
        freePolicy = "古城公共街区免费，全天可进入",
        ticketTip = "三月街、火把节等节庆期间人流大，住宿提前订",
        duration = "半天",
        bestSeason = "春秋两季；雨季带伞，雨停即晴",
        traffic = "高铁站乘 8 路 / 三塔专线到古城；古城内步行游览",
        pitTips = listOf(
            "主街银器 / 玉石水很深，不懂行谨慎购买",
            "酒吧街先问清单价与最低消费再入座",
            "古城内拉客「环洱海低价拼车」多为购物套路",
        ),
        userReviews = listOf(
            review("段郎", 4.5, "清晨人少的人民路最有味道，适合住两晚"),
            review("橘子海", 4.3, "商业化但方便，作为落脚点很合适"),
        ),
        lat = 25.6939,
        lng = 100.1634,
    ),
    ScenicSpot(
        spotId = "s012",
        city = "大理",
        name = "喜洲古镇",
        tag = SpotTag.NICHE,
        coverImg = scene(11),
        score = 4.6,
        reviewCount = 5213,
        shortDesc = "白族民居与稻田环绕的古镇，喜洲粑粑与扎染体验",
        imgUrls = listOf(scene(11), scene(3), scene(7)),
        ticketInfo = "免费进入；严家大院等院落门票 20-30 元",
        freePolicy = "古镇公共区域免费",
        ticketTip = "稻田季（6-9 月）与油菜花期（2-3 月）最美",
        duration = "2-3 小时",
        bestSeason = "春秋两季；花期加倍出片",
        traffic = "古城乘中巴 / 网约车约 30 分钟；可与海舌公园串联",
        pitTips = listOf(
            "网红稻田咖啡旺季排队久且价格偏高",
            "扎染体验认准正规作坊，问清价格与时长",
            "路边拉客的「白族家访」多为银器推销",
        ),
        userReviews = listOf(
            review("麦田", 4.7, "转角楼和稻田太治愈，粑粑趁热吃"),
            review("云游", 4.5, "比大理古城安静，白族建筑保存得好"),
        ),
        lat = 25.8550,
        lng = 100.1240,
    ),

    /* ---------------------------------- 三亚 ---------------------------------- */

    ScenicSpot(
        spotId = "s013",
        city = "三亚",
        name = "蜈支洲岛",
        tag = SpotTag.HOT,
        coverImg = scene(0),
        score = 4.5,
        reviewCount = 19045,
        shortDesc = "水质清澈的热带小岛，潜水与水上项目聚集地",
        imgUrls = listOf(scene(0), scene(5), scene(10)),
        ticketInfo = "门船票 144 元；水上项目套票 400-1500 元不等",
        freePolicy = "1.2 米以下儿童免门票（船票不免）",
        ticketTip = "务必官方渠道提前一天购票，旺季常售罄且现场排队久",
        duration = "1 天",
        bestSeason = "10 月-次年 4 月；台风季（7-9 月）可能停航",
        traffic = "市区打车 / 28 路到蜈支洲岛码头，乘船约 15 分钟上岛",
        pitTips = listOf(
            "岛上餐饮价格是市区 2-3 倍，可自带简餐和水",
            "潜水项目先确认是否含照片，二次收费项目很多",
            "停航日白跑风险高，出发前查船班与天气",
        ),
        userReviews = listOf(
            review("海岛控", 4.6, "海水能见度很高，潜水体验物有所值"),
            review("椰子", 4.2, "啥都贵，做好预算再来，景色确实没话说"),
        ),
        lat = 18.3106,
        lng = 109.7630,
    ),
    ScenicSpot(
        spotId = "s014",
        city = "三亚",
        name = "亚龙湾热带天堂森林公园",
        tag = SpotTag.HOT,
        coverImg = scene(1),
        score = 4.5,
        reviewCount = 9320,
        shortDesc = "《非诚勿扰》取景地，山顶俯瞰亚龙湾月牙形海岸",
        imgUrls = listOf(scene(1), scene(6), scene(11)),
        ticketInfo = "门票 + 观光车 158 元；索桥 / 玻璃栈道另收费",
        freePolicy = "1.2 米以下儿童免门票，观光车不免",
        ticketTip = "网上套票比窗口便宜，玻璃栈道项目现场常需排队",
        duration = "4 小时",
        bestSeason = "10 月-次年 4 月；山区午后多阵雨带伞",
        traffic = "亚龙湾区域打车 15 分钟；公交 15 路 / 25 路可达",
        pitTips = listOf(
            "园区很大全程靠观光车，穿拖鞋不适合",
            "山顶餐饮性价比一般，建议简餐解决",
            "索桥有人流上限，旺季排队 40 分钟起",
        ),
        userReviews = listOf(
            review("雨林", 4.6, "过江龙索桥很出片，山顶海景一绝"),
            review("夏天", 4.4, "项目单独收费偏多，门票已不便宜"),
        ),
        lat = 18.2470,
        lng = 109.6430,
    ),
    ScenicSpot(
        spotId = "s015",
        city = "三亚",
        name = "西岛",
        tag = SpotTag.NICHE,
        coverImg = scene(2),
        score = 4.6,
        reviewCount = 4612,
        shortDesc = "仍有渔村生活气息的原生小岛，珊瑚老屋与小众海滩",
        imgUrls = listOf(scene(2), scene(7), scene(0)),
        ticketInfo = "门船票 98 元；岛上电瓶车 30 元",
        freePolicy = "1.2 米以下儿童免门票",
        ticketTip = "当天往返注意末班船时间（约 17:30，随季节调整）",
        duration = "半天至 1 天",
        bestSeason = "10 月-次年 4 月",
        traffic = "市区打车到肖旗港码头，乘船约 15 分钟",
        pitTips = listOf(
            "岛上民宿条件参差，住岛务必看真实评价",
            "牛王岭路段日晒强，租电瓶车更轻松",
            "赶海注意潮汐时间，不要带走珊瑚与贝壳",
        ),
        userReviews = listOf(
            review("慢游", 4.7, "比蜈支洲安静便宜，渔村烟火气难得"),
            review("阿树", 4.5, "海水同样清澈，适合避开人群"),
        ),
        lat = 18.2370,
        lng = 109.3770,
    ),

    /* ---------------------------------- 成都 ---------------------------------- */

    ScenicSpot(
        spotId = "s016",
        city = "成都",
        name = "成都大熊猫繁育研究基地",
        tag = SpotTag.HOT,
        coverImg = scene(3),
        score = 4.7,
        reviewCount = 27640,
        shortDesc = "看花花等明星熊猫的最佳地点，幼年熊猫别墅最活跃",
        imgUrls = listOf(scene(3), scene(8), scene(1)),
        ticketInfo = "55 元/人",
        freePolicy = "6 周岁以下或 1.3 米以下儿童免票；60 岁以上免票",
        ticketTip = "必须官方公众号实名预约，旺季门票提前 7 天放票即抢光",
        duration = "3-4 小时",
        bestSeason = "四季可看；上午 8-10 点熊猫最活跃，中午多在睡觉",
        traffic = "地铁 3 号线熊猫大道站换乘景区直通车；不建议自驾",
        pitTips = listOf(
            "开园即入直奔幼年熊猫别墅，晚了只能看「糯米团子睡觉」",
            "园区很大，南门进人少，观光车旺季排队 1 小时",
            "不要拍打玻璃、不要投喂，闪光灯全程关闭",
        ),
        userReviews = listOf(
            review("花花粉丝", 4.8, "七点半排队值了，看到吃播一小时"),
            review("巴适", 4.6, "免费看顶流，成都最良心景点之一"),
        ),
        lat = 30.7383,
        lng = 104.1458,
    ),
    ScenicSpot(
        spotId = "s017",
        city = "成都",
        name = "宽窄巷子",
        tag = SpotTag.HOT,
        coverImg = scene(4),
        score = 4.2,
        reviewCount = 21035,
        shortDesc = "宽巷子、窄巷子、井巷子平行的清代街区",
        imgUrls = listOf(scene(4), scene(9), scene(2)),
        ticketInfo = "免费开放",
        freePolicy = "街区免费，内部茶馆 / 演出 / 院落单独消费",
        ticketTip = "节假日人流极大，逛吃建议工作日或傍晚",
        duration = "2 小时",
        bestSeason = "春秋两季；夏天注意防暑，街巷遮阴有限",
        traffic = "地铁 4 号线宽窄巷子站直达",
        pitTips = listOf(
            "三大炮、糖油果子等小吃溢价明显，浅尝即可",
            "采耳先问价，拍照合影可能被收费",
            "想安静喝茶可去周边社区茶馆，价格便宜一半",
        ),
        userReviews = listOf(
            review("蓉城", 4.3, "第一次来成都还是值得走一圈"),
            review("老饕", 4.0, "本地人不去，游客氛围浓，做好预期"),
        ),
        lat = 30.6698,
        lng = 104.0597,
    ),
    ScenicSpot(
        spotId = "s018",
        city = "成都",
        name = "东郊记忆",
        tag = SpotTag.NICHE,
        coverImg = scene(5),
        score = 4.4,
        reviewCount = 6280,
        shortDesc = "老工业区改造的文创园区，音乐现场与复古工业风",
        imgUrls = listOf(scene(5), scene(10), scene(3)),
        ticketInfo = "免费开放（展览 / Livehouse 演出单独购票）",
        freePolicy = "园区公共区域免费",
        ticketTip = "演出信息关注「东郊记忆」官方号，热门场提前购票",
        duration = "2-3 小时",
        bestSeason = "四季皆宜，夜间氛围更好",
        traffic = "地铁 8 号线东郊记忆站步行 5 分钟",
        pitTips = listOf(
            "白天店铺开门较晚，建议 14 点后前往",
            "周末常有市集，注意当日活动人流",
            "网红咖啡店出品参差，先看评价再消费",
        ),
        userReviews = listOf(
            review("黑胶", 4.5, "工业风拍照圣地，晚上的 Livehouse 很惊喜"),
            review("阿哲", 4.3, "免费逛展无压力，顺路可安排半天"),
        ),
        lat = 30.6653,
        lng = 104.1266,
    ),

    /* ---------------------------------- 西安 ---------------------------------- */

    ScenicSpot(
        spotId = "s019",
        city = "西安",
        name = "大唐不夜城",
        tag = SpotTag.HOT,
        coverImg = scene(6),
        score = 4.6,
        reviewCount = 24510,
        shortDesc = "盛唐主题步行街，不倒翁小姐姐与灯火璀璨的夜景",
        imgUrls = listOf(scene(6), scene(0), scene(4)),
        ticketInfo = "免费开放",
        freePolicy = "街区免费，街头演出均为免费场次",
        ticketTip = "节假日步行街区实行限流与单向通行，建议地铁往返",
        duration = "2-3 小时",
        bestSeason = "春秋夜晚；夏季 20 点后出门，冬季注意保暖",
        traffic = "地铁 4 号线大唐芙蓉园站；周边节假日停车极难",
        pitTips = listOf(
            "不倒翁等热门表演提前 40 分钟占位，否则只能看人头",
            "汉服租赁一条街价格水分大，先看妆造样片与总价",
            "周末 21 点散场地铁排队久，可错峰或步行一站",
        ),
        userReviews = listOf(
            review("长安", 4.7, "免费街区做出景区质感，灯光太震撼"),
            review("布丁", 4.5, "人从众但值得，节目单提前查好"),
        ),
        lat = 34.2184,
        lng = 108.9592,
    ),
    ScenicSpot(
        spotId = "s020",
        city = "西安",
        name = "西安城墙",
        tag = SpotTag.HOT,
        coverImg = scene(7),
        score = 4.7,
        reviewCount = 17860,
        shortDesc = "中国现存最完整的古城墙之一，骑行一圈看尽长安",
        imgUrls = listOf(scene(7), scene(1), scene(5)),
        ticketInfo = "门票 54 元；城上自行车租赁 45 元/3 小时",
        freePolicy = "1.2 米以下儿童免门票",
        ticketTip = "建议傍晚登城，白天骑行太晒，夜场有灯光",
        duration = "2-3 小时（骑行一圈约 13.7 公里）",
        bestSeason = "春秋两季；夏季务必傍晚或夜场",
        traffic = "地铁 2 号线永宁门站直达南门登城口",
        pitTips = listOf(
            "步行一圈不现实，建议骑车或只走南段",
            "城上无遮挡，防晒和水要备足",
            "自行车押金与计费规则租前确认，还车点不止一个",
        ),
        userReviews = listOf(
            review("骑行客", 4.8, "城墙上骑车看夕阳，独一无二的体验"),
            review("唐风", 4.6, "54 元玩两小时，性价比很高"),
        ),
        lat = 34.2555,
        lng = 108.9466,
    ),
    ScenicSpot(
        spotId = "s021",
        city = "西安",
        name = "广仁寺",
        tag = SpotTag.NICHE,
        coverImg = scene(8),
        score = 4.5,
        reviewCount = 2034,
        shortDesc = "城墙西北角的藏传佛教寺院，红墙金顶安静出片",
        imgUrls = listOf(scene(8), scene(2), scene(6)),
        ticketInfo = "免费开放（凭身份证入内）",
        freePolicy = "免门票，逢法会日同样免费",
        ticketTip = "燃灯节（农历十月左右）氛围最佳但人流集中",
        duration = "1 小时",
        bestSeason = "春秋两季；雪后的红墙极美",
        traffic = "地铁 1 号线玉祥门站步行约 10 分钟，可与城墙西北角串联",
        pitTips = listOf(
            "寺院清净，拍照保持安静、勿对佛像与信众特写",
            "周边网红咖啡旺季排队，错峰前往",
            "殿内禁止拍照，注意提示",
        ),
        userReviews = listOf(
            review("红墙", 4.6, "人少景美，红墙转经筒拍出来不像在西安"),
            review("慢行", 4.4, "半小时逛完，适合和城墙安排在一起"),
        ),
        lat = 34.2690,
        lng = 108.9350,
    ),

    /* ---------------------------------- 厦门 ---------------------------------- */

    ScenicSpot(
        spotId = "s022",
        city = "厦门",
        name = "鼓浪屿",
        tag = SpotTag.HOT,
        coverImg = scene(9),
        score = 4.5,
        reviewCount = 28790,
        shortDesc = "万国建筑与钢琴之岛，步行漫游的海上花园",
        imgUrls = listOf(scene(9), scene(3), scene(7)),
        ticketInfo = "船票 35 元（往返，市民与游客码头不同）；核心五景联票 90 元",
        freePolicy = "1.2 米以下儿童免船票",
        ticketTip = "船票务必官方公众号提前购买，旺季当天无票",
        duration = "1 天",
        bestSeason = "春秋两季；夏季注意台风与阵雨",
        traffic = "邮轮中心厦鼓码头乘船，约 20 分钟到三丘田 / 内厝澳码头",
        pitTips = listOf(
            "岛上禁止机动车与自行车，全程步行，穿好走的鞋",
            "不要跟野导走「一日游」，小巷自己逛才有惊喜",
            "海鲜排档先问价看秤，网红店排队前先看评价",
        ),
        userReviews = listOf(
            review("琴声", 4.6, "清晨的岛最美，住一晚才能避开人流"),
            review("海风", 4.4, "人多且热，但建筑细节真的耐看"),
        ),
        lat = 24.4492,
        lng = 118.0667,
    ),
    ScenicSpot(
        spotId = "s023",
        city = "厦门",
        name = "沙坡尾",
        tag = SpotTag.NICHE,
        coverImg = scene(10),
        score = 4.5,
        reviewCount = 7650,
        shortDesc = "老厦门避风港改造的文艺街区，渔船与咖啡屋并存",
        imgUrls = listOf(scene(10), scene(4), scene(8)),
        ticketInfo = "免费开放（艺术西区展览单独收费）",
        freePolicy = "街区与避风坞免费",
        ticketTip = "傍晚落日时分最美，周末有创意市集",
        duration = "2 小时",
        bestSeason = "春秋两季；台风天文大潮可能淹没栈道，注意提示",
        traffic = "公交到沙坡尾站 / 世茂海峡大厦站；地铁 1 号线镇海路站步行 15 分钟",
        pitTips = listOf(
            "避风坞边护栏较矮，拍照注意脚下",
            "网红店多为打卡型，正餐建议选巷内老店",
            "市集商品价格偏高，量力消费",
        ),
        userReviews = listOf(
            review("渔港", 4.6, "渔船和艺术感混搭，厦门最有味道的角落"),
            review("阿盐", 4.4, "两小时足够，可与厦大、南普陀串联"),
        ),
        lat = 24.4373,
        lng = 118.0862,
    ),
)
