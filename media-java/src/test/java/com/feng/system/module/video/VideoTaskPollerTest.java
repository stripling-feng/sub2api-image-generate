package com.feng.system.module.video;

import com.feng.system.module.image.exception.ImageApiException;
import com.feng.system.module.image.service.ImageModelConfigService;
import com.feng.system.module.image.service.Sub2apiBillingService;
import com.feng.system.module.video.service.VideoGateway;
import com.feng.system.module.video.service.VideoMaterialUploadService;
import com.feng.system.module.video.service.VideoTaskPoller;

import com.feng.system.module.image.entity.AiModel;
import com.feng.system.module.image.entity.ModelProvider;
import com.feng.system.module.video.entity.VideoGenerationJob;
import com.feng.system.module.video.mapper.GeneratedVideoMapper;
import com.feng.system.module.video.mapper.VideoGenerationJobMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoTaskPollerTest {
    @TempDir Path uploads;

    @Test
    void storesUpstreamErrorPayloadBeforeRetrying() {
        VideoGenerationJobMapper jobs = mock(VideoGenerationJobMapper.class);
        VideoGateway gateway = mock(VideoGateway.class);
        when(gateway.query(anyString(), anyString(), anyString(), anyString())).thenThrow(
                new com.feng.system.module.image.exception.ImageApiException(502, "failed", null, Map.of("error", "poll rejected")));
        ImageModelConfigService configs = mock(ImageModelConfigService.class);
        AiModel model = new AiModel(); model.setGenerationPath("/v1/videos");
        ModelProvider provider = new ModelProvider(); provider.setBaseUrl("https://api.example.com"); provider.setVideoApiKey("key");
        when(configs.requireVideo(7L)).thenReturn(new ImageModelConfigService.RuntimeModel(model, provider));
        VideoTaskPoller poller = new VideoTaskPoller(jobs, mock(GeneratedVideoMapper.class), gateway,
                mock(Sub2apiBillingService.class), configs, mock(VideoMaterialUploadService.class));
        VideoGenerationJob job = new VideoGenerationJob(); job.setId("job-error"); job.setModelConfigId(7L);
        job.setUpstreamTaskId("task-error"); job.setPollErrorCount(0); job.setRawResponses("[]");

        poller.process(job);

        verify(jobs).updateById(argThat(value -> value.getRawResponses().contains("poll rejected")
                && Integer.valueOf(1).equals(value.getPollErrorCount())));
    }

    @Test
    void omniCompletionDownloadsLocallyBeforeSettling() throws Exception {
        VideoGenerationJobMapper jobs = mock(VideoGenerationJobMapper.class);
        GeneratedVideoMapper videos = mock(GeneratedVideoMapper.class);
        VideoGateway gateway = mock(VideoGateway.class, invocation -> switch (invocation.getMethod().getName()) {
            case "query" -> new VideoGateway.Task("upstream-1", "COMPLETED", 100, null, null, Map.of());
            case "download" -> ResponseEntity.ok().contentType(MediaType.parseMediaType("video/mp4"))
                    .body(new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'});
            default -> RETURNS_DEFAULTS.answer(invocation);
        });
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        ImageModelConfigService configs = mock(ImageModelConfigService.class);
        VideoMaterialUploadService storage = new VideoMaterialUploadService(uploads.toString(), "https://media.example.com");
        VideoTaskPoller poller = VideoTaskPoller.class.getConstructor(VideoGenerationJobMapper.class,
                GeneratedVideoMapper.class, VideoGateway.class, Sub2apiBillingService.class,
                ImageModelConfigService.class, VideoMaterialUploadService.class)
                .newInstance(jobs, videos, gateway, billing, configs, storage);
        AiModel model = new AiModel(); model.setId(7L); model.setModelKey("omni-fast");
        model.setUpstreamModel("omni-fast"); model.setGenerationPath("/v1/videos");
        ModelProvider provider = new ModelProvider(); provider.setBaseUrl("https://api.example.com"); provider.setVideoApiKey("secret");
        when(configs.requireVideo(7L)).thenReturn(new ImageModelConfigService.RuntimeModel(model, provider));
        when(billing.settleVideo(anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn("usage-1");
        VideoGenerationJob job = new VideoGenerationJob(); job.setId("job-1"); job.setModelConfigId(7L);
        job.setUpstreamTaskId("upstream-1"); job.setDuration(10); job.setBillingStatus("RESERVED");
        job.setBillingAmount(BigDecimal.ZERO); job.setBillingApiKeyId("1"); job.setBillingUserId("2");
        job.setBillingAccountId("3"); job.setCreatedAt(LocalDateTime.now().minusSeconds(10));

        poller.process(job);

        verify(videos).insert(argThat(video -> video.getPublicUrl().equals(
                "https://media.example.com/uploads/generated-videos/job-1.mp4")));
        assertTrue(Files.exists(uploads.resolve("generated-videos/job-1.mp4")));
        verify(billing).settleVideo(anyString(), anyString(), anyString(), anyString(), any(),
                eq("omni-fast"), eq("omni-fast"), eq("/v1/videos"), eq(10));
    }

    @Test
    void completedTaskStoresUrlAndSettlesRequestedDuration() {
        VideoGenerationJobMapper jobs = mock(VideoGenerationJobMapper.class);
        GeneratedVideoMapper videos = mock(GeneratedVideoMapper.class);
        VideoGateway gateway = mock(VideoGateway.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        ImageModelConfigService configs = mock(ImageModelConfigService.class);
        VideoTaskPoller poller = new VideoTaskPoller(jobs, videos, gateway, billing, configs,
                mock(VideoMaterialUploadService.class));

        AiModel model = new AiModel();
        model.setId(7L); model.setModelKey("seedance-2.0"); model.setUpstreamModel("seedance-2.0");
        model.setGenerationPath("/v1/videos");
        ModelProvider provider = new ModelProvider();
        provider.setBaseUrl("https://api.example.com"); provider.setVideoApiKey("secret");
        when(configs.requireVideo(7L)).thenReturn(new ImageModelConfigService.RuntimeModel(model, provider));
        when(gateway.query(anyString(), anyString(), anyString(), anyString())).thenReturn(
                new VideoGateway.Task("upstream-1", "COMPLETED", 100, "https://example.com/video.mp4", null, Map.of("status", "completed")));
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
                && "CHARGED".equals(value.getBillingStatus()) && value.getRawResponses().contains("completed")));
    }

    @Test
    void completedTaskWithoutResultUrlFailsAndReleasesReservation() {
        VideoGenerationJobMapper jobs = mock(VideoGenerationJobMapper.class);
        GeneratedVideoMapper videos = mock(GeneratedVideoMapper.class);
        VideoGateway gateway = mock(VideoGateway.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        ImageModelConfigService configs = mock(ImageModelConfigService.class);
        VideoTaskPoller poller = new VideoTaskPoller(jobs, videos, gateway, billing, configs,
                mock(VideoMaterialUploadService.class));
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
