package com.feng.system.module.media.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.feng.system.module.image.handler.JsonbTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName(value = "media_tasks", autoResultMap = true)
public class MediaTask {
    @TableId(type = IdType.INPUT)
    private String id;
    private String apiKey;
    private String taskType;
    private String requestId;
    private Long modelConfigId;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String userRequest;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String taskData;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String systemResponse;
    private String upstreamTaskId;
    private String upstreamOperation;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String upstreamRequest;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String upstreamResponse;
    private String status;
    private String errorMessage;
    private LocalDateTime completedAt;
    private String billingStatus;
    private BigDecimal billingAmount;
    private Integer progress;
    private Integer durationMs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
