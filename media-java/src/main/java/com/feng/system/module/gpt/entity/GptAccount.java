package com.feng.system.module.gpt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.feng.system.module.image.handler.JsonbTypeHandler;
import com.feng.system.module.system.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "gpt_accounts", autoResultMap = true)
public class GptAccount extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String accountId;
    private String userId;
    private String email;
    private String displayName;
    private String accessToken;
    private String tokenHash;
    private LocalDateTime tokenExpiresAt;
    private String planType;
    private String subscriptionPlan;
    private Boolean activeSubscription;
    private Boolean activeSubscriptionGratis;
    private Boolean used;
    private String accountStatus;
    private String plusEligibility;
    private String eligibilityReason;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String eligibleOffers;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String eligiblePromoCampaigns;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String rawResponse;
    private LocalDateTime lastCheckedAt;
    private String lastError;
}
