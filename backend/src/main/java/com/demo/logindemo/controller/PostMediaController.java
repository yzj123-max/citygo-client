package com.demo.logindemo.controller;

import com.demo.logindemo.dto.PostMediaUploadResp;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.service.PostMediaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/posts/media")
public class PostMediaController {

    private static final Logger log =
            LoggerFactory.getLogger(PostMediaController.class);

    private final PostMediaService mediaService;

    public PostMediaController(PostMediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Result<PostMediaUploadResp>> upload(
            @RequestAttribute("userId") Long userId,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            PostMediaUploadResp result =
                    mediaService.upload(userId, file);

            return ResponseEntity.ok(
                    Result.success(result)
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Result.<PostMediaUploadResp>fail(
                            e.getMessage()
                    )
            );

        } catch (Exception e) {
            log.error(
                    "帖子图片上传失败，userId={}",
                    userId,
                    e
            );

            Result<PostMediaUploadResp> result =
                    Result.fail("图片上传失败，请查看后端日志");

            result.setCode(500);

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(result);
        }
    }
}