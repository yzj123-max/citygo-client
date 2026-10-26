package com.demo.logindemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("post_media")
public class PostMedia {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long postId;
    private String objectKey;
    private String contentType;
    private Long sizeBytes;
    private Integer width;
    private Integer height;
    private Integer sortOrder;
    private String status;
    private Date createTime;
    private Date updateTime;
}
