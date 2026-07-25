package com.feng.system.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MenuDTO {
    private Long id;

    @NotNull(message = "上级菜单不能为空")
    private Long parentId;

    @NotBlank(message = "菜单名称不能为空")
    private String menuName;

    @NotNull(message = "菜单类型不能为空")
    private Integer menuType;

    private String path;
    private String component;
    private String permission;
    private String icon;

    @NotNull(message = "排序不能为空")
    private Integer menuSort;

    @NotNull(message = "显示状态不能为空")
    private Integer visible;
}