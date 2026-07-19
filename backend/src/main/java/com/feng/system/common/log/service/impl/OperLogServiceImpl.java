package com.feng.system.common.log.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feng.system.common.api.PageResult;
import com.feng.system.common.log.dto.OperLogQueryDTO;
import com.feng.system.common.log.entity.SysOperLog;
import com.feng.system.common.log.mapper.SysOperLogMapper;
import com.feng.system.common.log.service.OperLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OperLogServiceImpl implements OperLogService {

    private final SysOperLogMapper operLogMapper;

    @Override
    public PageResult<SysOperLog> page(OperLogQueryDTO queryDTO) {
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<SysOperLog>()
                .like(StringUtils.hasText(queryDTO.getApiName()), SysOperLog::getApiName, queryDTO.getApiName())
                .like(StringUtils.hasText(queryDTO.getOperatorName()), SysOperLog::getOperatorName, queryDTO.getOperatorName())
                .eq(queryDTO.getSuccess() != null, SysOperLog::getSuccess, queryDTO.getSuccess())
                .orderByDesc(SysOperLog::getOperationTime)
                .orderByDesc(SysOperLog::getId);
        Page<SysOperLog> page = operLogMapper.selectPage(new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize()), wrapper);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }
}