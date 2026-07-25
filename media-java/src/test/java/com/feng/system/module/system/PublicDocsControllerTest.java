package com.feng.system.module.system;

import com.feng.system.module.system.controller.PublicDocsController;
import com.feng.system.module.system.service.PublicDocsService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicDocsControllerTest {

    @Test
    void returnsConfiguredMarkdownDocumentByKey() throws Exception {
        PublicDocsService service = key -> new PublicDocsService.Document("image", "Image API",
                "# Image API\n\n## Quick Start\ncontent", LocalDateTime.of(2026, 7, 26, 10, 0));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PublicDocsController(service)).build();

        mvc.perform(get("/api/docs/image"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("image"))
                .andExpect(jsonPath("$.title").value("Image API"))
                .andExpect(jsonPath("$.content").value("# Image API\n\n## Quick Start\ncontent"));
    }

    @Test
    void rejectsUnknownDocumentKey() throws Exception {
        PublicDocsService service = key -> new PublicDocsService.Document("image", "Image API",
                "# Image API", LocalDateTime.of(2026, 7, 26, 10, 0));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PublicDocsController(service)).build();

        mvc.perform(get("/api/docs/other"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("文档不存在"));
    }
}
