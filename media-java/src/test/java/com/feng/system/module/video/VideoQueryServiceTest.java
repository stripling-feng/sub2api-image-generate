package com.feng.system.module.video;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.media.entity.MediaTask;
import com.feng.system.module.media.entity.MediaTaskResult;
import com.feng.system.module.media.mapper.MediaTaskMapper;
import com.feng.system.module.media.service.MediaTaskResultService;
import com.feng.system.module.video.service.VideoQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class VideoQueryServiceTest {
    @Test
    void historyOrdersPendingTasksBeforeNewestCompletedTasks() {
        MediaTaskMapper tasks = mock(MediaTaskMapper.class);
        MediaTaskResultService results = mock(MediaTaskResultService.class);
        when(tasks.selectPage(any(), any())).thenReturn(new Page<MediaTask>().setRecords(List.of()));
        VideoQueryService service = new VideoQueryService(tasks, results, new ObjectMapper());

        service.history("api-key", 1, 10);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<MediaTask>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(tasks).selectPage(any(), wrapper.capture());
        String sql = wrapper.getValue().getSqlSegment();
        assertTrue(sql.contains("CASE WHEN status = 'PENDING' THEN 0 ELSE 1 END"));
        assertTrue(sql.contains("created_at DESC"));
    }

    @Test
    void deletesOnlyCompletedVideoTasksOwnedByApiKey() {
        MediaTaskMapper tasks = mock(MediaTaskMapper.class);
        MediaTaskResultService results = mock(MediaTaskResultService.class);
        VideoQueryService service = new VideoQueryService(tasks, results, new ObjectMapper());
        MediaTask first = task("job-1"), second = task("job-2");
        MediaTaskResult firstResult = result("result-1", "job-1");
        MediaTaskResult secondResult = result("result-2", "job-2");
        when(tasks.selectOne(any())).thenReturn(first);
        when(results.list(List.of("job-1"))).thenReturn(List.of(firstResult));
        when(results.metadata(firstResult)).thenReturn(Map.of());

        service.deleteJob("plain-key", "job-1");

        verify(results).deleteByTaskIds(List.of("job-1"));
        verify(tasks).deleteById("job-1");

        when(tasks.selectList(any())).thenReturn(List.of(first, second));
        when(results.list(anyList())).thenReturn(List.of(firstResult, secondResult));
        when(results.metadata(secondResult)).thenReturn(Map.of());
        when(tasks.deleteBatchIds(anyList())).thenReturn(2);

        service.deleteJobs("plain-key");

        verify(results).deleteByTaskIds(List.of("job-1", "job-2"));
        verify(tasks).deleteBatchIds(List.of("job-1", "job-2"));
    }

    private static MediaTask task(String id) {
        MediaTask task = new MediaTask();
        task.setId(id); task.setApiKey("plain-key"); task.setTaskType("VIDEO"); task.setStatus("SUCCEEDED");
        return task;
    }

    private static MediaTaskResult result(String id, String taskId) {
        MediaTaskResult result = new MediaTaskResult(); result.setId(id); result.setTaskId(taskId); return result;
    }
}
