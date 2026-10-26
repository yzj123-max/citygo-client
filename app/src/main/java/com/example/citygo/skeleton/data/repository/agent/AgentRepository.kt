package com.example.citygo.skeleton.data.repository.agent

import com.example.citygo.skeleton.data.model.TravelPlan
import com.example.citygo.skeleton.data.model.chat.ChatMessage
import com.example.citygo.skeleton.data.model.chat.ProposalStatus
import com.example.citygo.skeleton.data.repository.ai.AiGuideParser
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/* =====================================================================================
 * 豆包 AI 对话 Agent 模块（旅游规划助手）
 *
 * 与「AI 一键生成攻略」相互独立：
 * - 攻略生成：计划页通过 TravelApi 提交后端任务，由后端调用方舟生成攻略；
 * - 对话 Agent：基于【当前城市 + 当前完整行程 + 历史聊天记录】进行多轮上下文对话，
 *   支持询问景点信息、更换宠物友好点位、推荐小众探店美食，并可在用户要求改行程时
 *   由模型回传标准 JSON 提议，用户确认后替换行程（见 [AgentChatResult.planProposalJson]）。
 *
 * 网络协议：火山方舟（豆包）Responses API
 *   POST https://ark.cn-beijing.volces.com/api/v3/responses
 *   请求体 {model, input:[{role, content:[{type:"input_text"/"output_text", text}]}]}
 *   回复文本位于 output[?type=message].content[?type=output_text].text
 * 不包含任何高德导航 / 天气预报逻辑，也不修改既有 AI 攻略代码。
 * ===================================================================================== */

/** Agent 业务异常（网络 / Key / HTTP / 响应异常），消息可直接展示给用户。 */
class AgentException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 一次对话请求的完整上下文。
 *
 * @property city 当前城市
 * @property currentPlan 当前完整行程（可能为空行程）
 * @property history 历史聊天记录（不含本次用户消息，按时间正序）
 * @property userMessage 本次用户发言
 */
data class AgentChatRequest(
    val city: String,
    val currentPlan: TravelPlan,
    val history: List<ChatMessage>,
    val userMessage: String,
)

/**
 * 一次对话结果。
 *
 * @property replyText 展示在气泡里的回复文本（已剔除行程 JSON 协议块）
 * @property planProposalJson 非 null 表示模型在本轮给出了一份【可成功解析的】全量行程
 *   方案（`<PLAN_JSON>` 块内的 JSON 原文）；调用方不得立即改行程，应把它作为「待确认
 *   提议」挂到助手消息上，等用户点击「同步修改行程」后再解析应用；为 null 表示本轮
 *   只是问答（或协议块非法 / 无协议块），没有可同步的方案
 */
data class AgentChatResult(
    val replyText: String,
    val planProposalJson: String? = null,
)

/** 对话 Agent 仓库接口（方便后续替换实现 / 注入测试替身）。 */
interface AgentRepository {

    /**
     * 携带上下文发起一次多轮对话；失败抛 [AgentException]，不返回空壳结果。
     */
    suspend fun chat(request: AgentChatRequest): AgentChatResult
}

/* ===================================================================================== */

/** 豆包（火山方舟）连接配置：已接入 doubao-seed-evolving（Responses API）。 */
object DoubaoAgentConfig {
    /** 火山方舟 API 根地址（末尾不带斜杠），对话请求拼接 /responses */
    const val BASE_URL: String = "https://ark.cn-beijing.volces.com/api/v3"

    /** 火山方舟 API Key（控制台「API Key 管理」获取） */
    const val API_KEY: String = "ark-2890d5eb-ae66-42d1-b521-a67c3a439332-d8b33"

    /**
     * 模型 ID：doubao-seed-evolving（推理模型，已实测账号开通、两个端点均 HTTP 200）。
     * 本模块走用户指定的 Responses API：POST {BASE_URL}/responses
     */
    const val MODEL: String = "doubao-seed-evolving"

    /** 请求超时（秒）：推理模型改行程时 reasoning token 较多，放宽到 120s */
    const val TIMEOUT_SECONDS: Long = 120L

    /** 随请求携带的历史聊天条数上限（越早的越先丢弃，控制 token 用量） */
    const val MAX_HISTORY_MESSAGES: Int = 20
}

/**
 * 豆包大模型对话实现（火山方舟 Responses API，POST {baseUrl}/responses）：
 * 1. 组装系统提示词（角色 + 当前城市/完整行程 + 行程修改 JSON 协议）；
 * 2. 携带最近若干轮历史消息，以 Responses 协议 input 数组发起多轮对话；
 * 3. 从响应 output[].content[]（output_text）取回复文本；
 * 4. 回复中若带 <PLAN_JSON> 协议块，复用 [AiGuideParser] 解析为 [AiTravelPlan]，
 *    并把协议块从气泡文本中剔除。
 *
 * 注意：/responses 与 /chat/completions 是两套协议，字段不可混用（已实测两者均可用，
 * 本模块按用户指定走 /responses）。
 */
class DoubaoAgentRepository(
    private val baseUrl: String = DoubaoAgentConfig.BASE_URL,
    private val apiKey: String = DoubaoAgentConfig.API_KEY,
    private val model: String = DoubaoAgentConfig.MODEL,
    timeoutSeconds: Long = DoubaoAgentConfig.TIMEOUT_SECONDS,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) : AgentRepository {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val gson = Gson()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .build()

    override suspend fun chat(request: AgentChatRequest): AgentChatResult = withContext(ioDispatcher) {
        if (apiKey.isBlank()) {
            throw AgentException("豆包 API Key 未配置，请在 DoubaoAgentConfig.API_KEY 中填入后重试")
        }
        if (model.isBlank()) {
            throw AgentException("豆包模型 / 推理接入点未配置，请在 DoubaoAgentConfig.MODEL 中填入后重试")
        }

        val inputItems = buildInputItems(request)

        // Responses API 请求体：model + input（分段 content 结构）
        // reasoning.effort=minimal：关闭深度思考。该模型默认开启长链推理，生成全量行程
        // JSON 会耗时 3 分钟以上（实测远超 App 120s 超时）；本任务是结构化改写，关闭后
        // 延迟降到正常对话水平（火山方舟官方文档：Responses API 用 reasoning.effort=minimal
        // 关闭深度思考）。
        val payload = linkedMapOf(
            "model" to model,
            "temperature" to 0.7,
            "reasoning" to mapOf("effort" to "minimal"),
            "input" to inputItems,
        )
        val httpRequest = Request.Builder()
            .url("$baseUrl/responses")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(gson.toJson(payload).toRequestBody(jsonMediaType))
            .build()

        val rawReply = try {
            client.newCall(httpRequest).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw AgentException("豆包服务返回错误：HTTP ${response.code}，${extractErrorMessage(body)}")
                }
                extractContent(body)
            }
        } catch (e: AgentException) {
            throw e
        } catch (e: Exception) {
            throw AgentException("豆包网络请求失败：${e.message}", e)
        }

        // 解析行程提议协议块：仅当协议块存在且能成功解析为行程时，把【原文】作为待确认
        // 提议返回（客户端不立即改行程，由用户点击气泡按钮后再解析应用）；
        // 解析失败不影响文字回答本身，按纯文本回复处理。
        val planProposalJson = runCatching { extractProposalJson(request.city, rawReply) }
            .onFailure { android.util.Log.w(TAG, "对话中的行程 JSON 解析失败，按纯文本回复处理", it) }
            .getOrNull()
        val replyText = PLAN_BLOCK_REGEX.replace(rawReply, "").trim()
            .ifBlank {
                // 模型只回了 JSON、没写说明文字时给一句默认文案；
                // 协议块解析失败（坏 JSON）时绝不能把 rawReply 原始 JSON 回显到气泡
                if (planProposalJson != null) {
                    "我按你的诉求整理了一份新行程方案，点击下方「同步修改行程」即可更新出行计划。"
                } else {
                    "抱歉，我没能正确理解你的行程调整想法，可以换个说法再描述一次吗？"
                }
            }

        return@withContext AgentChatResult(replyText = replyText, planProposalJson = planProposalJson)
    }

    /**
     * 组装 Responses API 的 input 数组：
     * system（角色+当前行程上下文）+ 历史消息 + 本轮用户消息。
     * 每个消息项必须显式带 type="message"（多轮携带 assistant 历史时服务端强校验，
     * 缺失会返回 HTTP 400 missing input.type）；
     * user/system 文本段类型为 input_text，历史 assistant 文本段类型为 output_text。
     */
    private fun buildInputItems(request: AgentChatRequest): List<Map<String, Any?>> {
        val result = mutableListOf<Map<String, Any?>>()
        result.add(
            mapOf(
                "type" to "message",
                "role" to "system",
                "content" to listOf(textPart("input_text", AgentPrompts.systemPrompt(
                    city = request.city,
                    currentPlan = request.currentPlan,
                ))),
            ),
        )
        request.history
            .takeLast(DoubaoAgentConfig.MAX_HISTORY_MESSAGES)
            .forEach { msg ->
                val role = if (msg.isUser) "user" else "assistant"
                // 历史中助手的话按 Responses 规范用 output_text 段回传；
                // 助手曾给出的行程提议要在文本末尾标注用户的处理结果，避免模型把
                // 「已暂不同步 / 已被替代」的未生效提议当成既成事实延续进新方案
                val historyText = if (msg.isUser) msg.content else annotateAssistantHistory(msg)
                val partType = if (msg.isUser) "input_text" else "output_text"
                result.add(
                    mapOf(
                        "type" to "message",
                        "role" to role,
                        "content" to listOf(textPart(partType, historyText)),
                    ),
                )
            }
        result.add(
            mapOf(
                "type" to "message",
                "role" to "user",
                "content" to listOf(textPart("input_text", request.userMessage)),
            ),
        )
        return result
    }

    /** 构造一个 Responses 协议文本段：{"type": type, "text": text} */
    private fun textPart(type: String, text: String): Map<String, String> =
        mapOf("type" to type, "text" to text)

    /**
     * 给历史中的助手消息追加「行程提议处理结果」标注：
     * 只有 APPLIED 的提议真正进入了当前行程；其余状态的改动一律未生效，
     * 必须显式告诉模型不要在后续方案里延续这些改动。
     */
    private fun annotateAssistantHistory(msg: ChatMessage): String = when (msg.proposalStatus) {
        ProposalStatus.APPLIED ->
            msg.content + "\n（注：该行程方案已被用户确认并同步到当前出行计划。）"
        ProposalStatus.DECLINED ->
            msg.content + "\n（注：该行程方案被用户选择「暂不」，【未生效】。其中提到的任何增删改换" +
                "都没有进入当前行程，后续对话与新方案中严禁默认采纳或延续这些改动。）"
        ProposalStatus.SUPERSEDED ->
            msg.content + "\n（注：该行程方案后来被更新的方案替代，【未生效】。其中提到的任何增删改换" +
                "都没有进入当前行程，后续对话与新方案中严禁默认采纳或延续这些改动。）"
        ProposalStatus.PENDING ->
            msg.content + "\n（注：该行程方案用户尚未确认，当前出行计划未因此改动。）"
        ProposalStatus.NONE -> msg.content
    }

    /**
     * 从回复文本中提取 `<PLAN_JSON>` 协议块：取块内 JSON 原文，并用 AiGuideParser
     * 试解析校验（解析失败抛错由上层捕获；解析成功但 spotList 为空同样视为无提议，
     * 避免空方案被确认后清空行程）。无协议块返回 null。
     */
    private fun extractProposalJson(city: String, rawReply: String): String? {
        val block = PLAN_BLOCK_REGEX.find(rawReply)?.groupValues?.getOrNull(1)?.trim()
            ?: return null
        if (block.isEmpty()) return null
        // AiGuideParser 已兼容 ```json 围栏、字段名漂移与脏数据
        val parsed = AiGuideParser.parse(city = city, rawContent = block, nowMillis = System.currentTimeMillis())
        return block.takeIf { parsed.spotList.isNotEmpty() }
    }

    /**
     * 从 Responses 响应中取助手文本：
     * output[] 中 type=="message" 的项 → content[] 中 type=="output_text" 的 text。
     * （output 里还可能有 type=="reasoning" 的思考项，需跳过。）
     */
    private fun extractContent(responseText: String): String {
        try {
            val root = gson.fromJson(responseText, JsonObject::class.java)
            // status 非 completed（如 incomplete / failed）时把状态带入异常，便于定位
            val status = root?.get("status")?.takeIf { !it.isJsonNull }?.asString
            val messageItem = root
                ?.getAsJsonArray("output")
                ?.mapNotNull { it?.asJsonObject }
                ?.firstOrNull { it.get("type")?.asString == "message" }
            val content = messageItem
                ?.getAsJsonArray("content")
                ?.mapNotNull { it?.asJsonObject }
                ?.firstOrNull { it.get("type")?.asString == "output_text" }
                ?.get("text")
                ?.asString
            if (content.isNullOrBlank()) {
                throw AgentException(
                    "豆包响应中缺少 output[message].content[output_text].text" +
                        (status?.let { "（response.status=$it）" } ?: ""),
                )
            }
            return content
        } catch (e: AgentException) {
            throw e
        } catch (e: Exception) {
            throw AgentException("豆包响应解析失败：${e.message}", e)
        }
    }

    /** 火山方舟错误响应体形如 {"error":{"message":"...","code":"..."}}，提取可读 message。 */
    private fun extractErrorMessage(body: String): String {
        return try {
            val root = gson.fromJson(body, JsonObject::class.java)
            root?.getAsJsonObject("error")?.get("message")?.asString ?: body
        } catch (_: Exception) {
            body
        }.ifBlank { "无响应内容" }
    }

    private companion object {
        private const val TAG = "DoubaoAgentRepository"

        /** 行程修改协议块标记：模型仅在需要改行程时于回复中输出该块 */
        private val PLAN_BLOCK_REGEX =
            Regex("""<PLAN_JSON>\s*([\s\S]*?)\s*</PLAN_JSON>""", RegexOption.IGNORE_CASE)
    }
}

/* ===================================================================================== */

/** 对话 Agent 系统提示词（角色设定 + 行程上下文 + 行程修改 JSON 协议）。 */
object AgentPrompts {

    /**
     * @param city 当前城市
     * @param currentPlan 当前完整行程（用于让模型基于已有攻略对话 / 修改）
     */
    fun systemPrompt(city: String, currentPlan: TravelPlan): String {
        val planContext = renderPlanContext(currentPlan)
        return """
            你是旅游规划助手，基于当前用户已生成的旅游攻略进行对话。用户可以咨询景点信息、询问路线安排，也可以描述对行程的修改想法。回答尽量结构化。【重要】你无权直接修改用户的行程：任何行程调整都只能作为「提议方案」输出，并由用户在界面上点击确认后才会生效，因此答复中不要声称「已帮你改好/已更新」，而要先给出方案说明、再询问用户是否同步。

            当前城市：$city
            $planContext

            对话要求：
            1. 回答尽量结构化（可用短句、序号、emoji），语气像懂行的本地朋友，简洁实用；
            2. 【纯咨询场景】询问景点信息、问顺路/用时/避坑、征询建议、闲聊等不涉及落地修改行程的场景，只回复自然语言，不要输出任何 JSON；
            3. 【行程修改场景】当用户明确要求新增 / 删除 / 替换 / 调整顺序 / 更换宠物友好点位，或接受了你的调整建议时，按以下顺序回复：
               - 先用 1-3 句自然语言说明你打算怎么改、为什么这样改（说清涉及第几天、换进/换出哪些地点；如果总天数或住宿晚数发生变化——例如从 4 天改成「五天四晚」——必须明确说出新的总天数与晚数）；
               - 然后主动追问一句，例如「需要我帮你把这个方案同步到出行计划吗？」（措辞可变化，但必须给用户明确的选择，不要替用户做决定，不要说已经改好了）；
               - 然后另起一行，输出完整的新行程【提议】协议块（是修改后的【全量】行程，未改动的地点也要原样保留，不要只返回变化部分）：
               <PLAN_JSON>
               {
                 "city": "$city",
                 "spotList": [
                   {
                     "name": "地点名称",
                     "type": "大众景点|小众景点|本地美食|探店网红店|酒店住宿",
                     "day": 1,
                     "desc": "2-4 句接地气的简介",
                     "latitude": 29.563854,
                     "longitude": 106.598907,
                     "isPetFriendly": false,
                     "imageUrls": [],
                     "tip": "游玩小贴士：时段 / 避坑 / 人均消费 / 携宠要求等"
                   }
                 ]
               }
               </PLAN_JSON>
               即使用户用命令式口吻（如「直接把第二天的饭店换掉」），也同样先说明、再询问、最后给提议协议块，等待用户点击确认；
               - 【必须当轮给块】只要用户的话涉及行程增删改换调序（包括用「能不能 / 可不可以 / 帮我看看 / 加得合适我再同步」等征询、商量口吻），本轮回复就必须带上完整协议块，即使用户说「先不改/合适再同步/回头再说」、即使你给了多个备选方案（按你推荐的方案给块），也要当轮附块；禁止回复「你确认后我再给完整行程 / 你说好我再出方案」之类的话——用户在界面上点击按钮才是同步闸门，协议块本身只是待确认的提议，用户不点同步就什么都不会改；
            4. 【协议块格式硬性要求，违反会导致方案无法被识别】顶层只能有 "city" 和 "spotList" 两个键，spotList 必须是【扁平】的地点对象数组，严禁使用 days/points/itinerary 等任何嵌套分组；每个地点的 type 只能是第 3 步列出的五个中文枚举值之一，严禁输出 SPOT/RESTAURANT/SHOP/HOTEL 等英文标记；经纬度必须是 latitude、longitude 两个【数字】字段，严禁合并成 "coordinates":"lat,lng" 字符串；day 是地点对象内的整数；协议块内是纯 JSON，不要 markdown 围栏、不要注释；
            5. 经纬度使用 GCJ-02 火星坐标真实数值；新增国内地点时坐标必须能在中国地图上定位，不要编造；
            6. 更换宠物友好点位时，优先替换为确实可携宠进入的地点，并在 tip 中写明牵绳 / 拾便袋等要求，isPetFriendly 置为 true；
            7. day 字段表示该地点属于第几天（从 1 开始连续整数）。【行程总天数与住宿晚数必须以用户本轮描述为准，严禁死守当前行程天数】：
               - 用户明确改变行程长度时（例如当前是 4 天，用户说「改成五天四晚」「多玩一天」「压缩成 3 天」），新方案的 day 必须按新的总天数从 1 连续编号到 N（五天四晚即 day=1..5），并把全部地点（保留的 + 新增的）重新合理分配到每一天：延长天数时为新增的天补充同城市真实景点/美食/探店，缩短天数时把被删天数的地点合理并入保留的天或删除，并在说明里讲清；
               - 酒店数量 = 住宿晚数：「N 天 N-1 晚」时安排 N-1 个「酒店住宿」，分别放在第 1..N-1 天的最后一个，最后一天为返程日【不安排酒店】（如五天四晚 = 4 个酒店，位于 D1~D4 末尾，D5 无酒店）；用户未改变长度时，沿用当前行程天数，每天恰好保留 1 个酒店；多天可同名连住；
               - 每一天至少安排 1 个非住宿地点，禁止出现整天没有任何地点的空 day，也不要让 day 编号出现空洞（如只有 day=1 和 day=3）；
               - 未改变天数时，未改动地点的 day 原样保留；同一天的地点必须在同一行政区/相邻商圈、彼此短车程可达；输出 spotList 前对每一天重排：先排当天非住宿地点（按合理游览/用餐顺序），再把当天的酒店住宿放在【当天最后一个】；每天类型尽量混合（景点 + 本地美食 + 探店网红店/休闲）；
            8. 协议块之外不要再输出第二个 JSON 对象；纯咨询场景严禁输出协议块；
            9. 【事实来源】上面的「当前完整行程」清单是行程的唯一事实来源，已同步的改动一定会反映在该清单里。历史对话中你曾提过、但标注为【未生效 / 用户尚未确认】的方案一律视为不存在，严禁把其中的改动延续进新方案；全量协议块必须以「当前完整行程」清单为基准：除用户本轮明确要求的变化（含第 7 条的总天数/晚数调整及随之产生的 day 重排、新增/删除地点）外，其余地点一个不多、一个不少，名称、类型、day 都原样保留；
            10. 【新增去重】用户要求新增地点时，新增点严禁与当前行程中已有地点重名、同名分店或实为同一地点；如果用户点名想加的地方已经在行程里，要明确告知已存在，并改荐另一个不同的真实地点。
        """.trimIndent()
    }

    /** 把当前行程渲染为模型易读的文本上下文；空行程给明确提示。 */
    private fun renderPlanContext(plan: TravelPlan): String {
        if (plan.allPlaceList.isEmpty()) {
            return "当前行程：（暂无行程，用户可能想从零规划，可主动给出建议并在其确认后再输出行程协议块）"
        }
        val sb = StringBuilder("当前完整行程（共 ${plan.totalDays.coerceAtLeast(1)} 天，" +
            "${plan.allPlaceList.size} 个地点）：")
        plan.dailyPlans.forEach { dayPlan ->
            sb.append("\nD").append(dayPlan.day).append("：")
            dayPlan.places.forEachIndexed { index, place ->
                sb.append("\n").append(index + 1).append(". [").append(place.placeType.label).append("] ")
                    .append(place.name)
                    .append(" | ").append(place.lat).append(",").append(place.lng)
                    .append(" | ").append(if (place.petFriendly) "宠物可入" else "宠物不可入")
                if (place.intro.isNotBlank()) {
                    // 控制上下文长度，超长简介截断
                    val intro = place.intro.lineSequence().firstOrNull().orEmpty().ifBlank { place.intro }
                    sb.append(" | ").append(intro.take(80))
                }
            }
        }
        return sb.toString()
    }
}
