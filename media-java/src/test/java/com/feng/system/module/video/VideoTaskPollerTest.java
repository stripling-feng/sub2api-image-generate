package com.feng.system.module.video;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.image.entity.AiModel;
import com.feng.system.module.image.entity.ModelProvider;
import com.feng.system.module.image.service.ImageModelConfigService;
import com.feng.system.module.image.service.Sub2apiBillingService;
import com.feng.system.module.media.entity.MediaBillingRecord;
import com.feng.system.module.media.entity.MediaTask;
import com.feng.system.module.media.mapper.MediaTaskMapper;
import com.feng.system.module.media.service.MediaBillingRecordService;
import com.feng.system.module.media.service.MediaTaskResultService;
import com.feng.system.module.video.service.VideoGateway;
import com.feng.system.module.video.service.VideoTaskPoller;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VideoTaskPollerTest {
    @Test
    void pendingTaskKeepsFirstUpstreamResponseOnly() {
        MediaTaskMapper tasks = mock(MediaTaskMapper.class);
        VideoGateway gateway = mock(VideoGateway.class);
        when(gateway.query(anyString(), anyString(), anyString(), anyString())).thenReturn(
                new VideoGateway.Task("upstream-1", "PENDING", 10, null, null, Map.of()));
        VideoTaskPoller poller = poller(tasks, gateway, mock(Sub2apiBillingService.class),
                mock(MediaBillingRecordService.class), configured("seedance-2.0"),
                mock(MediaTaskResultService.class));

        poller.process(task("job-pending", 8));

        verify(tasks).updateById(argThat(value -> "PENDING".equals(value.getStatus())
                && "10".equals(String.valueOf(value.getProgress()))
                && value.getUpstreamResponse().equals(firstUpstreamResponse())));
    }

    @Test
    void pollingErrorKeepsFirstUpstreamResponseAndStoresLastErrorInTaskData() {
        MediaTaskMapper tasks = mock(MediaTaskMapper.class);
        VideoGateway gateway = mock(VideoGateway.class);
        when(gateway.query(anyString(), anyString(), anyString(), anyString())).thenThrow(
                new com.feng.system.module.image.exception.ImageApiException(
                        502, "failed", null, Map.of("error", "poll rejected")));
        ImageModelConfigService configs = configured("seedance-2.0");
        VideoTaskPoller poller = poller(tasks, gateway, mock(Sub2apiBillingService.class),
                mock(MediaBillingRecordService.class), configs, mock(MediaTaskResultService.class));
        MediaTask task = task("job-error", 10);

        poller.process(task);

        verify(tasks).updateById(argThat(value -> value.getUpstreamResponse().equals(firstUpstreamResponse())
                && value.getTaskData().contains("poll rejected")));
    }

    @Test
    void omniCompletionStoresUpstreamUrlWithoutDownloadingOrUploading() {
        MediaTaskMapper tasks = mock(MediaTaskMapper.class);
        VideoGateway gateway = mock(VideoGateway.class);
        String upstreamUrl = "https://tmp.cangyuansuanli.cn/gen-videos/122/upstream-1.mp4";
        when(gateway.query(anyString(), anyString(), anyString(), anyString())).thenReturn(
                new VideoGateway.Task("upstream-1", "COMPLETED", 100, upstreamUrl, null, Map.of()));
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        MediaBillingRecordService mediaBilling = mock(MediaBillingRecordService.class);
        when(mediaBilling.find("job-1")).thenReturn(reservation("job-1", BigDecimal.ZERO));
        ImageModelConfigService configs = configured("omni-fast");
        MediaTaskResultService results = mock(MediaTaskResultService.class);
        VideoTaskPoller poller = poller(tasks, gateway, billing, mediaBilling, configs, results);
        when(billing.settleVideo(anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn("usage-1");
        MediaTask task = task("job-1", 10);
        task.setTaskData("{\"duration\":10,\"model\":\"omni-fast\"}");

        poller.process(task);

        verify(results).saveIfAbsent(eq("job-1"), eq(0), eq(upstreamUrl), anyMap());
        verify(gateway, never()).download(anyString(), anyString(), anyString(), anyString());
        verify(billing).settleVideo(anyString(), anyString(), anyString(), anyString(), any(),
                eq("omni-fast"), eq("omni-fast"), eq("/v1/videos"), eq(10));
        verify(mediaBilling).charged("job-1", "usage-1");
    }

    @Test
    void completedTaskStoresUrlAndSettlesRequestedDuration() {
        MediaTaskMapper tasks = mock(MediaTaskMapper.class);
        VideoGateway gateway = mock(VideoGateway.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        MediaBillingRecordService mediaBilling = mock(MediaBillingRecordService.class);
        MediaTaskResultService results = mock(MediaTaskResultService.class);
        ImageModelConfigService configs = configured("seedance-2.0");
        VideoTaskPoller poller = poller(tasks, gateway, billing, mediaBilling, configs,
                results);
        when(gateway.query(anyString(), anyString(), anyString(), anyString())).thenReturn(
                new VideoGateway.Task("upstream-1", "COMPLETED", 100,
                        "https://example.com/video.mp4", null, Map.of("status", "completed")));
        when(mediaBilling.find("job-1")).thenReturn(reservation("job-1", new BigDecimal("2")));
        when(billing.settleVideo(anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn("usage-1");
        MediaTask task = task("job-1", 8);

        poller.process(task);

        verify(results).saveIfAbsent(eq("job-1"), eq(0), eq("https://example.com/video.mp4"), anyMap());
        verify(billing).settleVideo("job-1", "1", "2", "3", new BigDecimal("2"),
                "seedance-2.0", "seedance-2.0", "/v1/videos", 8);
        verify(tasks).updateById(argThat(value -> "SUCCEEDED".equals(value.getStatus())
                && "CHARGED".equals(value.getBillingStatus())
                && value.getUpstreamResponse().equals(firstUpstreamResponse())));
    }

    @Test
    void completedTaskWithoutResultUrlFailsAndReleasesReservation() {
        MediaTaskMapper tasks = mock(MediaTaskMapper.class);
        VideoGateway gateway = mock(VideoGateway.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        MediaBillingRecordService mediaBilling = mock(MediaBillingRecordService.class);
        ImageModelConfigService configs = configured("seedance-2.0");
        VideoTaskPoller poller = poller(tasks, gateway, billing, mediaBilling, configs,
                mock(MediaTaskResultService.class));
        when(gateway.query(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new VideoGateway.Task("task", "COMPLETED", 100, null, null, Map.of()));
        when(mediaBilling.find("job-2")).thenReturn(reservation("job-2", BigDecimal.ONE));
        MediaTask task = task("job-2", 6);

        poller.process(task);

        verify(billing).releaseVideo("job-2", "1", "2", BigDecimal.ONE);
        verify(mediaBilling).released("job-2");
        verify(tasks).updateById(argThat(value -> "FAILED".equals(value.getStatus())
                && "RELEASED".equals(value.getBillingStatus())));
    }

    private VideoTaskPoller poller(MediaTaskMapper tasks, VideoGateway gateway, Sub2apiBillingService billing,
                                   MediaBillingRecordService mediaBilling, ImageModelConfigService configs,
                                   MediaTaskResultService results) {
        return new VideoTaskPoller(tasks, gateway, billing, mediaBilling, configs, results, new ObjectMapper());
    }

    private ImageModelConfigService configured(String modelKey) {
        ImageModelConfigService configs = mock(ImageModelConfigService.class);
        AiModel model = new AiModel();
        model.setId(7L); model.setModelKey(modelKey); model.setUpstreamModel(modelKey); model.setGenerationPath("/v1/videos");
        ModelProvider provider = new ModelProvider();
        provider.setBaseUrl("https://api.example.com"); provider.setVideoApiKey("secret");
        when(configs.requireVideo(7L)).thenReturn(new ImageModelConfigService.RuntimeModel(model, provider));
        return configs;
    }

    private MediaTask task(String id, int duration) {
        MediaTask task = new MediaTask();
        task.setId(id); task.setTaskType("VIDEO"); task.setModelConfigId(7L); task.setUpstreamTaskId("upstream-1");
        task.setTaskData("{\"duration\":" + duration + ",\"model\":\"seedance-2.0\"}");
        task.setUpstreamResponse(firstUpstreamResponse()); task.setStatus("PENDING");
        task.setCreatedAt(LocalDateTime.now().minusSeconds(10));
        return task;
    }

    private String firstUpstreamResponse() {
        return "{\"id\":\"upstream-1\",\"status\":\"queued\"}";
    }

    private MediaBillingRecord reservation(String taskId, BigDecimal fee) {
        MediaBillingRecord record = new MediaBillingRecord();
        record.setTaskId(taskId); record.setTaskFee(fee); record.setDeductionStatus("RESERVED");
        record.setApiKeyId("1"); record.setUserId("2"); record.setAccountId("3");
        return record;
    }
}
