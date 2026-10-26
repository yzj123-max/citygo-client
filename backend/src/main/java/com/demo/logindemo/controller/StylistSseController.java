package com.demo.logindemo.controller;

import com.demo.logindemo.service.StylistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;

@RestController
@RequestMapping("/api/stylist/chat")
@RequiredArgsConstructor
@Slf4j
public class StylistSseController {

    private final StylistService stylistService;

    // 原有接口【保留不动，JSON DTO调用】
    // 图片+文字接口，form-data提交，不传图片也可以纯文本测试（我们现在要用这个！）
    @PostMapping(value = "/imgChatSse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter imgChatSse(
            @RequestParam("msg") String msg,
            @RequestParam(value = "image", required = false) MultipartFile imageFile
    ){
        log.info("【图片OOTD】收到消息：{}，图片文件名：{}", msg, imageFile != null ? imageFile.getOriginalFilename() : "无图片");
        SseEmitter emitter = new SseEmitter(300 * 1000L);
        stylistService.chatSse(msg, imageFile, emitter);
        return emitter;
    }
    @PostMapping(
            value = "/multiImgChatSse",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter multiImgChatSse(
            @RequestParam("msg") String msg,
            @RequestParam("travelSchedule") String travelSchedule,
            @RequestParam("images") List<MultipartFile> images
    ) throws Exception {

        SseEmitter emitter = new SseEmitter(300_000L);

        stylistService.multiImageChatSse(
                msg,
                travelSchedule,
                images,
                emitter
        );

        return emitter;
    }

}