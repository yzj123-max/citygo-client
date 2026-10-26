package com.example.citygo.skeleton.data.repository.ai

import android.util.Log
import com.example.citygo.skeleton.data.model.AiSpot
import com.example.citygo.skeleton.data.model.AiSpotSource
import com.example.citygo.skeleton.data.model.AiSpotType
import com.example.citygo.skeleton.data.model.AiTravelPlan
import com.example.citygo.skeleton.data.remote.AmapGeocoder
import com.example.citygo.skeleton.data.remote.GeocodeResultPoint
import com.example.citygo.skeleton.data.remote.StrictGeocodeResult

/* =====================================================================================
 * AI 攻略地点导入器 —— 后端原始文字攻略与出行计划页面【完全对齐】，一个点位都不许变。
 *
 * 严格解析规则（与后端约定，验收逐行核对）：
 *   1. 按 Markdown 标题「### 第X天」做天数分组（兼容 ## / # / 加粗 / Day N / D N），
 *      D1/D2/D3/D4 严格跟随原文天划分，前端不自动分配、不乱排；
 *   2. 每一天分组内，行首为「- 」（兼容「* 」）的条目，每一条 = 1 个独立点位卡片，
 *      不丢、不合并、不新增、不删减，顺序与原文完全一致；
 *   3. 点位后附属的配套字段（门票信息 / 人均 / 特色菜 / 时间段 / 拍照打卡点 / 出行要点 /
 *      来源 / 类型 / 宠物 …）全部解析并原样展示；「暂无相关信息」原样保留；
 *      时间段既支持「- 时间：上午9:00-12:00」字段行，也支持点名行内「午餐12:30」；
 *   4. 文末「预算核算 / 费用明细」整段忽略，绝不解析成点位（遇到该标题后停止解析，
 *      直到出现新的「第X天」标题才恢复）；
 *   5. 非字段、非点位的杂项「- 」行（如「交通：地铁2号线」「地址：…」）挂载到
 *      上一点位的出行要点补充信息，不会凭空生成点位（对齐"不能新增"）；
 *   6. 坐标逐个【串行】高德地理编码（city 必传）；查不到的点位使用城市中心兜底坐标，
 *      点位照样生成卡片与 Marker（卡片标「定位参考」），保证点位数量与原文一致。
 *
 * 全程在 ViewModel 的 viewModelScope 中执行（调用方保证），切页面 / 返回不取消。
 * ===================================================================================== */
class GuideSpotGeocodeImporter(
    private val geocoder: AmapGeocoder,
) {

    /** 从文本中解析出的单个地点块（尚未地理编码） */
    private data class RawBlock(
        var name: String,
        val day: Int?,
        var typeText: String? = null,
        var ticket: String? = null,
        var avgPrice: String? = null,
        var signatureDish: String? = null,
        var photoScene: String? = null,
        var travelTip: String? = null,
        var timeSlot: String? = null,
        var sourceText: String? = null,
        var petText: String? = null,
        var descText: String? = null,
        /** 杂项配套信息（交通 / 地址等），追加到出行要点后一起展示 */
        val extraTips: MutableList<String> = mutableListOf(),
        /** 名称行内【】标签里识别出的来源（如【本地库推荐】），优先级高于来源字段 */
        var inlineSource: AiSpotSource? = null,
        /** 名称行内【】标签里识别出的类型（如【餐饮】） */
        var inlineType: AiSpotType? = null,
        /** 名称里带的用餐时段前缀（午餐 / 晚餐…），写入简介辅助展示，同时暗示餐饮类型 */
        var mealLabel: String? = null,
    )

    /**
     * 导入结果。
     *
     * @property extractedNames 从攻略中解析出的【全部】点位名称（严格按原文顺序，含餐厅 / 商圈）
     * @property spots 点位列表（高德真实命中或兜底坐标），数量与 [extractedNames] 一一对应
     * @property failures 高德真实编码未命中的点位及原因（仅日志用；这些点用兜底坐标保留）
     * @property dayHeadersDetected 文本中是否出现过「### 第X天」等日标题
     */
    data class Result(
        val extractedNames: List<String>,
        val spots: List<AiSpot>,
        val failures: List<Pair<String, String>>,
        val dayHeadersDetected: Boolean,
    ) {
        /** 一个点位都没解析到（攻略里没有任何点位条目） */
        val nothingExtracted: Boolean get() = extractedNames.isEmpty()
    }

    /**
     * 执行导入。
     *
     * @param city 用户选择的目的地城市（如「西安」），地理编码必传，用于限定检索城市
     * @param markdown 后端返回的完整 Markdown 攻略原文
     */
    suspend fun import(city: String, markdown: String, nowMillis: Long): Result {
        // 步骤 1：全量解析点位块（严格日标题分组，每条「- 」都不丢）
        val blocks = parseBlocks(markdown)
        val dayHeadersDetected = blocks.any { it.day != null }
        val names = blocks.map { it.name }
        Log.i(
            TAG,
            "从攻略 Markdown 全量解析到点位 ${names.size} 个，城市=$city，识别到日标题=$dayHeadersDetected：$names",
        )
        blocks.forEachIndexed { idx, b ->
            Log.i(
                TAG,
                "点位#${idx + 1} D${b.day ?: "?"}「${b.name}」(${b.mealLabel ?: "—"})：类型=${b.inlineType ?: b.typeText ?: "—"}，" +
                    "来源=${b.inlineSource ?: b.sourceText ?: "—"}，时间=${b.timeSlot ?: "—"}，" +
                    "门票=${b.ticket ?: "—"}，人均=${b.avgPrice ?: "—"}，特色菜=${b.signatureDish ?: "—"}，" +
                    "拍照打卡=${b.photoScene ?: "—"}，出行要点=${b.travelTip ?: "—"}，" +
                    "补充配套=${if (b.extraTips.isEmpty()) "—" else b.extraTips.joinToString("；")}",
            )
        }

        val spots = mutableListOf<AiSpot>()
        val failures = mutableListOf<Pair<String, String>>()
        // 同名点位复用首次编码结果，仍各自保留为独立点位（顺序不变）
        val coordCache = HashMap<String, GeocodeResultPoint>()

        // 步骤 2：严格串行地理编码——for 循环内逐个 await，绝不同时发起多个请求（防高德限流）
        for (block in blocks) {
            val name = block.name
            val point = coordCache.getOrPut(name) { geocodeOne(name, city, failures) }
            spots += AiSpot(
                name = name,
                // 类型优先级：名称行【】类型标签 >「类型」字段 > 用餐时段（午餐/晚餐→餐饮）
                //           > 名称关键词兜底（泡馍/面馆→餐饮，商场/商圈→购物）
                type = block.inlineType
                    ?: mapType(block.typeText)
                    ?: (block.mealLabel?.let { AiSpotType.LOCAL_FOOD })
                    ?: inferType(name),
                desc = buildDesc(block),
                latitude = point.lat,
                longitude = point.lng,
                isPetFriendly = parsePetFriendly(block.petText),
                imageUrls = emptyList(),
                tip = "",
                day = block.day,
                // 后端配套信息原样透传（含「暂无相关信息」），null 表示文本无此字段
                ticket = block.ticket,
                avgPrice = block.avgPrice,
                signatureDish = block.signatureDish,
                photoScene = block.photoScene,
                travelTip = mergeTravelTip(block),
                timeSlot = block.timeSlot,
                source = block.inlineSource ?: AiSpotSource.fromText(block.sourceText),
                mockedCoord = point.mocked,
            )
        }

        Log.i(
            TAG,
            "点位同步完成：解析 ${names.size} 个，生成点位 ${spots.size} 个，" +
                "其中高德真实命中 ${spots.count { !it.mockedCoord }} 个、兜底坐标 " +
                "${spots.count { it.mockedCoord }} 个（均已保留，不丢弃）",
        )
        return Result(
            extractedNames = names,
            spots = spots,
            failures = failures,
            dayHeadersDetected = dayHeadersDetected,
        )
    }

    /** 成功点位组装成 [AiTravelPlan]；一个点位都没有时返回 null（上层走「无法匹配」提示） */
    fun toAiPlanOrNull(city: String, result: Result, nowMillis: Long): AiTravelPlan? =
        if (result.spots.isEmpty()) {
            null
        } else {
            AiTravelPlan(city = city, generateTime = nowMillis, spotList = result.spots)
        }

    /* ------------------------------ 单点地理编码（不丢点） ------------------------------ */

    /**
     * 单点串行地理编码：先用严格模式拿高德真实坐标；查不到 / 超时 / SDK 异常时
     * 退回 [AmapGeocoder.geocode] 的城市中心确定性兜底坐标，保证点位一定能落到地图。
     */
    private suspend fun geocodeOne(
        name: String,
        city: String,
        failures: MutableList<Pair<String, String>>,
    ): GeocodeResultPoint {
        val strict = geocoder.geocodeStrict(name = name, city = city)
        if (strict is StrictGeocodeResult.Success) {
            Log.i(TAG, "地点「$name」高德地理编码成功：lat=${strict.point.lat}, lng=${strict.point.lng}")
            return strict.point
        }
        val reason = when (strict) {
            is StrictGeocodeResult.Failure -> strict.reason
            StrictGeocodeResult.Timeout -> "地理编码超时"
            else -> "地理编码失败"
        }
        Log.w(TAG, "地点「$name」高德未命中（$reason），使用城市中心兜底坐标保留该点位")
        failures += name to reason
        // 非严格编码：内部会再试一次高德，仍失败则返回城市中心附近的确定性兜底坐标
        return geocoder.geocode(name = name, city = city)
    }

    /* ------------------------------ Markdown 全量解析 ------------------------------ */

    /**
     * 逐行扫描：
     * - 标题行（# 开头）/ 加粗短行：识别「第X天」更新当前天；识别「预算 / 费用」→ 进入忽略段；
     * - 「- 」条目按【内容】分流：命中已知字段名 → 挂到当前点位；其余 = 新独立点位；
     *   已知杂项字段（交通 / 地址等）→ 挂到当前点位的补充配套，不生成点位。
     */
    private fun parseBlocks(markdown: String): List<RawBlock> {
        val normalized = markdown.replace("\r\n", "\n").replace("\r", "\n")
        val blocks = mutableListOf<RawBlock>()
        var current: RawBlock? = null
        var currentDay: Int? = null
        var dayHeaderSeen = false
        // 文末预算核算段：命中后忽略后续全部条目，直到（理论上不会出现的）下一个日标题
        var ignoreSection = false

        for (rawLine in normalized.split("\n")) {
            if (rawLine.isBlank()) continue
            val line = rawLine.trimEnd()
            val trimmed = line.trim()

            // 1) Markdown 标题（# / ## / ### …）
            val headingLevel = if (trimmed.startsWith("#")) {
                trimmed.takeWhile { it == '#' }.length
            } else {
                0
            }
            if (headingLevel > 0) {
                val headingText = trimmed.drop(headingLevel).trim()
                // 预算 / 费用类标题：进入忽略段（其下条目全部不是点位）
                if (BUDGET_KEYWORDS.any { headingText.contains(it) }) {
                    ignoreSection = true
                    Log.i(TAG, "识别到预算/费用章节标题，忽略其下全部条目：$headingText")
                    continue
                }
                detectDayNumber(headingText)?.let { dayNum ->
                    currentDay = dayNum
                    dayHeaderSeen = true
                    ignoreSection = false
                    Log.i(TAG, "识别到日标题（heading）：第 $dayNum 天（原文=$trimmed）")
                }
                continue
            }

            // 2) 非标题行：「**预算核算**」这类加粗小节标题同样触发忽略
            val boldOnly = BOLD_ONLY_REGEX.matchEntire(trimmed)
            if (boldOnly != null && BUDGET_KEYWORDS.any { boldOnly.groupValues[1].contains(it) }) {
                ignoreSection = true
                Log.i(TAG, "识别到预算/费用加粗小节，忽略其下全部条目：${boldOnly.groupValues[1]}")
                continue
            }

            // 3) 普通文本行也兼容裸日标题（如「第一天」「Day 2」独占一行）
            if (!trimmed.startsWith("-") && !trimmed.startsWith("*")) {
                val dayNum = detectDayNumber(trimmed, strictTitle = true)
                if (dayNum != null) {
                    currentDay = dayNum
                    dayHeaderSeen = true
                    ignoreSection = false
                    Log.i(TAG, "识别到日标题（plain）：第 $dayNum 天（原文=$trimmed）")
                }
                continue
            }

            if (ignoreSection) continue // 预算段内的所有列表条目一律忽略

            val bullet = BULLET_LINE_REGEX.matchEntire(line) ?: continue
            val content = bullet.groupValues[1].trim()

            // 4) 先按字段名判定：「时间：/门票：/人均：…」是上一点位的配套属性
            val fieldPair = matchField(content)
            if (fieldPair != null) {
                val block = current
                if (block != null) {
                    assignField(block, fieldPair.first, fieldPair.second)
                } else {
                    Log.w(TAG, "字段行「$content」出现在任何点位之前，已忽略（不影响点位解析）")
                }
                continue
            }

            // 5) 其余每一条「- 」都是独立点位（景点 / 餐厅 / 商圈 / 酒店一律保留）。
            //    例外：纯日程行（整条只有「午餐12:30」「上午9:00-12:00」，没有点名）
            //    属于上一点位的【时间段】，挂到上一点位，不生成"午餐"这种假点位。
            val parsed = parsePointLine(content)
            val scheduleOnly = parsed.time != null &&
                (parsed.name.isBlank() || parsed.name in MEAL_WORDS)
            if (scheduleOnly) {
                val block = current
                if (block != null) {
                    if (block.timeSlot == null) block.timeSlot = parsed.time
                    Log.i(TAG, "纯日程行「$content」挂到上一点位「${block.name}」的时间段")
                } else {
                    Log.w(TAG, "日程行「$content」出现在任何点位之前，已忽略")
                }
                continue
            }
            val block = RawBlock(
                name = parsed.name,
                day = currentDay,
                inlineSource = parsed.source,
                inlineType = parsed.type,
                mealLabel = parsed.meal,
            )
            // 点名行内自带的时间段（午餐12:30 / 上午9:00-12:00）
            parsed.time?.let { block.timeSlot = it }
            blocks += block
            current = block
        }
        Log.i(
            TAG,
            "全量解析结束：共 ${blocks.size} 个点位，出现日标题=$dayHeaderSeen，" +
                "预算段忽略=${if (ignoreSection) "是" else "否（未出现预算章节）"}",
        )
        return blocks
    }

    /**
     * 识别标题文字里的「第X天」：返回天序号（1..N）。
     * @param strictTitle true 时（非 Markdown 标题的裸行）要求除「第X天」外只有少量标题后缀，
     *   避免把正文句子里的「第一天去兵马俑」误判成天标题；Markdown # 标题不做此限制。
     */
    private fun detectDayNumber(text: String, strictTitle: Boolean = false): Int? {
        val t = text.trim().trim('*', ' ', '　', '-', '：', ':')
        DAY_CN_REGEX.find(t)?.let { m ->
            val numText = m.groupValues[1]
            val n = numText.toIntOrNull() ?: chineseToInt(numText)
            if (n == null || n !in 1..14) return null
            if (strictTitle) {
                val rest = t.replace(m.value, "").trim()
                val looksLikeTitle = rest.isBlank() ||
                    rest.matches(Regex("""[\s:：、\-—.。（）()行程安排路线规划攻略概览站点主题A-Za-z0-9]*"""))
                if (!looksLikeTitle) return null
            }
            return n
        }
        // Day 2 / DAY3 / D2（短标题形态）
        if (t.length <= 12) {
            DAY_EN_REGEX.matchEntire(t)?.let { m ->
                val n = (m.groupValues[1].ifBlank { m.groupValues[2] }).toIntOrNull()
                if (n != null && n in 1..14) return n
            }
        }
        return null
    }

    /** 点位行解析结果 */
    private data class PointLine(
        val name: String,
        val source: AiSpotSource?,
        val type: AiSpotType?,
        val meal: String?,
        val time: String?,
    )

    /**
     * 解析点位条目，如：
     *   「上海科技馆【本地库推荐】」
     *   「午餐：同盛祥【AI生成推荐】」
     *   「老米家泡馍【AI生成推荐】午餐12:30」
     * 关键：用餐时段前缀（午餐 / 晚餐…）与行内时间必须从点名中剥离，保留【点位全名】。
     */
    private fun parsePointLine(rawContent: String): PointLine {
        var source: AiSpotSource? = null
        var type: AiSpotType? = null
        var meal: String? = null
        var residual = rawContent.trim()

        // 任务清单前缀
        residual = TASK_LIST_REGEX.replace(residual, "").trim()
        // 图片 / 链接标记
        residual = residual.replace(IMAGE_REGEX, "").replace(LINK_REGEX) { it.groupValues[1] }

        // 提取全部【…】：来源 / 类型标签识别后剔除；【午餐】等用餐标签记录后剔除
        for (m in BRACKET_REGEX.findAll(rawContent)) {
            val tag = m.groupValues[1].trim()
            when {
                LOCAL_TAG_KEYWORDS.any { tag.contains(it) } -> source = AiSpotSource.LOCAL_DB
                AI_TAG_KEYWORDS.any { tag.contains(it, ignoreCase = true) } ->
                    source = AiSpotSource.AI_GENERATED
                MEAL_WORDS.any { tag == it || tag.startsWith(it) } -> meal = tag
                else -> mapType(tag)?.let { type = it }
            }
            residual = residual.replace(m.value, "")
        }

        // 行内时间段：「午餐12:30」「上午9:00-12:00」（可能带括号 / 空格），从点名剥离
        var inlineTime: String? = null
        INLINE_TIME_REGEX.find(residual)?.let { m ->
            inlineTime = m.groupValues[1].replace(Regex("\\s+"), "").trim()
            residual = residual.removeRange(m.range).trim()
            // 清理时间残留的空括号
            residual = residual.replace(EMPTY_PAREN_REGEX, "").trim()
            // 时间段里的用餐词补记 meal（用于餐饮类型判定 / 简介）
            MEAL_WORDS.firstOrNull { inlineTime.orEmpty().startsWith(it) }?.let { meal = meal ?: it }
        }

        // 「午餐：餐厅全名」「晚餐-餐厅名」前缀剥离（冒号 / 破折号 / 空格分隔都兼容）
        MEAL_PREFIX_REGEX.find(residual)?.let { m ->
            meal = meal ?: m.groupValues[1]
            residual = m.groupValues[2].trim()
        }

        val name = cleanPointName(residual) ?: residual.let { fallback ->
            // 最后兜底：即便清洗不出合规名称，也用去装饰后的原文当点名，绝不丢点位
            fallback.replace(Regex("""[*`#>]"""), "").trim().ifBlank { rawContent.trim() }
        }
        return PointLine(name = name, source = source, type = type, meal = meal, time = inlineTime)
    }

    /**
     * 判断一条「- 」内容是不是字段行：形如「key：value」且 key 命中已知字段名 / 杂项配套名时
     * 返回 (key, value)，否则返回 null（即应作为独立点位）。
     */
    private fun matchField(content: String): Pair<String, String>? {
        val m = FIELD_SPLIT_REGEX.matchEntire(content) ?: return null
        val label = m.groupValues[1].replace(FIELD_LABEL_NOISE, "").trim()
        val value = cleanFieldValue(m.groupValues[2]) ?: return null
        return if (ALL_FIELD_PREFIXES.any { label.startsWith(it) }) label to value else null
    }

    /** 把字段行按字段名写入 [block]；同名字段首次出现为准，值原样保留（含「暂无相关信息」） */
    private fun assignField(block: RawBlock, label: String, value: String) {
        when {
            DISH_LABELS.any { label.startsWith(it) } ->
                if (block.signatureDish == null) block.signatureDish = value
            AVG_LABELS.any { label.startsWith(it) } ->
                if (block.avgPrice == null) block.avgPrice = value
            TICKET_LABELS.any { label.startsWith(it) } ->
                if (block.ticket == null) block.ticket = value
            TIME_LABELS.any { label.startsWith(it) } ->
                if (block.timeSlot == null) block.timeSlot = value
            PHOTO_LABELS.any { label.startsWith(it) } ->
                if (block.photoScene == null) block.photoScene = value
            TRAVEL_TIP_LABELS.any { label.startsWith(it) } ->
                if (block.travelTip == null) block.travelTip = value
            TYPE_LABELS.any { label.startsWith(it) } ->
                if (block.typeText == null) block.typeText = value
            SOURCE_LABELS.any { label.startsWith(it) } ->
                if (block.sourceText == null) block.sourceText = value
            PET_LABELS.any { label.startsWith(it) } -> block.petText = value
            DESC_LABELS.any { label.startsWith(it) } ->
                if (block.descText == null) block.descText = value
            // 杂项配套（交通 / 地址 / 停车 …）：追加到补充信息，不生成点位
            EXTRA_INFO_LABELS.any { label.startsWith(it) } ->
                block.extraTips += "$label：$value"
        }
    }

    /** 简介：优先用文本里的简介字段；否则用餐时段点位补一句客观前缀，不含任何编造信息 */
    private fun buildDesc(block: RawBlock): String {
        block.descText?.let { return it }
        return block.mealLabel?.let { "${it}推荐" }.orEmpty()
    }

    /** 出行要点 + 杂项配套（交通 / 地址等）合并展示，原文内容不丢 */
    private fun mergeTravelTip(block: RawBlock): String? {
        val main = block.travelTip
        val extras = block.extraTips
        if (main.isNullOrBlank() && extras.isEmpty()) return main
        return buildList {
            main?.takeIf { it.isNotBlank() }?.let { add(it) }
            addAll(extras)
        }.joinToString("\n")
    }

    /** 字段值清洗：去首尾空白 / 加粗 / 行内代码；不动正文字面（含「暂无相关信息」） */
    private fun cleanFieldValue(raw: String): String? {
        var v = raw.trim()
        BOLD_REGEX.find(v)?.let { v = it.groupValues[1].trim() }
        v = v.trim('`', ' ').trim()
        return v.ifBlank { null }
    }

    /** 解析宠物友好字段值：明确否定→false；肯定词→true；其余默认 false */
    private fun parsePetFriendly(text: String?): Boolean {
        val t = text.orEmpty().trim()
        if (t.isBlank()) return false
        if (PET_NEGATIVE.any { t.contains(it) }) return false
        return PET_POSITIVE.any { t.contains(it, ignoreCase = true) }
    }

    /** 严格四分类映射（住宿 → 餐饮 → 购物 → 景点）；识别不了返回 null */
    private fun mapType(text: String?): AiSpotType? {
        val t = text.orEmpty().trim()
        if (t.isBlank()) return null
        return when {
            HOTEL_KEYWORDS.any { t.contains(it, ignoreCase = true) } -> AiSpotType.HOTEL
            FOOD_KEYWORDS.any { t.contains(it, ignoreCase = true) } -> AiSpotType.LOCAL_FOOD
            SHOP_KEYWORDS.any { t.contains(it, ignoreCase = true) } -> AiSpotType.INTERNET_FAMOUS_SHOP
            SPOT_KEYWORDS.any { t.contains(it, ignoreCase = true) } -> AiSpotType.POPULAR_SPOT
            else -> null
        }
    }

    /**
     * 点位全名清洗：去任务清单 / 链接 / 图片 / emoji / 序号 / Markdown 装饰与尾部括注；
     * 【不取冒号前段】（那会把「午餐：同盛祥」错切成「午餐」）；保留完整名称。
     */
    private fun cleanPointName(rawEntry: String): String? {
        var entry = rawEntry.trim()
        entry = TASK_LIST_REGEX.replace(entry, "").trim()
        entry = entry.replace(IMAGE_REGEX, "").replace(LINK_REGEX) { it.groupValues[1] }
        if (entry.contains("http://", ignoreCase = true) ||
            entry.contains("https://", ignoreCase = true)
        ) {
            return null
        }
        BOLD_REGEX.find(entry)?.let { entry = it.groupValues[1].trim() }

        var name = entry.trim('#', '*', '`', '>', ' ').trim()
        name = name.trimStart { ch ->
            ch.isWhitespace() || ch.isSurrogate() || ch.code in EMOJI_RANGES
        }
        name = LEADING_INDEX_REGEX.replace(name, "").trim()
        name = TRAIL_PAREN_REGEX.replace(name, "").trim()
        name = name.replace(Regex("""[*`#>]"""), "").replace(Regex("\\s+"), " ").trim()

        return name.takeIf {
            it.length in 2..30 &&
                it.any { ch -> ch.code in CJK_RANGE } &&
                !it.all { ch -> ch.isDigit() || ch in "：:，,、.。-— " }
        }
    }

    /** 名称关键词兜底推断类型（字段 / 标签都没给类型时使用） */
    private fun inferType(name: String): AiSpotType {
        val t = name.lowercase()
        return when {
            HOTEL_KEYWORDS.any { t.contains(it) } -> AiSpotType.HOTEL
            FOOD_KEYWORDS.any { t.contains(it) } -> AiSpotType.LOCAL_FOOD
            SHOP_KEYWORDS.any { t.contains(it) } -> AiSpotType.INTERNET_FAMOUS_SHOP
            else -> AiSpotType.POPULAR_SPOT
        }
    }

    /** 中文数字（支持 一..十、十几、二十几），日标题用 */
    private fun chineseToInt(s: String): Int? {
        if (s.isEmpty()) return null
        s.toIntOrNull()?.let { return it }
        if (s == "十") return 10
        return when {
            s.startsWith("十") -> s.drop(1).singleCnDigit()?.let { 10 + it }
            s.contains("十") -> {
                val parts = s.split("十")
                val tens = parts.getOrNull(0)?.singleCnDigit()
                val ones = parts.getOrNull(1)?.singleCnDigit() ?: 0
                tens?.let { it * 10 + ones }
            }
            else -> s.singleCnDigit()
        }
    }

    private fun String.singleCnDigit(): Int? = CN_DIGITS[this]

    private companion object {
        private const val TAG = "GuideGeocode"

        private val CJK_RANGE = 0x4E00..0x9FFF
        private val EMOJI_RANGES = 0x1F000..0x1FAFF

        private val CN_DIGITS = mapOf(
            "一" to 1, "二" to 2, "两" to 2, "三" to 3, "四" to 4, "五" to 5,
            "六" to 6, "七" to 7, "八" to 8, "九" to 9,
        )

        /** 无序列表条目（严格按后端格式：行首「- 」，兼容「* 」；不接受数字编号，避免误吃预算行） */
        private val BULLET_LINE_REGEX = Regex("""^\s*[-*]\s+(.*\S.*)\s*$""")

        /** 「key：value」字段行（key 限长，全角 / 半角冒号） */
        private val FIELD_SPLIT_REGEX = Regex("""^([^:：]{1,12})[:：]\s*(.+)$""")
        private val FIELD_LABEL_NOISE = Regex("""[\s*`（）()\[\]【】]""")

        private val TASK_LIST_REGEX = Regex("""^\[[ xXX]\]\s*""")
        private val IMAGE_REGEX = Regex("""!\[[^\]]*]\([^)]*\)""")
        private val LINK_REGEX = Regex("""\[([^\]]+)]\([^)]*\)""")
        private val BOLD_REGEX = Regex("""\*\*\s*(.+?)\s*\*\*""")
        /** 整行只有一个加粗片段（如 **预算核算**） */
        private val BOLD_ONLY_REGEX = Regex("""^\*\*\s*(.+?)\s*\*\*$""")
        private val BRACKET_REGEX = Regex("""【\s*(.+?)\s*】""")
        private val LEADING_INDEX_REGEX = Regex("""^\s*(?:\d+\s*[.、)]\s*|[①②③④⑤⑥⑦⑧⑨⑩]\s*)""")
        private val TRAIL_PAREN_REGEX = Regex("""[（(][^）)]{0,10}[）)]\s*$""")
        /** 时间被剥离后残留的空括号（如「餐厅 （）」） */
        private val EMPTY_PAREN_REGEX = Regex("""\s*[（(]\s*[）)]""")

        /** 行内时间段：用餐词/时段词可选 + 时刻（可带「-时刻」区间），如 午餐12:30 / 上午9:00-12:00 */
        private val INLINE_TIME_REGEX = Regex(
            """((?:早餐|午餐|晚餐|夜宵|早茶|下午茶|上午|中午|下午|晚上)?\s*\d{1,2}:\d{2}\s*(?:[-—~～]\s*\d{1,2}:\d{2})?)""",
        )

        /** 日标题：第一天 / 第1天 / 第 2 天（捕获中文或阿拉伯数字） */
        private val DAY_CN_REGEX = Regex("""第\s*([一二两三四五六七八九十0-9]{1,3})\s*天""")
        /** 日标题：Day 2 / DAY3 / D2（两个捕获组，取值见 detectDayNumber） */
        private val DAY_EN_REGEX =
            Regex("""(?:Day|DAY)\s*0*(\d{1,2})\b.*|(?:^|[\s#*])D\s*0*(\d{1,2})(?![\d.])\s*.*""")

        /** 文末预算 / 费用章节关键词（Markdown 标题或加粗小节命中即忽略其下内容） */
        private val BUDGET_KEYWORDS = listOf(
            "预算核算", "预算汇总", "预算明细", "预算估算", "费用预算", "费用明细",
            "费用估算", "花费预算", "预算", "费用核算", "成本估算", "总计", "合计",
        )

        // 字段名（长词在前，匹配用 startsWith）
        private val TICKET_LABELS = listOf("门票信息", "门票价格", "门票费用", "门票", "票价")
        private val AVG_LABELS = listOf("人均消费", "人均价格", "人均费用", "人均", "客单价", "客单")
        private val DISH_LABELS = listOf(
            "特色菜品", "招牌菜品", "特色菜", "招牌菜", "推荐菜", "招牌", "必点菜",
        )
        private val TIME_LABELS = listOf(
            "时间段", "游玩时间", "用餐时间", "开放时间", "营业时间", "建议时段",
            "建议时间", "安排时间", "时段", "时间",
        )
        private val PHOTO_LABELS = listOf(
            "拍照打卡点", "拍照打卡", "打卡点位", "打卡点", "拍照场景", "拍照机位",
            "拍照点", "拍照", "出片场景", "出片点", "出片机位", "出片",
        )
        private val TRAVEL_TIP_LABELS = listOf(
            "出行要点", "出行贴士", "出行提示", "注意事项", "游玩贴士", "游玩提示",
            "避坑指南", "贴士", "温馨提示", "小贴士",
        )
        private val TYPE_LABELS = listOf("点位类型", "类型", "分类", "类别")
        private val SOURCE_LABELS = listOf("推荐来源", "来源", "出处")
        private val PET_LABELS = listOf("宠物友好", "宠物", "携宠", "带宠")
        private val DESC_LABELS = listOf(
            "简介", "介绍", "描述", "备注", "说明", "亮点", "推荐理由",
        )
        /** 杂项配套字段：不作为点位，挂到上一点位的补充信息里展示 */
        private val EXTRA_INFO_LABELS = listOf(
            "交通方式", "交通指南", "交通", "怎么去", "如何到达", "地铁路线", "公交路线",
            "详细地址", "具体地址", "地址", "位置", "停车", "建议游玩时长", "游玩时长",
            "建议用时", "用时", "预约方式", "预约", "联系方式", "电话",
        )

        /** 所有已知字段名前缀（matchField 用） */
        private val ALL_FIELD_PREFIXES =
            TICKET_LABELS + AVG_LABELS + DISH_LABELS + TIME_LABELS + PHOTO_LABELS +
                TRAVEL_TIP_LABELS + TYPE_LABELS + SOURCE_LABELS + PET_LABELS +
                DESC_LABELS + EXTRA_INFO_LABELS

        /** 用餐时段词（【午餐】标签 / 前缀 / 行内时间） */
        private val MEAL_WORDS = listOf("早餐", "午餐", "晚餐", "夜宵", "加餐", "早茶", "下午茶")

        /** 「午餐：餐厅名」「晚餐-餐厅名」「午餐 餐厅名」前缀（捕获时段 + 剩余全名） */
        private val MEAL_PREFIX_REGEX =
            Regex("""^(早餐|午餐|晚餐|夜宵|加餐|早茶|下午茶)\s*[:：、\-—\s]\s*(.+)$""")

        // 行内【】来源标签
        private val LOCAL_TAG_KEYWORDS = listOf("本地库", "数据库", "预置", "预设", "库内", "资料库")
        private val AI_TAG_KEYWORDS = listOf("AI", "智能", "拓展", "扩展", "额外", "生成")

        // 宠物友好取值
        private val PET_POSITIVE =
            listOf("是", "可", "允许", "友好", "true", "1", "支持", "能带", "可以")
        private val PET_NEGATIVE =
            listOf("否", "不可", "不能", "禁止", "不允许", "false", "0", "谢绝")

        // 严格四分类关键词
        private val HOTEL_KEYWORDS =
            listOf("住宿", "酒店", "民宿", "客栈", "宾馆", "旅馆", "青旅", "hotel")
        private val FOOD_KEYWORDS = listOf(
            // 类型 / 业态词
            "餐饮", "餐厅", "餐馆", "饭店", "美食", "小吃", "面馆", "泡馍", "凉皮",
            "肉夹馍", "葫芦头", "饭庄", "食府", "菜馆", "大排档", "酒楼", "小炒",
            "饺子", "包子", "火锅", "烧烤", "夜宵", "料理", "咖啡", "奶茶", "茶馆",
            "食堂", "本帮菜", "老字号", "food", "cafe", "restaurant",
        )
        private val SHOP_KEYWORDS = listOf(
            "购物", "商场", "购物中心", "商圈", "商店", "商铺", "超市", "市集", "集市",
            "免税", "买手", "百货", "奥特莱斯", "步行街", "网红店", "探店",
            "shop", "mall",
        )
        private val SPOT_KEYWORDS = listOf(
            "景点", "景区", "公园", "名胜", "观光", "游览", "游乐", "博物", "展馆", "展览",
            "风景", "古镇", "古城", "遗址", "寺", "庙", "纪念馆", "科技馆", "美术馆",
        )
    }
}
