package com.feng.system.module.tool.controller;

import com.feng.system.common.api.ApiResponse;
import com.feng.system.common.api.PageResult;
import com.feng.system.module.tool.dto.JobTaskLogQueryDTO;
import com.feng.system.module.tool.entity.SysJobTaskLog;
import com.feng.system.module.tool.service.JobTaskLogService;
import lombok.RequiredArgsConstructor;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tool/job-task-logs")
@RequiredArgsConstructor
public class JobTaskLogController {

    private final JobTaskLogService jobTaskLogService;

    @GetMapping
    @SaCheckPermission("tool:job:list")
    public ApiResponse<PageResult<SysJobTaskLog>> list(JobTaskLogQueryDTO queryDTO) {
        return ApiResponse.success(jobTaskLogService.page(queryDTO));
    }
}
