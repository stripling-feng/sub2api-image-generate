package com.feng.system.module.tool.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.feng.system.module.system.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_job_task_log")
public class SysJobTaskLog extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String taskName;
    private String taskGroup;
    private String className;
    private String methodName;
    private String methodParam;
    private Integer executeStatus;
    private String executeResult;
    private String errorMessage;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
}
