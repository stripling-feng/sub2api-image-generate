package com.feng.system.module.video;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.image.ImageModelConfigService;
import com.feng.system.module.image.ImageGateway;
import com.feng.system.module.image.Sub2apiBillingService;
import com.feng.system.module.image.entity.AiModel;
import com.feng.system.module.image.entity.ApiProfile;
import com.feng.system.module.image.entity.ModelProvider;
import com.feng.system.module.video.mapper.VideoGenerationJobMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VideoGenerationServiceTest {

    @Test
    void rejectsBase64VideoRequestValues() {
        VideoGenerationService service = new VideoGenerationService(mock(VideoGenerationJobMapper.class), mock(VideoGateway.class),
                mock(Sub2apiBillingService.class), mock(ImageModelConfigService.class),
                mock(VideoMaterialUploadService.class), new ObjectMapper());
        ApiProfile profile = new ApiProfile(); profile.setId("profile-1"); profile.setEncryptedKey("user-key");

        org.junit.jupiter.api.Assertions.assertThrows(com.feng.system.module.image.ImageApiException.class,
                () -> service.generate(profile, Map.of("model", "omni-fast", "prompt", "city",
                        "image_url", "data:image/png;base64,aGVsbG8="), List.of(), null, null));
    }

    @Test
    void omniFastUsesJsonForSingleImageMultipartForMultipleAndSupportsIndependentFrames() throws Exception {
        VideoGenerationJobMapper jobs = mock(VideoGenerationJobMapper.class);
        VideoGateway gateway = mock(VideoGateway.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        ImageModelConfigService configs = mock(ImageModelConfigService.class);
        VideoMaterialUploadService uploads = mock(VideoMaterialUploadService.class);
        VideoGenerationService service = new VideoGenerationService(jobs, gateway, billing, configs, uploads, new ObjectMapper());
        AiModel model = new AiModel(); model.setId(12L); model.setModelKey("omni-fast"); model.setUpstreamModel("omni-fast");
        model.setGenerationPath("/v1/videos"); model.setBillingMode("PER_REQUEST"); model.setUnitPriceUsd(BigDecimal.ZERO);
        ModelProvider provider = new ModelProvider(); provider.setBaseUrl("https://api.example.com"); provider.setVideoApiKey("secret-key");
        when(configs.requireVideo("omni-fast")).thenReturn(new ImageModelConfigService.RuntimeModel(model, provider));
        when(billing.reserveAmount(anyString(), any())).thenReturn(
                new Sub2apiBillingService.Reservation("1", "2", "3", BigDecimal.ZERO.setScale(10), BigDecimal.TEN));
        when(gateway.create(anyString(), anyString(), anyString(), anyMap())).thenReturn(
                new VideoGateway.Task("task-json", "PENDING", 0, null, null, Map.of()));
        when(gateway.create(anyString(), anyString(), anyString(), anyMap(), anyList())).thenReturn(
                new VideoGateway.Task("task-form", "PENDING", 0, null, null, Map.of()));
        when(uploads.uploadImage(any())).thenAnswer(call -> {
            ImageGateway.Upload image = call.getArgument(0);
            return new VideoMaterialUploadService.Uploaded("https://image.tcboys.de/" + image.name(), image.name(), image.mimeType(), image.bytes().length);
        });
        ApiProfile profile = new ApiProfile(); profile.setId("profile-1"); profile.setEncryptedKey("user-key");
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        ImageGateway.Upload a = new ImageGateway.Upload("a.png", "image/png", png);
        ImageGateway.Upload b = new ImageGateway.Upload("b.png", "image/png", png);
        Map<String, Object> raw = Map.of("model", "omni-fast", "prompt", "city");

        service.generate(profile, raw, List.of(a), null, null);
        service.generate(profile, raw, List.of(a, b), null, null);
        service.generate(profile, raw, List.of(), a, null);
        service.generate(profile, raw, List.of(), null, b);

        ArgumentCaptor<Map<String, Object>> jsonBodies = ArgumentCaptor.forClass(Map.class);
        verify(gateway, times(3)).create(anyString(), anyString(), eq("/v1/videos"), jsonBodies.capture());
        assertEquals("https://image.tcboys.de/a.png", jsonBodies.getAllValues().get(0).get("image_url"));
        assertEquals("https://image.tcboys.de/a.png", jsonBodies.getAllValues().get(1).get("first_image_url"));
        assertEquals("https://image.tcboys.de/b.png", jsonBodies.getAllValues().get(2).get("last_image_url"));
        jsonBodies.getAllValues().forEach(body -> assertEquals(false,
                body.containsKey("duration") || body.containsKey("resolution") || body.containsKey("audio")));
        ArgumentCaptor<List<String>> references = ArgumentCaptor.forClass(List.class);
        verify(gateway).create(anyString(), anyString(), eq("/v1/videos"), anyMap(), references.capture());
        assertEquals(List.of("https://image.tcboys.de/a.png", "https://image.tcboys.de/b.png"), references.getValue());
        ArgumentCaptor<com.feng.system.module.video.entity.VideoGenerationJob> jobsCreated = ArgumentCaptor.forClass(
                com.feng.system.module.video.entity.VideoGenerationJob.class);
        verify(jobs, times(4)).insert(jobsCreated.capture());
        Map<?, ?> firstAudit = new ObjectMapper().readValue(jobsCreated.getAllValues().get(0).getRawRequest(), Map.class);
        Map<?, ?> firstClient = (Map<?, ?>) firstAudit.get("client");
        Map<?, ?> firstUpstream = (Map<?, ?>) firstAudit.get("upstream");
        assertEquals(Map.of("name", "a.png", "mimeType", "image/png", "sizeBytes", 8),
                ((List<?>) firstClient.get("uploadedImages")).get(0));
        assertEquals("https://image.tcboys.de/a.png", firstUpstream.get("image_url"));
        Map<?, ?> multiAudit = new ObjectMapper().readValue(jobsCreated.getAllValues().get(1).getRawRequest(), Map.class);
        assertEquals(List.of("https://image.tcboys.de/a.png", "https://image.tcboys.de/b.png"),
                ((Map<?, ?>) multiAudit.get("upstream")).get("input_reference"));
        assertTrue(jobsCreated.getAllValues().stream().noneMatch(job -> job.getRawRequest().contains("base64")));
        assertTrue(jobsCreated.getAllValues().stream().allMatch(job -> job.getRawResponses().contains("create")));
    }

    @Test
    void omniV2vDefaultsToTenSecondsAndOmitsFixedAndAudioFieldsUpstream() {
        VideoGenerationJobMapper jobs = mock(VideoGenerationJobMapper.class);
        VideoGateway gateway = mock(VideoGateway.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        ImageModelConfigService configs = mock(ImageModelConfigService.class);
        VideoMaterialUploadService uploads = mock(VideoMaterialUploadService.class);
        VideoGenerationService service = new VideoGenerationService(jobs, gateway, billing, configs, uploads, new ObjectMapper());
        AiModel model = new AiModel(); model.setId(11L); model.setModelKey("omni-v2v"); model.setUpstreamModel("omni-v2v");
        model.setGenerationPath("/v1/videos"); model.setBillingMode("PER_REQUEST"); model.setUnitPriceUsd(BigDecimal.ZERO);
        ModelProvider provider = new ModelProvider(); provider.setBaseUrl("https://api.example.com"); provider.setVideoApiKey("secret-key");
        when(configs.requireVideo("omni-v2v")).thenReturn(new ImageModelConfigService.RuntimeModel(model, provider));
        when(billing.reserveAmount(eq("user-key"), eq(new BigDecimal("0E-10"))))
                .thenReturn(new Sub2apiBillingService.Reservation("1", "2", "3", new BigDecimal("0E-10"), BigDecimal.TEN));
        when(gateway.create(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(new VideoGateway.Task("task-1", "PENDING", 0, null, null, Map.of()));
        when(uploads.uploadImage(any())).thenReturn(new VideoMaterialUploadService.Uploaded(
                "https://image.tcboys.de/ref.png", "ref.png", "image/png", 8));
        ApiProfile profile = new ApiProfile(); profile.setId("profile-1"); profile.setEncryptedKey("user-key");
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};

        service.generate(profile, Map.of("model", "omni-v2v", "prompt", "restyle",
                "referenceVideoUrls", List.of("https://image.tcboys.de/a.mp4", "https://image.tcboys.de/b.mp4")),
                List.of(new ImageGateway.Upload("ref.png", "image/png", png)), null, null);
        service.generate(profile, Map.of("model", "omni-v2v", "prompt", "text only"),
                List.of(), null, null);

        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(gateway, times(2)).create(eq("https://api.example.com"), eq("secret-key"), eq("/v1/videos"), body.capture());
        assertEquals(List.of("https://image.tcboys.de/a.mp4", "https://image.tcboys.de/b.mp4"), body.getAllValues().get(0).get("reference_videos"));
        assertEquals(List.of("https://image.tcboys.de/ref.png"), body.getAllValues().get(0).get("reference_image_urls"));
        assertEquals(false, body.getAllValues().get(1).containsKey("reference_videos"));
        assertEquals(false, body.getAllValues().get(1).containsKey("reference_image_urls"));
        body.getAllValues().forEach(value -> assertEquals(false,
                value.containsKey("duration") || value.containsKey("resolution") || value.containsKey("audio")));
        verify(jobs, atLeastOnce()).updateById(argThat(job -> Integer.valueOf(10).equals(job.getDuration())));
    }

    @Test
    void fansOutBatchAndUsesConfiguredUpstreamModelAndPerSecondPrice() {
        VideoGenerationJobMapper jobs = mock(VideoGenerationJobMapper.class);
        VideoGateway gateway = mock(VideoGateway.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        ImageModelConfigService configs = mock(ImageModelConfigService.class);
        VideoMaterialUploadService uploads = mock(VideoMaterialUploadService.class);
        VideoGenerationService service = new VideoGenerationService(jobs, gateway, billing, configs, uploads, new ObjectMapper());

        AiModel model = new AiModel();
        model.setId(7L); model.setModelKey("seedance-2.0"); model.setUpstreamModel("configured-seedance");
        model.setGenerationPath("/v1/videos"); model.setBillingMode("PER_SECOND");
        model.setUnitPriceUsd(new BigDecimal("0.25")); model.setDefaultParams("{\"protocol\":\"seedance\"}");
        ModelProvider provider = new ModelProvider();
        provider.setBaseUrl("https://api.example.com"); provider.setVideoApiKey("secret-key");
        when(configs.requireVideo("seedance-2.0")).thenReturn(new ImageModelConfigService.RuntimeModel(model, provider));
        when(billing.reserveAmount(eq("user-key"), eq(new BigDecimal("2.0000000000"))))
                .thenReturn(new Sub2apiBillingService.Reservation("1", "2", "3", new BigDecimal("2.0000000000"), new BigDecimal("98")));
        when(gateway.create(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(new VideoGateway.Task("task-1", "PENDING", 0, null, null, Map.of()));

        ApiProfile profile = new ApiProfile();
        profile.setId("profile-1"); profile.setEncryptedKey("user-key");
        VideoGenerationService.Accepted accepted = service.generate(profile, Map.of(
                "model", "seedance-2.0", "prompt", "rainy city", "count", 2,
                "duration", 8, "aspectRatio", "16:9", "resolution", "720p", "generateAudio", true),
                List.of(), null, null);

        assertEquals(2, accepted.count());
        verify(billing, times(2)).reserveAmount("user-key", new BigDecimal("2.0000000000"));
        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(gateway, times(2)).create(eq("https://api.example.com"), eq("secret-key"), eq("/v1/videos"), body.capture());
        assertEquals("configured-seedance", body.getAllValues().get(0).get("model"));
    }

    @Test
    void seedanceSendsMultimodalHttpsUrls() {
        VideoGenerationJobMapper jobs = mock(VideoGenerationJobMapper.class);
        VideoGateway gateway = mock(VideoGateway.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        ImageModelConfigService configs = mock(ImageModelConfigService.class);
        VideoMaterialUploadService uploads = mock(VideoMaterialUploadService.class);
        VideoGenerationService service = new VideoGenerationService(jobs, gateway, billing, configs, uploads, new ObjectMapper());

        AiModel model = new AiModel();
        model.setId(10L); model.setModelKey("seedance-2.0-mini"); model.setUpstreamModel("seedance-2.0-mini");
        model.setGenerationPath("/v1/videos"); model.setBillingMode("PER_REQUEST"); model.setUnitPriceUsd(BigDecimal.ONE);
        ModelProvider provider = new ModelProvider();
        provider.setBaseUrl("https://api.example.com"); provider.setVideoApiKey("secret-key");
        when(configs.requireVideo("seedance-2.0-mini")).thenReturn(new ImageModelConfigService.RuntimeModel(model, provider));
        when(billing.reserveAmount(eq("user-key"), eq(new BigDecimal("1.0000000000"))))
                .thenReturn(new Sub2apiBillingService.Reservation("1", "2", "3", new BigDecimal("1.0000000000"), new BigDecimal("98")));
        when(gateway.create(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(new VideoGateway.Task("task-1", "PENDING", 0, null, null, Map.of()));
        when(uploads.uploadImage(any()))
                .thenReturn(new VideoMaterialUploadService.Uploaded("https://media.example.com/uploads/video-materials/img1.png", "img1.png", "image/png", 8))
                .thenReturn(new VideoMaterialUploadService.Uploaded("https://media.example.com/uploads/video-materials/img2.png", "img2.png", "image/png", 8));

        ApiProfile profile = new ApiProfile();
        profile.setId("profile-1"); profile.setEncryptedKey("user-key");
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        service.generate(profile, Map.of(
                "model", "seedance-2.0-mini", "prompt", "参考@image1与@image2，动作@video1，配乐@audio1",
                "duration", 4, "aspectRatio", "1:1", "resolution", "480p", "generateAudio", true,
                "referenceVideoUrls", List.of("https://image.tcboys.de/ref.mp4"),
                "referenceAudioUrls", List.of("https://image.tcboys.de/ref.mp3")),
                List.of(new ImageGateway.Upload("a.png", "image/png", png), new ImageGateway.Upload("b.png", "image/png", png)), null, null);

        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(gateway).create(eq("https://api.example.com"), eq("secret-key"), eq("/v1/videos"), body.capture());
        assertEquals("https://media.example.com/uploads/video-materials/img1.png", body.getValue().get("image_url"));
        assertEquals(List.of("https://media.example.com/uploads/video-materials/img2.png"), body.getValue().get("reference_image_urls"));
        assertEquals(List.of("https://image.tcboys.de/ref.mp4"), body.getValue().get("reference_videos"));
        assertEquals(List.of("https://image.tcboys.de/ref.mp3"), body.getValue().get("reference_audios"));
        assertEquals(false, String.valueOf(body.getValue()).contains("data:image"));
    }

    @Test
    void grokRequestSendsSecondsAsString() {
        VideoGenerationJobMapper jobs = mock(VideoGenerationJobMapper.class);
        VideoGateway gateway = mock(VideoGateway.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        ImageModelConfigService configs = mock(ImageModelConfigService.class);
        VideoMaterialUploadService uploads = mock(VideoMaterialUploadService.class);
        VideoGenerationService service = new VideoGenerationService(jobs, gateway, billing, configs, uploads, new ObjectMapper());

        AiModel model = new AiModel();
        model.setId(8L); model.setModelKey("grok-video"); model.setUpstreamModel("grok-video");
        model.setGenerationPath("/v1/videos"); model.setBillingMode("PER_REQUEST");
        model.setUnitPriceUsd(BigDecimal.ONE);
        ModelProvider provider = new ModelProvider();
        provider.setBaseUrl("https://api.example.com"); provider.setVideoApiKey("secret-key");
        when(configs.requireVideo("grok-video")).thenReturn(new ImageModelConfigService.RuntimeModel(model, provider));
        when(billing.reserveAmount(eq("user-key"), eq(new BigDecimal("1.0000000000"))))
                .thenReturn(new Sub2apiBillingService.Reservation("1", "2", "3", new BigDecimal("1.0000000000"), new BigDecimal("98")));
        when(gateway.create(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(new VideoGateway.Task("task-1", "PENDING", 0, null, null, Map.of()));

        ApiProfile profile = new ApiProfile();
        profile.setId("profile-1"); profile.setEncryptedKey("user-key");
        service.generate(profile, Map.of("model", "grok-video", "prompt", "city", "duration", 10,
                "aspectRatio", "16:9", "resolution", "720p"), List.of(), null, null);

        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(gateway).create(eq("https://api.example.com"), eq("secret-key"), eq("/v1/videos"), body.capture());
        assertEquals("10", body.getValue().get("seconds"));
    }

    @Test
    void grokVideoUploadsReferenceImagesAsUrls() {
        VideoGenerationJobMapper jobs = mock(VideoGenerationJobMapper.class);
        VideoGateway gateway = mock(VideoGateway.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        ImageModelConfigService configs = mock(ImageModelConfigService.class);
        VideoMaterialUploadService uploads = mock(VideoMaterialUploadService.class);
        VideoGenerationService service = new VideoGenerationService(jobs, gateway, billing, configs, uploads, new ObjectMapper());

        AiModel model = new AiModel();
        model.setId(8L); model.setModelKey("grok-video"); model.setUpstreamModel("grok-video");
        model.setGenerationPath("/v1/videos"); model.setBillingMode("PER_REQUEST");
        model.setUnitPriceUsd(BigDecimal.ONE);
        ModelProvider provider = new ModelProvider();
        provider.setBaseUrl("https://api.example.com"); provider.setVideoApiKey("secret-key");
        when(configs.requireVideo("grok-video")).thenReturn(new ImageModelConfigService.RuntimeModel(model, provider));
        when(billing.reserveAmount(eq("user-key"), eq(new BigDecimal("1.0000000000"))))
                .thenReturn(new Sub2apiBillingService.Reservation("1", "2", "3", new BigDecimal("1.0000000000"), new BigDecimal("98")));
        when(gateway.create(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(new VideoGateway.Task("task-1", "PENDING", 0, null, null, Map.of()));
        when(uploads.uploadImage(any())).thenReturn(new VideoMaterialUploadService.Uploaded(
                "https://media.example.com/uploads/video-materials/a.png", "a.png", "image/png", 8));

        ApiProfile profile = new ApiProfile();
        profile.setId("profile-1"); profile.setEncryptedKey("user-key");
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        service.generate(profile, Map.of("model", "grok-video", "prompt", "city", "duration", 10,
                "aspectRatio", "16:9", "resolution", "720p"), List.of(new ImageGateway.Upload("a.png", "image/png", png)), null, null);

        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(gateway).create(eq("https://api.example.com"), eq("secret-key"), eq("/v1/videos"), body.capture());
        assertEquals(List.of("https://media.example.com/uploads/video-materials/a.png"), body.getValue().get("image_urls"));
        verify(uploads).uploadImage(any());
    }

    @Test
    void grok15SendsImageObjectAndDuration() {
        VideoGenerationJobMapper jobs = mock(VideoGenerationJobMapper.class);
        VideoGateway gateway = mock(VideoGateway.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        ImageModelConfigService configs = mock(ImageModelConfigService.class);
        VideoMaterialUploadService uploads = mock(VideoMaterialUploadService.class);
        VideoGenerationService service = new VideoGenerationService(jobs, gateway, billing, configs, uploads, new ObjectMapper());

        AiModel model = new AiModel();
        model.setId(9L); model.setModelKey("grok-video-1.5"); model.setUpstreamModel("grok-video-1.5");
        model.setGenerationPath("/v1/video"); model.setBillingMode("PER_REQUEST");
        model.setUnitPriceUsd(BigDecimal.ONE);
        ModelProvider provider = new ModelProvider();
        provider.setBaseUrl("https://api.example.com"); provider.setVideoApiKey("secret-key");
        when(configs.requireVideo("grok-video-1.5")).thenReturn(new ImageModelConfigService.RuntimeModel(model, provider));
        when(billing.reserveAmount(eq("user-key"), eq(new BigDecimal("1.0000000000"))))
                .thenReturn(new Sub2apiBillingService.Reservation("1", "2", "3", new BigDecimal("1.0000000000"), new BigDecimal("98")));
        when(gateway.create(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(new VideoGateway.Task("task-1", "PENDING", 0, null, null, Map.of()));
        when(uploads.uploadImage(any())).thenReturn(new VideoMaterialUploadService.Uploaded(
                "https://media.example.com/uploads/video-materials/a.png", "a.png", "image/png", 8));

        ApiProfile profile = new ApiProfile();
        profile.setId("profile-1"); profile.setEncryptedKey("user-key");
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        service.generate(profile, Map.of("model", "grok-video-1.5", "prompt", "city", "duration", 10,
                "aspectRatio", "16:9", "resolution", "720p"), List.of(new ImageGateway.Upload("a.png", "image/png", png)), null, null);

        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(gateway).create(eq("https://api.example.com"), eq("secret-key"), eq("/v1/video"), body.capture());
        assertEquals(true, body.getValue().containsKey("image"));
        assertEquals(Map.of("url", "https://media.example.com/uploads/video-materials/a.png"), body.getValue().get("image"));
        assertEquals(10, body.getValue().get("duration"));
        assertEquals(false, body.getValue().containsKey("seconds"));
        assertEquals(false, body.getValue().containsKey("image_url"));
        assertEquals(false, body.getValue().containsKey("image_urls"));
    }
}
