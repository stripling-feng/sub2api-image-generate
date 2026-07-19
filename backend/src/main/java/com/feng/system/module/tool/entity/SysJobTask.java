package com.feng.system.module.tool.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.feng.system.module.system.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_job_task")
public class SysJobTask extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskName;
    private String taskGroup;
    private String cronExpression;
    private String className;
    private String methodName;
    private String methodParam;
    private String handlerKey;
    private String handlerParam;
    private Integer status;
    private String remark;
}
