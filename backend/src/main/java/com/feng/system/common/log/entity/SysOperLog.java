package com.feng.system.common.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_oper_log")
public class SysOperLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String apiName;
    private String businessType;
    private String methodName;
    private String requestUri;
    private Long operatorId;
    private String operatorName;
    private String ipAddress;
    private Integer success;
    private String errorMessage;
    private String afterData;
    private LocalDateTime operationTime;
}
