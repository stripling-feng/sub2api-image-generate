package com.feng.system.module.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.image.service.ImageModelConfigService;
import com.feng.system.module.image.service.Sub2apiBillingService;
import com.feng.system.module.image.support.ImageTime;
import com.feng.system.module.image.support.SafeUpstreamUrl;
import com.feng.system.module.media.entity.MediaBillingRecord;
import com.feng.system.module.media.entity.MediaTask;
import com.feng.system.module.media.service.MediaBillingRecordService;
import com.feng.system.module.media.service.MediaTaskData;
import com.feng.system.module.media.service.MediaTaskResultService;
import com.feng.system.module.media.entity.MediaTaskResult;
import com.feng.system.module.media.mapper.MediaTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 视频任务轮询器:定时拉取处于 PENDING 状态的视频任务,向上游查询进度,
 * 完成时保存产物并结算费用,失败或超时时释放预扣费用并标记任务失败。
 */
@Service
@RequiredArgsConstructor
public class VideoTaskPoller {
    private final MediaTaskMapper tasks;
    private final VideoGateway gateway;
    private final Sub2apiBillingService billing;
    private final MediaBillingRecordService mediaBilling;
    private final ImageModelConfigService modelConfigs;
    private final MediaTaskResultService resultService;
    private final ObjectMapper json;
    @Value("${video.poll-max-duration-ms:7200000}") private long maxDuration = 7_200_000;

    /** 定时轮询入口:每轮按创建时间取最多 10 个已有上游任务 ID 的 PENDING 任务逐个处理。 */
    @Scheduled(fixedDelayString = "${video.poll-interval-ms:5000}", initialDelayString = "${video.poll-interval-ms:5000}")
    public void poll() {
        List<MediaTask> due = tasks.selectList(new LambdaQueryWrapper<MediaTask>()
                .eq(MediaTask::getTaskType, "VIDEO").eq(MediaTask::getStatus, "PENDING")
                .isNotNull(MediaTask::getUpstreamTaskId)
                .orderByAsc(MediaTask::getCreatedAt).last("LIMIT 10"));
        for (MediaTask task : due) process(task);
    }

    /**
     * 处理单个任务:查询上游状态后按优先级分支——
     * 超过最大时长判定超时失败;上游 FAILED 则失败;非 omni 模型完成但缺结果 URL 也算失败;
     * 完成则落库产物并结算;否则更新进度继续等待。查询异常仅记录错误,留待下轮重试。
     */
    public void process(MediaTask task) {
        try {
            ImageModelConfigService.RuntimeModel runtime = modelConfigs.requireVideo(task.getModelConfigId());
            VideoGateway.Task upstream = gateway.query(runtime.provider().getBaseUrl(), runtime.provider().getVideoApiKey(),
                    runtime.model().getGenerationPath(), task.getUpstreamTaskId());
            long elapsed = Duration.between(task.getCreatedAt(), ImageTime.now()).toMillis();
            if (elapsed >= maxDuration) fail(task, "Video task timed out.", elapsed);
            else if ("FAILED".equals(upstream.status())) fail(task,
                    upstream.error() == null ? "Video task failed." : upstream.error(), elapsed);
            else if ("COMPLETED".equals(upstream.status())
                    && (upstream.resultUrl() == null || upstream.resultUrl().isBlank()))
                fail(task, "Completed video task returned no result URL.", elapsed);
            else if ("COMPLETED".equals(upstream.status())) complete(task, upstream, runtime, elapsed);
            else pending(task, upstream);
        } catch (Exception error) {
            retry(task, error);
        }
    }

    // 完成处理:直接保存上游结果 URL,随后结算费用并置为 SUCCEEDED
    private void complete(MediaTask task, VideoGateway.Task upstream,
                          ImageModelConfigService.RuntimeModel runtime, long elapsed) {
        MediaTaskResult result = resultService.find(task.getId(), 0);
        if (result == null) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("mimeType", "video/mp4");
            resultService.saveIfAbsent(task.getId(), 0,
                    SafeUpstreamUrl.requirePublicHttps(upstream.resultUrl()), metadata);
        }
        settleSuccess(task, runtime, elapsed);
        setUpstreamStatus(task, "completed");
        task.setStatus("SUCCEEDED"); task.setProgress(100); task.setDurationMs((int) elapsed);
        task.setCompletedAt(ImageTime.now());
        task.setUpdatedAt(ImageTime.now()); tasks.updateById(task);
    }

    // 成功结算:仅当计费记录仍处于 RESERVED 时向计费系统正式扣费,失败则标记 CHARGE_FAILED
    private void settleSuccess(MediaTask task, ImageModelConfigService.RuntimeModel runtime, long elapsed) {
        MediaBillingRecord record = mediaBilling.find(task.getId());
        if (record == null || !"RESERVED".equals(record.getDeductionStatus())) return;
        Map<String, Object> data = MediaTaskData.read(json, task.getTaskData());
        int duration = MediaTaskData.integer(data.get("duration"), 1);
        try {
            String usage = billing.settleVideo(task.getId(), record.getApiKeyId(), record.getUserId(), record.getAccountId(),
                    record.getTaskFee(), String.valueOf(data.getOrDefault("model", runtime.model().getModelKey())),
                    runtime.model().getUpstreamModel(), runtime.model().getGenerationPath(), duration);
            mediaBilling.charged(task.getId(), usage); task.setBillingStatus("CHARGED");
        } catch (Exception error) {
            mediaBilling.failed(task.getId(), "CHARGE_FAILED", message(error)); task.setBillingStatus("CHARGE_FAILED");
        }
    }

    // 失败处理:释放预扣费用(计费方若判定已结算则记为 CHARGED),并把任务置为 FAILED
    private void fail(MediaTask task, String message, long elapsed) {
        MediaBillingRecord record = mediaBilling.find(task.getId());
        if (record != null && "RESERVED".equals(record.getDeductionStatus())) {
            try {
                String result = billing.releaseVideo(task.getId(), record.getApiKeyId(), record.getUserId(), record.getTaskFee());
                if ("settled".equals(result)) {
                    mediaBilling.charged(task.getId(), result); task.setBillingStatus("CHARGED");
                } else {
                    mediaBilling.released(task.getId()); task.setBillingStatus("RELEASED");
                }
            } catch (Exception error) {
                task.setBillingStatus("RELEASE_FAILED");
                mediaBilling.failed(task.getId(), "RELEASE_FAILED", message(error));
            }
        }
        setUpstreamStatus(task, "failed");
        task.setStatus("FAILED"); task.setErrorMessage(message); task.setDurationMs((int) elapsed);
        task.setCompletedAt(ImageTime.now());
        task.setUpdatedAt(ImageTime.now()); tasks.updateById(task);
    }

    private void pending(MediaTask task, VideoGateway.Task upstream) {
        setUpstreamStatus(task, upstream.status());
        task.setProgress(upstream.progress()); setTaskData(task, "lastPollError", null);
        task.setUpdatedAt(ImageTime.now()); tasks.updateById(task);
    }

    // 轮询异常:仅记录 lastPollError,任务保持 PENDING,等待下一轮重试
    private void retry(MediaTask task, Exception error) {
        setTaskData(task, "lastPollError", message(error));
        task.setUpdatedAt(ImageTime.now());
        tasks.updateById(task);
    }

    private void setUpstreamStatus(MediaTask task, String status) { setTaskData(task, "upstreamStatus", status); }

    private void setTaskData(MediaTask task, String key, Object value) {
        Map<String, Object> data = MediaTaskData.read(json, task.getTaskData());
        MediaTaskData.put(data, key, value);
        task.setTaskData(MediaTaskData.write(json, data));
    }

    private static String message(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
