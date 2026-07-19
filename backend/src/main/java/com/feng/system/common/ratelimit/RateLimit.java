package com.feng.system.common.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 时间窗口内允许的最大请求次数
     */
    int maxRequests() default 60;

    /**
     * 时间窗口，单位秒
     */
    int windowSeconds() default 60;

    /**
     * 限流维度：PER_IP 或 PER_USER
     */
    LimitType limitType() default LimitType.PER_IP;

    /**
     * 被限流时的提示信息
     */
    String message() default "请求过于频繁，请稍后再试";

    enum LimitType {
        PER_IP, PER_USER
    }
}
