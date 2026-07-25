package com.feng.system.module.system.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.feng.system.common.api.ApiResponse;
import com.feng.system.common.ratelimit.RateLimit;
import com.feng.system.module.system.dto.ChangePasswordDTO;
import com.feng.system.module.system.dto.LoginDTO;
import com.feng.system.module.system.service.AuthService;
import com.feng.system.module.system.vo.LoginVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @RateLimit(maxRequests = 5, windowSeconds = 60, limitType = RateLimit.LimitType.PER_IP, message = "登录请求过于频繁，请稍后再试")
    public ApiResponse<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return ApiResponse.success(authService.login(dto));
    }

    @GetMapping("/current")
    public ApiResponse<LoginVO> current() {
        return ApiResponse.success(authService.current());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        StpUtil.logout();
        return ApiResponse.success("退出成功", null);
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        authService.changePassword(dto);
        return ApiResponse.success("密码修改成功", null);
    }
}
