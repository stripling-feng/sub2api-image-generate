package com.feng.system.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PostDTO {
    private Long id;

    @NotBlank(message = "岗位编码不能为空")
    private String postCode;

    @NotBlank(message = "岗位名称不能为空")
    private String postName;

    @NotNull(message = "排序不能为空")
    private Integer postSort;

    private String remark;
}