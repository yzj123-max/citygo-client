package com.demo.logindemo.service;

import com.demo.logindemo.dto.PersonalMediaUploadResp;
import com.demo.logindemo.entity.PersonalMedia;
import com.demo.logindemo.mapper.PersonalMediaMapper;
import com.demo.logindemo.util.TosUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

@Service
public class PersonalMediaService {

    private static final Logger log =
            LoggerFactory.getLogger(PersonalMediaService.class);

    private static final int MAX_BYTES = 10 * 1024 * 1024;

    private static final long MAX_PIXELS = 40_000_000L;

    private static final long TEMP_LIFETIME_MILLIS =
            24L * 60 * 60 * 1000;

    private final PersonalMediaMapper mediaMapper;
    private final TosUtil tosUtil;

    @Value("${volc.tos.presignExpireSeconds:1800}")
    private long previewExpiresInSeconds;

    public PersonalMediaService(
            PersonalMediaMapper mediaMapper,
            TosUtil tosUtil
    ) {
        this.mediaMapper = mediaMapper;
        this.tosUtil = tosUtil;
    }

    /**
     * 上传当前用户的私人日记图片。
     *
     * 不在整个方法上开启数据库事务，
     * 避免上传期间一直占用数据库事务。
     */
    public PersonalMediaUploadResp upload(
            Long userId,
            MultipartFile file
    ) {
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "请先登录"
            );
        }

        ValidatedImage image = validateImage(file);

        String objectKey = tosUtil.uploadDiaryImage(
                image.bytes(),
                image.contentType()
        );

        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalStateException(
                    "图片存储服务未返回对象名称"
            );
        }

        Date now = new Date();

        PersonalMedia media = new PersonalMedia();
        media.setUserId(userId);
        media.setPurpose("DIARY");
        media.setObjectKey(objectKey);
        media.setContentType(image.contentType());
        media.setSizeBytes((long) image.bytes().length);
        media.setWidth(image.width());
        media.setHeight(image.height());
        media.setStatus("TEMP");
        media.setExpiresAt(
                new Date(now.getTime() + TEMP_LIFETIME_MILLIS)
        );
        media.setCreateTime(now);
        media.setUpdateTime(now);

        try {
            int inserted = mediaMapper.insert(media);

            if (inserted != 1 || media.getId() == null) {
                throw new IllegalStateException(
                        "图片媒体记录保存失败"
                );
            }

        } catch (RuntimeException e) {
            /*
             * 数据库保存失败时，尝试清理刚上传的对象。
             * 补偿失败保留日志，供后续排查。
             */
            try {
                tosUtil.deleteDiaryObject(objectKey);
            } catch (RuntimeException cleanupError) {
                e.addSuppressed(cleanupError);

                log.error(
                        "媒体记录保存失败，且图片补偿清理未完成，objectKey={}",
                        objectKey
                );
            }

            throw new IllegalStateException(
                    "图片信息保存失败，请稍后重试",
                    e
            );
        }

        /*
         * 在媒体记录保存后生成预览地址。
         * 如果签名失败，临时记录仍保留，不会伪装成上传成功。
         */
        String previewUrl =
                tosUtil.generatePresignedUrl(objectKey);

        if (previewUrl == null || previewUrl.isBlank()) {
            throw new IllegalStateException(
                    "图片预览地址生成失败，请稍后重试"
            );
        }

        PersonalMediaUploadResp resp =
                new PersonalMediaUploadResp();

        resp.setMediaId(media.getId());
        resp.setPreviewUrl(previewUrl);
        resp.setPreviewExpiresInSeconds(
                previewExpiresInSeconds
        );
        resp.setContentType(media.getContentType());
        resp.setSizeBytes(media.getSizeBytes());
        resp.setWidth(media.getWidth());
        resp.setHeight(media.getHeight());
        resp.setStatus(media.getStatus());
        resp.setExpiresAt(media.getExpiresAt());

        return resp;
    }

    /**
     * 校验实际图片内容，不依赖文件名和客户端声明的类型。
     */
    private ValidatedImage validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择图片");
        }

        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException(
                    "每张图片不能超过10MB"
            );
        }

        byte[] bytes;

        try (InputStream input = file.getInputStream()) {
            bytes = input.readNBytes(MAX_BYTES + 1);

        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "读取图片失败，请重新选择",
                    e
            );
        }

        if (bytes.length == 0) {
            throw new IllegalArgumentException("图片内容为空");
        }

        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException(
                    "每张图片不能超过10MB"
            );
        }

        try (ImageInputStream input =
                     ImageIO.createImageInputStream(
                             new ByteArrayInputStream(bytes)
                     )) {

            if (input == null) {
                throw new IllegalArgumentException(
                        "无法读取图片"
                );
            }

            Iterator<ImageReader> readers =
                    ImageIO.getImageReaders(input);

            if (!readers.hasNext()) {
                throw new IllegalArgumentException(
                        "图片无效，目前仅支持JPG、PNG"
                );
            }

            ImageReader reader = readers.next();

            try {
                String format = reader.getFormatName()
                        .toLowerCase(Locale.ROOT);

                String contentType;

                if ("jpeg".equals(format)
                        || "jpg".equals(format)) {
                    contentType = "image/jpeg";

                } else if ("png".equals(format)) {
                    contentType = "image/png";

                } else {
                    throw new IllegalArgumentException(
                            "日记图片目前仅支持JPG、PNG"
                    );
                }

                reader.setInput(input, true, true);

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                if (width <= 0
                        || height <= 0
                        || (long) width * height > MAX_PIXELS) {
                    throw new IllegalArgumentException(
                            "图片尺寸无效或超过4000万像素，请压缩后上传"
                    );
                }

                // 实际解码，检查图片是否能够正常读取。
                BufferedImage decoded = reader.read(0);

                if (decoded == null) {
                    throw new IllegalArgumentException(
                            "图片内容无法正常解析"
                    );
                }

                try {
                    if (decoded.getWidth() != width
                            || decoded.getHeight() != height) {
                        throw new IllegalArgumentException(
                                "图片尺寸信息异常"
                        );
                    }
                } finally {
                    decoded.flush();
                }

                return new ValidatedImage(
                        bytes,
                        contentType,
                        width,
                        height
                );

            } finally {
                reader.dispose();
            }

        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "图片无法解析，请更换图片",
                    e
            );
        }
    }

    /**
     * 仅供本Service内部传递校验后的图片数据。
     */
    private record ValidatedImage(
            byte[] bytes,
            String contentType,
            int width,
            int height
    ) {
    }
}