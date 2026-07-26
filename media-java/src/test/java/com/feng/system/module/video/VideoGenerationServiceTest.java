package com.feng.system.module.video;

import com.feng.system.module.image.exception.ImageApiException;
import com.feng.system.module.image.service.ImageGateway;
import com.feng.system.module.image.service.ImageModelConfigService;
import com.feng.system.module.image.service.Sub2apiBillingService;
import com.feng.system.module.video.service.VideoGateway;
import com.feng.system.module.video.service.VideoGenerationService;
import com.feng.system.module.video.service.VideoMaterialUploadService;
import com.feng.system.module.video.dto.VideoGenerationRequest;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VideoGenerationServiceTest {

    @Test
    void rejectsBase64VideoRequestValues() {
        VideoGenerationService service = new VideoGenerationService(mock(MediaTaskMapper.class), mock(VideoGateway.class),
                mock(Sub2apiBillingService.class), mock(MediaBillingRecordService.class), mock(ImageModelConfigService.class),
                mock(VideoMaterialUploadService.class), new ObjectMapper());
        ApiProfile profile = new ApiProfile(); profile.setId("profile-1"); profile.setEncryptedKey("user-key");

        org.junit.jupiter.api.Assertions.assertThrows(com.feng.system.module.image.exception.ImageApiException.class,
                () -> service.generate(profile, request(Map.of("model", "omni-fast", "prompt", "city",
                        "image_url", "data:image/png;base64,aGVsbG8=")), List.of(), null, null));
    }

    @Test
    void omniFastUsesJsonForSingleImageMultipartForMultipleAndSupportsIndependentFrames() throws Exception {
        MediaTaskMapper jobs = mock(MediaTaskMapper.class);
        VideoGateway gateway = mock(VideoGateway.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        MediaBillingRecordService mediaBilling = mock(MediaBillingRecordService.class);
        ImageModelConfigService configs = mock(ImageModelConfigService.class);
        VideoMaterialUploadService uploads = mock(VideoMaterialUploadService.class);
        VideoGenerationService service = new VideoGenerationService(jobs, gateway, billing, mediaBilling, configs, uploads, new ObjectMapper());
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
        VideoGenerationRequest raw = request(Map.of("model", "omni-fast", "prompt", "city"));

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
        ArgumentCaptor<MediaTask> jobsCreated = ArgumentCaptor.forClass(MediaTask.class);
        verify(mediaBilling, times(4)).begin(jobsCreated.capture(),
                argThat(amount -> amount.compareTo(BigDecimal.ZERO) == 0), eq(true));
        Map<?, ?> firstClient = new ObjectMapper().readValue(jobsCreated.getAllValues().get(0).getUserRequest(), Map.class);
        Map<?, ?> firstUpstream = new ObjectMapper().readValue(jobsCreated.getAllValues().get(0).getUpstreamRequest(), Map.class);
        assertEquals(Map.of("name", "a.png", "mimeType", "image/png", "sizeBytes", 8),
                ((List<?>) firstClient.get("uploadedImages")).get(0));
        assertEquals("https://image.tcboys.de/a.png", firstUpstream.get("image_url"));
        Map<?, ?> multiAudit = new ObjectMapper().readValue(jobsCreated.getAllValues().get(1).getUpstreamRequest(), Map.class);
        assertEquals(List.of("https://image.tcboys.de/a.png", "https://image.tcboys.de/b.png"),
                multiAudit.get("input_reference"));
        assertTrue(jobsCreated.getAllValues().stream().noneMatch(job ->
                job.getUserRequest().contains("base64") || job.getUpstreamRequest().contains("base64")));
        assertTrue(jobsCreated.getAllValues().stream().allMatch(job -> job.getUpstreamResponse().contains("task-")));
    }

    @Test
    void omniV2vDefaultsToTenSecondsAndOmitsFixedAndAudioFieldsUpstream() throws Exception {
        MediaTaskMapper jobs = mock(MediaTaskMapper.class);
        VideoGateway gateway = mock(VideoGateway.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        MediaBillingRecordService mediaBilling = mock(MediaBillingRecordService.class);
        ImageModelConfigService configs = mock(ImageModelConfigService.class);
        VideoMaterialUploadService uploads = mock(VideoMaterialUploadService.class);
        VideoGenerationService service = new VideoGenerationService(jobs, gateway, billing, mediaBilling, configs, uploads, new ObjectMapper());
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

        service.generate(profile, request(Map.of("model", "omni-v2v", "prompt", "restyle",
                "referenceVideoUrls", List.of("https://image.tcboys.de/a.mp4", "https://image.tcboys.de/b.mp4"))),
                List.of(new ImageGateway.Upload("ref.png", "image/png", png)), null, null);
        service.generate(profile, request(Map.of("model", "omni-v2v", "prompt", "text only")),
                List.of(), null, null);

        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(gateway, times(2)).create(eq("https://api.example.com"), eq("secret-key"), eq("/v1/videos"), body.capture());
        assertEquals(List.of("https://image.tcboys.de/a.mp4", "https://image.tcboys.de/b.mp4"), body.getAllValues().get(0).get("reference_videos"));
        assertEquals(List.of("https://image.tcboys.de/ref.png"), body.getAllValues().get(0).get("reference_image_urls"));
        assertEquals(false, body.getAllValues().get(1).containsKey("reference_videos"));
        assertEquals(false, body.getAllValues().get(1).containsKey("reference_image_urls"));
        body.getAllValues().forEach(value -> assertEquals(false,
                value.containsKey("duration") || value.containsKey("resolution") || value.containsKey("audio")));
        verify(mediaBilling, atLeastOnce()).begin(argThat(job -> job.getTaskData().contains("\"duration\":10")), any(), eq(true));
    }

    @Test
    void fansOutBatchAndUsesConfiguredUpstreamModelAndPerSecondPrice() throws Exception {
        MediaTaskMapper jobs = mock(MediaTaskMapper.class);
        VideoGateway gateway = mock(VideoGateway.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        ImageModelConfigService configs = mock(ImageModelConfigService.class);
        VideoMaterialUploadService uploads = mock(VideoMaterialUploadService.class);
        VideoGenerationService service = new VideoGenerationService(jobs, gateway, billing, mock(MediaBillingRecordService.class), configs, uploads, new ObjectMapper());

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
        VideoGenerationService.Accepted accepted = service.generate(profile, request(Map.of(
                "model", "seedance-2.0", "prompt", "rainy city", "count", 2,
                "duration", 8, "aspectRatio", "16:9", "resolution", "720p", "generateAudio", true)),
                List.of(), null, null);

        assertEquals(2, accepted.count());
        verify(billing, times(2)).reserveAmount("user-key", new BigDecimal("2.0000000000"));
        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(gateway, times(2)).create(eq("https://api.example.com"), eq("secret-key"), eq("/v1/videos"), body.capture());
        assertEquals("configured-seedance", body.getAllValues().get(0).get("model"));
    }

    @Test
    void seedanceSendsMultimodalHttpsUrls() throws Exception {
        MediaTaskMapper jobs = mock(MediaTaskMapper.class);
        VideoGateway gateway = mock(VideoGateway.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        ImageModelConfigService configs = mock(ImageModelConfigService.class);
        VideoMaterialUploadService uploads = mock(VideoMaterialUploadService.class);
        VideoGenerationService service = new VideoGenerationService(jobs, gateway, billing, mock(MediaBillingRecordService.class), configs, uploads, new ObjectMapper());

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
                .thenReturn(new VideoMaterialUploadService.Uploaded("https://media.example.com/material/20260726/img1.png", "img1.png", "image/png", 8))
                .thenReturn(new VideoMaterialUploadService.Uploaded("https://media.example.com/material/20260726/img2.png", "img2.png", "image/png", 8));

        ApiProfile profile = new ApiProfile();
        profile.setId("profile-1"); profile.setEncryptedKey("user-key");
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        service.generate(profile, request(Map.of(
                "model", "seedance-2.0-mini", "prompt", "鍙傝€傽image1涓嶡image2锛屽姩浣淍video1锛岄厤涔怈audio1",
                "duration", 4, "aspectRatio", "1:1", "resolution", "480p", "generateAudio", true,
                "referenceVideoUrls", List.of("https://image.tcboys.de/ref.mp4"),
                "referenceAudioUrls", List.of("https://image.tcboys.de/ref.mp3"))),
                List.of(new ImageGateway.Upload("a.png", "image/png", png), new ImageGateway.Upload("b.png", "image/png", png)), null, null);

        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(gateway).create(eq("https://api.example.com"), eq("secret-key"), eq("/v1/videos"), body.capture());
        assertEquals("https://media.example.com/material/20260726/img1.png", body.getValue().get("image_url"));
        assertEquals(List.of("https://media.example.com/material/20260726/img2.png"), body.getValue().get("reference_image_urls"));
        assertEquals(List.of("https://image.tcboys.de/ref.mp4"), body.getValue().get("reference_videos"));
        assertEquals(List.of("https://image.tcboys.de/ref.mp3"), body.getValue().get("reference_audios"));
        assertEquals(false, String.valueOf(body.getValue()).contains("data:image"));
    }

    @Test
    void seedanceUsesPreuploadedImageUrlsFromJsonRequest() throws Exception {
        MediaTaskMapper jobs = mock(MediaTaskMapper.class);
        VideoGateway gateway = mock(VideoGateway.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        ImageModelConfigService configs = mock(ImageModelConfigService.class);
        VideoMaterialUploadService uploads = mock(VideoMaterialUploadService.class);
        VideoGenerationService service = new VideoGenerationService(jobs, gateway, billing, mock(MediaBillingRecordService.class), configs, uploads, new ObjectMapper());

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

        ApiProfile profile = new ApiProfile();
        profile.setId("profile-1"); profile.setEncryptedKey("user-key");
        service.generate(profile, request(Map.of(
                "model", "seedance-2.0-mini", "prompt", "city",
                "duration", 4, "aspectRatio", "1:1", "resolution", "480p", "generateAudio", true,
                "referenceImageUrls", List.of("https://image.tcboys.de/img1.png", "https://image.tcboys.de/img2.png"))),
                List.of(), null, null);

        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(gateway).create(eq("https://api.example.com"), eq("secret-key"), eq("/v1/videos"), body.capture());
        assertEquals("https://image.tcboys.de/img1.png", body.getValue().get("image_url"));
        assertEquals(List.of("https://image.tcboys.de/img2.png"), body.getValue().get("reference_image_urls"));
        verifyNoInteractions(uploads);
    }

    @Test
    void omniFastUsesPreuploadedFrameUrlsFromJsonRequest() throws Exception {
        MediaTaskMapper jobs = mock(MediaTaskMapper.class);
        VideoGateway gateway = mock(VideoGateway.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        ImageModelConfigService configs = mock(ImageModelConfigService.class);
        VideoMaterialUploadService uploads = mock(VideoMaterialUploadService.class);
        VideoGenerationService service = new VideoGenerationService(jobs, gateway, billing, mock(MediaBillingRecordService.class), configs, uploads, new ObjectMapper());

        AiModel model = new AiModel(); model.setId(12L); model.setModelKey("omni-fast"); model.setUpstreamModel("omni-fast");
        model.setGenerationPath("/v1/videos"); model.setBillingMode("PER_REQUEST"); model.setUnitPriceUsd(BigDecimal.ZERO);
        ModelProvider provider = new ModelProvider(); provider.setBaseUrl("https://api.example.com"); provider.setVideoApiKey("secret-key");
        when(configs.requireVideo("omni-fast")).thenReturn(new ImageModelConfigService.RuntimeModel(model, provider));
        when(billing.reserveAmount(anyString(), any())).thenReturn(
                new Sub2apiBillingService.Reservation("1", "2", "3", BigDecimal.ZERO.setScale(10), BigDecimal.TEN));
        when(gateway.create(anyString(), anyString(), anyString(), anyMap())).thenReturn(
                new VideoGateway.Task("task-json", "PENDING", 0, null, null, Map.of()));

        ApiProfile profile = new ApiProfile(); profile.setId("profile-1"); profile.setEncryptedKey("user-key");
        service.generate(profile, request(Map.of("model", "omni-fast", "prompt", "city",
                "firstFrameUrl", "https://image.tcboys.de/first.png",
                "lastFrameUrl", "https://image.tcboys.de/last.png")), List.of(), null, null);

        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(gateway).create(eq("https://api.example.com"), eq("secret-key"), eq("/v1/videos"), body.capture());
        assertEquals("https://image.tcboys.de/first.png", body.getValue().get("first_image_url"));
        assertEquals("https://image.tcboys.de/last.png", body.getValue().get("last_image_url"));
        verifyNoInteractions(uploads);
    }

    @Test
    void grokRequestSendsSecondsAsString() throws Exception {
        MediaTaskMapper jobs = mock(MediaTaskMapper.class);
        VideoGateway gateway = mock(VideoGateway.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        ImageModelConfigService configs = mock(ImageModelConfigService.class);
        VideoMaterialUploadService uploads = mock(VideoMaterialUploadService.class);
        VideoGenerationService service = new VideoGenerationService(jobs, gateway, billing, mock(MediaBillingRecordService.class), configs, uploads, new ObjectMapper());

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
        service.generate(profile, request(Map.of("model", "grok-video", "prompt", "city", "duration", 10,
                "aspectRatio", "16:9", "resolution", "720p")), List.of(), null, null);

        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(gateway).create(eq("https://api.example.com"), eq("secret-key"), eq("/v1/videos"), body.capture());
        assertEquals("10", body.getValue().get("seconds"));
    }

    @Test
    void grokVideoUploadsReferenceImagesAsUrls() throws Exception {
        MediaTaskMapper jobs = mock(MediaTaskMapper.class);
        VideoGateway gateway = mock(VideoGateway.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        ImageModelConfigService configs = mock(ImageModelConfigService.class);
        VideoMaterialUploadService uploads = mock(VideoMaterialUploadService.class);
        VideoGenerationService service = new VideoGenerationService(jobs, gateway, billing, mock(MediaBillingRecordService.class), configs, uploads, new ObjectMapper());

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
                "https://media.example.com/material/20260726/a.png", "a.png", "image/png", 8));

        ApiProfile profile = new ApiProfile();
        profile.setId("profile-1"); profile.setEncryptedKey("user-key");
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        service.generate(profile, request(Map.of("model", "grok-video", "prompt", "city", "duration", 10,
                "aspectRatio", "16:9", "resolution", "720p")), List.of(new ImageGateway.Upload("a.png", "image/png", png)), null, null);

        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(gateway).create(eq("https://api.example.com"), eq("secret-key"), eq("/v1/videos"), body.capture());
        assertEquals(List.of("https://media.example.com/material/20260726/a.png"), body.getValue().get("image_urls"));
        verify(uploads).uploadImage(any());
    }

    @Test
    void grokVideoAcceptsPreuploadedReferenceImageUrls() throws Exception {
        MediaTaskMapper jobs = mock(MediaTaskMapper.class);
        VideoGateway gateway = mock(VideoGateway.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        ImageModelConfigService configs = mock(ImageModelConfigService.class);
        VideoMaterialUploadService uploads = mock(VideoMaterialUploadService.class);
        VideoGenerationService service = new VideoGenerationService(jobs, gateway, billing, mock(MediaBillingRecordService.class), configs, uploads, new ObjectMapper());

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
        service.generate(profile, request(Map.of("model", "grok-video", "prompt", "city", "duration", 10,
                "aspectRatio", "16:9", "resolution", "720p",
                "referenceImageUrls", List.of("https://image.tcboys.de/a.png"))), List.of(), null, null);

        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(gateway).create(eq("https://api.example.com"), eq("secret-key"), eq("/v1/videos"), body.capture());
        assertEquals(List.of("https://image.tcboys.de/a.png"), body.getValue().get("image_urls"));
        verifyNoInteractions(uploads);
    }

    @Test
    void grok15SendsImageObjectAndDuration() throws Exception {
        MediaTaskMapper jobs = mock(MediaTaskMapper.class);
        VideoGateway gateway = mock(VideoGateway.class);
        Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
        ImageModelConfigService configs = mock(ImageModelConfigService.class);
        VideoMaterialUploadService uploads = mock(VideoMaterialUploadService.class);
        VideoGenerationService service = new VideoGenerationService(jobs, gateway, billing, mock(MediaBillingRecordService.class), configs, uploads, new ObjectMapper());

        AiModel model = new AiModel();
        model.setId(9L); model.setModelKey("grok-video-1.5"); model.setUpstreamModel("grok-video-1.5");
        model.setGenerationPath("/v1/videos"); model.setBillingMode("PER_REQUEST");
        model.setUnitPriceUsd(BigDecimal.ONE);
        ModelProvider provider = new ModelProvider();
        provider.setBaseUrl("https://api.example.com"); provider.setVideoApiKey("secret-key");
        when(configs.requireVideo("grok-video-1.5")).thenReturn(new ImageModelConfigService.RuntimeModel(model, provider));
        when(billing.reserveAmount(eq("user-key"), eq(new BigDecimal("1.0000000000"))))
                .thenReturn(new Sub2apiBillingService.Reservation("1", "2", "3", new BigDecimal("1.0000000000"), new BigDecimal("98")));
        when(gateway.create(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(new VideoGateway.Task("task-1", "PENDING", 0, null, null, Map.of()));
        when(uploads.uploadImage(any())).thenReturn(new VideoMaterialUploadService.Uploaded(
                "https://media.example.com/material/20260726/a.png", "a.png", "image/png", 8));

        ApiProfile profile = new ApiProfile();
        profile.setId("profile-1"); profile.setEncryptedKey("user-key");
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        service.generate(profile, request(Map.of("model", "grok-video-1.5", "prompt", "city", "duration", 10,
                "aspectRatio", "16:9", "resolution", "720p")), List.of(new ImageGateway.Upload("a.png", "image/png", png)), null, null);

        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(gateway).create(eq("https://api.example.com"), eq("secret-key"), eq("/v1/videos"), body.capture());
        assertEquals(true, body.getValue().containsKey("image"));
        assertEquals(Map.of("url", "https://media.example.com/material/20260726/a.png"), body.getValue().get("image"));
        assertEquals(10, body.getValue().get("duration"));
        assertEquals(false, body.getValue().containsKey("seconds"));
        assertEquals(false, body.getValue().containsKey("image_url"));
        assertEquals(false, body.getValue().containsKey("image_urls"));
    }

    private static VideoGenerationRequest request(Map<String, Object> value) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(mapper.writeValueAsString(value), VideoGenerationRequest.class);
    }
}
