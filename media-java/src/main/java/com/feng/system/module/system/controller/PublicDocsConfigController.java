package com.feng.system.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.feng.system.common.api.ApiResponse;
import com.feng.system.common.log.BusinessOperationType;
import com.feng.system.common.log.OperLog;
import com.feng.system.common.submit.PreventDuplicateSubmit;
import com.feng.system.module.system.dto.PublicDocsConfigDTO;
import com.feng.system.module.system.service.PublicDocsConfigService;
import com.feng.system.module.system.vo.PublicDocsConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/docs-config")
@RequiredArgsConstructor
public class PublicDocsConfigController {
    private final PublicDocsConfigService docsConfigService;

    @GetMapping
    @SaCheckPermission("system:docs:list")
    public ApiResponse<PublicDocsConfigVO> detail() {
        return ApiResponse.success(docsConfigService.detail());
    }

    @PutMapping
    @SaCheckPermission("system:docs:edit")
    @PreventDuplicateSubmit
    @OperLog(name = "保存前台文档配置", type = BusinessOperationType.UPDATE)
    public ApiResponse<Void> save(@RequestBody PublicDocsConfigDTO dto) {
        docsConfigService.save(dto);
        return ApiResponse.success("保存成功", null);
    }
}
