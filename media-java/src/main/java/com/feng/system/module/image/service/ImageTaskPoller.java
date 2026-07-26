package com.feng.system.module.image.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.image.support.ImageTime;
import com.feng.system.module.media.entity.MediaBillingRecord;
import com.feng.system.module.media.entity.MediaTask;
import com.feng.system.module.media.mapper.MediaTaskMapper;
import com.feng.system.module.media.service.MediaBillingRecordService;
import com.feng.system.module.media.service.MediaTaskData;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 图片任务轮询器:定时向上游查询处于 PENDING 状态的图片任务,
 * 根据上游状态完成落库保存、计费结算(成功扣费/失败释放冻结)、超时失败等状态流转。
 */
@Service
@RequiredArgsConstructor
public class ImageTaskPoller {
    private final MediaTaskMapper tasks;
    private final Sub2apiBillingService billing;
    private final MediaBillingRecordService mediaBilling;
    private final ImageModelConfigService modelConfigs;
    private final ImageGateway gateway;
    private final ImageStorageService storage;
    private final ObjectMapper json;
    @Value("${image.poll-max-duration-ms:1800000}") private long maxDuration = 1_800_000;
    @Value("${image.charge-on-failure:false}") private boolean chargeOnFailure;

    /**
     * 定时轮询(默认每 5 秒):按创建时间取最早的 10 条待处理图片任务逐个处理。
     */
    @Scheduled(fixedDelayString = "${image.poll-interval-ms:5000}", initialDelayString = "${image.poll-interval-ms:5000}")
    public void poll() {
        List<MediaTask> due = tasks.selectList(new LambdaQueryWrapper<MediaTask>()
                .eq(MediaTask::getTaskType, "IMAGE")
                .eq(MediaTask::getStatus, "PENDING")
                .isNotNull(MediaTask::getUpstreamTaskId)
                .orderByAsc(MediaTask::getCreatedAt).last("LIMIT 10"));
        for (MediaTask task : due) process(task);
    }

    /**
     * 处理单个任务:查询上游状态,依据规则决策进入完成/失败/超时/继续等待分支;
     * 处理过程中的异常仅记录到任务数据,留待下次轮询重试。
     */
    public void process(MediaTask task) {
        try {
            if (task.getModelConfigId() == null || task.getUpstreamTaskId() == null)
                throw new IllegalStateException("Image task model or upstream task is missing");
            ImageModelConfigService.RuntimeModel source = modelConfigs.requireImage(task.getModelConfigId());
            ImageGateway.Task upstream = gateway.task(source.provider().getBaseUrl(), source.provider().getImageApiKey(),
                    source.model().getGenerationPath(), task.getUpstreamTaskId());
            long elapsed = Duration.between(task.getCreatedAt(), ImageTime.now()).toMillis();
            switch (ImageTaskRules.decide(upstream.status(), elapsed, maxDuration)) {
                case COMPLETED -> complete(task, upstream, source, elapsed);
                case FAILED -> fail(task, upstream.error() == null ? "Image task failed." : upstream.error(), elapsed);
                case TIMEOUT -> fail(task, "Image task timed out.", elapsed);
                case PENDING -> pending(task, upstream);
            }
        } catch (Exception error) {
            retry(task, error);
        }
    }

    // 任务完成:下载并保存全部图片结果,再结算计费并更新任务为 SUCCEEDED
    private void complete(MediaTask task, ImageGateway.Task upstream,
                          ImageModelConfigService.RuntimeModel source, long elapsed) {
        Map<String, Object> data = MediaTaskData.read(json, task.getTaskData());
        int count = MediaTaskData.integer(data.get("count"), 1);
        String size = MediaTaskData.text(data.get("size"));
        if (upstream.urls().isEmpty() && count == 1) {
            // 上游未返回 URL 且只有单张图:走内容接口直接拉取图片字节
            if (!storage.exists(task.getId(), 0)) {
                ImageGateway.Download content = gateway.content(source.provider().getBaseUrl(), source.provider().getImageApiKey(),
                        source.model().getGenerationPath(), task.getUpstreamTaskId());
                storage.save(task.getId(), 0, content.bytes(), content.mimeType());
            }
        } else {
            int index = 0;
            for (String url : upstream.urls()) {
                // exists 判断保证幂等,重复轮询不会重复下载
                if (!storage.exists(task.getId(), index)) storage.saveUrl(task.getId(), index, url);
                index++;
            }
            if (index == 0) throw new IllegalStateException("Completed image task returned no downloadable images");
        }
        settleSuccess(task, count, size, elapsed);
        setUpstreamStatus(task, "completed");
        task.setStatus("SUCCEEDED");
        task.setProgress(100);
        task.setDurationMs((int) elapsed);
        task.setCompletedAt(ImageTime.now());
        task.setErrorMessage(null);
        task.setUpdatedAt(ImageTime.now());
        tasks.updateById(task);
    }

    // 成功结算:仅对仍处于 RESERVED(已冻结未扣费)状态的计费记录执行真正扣费
    private void settleSuccess(MediaTask task, int count, String size, long elapsed) {
        MediaBillingRecord record = mediaBilling.find(task.getId());
        if (record == null || !"RESERVED".equals(record.getDeductionStatus())) return;
        try {
            String usage = billing.settle(task.getId(), record.getApiKeyId(), record.getUserId(), record.getAccountId(),
                    record.getTaskFee(), count, size, (int) elapsed);
            mediaBilling.charged(task.getId(), usage);
            task.setBillingStatus("CHARGED");
            task.setBillingAmount(record.getTaskFee());
        } catch (Exception error) {
            mediaBilling.failed(task.getId(), "CHARGE_FAILED", message(error));
            task.setBillingStatus("CHARGE_FAILED");
        }
    }

    // 任务失败/超时:按配置决定失败是否仍然扣费,否则释放冻结金额,最后更新任务为 FAILED
    private void fail(MediaTask task, String message, long elapsed) {
        MediaBillingRecord record = mediaBilling.find(task.getId());
        if (record != null && "RESERVED".equals(record.getDeductionStatus())) {
            try {
                if (chargeOnFailure) {
                    // 失败也扣费模式:直接结算
                    String usage = billing.settle(task.getId(), record.getApiKeyId(), record.getUserId(), record.getAccountId(),
                            record.getTaskFee(), 1, MediaTaskData.text(MediaTaskData.read(json, task.getTaskData()).get("size")),
                            (int) elapsed);
                    mediaBilling.charged(task.getId(), usage);
                    task.setBillingStatus("CHARGED");
                } else {
                    // 默认模式:释放冻结余额;若发现已被结算过(幂等标记),则记为已扣费
                    String result = billing.release(task.getId(), record.getApiKeyId(), record.getUserId(), record.getTaskFee());
                    if ("settled".equals(result)) {
                        mediaBilling.charged(task.getId(), result);
                        task.setBillingStatus("CHARGED");
                    } else {
                        mediaBilling.released(task.getId());
                        task.setBillingStatus("RELEASED");
                    }
                }
            } catch (Exception billingError) {
                task.setBillingStatus(chargeOnFailure ? "CHARGE_FAILED" : "RELEASE_FAILED");
                mediaBilling.failed(task.getId(), task.getBillingStatus(), message(billingError));
            }
        }
        setUpstreamStatus(task, "failed");
        task.setStatus("FAILED");
        task.setErrorMessage(message);
        task.setDurationMs((int) elapsed);
        task.setCompletedAt(ImageTime.now());
        task.setUpdatedAt(ImageTime.now());
        tasks.updateById(task);
    }

    // 仍在处理中:同步上游状态与进度,并清除上次轮询错误
    private void pending(MediaTask task, ImageGateway.Task upstream) {
        setUpstreamStatus(task, upstream.status());
        task.setProgress(upstream.progress());
        setTaskData(task, "lastPollError", null);
        task.setUpdatedAt(ImageTime.now());
        tasks.updateById(task);
    }

    // 轮询出错:仅记录错误信息,任务保持 PENDING 等待下次轮询重试
    private void retry(MediaTask task, Exception error) {
        setTaskData(task, "lastPollError", message(error));
        task.setUpdatedAt(ImageTime.now());
        tasks.updateById(task);
    }

    private void setUpstreamStatus(MediaTask task, String status) {
        setTaskData(task, "upstreamStatus", status);
    }

    private void setTaskData(MediaTask task, String key, Object value) {
        Map<String, Object> data = MediaTaskData.read(json, task.getTaskData());
        MediaTaskData.put(data, key, value);
        task.setTaskData(MediaTaskData.write(json, data));
    }

    private static String message(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
