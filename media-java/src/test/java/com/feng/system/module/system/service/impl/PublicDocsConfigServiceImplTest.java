package com.feng.system.module.system.service.impl;

import com.feng.system.common.exception.BusinessException;
import com.feng.system.module.system.dto.PublicDocsConfigDTO;
import com.feng.system.module.system.entity.SysUploadFile;
import com.feng.system.module.system.mapper.SysConfigMapper;
import com.feng.system.module.system.mapper.SysUploadFileMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicDocsConfigServiceImplTest {

    @Test
    void savesImageAndVideoMarkdownFileBindings() {
        SysConfigMapper configs = mock(SysConfigMapper.class);
        SysUploadFileMapper uploads = mock(SysUploadFileMapper.class);
        PublicDocsConfigServiceImpl service = new PublicDocsConfigServiceImpl(configs, uploads);

        when(configs.selectOne(any())).thenReturn(null);
        when(uploads.selectById(11L)).thenReturn(markdown(11L, "image.md"));
        when(uploads.selectById(12L)).thenReturn(markdown(12L, "video.md"));

        PublicDocsConfigDTO dto = new PublicDocsConfigDTO();
        dto.setImageFileId(11L);
        dto.setVideoFileId(12L);

        service.save(dto);

        verify(configs, times(2)).insert(any());
    }

    @Test
    void rejectsNonMarkdownFileBinding() {
        SysConfigMapper configs = mock(SysConfigMapper.class);
        SysUploadFileMapper uploads = mock(SysUploadFileMapper.class);
        PublicDocsConfigServiceImpl service = new PublicDocsConfigServiceImpl(configs, uploads);
        SysUploadFile file = new SysUploadFile();
        file.setId(11L);
        file.setOriginalName("image.png");
        file.setFileType("image/png");
        when(uploads.selectById(11L)).thenReturn(file);

        PublicDocsConfigDTO dto = new PublicDocsConfigDTO();
        dto.setImageFileId(11L);

        assertThrows(BusinessException.class, () -> service.save(dto));
    }

    private SysUploadFile markdown(Long id, String originalName) {
        SysUploadFile file = new SysUploadFile();
        file.setId(id);
        file.setOriginalName(originalName);
        file.setFileType("text/markdown");
        return file;
    }
}
