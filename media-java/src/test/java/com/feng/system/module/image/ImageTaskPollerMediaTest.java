package com.feng.system.module.image;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.image.entity.AiModel;
import com.feng.system.module.image.entity.ModelProvider;
import com.feng.system.module.image.service.*;
import com.feng.system.module.media.entity.MediaBillingRecord;
import com.feng.system.module.media.entity.MediaTask;
import com.feng.system.module.media.mapper.MediaTaskMapper;
import com.feng.system.module.media.service.MediaBillingRecordService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImageTaskPollerMediaTest {

    @Test
    void pendingTaskKeepsFirstUpstreamResponseOnly() {
        MediaTaskMapper tasks = mock(MediaTaskMapper.class);
        ImageGateway gateway = mock(ImageGateway.class);
        when(gateway.task(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ImageGateway.Task("upstream-1", "queued", 10, List.of(), null, Map.of()));
        ImageTaskPoller poller = new ImageTaskPoller(tasks, mock(Sub2apiBillingService.class),
                mock(MediaBillingRecordService.class), configured(), gateway,
                mock(ImageStorageService.class), new ObjectMapper());

        poller.process(task("task-pending"));

        verify(tasks).updateById(argThat(value -> "PENDING".equals(value.getStatus())
                && "10".equals(String.valueOf(value.getProgress()))
                && value.getUpstreamResponse().equals(firstUpstreamResponse())));
    }

    @Test
    void completedTaskStoresResultAndChargesReservedBillingRecord() {
        MediaTaskMapper tasks = mock(MediaTaskMapper.class);
        ImageGateway gateway = mock(ImageGateway.class);
        ImageStorageService storage = mock(ImageStorageService.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        MediaBillingRecordService mediaBilling = mock(MediaBillingRecordService.class);
        ImageModelConfigService configs = configured();
        ImageTaskPoller poller = new ImageTaskPoller(tasks, billing, mediaBilling, configs, gateway, storage, new ObjectMapper());
        when(gateway.task(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ImageGateway.Task("upstream-1", "completed", 100,
                        List.of("https://cdn.example.com/image.png"), null, Map.of("status", "completed")));
        when(mediaBilling.find("task-1")).thenReturn(reservation(new BigDecimal("0.5")));
        when(billing.settle(anyString(), anyString(), anyString(), anyString(), any(), anyInt(), anyString(), anyInt()))
                .thenReturn("usage-1");
        MediaTask task = task("task-1");

        poller.process(task);

        verify(storage).saveUrl("task-1", 0, "https://cdn.example.com/image.png");
        verify(mediaBilling).charged("task-1", "usage-1");
        verify(tasks).updateById(argThat(value -> "SUCCEEDED".equals(value.getStatus())
                && "CHARGED".equals(value.getBillingStatus())
                && value.getUpstreamResponse().equals(firstUpstreamResponse())));
    }

    @Test
    void failedTaskReleasesReservedBillingRecord() {
        MediaTaskMapper tasks = mock(MediaTaskMapper.class);
        ImageGateway gateway = mock(ImageGateway.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        MediaBillingRecordService mediaBilling = mock(MediaBillingRecordService.class);
        ImageTaskPoller poller = new ImageTaskPoller(tasks, billing, mediaBilling, configured(), gateway,
                mock(ImageStorageService.class), new ObjectMapper());
        when(gateway.task(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ImageGateway.Task("upstream-1", "failed", 20, List.of(), "rejected", Map.of()));
        when(mediaBilling.find("task-2")).thenReturn(reservation(BigDecimal.ONE));
        when(billing.release("task-2", "1", "2", BigDecimal.ONE)).thenReturn("released");
        MediaTask task = task("task-2");

        poller.process(task);

        verify(mediaBilling).released("task-2");
        verify(tasks).updateById(argThat(value -> "FAILED".equals(value.getStatus())
                && "RELEASED".equals(value.getBillingStatus()) && "rejected".equals(value.getErrorMessage())));
    }

    private ImageModelConfigService configured() {
        ImageModelConfigService configs = mock(ImageModelConfigService.class);
        AiModel model = new AiModel();
        model.setId(7L); model.setModelKey("gpt-image-2"); model.setGenerationPath("/v1/images/generations");
        ModelProvider provider = new ModelProvider();
        provider.setBaseUrl("https://api.example.com"); provider.setImageApiKey("secret");
        when(configs.requireImage(7L)).thenReturn(new ImageModelConfigService.RuntimeModel(model, provider));
        return configs;
    }

    private MediaTask task(String id) {
        MediaTask task = new MediaTask();
        task.setId(id); task.setTaskType("IMAGE"); task.setModelConfigId(7L); task.setUpstreamTaskId("upstream-1");
        task.setUpstreamOperation("generations"); task.setTaskData("{\"count\":1,\"size\":\"1024x1024\"}");
        task.setUpstreamResponse(firstUpstreamResponse()); task.setStatus("PENDING"); task.setCreatedAt(LocalDateTime.now().minusSeconds(5));
        return task;
    }

    private String firstUpstreamResponse() {
        return "{\"id\":\"upstream-1\",\"status\":\"queued\"}";
    }

    private MediaBillingRecord reservation(BigDecimal fee) {
        MediaBillingRecord record = new MediaBillingRecord();
        record.setDeductionStatus("RESERVED"); record.setTaskFee(fee);
        record.setApiKeyId("1"); record.setUserId("2"); record.setAccountId("3");
        return record;
    }
}
