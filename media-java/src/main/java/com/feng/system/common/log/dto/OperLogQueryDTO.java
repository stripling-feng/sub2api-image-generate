package com.feng.system.common.log.dto;

import lombok.Data;

@Data
public class OperLogQueryDTO {
    private Long pageNum = 1L;
    private Long pageSize = 10L;
    private String apiName;
    private String operatorName;
    private Integer success;
}