package com.feng.system.module.gpt.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GptAccountVO {
    private Long id;
    private String accountId;
    private String email;
    private String displayName;
    private String tokenFingerprint;
    private LocalDateTime tokenExpiresAt;
    private String planType;
    private String subscriptionPlan;
    private Boolean activeSubscription;
    private Boolean activeSubscriptionGratis;
    private Boolean used;
    private String accountStatus;
    private String plusEligibility;
    private String eligibilityReason;
    private String eligibleOffers;
    private String eligiblePromoCampaigns;
    private LocalDateTime lastCheckedAt;
    private String lastError;
    private LocalDateTime createTime;
}
