package com.feng.system.module.video.service;

import com.feng.system.module.image.exception.ImageApiException;
import com.feng.system.module.image.service.ImageGateway;
import com.feng.system.module.image.service.ImageModelConfigService;
import com.feng.system.module.image.service.Sub2apiBillingService;
import com.feng.system.module.image.support.GenerationAuditJson;
import com.feng.system.module.image.support.ImageTime;
import com.feng.system.module.image.support.SafeUpstreamUrl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.image.entity.ApiProfile;
import com.feng.system.module.video.entity.VideoGenerationJob;
import com.feng.system.module.video.mapper.VideoGenerationJobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class VideoGenerationService {
    private final VideoGenerationJobMapper jobs;
    private final VideoGateway gateway;
    private final Sub2apiBillingService billing;
    private final ImageModelConfigService modelConfigs;
    private final VideoMaterialUploadService materialUploads;
    private final ObjectMapper json;

    public Accepted generate(ApiProfile profile, Map<String, Object> raw, List<ImageGateway.Upload> images,
                             ImageGateway.Upload firstFrame, ImageGateway.Upload lastFrame) {
        GenerationAuditJson.rejectBase64(raw);
        String modelKey = text(raw.get("model"));
        if (modelKey == null) throw new ImageApiException(422, "Invalid video generation request.");
        ImageModelConfigService.RuntimeModel runtime = modelConfigs.requireVideo(modelKey);
        Input input = parse(raw, images, firstFrame, lastFrame, runtime);
        String requestId = id().substring(0, 16);
        for (int index = 1; index <= input.count; index++) create(profile, input, raw, requestId, index);
        return new Accepted(requestId, input.count);
    }

    private void create(ApiProfile profile, Input input, Map<String, Object> raw, String requestId, int index) {
        VideoGenerationJob job = job(profile, input, raw, requestId, index);
        jobs.insert(job);
        Sub2apiBillingService.Reservation reservation = null;
        try {
            BigDecimal amount = VideoTaskRules.charge(input.runtime.model().getBillingMode(), input.duration,
                    input.runtime.model().getUnitPriceUsd());
            reservation = billing.reserveAmount(profile.getEncryptedKey(), amount);
            job.setBillingStatus("RESERVED"); job.setBillingAmount(reservation.amountUsd());
            job.setBillingApiKeyId(reservation.apiKeyId()); job.setBillingUserId(reservation.userId());
            job.setBillingAccountId(reservation.accountId()); job.setBillingReservedAt(ImageTime.now());
            jobs.updateById(job);
            UpstreamRequest request = request(input);
            Map<String, Object> upstream = new LinkedHashMap<>(request.body);
            if (!request.inputReferences.isEmpty()) upstream.put("input_reference", request.inputReferences);
            job.setRawRequest(GenerationAuditJson.withUpstream(job.getRawRequest(), upstream));
            jobs.updateById(job);
            VideoGateway.Task task = request.inputReferences.isEmpty()
                    ? gateway.create(input.runtime.provider().getBaseUrl(), input.runtime.provider().getVideoApiKey(),
                    input.runtime.model().getGenerationPath(), request.body)
                    : gateway.create(input.runtime.provider().getBaseUrl(), input.runtime.provider().getVideoApiKey(),
                    input.runtime.model().getGenerationPath(), request.body, request.inputReferences);
            job.setRawResponses(GenerationAuditJson.append(job.getRawResponses(), "create", task.raw()));
            if (task.id() == null) throw new ImageApiException(502, "Upstream video task response is missing id.", null, task.raw());
            job.setUpstreamTaskId(task.id()); job.setUpstreamStatus(task.status()); job.setProgress(task.progress());
            job.setNextPollAt(ImageTime.now()); job.setUpdatedAt(ImageTime.now()); jobs.updateById(job);
        } catch (Exception failure) {
            if (job.getRawResponses() == null || "[]".equals(job.getRawResponses()))
                job.setRawResponses(GenerationAuditJson.append(job.getRawResponses(), "create_error", payload(failure)));
            if (reservation != null) {
                try {
                    billing.releaseVideo(job.getId(), reservation.apiKeyId(), reservation.userId(), reservation.amountUsd());
                    job.setBillingStatus("RELEASED");
                } catch (Exception releaseFailure) {
                    job.setBillingStatus("RELEASE_FAILED"); job.setBillingError(message(releaseFailure));
                }
            } else job.setBillingStatus("FAILED");
            job.setStatus("FAILED"); job.setUpstreamStatus("create_failed"); job.setErrorMessage(message(failure));
            job.setCompletedAt(ImageTime.now()); job.setUpdatedAt(ImageTime.now()); jobs.updateById(job);
        }
    }

    private Input parse(Map<String, Object> raw, List<ImageGateway.Upload> images, ImageGateway.Upload firstFrame,
                        ImageGateway.Upload lastFrame, ImageModelConfigService.RuntimeModel runtime) {
        String prompt = text(raw.get("prompt"));
        boolean omni = runtime.model().getModelKey().startsWith("omni-");
        int count = number(raw.get("count"), 1); int duration = number(raw.get("duration"), omni ? 10 : 8);
        String ratio = value(raw.get("aspectRatio"), "16:9"); String resolution = value(raw.get("resolution"), "720p");
        boolean audio = bool(raw.get("generateAudio"), runtime.model().getModelKey().startsWith("seedance-"));
        List<String> videoUrls = urls(raw.get("referenceVideoUrls"));
        List<String> audioUrls = urls(raw.get("referenceAudioUrls"));
        if (prompt == null || prompt.length() > 5000 || count < 1 || count > 4) throw new ImageApiException(422, "Invalid video generation request.");
        if (omni && audio) throw new ImageApiException(422, "Invalid video generation parameters.");
        long maxImageBytes = runtime.model().getModelKey().startsWith("omni-fast") ? 5L * 1024 * 1024
                : runtime.model().getModelKey().startsWith("omni-v2v") ? 8L * 1024 * 1024 : 30L * 1024 * 1024;
        for (ImageGateway.Upload image : images) validateImage(image, maxImageBytes);
        if (firstFrame != null) validateImage(firstFrame, maxImageBytes);
        if (lastFrame != null) validateImage(lastFrame, maxImageBytes);
        VideoTaskRules.validate(runtime.model().getModelKey(), duration, ratio, resolution, images.size(), videoUrls.size(),
                audioUrls.size(), firstFrame != null, lastFrame != null);
        return new Input(prompt, count, duration, ratio, resolution, audio, images, videoUrls, audioUrls,
                firstFrame, lastFrame, runtime);
    }

    private UpstreamRequest request(Input input) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", input.runtime.model().getUpstreamModel()); body.put("prompt", input.prompt);
        body.put("aspect_ratio", input.ratio);
        String model = input.runtime.model().getModelKey();
        if (model.startsWith("omni-fast")) {
            List<String> images = input.images.stream().map(image -> materialUploads.uploadImage(image).url()).toList();
            if (images.size() == 1) body.put("image_url", images.get(0));
            if (input.firstFrame != null) body.put("first_image_url", materialUploads.uploadImage(input.firstFrame).url());
            if (input.lastFrame != null) body.put("last_image_url", materialUploads.uploadImage(input.lastFrame).url());
            return new UpstreamRequest(body, images.size() > 1 ? images : List.of());
        }
        if (model.startsWith("omni-v2v")) {
            if (!input.videoUrls.isEmpty()) body.put("reference_videos", input.videoUrls);
            List<String> images = input.images.stream().map(image -> materialUploads.uploadImage(image).url()).toList();
            if (!images.isEmpty()) body.put("reference_image_urls", images);
            return new UpstreamRequest(body, List.of());
        }
        body.put("resolution", input.resolution);
        if (model.startsWith("seedance-")) {
            List<String> imageData = input.images.stream().map(image -> materialUploads.uploadImage(image).url()).toList();
            body.put("duration", input.duration); body.put("audio", input.audio);
            if (!imageData.isEmpty()) {
                body.put("image_url", imageData.get(0));
                if (imageData.size() > 1) body.put("reference_image_urls", imageData.subList(1, imageData.size()));
            }
            if (!input.videoUrls.isEmpty()) body.put("reference_videos", input.videoUrls);
            if (!input.audioUrls.isEmpty()) body.put("reference_audios", input.audioUrls);
            if (input.firstFrame != null) body.put("first_image_url", materialUploads.uploadImage(input.firstFrame).url());
            if (input.lastFrame != null) body.put("last_image_url", materialUploads.uploadImage(input.lastFrame).url());
        } else {
            if ("grok-video-1.5".equals(model)) {
                body.put("duration", input.duration);
                body.put("image", Map.of("url", materialUploads.uploadImage(input.images.get(0)).url()));
            } else {
                List<String> imageData = input.images.stream().map(image -> materialUploads.uploadImage(image).url()).toList();
                body.put("seconds", String.valueOf(input.duration));
                if (!imageData.isEmpty()) body.put("image_urls", imageData);
            }
            if (!input.videoUrls.isEmpty()) body.put("video_url", input.videoUrls.get(0));
        }
        return new UpstreamRequest(body, List.of());
    }

    private VideoGenerationJob job(ApiProfile profile, Input input, Map<String, Object> raw, String requestId, int index) {
        VideoGenerationJob job = new VideoGenerationJob();
        job.setId(id()); job.setProfileId(profile.getId()); job.setModelConfigId(input.runtime.model().getId());
        job.setRequestId(requestId); job.setPrompt(input.prompt); job.setModel(input.runtime.model().getModelKey());
        job.setDuration(input.duration); job.setAspectRatio(input.ratio); job.setResolution(input.resolution);
        job.setGenerateAudio(input.audio ? 1 : 0); job.setStatus("PENDING"); job.setProgress(0); job.setPollErrorCount(0);
        job.setParams(stringify(Map.of("requestIndex", index, "requestTotal", input.count,
                "referenceImageCount", input.images.size(), "referenceVideoUrls", input.videoUrls,
                "referenceAudioUrls", input.audioUrls, "hasFrames", input.firstFrame != null)));
        job.setRawRequest(GenerationAuditJson.request(auditClient(raw, input))); job.setRawResponses("[]");
        job.setCreatedAt(ImageTime.now()); job.setUpdatedAt(ImageTime.now()); return job;
    }

    private List<String> urls(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : list) result.add(SafeUpstreamUrl.requirePublicHttps(String.valueOf(item)));
        return result;
    }
    private void validateImage(ImageGateway.Upload image, long maxBytes) {
        byte[] bytes = image.bytes();
        if (bytes.length == 0 || bytes.length > maxBytes || !(image.mimeType().equals("image/png")
                || image.mimeType().equals("image/jpeg") || image.mimeType().equals("image/webp")) || !imageSignature(bytes))
            throw new ImageApiException(422, "Unsupported or invalid reference image.");
    }
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
    private Map<String, Object> auditClient(Map<String, Object> raw, Input input) {
        Map<String, Object> value = new LinkedHashMap<>(raw);
        if (!input.images.isEmpty()) value.put("uploadedImages", input.images.stream().map(VideoGenerationService::file).toList());
        if (input.firstFrame != null) value.put("firstFrame", file(input.firstFrame));
        if (input.lastFrame != null) value.put("lastFrame", file(input.lastFrame));
        return value;
    }
    private static Map<String, Object> file(ImageGateway.Upload value) {
        return Map.of("name", value.name(), "mimeType", value.mimeType(), "sizeBytes", value.bytes().length);
    }
    private static String id() { return UUID.randomUUID().toString().replace("-", ""); }
    private static String text(Object value) { return value instanceof String text && !text.isBlank() ? text : null; }
    private static String value(Object value, String fallback) { String text = text(value); return text == null ? fallback : text; }
    private static int number(Object value, int fallback) { return value instanceof Number number ? number.intValue() : fallback; }
    private static boolean bool(Object value, boolean fallback) { return value instanceof Boolean flag ? flag : fallback; }
    private static String message(Exception error) { return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }
    private static Object payload(Exception error) {
        return error instanceof ImageApiException api && api.getPayload() != null
                ? api.getPayload() : Map.of("message", message(error));
    }

    private record Input(String prompt, int count, int duration, String ratio, String resolution, boolean audio,
                         List<ImageGateway.Upload> images, List<String> videoUrls, List<String> audioUrls,
                         ImageGateway.Upload firstFrame, ImageGateway.Upload lastFrame,
                         ImageModelConfigService.RuntimeModel runtime) {}
    private record UpstreamRequest(Map<String, Object> body, List<String> inputReferences) {}
    public record Accepted(String requestId, int count) {}
}
