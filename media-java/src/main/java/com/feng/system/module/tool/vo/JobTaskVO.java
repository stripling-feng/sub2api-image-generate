package com.feng.system.module.tool.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class JobTaskVO {
    private Long id;
    private String taskName;
    private String taskGroup;
    private String cronExpression;
    private String className;
    private String methodName;
    private String methodParam;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime previousFireTime;
    private LocalDateTime nextFireTime;
    private String schedulerState;
}
