package com.feng.system.module.image.service;

import com.feng.system.module.image.exception.ImageApiException;
import com.feng.system.module.image.support.GenerationAuditJson;
import com.feng.system.module.image.support.ImageTime;
import com.feng.system.module.image.support.SafeUpstreamUrl;
import com.feng.system.module.media.entity.MediaTask;
import com.feng.system.module.media.mapper.MediaTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 图片生成后台执行器：在异步线程中同步调用上游完成生成，
 * 将结果图片持久化到存储，并更新任务的最终状态（SUCCEEDED/FAILED）。
 */
@Service
@RequiredArgsConstructor
public class ImageGenerationWorker {
    private final MediaTaskMapper tasks;
    private final ImageGateway gateway;
    private final ImageStorageService storage;
    @Value("${image.upstream-base-url:}") private String upstreamOverride;

    /**
     * 在后台线程中执行一次图片生成任务：调用上游、保存结果图片并更新任务状态。
     * 成功时将进度置为 100 并记录耗时；任何异常都会把任务标记为 FAILED 而不向外抛出。
     *
     * @param taskId 本地任务 ID
     * @param body   已合并模型默认参数的上游请求体
     */
    @Async("taskExecutor")
    public void run(String taskId, String baseUrl, String apiKey, String generationPath, Map<String, Object> body) {
        long started = System.currentTimeMillis();
        MediaTask current = tasks.selectById(taskId);
        String responseSnapshot = current == null ? "{}" : current.getUpstreamResponse();
        try {
            // 未配置覆盖地址时，校验上游必须是公网 HTTPS 地址（防 SSRF）
            String target = upstreamOverride == null || upstreamOverride.isBlank()
                    ? SafeUpstreamUrl.requirePublicHttps(baseUrl) : upstreamOverride;
            ImageGateway.GatewayResponse response = gateway.create(target, apiKey, generationPath, body);
            responseSnapshot = GenerationAuditJson.stringify(response.payload());
            List<ImageGateway.Item> items = gateway.items(response.payload());
            if (items.isEmpty()) throw new ImageApiException(502, "sub2api returned no images.");
            int index = 0;
            // 逐张保存结果：优先保存 base64 内容，否则按 URL 拉取保存
            for (ImageGateway.Item item : items) {
                if (item.b64() != null) storage.saveBase64(taskId, index++, item.b64());
                else if (item.url() != null) storage.saveUrl(taskId, index++, item.url());
            }
            MediaTask update = update(taskId, responseSnapshot);
            update.setStatus("SUCCEEDED");
            update.setProgress(100);
            update.setDurationMs((int) (System.currentTimeMillis() - started));
            update.setCompletedAt(ImageTime.now());
            update.setUpdatedAt(ImageTime.now());
            tasks.updateById(update);
        } catch (Exception e) {
            // 若尚无有效的上游响应快照，则以异常负载兜底，保证审计信息不为空
            if (responseSnapshot == null || responseSnapshot.isBlank() || "{}".equals(responseSnapshot) || "[]".equals(responseSnapshot)) {
                responseSnapshot = GenerationAuditJson.stringify(payload(e));
            }
            MediaTask update = update(taskId, responseSnapshot);
            update.setStatus("FAILED");
            update.setErrorMessage(message(e));
            update.setDurationMs((int) (System.currentTimeMillis() - started));
            update.setCompletedAt(ImageTime.now());
            update.setUpdatedAt(ImageTime.now());
            tasks.updateById(update);
        }
    }

    private MediaTask update(String taskId, String responses) {
        MediaTask update = new MediaTask();
        update.setId(taskId);
        update.setUpstreamResponse(responses);
        return update;
    }

    private static String message(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static Object payload(Exception error) {
        return error instanceof ImageApiException api && api.getPayload() != null
                ? api.getPayload() : Map.of("message", message(error));
    }
}
