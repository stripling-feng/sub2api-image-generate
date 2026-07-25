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
import com.feng.system.module.video.dto.VideoGenerationInput;
import com.feng.system.module.video.dto.VideoGenerationRequest;
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

    public Accepted generate(ApiProfile profile, VideoGenerationRequest request, List<ImageGateway.Upload> images,
                             ImageGateway.Upload firstFrame, ImageGateway.Upload lastFrame) {
        Map<String, Object> raw = raw(request);
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

    private void create(ApiProfile profile, VideoGenerationInput input, Map<String, Object> raw, String requestId, int index) {
        VideoGenerationJob job = job(profile, input, raw, requestId, index);
        jobs.insert(job);
        Sub2apiBillingService.Reservation reservation = null;
        try {
            BigDecimal amount = VideoTaskRules.charge(input.runtime().model().getBillingMode(), input.duration(),
                    input.runtime().model().getUnitPriceUsd());
            reservation = billing.reserveAmount(profile.getEncryptedKey(), amount);
            job.setBillingStatus("RESERVED"); job.setBillingAmount(reservation.amountUsd());
            job.setBillingApiKeyId(reservation.apiKeyId()); job.setBillingUserId(reservation.userId());
            job.setBillingAccountId(reservation.accountId()); job.setBillingReservedAt(ImageTime.now());
            jobs.updateById(job);
            UpstreamRequest request = request(input);
            Map<String, Object> upstream = new LinkedHashMap<>(request.body());
            if (!request.inputReferences().isEmpty()) upstream.put("input_reference", request.inputReferences());
            job.setRawRequest(GenerationAuditJson.withUpstream(job.getRawRequest(), upstream));
            jobs.updateById(job);
            VideoGateway.Task task = request.inputReferences().isEmpty()
                    ? gateway.create(input.runtime().provider().getBaseUrl(), input.runtime().provider().getVideoApiKey(),
                    input.runtime().model().getGenerationPath(), request.body())
                    : gateway.create(input.runtime().provider().getBaseUrl(), input.runtime().provider().getVideoApiKey(),
                    input.runtime().model().getGenerationPath(), request.body(), request.inputReferences());
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

    private void validateUploads(VideoGenerationInput input) {
        long maxImageBytes = input.modelKey().startsWith("omni-fast") ? 5L * 1024 * 1024
                : input.modelKey().startsWith("omni-v2v") ? 8L * 1024 * 1024 : 30L * 1024 * 1024;
        for (ImageGateway.Upload image : input.images()) validateImage(image, maxImageBytes);
        if (input.firstFrame() != null) validateImage(input.firstFrame(), maxImageBytes);
        if (input.lastFrame() != null) validateImage(input.lastFrame(), maxImageBytes);
    }

    private UpstreamRequest request(VideoGenerationInput input) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", input.runtime().model().getUpstreamModel()); body.put("prompt", input.prompt());
        body.put("aspect_ratio", input.aspectRatio());
        String model = input.runtime().model().getModelKey();
        List<String> images = imageUrls(input);
        String firstFrame = frameUrl(input.firstFrameUrl(), input.firstFrame());
        String lastFrame = frameUrl(input.lastFrameUrl(), input.lastFrame());
        if (model.startsWith("omni-fast")) {
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
                body.put("duration", input.duration());
                body.put("image", Map.of("url", images.get(0)));
            } else {
                body.put("seconds", String.valueOf(input.duration()));
                if (!images.isEmpty()) body.put("image_urls", images);
            }
            if (!input.referenceVideoUrls().isEmpty()) body.put("video_url", input.referenceVideoUrls().get(0));
        }
        return new UpstreamRequest(body, List.of());
    }

    private List<String> imageUrls(VideoGenerationInput input) {
        List<String> urls = new ArrayList<>(input.referenceImageUrls());
        urls.addAll(input.images().stream().map(image -> materialUploads.uploadImage(image).url()).toList());
        return urls;
    }

    private String frameUrl(String url, ImageGateway.Upload upload) {
        if (url != null) return url;
        return upload == null ? null : materialUploads.uploadImage(upload).url();
    }

    private VideoGenerationJob job(ApiProfile profile, VideoGenerationInput input, Map<String, Object> raw, String requestId, int index) {
        VideoGenerationJob job = new VideoGenerationJob();
        job.setId(id()); job.setProfileId(profile.getId()); job.setModelConfigId(input.runtime().model().getId());
        job.setRequestId(requestId); job.setPrompt(input.prompt()); job.setModel(input.runtime().model().getModelKey());
        job.setDuration(input.duration()); job.setAspectRatio(input.aspectRatio()); job.setResolution(input.resolution());
        job.setGenerateAudio(input.generateAudio() ? 1 : 0); job.setStatus("PENDING"); job.setProgress(0); job.setPollErrorCount(0);
        job.setParams(stringify(Map.of("requestIndex", index, "requestTotal", input.count(),
                "referenceImageCount", input.imageCount(), "referenceVideoUrls", input.referenceVideoUrls(),
                "referenceAudioUrls", input.referenceAudioUrls(), "hasFrames", input.hasFirstFrame())));
        job.setRawRequest(GenerationAuditJson.request(auditClient(raw, input))); job.setRawResponses("[]");
        job.setCreatedAt(ImageTime.now()); job.setUpdatedAt(ImageTime.now()); return job;
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

    private record UpstreamRequest(Map<String, Object> body, List<String> inputReferences) {}
    public record Accepted(String requestId, int count) {}
}
