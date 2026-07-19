package com.feng.system.module.tool.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feng.system.common.api.PageResult;
import com.feng.system.module.tool.dto.JobTaskLogQueryDTO;
import com.feng.system.module.tool.entity.SysJobTask;
import com.feng.system.module.tool.entity.SysJobTaskLog;
import com.feng.system.module.tool.mapper.SysJobTaskLogMapper;
import com.feng.system.module.tool.service.JobTaskLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class JobTaskLogServiceImpl implements JobTaskLogService {

    private final SysJobTaskLogMapper jobTaskLogMapper;

    @Override
    public PageResult<SysJobTaskLog> page(JobTaskLogQueryDTO queryDTO) {
        LambdaQueryWrapper<SysJobTaskLog> wrapper = new LambdaQueryWrapper<SysJobTaskLog>()
                .eq(SysJobTaskLog::getDeleted, 0)
                .eq(queryDTO.getTaskId() != null, SysJobTaskLog::getTaskId, queryDTO.getTaskId())
                .eq(queryDTO.getExecuteStatus() != null, SysJobTaskLog::getExecuteStatus, queryDTO.getExecuteStatus())
                .orderByDesc(SysJobTaskLog::getId);
        Page<SysJobTaskLog> page = jobTaskLogMapper.selectPage(new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize()), wrapper);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    @Override
    public void saveLog(SysJobTask task, Integer executeStatus, String executeResult, String errorMessage,
                        LocalDateTime startTime, LocalDateTime endTime, long durationMs) {
        SysJobTaskLog log = new SysJobTaskLog();
        log.setTaskId(task.getId());
        log.setTaskName(task.getTaskName());
        log.setTaskGroup(task.getTaskGroup());
        log.setClassName(task.getClassName());
        log.setMethodName(task.getMethodName());
        log.setMethodParam(task.getMethodParam());
        log.setExecuteStatus(executeStatus);
        log.setExecuteResult(executeResult);
        log.setErrorMessage(errorMessage);
        log.setStartTime(startTime);
        log.setEndTime(endTime);
        log.setDurationMs(durationMs);
        jobTaskLogMapper.insert(log);
    }
}
