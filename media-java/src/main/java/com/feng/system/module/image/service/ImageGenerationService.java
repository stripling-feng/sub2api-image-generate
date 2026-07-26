package com.feng.system.module.image.service;

import com.feng.system.module.image.exception.ImageApiException;
import com.feng.system.module.image.dto.ImageGenerateRequest;
import com.feng.system.module.image.formatter.ImageGenerateRequestFormatters;
import com.feng.system.module.image.support.GenerationAuditJson;
import com.feng.system.module.image.support.ImageTime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.image.entity.ApiProfile;
import com.feng.system.module.media.entity.MediaTask;
import com.feng.system.module.media.mapper.MediaTaskMapper;
import com.feng.system.module.media.service.MediaBillingRecordService;
import com.feng.system.module.media.service.MediaTaskData;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 图片生成核心服务：负责校验并解析生成请求、创建任务记录、
 * 按模型的异步/后台模式分发到上游，并处理预扣费、结算与失败回滚等计费流转。
 */
@Service
@RequiredArgsConstructor
public class ImageGenerationService {
    private final MediaTaskMapper jobs;
    private final ImageGateway gateway;
    private final ImageGenerationWorker worker;
    private final Sub2apiBillingService billing;
    private final MediaBillingRecordService mediaBilling;
    private final ImageModelConfigService modelConfigs;
    private final ObjectMapper json;
    private final ImageGenerateRequestFormatters requestFormatters = new ImageGenerateRequestFormatters();
    @Value("${image.charge-on-failure:false}") private boolean chargeOnFailure;

    /**
     * 受理一次图片生成请求：校验参数后按数量拆分为多个子任务提交。
     * 异步模式下同步提交上游任务并预扣费；后台模式下交由 Worker 线程异步执行。
     *
     * @param profile      调用方 API 密钥档案
     * @param uploads      旧文件上传参数，生成链路不再使用
     * @param uploadedMask 旧蒙版上传参数，生成链路不再使用
     * @return 本批请求的 requestId 与任务数量
     */
    public Accepted generate(ApiProfile profile, ImageGenerateRequest request, List<ImageGateway.Upload> uploads, ImageGateway.Upload uploadedMask) {
        Map<String, Object> raw = request == null ? Map.of() : request.clientPayload();
        GenerationAuditJson.rejectBase64(raw);
        String modelKey = request == null ? null : text(request.getModel());
        if (modelKey == null) throw new ImageApiException(422, "Invalid request.");
        Input input = parse(request, uploads, uploadedMask, modelConfigs.requireImage(modelKey));
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        // asyncMode=1：逐张同步提交上游异步任务；否则走后台线程整批执行
        if (Integer.valueOf(1).equals(input.runtime.model().getAsyncMode())) {
            for (int index = 1; index <= input.count; index++) {
                try { createAsync(profile, input, raw, requestId, index, input.count); }
                catch (Exception ignored) { /* failed child is already persisted; continue the batch */ }
            }
        }
        else createBackground(profile, input, raw, requestId);
        return new Accepted(requestId, input.count);
    }

    // 异步模式：先预扣费（reserve），再向上游提交任务；提交成功记录任务 ID，失败则回滚计费
    private void createAsync(ApiProfile profile, Input input, Map<String, Object> raw, String requestId, int index, int total) {
        MediaTask job = newJob(profile, input, raw, requestId, index, total, 1);
        mediaBilling.begin(job, input.runtime.model().getUnitPriceUsd(), true);
        Sub2apiBillingService.Reservation reservation = null;
        try {
            // 预扣费：先冻结额度，任务状态置为 RESERVED
            reservation = billing.reserve(profile.getEncryptedKey(), 1, input.runtime.model().getUnitPriceUsd());
            mediaBilling.reserved(job.getId(), reservation);
            job.setBillingStatus("RESERVED");
            job.setBillingAmount(reservation.amountUsd());
            // 配置为失败也计费时，提交前即直接结算（CHARGED）
            if (chargeOnFailure) {
                String usageId = billing.settle(job.getId(), reservation.apiKeyId(), reservation.userId(), reservation.accountId(),
                        reservation.amountUsd(), 1, input.size, null);
                mediaBilling.charged(job.getId(), usageId);
                job.setBillingStatus("CHARGED");
            }
            Map<String, Object> body = upstreamBody(input);
            job.setUpstreamRequest(GenerationAuditJson.stringify(auditUpstream(body, input)));
            jobs.updateById(job);
            ImageGateway.GatewayResponse response = gateway.create(input.runtime.provider().getBaseUrl(), input.runtime.provider().getImageApiKey(),
                    input.runtime.model().getGenerationPath(), body);
            job.setUpstreamResponse(GenerationAuditJson.stringify(response.payload()));
            ImageGateway.Task task = gateway.parseTask(response.payload());
            if (task.id() == null) throw new ImageApiException(502, "Upstream image task response is missing id.", null, response.payload());
            job.setUpstreamTaskId(task.id()); job.setUpstreamOperation("generations");
            setTaskData(job, "upstreamStatus", task.status() == null ? "queued" : task.status());
            job.setProgress(task.progress());
            job.setUpdatedAt(ImageTime.now()); jobs.updateById(job);
        } catch (Exception e) {
            // 保留失败现场：若尚未记录上游响应，则把异常负载写入审计字段
            if (job.getUpstreamResponse() == null || "{}".equals(job.getUpstreamResponse()) || "[]".equals(job.getUpstreamResponse()))
                job.setUpstreamResponse(GenerationAuditJson.stringify(payload(e)));
            failReservation(job, reservation, input, e);
            throw e;
        }
    }

    // 后台模式：不预扣费，任务落库后交给 Worker 异步线程同步调用上游并保存结果
    private void createBackground(ApiProfile profile, Input input, Map<String, Object> raw, String requestId) {
        for (int i = 1; i <= input.count; i++) {
            MediaTask job = newJob(profile, input, raw, requestId, i, input.count, 1);
            mediaBilling.begin(job, BigDecimal.ZERO, false);
            Map<String, Object> body = upstreamBody(input);
            job.setUpstreamRequest(GenerationAuditJson.stringify(auditUpstream(body, input)));
            jobs.updateById(job);
            worker.run(job.getId(), input.runtime.provider().getBaseUrl(), input.runtime.provider().getImageApiKey(),
                    input.runtime.model().getGenerationPath(), body);
        }
    }

    // 创建失败时的计费收尾：按配置决定失败结算（CHARGED）或释放预扣（RELEASED），并把任务标记为 FAILED
    private void failReservation(MediaTask job, Sub2apiBillingService.Reservation reservation, Input input, Exception failure) {
        String billingStatus = reservation == null ? "FAILED" : job.getBillingStatus();
        try {
            if (reservation != null && !"CHARGED".equals(job.getBillingStatus())) {
                if (chargeOnFailure) {
                    String usage = billing.settle(job.getId(), reservation.apiKeyId(), reservation.userId(), reservation.accountId(), reservation.amountUsd(),
                            1, input.size, null);
                    mediaBilling.charged(job.getId(), usage); billingStatus = "CHARGED";
                } else {
                    String result = billing.release(job.getId(), reservation.apiKeyId(), reservation.userId(), reservation.amountUsd());
                    // 释放接口可能返回 "settled"（上游已实际结算），此时按已计费处理
                    if ("settled".equals(result)) {
                        mediaBilling.charged(job.getId(), result); billingStatus = "CHARGED";
                    } else {
                        mediaBilling.released(job.getId()); billingStatus = "RELEASED";
                    }
                }
            }
        } catch (Exception billingFailure) {
            billingStatus = chargeOnFailure ? "CHARGE_FAILED" : "RELEASE_FAILED";
            mediaBilling.failed(job.getId(), billingStatus, message(billingFailure));
        }
        if (reservation == null) {
            mediaBilling.failed(job.getId(), "FAILED", message(failure));
        }
        setTaskData(job, "upstreamStatus", "create_failed");
        job.setStatus("FAILED"); job.setErrorMessage(message(failure)); job.setBillingStatus(billingStatus);
        job.setCompletedAt(ImageTime.now()); job.setUpdatedAt(ImageTime.now()); jobs.updateById(job);
    }

    // 构建并初始化一条 PENDING 状态的图片任务记录（含参数快照与客户端请求审计）
    private MediaTask newJob(ApiProfile profile, Input input, Map<String, Object> raw, String requestId, int index, int total, int count) {
        MediaTask job = new MediaTask();
        job.setModelConfigId(input.runtime.model().getId());
        job.setId(id()); job.setApiKey(profile.getEncryptedKey()); job.setTaskType("IMAGE"); job.setRequestId(requestId);
        Map<String, Object> params = new LinkedHashMap<>(input.requestParams);
        params.put("request_id", requestId); params.put("request_index", index); params.put("request_total", total);
        params.put("reference_image_count", input.referenceCount());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("prompt", input.prompt); data.put("negativePrompt", null); data.put("model", input.model);
        data.put("size", input.size); data.put("quality", input.quality); data.put("style", null); data.put("count", count);
        data.put("responseFormat", "url"); data.put("outputFormat", null); data.put("params", params);
        data.put("requestIndex", index); data.put("requestTotal", total); data.put("referenceImageCount", input.referenceCount());
        data.put("hasMask", false); data.put("images", input.imageUrls);
        job.setTaskData(MediaTaskData.write(json, data));
        job.setUserRequest(GenerationAuditJson.stringify(auditClient(raw, input))); job.setSystemResponse("{}");
        job.setUpstreamRequest("{}"); job.setUpstreamResponse("{}"); job.setStatus("PENDING"); job.setProgress(0);
        job.setBillingStatus(null); job.setCreatedAt(ImageTime.now()); job.setUpdatedAt(ImageTime.now());
        return job;
    }

    // 校验并规整请求：数量上限、参考图格式/大小、蒙版规则（PNG 且尺寸与首图一致），再按模型 schema 合并参数
    private Input parse(ImageGenerateRequest request, List<ImageGateway.Upload> uploaded, ImageGateway.Upload uploadedMask,
                        ImageModelConfigService.RuntimeModel runtime) {
        String prompt = text(request.getPrompt()); String model = runtime.model().getModelKey();
        int count = number(request.getCount(), 1);
        if (prompt == null || !Boolean.TRUE.equals(request.getAsync()) || count < 1 || count > runtime.model().getMaxCount())
            throw new ImageApiException(422, "Invalid request.");
        validateModelParameters(model, request);
        List<ImageGateway.Upload> images = new ArrayList<>(uploaded);
        if (uploadedMask != null) throw new ImageApiException(422, "Invalid request.");
        List<String> submittedImageUrls = request.getImages();
        List<String> imageUrls = submittedImageUrls == null ? List.of() : submittedImageUrls.stream()
                .filter(url -> url != null && !url.isBlank()).toList();
        if (!images.isEmpty() || images.size() + imageUrls.size() > runtime.model().getMaxReferenceImages())
            throw new ImageApiException(422, "Unsupported or invalid image.");
        int referenceCount = images.size() + imageUrls.size();
        ImageGenerateRequest upstream = new ImageGenerateRequest();
        upstream.setModel(model);
        upstream.setPrompt(prompt);
        upstream.setAsync(request.getAsync());
        upstream.setCount(request.getCount());
        upstream.setSize(text(request.getSize()));
        upstream.setAspectRatio(text(request.getAspectRatio()));
        upstream.setQuality(text(request.getQuality()));
        upstream.setImages(imageUrls);
        Map<String, Object> requestParams = requestFormatters.format(model, upstream);
        GenerationAuditJson.rejectBase64(requestParams);
        String size = value(requestParams.get("size"), value(requestParams.get("aspect_ratio"), "auto"));
        return new Input(prompt, model, size, text(requestParams.get("quality")), count, requestParams, images, imageUrls, referenceCount, runtime);
    }

    private void validateModelParameters(String model, ImageGenerateRequest request) {
        String size = text(request.getSize());
        String aspectRatio = text(request.getAspectRatio());
        if ("gpt-image-2".equals(model) && size == null) throw new ImageApiException(422, "Invalid request.");
        if (Set.of("gpt-image-2-1k", "gpt-image-2-2k", "gpt-image-2-4k").contains(model)
                && size == null && aspectRatio == null) throw new ImageApiException(422, "Invalid request.");
    }
    private String stringify(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new ImageApiException(422, "Invalid request."); } }
    private void setTaskData(MediaTask task, String key, Object value) {
        Map<String, Object> data = MediaTaskData.read(json, task.getTaskData());
        MediaTaskData.put(data, key, value);
        task.setTaskData(MediaTaskData.write(json, data));
    }
    // 构建客户端请求审计快照：文件仅记录名称/类型/大小，不落库二进制内容
    private Map<String, Object> auditClient(Map<String, Object> raw, Input input) {
        Map<String, Object> value = new LinkedHashMap<>(raw);
        if (!input.images.isEmpty()) value.put("uploadedImages", input.images.stream().map(ImageGenerationService::file).toList());
        if (!input.imageUrls.isEmpty()) value.put("images", input.imageUrls);
        return value;
    }
    private Map<String, Object> auditUpstream(Map<String, Object> body, Input input) {
        Map<String, Object> value = new LinkedHashMap<>(body);
        if (!input.images.isEmpty()) {
            value.put("images", input.images.stream().map(ImageGenerationService::file).toList());
        }
        return value;
    }
    private Map<String, Object> upstreamBody(Input input) {
        Map<String, Object> body = new LinkedHashMap<>(input.requestParams);
        body.put("n", 1);
        return body;
    }
    private static Map<String, Object> file(ImageGateway.Upload value) {
        return Map.of("name", value.name(), "mimeType", value.mimeType(), "sizeBytes", value.bytes().length);
    }
    private static Object payload(Exception error) {
        return error instanceof ImageApiException api && api.getPayload() != null
                ? api.getPayload() : Map.of("message", message(error));
    }
    private static String text(Object value) { return value instanceof String text && !text.isBlank() ? text : null; }
    private static String value(Object value, String fallback) { String text = text(value); return text == null ? fallback : text; }
    private static int number(Object value, int fallback) { return value instanceof Number n ? n.intValue() : fallback; }
    private static String id() { return UUID.randomUUID().toString().replace("-", ""); }
    private static String message(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }

    private record Input(String prompt, String model, String size, String quality, int count,
                         Map<String, Object> requestParams, List<ImageGateway.Upload> images, List<String> imageUrls, int referenceCount,
                         ImageModelConfigService.RuntimeModel runtime) {
        boolean hasReferenceInputs() { return !images.isEmpty() || !imageUrls.isEmpty(); }
    }
    /** 请求受理结果：批次 requestId 及创建的任务数量。 */
    public record Accepted(String requestId, int count) {}
}
