package com.feng.system.module.image;

import com.feng.system.module.image.entity.GenerationJob;
import com.feng.system.module.image.mapper.GenerationJobMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImageGenerationWorkerAuditTest {
    @Test
    void storesUpstreamErrorPayloadOnFailure() {
        GenerationJobMapper jobs = mock(GenerationJobMapper.class);
        ImageGateway gateway = mock(ImageGateway.class);
        when(gateway.create(anyString(), anyString(), anyString(), anyString(), anyMap(), anyList(), isNull()))
                .thenThrow(new ImageApiException(502, "failed", null, Map.of("error", "upstream rejected")));
        ImageGenerationWorker worker = new ImageGenerationWorker(jobs, gateway, mock(ImageStorageService.class));

        worker.run("job-1", "https://example.com", "key", "/v1/images/generations", "/v1/images/edits",
                Map.of("prompt", "city"), List.of(), null);

        verify(jobs).updateById(argThat((GenerationJob job) -> "FAILED".equals(job.getStatus())
                && job.getRawResponses().contains("upstream rejected")));
    }
}
