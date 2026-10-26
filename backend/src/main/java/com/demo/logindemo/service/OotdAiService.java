package com.demo.logindemo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OotdAiService {

    // 注意！地址换成 chat/completions，不是 responses！
    private static final String API_URL = "https://ark.cn-beijing.volces.com/api/v1/chat/completions";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.agent.ark-api-key}")
    private String apiKey;

    /**
     * @param pureBase64 纯base64，不带 data:image 前缀
     * @param userText   提示词
     * @return SSE流
     */
    public SseEmitter streamAnalyze(String pureBase64, String userText) {
        SseEmitter emitter = new SseEmitter(0L);

        // ========== 构造报文：chat/completions 格式，支持图片base64 data uri ==========
        Map<String, Object> reqBody = new HashMap<>();
        reqBody.put("model", "ep-20261018205924-xqrd6");
        reqBody.put("stream", true);

        List<Map<String, Object>> messages = new ArrayList<>();

        // system 角色
        Map<String, Object> sysMsg = new HashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", "你是专业OOTD穿搭分析顾问，仔细观察图片穿搭，输出风格定位、色彩搭配、适配场景、优化建议，文字简洁。");
        messages.add(sysMsg);

        // user 消息，多模态 content 数组
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");

        List<Map<String, Object>> contentList = new ArrayList<>();

        // 文本部分
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", userText);
        contentList.add(textPart);

        // 图片部分：拼接成 data URI！！核心改动在这里
        String dataUri = "data:image/jpeg;base64," + pureBase64;
        Map<String, Object> imgPart = new HashMap<>();
        imgPart.put("type", "image_url");
        Map<String, String> imageUrlObj = new HashMap<>();
        imageUrlObj.put("url", dataUri);
        imgPart.put("image_url", imageUrlObj);
        contentList.add(imgPart);

        userMsg.put("content", contentList);
        messages.add(userMsg);

        reqBody.put("messages", messages);
        // =====================================================================

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String json = objectMapper.writeValueAsString(reqBody);
                URL url = new URL(API_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                conn.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6);
                        if ("[DONE]".equals(data)) {
                            break;
                        }
                        JsonNode node = objectMapper.readTree(data);
                        // chat/completions 的返回路径： choices[0].delta.content
                        JsonNode deltaContent = node.at("/choices/0/delta/content");
                        if (!deltaContent.isMissingNode() && deltaContent.asText().length() > 0) {
                            emitter.send(SseEmitter.event().data(deltaContent.asText()));
                        }
                    }
                }
                br.close();
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
                e.printStackTrace();
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();

        return emitter;
    }
    public String testSimple() {
        Map<String, Object> reqBody = new HashMap<>();
        reqBody.put("model", "ep-20261018205924-xqrd6");
        reqBody.put("stream", false);

        List<Map<String, Object>> messages = new ArrayList<>();

        Map<String, Object> sysMsg = new HashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", "你是穿搭顾问，回答简短");
        messages.add(sysMsg);

        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", "描述一下休闲风穿搭");
        messages.add(userMsg);

        reqBody.put("messages", messages);

        try {
            String json = objectMapper.writeValueAsString(reqBody);
            URL url = new URL("https://ark.cn-beijing.volces.com/api/v1/chat/completions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            conn.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
            int code = conn.getResponseCode();
            BufferedReader br;
            if (code >= 400) {
                br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
            } else {
                br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            }
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();
            conn.disconnect();

            JsonNode node = objectMapper.readTree(sb.toString());
            String result = node.at("/choices/0/message/content").asText();
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return "调用出错：" + e.getMessage();
        }
    }
}