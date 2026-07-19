package com.feng.system.module.system.service.impl;

import com.feng.system.common.exception.BusinessException;
import com.feng.system.module.system.service.LoginAttemptService;
import com.feng.system.module.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class LoginAttemptServiceImpl implements LoginAttemptService {

    private static final String FAILURE_KEY_PREFIX = "login:fail:";
    private static final String LOCK_KEY_PREFIX = "login:lock:";

    private final StringRedisTemplate stringRedisTemplate;
    private final SystemConfigService systemConfigService;

    @Override
    public void checkLoginAllowed(String username) {
        String lockKey = buildLockKey(username);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey))) {
            Long seconds = stringRedisTemplate.getExpire(lockKey);
            long remainingSeconds = seconds == null || seconds < 0
                    ? Duration.ofMinutes(systemConfigService.getLoginFailLockMinutes()).getSeconds()
                    : seconds;
            long remainingMinutes = Math.max(1, (remainingSeconds + 59) / 60);
            throw new BusinessException("登录失败次数过多，请 " + remainingMinutes + " 分钟后再试");
        }
    }

    @Override
    public void recordLoginSuccess(String username) {
        stringRedisTemplate.delete(buildFailureKey(username));
        stringRedisTemplate.delete(buildLockKey(username));
    }

    @Override
    public void recordLoginFailure(String username) {
        String failureKey = buildFailureKey(username);
        Duration failureWindow = Duration.ofMinutes(systemConfigService.getLoginFailWindowMinutes());
        Duration lockDuration = Duration.ofMinutes(systemConfigService.getLoginFailLockMinutes());
        int maxFailures = systemConfigService.getLoginFailMaxAttempts();
        Long failureCount = stringRedisTemplate.opsForValue().increment(failureKey);
        if (failureCount == null) {
            return;
        }
        if (failureCount == 1L) {
            stringRedisTemplate.expire(failureKey, failureWindow);
        }
        if (failureCount >= maxFailures) {
            stringRedisTemplate.opsForValue().set(buildLockKey(username), "1", lockDuration);
            stringRedisTemplate.delete(failureKey);
        }
    }

    private String buildFailureKey(String username) {
        return FAILURE_KEY_PREFIX + normalize(username);
    }

    private String buildLockKey(String username) {
        return LOCK_KEY_PREFIX + normalize(username);
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
