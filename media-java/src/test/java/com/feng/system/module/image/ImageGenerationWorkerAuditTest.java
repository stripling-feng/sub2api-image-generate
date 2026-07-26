package com.feng.system.module.image;

import com.feng.system.module.image.exception.ImageApiException;
import com.feng.system.module.image.service.ImageGateway;
import com.feng.system.module.image.service.ImageGenerationWorker;
import com.feng.system.module.image.service.ImageStorageService;

import com.feng.system.module.media.entity.MediaTask;
import com.feng.system.module.media.mapper.MediaTaskMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImageGenerationWorkerAuditTest {
    @Test
    void storesUpstreamErrorPayloadOnFailure() {
        MediaTaskMapper jobs = mock(MediaTaskMapper.class);
        MediaTask current = new MediaTask(); current.setId("job-1"); current.setUpstreamResponse("[]");
        when(jobs.selectById("job-1")).thenReturn(current);
        ImageGateway gateway = mock(ImageGateway.class);
        when(gateway.create(anyString(), anyString(), anyString(), anyMap()))
                .thenThrow(new ImageApiException(502, "failed", null, Map.of("error", "upstream rejected")));
        ImageGenerationWorker worker = new ImageGenerationWorker(jobs, gateway, mock(ImageStorageService.class));

        worker.run("job-1", "https://example.com", "key", "/v1/images/generations", Map.of("prompt", "city"));

        verify(jobs).updateById(argThat((MediaTask job) -> "FAILED".equals(job.getStatus())
                && job.getUpstreamResponse().contains("upstream rejected")));
    }
}
