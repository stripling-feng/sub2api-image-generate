package com.feng.system.module.system.controller;

import com.feng.system.common.api.ApiResponse;
import com.feng.system.common.api.PageResult;
import com.feng.system.common.log.BusinessOperationType;
import com.feng.system.common.log.OperLog;
import com.feng.system.common.submit.PreventDuplicateSubmit;
import com.feng.system.module.system.dto.RoleDTO;
import com.feng.system.module.system.dto.RoleQueryDTO;
import com.feng.system.module.system.entity.SysRole;
import com.feng.system.module.system.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @SaCheckPermission("system:role:list")
    public ApiResponse<PageResult<SysRole>> list(RoleQueryDTO queryDTO) {
        return ApiResponse.success(roleService.page(queryDTO));
    }

    @GetMapping("/{id}/menu-ids")
    @SaCheckPermission("system:role:query")
    public ApiResponse<java.util.List<Long>> menuIds(@PathVariable Long id) {
        return ApiResponse.success(roleService.menuIds(id));
    }

    @PostMapping
    @SaCheckPermission("system:role:add")
    @PreventDuplicateSubmit
    @OperLog(name = "新增角色", type = BusinessOperationType.INSERT)
    public ApiResponse<Void> save(@Valid @RequestBody RoleDTO dto) {
        roleService.save(dto);
        return ApiResponse.success("新增成功", null);
    }

    @PutMapping("/{id}")
    @SaCheckPermission("system:role:edit")
    @PreventDuplicateSubmit
    @OperLog(name = "编辑角色", type = BusinessOperationType.UPDATE)
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody RoleDTO dto) {
        dto.setId(id);
        roleService.update(dto);
        return ApiResponse.success("修改成功", null);
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("system:role:remove")
    @PreventDuplicateSubmit
    @OperLog(name = "删除角色", type = BusinessOperationType.DELETE)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
