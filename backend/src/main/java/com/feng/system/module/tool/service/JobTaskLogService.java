package com.feng.system.module.tool.service;

import com.feng.system.common.api.PageResult;
import com.feng.system.module.tool.dto.JobTaskLogQueryDTO;
import com.feng.system.module.tool.entity.SysJobTask;
import com.feng.system.module.tool.entity.SysJobTaskLog;

import java.time.LocalDateTime;

public interface JobTaskLogService {
    PageResult<SysJobTaskLog> page(JobTaskLogQueryDTO queryDTO);
    void saveLog(SysJobTask task, Integer executeStatus, String executeResult, String errorMessage,
                 LocalDateTime startTime, LocalDateTime endTime, long durationMs);
}
