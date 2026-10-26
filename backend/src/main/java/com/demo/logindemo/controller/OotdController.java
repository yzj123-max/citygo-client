package com.demo.logindemo.controller;

import com.demo.logindemo.dto.OotdReq;
import com.demo.logindemo.service.OotdAiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/api/ai")
public class OotdController {

    @Autowired
    private OotdAiService ootdAiService;

    @PostMapping(value = "/ootd-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ootdStream(@RequestBody OotdReq req) {
        return ootdAiService.streamAnalyze(req.getBase64(), req.getPrompt());
    }

    @GetMapping("/test-simple")
    public String testSimple() {
        // 调用你的service，直接返回一整段文字，不是流式！
        return ootdAiService.testSimple();
    }
}