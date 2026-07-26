package com.feng.system.module.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.media.entity.MediaTaskResult;
import com.feng.system.module.media.mapper.MediaTaskResultMapper;
import com.feng.system.module.media.service.MediaTaskResultService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MediaTaskResultServiceTest {

    @Test
    void saveIfAbsentDoesNotInsertTheSameTaskResultTwice() {
        MediaTaskResultMapper mapper = mock(MediaTaskResultMapper.class);
        MediaTaskResult existing = new MediaTaskResult();
        existing.setTaskId("task-1");
        existing.setSortOrder(0);
        when(mapper.selectOne(any())).thenReturn(existing);

        MediaTaskResultService service = new MediaTaskResultService(mapper, new ObjectMapper());
        MediaTaskResult result = service.saveIfAbsent("task-1", 0, "https://cdn/result.png", Map.of("mimeType", "image/png"));

        assertEquals(existing, result);
        verify(mapper, never()).insert(any());
    }
}
