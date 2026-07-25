package com.feng.system.module.tool.quartz;

import com.feng.system.config.SpringContextHolder;
import com.feng.system.module.tool.entity.SysJobTask;
import com.feng.system.module.tool.mapper.SysJobTaskMapper;
import com.feng.system.module.tool.service.JobTaskLogService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

@DisallowConcurrentExecution
public class QuartzJobExecutor implements Job {

    public static final String TASK_ID = "taskId";

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Long taskId = context.getMergedJobDataMap().getLong(TASK_ID);
        SysJobTaskMapper mapper = SpringContextHolder.getBean(SysJobTaskMapper.class);
        JobTaskLogService jobTaskLogService = SpringContextHolder.getBean(JobTaskLogService.class);
        SysJobTask task = mapper.selectById(taskId);
        if (task == null || task.getDeleted() != null && task.getDeleted() == 1) {
            return;
        }

        LocalDateTime startTime = LocalDateTime.now();
        long startMillis = System.currentTimeMillis();
        try {
            String result = invoke(task);
            LocalDateTime endTime = LocalDateTime.now();
            jobTaskLogService.saveLog(task, 1, result, null, startTime, endTime, System.currentTimeMillis() - startMillis);
        } catch (Exception ex) {
            LocalDateTime endTime = LocalDateTime.now();
            String message = buildErrorMessage(ex);
            jobTaskLogService.saveLog(task, 0, null, message, startTime, endTime, System.currentTimeMillis() - startMillis);
            throw new JobExecutionException("Invoke scheduled method failed: " + message, ex);
        }
    }

    private String invoke(SysJobTask task) throws Exception {
        Class<?> clazz = Class.forName(task.getClassName());
        Object target = resolveTarget(clazz);
        String methodParam = task.getMethodParam();
        Object result;
        if (methodParam == null || methodParam.isBlank()) {
            Method method = clazz.getMethod(task.getMethodName());
            method.setAccessible(true);
            result = method.invoke(target);
        } else {
            Method method = clazz.getMethod(task.getMethodName(), String.class);
            method.setAccessible(true);
            result = method.invoke(target, methodParam);
        }
        return result == null ? "SUCCESS" : String.valueOf(result);
    }

    private Object resolveTarget(Class<?> clazz) throws Exception {
        ApplicationContext applicationContext = SpringContextHolder.getApplicationContext();
        String[] beanNames = applicationContext.getBeanNamesForType(clazz);
        if (beanNames.length > 0) {
            return applicationContext.getBean(clazz);
        }
        Constructor<?> constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private String buildErrorMessage(Exception ex) {
        Throwable target = ex instanceof InvocationTargetException invocationTargetException
                ? invocationTargetException.getTargetException()
                : ex;
        String message = target.getMessage();
        String summary = target.getClass().getName() + (message == null || message.isBlank() ? "" : ": " + message);
        return summary.length() > 1000 ? summary.substring(0, 1000) : summary;
    }
}
