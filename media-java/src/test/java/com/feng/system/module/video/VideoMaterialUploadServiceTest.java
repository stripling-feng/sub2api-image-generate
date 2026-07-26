package com.feng.system.module.video;

import com.feng.system.module.image.exception.ImageApiException;
import com.feng.system.module.image.service.ImageGateway;
import com.feng.system.module.system.service.UploadFileService;
import com.feng.system.module.system.vo.UploadFileVO;
import com.feng.system.module.video.service.VideoMaterialUploadService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VideoMaterialUploadServiceTest {
    @TempDir Path uploads;

    @Test
    void storesVideoMaterialAndReturnsHttpsUrl() {
        UploadFileService uploadFileService = mock(UploadFileService.class);
        VideoMaterialUploadService service = new VideoMaterialUploadService(uploadFileService, uploads.toString(), "");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "media.example.com");
        MockMultipartFile file = new MockMultipartFile("file", "clip.mp4", "video/mp4", new byte[]{0, 1, 2});
        when(uploadFileService.upload(any(), eq(materialDirectory()))).thenReturn(uploadedFile(
                "https://media.example.com/material/" + today() + "/clip.mp4", "clip.mp4", 3L, "mp4"));

        VideoMaterialUploadService.Uploaded uploaded = service.upload(file, request);

        assertEquals("https://media.example.com/material/" + today() + "/clip.mp4", uploaded.url());
        assertEquals("video/mp4", uploaded.mimeType());
        assertEquals(3, uploaded.sizeBytes());
    }

    @Test
    void rejectsLocalPathReturnedByDefaultUploadService() {
        UploadFileService uploadFileService = mock(UploadFileService.class);
        VideoMaterialUploadService service = new VideoMaterialUploadService(uploadFileService, uploads.toString(), "");
        MockMultipartFile file = new MockMultipartFile("file", "clip.mp4", "video/mp4", new byte[]{0, 1, 2});
        when(uploadFileService.upload(any(), eq(materialDirectory()))).thenReturn(uploadedFile(
                "D:\\uploads\\material\\20260726\\clip.mp4", "clip.mp4", 3L, "mp4"));

        ImageApiException error = assertThrows(ImageApiException.class,
                () -> service.upload(file, new MockHttpServletRequest()));

        assertEquals(500, error.getStatus());
    }

    @Test
    void rejectsUnsupportedMaterial() {
        VideoMaterialUploadService service = new VideoMaterialUploadService(uploads.toString(), "");
        MockMultipartFile file = new MockMultipartFile("file", "bad.txt", "text/plain", new byte[]{1});

        assertThrows(ImageApiException.class, () -> service.upload(file, new MockHttpServletRequest()));
    }

    @Test
    void storesReferenceImageForUpstreamUrl() {
        UploadFileService uploadFileService = mock(UploadFileService.class);
        VideoMaterialUploadService service = new VideoMaterialUploadService(uploadFileService, uploads.toString(), "https://media.example.com");
        when(uploadFileService.upload(any(), eq(materialDirectory()))).thenReturn(uploadedFile(
                "https://media.example.com/material/" + today() + "/a.png", "a.png", 4L, "png"));

        VideoMaterialUploadService.Uploaded uploaded = service.uploadImage(
                new ImageGateway.Upload("a.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G'}));

        assertEquals("https://media.example.com/material/" + today() + "/a.png", uploaded.url());
    }

    @Test
    void generatedVideoStorageIsValidatedIdempotentAndDeletable() throws Exception {
        VideoMaterialUploadService service = new VideoMaterialUploadService(uploads.toString(), "https://media.example.com");
        byte[] mp4 = new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
        var store = VideoMaterialUploadService.class.getMethod("storeGenerated", String.class, byte[].class, String.class);
        var delete = VideoMaterialUploadService.class.getMethod("deleteGenerated", String.class);

        VideoMaterialUploadService.Uploaded first = (VideoMaterialUploadService.Uploaded) store.invoke(service, "job-1", mp4, "video/mp4");
        VideoMaterialUploadService.Uploaded second = (VideoMaterialUploadService.Uploaded) store.invoke(service, "job-1", mp4, "video/mp4");
        assertEquals(first, second);
        assertEquals(1, Files.list(uploads.resolve("generated-videos")).count());
        assertThrows(InvocationTargetException.class, () -> store.invoke(service, "bad", new byte[] {1, 2, 3}, "video/mp4"));

        delete.invoke(service, "job-1");
        assertFalse(Files.exists(Path.of(first.filePath())));
    }

    private static String materialDirectory() {
        return "material/" + today();
    }

    private static String today() {
        return LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    private static UploadFileVO uploadedFile(String filePath, String currentName, long size, String fileType) {
        UploadFileVO vo = new UploadFileVO();
        vo.setFilePath(filePath);
        vo.setCurrentName(currentName);
        vo.setFileSize(size);
        vo.setFileType(fileType);
        return vo;
    }
}
