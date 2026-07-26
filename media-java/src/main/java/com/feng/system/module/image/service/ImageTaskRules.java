package com.feng.system.module.image.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * 图片任务规则工具类:集中定义上游状态到本地决策的映射规则,以及生成费用的计算规则。
 */
public final class ImageTaskRules {
    private static final Set<String> COMPLETED = Set.of("completed", "succeeded", "success");
    private static final Set<String> FAILED = Set.of("failed", "error", "cancelled", "canceled");

    private ImageTaskRules() {}

    /**
     * 根据上游状态与已耗时判定任务走向:完成、失败、超时或继续等待。
     *
     * @param status        上游返回的状态字符串(大小写不敏感)
     * @param elapsedMillis 任务自创建以来已耗时(毫秒)
     * @param maxMillis     允许的最大轮询时长(毫秒),超过则判定超时
     */
    public static Decision decide(String status, long elapsedMillis, long maxMillis) {
        String normalized = status == null ? "" : status.toLowerCase();
        if (COMPLETED.contains(normalized)) return Decision.COMPLETED;
        if (FAILED.contains(normalized)) return Decision.FAILED;
        if (elapsedMillis > maxMillis) return Decision.TIMEOUT;
        return Decision.PENDING;
    }

    /**
     * 计算生成费用:单价乘以张数(张数强制限制在 1~10 之间),保留 10 位小数。
     */
    public static BigDecimal charge(int count, BigDecimal unitPrice) {
        return unitPrice.multiply(BigDecimal.valueOf(Math.max(1, Math.min(10, count))))
                .setScale(10, RoundingMode.HALF_UP);
    }

    /** 轮询决策结果:已完成 / 已失败 / 已超时 / 继续等待 */
    public enum Decision { COMPLETED, FAILED, TIMEOUT, PENDING }
}
