package com.feng.system.module.video;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.image.ImageApiExceptionHandler;
import com.feng.system.module.image.ImageSessionService;
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
    void jsonGenerationReturnsAcceptedRequest() throws Exception {
        ImageSessionService sessions = mock(ImageSessionService.class);
        VideoGenerationService generation = mock(VideoGenerationService.class);
        ApiProfile profile = new ApiProfile(); profile.setId("profile-1");
        when(sessions.requireProfile(any(), any())).thenReturn(profile);
        when(generation.generate(eq(profile), any(), any(), any(), any()))
                .thenReturn(new VideoGenerationService.Accepted("request-1", 2));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new VideoGenerationController(sessions, generation,
                        mock(VideoMaterialUploadService.class), new ObjectMapper()))
                .setControllerAdvice(new ImageApiExceptionHandler()).build();

        mvc.perform(post("/api/videos/generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"seedance-2.0\",\"prompt\":\"city\",\"count\":2}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestId").value("request-1"))
                .andExpect(jsonPath("$.count").value(2));
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
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new VideoGenerationController(sessions, generation, uploads, new ObjectMapper()))
                .setControllerAdvice(new ImageApiExceptionHandler()).build();

        mvc.perform(multipart("/api/videos/uploads").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://media.example.com/uploads/video-materials/a.mp4"))
                .andExpect(jsonPath("$.mimeType").value("video/mp4"));
    }
}
