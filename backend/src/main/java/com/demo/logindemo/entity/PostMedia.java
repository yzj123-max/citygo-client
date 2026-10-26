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

    // 上传者ID
    private Long userId;

    // 所属帖子ID，尚未发布时为空
    private Long postId;

    // TOS对象名称，不保存临时签名URL
    private String objectKey;

    // 图片类型，例如 image/jpeg
    private String contentType;

    // 图片字节数
    private Long sizeBytes;

    private Integer width;

    private Integer height;

    // 图片展示顺序
    private Integer sortOrder;

    // TEMP待发布、ATTACHED已关联、DELETED已删除
    private String status;

    private Date createTime;

    private Date updateTime;
}
