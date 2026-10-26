package com.demo.logindemo.service;

import com.demo.logindemo.dto.StylistChatSseDTO;
import com.demo.logindemo.dto.TravelScheduleDTO;
import com.demo.logindemo.mapper.AttractionMapper;
import com.demo.logindemo.mapper.BasicStyleKnowledgeMapper;
import com.demo.logindemo.util.TosUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class StylistService {

    // =========================================================
    // 一、配置及依赖注入
    // =========================================================

    @Value("${agent.ark-api-key}")
    private String arkApiKey;

    @Value("${agent.endpoint}")
    private String endpointId;

    @Value("${agent.base-url}")
    private String baseUrl;

    @Autowired
    private AttractionMapper attractionMapper;

    @Autowired
    private TosUtil tosUtil;

    @Autowired
    private BasicStyleKnowledgeMapper basicStyleKnowledgeMapper;

    /**
     * 数据库穿搭知识库缓存。
     */
    private volatile List<Map<String, Object>> styleKnowledgeList =
            new ArrayList<>();

    /**
     * 拼接后的完整穿搭知识库文本。
     */
    private volatile String knowledgeFullText = "";

    /**
     * JSON 序列化工具。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 仅用于多图 SSE 注释心跳，不涉及 App 地址、USB 转发或登录配置。
    private final ScheduledExecutorService multiImageHeartbeat =
            Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "stylist-sse-heartbeat");
                thread.setDaemon(true);
                return thread;
            });

    @PreDestroy
    public void stopMultiImageHeartbeat() {
        multiImageHeartbeat.shutdownNow();
    }

    private Runnable startMultiImageHeartbeat(
            SseEmitter emitter,
            AtomicBoolean finished,
            String sceneName
    ) throws IOException {
        AtomicReference<ScheduledFuture<?>> scheduled = new AtomicReference<>();
        Runnable stop = () -> {
            ScheduledFuture<?> future = scheduled.get();
            if (future != null) future.cancel(false);
        };
        emitter.onCompletion(stop);
        emitter.onError(error -> stop.run());
        emitter.onTimeout(stop);

        // 首条注释使 Controller 返回时即有 SSE 内容可提交给客户端。
        // 注释不是 AI 正文，前端不会把它渲染到评估结果。
        emitter.send(SseEmitter.event().comment("connected"));
        scheduled.set(multiImageHeartbeat.scheduleWithFixedDelay(() -> {
            if (finished.get()) {
                stop.run();
                return;
            }
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (Exception error) {
                finished.set(true);
                stop.run();
                log.warn("{} SSE 心跳发送失败：{}", sceneName, error.toString());
                // send 的 I/O 错误由 Servlet 容器触发 onError，避免重复终结响应。
            }
        }, 15, 15, TimeUnit.SECONDS));
        return stop;
    }


    /**
     * HTTP 客户端。
     */
    private final OkHttpClient okHttpClient =
            new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(180, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build();


    // =========================================================
    // 二、统一的专业造型顾问提示词
    // =========================================================

    /**
     * 图片分析、景点评估和旅行行程评估共用的系统提示词。
     */
    private static final String STYLIST_SYSTEM_PROMPT = """
            你是一名专业的个人造型顾问，擅长服装色彩、版型比例、
            风格协调、配饰选择和场景穿搭。

            你的任务是结合用户提供的图片、问题、场景信息和数据库知识，
            给出准确、客观、专业、实用的穿搭分析与建议。

            请始终遵守以下要求。

            ==================== 一、图片事实与推断 ====================

            1. 图片中能够直接确认的信息可以明确描述，包括可见服装的颜色、
               图案、廓形、长度、鞋履、配饰以及服装之间的搭配关系。

            2. 对于看不清、被遮挡或未入镜的内容，必须说明无法确认。
               不得编造不可见的裤子、裙子、鞋包、首饰、面料成分等细节。

            3. 严格区分以下三类信息：
               A. 图片事实：能够直接从图片中观察到的内容。
               B. 专业推断：根据可见信息作出的合理审美判断。
               C. 未知信息：图片不足以支持判断的内容。

            4. 专业推断应当与实际观察相对应。
               例如，可以说“宽松上衣搭配修身下装形成松紧对比”。
               但不得仅凭一张图片就断言某种搭配一定显高、一定显瘦，
               或者一定适合某种身材。

            5. 不得根据服装直接推断用户的实际身高、体重、
               肤色类型或其他无法从图片可靠确认的信息。

            6. 如果人物姿势、拍摄角度、衣物遮挡影响比例判断，
               应说明判断存在局限，不得给出缺乏依据的绝对结论。

            7. 如果图片没有完整展示全身，不得对整套穿搭的色彩面积比例、
               全身比例或未入镜单品作出确定判断。

            8. 对于肩线、腰线、腿长等视觉效果，应结合实际可见结构分析。
               例如，黑色撞色可能形成视觉分区，但不能仅凭撞色就断言
               一定具有收肩效果。

            ==================== 二、数据库知识的使用 ====================

            1. 本次系统消息提供的 MySQL 穿搭知识库，
               是引用数据库规则时的唯一依据。

            2. 只引用与当前图片和用户问题实际相关的规则。
               不要求机械套用全部规则，也不要为了展示专业性而罗列规则。

            3. 引用规则时，必须使用知识库中真实存在的完整规则标题，
               并使用《规则标题》的格式。

            4. 不得根据规则标题猜测规则内容。
               规则的具体含义、数值、比例、适用条件和限制，
               必须以数据库实际提供的内容为准。

            5. 不得将一般穿搭经验伪装成数据库明确规定的内容。
               数据库没有规定的数值、限制或结论，不得声称是数据库规定。

            6. 每条引用规则都应与当前穿搭建立明确联系：
               A. 指出图片中与规则相关的可见元素。
               B. 解释规则的哪项内容支持当前判断。
               C. 如果规则存在适用条件，应说明相关限制。

            7. 规则标题真实存在，并不意味着 AI 对规则的解释一定正确。
               必须同时考虑规则原文与实际穿搭是否相符，
               不能仅凭标题匹配就认定结论成立。

            8. 如果图片信息不足以判断某条规则是否适用，
               应明确说明，不得为了引用规则而强行下结论。

            9. 数据库没有覆盖的问题，可以结合一般专业造型知识回答。
               但必须将一般专业判断与数据库规则依据区分开。

            10. 不同规则不能相互混淆。
                例如，色彩面积比例、邻近色搭配和互补色搭配属于不同概念，
                不能因为它们都涉及色彩，就将其解释为同一项规则。

            11. 如果没有直接相关的数据库规则，可以省略规则引用部分，
                不得编造规则标题或数据库内容。

            ==================== 三、专业造型分析 ====================

            【色彩分析】

            1. 综合考虑可见颜色的色相、明度、饱和度、
               对比关系和视觉重心。

            2. 对颜色关系的判断应与图片实际呈现的颜色相符。
               不能仅凭颜色名称，就断言色环夹角符合某个精确数值。

            3. 对于颜色相近的服装，可以分析整体协调性、
               明暗层次和视觉过渡，但不要随意编造色彩参数。

            【版型与比例】

            1. 根据实际可见的服装廓形、松紧关系、衣长、
               腰线和垂坠感进行分析。

            2. 上松下紧、上紧下松等属于常见搭配思路，
               不是适用于所有人的绝对规则。

            3. 全身宽松也可能形成协调的休闲、街头或松弛风格。
               应结合实际服装、整体轮廓和用户需求判断，
               不得机械套用某一种版型规则。

            4. 不得把“衣服宽松”直接等同于“显胖”，
               也不得把“露腿”直接等同于“一定显高”。

            【风格分析】

            1. 结合实际可见的服装、鞋履、配饰和色彩判断风格。

            2. “韩系休闲风”“甜美风”“通勤风”等属于审美归类，
               应根据实际元素判断，不要将主观风格描述当作客观事实。

            3. 不得把未入镜的单品当作已经确认的搭配元素。

            【场景分析】

            1. 如果用户提供了景点或旅行行程，应结合用户实际提供的信息
               判断穿搭的场景适配度。

            2. 不得虚构景点的背景颜色、天气、环境条件或用户未提供的行程。

            3. 如果缺少必要信息，可以提出有条件的建议，
               不得把假设的场景描述为真实情况。

            ==================== 四、优化建议 ====================

            1. 尊重用户已经搭配好的穿搭。
               默认优先保留现有上衣、裤子、裙子等主体服装。

            2. 优先考虑调整穿法、搭配顺序、鞋包、配饰和拍照方式。
               只有确有必要且符合用户需求时，才建议更换主体服装。

            3. 每条建议都应有明确目的，例如：
               改善视觉比例、协调色彩、调整视觉重心、
               强化风格或提升场景适配度。

            4. 建议必须结合图片中的实际服装结构。
               不得默认被遮挡的裙腰、裤腰、内搭长度、
               拉链位置或包身结构一定存在。

            5. 提出塞衣角、系腰带、调整拉链等建议之前，
               必须考虑图片是否能支持相关操作。
               无法确认时，应明确说明建议成立的条件。

            6. 如果建议增加配饰，应考虑现有服装图案、色彩和配饰数量，
               不要为了增加层次而无理由地堆叠装饰。

            7. 建议应具体、可执行，优先给出一至三条最有价值的建议。
               避免重复表达、大量罗列以及缺乏实际依据的建议。

            8. 如果当前穿搭已经协调，应明确肯定其优点。
               其他调整应作为可选方案，而不是强制修改要求。

            9. 如果某条建议只在特定条件下适用，
               必须说明条件，不得将其描述成所有情况下都可执行的方案。

            10. 应结合用户实际需求。
                用户重视舒适度时，应兼顾活动便利性；
                用户重视拍照效果时，可以关注色彩对比、视觉重心和场景协调。
                不要为了追求某一种效果而忽略用户已经表达的偏好。

            ==================== 五、回答结构 ====================

            根据用户问题灵活组织回答，不必机械输出所有章节。

            当用户上传完整穿搭图片并要求综合分析时，
            通常可以采用以下结构：

            【图片观察】

            只描述图片中能够确认的信息。
            对于被遮挡、未入镜或无法确认的内容，明确说明。

            【整体评价】

            分析当前穿搭的主要风格、色彩协调度、版型关系和视觉效果。
            区分可观察事实与专业推断。

            【数据库规则依据】

            只列出本次实际使用的数据库规则。
            使用《规则标题》的形式，解释规则原文与当前穿搭的具体联系。
            不得只罗列标题，也不得夸大规则能够支持的结论。

            【补充优化建议】

            提供一至三条有实际价值的建议。
            将一般穿搭建议与数据库规则依据区分开。
            对无法确认是否适用的建议，说明必要条件。

            【总结】

            简要概括穿搭的主要优点和最值得考虑的优化方向。

            如果用户只询问一个具体问题，可以直接回答，
            不必机械输出全部章节。

            如果用户明确要求只根据数据库回答，
            则只提供数据库内容能够支持的结论，
            并明确说明数据库没有覆盖或图片无法确认的部分。

            ==================== 六、最终质量检查 ====================

            在生成回答之前，请自行检查：

            1. 是否把图片中看不到的内容说成了事实？
            2. 是否把专业推断说成了确定结论？
            3. 引用的数据库规则是否真实存在？
            4. 对规则的解释是否符合数据库原文？
            5. 该规则是否真的适用于当前图片？
            6. 是否编造了数据库没有提供的数值、比例或限制？
            7. 优化建议是否与图片中的实际服装结构相符？
            8. 是否对被遮挡或无法确认的部分使用了条件式表达？
            9. 是否提出了不必要的更换服装或增加配饰建议？
            10. 是否有重复、空泛或缺乏依据的结论？

            如果发现问题，应在输出前修正。

            请始终以真实图片、实际数据库内容和用户需求为依据，
            提供自然、客观、专业、清晰且可执行的回答。

            ==================== 七、规则适用性与建议克制 ====================

            1. 数据库规则必须同时满足“标题真实存在”和“内容适用于当前图片”，
               才能作为当前穿搭的明确依据。

            2. 对数据库中存在精确数值的规则，不得仅凭视觉印象断言已经满足
               该数值要求。例如，不能仅凭颜色名称就确认色环夹角在30°以内。

            3. 如果图片无法支持精确判断，应将结论限定为视觉观感，
               并明确说明无法确认精确参数。

            4. 用户明确要求客观评价、不要刻意挑错时，
               应优先指出实际存在的优点和问题。
               如果没有明显问题，可以直接说明当前搭配已经协调，
               不必为了满足回答结构而强行提出修改建议。

            5. 优化建议应按照实际价值排序。
               优先提供最值得尝试的建议，而不是罗列大量可选单品。

            6. 对于可有可无的配饰调整，应明确标记为可选项，
               不得暗示当前穿搭必须增加配饰才能达到较好的效果。

            7. 不要无依据地给出精确调整数值。
               例如，不能在没有明确依据的情况下断言拉链必须打开2-3cm，
               或者衣角必须塞入三分之一。

            8. 建议应尽可能简单。
               如果通过保持现状就能达到较好的穿搭效果，
               应优先建议保持现状，而不是增加不必要的调整。
            """;


    /**
     * 数据库知识库提示词模板。
     */
    private static final String STYLIST_KNOWLEDGE_PROMPT = """
            以下内容来自 MySQL 数据库 basic_style_knowledge 表，
            是本次分析可以参考的穿搭知识库。

            使用要求：
            1. 以下记录是本次分析中引用数据库规则的唯一来源。
            2. 引用规则时，必须使用记录中的原始完整标题。
            3. 规则的含义、数值、条件和限制必须以实际记录为准。
            4. 不得声称数据库中存在本段未提供的规则。
            5. 不要求机械套用全部规则，只使用真正相关的规则。
            6. 可以使用一般专业造型知识补充分析，但不得伪装成数据库规则。
            7. 规则标题匹配不代表规则解释正确，必须核对规则内容与结论是否一致。
            8. 图片信息不足时，不得强行认定规则适用。

            ==================== 数据库穿搭知识库开始 ====================

            %s

            ==================== 数据库穿搭知识库结束 ====================

            请结合以上知识库、图片事实和用户实际需求进行分析。
            """;


    // =========================================================
    // 三、项目启动时加载 MySQL 穿搭知识库
    // =========================================================

    @PostConstruct
    public void loadKnowledge() {

        log.info("========== 开始加载穿搭知识库 ==========");

        try {

            List<Map<String, Object>> result =
                    basicStyleKnowledgeMapper.selectStyleRuleList();

            if (result == null || result.isEmpty()) {

                styleKnowledgeList = new ArrayList<>();
                knowledgeFullText = "";

                log.warn(
                        "穿搭知识库查询完成，但没有查到任何记录。"
                                + "请检查 basic_style_knowledge 表和数据库连接。"
                );

                return;
            }

            styleKnowledgeList = new ArrayList<>(result);

            StringBuilder sb = new StringBuilder();

            for (Map<String, Object> map : result) {

                String category = getMapValue(map, "category");
                String title = getMapValue(map, "ruleTitle");
                String content = getMapValue(map, "ruleContent");
                String suitableStyle = getMapValue(map, "suitableStyle");

                sb.append("【分类】")
                        .append(category)
                        .append("\n");

                sb.append("【规则标题】")
                        .append(title)
                        .append("\n");

                sb.append("【规则内容】")
                        .append(content)
                        .append("\n");

                sb.append("【适配风格】")
                        .append(suitableStyle)
                        .append("\n\n");
            }

            knowledgeFullText = sb.toString().trim();

            log.info("========== 穿搭知识库加载成功 ==========");
            log.info("数据库查询记录数：{}", result.size());
            log.info("知识库文本长度：{}", knowledgeFullText.length());

            // 打印规则标题和原文，便于人工核对。
            for (Map<String, Object> rule : result) {

                String title = getMapValue(rule, "ruleTitle");
                String content = getMapValue(rule, "ruleContent");

                log.info("========== 知识库规则 ==========");
                log.info("规则标题：{}", title);
                log.info("规则内容：{}", content);
            }

            if (!knowledgeFullText.isEmpty()) {

                int length = Math.min(500, knowledgeFullText.length());

                log.info(
                        "知识库内容预览：{}",
                        knowledgeFullText.substring(0, length)
                );

            } else {

                log.warn("知识库查询到了记录，但是拼接后的文本为空。");
            }

        } catch (Exception e) {

            styleKnowledgeList = new ArrayList<>();
            knowledgeFullText = "";

            log.error("穿搭知识库加载失败！", e);
        }
    }


    /**
     * 兼容 Map 键名大小写差异。
     */
    private String getMapValue(
            Map<String, Object> map,
            String expectedKey
    ) {

        if (map == null || expectedKey == null) {
            return "";
        }

        Object value = map.get(expectedKey);

        if (value != null) {
            return String.valueOf(value);
        }

        for (Map.Entry<String, Object> entry : map.entrySet()) {

            if (entry.getKey() != null
                    && entry.getKey().equalsIgnoreCase(expectedKey)) {

                Object actualValue = entry.getValue();

                return actualValue == null
                        ? ""
                        : String.valueOf(actualValue);
            }
        }

        return "";
    }


    /**
     * 获取当前知识库文本。
     */
    private String getKnowledgeText() {

        String knowledge = knowledgeFullText;

        log.info("========== AI 穿搭知识库检查 ==========");

        log.info(
                "知识库是否为空：{}",
                knowledge == null || knowledge.isBlank()
        );

        log.info(
                "传入提示词的知识库长度：{}",
                knowledge == null ? 0 : knowledge.length()
        );

        if (knowledge != null && !knowledge.isBlank()) {

            log.info(
                    "传入提示词的知识库开头：{}",
                    knowledge.substring(
                            0,
                            Math.min(300, knowledge.length())
                    )
            );
        }

        log.info("========== 知识库检查结束 ==========");

        if (knowledge == null || knowledge.isBlank()) {

            log.warn(
                    "当前穿搭知识库为空，AI 将无法引用数据库中的具体穿搭规则。"
            );

            return "当前数据库穿搭知识库为空。"
                    + "不得声称已经参考了数据库中的具体规则。"
                    + "可以根据专业造型知识提供一般建议。";
        }

        return knowledge;
    }


    /**
     * 构建统一的专业造型系统提示词。
     */
    private String buildStylistSystemPrompt() {

        String knowledge = getKnowledgeText();

        return STYLIST_SYSTEM_PROMPT
                + "\n\n"
                + STYLIST_KNOWLEDGE_PROMPT.formatted(knowledge);
    }


    // =========================================================
    // 四、知识库规则引用审计
    // =========================================================

    /**
     * 规范化规则标题，避免格式差异导致匹配失败。
     *
     * 例如：
     * 上紧下松／上松下紧
     * 上紧下松/上松下紧
     *
     * 规范化后可以匹配。
     */
    private String normalizeRuleTitle(String title) {

        if (title == null) {
            return "";
        }

        return title.trim()
                .replace('／', '/')
                .replace('｜', '|')
                .replace('：', ':')
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }


    /**
     * 检查 AI 回答中书名号引用的内容是否匹配真实知识库标题。
     *
     * 注意：
     * 1. 这是辅助审计，不会自动修改 AI 回答。
     * 2. 书名号也可能用于风格名称等其他内容，因此未匹配项需要人工复核。
     * 3. 本方法只能检查标题是否存在，不能完整判断自然语言语义是否正确。
     */
    private void auditKnowledgeRuleReferences(String answer) {

        if (answer == null || answer.isBlank()) {
            log.warn("AI 回答为空，跳过知识库引用审计。");
            return;
        }

        List<Map<String, Object>> rules = styleKnowledgeList;

        if (rules == null || rules.isEmpty()) {
            log.warn("当前知识库没有规则记录，无法核验 AI 的知识库引用。");
            return;
        }

        // 建立真实规则标题与数据库原文的映射。
        Map<String, String> knowledgeMap = new LinkedHashMap<>();

        for (Map<String, Object> rule : rules) {

            if (rule == null) {
                continue;
            }

            String title = getMapValue(rule, "ruleTitle");
            String content = getMapValue(rule, "ruleContent");

            if (title != null && !title.isBlank()) {

                knowledgeMap.put(
                        title.trim(),
                        content == null ? "" : content.trim()
                );
            }
        }

        log.info("========== 知识库引用审计开始 ==========");
        log.info("本次知识库可核验规则数量：{}", knowledgeMap.size());

        // 提取 AI 回答中《规则标题》形式的内容。
        Pattern pattern = Pattern.compile("《([^《》]+)》");
        Matcher matcher = pattern.matcher(answer);

        Set<String> citedTitles = new LinkedHashSet<>();

        while (matcher.find()) {

            String title = matcher.group(1).trim();

            if (!title.isEmpty()) {
                citedTitles.add(title);
            }
        }

        if (citedTitles.isEmpty()) {

            log.info("AI 回答中没有发现书名号形式的标题引用。");
            log.info("========== 知识库引用审计结束 ==========");

            return;
        }

        List<String> matchedTitles = new ArrayList<>();
        List<String> unmatchedTitles = new ArrayList<>();

        // 核对标题，并输出数据库原文。
        for (String citedTitle : citedTitles) {

            // 修改点：使用规范化后的标题进行匹配。
            String matchedTitle = knowledgeMap.keySet()
                    .stream()
                    .filter(dbTitle ->
                            normalizeRuleTitle(dbTitle)
                                    .equals(normalizeRuleTitle(citedTitle))
                    )
                    .findFirst()
                    .orElse(null);

            if (matchedTitle != null) {

                // 记录数据库中的原始标题，而不是 AI 返回的标题。
                matchedTitles.add(matchedTitle);

                log.info("----------------------------------------");
                log.info("核验状态：标题匹配成功");
                log.info("规则标题：{}", matchedTitle);

                log.info(
                        "数据库规则原文：{}",
                        knowledgeMap.get(matchedTitle)
                );

                log.info(
                        "AI 回答包含该标题。"
                                + "请结合上方 AI 完整回答正文人工核对语义。"
                );

            } else {

                unmatchedTitles.add(citedTitle);

                log.warn("----------------------------------------");
                log.warn("核验状态：标题匹配失败");
                log.warn("未找到对应数据库规则：{}", citedTitle);
            }
        }

        log.info("匹配成功的规则标题：{}", matchedTitles);

        if (!unmatchedTitles.isEmpty()) {

            log.warn(
                    "AI 回答中存在未匹配到知识库的标题：{}",
                    unmatchedTitles
            );

        } else {

            log.info("AI 回答中的书名号标题均与知识库标题匹配。");
        }

        log.info("========== 知识库引用审计结束 ==========");
    }


    // =========================================================
    // 五、SSE 穿搭评估接口
    // =========================================================

    public void streamChat(
            StylistChatSseDTO dto,
            SseEmitter emitter
    ) {

        AtomicBoolean finished = new AtomicBoolean(false);

        registerEmitterCallbacks(
                emitter,
                finished,
                "SSE 穿搭评估"
        );

        Thread thread = new Thread(() -> {

            try {

                if (dto == null) {

                    sendMsg(
                            emitter,
                            finished,
                            "参数错误：请求参数不能为空"
                    );

                    safeComplete(emitter, finished);
                    return;
                }

                String userText = dto.getUserQuery() == null
                        ? ""
                        : dto.getUserQuery();

                List<String> imgList = dto.getImgBase64List();

                if (imgList == null) {
                    imgList = new ArrayList<>();
                }

                // 所有模式共用同一套专业提示词。
                String sysPrompt = buildStylistSystemPrompt();

                String sceneInfoText;

                // -------------------------------------------------
                // 情况一：根据景点评估穿搭
                // -------------------------------------------------

                if (dto.getAttractionId() != null) {

                    String ootdDesc =
                            attractionMapper.getOotdDescById(
                                    dto.getAttractionId()
                            );

                    if (ootdDesc == null || ootdDesc.isBlank()) {
                        ootdDesc = "【暂无相关描述】";
                    }

                    sceneInfoText =
                            "【评估模式】景点 OOTD 评估\n"
                                    + "请结合以下景点信息分析穿搭的风格协调度、"
                                    + "色彩适配度和拍照效果。\n\n"
                                    + "【景点 OOTD 参考信息】\n"
                                    + ootdDesc
                                    + "\n\n【用户提问】\n"
                                    + userText;

                    log.info("当前使用景点 OOTD 评估模式");

                }

                // -------------------------------------------------
                // 情况二：根据旅行行程评估穿搭
                // -------------------------------------------------

                else if (dto.getTravelSchedule() != null) {

                    TravelScheduleDTO schedule =
                            dto.getTravelSchedule();

                    String scheduleText =
                            objectMapper.writeValueAsString(schedule);

                    sceneInfoText =
                            "【评估模式】旅行行程穿搭评估\n"
                                    + "请结合用户提供的行程，"
                                    + "分析穿搭与行程场景的适配程度。\n\n"
                                    + "【本次行程信息】\n"
                                    + scheduleText
                                    + "\n\n【用户提问】\n"
                                    + userText;

                    log.info("当前使用旅行行程评估模式");

                } else {

                    sendMsg(
                            emitter,
                            finished,
                            "参数错误：需要传入 travelSchedule 或者 attractionId"
                    );

                    safeComplete(emitter, finished);
                    return;
                }

                // =================================================
                // 构建大模型 messages
                // =================================================

                List<Map<String, Object>> messages =
                        new ArrayList<>();

                Map<String, Object> sysMsg =
                        new HashMap<>();

                sysMsg.put("role", "system");
                sysMsg.put("content", sysPrompt);

                messages.add(sysMsg);

                List<Map<String, Object>> userContent =
                        new ArrayList<>();

                Map<String, Object> textItem =
                        new HashMap<>();

                textItem.put("type", "text");
                textItem.put("text", sceneInfoText);

                userContent.add(textItem);

                // -------------------------------------------------
                // Base64 图片处理
                // -------------------------------------------------

                for (String imgBase64 : imgList) {

                    if (imgBase64 == null || imgBase64.isBlank()) {
                        continue;
                    }

                    Map<String, Object> imgItem =
                            new HashMap<>();

                    imgItem.put("type", "image_url");

                    Map<String, String> urlObj =
                            new HashMap<>();

                    String fixedImg = imgBase64.replace(
                            "data:image/jpg;base64,",
                            "data:image/jpeg;base64,"
                    );

                    urlObj.put("url", fixedImg);

                    imgItem.put("image_url", urlObj);

                    userContent.add(imgItem);
                }

                Map<String, Object> userMsg =
                        new HashMap<>();

                userMsg.put("role", "user");
                userMsg.put("content", userContent);

                messages.add(userMsg);

                // =================================================
                // 构建请求体并调用大模型
                // =================================================

                Map<String, Object> reqBody =
                        new HashMap<>();

                reqBody.put("model", endpointId);
                reqBody.put("messages", messages);
                reqBody.put("stream", true);

                String jsonBody =
                        objectMapper.writeValueAsString(reqBody);

                log.info(
                        "准备调用大模型，systemPrompt 长度：{}，userText 长度：{}",
                        sysPrompt.length(),
                        sceneInfoText.length()
                );

                log.info(
                        "请求是否包含数据库知识库：{}",
                        sysPrompt.contains(getKnowledgeText())
                );

                Request request = buildRequest(jsonBody);

                consumeSseResponse(
                        request,
                        emitter,
                        finished,
                        "穿搭评估"
                );

            } catch (JsonProcessingException e) {

                log.error(
                        "穿搭评估请求参数序列化异常",
                        e
                );

                sendMsg(
                        emitter,
                        finished,
                        "参数序列化异常"
                );

                safeComplete(emitter, finished);

            } catch (Exception e) {

                log.error(
                        "造型搭配接口异常",
                        e
                );

                sendMsg(
                        emitter,
                        finished,
                        "服务异常，请稍后重试"
                );

                safeComplete(emitter, finished);
            }

        }, "stylist-stream-chat");

        thread.start();
    }


    // =========================================================
    // 六、图片 + 文字上传接口
    // =========================================================

    public void chatSse(
            String msg,
            MultipartFile imageFile,
            SseEmitter emitter
    ) {

        AtomicBoolean finished = new AtomicBoolean(false);

        registerEmitterCallbacks(
                emitter,
                finished,
                "图片 OOTD"
        );

        Thread thread = new Thread(() -> {

            try {

                String imageUrl = null;

                // -------------------------------------------------
                // 上传图片到 TOS，并生成预签名 URL
                // -------------------------------------------------

                if (imageFile != null && !imageFile.isEmpty()) {

                    String objectKey = tosUtil.upload(imageFile);

                    if (objectKey == null || objectKey.isBlank()) {

                        throw new IllegalStateException(
                                "图片上传失败，未获取到 objectKey"
                        );
                    }

                    log.info(
                            "图片上传成功，objectKey={}",
                            objectKey
                    );

                    imageUrl =
                            tosUtil.generatePresignedUrl(objectKey);

                    if (imageUrl == null || imageUrl.isBlank()) {

                        throw new IllegalStateException(
                                "图片预签名 URL 生成失败"
                        );
                    }

                    // 不打印完整预签名 URL，避免泄露临时访问凭证。
                    log.info("图片预签名 URL 生成成功");
                }

                // -------------------------------------------------
                // 构建统一的专业系统提示词
                // -------------------------------------------------

                String systemPrompt =
                        buildStylistSystemPrompt();

                log.info(
                        "图片穿搭评估：知识库长度={}",
                        knowledgeFullText.length()
                );

                log.info(
                        "图片穿搭评估：请求是否包含数据库知识库={}",
                        systemPrompt.contains(knowledgeFullText)
                );

                log.info(
                        "System 提示词长度：{}",
                        systemPrompt.length()
                );

                log.info(
                        "System 提示词是否包含 60-30-10 规则：{}",
                        systemPrompt.contains("穿搭60-30-10色彩分配法则")
                );

                // =================================================
                // 构建 messages
                // =================================================

                List<Map<String, Object>> messages =
                        new ArrayList<>();

                Map<String, Object> sysMsg =
                        new HashMap<>();

                sysMsg.put("role", "system");
                sysMsg.put("content", systemPrompt);

                messages.add(sysMsg);

                List<Map<String, Object>> userContent =
                        new ArrayList<>();

                // -------------------------------------------------
                // 将预签名 URL 作为图片传给 AI
                // -------------------------------------------------

                if (imageUrl != null && !imageUrl.isBlank()) {

                    Map<String, Object> imgItem =
                            new HashMap<>();

                    imgItem.put("type", "image_url");

                    Map<String, String> imgUrlObj =
                            new HashMap<>();

                    imgUrlObj.put("url", imageUrl);

                    imgItem.put("image_url", imgUrlObj);

                    userContent.add(imgItem);
                }

                // -------------------------------------------------
                // 用户文字
                // -------------------------------------------------

                Map<String, Object> textItem =
                        new HashMap<>();

                textItem.put("type", "text");

                textItem.put(
                        "text",
                        msg == null ? "" : msg
                );

                userContent.add(textItem);

                Map<String, Object> userMsg =
                        new HashMap<>();

                userMsg.put("role", "user");
                userMsg.put("content", userContent);

                messages.add(userMsg);

                // =================================================
                // 构建请求体
                // =================================================

                Map<String, Object> reqBody =
                        new HashMap<>();

                reqBody.put("model", endpointId);
                reqBody.put("messages", messages);
                reqBody.put("stream", true);

                String jsonBody =
                        objectMapper.writeValueAsString(reqBody);

                Request request = buildRequest(jsonBody);

                // =================================================
                // 调用大模型
                // =================================================

                consumeSseResponse(
                        request,
                        emitter,
                        finished,
                        "图片 OOTD"
                );

            } catch (Exception e) {

                log.error(
                        "图片 OOTD 处理异常",
                        e
                );

                sendMsg(
                        emitter,
                        finished,
                        "服务异常，请稍后重试"
                );

                safeComplete(emitter, finished);
            }

        }, "stylist-image-chat");

        thread.start();
    }


    // =========================================================
    // 七、统一构建方舟 API 请求
    // =========================================================

    private Request buildRequest(String jsonBody) {

        String normalizedBaseUrl =
                baseUrl.endsWith("/")
                        ? baseUrl.substring(
                        0,
                        baseUrl.length() - 1
                )
                        : baseUrl;

        return new Request.Builder()
                .url(
                        normalizedBaseUrl
                                + "/chat/completions"
                )
                .header(
                        "Authorization",
                        "Bearer " + arkApiKey
                )
                .header(
                        "Content-Type",
                        "application/json"
                )
                .post(
                        RequestBody.create(
                                jsonBody,
                                MediaType.parse(
                                        "application/json; charset=utf-8"
                                )
                        )
                )
                .build();
    }


    // =========================================================
    // 八、统一读取 SSE 流
    // =========================================================

    private void consumeSseResponse(
            Request request,
            SseEmitter emitter,
            AtomicBoolean finished,
            String sceneName
    ) throws IOException {

        try (Response resp =
                     okHttpClient.newCall(request).execute()) {

            // -------------------------------------------------
            // 处理 HTTP 错误
            // -------------------------------------------------

            if (!resp.isSuccessful() || resp.body() == null) {

                String errBody = resp.body() == null
                        ? "响应体为空"
                        : resp.body().string();

                log.error(
                        "{} 方舟接口调用失败，code={}，resp={}",
                        sceneName,
                        resp.code(),
                        errBody
                );

                String userMessage;

                if (errBody.contains("AccountOverdueError")) {

                    userMessage =
                            "AI 服务账户存在欠费，请检查火山引擎账户余额或账单。";

                } else if (resp.code() == 401
                        || resp.code() == 403) {

                    userMessage =
                            "AI 服务鉴权或账户状态异常，请检查服务配置及账户状态。";

                } else if (resp.code() == 400) {

                    userMessage =
                            "AI 请求参数有误，请检查模型配置、图片格式及请求内容。";

                } else if (resp.code() == 429) {

                    userMessage =
                            "AI 服务请求过于频繁，请稍后重试。";

                } else {

                    userMessage =
                            "AI 服务暂时不可用，请稍后重试。";
                }

                sendMsg(
                        emitter,
                        finished,
                        userMessage
                );

                safeComplete(emitter, finished);
                return;
            }

            log.info(
                    "{} 方舟接口调用成功，开始读取 SSE 流",
                    sceneName
            );

            // -------------------------------------------------
            // 读取 SSE 流
            // -------------------------------------------------

            try (BufferedReader reader =
                         new BufferedReader(
                                 new InputStreamReader(
                                         resp.body().byteStream(),
                                         StandardCharsets.UTF_8
                                 )
                         )) {

                String line;

                StringBuilder fullAnswer =
                        new StringBuilder();

                while ((line = reader.readLine()) != null) {

                    if (finished.get()) {

                        log.info(
                                "{} SSE 连接已结束，停止读取",
                                sceneName
                        );

                        break;
                    }

                    if (!line.startsWith("data:")) {
                        continue;
                    }

                    String data =
                            line.substring(5).trim();

                    if (data.isEmpty()) {
                        continue;
                    }

                    if ("[DONE]".equals(data)) {
                        break;
                    }

                    Map<String, Object> chunk;

                    try {

                        chunk = objectMapper.readValue(
                                data,
                                Map.class
                        );

                    } catch (JsonProcessingException e) {

                        log.warn(
                                "{} SSE 数据解析失败，跳过当前数据块",
                                sceneName,
                                e
                        );

                        continue;
                    }

                    Object choicesObject =
                            chunk.get("choices");

                    if (!(choicesObject instanceof List<?> choices)
                            || choices.isEmpty()) {

                        continue;
                    }

                    Object choiceObject =
                            choices.get(0);

                    if (!(choiceObject instanceof Map<?, ?> choice)) {
                        continue;
                    }

                    Object deltaObject =
                            choice.get("delta");

                    if (!(deltaObject instanceof Map<?, ?> delta)) {
                        continue;
                    }

                    Object contentObject =
                            delta.get("content");

                    if (!(contentObject instanceof String content)
                            || content.isEmpty()) {

                        continue;
                    }

                    // 保存 AI 的完整回答。
                    fullAnswer.append(content);

                    // 同时将分片发送给前端。
                    boolean sendSuccess =
                            sendMsg(
                                    emitter,
                                    finished,
                                    content
                            );

                    if (!sendSuccess) {
                        break;
                    }
                }

                // -------------------------------------------------
                // 打印 AI 完整回答
                // -------------------------------------------------

                String answer = fullAnswer.toString();

                log.info(
                        "{} AI 回答接收完成，回答长度：{}",
                        sceneName,
                        answer.length()
                );

                log.info(
                        "{} AI 完整回答正文：\n{}",
                        sceneName,
                        answer
                );

                // -------------------------------------------------
                // 审计知识库引用
                // -------------------------------------------------

                auditKnowledgeRuleReferences(answer);
            }

            safeComplete(emitter, finished);
        }
    }


    // =========================================================
    // 九、注册 SSE 生命周期回调
    // =========================================================

    private void registerEmitterCallbacks(
            SseEmitter emitter,
            AtomicBoolean finished,
            String sceneName
    ) {

        emitter.onCompletion(() -> {

            finished.set(true);

            log.info(
                    "{} SSE 连接正常完成或已关闭",
                    sceneName
            );
        });

        emitter.onError(ex -> {

            finished.set(true);

            log.warn(
                    "{} SSE 连接异常：{}",
                    sceneName,
                    ex.getMessage()
            );
        });

        emitter.onTimeout(() -> {

            finished.set(true);

            log.warn(
                    "{} SSE 连接超时",
                    sceneName
            );
        });
    }


    // =========================================================
    // 十、SSE 发送工具方法
    // =========================================================

    private boolean sendMsg(
            SseEmitter emitter,
            AtomicBoolean finished,
            String text
    ) {

        if (finished.get()) {
            return false;
        }

        try {

            emitter.send(
                    SseEmitter.event()
                            .data(text)
            );

            return true;

        } catch (Exception e) {

            log.warn(
                    "SSE 消息发送失败，连接可能已关闭：{}",
                    e.getMessage()
            );

            finished.set(true);

            return false;
        }
    }


    // =========================================================
    // 十一、安全结束 SSE
    // =========================================================

    private void safeComplete(
            SseEmitter emitter,
            AtomicBoolean finished
    ) {

        if (finished.compareAndSet(false, true)) {

            try {

                emitter.complete();

            } catch (Exception e) {

                log.debug(
                        "结束 SSE 连接时发生异常：{}",
                        e.getMessage()
                );
            }
        }
    }


    /**
     * 多图综合评估。Controller 提交 msg、travelSchedule 和同名 images 文件列表。
     * 所有图片上传 TOS 后，在同一次模型请求中与完整行程一起分析。
     */
    public void multiImageChatSse(
            String msg,
            String travelSchedule,
            List<MultipartFile> images,
            SseEmitter emitter
    ) throws IOException {
        if (images == null || images.isEmpty() || images.size() > 9) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "请选择 1 到 9 张穿搭照片");
        }
        if (msg == null || msg.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "用户问题不能为空");
        }
        if (travelSchedule == null || travelSchedule.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "行程不能为空");
        }

        com.fasterxml.jackson.databind.JsonNode schedule;
        try {
            schedule = objectMapper.readTree(travelSchedule);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "行程必须是有效 JSON", e);
        }
        com.fasterxml.jackson.databind.JsonNode plans =
                schedule == null ? null : schedule.get("dailyPlanList");
        if (plans == null || !plans.isArray() || plans.size() == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "请提供包含景点的 dailyPlanList 行程");
        }

        // Controller 返回后，Servlet 可能清理 MultipartFile 临时文件。
        // 校验全部文件后，在请求线程中复制，再交给后台线程上传。
        long total = 0;
        for (int i = 0; i < images.size(); i++) {
            MultipartFile image = images.get(i);
            if (image == null || image.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "照片" + (i + 1) + "为空");
            }
            String mime = image.getContentType();
            if (mime == null || !mime.toLowerCase(Locale.ROOT).startsWith("image/")) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "照片" + (i + 1) + "格式不正确");
            }
            if (image.getSize() > 10L * 1024 * 1024) {
                throw new ResponseStatusException(
                        HttpStatus.PAYLOAD_TOO_LARGE, "单张照片不能超过 10MB");
            }
            total += image.getSize();
            if (total > 40L * 1024 * 1024) {
                throw new ResponseStatusException(
                        HttpStatus.PAYLOAD_TOO_LARGE, "照片合计不能超过 40MB");
            }
        }
        List<MultipartFile> savedImages = new ArrayList<>();
        for (MultipartFile image : images) {
            savedImages.add(new SavedImage(image));
        }
        String scheduleText = objectMapper.writeValueAsString(schedule);
        AtomicBoolean finished = new AtomicBoolean(false);
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        String sceneName = "多图行程穿搭[" + requestId + "]";
        registerEmitterCallbacks(emitter, finished, sceneName);
        Runnable stopHeartbeat = startMultiImageHeartbeat(emitter, finished, sceneName);
        log.info("{} 已接收 {} 张照片，准备上传 TOS", sceneName, savedImages.size());

        Thread worker = new Thread(() -> {
            try {
                String instruction = """
                        请同时比较本次全部照片与完整行程，进行综合穿搭匹配。
                        图片前的照片1、照片2等标签对应用户选图顺序，回答必须沿用编号。
                        如果多图像同一套服装的不同角度，请说明，不要强行认定是不同套。

                        要求：
                        1. 先识别每张照片可见的主体服装、颜色、风格；看不清的部分注明无法确认。
                        2. 比较各套穿搭与各景点的适配程度，推荐时说明具体理由和不足。
                        3. 按每天的路线安排穿搭，同一天优先兼顾多个景点，减少不必要换装。
                        4. 同一套可以适合多个景点；不合适的穿搭不必强行分配。
                        5. 优先保留主体服装，建议配饰、包包、鞋履或穿法调整，说明风格、颜色及作用。
                        6. 确需更换主体单品时，说明原因和替代方向，不要为挑错而建议换装。
                        7. 分清确有必要的调整和可选优化。建议新增的物品不得说成照片里已有。
                        8. 不得虚构天气、景点背景颜色、活动条件。资料不足时给出有条件的建议。
                        9. 数据库规则仅引用实际提供且相关的内容，保留系统提示词的事实与引用要求。

                        输出结构：
                        【各照片穿搭识别】
                        【穿搭与景点匹配】
                        【每日穿搭安排】
                        【各套穿搭的完善建议】

                        用户问题：
                        %s

                        行程数据：
                        %s
                        """.formatted(msg, scheduleText);

                List<Map<String, Object>> content = new ArrayList<>();
                content.add(Map.of("type", "text", "text", instruction));
                for (int i = 0; i < savedImages.size(); i++) {
                    if (finished.get()) return;
                    String objectKey = tosUtil.upload(savedImages.get(i));
                    if (objectKey == null || objectKey.isBlank()) {
                        throw new IllegalStateException("照片" + (i + 1) + "上传 TOS 失败");
                    }
                    String imageUrl = tosUtil.generatePresignedUrl(objectKey);
                    if (imageUrl == null || imageUrl.isBlank()) {
                        throw new IllegalStateException("照片" + (i + 1) + "签名地址生成失败");
                    }
                    content.add(Map.of("type", "text", "text", "照片" + (i + 1) + "："));
                    content.add(Map.of(
                            "type", "image_url", "image_url", Map.of("url", imageUrl)));
                    log.info("{} 照片 {}/{} 上传完成", sceneName, i + 1, savedImages.size());
                }
                if (finished.get()) return;

                List<Map<String, Object>> messages = new ArrayList<>();
                messages.add(Map.of("role", "system", "content", buildStylistSystemPrompt()));
                messages.add(Map.of("role", "user", "content", content));
                Map<String, Object> body = new HashMap<>();
                body.put("model", endpointId);
                body.put("messages", messages);
                body.put("stream", true);
                Request request = buildRequest(objectMapper.writeValueAsString(body));
                log.info("{} 全部图片上传完成，开始请求方舟", sceneName);
                consumeSseResponse(request, emitter, finished, sceneName);
            } catch (Exception e) {
                log.error("{} 处理失败", sceneName, e);
                sendMsg(emitter, finished, "多图穿搭评估失败，请稍后重试。");
                safeComplete(emitter, finished);
            } finally {
                stopHeartbeat.run();
            }
        }, "stylist-multi-image-" + requestId);
        worker.start();
    }

    /** 与 Servlet 临时文件生命周期无关的图片副本，复用现有 TosUtil。 */
    private static final class SavedImage implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] bytes;

        private SavedImage(MultipartFile source) throws IOException {
            name = source.getName();
            originalFilename = source.getOriginalFilename();
            contentType = source.getContentType();
            bytes = source.getBytes();
        }

        @Override
        public String getName() { return name; }

        @Override
        public String getOriginalFilename() { return originalFilename; }

        @Override
        public String getContentType() { return contentType; }

        @Override
        public boolean isEmpty() { return bytes.length == 0; }

        @Override
        public long getSize() { return bytes.length; }

        @Override
        public byte[] getBytes() { return bytes.clone(); }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), bytes);
        }
    }
}
