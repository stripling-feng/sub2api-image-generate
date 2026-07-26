package com.feng.system.module.image.service;

import com.feng.system.module.image.support.ImageTime;
import com.feng.system.module.media.entity.MediaTaskResult;
import com.feng.system.module.media.mapper.MediaTaskResultMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ImageRetentionService {
    private final MediaTaskResultMapper results;
    private final ImageStorageService storage;

    @Scheduled(fixedDelayString = "${image.cleanup-interval-ms:3600000}", initialDelayString = "${image.cleanup-interval-ms:3600000}")
    public void cleanup() {
        List<MediaTaskResult> expired = results.selectExpiredImageResults(ImageTime.now().minusHours(24));
        expired.forEach(storage::delete);
        if (!expired.isEmpty()) results.deleteBatchIds(expired.stream().map(MediaTaskResult::getId).toList());
    }
}
