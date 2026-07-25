package com.feng.system.module.system.controller;

import com.feng.system.common.api.ApiResponse;
import com.feng.system.common.log.BusinessOperationType;
import com.feng.system.common.log.OperLog;
import com.feng.system.common.submit.PreventDuplicateSubmit;
import com.feng.system.module.system.dto.SystemConfigDTO;
import com.feng.system.module.system.service.SystemConfigService;
import com.feng.system.module.system.vo.SystemConfigVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system/configs")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    @GetMapping
    @SaCheckPermission("system:config:list")
    public ApiResponse<SystemConfigVO> detail() {
        return ApiResponse.success(systemConfigService.getManageConfig());
    }

    @PutMapping
    @SaCheckPermission("system:config:edit")
    @PreventDuplicateSubmit
    @OperLog(name = "保存系统配置", type = BusinessOperationType.UPDATE)
    public ApiResponse<Void> save(@Valid @RequestBody SystemConfigDTO dto) {
        systemConfigService.saveConfig(dto);
        return ApiResponse.success("保存成功", null);
    }
}
