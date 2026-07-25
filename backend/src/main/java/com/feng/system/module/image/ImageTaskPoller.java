package com.feng.system.module.image;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feng.system.module.image.entity.ApiProfile;
import com.feng.system.module.image.entity.GenerationJob;
import com.feng.system.module.image.mapper.ApiProfileMapper;
import com.feng.system.module.image.mapper.GenerationJobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ImageTaskPoller {
    private final GenerationJobMapper jobs;
    private final ApiProfileMapper profiles;
    private final Sub2apiBillingService billing;
    private final ImageModelConfigService modelConfigs;
    private final ImageGateway gateway;
    private final ImageStorageService storage;
    @Value("${image.poll-max-duration-ms:1800000}") private long maxDuration;
    @Value("${image.poll-lease-ms:60000}") private long leaseMillis;
    @Value("${image.poll-interval-ms:2000}") private long pollInterval;
    @Value("${image.charge-on-failure:false}") private boolean chargeOnFailure;

    @Scheduled(fixedDelayString = "${image.poll-interval-ms:2000}", initialDelayString = "${image.poll-interval-ms:2000}")
    public void poll() {
        LocalDateTime now = ImageTime.now();
        List<GenerationJob> due = jobs.selectList(new LambdaQueryWrapper<GenerationJob>()
                .eq(GenerationJob::getStatus, "PENDING").isNotNull(GenerationJob::getUpstreamTaskId)
                .and(q -> q.isNull(GenerationJob::getNextPollAt).or().le(GenerationJob::getNextPollAt, now))
                .and(q -> q.isNull(GenerationJob::getPollLeaseUntil).or().lt(GenerationJob::getPollLeaseUntil, now))
                .orderByAsc(GenerationJob::getNextPollAt).last("LIMIT 10"));
        for (GenerationJob job : due) if (claim(job.getId(), now)) process(job);
    }

    private boolean claim(String id, LocalDateTime now) {
        GenerationJob claim = new GenerationJob();
        claim.setId(id); claim.setPollLeaseUntil(now.plusNanos(leaseMillis * 1_000_000)); claim.setUpdatedAt(now);
        return jobs.update(claim, new LambdaQueryWrapper<GenerationJob>().eq(GenerationJob::getId, id)
                .eq(GenerationJob::getStatus, "PENDING")
                .and(q -> q.isNull(GenerationJob::getPollLeaseUntil).or().lt(GenerationJob::getPollLeaseUntil, now))) == 1;
    }

    private void process(GenerationJob job) {
        try {
            ApiProfile profile = profiles.selectById(job.getProfileId());
            if (profile == null || job.getBillingAccountId() == null || job.getModelConfigId() == null)
                throw new IllegalStateException("Image task profile, model, or billing account is missing");
            ImageModelConfigService.RuntimeModel source = modelConfigs.requireImage(job.getModelConfigId());
            ImageGateway.Task task = gateway.task(source.provider().getBaseUrl(), source.provider().getImageApiKey(),
                    source.model().getGenerationPath(), source.model().getEditPath(), job.getUpstreamOperation(), job.getUpstreamTaskId());
            job.setRawResponses(GenerationAuditJson.append(job.getRawResponses(), "poll", task.raw()));
            long elapsed = Duration.between(job.getCreatedAt(), ImageTime.now()).toMillis();
            switch (ImageTaskRules.decide(task.status(), elapsed, maxDuration)) {
                case COMPLETED -> complete(job, task, source, elapsed);
                case FAILED -> fail(job, task.error() == null ? "Image task failed." : task.error(), elapsed);
                case TIMEOUT -> fail(job, "Image task timed out.", elapsed);
                case PENDING -> pending(job, task);
            }
        } catch (Exception e) {
            retry(job, e);
        }
    }

    private void complete(GenerationJob job, ImageGateway.Task task, ImageModelConfigService.RuntimeModel source, long elapsed) {
        if (task.urls().isEmpty() && job.getCount() == 1) {
            if (!storage.exists(job.getId(), 0)) {
                ImageGateway.Download content = gateway.content(source.provider().getBaseUrl(), source.provider().getImageApiKey(),
                        source.model().getGenerationPath(), job.getUpstreamTaskId());
                job.setRawResponses(GenerationAuditJson.append(job.getRawResponses(), "content",
                        java.util.Map.of("mimeType", content.mimeType(), "body", content.bytes())));
                storage.save(job.getId(), 0, content.bytes(), content.mimeType());
            }
        } else {
            int index = 0;
            for (String url : task.urls()) {
                if (!storage.exists(job.getId(), index)) storage.saveUrl(job.getId(), index, url);
                index++;
            }
            if (index == 0) throw new IllegalStateException("Completed image task returned no downloadable images");
        }
        if ("RESERVED".equals(job.getBillingStatus())) {
            String usageId = billing.settle(job.getId(), job.getBillingApiKeyId(), job.getBillingUserId(), job.getBillingAccountId(),
                    job.getBillingAmount(), job.getCount(), job.getSize(), job.getUpstreamOperation(), (int) elapsed);
            job.setBillingStatus("CHARGED"); job.setBillingUsageLogId(usageId); job.setBillingSettledAt(ImageTime.now());
        }
        job.setStatus("SUCCEEDED"); job.setProgress(100); job.setUpstreamStatus("completed"); job.setDurationMs((int) elapsed);
        job.setCompletedAt(ImageTime.now()); job.setPollLeaseUntil(null); job.setNextPollAt(null); job.setBillingError(null); job.setUpdatedAt(ImageTime.now());
        jobs.updateById(job);
    }

    private void fail(GenerationJob job, String message, long elapsed) {
        if ("RESERVED".equals(job.getBillingStatus())) {
            try {
                if (chargeOnFailure) {
                    String usageId = billing.settle(job.getId(), job.getBillingApiKeyId(), job.getBillingUserId(), job.getBillingAccountId(),
                            job.getBillingAmount(), job.getCount(), job.getSize(), job.getUpstreamOperation(), (int) elapsed);
                    job.setBillingStatus("CHARGED"); job.setBillingUsageLogId(usageId); job.setBillingSettledAt(ImageTime.now());
                } else {
                    String result = billing.release(job.getId(), job.getBillingApiKeyId(), job.getBillingUserId(), job.getBillingAmount());
                    job.setBillingStatus("settled".equals(result) ? "CHARGED" : "RELEASED");
                }
            } catch (Exception e) {
                job.setBillingStatus(chargeOnFailure ? "CHARGE_FAILED" : "RELEASE_FAILED"); job.setBillingError(message(e));
            }
        }
        job.setStatus("FAILED"); job.setUpstreamStatus("failed"); job.setErrorMessage(message); job.setDurationMs((int) elapsed);
        job.setCompletedAt(ImageTime.now()); job.setPollLeaseUntil(null); job.setNextPollAt(null); job.setUpdatedAt(ImageTime.now());
        jobs.updateById(job);
    }

    private void pending(GenerationJob job, ImageGateway.Task task) {
        job.setProgress(task.progress()); job.setUpstreamStatus(task.status()); job.setPollErrorCount(0); job.setPollLeaseUntil(null);
        job.setNextPollAt(ImageTime.now().plusNanos(pollInterval * 1_000_000)); job.setUpdatedAt(ImageTime.now()); jobs.updateById(job);
    }

    private void retry(GenerationJob job, Exception error) {
        int errors = (job.getPollErrorCount() == null ? 0 : job.getPollErrorCount()) + 1;
        long backoff = Math.min(30_000, pollInterval * (1L << Math.min(4, Math.max(0, errors - 1))));
        job.setRawResponses(GenerationAuditJson.append(job.getRawResponses(), "poll_error", payload(error)));
        job.setPollErrorCount(errors); job.setPollLeaseUntil(null); job.setNextPollAt(ImageTime.now().plusNanos(backoff * 1_000_000));
        job.setBillingError(message(error)); job.setUpdatedAt(ImageTime.now()); jobs.updateById(job);
    }

    private static String message(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
    private static Object payload(Exception error) {
        return error instanceof ImageApiException api && api.getPayload() != null
                ? api.getPayload() : java.util.Map.of("message", message(error));
    }
}
