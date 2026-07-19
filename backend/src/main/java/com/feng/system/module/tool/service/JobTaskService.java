package com.feng.system.module.tool.service;

import com.feng.system.common.api.PageResult;
import com.feng.system.module.tool.dto.JobTaskDTO;
import com.feng.system.module.tool.dto.JobTaskQueryDTO;
import com.feng.system.module.tool.vo.JobTaskVO;

public interface JobTaskService {
    PageResult<JobTaskVO> page(JobTaskQueryDTO queryDTO);
    void save(JobTaskDTO dto);
    void update(JobTaskDTO dto);
    void delete(Long id);
    void pause(Long id);
    void resume(Long id);
    void runOnce(Long id);
}
