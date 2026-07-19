package com.feng.system.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DictTypeDTO {
    private Long id;

    @NotBlank(message = "字典名称不能为空")
    private String typeName;

    @NotBlank(message = "字典类型不能为空")
    private String typeCode;

    private String remark;
}