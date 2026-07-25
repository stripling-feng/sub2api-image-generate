package com.feng.system.module.system.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.feng.system.common.api.ApiResponse;
import com.feng.system.module.system.entity.SysPost;
import com.feng.system.module.system.entity.SysRole;
import com.feng.system.module.system.service.DeptService;
import com.feng.system.module.system.service.DictService;
import com.feng.system.module.system.service.MenuService;
import com.feng.system.module.system.service.PostService;
import com.feng.system.module.system.service.RoleService;
import com.feng.system.module.system.service.SystemConfigService;
import com.feng.system.module.system.vo.DictOptionVO;
import com.feng.system.module.system.vo.MenuTreeVO;
import com.feng.system.module.system.vo.PublicSystemConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/business-dicts")
@RequiredArgsConstructor
public class BusinessDictController {

    private final DeptService deptService;
    private final PostService postService;
    private final RoleService roleService;
    private final DictService dictService;
    private final MenuService menuService;
    private final SystemConfigService systemConfigService;

    @GetMapping("/depts/tree")
    public ApiResponse<List<MenuTreeVO>> deptTree() {
        return ApiResponse.success(deptService.tree());
    }

    @GetMapping("/posts/options")
    public ApiResponse<List<SysPost>> postOptions() {
        return ApiResponse.success(postService.options());
    }

    @GetMapping("/roles/options")
    public ApiResponse<List<SysRole>> roleOptions() {
        return ApiResponse.success(roleService.options());
    }

    @GetMapping("/dicts/options/{typeCode}")
    public ApiResponse<List<DictOptionVO>> dictOptions(@PathVariable String typeCode) {
        return ApiResponse.success(dictService.options(typeCode));
    }

    @GetMapping("/menus/current")
    public ApiResponse<List<MenuTreeVO>> currentMenus() {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(menuService.userMenuTree(userId));
    }

    @GetMapping("/menus/tree")
    public ApiResponse<List<MenuTreeVO>> menuTree() {
        return ApiResponse.success(menuService.tree());
    }

    @GetMapping("/site-config")
    public ApiResponse<PublicSystemConfigVO> siteConfig() {
        return ApiResponse.success(systemConfigService.getPublicConfig());
    }

    @GetMapping("/default-password")
    public ApiResponse<String> defaultPassword() {
        return ApiResponse.success(systemConfigService.getDefaultPassword());
    }
}
