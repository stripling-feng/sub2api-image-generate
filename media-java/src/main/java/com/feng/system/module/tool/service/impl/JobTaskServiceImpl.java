package com.feng.system.module.tool.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feng.system.common.api.PageResult;
import com.feng.system.common.exception.BusinessException;
import com.feng.system.module.tool.dto.JobTaskDTO;
import com.feng.system.module.tool.dto.JobTaskQueryDTO;
import com.feng.system.module.tool.entity.SysJobTask;
import com.feng.system.module.tool.mapper.SysJobTaskMapper;
import com.feng.system.module.tool.quartz.JobTaskSchedulerManager;
import com.feng.system.module.tool.service.JobTaskService;
import com.feng.system.module.tool.vo.JobTaskVO;
import lombok.RequiredArgsConstructor;
import org.quartz.CronExpression;
import org.quartz.SchedulerException;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JobTaskServiceImpl implements JobTaskService {

    private final SysJobTaskMapper jobTaskMapper;
    private final JobTaskSchedulerManager schedulerManager;
    private final ApplicationContext applicationContext;

    @Override
    public PageResult<JobTaskVO> page(JobTaskQueryDTO queryDTO) {
        LambdaQueryWrapper<SysJobTask> wrapper = new LambdaQueryWrapper<SysJobTask>()
                .eq(SysJobTask::getDeleted, 0)
                .like(queryDTO.getTaskName() != null && !queryDTO.getTaskName().isBlank(), SysJobTask::getTaskName, queryDTO.getTaskName())
                .like(queryDTO.getClassName() != null && !queryDTO.getClassName().isBlank(), SysJobTask::getClassName, queryDTO.getClassName())
                .eq(queryDTO.getStatus() != null, SysJobTask::getStatus, queryDTO.getStatus())
                .orderByDesc(SysJobTask::getId);
        Page<SysJobTask> page = jobTaskMapper.selectPage(new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize()), wrapper);
        List<JobTaskVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(JobTaskDTO dto) {
        validate(dto);
        SysJobTask task = new SysJobTask();
        BeanUtils.copyProperties(dto, task);
        jobTaskMapper.insert(task);
        dto.setId(task.getId());
        schedule(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(JobTaskDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("任务ID不能为空");
        }
        validate(dto);
        SysJobTask oldTask = getRequired(dto.getId());
        SysJobTask newTask = new SysJobTask();
        BeanUtils.copyProperties(dto, newTask);
        jobTaskMapper.updateById(newTask);
        reschedule(oldTask, newTask);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysJobTask task = getRequired(id);
        try {
            schedulerManager.deleteTask(task);
        } catch (SchedulerException ex) {
            throw new BusinessException("删除 Quartz 任务失败: " + ex.getMessage());
        }
        jobTaskMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pause(Long id) {
        SysJobTask task = getRequired(id);
        task.setStatus(0);
        jobTaskMapper.updateById(task);
        try {
            schedulerManager.pauseTask(task);
        } catch (SchedulerException ex) {
            throw new BusinessException("暂停 Quartz 任务失败: " + ex.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resume(Long id) {
        SysJobTask task = getRequired(id);
        task.setStatus(1);
        jobTaskMapper.updateById(task);
        try {
            schedulerManager.resumeTask(task);
        } catch (SchedulerException ex) {
            throw new BusinessException("恢复 Quartz 任务失败: " + ex.getMessage());
        }
    }

    @Override
    public void runOnce(Long id) {
        SysJobTask task = getRequired(id);
        try {
            schedulerManager.runOnce(task);
        } catch (SchedulerException ex) {
            throw new BusinessException("立即执行 Quartz 任务失败: " + ex.getMessage());
        }
    }

    private void validate(JobTaskDTO dto) {
        if (!CronExpression.isValidExpression(dto.getCronExpression())) {
            throw new BusinessException("Cron 表达式不合法");
        }
        validateInvokeTarget(dto.getClassName(), dto.getMethodName(), dto.getMethodParam());
        Long count = jobTaskMapper.selectCount(new LambdaQueryWrapper<SysJobTask>()
                .eq(SysJobTask::getDeleted, 0)
                .eq(SysJobTask::getTaskName, dto.getTaskName())
                .eq(SysJobTask::getTaskGroup, dto.getTaskGroup())
                .ne(dto.getId() != null, SysJobTask::getId, dto.getId()));
        if (count != null && count > 0) {
            throw new BusinessException("任务名称和分组已存在");
        }
    }

    private void validateInvokeTarget(String className, String methodName, String methodParam) {
        try {
            Class<?> clazz = Class.forName(className);
            String[] beanNames = applicationContext.getBeanNamesForType(clazz);
            if (beanNames.length == 0) {
                Constructor<?> constructor = clazz.getDeclaredConstructor();
                constructor.setAccessible(true);
            }
            if (methodParam == null || methodParam.isBlank()) {
                clazz.getMethod(methodName);
            } else {
                clazz.getMethod(methodName, String.class);
            }
        } catch (NoSuchMethodException ex) {
            throw new BusinessException("目标方法不存在，仅支持无参方法或单 String 参数方法");
        } catch (ClassNotFoundException ex) {
            throw new BusinessException("目标类不存在");
        } catch (Exception ex) {
            throw new BusinessException("目标类不可实例化或不可访问: " + ex.getMessage());
        }
    }

    private SysJobTask getRequired(Long id) {
        SysJobTask task = jobTaskMapper.selectById(id);
        if (task == null || task.getDeleted() != null && task.getDeleted() == 1) {
            throw new BusinessException("任务不存在");
        }
        return task;
    }

    private JobTaskVO toVO(SysJobTask task) {
        JobTaskVO vo = new JobTaskVO();
        BeanUtils.copyProperties(task, vo);
        try {
            vo.setNextFireTime(schedulerManager.getNextFireTime(task));
            vo.setPreviousFireTime(schedulerManager.getPreviousFireTime(task));
            vo.setSchedulerState(schedulerManager.getSchedulerState(task));
        } catch (SchedulerException ex) {
            vo.setSchedulerState("ERROR");
        }
        return vo;
    }

    private void schedule(SysJobTask task) {
        try {
            schedulerManager.scheduleTask(task);
        } catch (SchedulerException ex) {
            throw new BusinessException("创建 Quartz 任务失败: " + ex.getMessage());
        }
    }

    private void reschedule(SysJobTask oldTask, SysJobTask newTask) {
        try {
            schedulerManager.rescheduleTask(oldTask, newTask);
        } catch (SchedulerException ex) {
            throw new BusinessException("更新 Quartz 任务失败: " + ex.getMessage());
        }
    }
}
