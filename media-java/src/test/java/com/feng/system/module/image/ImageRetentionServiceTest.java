package com.feng.system.module.image;

import com.feng.system.module.image.service.ImageRetentionService;
import com.feng.system.module.image.service.ImageStorageService;
import com.feng.system.module.media.entity.MediaTaskResult;
import com.feng.system.module.media.mapper.MediaTaskResultMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageRetentionServiceTest {
    @Test
    void cleanupOnlyLoadsExpiredImageResultsFromTheSharedTable() {
        MediaTaskResultMapper results = mock(MediaTaskResultMapper.class);
        ImageStorageService storage = mock(ImageStorageService.class);
        MediaTaskResult expired = new MediaTaskResult();
        expired.setId("image-result-1");
        expired.setCreatedAt(LocalDateTime.now().minusDays(2));
        when(results.selectExpiredImageResults(any())).thenReturn(List.of(expired));

        ImageRetentionService service = new ImageRetentionService(results, storage);

        service.cleanup();

        verify(results).selectExpiredImageResults(any());
        verify(storage).delete(expired);
        verify(results).deleteBatchIds(List.of("image-result-1"));
    }
}
