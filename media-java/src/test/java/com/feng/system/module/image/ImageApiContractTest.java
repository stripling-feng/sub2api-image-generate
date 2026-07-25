package com.feng.system.module.image;

import com.feng.system.module.image.controller.ImageGenerationController;
import com.feng.system.module.image.controller.ImageQueryController;
import com.feng.system.module.image.controller.ImageSessionController;
import com.feng.system.module.image.exception.ImageApiExceptionHandler;
import com.feng.system.module.image.service.ImageGenerationService;
import com.feng.system.module.image.service.ImageQueryService;
import com.feng.system.module.image.service.ImageSessionService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.image.entity.ApiProfile;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ImageApiContractTest {

    @Test
    void preservesAllExpressRoutesAndRawResponseShapes() throws Exception {
        ImageSessionService sessions = mock(ImageSessionService.class);
        ImageQueryService queries = mock(ImageQueryService.class);
        ImageGenerationService generation = mock(ImageGenerationService.class);
        ApiProfile profile = new ApiProfile();
        profile.setId("profile"); profile.setBaseUrl("https://example.com"); profile.setKeyHash("1234567890abcdef");
        when(sessions.requireProfile(any(), any())).thenReturn(profile);
        when(sessions.bind(anyString(), anyString(), any())).thenReturn(new ImageSessionService.BoundProfile(profile, "10.0000000000", "9.5000000000"));
        when(queries.history(anyString(), anyInt(), anyInt())).thenReturn(Map.of("jobs", List.of(), "page", 1, "pageSize", 10, "total", 0, "totalPages", 1));
        when(queries.results(anyString(), anyString())).thenReturn(List.of());
        when(queries.deleteJobs(anyString())).thenReturn(2);
        when(queries.templates(anyString())).thenReturn(List.of());
        when(queries.createTemplate(anyString(), anyString(), anyString(), any())).thenReturn(Map.of("id", "template"));
        when(generation.generate(any(), anyMap(), anyList(), isNull())).thenReturn(new ImageGenerationService.Accepted("request", 1));

        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new ImageSessionController(sessions),
                        new ImageQueryController(sessions, queries),
                        new ImageGenerationController(sessions, generation, json))
                .setControllerAdvice(new ImageApiExceptionHandler()).build();

        mvc.perform(post("/api/session/bind").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"https://example.com\",\"apiKey\":\"12345678\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profile.id").value("profile"));
        mvc.perform(post("/api/images/generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"x\",\"model\":\"gpt-image-2\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.requestId").value("request"));
        mvc.perform(get("/api/images/history")).andExpect(status().isOk()).andExpect(jsonPath("$.jobs").isArray());
        mvc.perform(get("/api/images/results/request")).andExpect(status().isOk()).andExpect(jsonPath("$.jobs").isArray());
        mvc.perform(delete("/api/images/image")).andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
        mvc.perform(delete("/api/jobs")).andExpect(status().isOk()).andExpect(jsonPath("$.deletedCount").value(2));
        mvc.perform(delete("/api/jobs/job")).andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
        mvc.perform(get("/api/templates")).andExpect(status().isOk()).andExpect(jsonPath("$.templates").isArray());
        mvc.perform(post("/api/templates").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"prompt\":\"p\",\"params\":{}}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.template.id").value("template"));
        mvc.perform(delete("/api/templates/template")).andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
    }
}
