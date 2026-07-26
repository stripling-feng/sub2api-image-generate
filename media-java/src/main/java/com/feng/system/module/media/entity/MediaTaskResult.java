package com.feng.system.module.media.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.feng.system.module.image.handler.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "media_task_results", autoResultMap = true)
public class MediaTaskResult {
    @TableId(type = IdType.INPUT)
    private String id;
    private String taskId;
    private String address;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String metadata;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
