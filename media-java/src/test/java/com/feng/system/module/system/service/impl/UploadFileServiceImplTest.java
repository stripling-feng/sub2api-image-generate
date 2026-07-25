package com.feng.system.module.system.service.impl;

import com.feng.system.module.system.entity.SysUploadFile;
import com.feng.system.module.system.mapper.SysUploadFileMapper;
import com.feng.system.module.system.service.SystemConfigService;
import com.feng.system.module.system.vo.UploadFileVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadFileServiceImplTest {

    @Mock
    private SysUploadFileMapper uploadFileMapper;

    @Mock
    private SystemConfigService systemConfigService;

    @InjectMocks
    private UploadFileServiceImpl uploadFileService;

    @Test
    void uploadShouldReturnExistingFileWhenMd5AlreadyExists() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "same-content".getBytes()
        );
        SysUploadFile existingFile = new SysUploadFile();
        existingFile.setId(7L);
        existingFile.setOriginalName("old-avatar.png");
        existingFile.setCurrentName("stored-avatar.png");
        existingFile.setFilePath("https://cdn.example.com/stored-avatar.png");
        existingFile.setFileSize(123L);
        existingFile.setFileType("png");
        existingFile.setMd5Value("793953ee398d864ec40252df9554c3e6");

        when(uploadFileMapper.selectOne(any())).thenReturn(existingFile);

        UploadFileVO result = uploadFileService.upload(file);

        assertEquals(existingFile.getId(), result.getId());
        assertEquals(existingFile.getFilePath(), result.getFilePath());
        assertEquals(existingFile.getMd5Value(), result.getMd5Value());
        verify(uploadFileMapper, never()).insert(any());
        verify(systemConfigService, never()).getUploadProvider();
    }
}
