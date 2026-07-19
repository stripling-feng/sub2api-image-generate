package com.feng.system.module.system.controller;

import com.feng.system.common.api.ApiResponse;
import com.feng.system.common.ratelimit.RateLimit;
import com.feng.system.module.system.service.DistrictService;
import com.feng.system.module.system.vo.MenuTreeVO;
import lombok.RequiredArgsConstructor;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/districts")
@RequiredArgsConstructor
public class DistrictController {

    private final DistrictService districtService;

    @PostMapping("/sync")
    @SaCheckPermission("system:district:sync")
    @RateLimit(maxRequests = 3, windowSeconds = 3600, limitType = RateLimit.LimitType.PER_USER, message = "同步请求过于频繁，每小时最多3次")
    public ApiResponse<String> sync() {
        districtService.syncAsync();
        return ApiResponse.success("同步任务已提交，后台执行中");
    }

    @GetMapping("/tree")
    @SaCheckPermission("system:district:query")
    public ApiResponse<List<MenuTreeVO>> tree() {
        return ApiResponse.success(districtService.tree());
    }
}
