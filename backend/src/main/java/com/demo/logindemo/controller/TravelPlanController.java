package com.demo.logindemo.controller;

import com.demo.logindemo.dto.TaskDTO;
import com.demo.logindemo.dto.TravelPlanReqDTO;
import com.demo.logindemo.service.TravelPlanService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.HashMap;
import java.util.Map;

import java.util.HashMap;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/travel")
public class TravelPlanController {

    private final TravelPlanService travelPlanService;
    private final ObjectMapper objectMapper;

    public TravelPlanController(TravelPlanService travelPlanService, ObjectMapper objectMapper) {
        this.travelPlanService = travelPlanService;
        this.objectMapper = objectMapper;
    }

    // ===== 原来的老接口，完全保留不变！=====
    @PostMapping("/generate")
    public String generate(@RequestBody TravelPlanReqDTO dto) {
        Map<String,Object> map = objectMapper.convertValue(dto, Map.class);
        return travelPlanService.getPlan(map);
    }

    // ===== 新增1：提交异步任务，返回 taskId =====
    @PostMapping("/generateTask")
    public Map<String, Object> generateTask(@RequestBody TravelPlanReqDTO dto) {
        Map<String,Object> map = objectMapper.convertValue(dto, Map.class);
        String taskId = travelPlanService.submitGenerateTask(map);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("taskId", taskId);
        resp.put("msg", "任务已提交，后台正在生成攻略");
        return resp;
    }

    // ===== 新增2：轮询查询任务状态 =====
    @GetMapping("/task/{taskId}")
    public Map<String, Object> queryTask(@PathVariable String taskId) {
        TaskDTO task = travelPlanService.getTask(taskId);
        Map<String, Object> resp = new HashMap<>();
        if (task == null) {
            resp.put("success", false);
            resp.put("msg", "任务不存在");
            return resp;
        }

        resp.put("success", true);
        resp.put("status", task.getStatus());
        if ("SUCCESS".equals(task.getStatus())) {
            resp.put("data", task.getResult());
        } else if ("FAIL".equals(task.getStatus())) {
            resp.put("msg", "生成失败：" + task.getErrorMsg());
        } else {
            resp.put("msg", "正在生成，请稍后查询");
        }
        return resp;
    }
    @GetMapping("/getTask")
    public ResponseEntity<?> getTask(@RequestParam String taskId) {
        TaskDTO taskDTO = travelPlanService.getTask(taskId);
        if (taskDTO == null) {
            return ResponseEntity.status(404).body("任务不存在");
        }
        Map<String,Object> resp = new HashMap<>();
        resp.put("status", taskDTO.getStatus());
        resp.put("result", taskDTO.getResult());
        resp.put("msg", "查询成功");
        return ResponseEntity.ok(resp);
    }
}