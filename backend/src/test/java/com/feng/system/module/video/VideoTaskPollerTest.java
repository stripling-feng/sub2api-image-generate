package com.feng.system.module.video;

import com.feng.system.module.image.ImageModelConfigService;
import com.feng.system.module.image.Sub2apiBillingService;
import com.feng.system.module.image.entity.AiModel;
import com.feng.system.module.image.entity.ModelProvider;
import com.feng.system.module.video.entity.VideoGenerationJob;
import com.feng.system.module.video.mapper.GeneratedVideoMapper;
import com.feng.system.module.video.mapper.VideoGenerationJobMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VideoTaskPollerTest {

    @Test
    void completedTaskStoresUrlAndSettlesRequestedDuration() {
        VideoGenerationJobMapper jobs = mock(VideoGenerationJobMapper.class);
        GeneratedVideoMapper videos = mock(GeneratedVideoMapper.class);
        VideoGateway gateway = mock(VideoGateway.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        ImageModelConfigService configs = mock(ImageModelConfigService.class);
        VideoTaskPoller poller = new VideoTaskPoller(jobs, videos, gateway, billing, configs);

        AiModel model = new AiModel();
        model.setId(7L); model.setModelKey("seedance-2.0"); model.setUpstreamModel("seedance-2.0");
        model.setGenerationPath("/v1/videos");
        ModelProvider provider = new ModelProvider();
        provider.setBaseUrl("https://api.example.com"); provider.setVideoApiKey("secret");
        when(configs.requireVideo(7L)).thenReturn(new ImageModelConfigService.RuntimeModel(model, provider));
        when(gateway.query(anyString(), anyString(), anyString(), anyString())).thenReturn(
                new VideoGateway.Task("upstream-1", "COMPLETED", 100, "https://example.com/video.mp4", null, Map.of()));
        when(billing.settleVideo(anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn("usage-1");

        VideoGenerationJob job = new VideoGenerationJob();
        job.setId("job-1"); job.setModelConfigId(7L); job.setUpstreamTaskId("upstream-1");
        job.setDuration(8); job.setBillingStatus("RESERVED"); job.setBillingAmount(new BigDecimal("2"));
        job.setBillingApiKeyId("1"); job.setBillingUserId("2"); job.setBillingAccountId("3");
        job.setCreatedAt(LocalDateTime.now().minusSeconds(10));

        poller.process(job);

        verify(videos).insert(any());
        verify(billing).settleVideo("job-1", "1", "2", "3", new BigDecimal("2"),
                "seedance-2.0", "seedance-2.0", "/v1/videos", 8);
        verify(jobs).updateById(argThat(value -> "SUCCEEDED".equals(value.getStatus())
                && "CHARGED".equals(value.getBillingStatus())));
    }

    @Test
    void completedTaskWithoutResultUrlFailsAndReleasesReservation() {
        VideoGenerationJobMapper jobs = mock(VideoGenerationJobMapper.class);
        GeneratedVideoMapper videos = mock(GeneratedVideoMapper.class);
        VideoGateway gateway = mock(VideoGateway.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        ImageModelConfigService configs = mock(ImageModelConfigService.class);
        VideoTaskPoller poller = new VideoTaskPoller(jobs, videos, gateway, billing, configs);
        AiModel model = new AiModel(); model.setGenerationPath("/v1/video");
        ModelProvider provider = new ModelProvider(); provider.setBaseUrl("https://api.example.com"); provider.setVideoApiKey("secret");
        when(configs.requireVideo(7L)).thenReturn(new ImageModelConfigService.RuntimeModel(model, provider));
        when(gateway.query(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new VideoGateway.Task("task", "COMPLETED", 100, null, null, Map.of()));
        VideoGenerationJob job = new VideoGenerationJob();
        job.setId("job-2"); job.setModelConfigId(7L); job.setUpstreamTaskId("task"); job.setDuration(6);
        job.setBillingStatus("RESERVED"); job.setBillingAmount(BigDecimal.ONE); job.setBillingApiKeyId("1");
        job.setBillingUserId("2"); job.setBillingAccountId("3"); job.setCreatedAt(LocalDateTime.now().minusSeconds(5));

        poller.process(job);

        verify(billing).releaseVideo("job-2", "1", "2", BigDecimal.ONE);
        verify(jobs).updateById(argThat(value -> "FAILED".equals(value.getStatus())
                && "RELEASED".equals(value.getBillingStatus())));
    }
}
