package com.demo.logindemo.service;

import com.demo.logindemo.dto.PostMediaUploadResp;
import com.demo.logindemo.entity.PostMedia;
import com.demo.logindemo.mapper.PostMediaMapper;
import com.demo.logindemo.util.TosUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

@Service
public class PostMediaService {

    // 单张图片最大10MB
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    // 最大4000万像素
    private static final long MAX_PIXELS = 40_000_000L;

    private final PostMediaMapper mediaMapper;

    private final TosUtil tosUtil;

    @Value("${volc.tos.presignExpireSeconds:1800}")
    private long previewExpiresInSeconds;

    public PostMediaService(
            PostMediaMapper mediaMapper,
            TosUtil tosUtil
    ) {
        this.mediaMapper = mediaMapper;
        this.tosUtil = tosUtil;
    }

    public PostMediaUploadResp upload(
            Long userId,
            MultipartFile file
    ) throws IOException {

        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("请先登录");
        }

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择照片");
        }

        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("每张照片不能超过10MB");
        }

        byte[] bytes = readLimited(file);

        if (bytes.length == 0) {
            throw new IllegalArgumentException("照片内容为空");
        }

        String mime;
        int width;
        int height;

        // 根据实际图片内容判断格式，不依赖文件名或客户端MIME。
        try (ImageInputStream input =
                     ImageIO.createImageInputStream(
                             new ByteArrayInputStream(bytes)
                     )) {

            if (input == null) {
                throw new IllegalArgumentException("无法读取图片");
            }

            Iterator<ImageReader> readers =
                    ImageIO.getImageReaders(input);

            if (!readers.hasNext()) {
                throw new IllegalArgumentException(
                        "图片无效，本接口目前支持JPG、PNG"
                );
            }

            ImageReader reader = readers.next();

            try {
                String format = reader.getFormatName()
                        .toLowerCase(Locale.ROOT);

                if (format.equals("jpeg") || format.equals("jpg")) {
                    mime = "image/jpeg";
                } else if (format.equals("png")) {
                    mime = "image/png";
                } else {
                    throw new IllegalArgumentException(
                            "本接口目前支持JPG、PNG"
                    );
                }

                reader.setInput(input, true, true);

                width = reader.getWidth(0);
                height = reader.getHeight(0);

                if (width <= 0
                        || height <= 0
                        || (long) width * height > MAX_PIXELS) {
                    throw new IllegalArgumentException(
                            "图片尺寸无效或超过4000万像素，请压缩后上传"
                    );
                }

            } finally {
                reader.dispose();
            }

        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "图片无法解析，请更换照片",
                    e
            );
        }

        // 使用校验后的真实类型上传，复用现有TosUtil。
        String suffix = mime.equals("image/png")
                ? ".png"
                : ".jpg";

        MultipartFile validatedFile = new ValidatedImage(
                bytes,
                mime,
                "post" + suffix
        );

        String objectKey = tosUtil.upload(validatedFile);

        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalStateException(
                    "TOS未返回图片对象名称"
            );
        }

        String previewUrl =
                tosUtil.generatePresignedUrl(objectKey);

        if (previewUrl == null || previewUrl.isBlank()) {
            throw new IllegalStateException(
                    "TOS未返回预览地址"
            );
        }

        // 保存为待发布图片，发布帖子时再关联postId。
        Date now = new Date();

        PostMedia media = new PostMedia();
        media.setUserId(userId);
        media.setPostId(null);
        media.setObjectKey(objectKey);
        media.setContentType(mime);
        media.setSizeBytes((long) bytes.length);
        media.setWidth(width);
        media.setHeight(height);
        media.setSortOrder(0);
        media.setStatus("TEMP");
        media.setCreateTime(now);
        media.setUpdateTime(now);

        if (mediaMapper.insert(media) != 1
                || media.getId() == null) {
            throw new IllegalStateException(
                    "图片记录保存失败"
            );
        }

        PostMediaUploadResp result =
                new PostMediaUploadResp();

        result.setMediaId(media.getId());
        result.setPreviewUrl(previewUrl);
        result.setPreviewExpiresInSeconds(
                previewExpiresInSeconds
        );
        result.setContentType(mime);
        result.setSizeBytes(media.getSizeBytes());
        result.setWidth(width);
        result.setHeight(height);
        result.setStatus("TEMP");

        return result;
    }

    // 限制实际读取的大小，防止文件超过10MB。
    private byte[] readLimited(
            MultipartFile file
    ) throws IOException {

        try (InputStream input = file.getInputStream();
             ByteArrayOutputStream output =
                     new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            int read;
            long total = 0;

            while ((read = input.read(buffer)) != -1) {
                total += read;

                if (total > MAX_BYTES) {
                    throw new IllegalArgumentException(
                            "每张照片不能超过10MB"
                    );
                }

                output.write(buffer, 0, read);
            }

            return output.toByteArray();
        }
    }

    // 将校验后的图片内容交给现有TosUtil使用。
    private static final class ValidatedImage
            implements MultipartFile {

        private final byte[] bytes;
        private final String mime;
        private final String filename;

        private ValidatedImage(
                byte[] bytes,
                String mime,
                String filename
        ) {
            this.bytes = bytes;
            this.mime = mime;
            this.filename = filename;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return filename;
        }

        @Override
        public String getContentType() {
            return mime;
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes.clone();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(File dest)
                throws IOException {
            Files.write(dest.toPath(), bytes);
        }
    }
}