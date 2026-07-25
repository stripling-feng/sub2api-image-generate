package com.feng.system.module.image.service;

import com.feng.system.module.image.support.ImageTime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feng.system.module.image.entity.GeneratedImage;
import com.feng.system.module.image.mapper.GeneratedImageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ImageRetentionService {
    private final GeneratedImageMapper images;
    @Value("${image.upload-dir:./uploads}") private String uploadDir;

    @Scheduled(fixedDelayString = "${image.cleanup-interval-ms:3600000}", initialDelayString = "${image.cleanup-interval-ms:3600000}")
    public void cleanup() {
        List<GeneratedImage> expired = images.selectList(new LambdaQueryWrapper<GeneratedImage>()
                .lt(GeneratedImage::getCreatedAt, ImageTime.now().minusHours(24)));
        Path root = Path.of(uploadDir).toAbsolutePath().normalize();
        for (GeneratedImage image : expired) {
            try {
                Path file = Path.of(image.getFilePath()).toAbsolutePath().normalize();
                if (file.startsWith(root)) Files.deleteIfExists(file);
            } catch (Exception ignored) { }
        }
        if (!expired.isEmpty()) images.deleteBatchIds(expired.stream().map(GeneratedImage::getId).toList());
    }
}
