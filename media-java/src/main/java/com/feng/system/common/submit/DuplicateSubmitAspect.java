package com.feng.system.common.submit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
@RequiredArgsConstructor
public class DuplicateSubmitAspect {

    private static final String DUP_PREFIX = "dup:submit:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final HttpServletRequest request;

    @Around("@annotation(preventDuplicateSubmit)")
    public Object around(ProceedingJoinPoint joinPoint, PreventDuplicateSubmit preventDuplicateSubmit) throws Throwable {
        String cacheKey = buildCacheKey(joinPoint.getArgs());
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(cacheKey, "1", preventDuplicateSubmit.interval(), TimeUnit.MILLISECONDS);
        if (Boolean.FALSE.equals(success)) {
            throw new DuplicateSubmitException(preventDuplicateSubmit.message());
        }
        return joinPoint.proceed();
    }

    private String buildCacheKey(Object[] args) {
        String principal = request.getUserPrincipal() == null ? "anonymous" : request.getUserPrincipal().getName();
        String payload = principal + "|" + request.getMethod() + "|" + request.getRequestURI() + "|" + serializeArgs(args);
        return DUP_PREFIX + sha256(payload);
    }

    private String serializeArgs(Object[] args) {
        List<Object> serializableArgs = new ArrayList<>();
        for (Object arg : args) {
            if (arg == null || isIgnoredArg(arg)) {
                continue;
            }
            serializableArgs.add(arg);
        }
        try {
            return objectMapper.writeValueAsString(serializableArgs);
        } catch (JsonProcessingException ex) {
            return serializableArgs.toString();
        }
    }

    private boolean isIgnoredArg(Object arg) {
        return arg instanceof jakarta.servlet.ServletRequest
                || arg instanceof jakarta.servlet.ServletResponse
                || arg instanceof MultipartFile
                || arg instanceof MultipartFile[];
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            return value;
        }
    }
}
