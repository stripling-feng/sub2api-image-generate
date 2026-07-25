package com.feng.system.module.video;

import com.feng.system.module.video.service.VideoMaterialUploadService;
import com.feng.system.module.video.service.VideoQueryService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.video.entity.VideoGenerationJob;
import com.feng.system.module.video.mapper.GeneratedVideoMapper;
import com.feng.system.module.video.mapper.VideoGenerationJobMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VideoQueryServiceTest {
    @Test
    void deletesGeneratedFilesForVideoJobAndBulkDeletion() throws Exception {
        VideoGenerationJobMapper jobs = mock(VideoGenerationJobMapper.class);
        GeneratedVideoMapper videos = mock(GeneratedVideoMapper.class);
        VideoMaterialUploadService storage = mock(VideoMaterialUploadService.class);
        VideoQueryService service = VideoQueryService.class.getConstructor(VideoGenerationJobMapper.class,
                GeneratedVideoMapper.class, ObjectMapper.class, VideoMaterialUploadService.class)
                .newInstance(jobs, videos, new ObjectMapper(), storage);
        VideoGenerationJob first = job("job-1"), second = job("job-2");
        when(jobs.selectById("job-1")).thenReturn(first);

        service.deleteJob("profile-1", "job-1");
        verify(storage).deleteGenerated("job-1");

        when(jobs.selectList(any())).thenReturn(List.of(first, second));
        when(jobs.delete(any())).thenReturn(2);
        service.deleteJobs("profile-1");
        verify(storage).deleteGenerated("job-2");
    }

    private static VideoGenerationJob job(String id) {
        VideoGenerationJob job = new VideoGenerationJob(); job.setId(id); job.setProfileId("profile-1"); job.setStatus("SUCCEEDED");
        return job;
    }
}
