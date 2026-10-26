package com.demo.logindemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.logindemo.dto.TaskDTO;
import com.demo.logindemo.entity.Attraction;
import com.demo.logindemo.entity.Restaurant;
import com.demo.logindemo.mapper.AttractionMapper;
import com.demo.logindemo.mapper.RestaurantMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.beans.factory.annotation.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class TravelPlanService {

    @Value("${agent.ark-api-key}")
    private String arkApiKey;

    @Value("${agent.endpoint}")
    private String endpointId;

    @Value("${agent.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TravelAsyncService travelAsyncService;

    // ===== 成员变量：两个mapper =====
    private final RestaurantMapper restaurantMapper;
    private final AttractionMapper attractionMapper;

    // ===== 构造注入 =====
    public TravelPlanService(RestTemplate restTemplate,
                             TravelAsyncService travelAsyncService,
                             RestaurantMapper restaurantMapper,
                             AttractionMapper attractionMapper) {
        this.restTemplate = restTemplate;
        this.travelAsyncService = travelAsyncService;
        this.restaurantMapper = restaurantMapper;
        this.attractionMapper = attractionMapper;
    }

    // 内存任务缓存
    public static final ConcurrentHashMap<String, TaskDTO> taskMap = new ConcurrentHashMap<>();

    public String getPlan(Map<String, Object> dto) {
        log.info("【1】收到生成任务入参: {}", dto);
        log.info("arkApiKey = [{}]", arkApiKey);
        log.info("endpointId = [{}]", endpointId);
        log.info("baseUrl = [{}]", baseUrl);

        String city = (String) dto.get("city");
        Boolean petFriendlyFlag = (Boolean) dto.get("petFriendlyFlag");
        String petStr = Boolean.TRUE.equals(petFriendlyFlag) ? "是" : "否";
        log.info("【2】准备查询数据库，城市={}, 是否宠物友好={}", city, petStr);

        // 查询餐厅
        LambdaQueryWrapper<Restaurant> restWrapper = new LambdaQueryWrapper<>();
        restWrapper.eq(Restaurant::getCity, city);
        restWrapper.eq(Restaurant::getPetFriendly, petStr);
        List<Restaurant> restaurantList = restaurantMapper.selectList(restWrapper);
        log.info("【3】数据库查询餐厅结果：{}", restaurantList);

        // 查询景点
        LambdaQueryWrapper<Attraction> attrWrapper = new LambdaQueryWrapper<>();
        attrWrapper.eq(Attraction::getCity, city);
        attrWrapper.eq(Attraction::getPetFriendly, petStr);
        List<Attraction> attractionList = attractionMapper.selectList(attrWrapper);
        log.info("【4】数据库查询景点结果：{}", attractionList);


        StringBuilder localDataSb = new StringBuilder();
        localDataSb.append("\n===== 本地库推荐（优先安排到行程）=====\n");
        localDataSb.append("【推荐餐厅】\n");
        for(Restaurant r : restaurantList){
            localDataSb.append("- ")
                    .append(r.getRestName())
                    .append("，区域：").append(r.getDistrict())
                    .append("，菜系：").append(r.getFoodType())
                    .append("，人均：").append(r.getAvgPrice())
                    .append("，推荐理由：").append(r.getRecommend())
                    .append("\n");
        }
        localDataSb.append("\n【推荐景点】\n");
        for(Attraction a : attractionList){
            localDataSb.append("- ")
                    .append(a.getAttrName())
                    .append("，区域：").append(a.getDistrict())
                    .append("，特色：").append(a.getFeature())
                    .append("，推荐理由：").append(a.getRecommend())
                    .append("\n");
        }
        log.info("【5】拼接后给到Agent的本地库文本：{}", localDataSb);

        String systemPrompt = "你是专业旅游规划师，输出精简，少氛围感描述，保留时间、点位、餐饮、出行要点，不要废话。优先使用本地库点位，可少量新增；景点标注门票，餐馆标注人均、特色菜，缺失写「暂无相关信息」，禁止编造，AI新增点位统一填暂无。每日至少午、晚两餐，就近规划减少远距离通勤，可适度加入购物打卡点。标记来源：【本地库推荐】或【AI生成推荐】，参考remark和宠物友好标识生成。格式：点位行首 `- `，配套文字不加；禁用数字序号，禁止输出经纬度，无多余开场白。";
        String userContent = String.format(
                "目的地：%s，天数：%s天，预算：%s元，偏好：%s，是否宠物友好：%s，额外备注：%s",
                dto.get("city"),
                dto.get("dayNum"),
                dto.get("budget"),
                dto.get("preferenceList"),
                dto.get("petFriendlyFlag"),
                dto.get("remark")
        );
        userContent += localDataSb;
        log.info("【6】最终发给Agent的完整Prompt：{}", userContent);
        log.info("【7】开始调用大模型Agent（火山方舟）");

        Map<String, Object> reqBody = new HashMap<>();
        reqBody.put("model", endpointId);

        Map<String, String> sysMsg = new HashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userContent);

        reqBody.put("messages", List.of(sysMsg, userMsg));
        reqBody.put("temperature", 0.7);

        try {
            String jsonBody = objectMapper.writeValueAsString(reqBody);
            String fullUrl = baseUrl + "/chat/completions";
            log.info("请求地址：{}", fullUrl);
            log.info("发给方舟的JSON：{}", jsonBody);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + arkApiKey);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<String> resp = restTemplate.exchange(
                    fullUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            String respBody = resp.getBody();
            log.info("方舟返回码：{}，返回原始报文：{}", resp.getStatusCode().value(), respBody);

            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("调用火山方舟失败，httpCode=" + resp.getStatusCode());
            }

            Map<String, Object> result = objectMapper.readValue(respBody, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String finalContent = (String) message.get("content");
            log.info("【8】Agent调用完成，拿到攻略结果");
            return finalContent;
        } catch (ResourceAccessException e) {
            log.error("网络层面异常（连接/超时）", e);
            throw new RuntimeException("网络连接方舟失败：" + e.getMessage());
        } catch (Exception e) {
            log.error("调用方舟异常", e);
            throw new RuntimeException(e);
        }
    }

    public String submitGenerateTask(Map<String, Object> param) {
        String taskId = UUID.randomUUID().toString();
        TaskDTO task = new TaskDTO();
        task.setStatus("PROCESSING");
        taskMap.put(taskId, task);
        travelAsyncService.doGenerateAsync(taskId, param, taskMap);
        return taskId;
    }

    public TaskDTO getTask(String taskId) {
        return taskMap.get(taskId);
    }
}