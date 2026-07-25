package com.feng.system.module.tool.dto;

import lombok.Data;

@Data
public class JobTaskLogQueryDTO {
    private Long pageNum = 1L;
    private Long pageSize = 10L;
    private Long taskId;
    private Integer executeStatus;
}
