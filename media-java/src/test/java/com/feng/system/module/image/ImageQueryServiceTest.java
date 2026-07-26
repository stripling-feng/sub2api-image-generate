package com.feng.system.module.image;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.image.service.ImageQueryService;
import com.feng.system.module.image.service.ImageStorageService;
import com.feng.system.module.media.entity.MediaTask;
import com.feng.system.module.media.mapper.MediaTaskMapper;
import com.feng.system.module.media.service.MediaTaskResultService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ImageQueryServiceTest {
    @Test
    void historyOrdersPendingTasksBeforeNewestCompletedTasks() {
        MediaTaskMapper tasks = mock(MediaTaskMapper.class);
        MediaTaskResultService results = mock(MediaTaskResultService.class);
        when(tasks.selectPage(any(), any())).thenReturn(new Page<MediaTask>().setRecords(List.of()));
        ImageQueryService service = new ImageQueryService(tasks, results, mock(ImageStorageService.class), new ObjectMapper());

        service.history("api-key", 1, 10);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<MediaTask>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(tasks).selectPage(any(), wrapper.capture());
        String sql = wrapper.getValue().getSqlSegment();
        assertTrue(sql.contains("CASE WHEN status = 'PENDING' THEN 0 ELSE 1 END"));
        assertTrue(sql.contains("created_at DESC"));
    }
}
