package com.feng.system.module.image;

import com.feng.system.module.image.controller.ImageGenerationController;
import com.feng.system.module.image.controller.ImageQueryController;
import com.feng.system.module.image.dto.ImageGenerateRequest;
import com.feng.system.module.image.exception.ImageApiException;
import com.feng.system.module.image.exception.ImageApiExceptionHandler;
import com.feng.system.module.image.service.ImageGenerationService;
import com.feng.system.module.image.service.ImageQueryService;
import com.feng.system.module.image.service.ImageReferenceUploadService;
import com.feng.system.module.image.service.ImageSessionService;

import com.feng.system.module.image.entity.ApiProfile;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ImageApiContractTest {

    @Test
    void symbolicBillingErrorCodeDoesNotBreakApiResponse() {
        ResponseEntity<?> response = new ImageApiExceptionHandler().handleImageApi(
                new ImageApiException(401, "Invalid API Key.", "INVALID_API_KEY", null));

        assertEquals(401, response.getStatusCode().value());
        assertEquals(401, ((com.feng.system.common.api.ApiResponse<?>) response.getBody()).getCode());
    }

    @Test
    void exposesImageRoutesWithoutRemovedHealthOrSessionEndpoints() throws Exception {
        ImageSessionService sessions = mock(ImageSessionService.class);
        ImageQueryService queries = mock(ImageQueryService.class);
        ImageGenerationService generation = mock(ImageGenerationService.class);
        ImageReferenceUploadService uploads = mock(ImageReferenceUploadService.class);
        ApiProfile profile = new ApiProfile();
        profile.setId("profile"); profile.setBaseUrl("https://example.com"); profile.setKeyHash("1234567890abcdef");
        profile.setEncryptedKey("plain-key");
        when(sessions.requireProfile(any(), any())).thenReturn(profile);
        when(queries.history(anyString(), anyInt(), anyInt())).thenReturn(Map.of("jobs", List.of(), "page", 1, "pageSize", 10, "total", 0, "totalPages", 1));
        when(queries.results(anyString(), anyString())).thenReturn(List.of());
        when(queries.download(anyString(), eq("image"))).thenReturn(new ImageQueryService.DownloadedImage(
                "fake-png".getBytes(), "image/png", "image.png"));
        when(queries.deleteJobs(anyString())).thenReturn(2);
        when(generation.generate(any(), any(ImageGenerateRequest.class), anyList(), isNull())).thenReturn(new ImageGenerationService.Accepted("request", 1));
        when(uploads.upload(any(), any())).thenReturn(new ImageReferenceUploadService.Uploaded(
                "https://cdn.example.com/material/" + today() + "/image.png", "/tmp/image.png", "image/png", 8));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new ImageQueryController(sessions, queries),
                        new ImageGenerationController(sessions, generation, uploads))
                .setControllerAdvice(new ImageApiExceptionHandler()).build();

        mvc.perform(get("/api/health")).andExpect(status().isNotFound());
        mvc.perform(post("/api/session/bind").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"https://example.com\",\"apiKey\":\"12345678\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/session/logout")).andExpect(status().isNotFound());
        mvc.perform(get("/api/session/me")).andExpect(status().isNotFound());
        mvc.perform(post("/api/images/generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"x\",\"model\":\"gpt-image-2\",\"async\":true,\"size\":\"1:1\",\"images\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.requestId").value("request"));
        mvc.perform(multipart("/api/images/generate")
                        .file("image", "fake-png".getBytes())
                        .param("payload", "{\"prompt\":\"x\",\"model\":\"gpt-image-2\",\"async\":true,\"size\":\"1:1\",\"images\":[]}"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(multipart("/api/images/uploads")
                        .file("file", "fake-png".getBytes()).contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.url").value("https://cdn.example.com/material/" + today() + "/image.png"));
        mvc.perform(get("/api/images/history")).andExpect(status().isOk()).andExpect(jsonPath("$.data.jobs").isArray());
        mvc.perform(get("/api/images/results/request")).andExpect(status().isOk()).andExpect(jsonPath("$.data.jobs").isArray());
        mvc.perform(get("/api/images/image/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().bytes("fake-png".getBytes()));
        mvc.perform(delete("/api/images/image")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/jobs")).andExpect(status().isOk()).andExpect(jsonPath("$.data.deletedCount").value(2));
        mvc.perform(delete("/api/jobs/job")).andExpect(status().isOk()).andExpect(jsonPath("$.data.ok").value(true));
        mvc.perform(get("/api/templates")).andExpect(status().isNotFound());
        mvc.perform(post("/api/templates").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"prompt\":\"p\",\"params\":{}}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/templates/template")).andExpect(status().isNotFound());
    }

    private static String today() {
        return LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}
