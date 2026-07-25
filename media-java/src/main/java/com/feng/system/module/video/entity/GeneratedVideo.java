package com.feng.system.module.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("generated_videos")
public class GeneratedVideo {
    @TableId(type = IdType.INPUT) private String id;
    private String jobId;
    private String publicUrl;
    private String mimeType;
    private LocalDateTime createdAt;
}
