package com.feng.system.module.image.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("generated_images")
public class GeneratedImage {
    @TableId(type = IdType.INPUT) private String id;
    @TableField("\"jobId\"") private String jobId;
    @TableField("\"filePath\"") private String filePath;
    @TableField("\"publicUrl\"") private String publicUrl;
    @TableField("\"mimeType\"") private String mimeType;
    private Integer width;
    private Integer height;
    @TableField("\"sizeBytes\"") private Integer sizeBytes;
    @TableField("\"sourceIndex\"") private Integer sourceIndex;
    @TableField("\"createdAt\"") private LocalDateTime createdAt;
}
