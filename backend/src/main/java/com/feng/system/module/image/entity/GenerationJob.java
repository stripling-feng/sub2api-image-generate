package com.feng.system.module.image.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.feng.system.module.image.JsonbTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName(value = "generation_jobs", autoResultMap = true)
public class GenerationJob {
    @TableId(type = IdType.INPUT) private String id;
    @TableField("\"profileId\"") private String profileId;
    private Long modelConfigId;
    private String prompt;
    @TableField("\"negativePrompt\"") private String negativePrompt;
    private String model;
    private String size;
    private String quality;
    private String style;
    private Integer count;
    @TableField("\"responseFormat\"") private String responseFormat;
    @TableField(typeHandler = JsonbTypeHandler.class) private String params;
    @TableField(typeHandler = JsonbTypeHandler.class) private String rawRequest;
    @TableField(typeHandler = JsonbTypeHandler.class) private String rawResponses;
    private String status;
    @TableField("\"errorMessage\"") private String errorMessage;
    @TableField("\"durationMs\"") private Integer durationMs;
    @TableField("\"upstreamTaskId\"") private String upstreamTaskId;
    @TableField("\"upstreamOperation\"") private String upstreamOperation;
    @TableField("\"upstreamStatus\"") private String upstreamStatus;
    private Integer progress;
    @TableField("\"nextPollAt\"") private LocalDateTime nextPollAt;
    @TableField("\"pollErrorCount\"") private Integer pollErrorCount;
    @TableField("\"pollLeaseUntil\"") private LocalDateTime pollLeaseUntil;
    @TableField("\"completedAt\"") private LocalDateTime completedAt;
    @TableField("\"billingStatus\"") private String billingStatus;
    @TableField("\"billingAmount\"") private BigDecimal billingAmount;
    @TableField("\"billingApiKeyId\"") private String billingApiKeyId;
    @TableField("\"billingUserId\"") private String billingUserId;
    @TableField("\"billingAccountId\"") private String billingAccountId;
    @TableField("\"billingUsageLogId\"") private String billingUsageLogId;
    @TableField("\"billingReservedAt\"") private LocalDateTime billingReservedAt;
    @TableField("\"billingSettledAt\"") private LocalDateTime billingSettledAt;
    @TableField("\"billingError\"") private String billingError;
    @TableField("\"createdAt\"") private LocalDateTime createdAt;
    @TableField("\"updatedAt\"") private LocalDateTime updatedAt;
}
