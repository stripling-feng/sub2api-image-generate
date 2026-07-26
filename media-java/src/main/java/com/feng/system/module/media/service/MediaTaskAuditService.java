package com.feng.system.module.media.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.image.support.GenerationAuditJson;
import com.feng.system.module.media.entity.MediaTask;
import com.feng.system.module.media.mapper.MediaTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaTaskAuditService {
    private final MediaTaskMapper tasks;
    private final ObjectMapper objectMapper;

    public String json(Object value) {
        try {
            return GenerationAuditJson.stringify(objectMapper.convertValue(value, Object.class));
        } catch (IllegalArgumentException ignored) {
            return GenerationAuditJson.stringify(value);
        }
    }

    public String append(String existing, String phase, Object payload) {
        return GenerationAuditJson.append(existing, phase, payload);
    }

    public void recordSystemResponse(String apiKey, String taskType, String requestId, Object response) {
        String serialized = json(response);
        List<MediaTask> matches = tasks.selectList(new LambdaQueryWrapper<MediaTask>()
                .eq(MediaTask::getApiKey, apiKey)
                .eq(MediaTask::getTaskType, taskType)
                .eq(MediaTask::getRequestId, requestId));
        for (MediaTask task : matches) {
            MediaTask update = new MediaTask();
            update.setId(task.getId());
            update.setSystemResponse(serialized);
            tasks.updateById(update);
        }
    }
}
