package com.feng.system.module.image;

import com.feng.system.module.image.exception.ImageApiException;
import com.feng.system.module.image.service.ImageReferenceUploadService;
import com.feng.system.module.system.service.UploadFileService;
import com.feng.system.module.system.vo.UploadFileVO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImageReferenceUploadServiceTest {

    @Test
    void rejectsLocalPathReturnedByDefaultUploadService() {
        UploadFileService uploads = mock(UploadFileService.class);
        UploadFileVO stored = new UploadFileVO();
        stored.setFilePath("D:\\uploads\\material\\20260726\\image.png");
        when(uploads.upload(any(), anyString())).thenReturn(stored);
        ImageReferenceUploadService service = new ImageReferenceUploadService(uploads);
        MockMultipartFile file = new MockMultipartFile(
                "file", "image.png", "image/png", new byte[]{1, 2, 3});

        ImageApiException error = assertThrows(ImageApiException.class,
                () -> service.upload(file, new MockHttpServletRequest()));

        assertEquals(500, error.getStatus());
    }
}
