package com.feng.system.module.tool.dto;

import lombok.Data;

@Data
public class JobTaskQueryDTO {
    private Long pageNum = 1L;
    private Long pageSize = 10L;
    private String taskName;
    private String className;
    private Integer status;
}
