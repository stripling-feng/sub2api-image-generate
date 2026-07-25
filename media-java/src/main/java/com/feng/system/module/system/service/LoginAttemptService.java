package com.feng.system.module.system.service;

public interface LoginAttemptService {
    void checkLoginAllowed(String username);
    void recordLoginSuccess(String username);
    void recordLoginFailure(String username);
}
