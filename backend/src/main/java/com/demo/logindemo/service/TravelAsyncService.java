package com.demo.logindemo.service;

import com.demo.logindemo.dto.TaskDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class TravelAsyncService {

    private final TravelPlanService travelPlanService;

    // 在这里加上 @Lazy！！
    public TravelAsyncService(@Lazy TravelPlanService travelPlanService) {
        this.travelPlanService = travelPlanService;
    }

    @Async
    public void doGenerateAsync(String taskId, Map<String, Object> param, ConcurrentHashMap<String, TaskDTO> taskMap) {
        TaskDTO task = taskMap.get(taskId);
        if (task == null) {
            log.warn("taskId={} 不存在", taskId);
            return;
        }
        try {
            String planText = travelPlanService.getPlan(param);
            task.setStatus("SUCCESS");
            task.setResult(planText);
            log.info("taskId={} 生成成功", taskId);
        } catch (Exception e) {
            log.error("taskId={} 生成失败", taskId, e);
            task.setStatus("FAIL");
            task.setErrorMsg(e.getMessage());
        }
    }
}