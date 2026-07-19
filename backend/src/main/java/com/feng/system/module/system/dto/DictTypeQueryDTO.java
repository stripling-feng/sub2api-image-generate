package com.feng.system.module.system.dto;

import lombok.Data;

@Data
public class DictTypeQueryDTO {
    private Long pageNum = 1L;
    private Long pageSize = 10L;
    private String typeName;
    private String typeCode;
}