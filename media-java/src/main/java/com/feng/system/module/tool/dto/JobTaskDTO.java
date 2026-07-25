package com.feng.system.module.tool.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class JobTaskDTO {
    private Long id;

    @NotBlank(message = "任务名称不能为空")
    private String taskName;

    @NotBlank(message = "任务分组不能为空")
    private String taskGroup;

    @NotBlank(message = "Cron 表达式不能为空")
    private String cronExpression;

    @NotBlank(message = "类名不能为空")
    private String className;

    @NotBlank(message = "方法名不能为空")
    private String methodName;

    private String methodParam;

    @NotNull(message = "状态不能为空")
    private Integer status;

    private String remark;
}
