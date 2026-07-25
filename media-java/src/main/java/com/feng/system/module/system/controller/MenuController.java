package com.feng.system.module.system.controller;

import com.feng.system.common.api.ApiResponse;
import com.feng.system.common.log.BusinessOperationType;
import com.feng.system.common.log.OperLog;
import com.feng.system.common.submit.PreventDuplicateSubmit;
import com.feng.system.module.system.dto.MenuDTO;
import com.feng.system.module.system.service.MenuService;
import com.feng.system.module.system.vo.MenuTreeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @GetMapping("/tree")
    public ApiResponse<List<MenuTreeVO>> tree() {
        return ApiResponse.success(menuService.tree());
    }

    @PostMapping
    @SaCheckPermission("system:menu:add")
    @PreventDuplicateSubmit
    @OperLog(name = "新增菜单", type = BusinessOperationType.INSERT)
    public ApiResponse<Void> save(@Valid @RequestBody MenuDTO dto) {
        menuService.save(dto);
        return ApiResponse.success("新增成功", null);
    }

    @PutMapping("/{id}")
    @SaCheckPermission("system:menu:edit")
    @PreventDuplicateSubmit
    @OperLog(name = "编辑菜单", type = BusinessOperationType.UPDATE)
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody MenuDTO dto) {
        dto.setId(id);
        menuService.update(dto);
        return ApiResponse.success("修改成功", null);
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("system:menu:remove")
    @PreventDuplicateSubmit
    @OperLog(name = "删除菜单", type = BusinessOperationType.DELETE)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
