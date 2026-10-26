package com.demo.logindemo.util;

import com.volcengine.tos.TOSV2;
import com.volcengine.tos.TOSV2ClientBuilder;
import com.volcengine.tos.model.object.PutObjectBasicInput;
import com.volcengine.tos.model.object.PutObjectInput;
import com.volcengine.tos.model.object.PutObjectOutput;
import com.volcengine.tos.model.object.PreSignedURLInput;
import com.volcengine.tos.model.object.PreSignedURLOutput;
import com.volcengine.tos.comm.HttpMethod;
import com.volcengine.tos.comm.common.ACLType;
import com.volcengine.tos.model.object.ObjectMetaRequestOptions;
import com.volcengine.tos.model.object.DeleteObjectInput;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PreDestroy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Component
public class TosUtil {

    @Value("${volc.tos.accessKeyId}")
    private String accessKeyId;

    @Value("${volc.tos.secretAccessKey}")
    private String secretAccessKey;

    @Value("${volc.tos.bucketName}")
    private String bucketName;

    @Value("${volc.tos.endpoint}")
    private String endpoint;

    @Value("${volc.tos.region}")
    private String region;

    @Value("${volc.tos.presignExpireSeconds:1800}")
    private long presignExpireSeconds;

    private TOSV2 tosClient;

    /**
     * 初始化 TOS 客户端
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        tosClient = new TOSV2ClientBuilder()
                .build(region, endpoint, accessKeyId, secretAccessKey);

        log.info("TOS 客户端初始化完成，bucket={}", bucketName);
    }

    /**
     * 上传图片到 TOS。
     *
     * @return 对象名称 objectKey，而不是公开访问 URL
     */
    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传图片不能为空");
        }

        // 限制单张图片不超过 10 MB
        if (file.getSize() > 10L * 1024 * 1024) {
            throw new IllegalArgumentException("图片不能超过 10 MB");
        }

        String contentType = file.getContentType();

        if (contentType == null ||
                !(contentType.equalsIgnoreCase("image/jpeg")
                        || contentType.equalsIgnoreCase("image/png")
                        || contentType.equalsIgnoreCase("image/webp"))) {
            throw new IllegalArgumentException(
                    "仅支持 JPEG、PNG、WEBP 格式的图片");
        }

        String suffix;

        switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png":
                suffix = ".png";
                break;
            case "image/webp":
                suffix = ".webp";
                break;
            default:
                suffix = ".jpg";
        }

        String objectKey =
                "outfit/" + UUID.randomUUID() + suffix;

        try {
            byte[] bytes = file.getBytes();

            PutObjectBasicInput basicInput =
                    new PutObjectBasicInput()
                            .setBucket(bucketName)
                            .setKey(objectKey);

            PutObjectInput input =
                    new PutObjectInput()
                            .setPutObjectBasicInput(basicInput)
                            .setContent(new ByteArrayInputStream(bytes));

            PutObjectOutput output = tosClient.putObject(input);

            log.info("图片上传成功，objectKey={}", objectKey);

            return objectKey;

        } catch (IOException e) {
            throw new RuntimeException("读取上传图片失败", e);
        } catch (Exception e) {
            log.error("TOS 图片上传失败", e);
            throw new RuntimeException("TOS 图片上传失败，请检查配置和权限", e);
        }
    }

    /**
     * 生成私有对象的临时访问 URL。
     */
    public String generatePresignedUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }

        try {
            PreSignedURLInput input =
                    new PreSignedURLInput()
                            .setBucket(bucketName)
                            .setKey(objectKey)
                            .setHttpMethod(HttpMethod.GET)
                            .setExpires(presignExpireSeconds);

            PreSignedURLOutput output =
                    tosClient.preSignedURL(input);

            return output.getSignedUrl();

        } catch (Exception e) {
            log.error("生成 TOS 预签名 URL 失败", e);
            throw new RuntimeException("生成图片访问 URL 失败", e);
        }
    }

    /**
     * 上传已经过内容校验的日记图片，返回对象名称。
     */
    public String uploadDiaryImage(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("图片内容不能为空");
        }
        if (bytes.length > 10L * 1024 * 1024) {
            throw new IllegalArgumentException("图片不能超过10MB");
        }

        String suffix;
        if ("image/jpeg".equals(contentType)) {
            suffix = ".jpg";
        } else if ("image/png".equals(contentType)) {
            suffix = ".png";
        } else {
            throw new IllegalArgumentException("日记图片目前仅支持JPG、PNG");
        }

        String objectKey = "private/diary/" + UUID.randomUUID() + suffix;
        ObjectMetaRequestOptions options = new ObjectMetaRequestOptions()
                .setContentType(contentType)
                .setAclType(ACLType.ACL_PRIVATE)
                .setCacheControl("private, no-store");
        PutObjectBasicInput basicInput = new PutObjectBasicInput()
                .setBucket(bucketName)
                .setKey(objectKey)
                .setContentLength(bytes.length)
                .setOptions(options);

        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            tosClient.putObject(new PutObjectInput()
                    .setPutObjectBasicInput(basicInput)
                    .setContent(input));
            return objectKey;
        } catch (Exception e) {
            log.error("日记图片上传失败，异常类型={}", e.getClass().getSimpleName());
            throw new IllegalStateException("日记图片上传失败，请稍后重试", e);
        }
    }

    /**
     * 仅供后端在检查归属和引用状态后清理日记对象。
     */
    public void deleteDiaryObject(String objectKey) {
        if (objectKey == null
                || !objectKey.startsWith("private/diary/")
                || objectKey.length() <= "private/diary/".length()) {
            throw new IllegalArgumentException("无效的日记图片对象名称");
        }
        try {
            tosClient.deleteObject(new DeleteObjectInput()
                    .setBucket(bucketName)
                    .setKey(objectKey));
        } catch (Exception e) {
            log.error("日记图片清理失败，异常类型={}", e.getClass().getSimpleName());
            throw new IllegalStateException("日记图片清理失败", e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (tosClient != null) {
            try {
                tosClient.close();
                log.info("TOS 客户端已关闭");
            } catch (java.io.IOException e) {
                log.warn("关闭 TOS 客户端时发生异常", e);
            }
        }
    }
}
