package com.feng.system.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeptDTO {
    private Long id;

    @NotNull(message = "上级部门不能为空")
    private Long parentId;

    @NotBlank(message = "部门名称不能为空")
    private String deptName;

    @NotNull(message = "排序不能为空")
    private Integer deptSort;

    private String leader;
    private String phone;
    private String email;
}