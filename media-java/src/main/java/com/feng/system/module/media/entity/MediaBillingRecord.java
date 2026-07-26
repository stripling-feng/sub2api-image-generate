package com.feng.system.module.media.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("media_billing_records")
public class MediaBillingRecord {
    @TableId(type = IdType.INPUT)
    private String id;
    private String taskId;
    private String apiKey;
    private BigDecimal taskFee;
    private String deductionStatus;
    private String apiKeyId;
    private String userId;
    private String accountId;
    private String usageLogId;
    private LocalDateTime reservedAt;
    private LocalDateTime settledAt;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
