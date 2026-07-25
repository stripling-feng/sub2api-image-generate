package com.feng.system.module.video;

import com.feng.system.module.image.exception.ImageApiExceptionHandler;
import com.feng.system.module.image.service.ImageSessionService;
import com.feng.system.module.video.controller.VideoGenerationController;
import com.feng.system.module.video.dto.GrokVideo15Request;
import com.feng.system.module.video.dto.GrokVideoRequest;
import com.feng.system.module.video.dto.OmniFastVideoRequest;
import com.feng.system.module.video.dto.OmniV2vVideoRequest;
import com.feng.system.module.video.dto.SeedanceVideoRequest;
import com.feng.system.module.video.dto.VideoGenerationRequest;
import com.feng.system.module.video.service.VideoGenerationService;
import com.feng.system.module.video.service.VideoMaterialUploadService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.image.entity.ApiProfile;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VideoApiContractTest {

    @Test
    void requestBodyDeserializesToModelSpecificRequestTypes() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        assertRequestType(mapper, "seedance-2.0", SeedanceVideoRequest.class);
        assertRequestType(mapper, "seedance-2.0-fast", SeedanceVideoRequest.class);
        assertRequestType(mapper, "grok-video", GrokVideoRequest.class);
        assertRequestType(mapper, "grok-video-1.5", GrokVideo15Request.class);
        assertRequestType(mapper, "omni-fast", OmniFastVideoRequest.class);
        assertRequestType(mapper, "omni-v2v-no-water", OmniV2vVideoRequest.class);
    }

    @Test
    void jsonGenerationReturnsAcceptedRequest() throws Exception {
        ImageSessionService sessions = mock(ImageSessionService.class);
        VideoGenerationService generation = mock(VideoGenerationService.class);
        ApiProfile profile = new ApiProfile(); profile.setId("profile-1");
        when(sessions.requireProfile(any(), any())).thenReturn(profile);
        when(generation.generate(eq(profile), any(), any(), any(), any()))
                .thenReturn(new VideoGenerationService.Accepted("request-1", 2));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new VideoGenerationController(sessions, generation,
                        mock(VideoMaterialUploadService.class)))
                .setControllerAdvice(new ImageApiExceptionHandler()).build();

        mvc.perform(post("/api/videos/generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"seedance-2.0\",\"prompt\":\"city\",\"count\":2}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestId").value("request-1"))
                .andExpect(jsonPath("$.count").value(2));
        mvc.perform(multipart("/api/videos/generate")
                        .file("referenceImage", "fake-png".getBytes())
                        .param("payload", "{\"model\":\"seedance-2.0\",\"prompt\":\"city\"}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    private static void assertRequestType(ObjectMapper mapper, String model,
            Class<? extends VideoGenerationRequest> expected) throws Exception {
        VideoGenerationRequest request = mapper.readValue(
                "{\"model\":\"" + model + "\",\"prompt\":\"city\"}", VideoGenerationRequest.class);
        org.junit.jupiter.api.Assertions.assertEquals(expected, request.getClass());
    }

    @Test
    void materialUploadReturnsPublicUrl() throws Exception {
        ImageSessionService sessions = mock(ImageSessionService.class);
        VideoGenerationService generation = mock(VideoGenerationService.class);
        VideoMaterialUploadService uploads = mock(VideoMaterialUploadService.class);
        ApiProfile profile = new ApiProfile(); profile.setId("profile-1");
        when(sessions.requireProfile(any(), any())).thenReturn(profile);
        when(uploads.upload(any(), any())).thenReturn(new VideoMaterialUploadService.Uploaded(
                "https://media.example.com/uploads/video-materials/a.mp4", "/tmp/a.mp4", "video/mp4", 3));
        MockMultipartFile file = new MockMultipartFile("file", "a.mp4", "video/mp4", new byte[]{1, 2, 3});
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new VideoGenerationController(sessions, generation, uploads))
                .setControllerAdvice(new ImageApiExceptionHandler()).build();

        mvc.perform(multipart("/api/videos/uploads").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://media.example.com/uploads/video-materials/a.mp4"))
                .andExpect(jsonPath("$.mimeType").value("video/mp4"));
    }

    @Test
    void materialUploadAcceptsImageFilesForJsonGenerationReferences() throws Exception {
        ImageSessionService sessions = mock(ImageSessionService.class);
        VideoGenerationService generation = mock(VideoGenerationService.class);
        VideoMaterialUploadService uploads = mock(VideoMaterialUploadService.class);
        ApiProfile profile = new ApiProfile(); profile.setId("profile-1");
        when(sessions.requireProfile(any(), any())).thenReturn(profile);
        when(uploads.upload(any(), any())).thenReturn(new VideoMaterialUploadService.Uploaded(
                "https://media.example.com/uploads/video-materials/frame.png", "/tmp/frame.png", "image/png", 8));
        MockMultipartFile file = new MockMultipartFile("file", "frame.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new VideoGenerationController(sessions, generation, uploads))
                .setControllerAdvice(new ImageApiExceptionHandler()).build();

        mvc.perform(multipart("/api/videos/uploads").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://media.example.com/uploads/video-materials/frame.png"))
                .andExpect(jsonPath("$.mimeType").value("image/png"));
    }
}
