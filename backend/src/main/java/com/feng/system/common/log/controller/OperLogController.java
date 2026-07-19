package com.feng.system.common.log.controller;

import com.feng.system.common.api.ApiResponse;
import com.feng.system.common.api.PageResult;
import com.feng.system.common.log.dto.OperLogQueryDTO;
import com.feng.system.common.log.entity.SysOperLog;
import com.feng.system.common.log.service.OperLogService;
import lombok.RequiredArgsConstructor;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/oper-logs")
@RequiredArgsConstructor
public class OperLogController {

    private final OperLogService operLogService;

    @GetMapping
    @SaCheckPermission("system:log:list")
    public ApiResponse<PageResult<SysOperLog>> page(OperLogQueryDTO queryDTO) {
        return ApiResponse.success(operLogService.page(queryDTO));
    }
}
