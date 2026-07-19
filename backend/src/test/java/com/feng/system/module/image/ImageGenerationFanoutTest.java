package com.feng.system.module.image;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.image.entity.AiModel;
import com.feng.system.module.image.entity.ApiProfile;
import com.feng.system.module.image.entity.GenerationJob;
import com.feng.system.module.image.entity.ModelProvider;
import com.feng.system.module.image.mapper.GenerationJobMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImageGenerationFanoutTest {

    @Test
    void splitsFourImagesIntoFourIndependentlyBilledSingleImageRequests() throws Exception {
        GenerationJobMapper jobs = mock(GenerationJobMapper.class);
        ImageGateway gateway = mock(ImageGateway.class);
        ImageGenerationWorker worker = mock(ImageGenerationWorker.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        ImageModelConfigService configs = mock(ImageModelConfigService.class);
        ObjectMapper json = new ObjectMapper();
        ImageGenerationService service = new ImageGenerationService(jobs, gateway, worker, billing, configs, json);

        AiModel model = new AiModel();
        model.setId(1L);
        model.setModelKey("gpt-image-2-4k");
        model.setUpstreamModel("cy-img2-gpt-image-2-4k");
        model.setGenerationPath("/v1/images/generations");
        model.setEditPath("/v1/images/edits");
        model.setAsyncMode(1);
        model.setMaxCount(10);
        model.setMaxReferenceImages(9);
        model.setSupportsMask(0);
        model.setParameterSchema("[]");
        model.setDefaultParams("{\"response_format\":\"url\"}");
        model.setUnitPriceUsd(new BigDecimal("0.5"));

        ModelProvider provider = new ModelProvider();
        provider.setBaseUrl("https://example.com");
        provider.setImageApiKey("key");
        when(configs.requireImage("gpt-image-2-4k")).thenReturn(new ImageModelConfigService.RuntimeModel(model, provider));
        when(billing.reserve("plain-key", 1, new BigDecimal("0.5")))
                .thenReturn(new Sub2apiBillingService.Reservation("key-id", "user-id", "account-id",
                        new BigDecimal("0.5000000000"), new BigDecimal("9.5000000000")));

        AtomicInteger taskIds = new AtomicInteger();
        when(gateway.create(anyString(), anyString(), anyString(), anyString(), anyMap(), anyList(), isNull()))
                .thenAnswer(invocation -> new ImageGateway.GatewayResponse(Map.of("id", "task-" + taskIds.incrementAndGet()), 10));
        when(gateway.parseTask(any())).thenAnswer(invocation -> {
            Map<?, ?> payload = invocation.getArgument(0);
            return new ImageGateway.Task(String.valueOf(payload.get("id")), "queued", 0, List.of(), null, payload);
        });

        ApiProfile profile = new ApiProfile();
        profile.setId("profile");
        profile.setEncryptedKey("plain-key");

        ImageGenerationService.Accepted accepted = service.generate(profile,
                Map.of("model", "gpt-image-2-4k", "prompt", "city", "count", 4), List.of(), null);

        ArgumentCaptor<GenerationJob> jobCaptor = ArgumentCaptor.forClass(GenerationJob.class);
        verify(jobs, times(4)).insert(jobCaptor.capture());
        verify(billing, times(4)).reserve("plain-key", 1, new BigDecimal("0.5"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(gateway, times(4)).create(eq("https://example.com"), eq("key"), anyString(), anyString(),
                bodyCaptor.capture(), anyList(), isNull());

        assertEquals(4, accepted.count());
        assertTrue(bodyCaptor.getAllValues().stream().allMatch(body -> Integer.valueOf(1).equals(body.get("n"))));
        assertTrue(bodyCaptor.getAllValues().stream().allMatch(body -> "gpt-image-2-4k".equals(body.get("model"))));

        String requestId = null;
        for (int index = 0; index < 4; index++) {
            GenerationJob job = jobCaptor.getAllValues().get(index);
            Map<String, Object> params = json.readValue(job.getParams(), new TypeReference<>() {});
            assertEquals(1, job.getCount());
            assertEquals(index + 1, ((Number) params.get("request_index")).intValue());
            assertEquals(4, ((Number) params.get("request_total")).intValue());
            if (requestId == null) requestId = String.valueOf(params.get("request_id"));
            else assertEquals(requestId, params.get("request_id"));
        }
    }
}
