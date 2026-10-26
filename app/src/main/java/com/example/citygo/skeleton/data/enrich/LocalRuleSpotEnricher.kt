package com.example.citygo.skeleton.data.enrich

import com.example.citygo.skeleton.data.model.ScenicSpot

/* =====================================================================================
 * 本地规则版景点文案补充器（默认实现）
 *
 * 依据景点名称 / 简介中的关键词（山、公园、老街、夜市、寺庙、博物馆、湖海、古镇等）
 * 套确定性模板，补齐：门票政策、购票提示、建议时长、最佳季节、交通、避坑要点。
 * 未来接入 RAG 大模型后，本类继续作为「模型失败 / 离线」时的兜底，不删除。
 * ===================================================================================== */

class LocalRuleSpotEnricher : SpotContentEnricher {

    override suspend fun enrich(spot: ScenicSpot): ScenicSpot {
        val text = spot.name + spot.shortDesc
        return spot.copy(
            ticketInfo = spot.ticketInfo.ifBlank { defaultTicket(text) },
            freePolicy = spot.freePolicy.ifBlank { defaultFreePolicy(text) },
            ticketTip = spot.ticketTip.ifBlank { TICKET_TIP },
            duration = spot.duration.ifBlank { pickDuration(text) },
            bestSeason = spot.bestSeason.ifBlank { pickBestSeason(text) },
            traffic = spot.traffic.ifBlank { defaultTraffic(spot.name) },
            pitTips = spot.pitTips.ifEmpty { buildPitTips(text) },
        )
    }

    /** 票价文案：免费街区给免费，其余统一提示以官方公示为准（不编造价格） */
    private fun defaultTicket(text: String): String =
        if (FREE_KEYWORDS.any { text.contains(it) }) "免费开放" else "票价以景区官方公示为准"

    private fun defaultFreePolicy(text: String): String =
        if (FREE_KEYWORDS.any { text.contains(it) }) {
            "街区 / 园区免费开放，内部小景点、体验项目可能单独收费"
        } else {
            "老人、儿童、学生等群体按景区政策享受优惠，具体以现场公示为准"
        }

    /** 建议游玩时长：按点位类型给经验区间 */
    private fun pickDuration(text: String): String = when {
        text.contains("山") || text.contains("岭") || text.contains("峰") ->
            "3-5 小时，建议预留半天"
        text.contains("博物馆") || text.contains("纪念馆") || text.contains("寺") ||
            text.contains("庙") || text.contains("故居") || text.contains("遗址") ->
            "1-2 小时"
        text.any { it in STREET_CHARS } || text.contains("夜市") || text.contains("市集") ||
            text.contains("集市") || text.contains("美食街") ->
            "2-3 小时"
        text.contains("公园") || text.contains("湿地") || text.contains("广场") ||
            text.contains("绿道") || text.contains("植物园") || text.contains("花园") ->
            "1-2 小时"
        text.contains("湖") || text.contains("海") || text.contains("沙滩") || text.contains("岛") ->
            "2-4 小时"
        else -> "2-3 小时"
    }

    /** 最佳游玩季节 */
    private fun pickBestSeason(text: String): String = when {
        text.contains("山") || text.contains("岭") || text.contains("峰") ->
            "春秋两季最佳，夏季注意防暑、山中天气多变"
        text.contains("湿地") || text.contains("花") || text.contains("植物园") ->
            "春秋两季花开成片，雨天注意栈道湿滑"
        text.any { it in STREET_CHARS } || text.contains("夜市") ->
            "全年皆宜，傍晚至夜间氛围最好"
        else -> "春秋两季气候适宜，出行前关注天气预报"
    }

    /** 通用交通提示（导航搜索景点名即可，坐标已同步地图页可直接导航） */
    private fun defaultTraffic(name: String): String =
        "可乘地铁 / 公交到「$name」附近站点，或在地图页直接导航前往；" +
            "周末节假日周边易拥堵，建议优先公共交通出行。"

    /** 避坑要点：3 条通用 + 按类型追加 1 条，去重后保留 4 条以内 */
    private fun buildPitTips(text: String): List<String> {
        val tips = mutableListOf(
            "旺季 / 周末人流量大，建议开园即入或错峰前往，热门机位拍照需要排队",
            "警惕门口「低价票 / 免费带路」拉客套路，门票务必走官方渠道",
            "景区内餐饮、纪念品溢价明显，建议自带饮用水和小食",
        )
        val specific = when {
            text.contains("山") || text.contains("岭") || text.contains("峰") ->
                "山上天气多变，带好防晒、雨具，穿舒适运动鞋；索道、观光车末班时间提前确认"
            text.any { it in STREET_CHARS } || text.contains("夜市") ||
                text.contains("美食街") || text.contains("市集") ->
                "餐饮先看菜单是否明码标价再落座，人多处保管好手机等随身财物"
            text.contains("寺") || text.contains("庙") ->
                "殿内通常禁止拍照、请勿大声喧哗，香烛在正规法物流通处购买"
            text.contains("博物馆") || text.contains("纪念馆") ->
                "多数场馆免费但需提前在官方公众号预约，周一常闭馆，入馆带好身份证"
            text.contains("湖") || text.contains("海") || text.contains("沙滩") || text.contains("岛") ->
                "水边游玩注意安全，关注天气与潮汐预警，不要进入未开放的野泳区域"
            text.contains("古镇") || text.contains("古城") || text.contains("古村") ->
                "核心区商业化较重，往支巷走更有本地味道；车辆停官方停车场更稳妥"
            text.contains("公园") || text.contains("湿地") || text.contains("绿道") ->
                "园区较大、遮阴不均，做好防晒防蚊，部分区域禁止野餐与搭帐篷"
            else -> null
        }
        if (specific != null) tips.add(specific)
        return tips.distinct().take(4)
    }

    private companion object {
        /** 可直接判定免费开放的点位关键词 */
        val FREE_KEYWORDS = listOf(
            "步行街", "老街", "夜市", "美食街", "公园", "广场", "湿地", "绿道",
            "巷子", "古街", "滨江", "海滨步道",
        )

        /** 街 / 路 / 里 / 弄等街区类单字（用字符集合匹配，避免误命中常用词） */
        val STREET_CHARS = setOf('街', '巷', '里', '弄')

        const val TICKET_TIP =
            "节假日建议提前在官方公众号或正规平台预约购票，认准官方渠道，勿信门口黄牛"
    }
}

/* =====================================================================================
 * RAG 大模型文案补充器（预留骨架，暂未启用）
 *
 * 目标流程（POI 拿点位、大模型补文案）：
 * 1. 以 {城市、景点名、POI 类型、地址} 组装 prompt（RAG 可外挂本地攻略知识库）；
 * 2. 调用大模型生成：门票提示 / 建议时长 / 最佳季节 / 出行攻略 / 避坑要点；
 * 3. 解析模型返回的结构化 JSON，覆盖本地规则文案（用户评价类内容不做模型编造）；
 * 4. 请求失败 / 超时 / 返回非法时，降级回 [LocalRuleSpotEnricher]，保证页面不空。
 *
 * TODO（接入时实施）：
 * - 在 amap.properties 增加 LLM_BASE_URL / LLM_API_KEY，并注入 BuildConfig；
 * - 新建 Retrofit chat 接口与响应解析；
 * - 本类构造注入 api + LocalRuleSpotEnricher 作为 fallback；
 * - RepositoryContainer 装配处增加开关：有模型配置用本类，否则用本地规则。
 * ===================================================================================== */
class RemoteLlmSpotEnricher(
    /** 大模型不可用时的降级补充器 */
    private val fallback: SpotContentEnricher = LocalRuleSpotEnricher(),
) : SpotContentEnricher {

    /** 当前未接入大模型端点，直接降级本地规则；接入后替换为本方法内的远程调用 */
    override suspend fun enrich(spot: ScenicSpot): ScenicSpot = fallback.enrich(spot)
}
