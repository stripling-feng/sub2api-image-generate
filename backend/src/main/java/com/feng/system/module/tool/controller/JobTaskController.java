package com.feng.system.module.tool.controller;

import com.feng.system.common.api.ApiResponse;
import com.feng.system.common.api.PageResult;
import com.feng.system.common.log.BusinessOperationType;
import com.feng.system.common.log.OperLog;
import com.feng.system.common.submit.PreventDuplicateSubmit;
import com.feng.system.module.tool.dto.JobTaskDTO;
import com.feng.system.module.tool.dto.JobTaskQueryDTO;
import com.feng.system.module.tool.service.JobTaskService;
import com.feng.system.module.tool.vo.JobTaskVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tool/job-tasks")
@RequiredArgsConstructor
public class JobTaskController {

    private final JobTaskService jobTaskService;

    @GetMapping
    @SaCheckPermission("tool:job:list")
    public ApiResponse<PageResult<JobTaskVO>> list(JobTaskQueryDTO queryDTO) {
        return ApiResponse.success(jobTaskService.page(queryDTO));
    }

    @PostMapping
    @SaCheckPermission("tool:job:add")
    @PreventDuplicateSubmit
    @OperLog(name = "新增定时任务", type = BusinessOperationType.INSERT)
    public ApiResponse<Void> save(@Valid @RequestBody JobTaskDTO dto) {
        jobTaskService.save(dto);
        return ApiResponse.success("新增任务成功", null);
    }

    @PutMapping("/{id}")
    @SaCheckPermission("tool:job:edit")
    @PreventDuplicateSubmit
    @OperLog(name = "编辑定时任务", type = BusinessOperationType.UPDATE)
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody JobTaskDTO dto) {
        dto.setId(id);
        jobTaskService.update(dto);
        return ApiResponse.success("修改任务成功", null);
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("tool:job:remove")
    @PreventDuplicateSubmit
    @OperLog(name = "删除定时任务", type = BusinessOperationType.DELETE)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        jobTaskService.delete(id);
        return ApiResponse.success("删除任务成功", null);
    }

    @PutMapping("/{id}/pause")
    @SaCheckPermission("tool:job:pause")
    @PreventDuplicateSubmit
    public ApiResponse<Void> pause(@PathVariable Long id) {
        jobTaskService.pause(id);
        return ApiResponse.success("暂停任务成功", null);
    }

    @PutMapping("/{id}/resume")
    @SaCheckPermission(value = {"tool:job:pause", "tool:job:edit"}, mode = SaMode.OR)
    @PreventDuplicateSubmit
    public ApiResponse<Void> resume(@PathVariable Long id) {
        jobTaskService.resume(id);
        return ApiResponse.success("恢复任务成功", null);
    }

    @PutMapping("/{id}/run")
    @SaCheckPermission("tool:job:run")
    @PreventDuplicateSubmit
    public ApiResponse<Void> run(@PathVariable Long id) {
        jobTaskService.runOnce(id);
        return ApiResponse.success("任务已触发执行", null);
    }
}
