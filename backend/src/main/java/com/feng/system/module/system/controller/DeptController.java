package com.feng.system.module.system.controller;

import com.feng.system.common.api.ApiResponse;
import com.feng.system.common.log.BusinessOperationType;
import com.feng.system.common.log.OperLog;
import com.feng.system.common.submit.PreventDuplicateSubmit;
import com.feng.system.module.system.dto.DeptDTO;
import com.feng.system.module.system.service.DeptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system/depts")
@RequiredArgsConstructor
public class DeptController {

    private final DeptService deptService;

    @PostMapping
    @SaCheckPermission("system:dept:add")
    @PreventDuplicateSubmit
    @OperLog(name = "新增部门", type = BusinessOperationType.INSERT)
    public ApiResponse<Void> save(@Valid @RequestBody DeptDTO dto) {
        deptService.save(dto);
        return ApiResponse.success("新增成功", null);
    }

    @PutMapping("/{id}")
    @SaCheckPermission("system:dept:edit")
    @PreventDuplicateSubmit
    @OperLog(name = "编辑部门", type = BusinessOperationType.UPDATE)
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody DeptDTO dto) {
        dto.setId(id);
        deptService.update(dto);
        return ApiResponse.success("修改成功", null);
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("system:dept:remove")
    @PreventDuplicateSubmit
    @OperLog(name = "删除部门", type = BusinessOperationType.DELETE)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        deptService.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
