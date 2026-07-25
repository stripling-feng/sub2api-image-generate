package com.feng.system.common.log.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.common.log.OperLog;
import com.feng.system.common.log.entity.SysOperLog;
import com.feng.system.common.log.service.OperationLogAsyncService;
import com.feng.system.module.system.vo.UserInfoVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;

@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    private final OperationLogAsyncService asyncService;
    private final ObjectMapper objectMapper;
    private final HttpServletRequest request;

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        try {
            Object result = joinPoint.proceed();
            saveLog(joinPoint, operLog, collectChangeData(joinPoint.getArgs()), true, null);
            return result;
        } catch (Throwable ex) {
            saveLog(joinPoint, operLog, collectChangeData(joinPoint.getArgs()), false, ex.getMessage());
            throw ex;
        }
    }

    private void saveLog(ProceedingJoinPoint joinPoint, OperLog operLogMeta, String changeData,
                         boolean success, String errorMessage) {
        SysOperLog log = new SysOperLog();
        log.setApiName(operLogMeta.name());
        log.setBusinessType(operLogMeta.type().name());
        log.setMethodName(joinPoint.getSignature().toShortString());
        log.setRequestUri(request.getRequestURI());
        Long userId = currentUserId();
        if (userId != null) {
            log.setOperatorId(userId);
            UserInfoVO userInfo = getFromSession(userId);
            log.setOperatorName(userInfo == null ? "unknown" : userInfo.getUsername());
        } else {
            log.setOperatorName("anonymous");
        }
        log.setIpAddress(resolveIp());
        log.setSuccess(success ? 1 : 0);
        log.setErrorMessage(errorMessage);
        log.setAfterData(changeData);
        log.setOperationTime(LocalDateTime.now());
        asyncService.save(log);
    }

    private String collectChangeData(Object[] args) {
        ArrayList<Object> values = new ArrayList<>();
        for (Object arg : args) {
            Object normalized = normalizeArg(arg);
            if (normalized != null) {
                values.add(normalized);
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        Object payload = values.size() == 1 ? values.get(0) : values;
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"error\":\"json serialize failed\"}";
        }
    }

    private Object normalizeArg(Object arg) {
        if (arg == null) {
            return null;
        }
        if (arg instanceof HttpServletRequest
                || arg instanceof HttpServletResponse
                || arg instanceof BindingResult) {
            return null;
        }
        if (arg instanceof MultipartFile file) {
            return Map.of(
                    "originalName", file.getOriginalFilename(),
                    "size", file.getSize(),
                    "contentType", file.getContentType()
            );
        }
        if (arg instanceof CharSequence
                || arg instanceof Number
                || arg instanceof Boolean
                || arg.getClass().isEnum()) {
            return null;
        }
        return arg;
    }

    private Long currentUserId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;
        }
    }

    private UserInfoVO getFromSession(Long userId) {
        try {
            return StpUtil.getSessionByLoginId(userId).getModel("userInfo", UserInfoVO.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveIp() {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
