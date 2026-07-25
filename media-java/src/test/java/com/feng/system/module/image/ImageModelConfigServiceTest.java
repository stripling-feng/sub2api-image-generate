package com.feng.system.module.image;

import com.feng.system.module.image.service.ImageModelConfigService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.image.entity.AiModel;
import com.feng.system.module.image.entity.ModelProvider;
import com.feng.system.module.image.mapper.AiModelMapper;
import com.feng.system.module.image.mapper.GenerationJobMapper;
import com.feng.system.module.image.mapper.ModelProviderMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

class ImageModelConfigServiceTest {

    @Test
    void publicModelsExposeConfiguredUnitPriceBeforeProviderKeyIsConfigured() {
        ModelProviderMapper providers = mock(ModelProviderMapper.class);
        AiModelMapper models = mock(AiModelMapper.class);
        GenerationJobMapper jobs = mock(GenerationJobMapper.class);
        ImageModelConfigService service = new ImageModelConfigService(providers, models, jobs, new ObjectMapper());

        ModelProvider provider = new ModelProvider();
        provider.setId(1L);
        provider.setName("provider");
        provider.setEnabled(1);
        provider.setImageApiKey("");

        AiModel model = new AiModel();
        model.setId(2L);
        model.setProviderId(1L);
        model.setModelKey("gpt-image-2");
        model.setDisplayName("GPT Image 2");
        model.setUnitPriceUsd(new BigDecimal("0.75"));
        model.setMaxCount(10);
        model.setMaxReferenceImages(9);
        model.setSupportsMask(0);
        model.setParameterSchema("[]");
        model.setDefaultParams("{}");

        when(models.selectList(any())).thenReturn(List.of(model));
        when(providers.selectBatchIds(any())).thenReturn(List.of(provider));

        List<Map<String, Object>> result = service.publicImages();

        assertEquals(new BigDecimal("0.75"), result.get(0).get("unitPriceUsd"));
    }

    @Test
    void publicVideosExposeBillingModeAndDocumentCapabilities() {
        ModelProviderMapper providers = mock(ModelProviderMapper.class);
        AiModelMapper models = mock(AiModelMapper.class);
        GenerationJobMapper jobs = mock(GenerationJobMapper.class);
        ImageModelConfigService service = new ImageModelConfigService(providers, models, jobs, new ObjectMapper());

        ModelProvider provider = new ModelProvider();
        provider.setId(1L);
        provider.setName("provider");
        provider.setEnabled(1);

        AiModel model = new AiModel();
        model.setId(2L);
        model.setProviderId(1L);
        model.setModelKey("seedance-2.0");
        model.setDisplayName("Seedance 2.0");
        model.setBillingMode("PER_SECOND");
        model.setUnitPriceUsd(new BigDecimal("0.25"));
        model.setMaxCount(4);
        model.setMaxReferenceImages(4);
        model.setSupportsMask(0);
        model.setParameterSchema("[{\"key\":\"duration\",\"type\":\"select\"}]");
        model.setDefaultParams("{\"protocol\":\"seedance\"}");

        when(models.selectList(any())).thenReturn(List.of(model));
        when(providers.selectBatchIds(any())).thenReturn(List.of(provider));

        Map<String, Object> result = service.publicVideos().get(0);

        assertEquals("PER_SECOND", result.get("billingMode"));
        assertEquals(new BigDecimal("0.25"), result.get("unitPriceUsd"));
        assertEquals(4, result.get("maxCount"));
        assertEquals("duration", ((List<?>) result.get("parameters")).stream()
                .map(Map.class::cast).findFirst().orElseThrow().get("key"));
    }

    @Test
    void newGrokVideoUsesUnifiedVideosEndpoint() {
        ModelProviderMapper providers = mock(ModelProviderMapper.class);
        AiModelMapper models = mock(AiModelMapper.class);
        ImageModelConfigService service = new ImageModelConfigService(providers, models,
                mock(GenerationJobMapper.class), new ObjectMapper());
        ModelProvider provider = new ModelProvider(); provider.setId(1L);
        when(providers.selectById(1L)).thenReturn(provider);
        AiModel model = new AiModel(); model.setProviderId(1L); model.setModelKey("grok-video");
        model.setDisplayName("Grok Video"); model.setUpstreamModel("cy-gv1-grok-video");
        model.setUnitPriceUsd(BigDecimal.ONE); model.setEnabled(1);

        service.saveVideo(model);

        ArgumentCaptor<AiModel> saved = ArgumentCaptor.forClass(AiModel.class);
        verify(models).insert(saved.capture());
        assertEquals("/v1/videos", saved.getValue().getGenerationPath());
    }

    @Test
    void omniTemplatesExposeFixedCapabilities() throws Exception {
        ModelProviderMapper providers = mock(ModelProviderMapper.class);
        AiModelMapper models = mock(AiModelMapper.class);
        ImageModelConfigService service = new ImageModelConfigService(providers, models,
                mock(GenerationJobMapper.class), new ObjectMapper());
        ModelProvider provider = new ModelProvider(); provider.setId(1L);
        when(providers.selectById(1L)).thenReturn(provider);

        for (String key : List.of("omni-fast", "omni-fast-no-water", "omni-v2v", "omni-v2v-no-water")) {
            AiModel model = new AiModel(); model.setProviderId(1L); model.setModelKey(key);
            model.setDisplayName(key); model.setUpstreamModel(key); model.setUnitPriceUsd(BigDecimal.ZERO);
            service.saveVideo(model);
        }

        ArgumentCaptor<AiModel> saved = ArgumentCaptor.forClass(AiModel.class);
        verify(models, times(4)).insert(saved.capture());
        AiModel fast = saved.getAllValues().stream().filter(value -> value.getModelKey().equals("omni-fast")).findFirst().orElseThrow();
        AiModel v2v = saved.getAllValues().stream().filter(value -> value.getModelKey().equals("omni-v2v")).findFirst().orElseThrow();
        assertEquals("/v1/videos", fast.getGenerationPath());
        assertEquals(5, fast.getMaxReferenceImages());
        assertEquals(true, new ObjectMapper().readValue(fast.getDefaultParams(), Map.class).containsKey("maxImageBytes"));
        assertEquals(false, new ObjectMapper().readValue(v2v.getDefaultParams(), Map.class).containsKey("requiresVideo"));
    }
}
