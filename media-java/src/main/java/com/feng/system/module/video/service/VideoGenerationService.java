package com.feng.system.module.video.service;

import com.feng.system.module.image.exception.ImageApiException;
import com.feng.system.module.image.service.ImageGateway;
import com.feng.system.module.image.service.ImageModelConfigService;
import com.feng.system.module.image.service.Sub2apiBillingService;
import com.feng.system.module.image.support.GenerationAuditJson;
import com.feng.system.module.image.support.ImageTime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.image.entity.ApiProfile;
import com.feng.system.module.media.entity.MediaTask;
import com.feng.system.module.media.mapper.MediaTaskMapper;
import com.feng.system.module.media.service.MediaBillingRecordService;
import com.feng.system.module.media.service.MediaTaskData;
import com.feng.system.module.video.dto.VideoGenerationInput;
import com.feng.system.module.video.dto.VideoGenerationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 视频生成核心服务:校验请求与上传素材、按数量拆分创建 MediaTask 任务、
 * 预扣计费额度、组装并发送上游生成请求;失败时负责释放/结算已预扣的费用。
 */
@Service
@RequiredArgsConstructor
public class VideoGenerationService {
    private final MediaTaskMapper jobs;
    private final VideoGateway gateway;
    private final Sub2apiBillingService billing;
    private final MediaBillingRecordService mediaBilling;
    private final ImageModelConfigService modelConfigs;
    private final VideoMaterialUploadService materialUploads;
    private final ObjectMapper json;

    /**
     * 受理视频生成请求:校验模型与参数后,按 count 拆分为多个任务逐个创建,
     * 返回本批次的 requestId 与任务数量。
     * @param images 直接上传的参考图片
     * @param firstFrame 首帧图上传文件(可为 null)
     * @param lastFrame 尾帧图上传文件(可为 null)
     */
    public Accepted generate(ApiProfile profile, VideoGenerationRequest request, List<ImageGateway.Upload> images,
                             ImageGateway.Upload firstFrame, ImageGateway.Upload lastFrame) {
        Map<String, Object> raw = raw(request);
        // 拒绝内嵌 base64 数据,素材必须走 URL 或上传接口
        GenerationAuditJson.rejectBase64(raw);
        String modelKey = request.getModel();
        if (modelKey == null) throw new ImageApiException(422, "Invalid video generation request.");
        ImageModelConfigService.RuntimeModel runtime = modelConfigs.requireVideo(modelKey);
        VideoGenerationInput input = request.toInput(runtime, images, firstFrame, lastFrame);
        validateUploads(input);
        String requestId = id().substring(0, 16);
        for (int index = 1; index <= input.count(); index++) create(profile, input, raw, requestId, index);
        return new Accepted(requestId, input.count());
    }

    // 创建单个任务:预扣费 -> 组装并发送上游请求 -> 记录上游任务 ID;任一步失败则释放费用并把任务置为 FAILED
    private void create(ApiProfile profile, VideoGenerationInput input, Map<String, Object> raw, String requestId, int index) {
        MediaTask job = job(profile, input, raw, requestId, index);
        // 按计费模式(按秒/按次)计算本任务应扣金额
        BigDecimal amount = VideoTaskRules.charge(input.runtime().model().getBillingMode(), input.duration(),
                input.runtime().model().getUnitPriceUsd());
        mediaBilling.begin(job, amount, true);
        Sub2apiBillingService.Reservation reservation = null;
        try {
            // 先向计费系统预留额度,成功后才调用上游
            reservation = billing.reserveAmount(profile.getEncryptedKey(), amount);
            mediaBilling.reserved(job.getId(), reservation);
            job.setBillingStatus("RESERVED");
            job.setBillingAmount(reservation.amountUsd());
            UpstreamRequest request = request(input);
            Map<String, Object> upstream = new LinkedHashMap<>(request.body());
            if (!request.inputReferences().isEmpty()) upstream.put("input_reference", request.inputReferences());
            setTaskData(job, "normalizedRequest", upstream);
            setTaskData(job, "inputReferences", request.inputReferences());
            job.setUpstreamRequest(GenerationAuditJson.stringify(upstream));
            jobs.updateById(job);
            VideoGateway.Task task = request.inputReferences().isEmpty()
                    ? gateway.create(input.runtime().provider().getBaseUrl(), input.runtime().provider().getVideoApiKey(),
                    input.runtime().model().getGenerationPath(), request.body())
                    : gateway.create(input.runtime().provider().getBaseUrl(), input.runtime().provider().getVideoApiKey(),
                    input.runtime().model().getGenerationPath(), request.body(), request.inputReferences());
            job.setUpstreamResponse(GenerationAuditJson.stringify(task.raw()));
            if (task.id() == null) throw new ImageApiException(502, "Upstream video task response is missing id.", null, task.raw());
            job.setUpstreamTaskId(task.id()); setTaskData(job, "upstreamStatus", task.status()); job.setProgress(task.progress());
            job.setUpdatedAt(ImageTime.now()); jobs.updateById(job);
        } catch (Exception failure) {
            if (job.getUpstreamResponse() == null || "{}".equals(job.getUpstreamResponse()) || "[]".equals(job.getUpstreamResponse()))
                job.setUpstreamResponse(GenerationAuditJson.stringify(payload(failure)));
            if (reservation != null) {
                // 已预扣费:尝试释放;若计费方判定为已结算(settled)则记为 CHARGED
                try {
                    String result = billing.releaseVideo(job.getId(), reservation.apiKeyId(), reservation.userId(), reservation.amountUsd());
                    if ("settled".equals(result)) {
                        mediaBilling.charged(job.getId(), result); job.setBillingStatus("CHARGED");
                    } else {
                        mediaBilling.released(job.getId()); job.setBillingStatus("RELEASED");
                    }
                } catch (Exception releaseFailure) {
                    job.setBillingStatus("RELEASE_FAILED");
                    mediaBilling.failed(job.getId(), "RELEASE_FAILED", message(releaseFailure));
                }
            } else {
                job.setBillingStatus("FAILED");
                mediaBilling.failed(job.getId(), "FAILED", message(failure));
            }
            setTaskData(job, "upstreamStatus", "create_failed");
            job.setStatus("FAILED"); job.setErrorMessage(message(failure));
            job.setCompletedAt(ImageTime.now()); job.setUpdatedAt(ImageTime.now()); jobs.updateById(job);
        }
    }

    // 校验上传图片:大小上限按模型区分(omni-fast 5MB、omni-v2v 8MB、其余 30MB)
    private void validateUploads(VideoGenerationInput input) {
        long maxImageBytes = input.modelKey().startsWith("omni-fast") ? 5L * 1024 * 1024
                : input.modelKey().startsWith("omni-v2v") ? 8L * 1024 * 1024 : 30L * 1024 * 1024;
        for (ImageGateway.Upload image : input.images()) validateImage(image, maxImageBytes);
        if (input.firstFrame() != null) validateImage(input.firstFrame(), maxImageBytes);
        if (input.lastFrame() != null) validateImage(input.lastFrame(), maxImageBytes);
    }

    // 按不同模型组装上游请求体(各上游的字段命名与结构不同)
    private UpstreamRequest request(VideoGenerationInput input) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", input.runtime().model().getUpstreamModel()); body.put("prompt", input.prompt());
        body.put("aspect_ratio", input.aspectRatio());
        String model = input.runtime().model().getModelKey();
        List<String> images = imageUrls(input);
        String firstFrame = frameUrl(input.firstFrameUrl(), input.firstFrame());
        String lastFrame = frameUrl(input.lastFrameUrl(), input.lastFrame());
        if (model.startsWith("omni-fast")) {
            // omni-fast:单图走 image_url;多图改用 multipart 的 input_reference 逐个上传
            if (images.size() == 1) body.put("image_url", images.get(0));
            if (firstFrame != null) body.put("first_image_url", firstFrame);
            if (lastFrame != null) body.put("last_image_url", lastFrame);
            return new UpstreamRequest(body, images.size() > 1 ? images : List.of());
        }
        if (model.startsWith("omni-v2v")) {
            if (!input.referenceVideoUrls().isEmpty()) body.put("reference_videos", input.referenceVideoUrls());
            if (!images.isEmpty()) body.put("reference_image_urls", images);
            return new UpstreamRequest(body, List.of());
        }
        body.put("resolution", input.resolution());
        if (model.startsWith("seedance-")) {
            // seedance:首图作为 image_url,其余作为 reference_image_urls,支持参考视频/音频与首尾帧
            body.put("duration", input.duration()); body.put("audio", input.generateAudio());
            if (!images.isEmpty()) {
                body.put("image_url", images.get(0));
                if (images.size() > 1) body.put("reference_image_urls", images.subList(1, images.size()));
            }
            if (!input.referenceVideoUrls().isEmpty()) body.put("reference_videos", input.referenceVideoUrls());
            if (!input.referenceAudioUrls().isEmpty()) body.put("reference_audios", input.referenceAudioUrls());
            if (firstFrame != null) body.put("first_image_url", firstFrame);
            if (lastFrame != null) body.put("last_image_url", lastFrame);
        } else {
            if ("grok-video-1.5".equals(model)) {
                // grok-video-1.5:duration 为数字,单张图以 image.url 对象形式传递
                body.put("duration", input.duration());
                body.put("image", Map.of("url", images.get(0)));
            } else {
                // grok-video:时长字段为字符串 seconds,多图走 image_urls
                body.put("seconds", String.valueOf(input.duration()));
                if (!images.isEmpty()) body.put("image_urls", images);
            }
            if (!input.referenceVideoUrls().isEmpty()) body.put("video_url", input.referenceVideoUrls().get(0));
        }
        return new UpstreamRequest(body, List.of());
    }

    // 汇总参考图 URL:请求中给的 URL + 上传文件转存后的公网 URL
    private List<String> imageUrls(VideoGenerationInput input) {
        List<String> urls = new ArrayList<>(input.referenceImageUrls());
        urls.addAll(input.images().stream().map(image -> materialUploads.uploadImage(image).url()).toList());
        return urls;
    }

    private String frameUrl(String url, ImageGateway.Upload upload) {
        if (url != null) return url;
        return upload == null ? null : materialUploads.uploadImage(upload).url();
    }

    // 构建 MediaTask 初始记录:写入任务参数快照与审计用的客户端原始请求
    private MediaTask job(ApiProfile profile, VideoGenerationInput input, Map<String, Object> raw, String requestId, int index) {
        MediaTask job = new MediaTask();
        job.setId(id()); job.setApiKey(profile.getEncryptedKey()); job.setTaskType("VIDEO");
        job.setModelConfigId(input.runtime().model().getId()); job.setRequestId(requestId);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("requestIndex", index); params.put("requestTotal", input.count());
        params.put("referenceImageCount", input.imageCount()); params.put("referenceVideoUrls", input.referenceVideoUrls());
        params.put("referenceAudioUrls", input.referenceAudioUrls()); params.put("hasFrames", input.hasFirstFrame());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("prompt", input.prompt()); data.put("model", input.runtime().model().getModelKey());
        data.put("duration", input.duration()); data.put("aspectRatio", input.aspectRatio());
        data.put("resolution", input.resolution()); data.put("generateAudio", input.generateAudio());
        data.put("count", input.count()); data.put("params", params); data.put("requestIndex", index);
        data.put("requestTotal", input.count()); data.put("referenceImageCount", input.imageCount());
        data.put("referenceImageUrls", input.referenceImageUrls()); data.put("referenceVideoUrls", input.referenceVideoUrls());
        data.put("referenceAudioUrls", input.referenceAudioUrls()); data.put("hasFrames", input.hasFirstFrame());
        job.setTaskData(stringify(data));
        job.setUserRequest(GenerationAuditJson.stringify(auditClient(raw, input))); job.setSystemResponse("{}");
        job.setUpstreamRequest("{}"); job.setUpstreamResponse("{}"); job.setStatus("PENDING");
        job.setProgress(0); job.setCreatedAt(ImageTime.now()); job.setUpdatedAt(ImageTime.now());
        return job;
    }

    // 校验单张图片:非空、不超限、MIME 仅限 png/jpeg/webp,且文件头签名需与格式一致
    private void validateImage(ImageGateway.Upload image, long maxBytes) {
        byte[] bytes = image.bytes();
        if (bytes.length == 0 || bytes.length > maxBytes || !(image.mimeType().equals("image/png")
                || image.mimeType().equals("image/jpeg") || image.mimeType().equals("image/webp")) || !imageSignature(bytes))
            throw new ImageApiException(422, "Unsupported or invalid reference image.");
    }
    // 通过文件头魔数识别 PNG/JPEG/WebP,防止伪造 MIME 类型
    private boolean imageSignature(byte[] bytes) {
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return true;
        if (bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff) return true;
        return bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }
    private String stringify(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new ImageApiException(422, "Invalid video generation request."); }
    }
    private void setTaskData(MediaTask task, String key, Object value) {
        Map<String, Object> data = MediaTaskData.read(json, task.getTaskData());
        MediaTaskData.put(data, key, value);
        task.setTaskData(MediaTaskData.write(json, data));
    }
    private Map<String, Object> auditClient(Map<String, Object> raw, VideoGenerationInput input) {
        Map<String, Object> value = new LinkedHashMap<>(raw);
        if (!input.images().isEmpty()) value.put("uploadedImages", input.images().stream().map(VideoGenerationService::file).toList());
        if (!input.referenceImageUrls().isEmpty()) value.put("referenceImageUrls", input.referenceImageUrls());
        if (input.firstFrame() != null) value.put("firstFrame", file(input.firstFrame()));
        if (input.firstFrameUrl() != null) value.put("firstFrameUrl", input.firstFrameUrl());
        if (input.lastFrame() != null) value.put("lastFrame", file(input.lastFrame()));
        if (input.lastFrameUrl() != null) value.put("lastFrameUrl", input.lastFrameUrl());
        return value;
    }
    private Map<String, Object> raw(VideoGenerationRequest request) {
        return json.convertValue(request, new TypeReference<>() {});
    }
    private static Map<String, Object> file(ImageGateway.Upload value) {
        return Map.of("name", value.name(), "mimeType", value.mimeType(), "sizeBytes", value.bytes().length);
    }
    private static String id() { return UUID.randomUUID().toString().replace("-", ""); }
    private static String message(Exception error) { return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }
    private static Object payload(Exception error) {
        return error instanceof ImageApiException api && api.getPayload() != null
                ? api.getPayload() : Map.of("message", message(error));
    }

    /** 上游请求封装:JSON 请求体 + 需要以 multipart 方式追加的 input_reference 列表。 */
    private record UpstreamRequest(Map<String, Object> body, List<String> inputReferences) {}
    /** 受理结果:本批次的 requestId 与创建的任务数量。 */
    public record Accepted(String requestId, int count) {}
}
