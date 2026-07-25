package com.feng.system.common.log.service;

import com.feng.system.common.api.PageResult;
import com.feng.system.common.log.dto.OperLogQueryDTO;
import com.feng.system.common.log.entity.SysOperLog;

public interface OperLogService {
    PageResult<SysOperLog> page(OperLogQueryDTO queryDTO);
}
