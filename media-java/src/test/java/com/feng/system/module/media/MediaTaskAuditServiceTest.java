package com.feng.system.module.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.common.api.ApiResponse;
import com.feng.system.module.image.dto.GenerationAcceptedResponse;
import com.feng.system.module.media.entity.MediaTask;
import com.feng.system.module.media.mapper.MediaTaskMapper;
import com.feng.system.module.media.service.MediaTaskAuditService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MediaTaskAuditServiceTest {

    @Test
    void storesCompleteApiResponseForEveryFanoutTask() throws Exception {
        MediaTaskMapper tasks = mock(MediaTaskMapper.class);
        MediaTask first = new MediaTask(); first.setId("task-1"); first.setStatus("PENDING"); first.setProgress(25);
        MediaTask second = new MediaTask(); second.setId("task-2"); second.setStatus("SUCCEEDED"); second.setProgress(100);
        when(tasks.selectList(any())).thenReturn(List.of(first, second));
        ObjectMapper json = new ObjectMapper();
        MediaTaskAuditService service = new MediaTaskAuditService(tasks, json);

        service.recordSystemResponse("plain-key", "IMAGE", "request-1",
                ApiResponse.success(new GenerationAcceptedResponse("request-1", 2)));

        ArgumentCaptor<MediaTask> updates = ArgumentCaptor.forClass(MediaTask.class);
        verify(tasks, times(2)).updateById(updates.capture());
        for (MediaTask task : updates.getAllValues()) {
            JsonNode response = json.readTree(task.getSystemResponse());
            assertEquals(200, response.path("code").asInt());
            assertEquals("request-1", response.path("data").path("requestId").asText());
            assertEquals(2, response.path("data").path("count").asInt());
            assertNull(task.getStatus());
            assertNull(task.getProgress());
        }
    }
}
