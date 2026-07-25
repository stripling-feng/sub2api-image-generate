package com.feng.system.module.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.feng.system.module.image.JsonbTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName(value = "video_generation_jobs", autoResultMap = true)
public class VideoGenerationJob {
    @TableId(type = IdType.INPUT) private String id;
    private String profileId;
    private Long modelConfigId;
    private String requestId;
    private String prompt;
    private String model;
    private Integer duration;
    private String aspectRatio;
    private String resolution;
    private Integer generateAudio;
    @TableField(typeHandler = JsonbTypeHandler.class) private String params;
    @TableField(typeHandler = JsonbTypeHandler.class) private String rawRequest;
    @TableField(typeHandler = JsonbTypeHandler.class) private String rawResponses;
    private String upstreamTaskId;
    private String upstreamStatus;
    private Integer progress;
    private String status;
    private String errorMessage;
    private Integer durationMs;
    private LocalDateTime nextPollAt;
    private Integer pollErrorCount;
    private LocalDateTime pollLeaseUntil;
    private LocalDateTime completedAt;
    private String billingStatus;
    private BigDecimal billingAmount;
    private String billingApiKeyId;
    private String billingUserId;
    private String billingAccountId;
    private String billingUsageLogId;
    private LocalDateTime billingReservedAt;
    private LocalDateTime billingSettledAt;
    private String billingError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
