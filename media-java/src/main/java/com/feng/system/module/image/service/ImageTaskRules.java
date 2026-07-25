package com.feng.system.module.image.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

public final class ImageTaskRules {
    private static final Set<String> COMPLETED = Set.of("completed", "succeeded", "success");
    private static final Set<String> FAILED = Set.of("failed", "error", "cancelled", "canceled");

    private ImageTaskRules() {}

    public static Decision decide(String status, long elapsedMillis, long maxMillis) {
        String normalized = status == null ? "" : status.toLowerCase();
        if (COMPLETED.contains(normalized)) return Decision.COMPLETED;
        if (FAILED.contains(normalized)) return Decision.FAILED;
        if (elapsedMillis > maxMillis) return Decision.TIMEOUT;
        return Decision.PENDING;
    }

    public static BigDecimal charge(int count, BigDecimal unitPrice) {
        return unitPrice.multiply(BigDecimal.valueOf(Math.max(1, Math.min(10, count))))
                .setScale(10, RoundingMode.HALF_UP);
    }

    public enum Decision { COMPLETED, FAILED, TIMEOUT, PENDING }
}
