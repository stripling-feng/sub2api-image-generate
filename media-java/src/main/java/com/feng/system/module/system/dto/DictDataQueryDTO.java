package com.feng.system.module.system.dto;

import lombok.Data;

@Data
public class DictDataQueryDTO {
    private Long pageNum = 1L;
    private Long pageSize = 10L;
    private Long typeId;
    private String typeCode;
    private String dictLabel;
    private String dictValue;
}