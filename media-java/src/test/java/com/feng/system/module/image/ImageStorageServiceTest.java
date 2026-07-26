package com.feng.system.module.image;

import com.feng.system.module.image.service.ImageGateway;
import com.feng.system.module.image.service.ImageStorageService;
import com.feng.system.module.media.entity.MediaTaskResult;
import com.feng.system.module.media.service.MediaTaskResultService;
import com.feng.system.module.system.service.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ImageStorageServiceTest {
    @TempDir Path uploads;

    @Test
    void duplicateResultDoesNotLeaveAnOrphanedImageFile() throws Exception {
        MediaTaskResultService results = mock(MediaTaskResultService.class);
        MediaTaskResult existing = new MediaTaskResult();
        existing.setAddress("/img/existing.png");
        when(results.saveIfAbsent(eq("task-1"), eq(0), anyString(), anyMap())).thenReturn(existing);
        SystemConfigService configs = mock(SystemConfigService.class);
        when(configs.getUploadProvider()).thenReturn("server");
        ImageStorageService storage = new ImageStorageService(results, mock(ImageGateway.class), configs);
        ReflectionTestUtils.setField(storage, "uploadDir", uploads.toString());

        storage.save("task-1", 0,
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}, "image/png");

        try (var files = Files.walk(uploads)) {
            assertEquals(0, files.filter(Files::isRegularFile).count());
        }
    }

    @Test
    void cloudflareR2ProviderStoresGeneratedImageAsRemoteObject() {
        MediaTaskResultService results = mock(MediaTaskResultService.class);
        MediaTaskResult saved = new MediaTaskResult();
        saved.setAddress("https://cdn.example.com/images/" + today() + "/tcboys.de_1234567890_abcd1234.png");
        when(results.saveIfAbsent(eq("task-r2"), eq(0), anyString(), anyMap())).thenReturn(saved);
        SystemConfigService configs = mock(SystemConfigService.class);
        when(configs.getUploadProvider()).thenReturn("cloudflare-r2");
        when(configs.getConfigValue("upload.r2.endpoint")).thenReturn("https://account.r2.cloudflarestorage.com");
        when(configs.getConfigValue("upload.r2.bucket")).thenReturn("media");
        when(configs.getConfigValue("upload.r2.access-key-id")).thenReturn("access");
        when(configs.getConfigValue("upload.r2.access-key-secret")).thenReturn("secret");
        when(configs.getConfigValue("upload.r2.domain")).thenReturn("https://cdn.example.com");
        ImageStorageService storage = spy(new ImageStorageService(results, mock(ImageGateway.class), configs));
        doNothing().when(storage).putR2Object(anyString(), any(), anyString());

        storage.save("task-r2", 0,
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}, "image/png");

        verify(storage).putR2Object(matches("images/" + today() + "/tcboys\\.de_\\d+_[a-f0-9]{8}\\.png"),
                any(), eq("image/png"));
        verify(results).saveIfAbsent(eq("task-r2"), eq(0), startsWith("https://cdn.example.com/images/" + today()),
                argThat(metadata -> "cloudflare-r2".equals(metadata.get("storageProvider"))
                        && String.valueOf(metadata.get("objectKey")).startsWith("images/" + today())
                        && "image/png".equals(metadata.get("mimeType"))));
    }

    @Test
    void cloudflareR2ResultDeletesRemoteObjectByObjectKey() {
        MediaTaskResultService results = mock(MediaTaskResultService.class);
        MediaTaskResult result = new MediaTaskResult();
        result.setAddress("https://cdn.example.com/images/2026-07-26/a.png");
        when(results.metadata(result)).thenReturn(Map.of(
                "storageProvider", "cloudflare-r2",
                "objectKey", "images/2026-07-26/a.png"));
        SystemConfigService configs = mock(SystemConfigService.class);
        when(configs.getConfigValue("upload.r2.bucket")).thenReturn("media");
        ImageStorageService storage = spy(new ImageStorageService(results, mock(ImageGateway.class), configs));
        doNothing().when(storage).deleteR2Object(anyString());

        storage.delete(result);

        verify(storage).deleteR2Object("images/2026-07-26/a.png");
    }

    private static String today() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }
}
