package com.feng.system.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class RoleDTO {
    private Long id;
    @NotBlank(message = "角色名称不能为空")
    private String roleName;
    @NotBlank(message = "角色标识不能为空")
    private String roleKey;
    @NotNull(message = "排序不能为空")
    private Integer roleSort;
    private String remark;
    private List<Long> menuIds;
}