package com.feng.system.module.tool.quartz;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feng.system.module.tool.entity.SysJobTask;
import com.feng.system.module.tool.mapper.SysJobTaskMapper;
import lombok.RequiredArgsConstructor;
import org.quartz.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JobTaskSchedulerManager {

    private final Scheduler scheduler;
    private final SysJobTaskMapper jobTaskMapper;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() throws SchedulerException {
        scheduler.clear();
        List<SysJobTask> tasks = jobTaskMapper.selectList(new LambdaQueryWrapper<SysJobTask>()
                .eq(SysJobTask::getDeleted, 0));
        for (SysJobTask task : tasks) {
            scheduleTask(task);
        }
    }

    public void scheduleTask(SysJobTask task) throws SchedulerException {
        JobKey jobKey = jobKey(task);
        TriggerKey triggerKey = triggerKey(task);
        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey);
        }

        JobDetail jobDetail = JobBuilder.newJob(QuartzJobExecutor.class)
                .withIdentity(jobKey)
                .usingJobData(QuartzJobExecutor.TASK_ID, task.getId())
                .build();

        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(jobDetail)
                .withSchedule(CronScheduleBuilder.cronSchedule(task.getCronExpression())
                        .withMisfireHandlingInstructionDoNothing())
                .build();

        scheduler.scheduleJob(jobDetail, trigger);
        if (Integer.valueOf(0).equals(task.getStatus())) {
            scheduler.pauseJob(jobKey);
        }
    }

    public void rescheduleTask(SysJobTask oldTask, SysJobTask newTask) throws SchedulerException {
        deleteTask(oldTask);
        scheduleTask(newTask);
    }

    public void deleteTask(SysJobTask task) throws SchedulerException {
        scheduler.deleteJob(jobKey(task));
    }

    public void pauseTask(SysJobTask task) throws SchedulerException {
        scheduler.pauseJob(jobKey(task));
    }

    public void resumeTask(SysJobTask task) throws SchedulerException {
        scheduler.resumeJob(jobKey(task));
    }

    public void runOnce(SysJobTask task) throws SchedulerException {
        scheduler.triggerJob(jobKey(task));
    }

    public LocalDateTime getNextFireTime(SysJobTask task) throws SchedulerException {
        Trigger trigger = scheduler.getTrigger(triggerKey(task));
        return toLocalDateTime(trigger == null ? null : trigger.getNextFireTime());
    }

    public LocalDateTime getPreviousFireTime(SysJobTask task) throws SchedulerException {
        Trigger trigger = scheduler.getTrigger(triggerKey(task));
        return toLocalDateTime(trigger == null ? null : trigger.getPreviousFireTime());
    }

    public String getSchedulerState(SysJobTask task) throws SchedulerException {
        Trigger.TriggerState state = scheduler.getTriggerState(triggerKey(task));
        return switch (state) {
            case PAUSED -> "PAUSED";
            case BLOCKED -> "BLOCKED";
            case COMPLETE -> "COMPLETE";
            case ERROR -> "ERROR";
            case NORMAL -> "NORMAL";
            default -> "NONE";
        };
    }

    private JobKey jobKey(SysJobTask task) {
        return JobKey.jobKey(task.getTaskName(), task.getTaskGroup());
    }

    private TriggerKey triggerKey(SysJobTask task) {
        return TriggerKey.triggerKey(task.getTaskName(), task.getTaskGroup());
    }

    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault());
    }
}
