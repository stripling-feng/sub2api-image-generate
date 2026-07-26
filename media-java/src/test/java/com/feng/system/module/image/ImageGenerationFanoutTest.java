package com.feng.system.module.image;

import com.feng.system.module.image.dto.ImageGenerateRequest;
import com.feng.system.module.image.exception.ImageApiException;
import com.feng.system.module.image.service.ImageGateway;
import com.feng.system.module.image.service.ImageGenerationService;
import com.feng.system.module.image.service.ImageGenerationWorker;
import com.feng.system.module.image.service.ImageModelConfigService;
import com.feng.system.module.image.service.Sub2apiBillingService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.image.entity.AiModel;
import com.feng.system.module.image.entity.ApiProfile;
import com.feng.system.module.image.entity.ModelProvider;
import com.feng.system.module.media.entity.MediaTask;
import com.feng.system.module.media.mapper.MediaTaskMapper;
import com.feng.system.module.media.service.MediaBillingRecordService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImageGenerationFanoutTest {

    @Test
    void rejectsMissingRequiredAsyncOrSizeFields() {
        MediaTaskMapper jobs = mock(MediaTaskMapper.class);
        ImageModelConfigService configs = mock(ImageModelConfigService.class);
        ImageGenerationService service = new ImageGenerationService(jobs, mock(ImageGateway.class),
                mock(ImageGenerationWorker.class), mock(Sub2apiBillingService.class),
                mock(MediaBillingRecordService.class), configs, new ObjectMapper());
        AiModel model = new AiModel(); model.setId(1L); model.setModelKey("gpt-image-2"); model.setAsyncMode(1);
        model.setMaxCount(1); model.setMaxReferenceImages(1); model.setSupportsMask(1);
        model.setParameterSchema("[]"); model.setDefaultParams("{}");
        ModelProvider provider = new ModelProvider(); provider.setBaseUrl("https://example.com"); provider.setImageApiKey("key");
        when(configs.requireImage("gpt-image-2")).thenReturn(new ImageModelConfigService.RuntimeModel(model, provider));
        ApiProfile profile = new ApiProfile(); profile.setId("profile"); profile.setEncryptedKey("plain-key");

        ImageGenerateRequest missingAsync = new ImageGenerateRequest();
        missingAsync.setModel("gpt-image-2");
        missingAsync.setPrompt("city");
        missingAsync.setCount(1);
        missingAsync.setSize("1:1");
        assertThrows(ImageApiException.class, () -> service.generate(profile, missingAsync, List.of(), null));

        ImageGenerateRequest missingSize = request("gpt-image-2", "city", 1);
        missingSize.setSize(null);
        assertThrows(ImageApiException.class, () -> service.generate(profile, missingSize, List.of(), null));
        verifyNoInteractions(jobs);
    }

    @Test
    void splitsFourImagesIntoFourIndependentlyBilledSingleImageRequests() throws Exception {
        MediaTaskMapper jobs = mock(MediaTaskMapper.class);
        ImageGateway gateway = mock(ImageGateway.class);
        ImageGenerationWorker worker = mock(ImageGenerationWorker.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        MediaBillingRecordService mediaBilling = mock(MediaBillingRecordService.class);
        ImageModelConfigService configs = mock(ImageModelConfigService.class);
        ObjectMapper json = new ObjectMapper();
        ImageGenerationService service = new ImageGenerationService(jobs, gateway, worker, billing, mediaBilling, configs, json);

        AiModel model = new AiModel();
        model.setId(1L);
        model.setModelKey("gpt-image-2-4k");
        model.setUpstreamModel("cy-img2-gpt-image-2-4k");
        model.setGenerationPath("/v1/images/generations");
        model.setAsyncMode(1);
        model.setMaxCount(10);
        model.setMaxReferenceImages(9);
        model.setSupportsMask(0);
        model.setParameterSchema("[]");
        model.setDefaultParams("{}");
        model.setUnitPriceUsd(new BigDecimal("0.5"));

        ModelProvider provider = new ModelProvider();
        provider.setBaseUrl("https://example.com");
        provider.setImageApiKey("key");
        when(configs.requireImage("gpt-image-2-4k")).thenReturn(new ImageModelConfigService.RuntimeModel(model, provider));
        when(billing.reserve("plain-key", 1, new BigDecimal("0.5")))
                .thenReturn(new Sub2apiBillingService.Reservation("key-id", "user-id", "account-id",
                        new BigDecimal("0.5000000000"), new BigDecimal("9.5000000000")));

        AtomicInteger taskIds = new AtomicInteger();
        when(gateway.create(anyString(), anyString(), anyString(), anyMap()))
                .thenAnswer(invocation -> new ImageGateway.GatewayResponse(Map.of("id", "task-" + taskIds.incrementAndGet()), 10));
        when(gateway.parseTask(any())).thenAnswer(invocation -> {
            Map<?, ?> payload = invocation.getArgument(0);
            return new ImageGateway.Task(String.valueOf(payload.get("id")), "queued", 0, List.of(), null, payload);
        });

        ApiProfile profile = new ApiProfile();
        profile.setId("profile");
        profile.setEncryptedKey("plain-key");

        ImageGenerationService.Accepted accepted = service.generate(profile,
                request("gpt-image-2-4k", "city", 4),
                List.of(new ImageGateway.Upload("ref.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10})), null);

        ArgumentCaptor<MediaTask> jobCaptor = ArgumentCaptor.forClass(MediaTask.class);
        verify(mediaBilling, times(4)).begin(jobCaptor.capture(), eq(new BigDecimal("0.5")), eq(true));
        verify(billing, times(4)).reserve("plain-key", 1, new BigDecimal("0.5"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(gateway, times(4)).create(eq("https://example.com"), eq("key"), anyString(), bodyCaptor.capture());

        assertEquals(4, accepted.count());
        assertTrue(bodyCaptor.getAllValues().stream().allMatch(body -> Integer.valueOf(1).equals(body.get("n"))));
        assertTrue(bodyCaptor.getAllValues().stream().allMatch(body -> !body.containsKey("count")));
        assertTrue(bodyCaptor.getAllValues().stream().allMatch(body -> "gpt-image-2-4k".equals(body.get("model"))));

        String requestId = null;
        for (int index = 0; index < 4; index++) {
            MediaTask job = jobCaptor.getAllValues().get(index);
            Map<String, Object> taskData = json.readValue(job.getTaskData(), new TypeReference<>() {});
            Map<String, Object> params = (Map<String, Object>) taskData.get("params");
            Map<String, Object> client = json.readValue(job.getUserRequest(), new TypeReference<>() {});
            Map<String, Object> upstream = json.readValue(job.getUpstreamRequest(), new TypeReference<>() {});
            Map<String, Object> rawResponse = json.readValue(job.getUpstreamResponse(), new TypeReference<>() {});
            assertEquals(1, ((Number) taskData.get("count")).intValue());
            assertEquals("plain-key", job.getApiKey());
            assertEquals("IMAGE", job.getTaskType());
            assertEquals("city", client.get("prompt"));
            assertEquals(Map.of("name", "ref.png", "mimeType", "image/png", "sizeBytes", 8),
                    ((List<?>) client.get("uploadedImages")).get(0));
            assertEquals("city", upstream.get("prompt"));
            List<?> upstreamImages = (List<?>) upstream.get("images");
            assertEquals(1, upstreamImages.size());
            assertEquals("ref.png", ((Map<?, ?>) upstreamImages.get(0)).get("name"));
            assertEquals("task-" + (index + 1), rawResponse.get("id"));
            assertEquals(index + 1, ((Number) params.get("request_index")).intValue());
            assertEquals(4, ((Number) params.get("request_total")).intValue());
            if (requestId == null) requestId = String.valueOf(params.get("request_id"));
            else assertEquals(requestId, params.get("request_id"));
        }
    }

    private static ImageGenerateRequest request(String model, String prompt, int count) {
        ImageGenerateRequest request = new ImageGenerateRequest();
        request.setModel(model);
        request.setPrompt(prompt);
        request.setAsync(true);
        request.setCount(count);
        request.setSize("1:1");
        request.setImages(List.of());
        return request;
    }
}
