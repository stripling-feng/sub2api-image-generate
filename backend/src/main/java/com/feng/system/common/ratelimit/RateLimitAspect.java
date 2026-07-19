package com.feng.system.common.ratelimit;

import com.feng.system.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;

@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private static final String RATE_LIMIT_PREFIX = "ratelimit:";

    private static final String RATE_LIMIT_LUA =
            "local key = KEYS[1]\n" +
            "local now = tonumber(ARGV[1])\n" +
            "local window = tonumber(ARGV[2])\n" +
            "local max = tonumber(ARGV[3])\n" +
            "redis.call('ZREMRANGEBYSCORE', key, 0, now - window)\n" +
            "local count = redis.call('ZCARD', key)\n" +
            "if count >= max then\n" +
            "    return 1\n" +
            "end\n" +
            "redis.call('ZADD', key, now, now .. '-' .. math.random(100000))\n" +
            "redis.call('EXPIRE', key, math.ceil(window / 1000) + 1)\n" +
            "return 0";

    private final StringRedisTemplate stringRedisTemplate;
    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final ObjectMapper objectMapper;
    private final RedisScript<Long> rateLimitScript = RedisScript.of(RATE_LIMIT_LUA, Long.class);

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = buildKey(rateLimit);
        long now = System.currentTimeMillis();
        long windowMs = (long) rateLimit.windowSeconds() * 1000;

        Long result = stringRedisTemplate.execute(rateLimitScript,
                List.of(key),
                String.valueOf(now),
                String.valueOf(windowMs),
                String.valueOf(rateLimit.maxRequests()));

        if (result == 1) {
            writeRateLimitResponse(rateLimit.message());
            return null;
        }

        return joinPoint.proceed();
    }

    private void writeRateLimitResponse(String message) throws java.io.IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(message)));
    }

    private String buildKey(RateLimit rateLimit) {
        String identifier;
        if (rateLimit.limitType() == RateLimit.LimitType.PER_USER) {
            identifier = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : getClientIp();
        } else {
            identifier = getClientIp();
        }
        String raw = rateLimit.limitType() + "|" + request.getRequestURI() + "|" + identifier;
        return RATE_LIMIT_PREFIX + sha256(raw);
    }

    private String getClientIp() {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        // Only trust the first IP in the chain
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            return value;
        }
    }
}
