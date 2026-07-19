package com.feng.system.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DictDataDTO {
    private Long id;

    @NotNull(message = "字典类型不能为空")
    private Long typeId;

    @NotBlank(message = "字典标签不能为空")
    private String dictLabel;

    @NotBlank(message = "字典键值不能为空")
    private String dictValue;

    @NotNull(message = "排序不能为空")
    private Integer dictSort;

    private String tagType;
    private String cssClass;
    private String remark;
}