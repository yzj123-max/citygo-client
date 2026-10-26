package com.demo.logindemo.controller;

import com.demo.logindemo.dto.PersonalMediaUploadResp;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.service.PersonalMediaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/me/diaries/media")
public class PersonalMediaController {

    private static final Logger log =
            LoggerFactory.getLogger(
                    PersonalMediaController.class
            );

    private final PersonalMediaService mediaService;

    public PersonalMediaController(
            PersonalMediaService mediaService
    ) {
        this.mediaService = mediaService;
    }

    /**
     * 上传自己的日记图片。
     * POST /api/me/diaries/media
     *
     * Body：form-data
     * 字段名：file
     * 字段类型：File
     */
    @PostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Result<PersonalMediaUploadResp>> upload(
            @RequestAttribute("userId") Long userId,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            PersonalMediaUploadResp data =
                    mediaService.upload(userId, file);

            return ResponseEntity.ok(
                    Result.success(data)
            );

        } catch (ResponseStatusException e) {
            return failure(
                    e.getStatusCode().value(),
                    e.getReason() == null
                            ? "请求失败"
                            : e.getReason()
            );

        } catch (IllegalArgumentException e) {
            return failure(400, e.getMessage());

        } catch (Exception e) {
            log.error(
                    "日记图片上传失败，userId={}",
                    userId,
                    e
            );

            return failure(
                    500,
                    "图片上传失败，请稍后重试"
            );
        }
    }

    private <T> ResponseEntity<Result<T>> failure(
            int status,
            String message
    ) {
        Result<T> result = Result.fail(message);
        result.setCode(status);

        return ResponseEntity
                .status(status)
                .body(result);
    }
}