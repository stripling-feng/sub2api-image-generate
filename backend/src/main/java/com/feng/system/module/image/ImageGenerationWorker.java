package com.feng.system.module.image;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.image.entity.ApiProfile;
import com.feng.system.module.image.entity.GenerationJob;
import com.feng.system.module.image.mapper.GenerationJobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ImageGenerationWorker {
    private final GenerationJobMapper jobs;
    private final ImageGateway gateway;
    private final ImageStorageService storage;
    @Value("${image.upstream-base-url:}") private String upstreamOverride;

    @Async("taskExecutor")
    public void run(String jobId, String baseUrl, String apiKey, String generationPath, String editPath, Map<String, Object> body,
                    List<ImageGateway.Upload> images, ImageGateway.Upload mask) {
        long started = System.currentTimeMillis();
        try {
            String target = upstreamOverride == null || upstreamOverride.isBlank()
                    ? SafeUpstreamUrl.requirePublicHttps(baseUrl) : upstreamOverride;
            ImageGateway.GatewayResponse response = gateway.create(target, apiKey, generationPath, editPath, body, images, mask);
            List<ImageGateway.Item> items = gateway.items(response.payload());
            if (items.isEmpty()) throw new ImageApiException(502, "sub2api returned no images.");
            int index = 0;
            for (ImageGateway.Item item : items) {
                if (item.b64() != null) storage.saveBase64(jobId, index++, item.b64());
                else if (item.url() != null) storage.saveUrl(jobId, index++, item.url());
            }
            GenerationJob update = new GenerationJob();
            update.setId(jobId); update.setStatus("SUCCEEDED"); update.setProgress(100);
            update.setDurationMs((int) (System.currentTimeMillis() - started)); update.setCompletedAt(ImageTime.now()); update.setUpdatedAt(ImageTime.now());
            jobs.updateById(update);
        } catch (Exception e) {
            GenerationJob update = new GenerationJob();
            update.setId(jobId); update.setStatus("FAILED"); update.setErrorMessage(message(e));
            update.setDurationMs((int) (System.currentTimeMillis() - started)); update.setCompletedAt(ImageTime.now()); update.setUpdatedAt(ImageTime.now());
            jobs.updateById(update);
        }
    }

    private static String message(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
}
