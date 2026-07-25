package com.feng.system.module.system.service.impl;

import com.feng.system.common.exception.BusinessException;
import com.feng.system.module.system.entity.SysConfig;
import com.feng.system.module.system.entity.SysUploadFile;
import com.feng.system.module.system.mapper.SysConfigMapper;
import com.feng.system.module.system.mapper.SysUploadFileMapper;
import com.feng.system.module.system.service.PublicDocsService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicDocsServiceImplTest {

    @Test
    void readsMarkdownFileConfiguredForImageDocument() throws Exception {
        SysConfigMapper configs = mock(SysConfigMapper.class);
        SysUploadFileMapper uploads = mock(SysUploadFileMapper.class);
        PublicDocsServiceImpl service = new PublicDocsServiceImpl(configs, uploads);
        Path markdown = Files.createTempFile("image-doc", ".md");
        Files.writeString(markdown, "# Image API\n\n## Quick Start\nHello");

        SysConfig config = new SysConfig();
        config.setConfigValue("7");
        SysUploadFile file = new SysUploadFile();
        file.setId(7L);
        file.setOriginalName("image-doc.md");
        file.setFileType("md");
        file.setFilePath(markdown.toString());
        file.setUpdateTime(LocalDateTime.of(2026, 7, 26, 10, 0));

        when(configs.selectOne(any())).thenReturn(config);
        when(uploads.selectById(7L)).thenReturn(file);

        PublicDocsService.Document result = service.getDocument("image");

        assertEquals("image", result.key());
        assertEquals("Image API", result.title());
        assertEquals("# Image API\n\n## Quick Start\nHello", result.content());
        assertEquals(file.getUpdateTime(), result.updatedAt());
    }

    @Test
    void rejectsUnknownDocumentKey() {
        PublicDocsServiceImpl service = new PublicDocsServiceImpl(mock(SysConfigMapper.class), mock(SysUploadFileMapper.class));

        assertThrows(BusinessException.class, () -> service.getDocument("other"));
    }
}
