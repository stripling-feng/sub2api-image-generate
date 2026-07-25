package com.feng.system.module.video;

import com.feng.system.module.image.ImageApiException;
import com.feng.system.module.image.ImageGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

class VideoMaterialUploadServiceTest {
    @TempDir Path uploads;

    @Test
    void storesVideoMaterialAndReturnsHttpsUrl() {
        VideoMaterialUploadService service = new VideoMaterialUploadService(uploads.toString(), "");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "media.example.com");
        MockMultipartFile file = new MockMultipartFile("file", "clip.mp4", "video/mp4", new byte[]{0, 1, 2});

        VideoMaterialUploadService.Uploaded uploaded = service.upload(file, request);

        assertTrue(uploaded.url().startsWith("https://media.example.com/uploads/video-materials/"));
        assertTrue(uploaded.url().endsWith(".mp4"));
        assertTrue(Files.exists(Path.of(uploaded.filePath())));
        assertEquals("video/mp4", uploaded.mimeType());
        assertEquals(3, uploaded.sizeBytes());
    }

    @Test
    void rejectsUnsupportedMaterial() {
        VideoMaterialUploadService service = new VideoMaterialUploadService(uploads.toString(), "");
        MockMultipartFile file = new MockMultipartFile("file", "bad.txt", "text/plain", new byte[]{1});

        assertThrows(ImageApiException.class, () -> service.upload(file, new MockHttpServletRequest()));
    }

    @Test
    void storesReferenceImageForUpstreamUrl() {
        VideoMaterialUploadService service = new VideoMaterialUploadService(uploads.toString(), "https://media.example.com");

        VideoMaterialUploadService.Uploaded uploaded = service.uploadImage(
                new ImageGateway.Upload("a.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G'}));

        assertTrue(uploaded.url().startsWith("https://media.example.com/uploads/video-materials/"));
        assertTrue(uploaded.url().endsWith(".png"));
        assertTrue(Files.exists(Path.of(uploaded.filePath())));
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
}
