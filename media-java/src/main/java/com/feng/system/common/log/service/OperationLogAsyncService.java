package com.feng.system.common.log.service;

import com.feng.system.common.log.entity.SysOperLog;
import com.feng.system.common.log.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OperationLogAsyncService {

    private final SysOperLogMapper operLogMapper;

    @Async
    public void save(SysOperLog operLog) {
        operLogMapper.insert(operLog);
    }
}
