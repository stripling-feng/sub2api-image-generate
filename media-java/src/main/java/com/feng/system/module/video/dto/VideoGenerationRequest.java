package com.feng.system.module.video.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.feng.system.module.image.exception.ImageApiException;
import com.feng.system.module.image.service.ImageGateway;
import com.feng.system.module.image.service.ImageModelConfigService;
import com.feng.system.module.image.support.SafeUpstreamUrl;
import com.feng.system.module.video.service.VideoTaskRules;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@JsonDeserialize(using = VideoGenerationRequestDeserializer.class)
public abstract class VideoGenerationRequest {
    private String model;
    private String prompt;
    private Integer count;
    private Integer duration;
    private String aspectRatio;
    private String resolution;
    private List<String> referenceImageUrls;
    private String firstFrameUrl;
    private String lastFrameUrl;
    private final Map<String, Object> extraFields = new LinkedHashMap<>();

    @JsonAnySetter
    public void addExtraField(String key, Object value) {
        extraFields.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getExtraFields() {
        return extraFields;
    }

    public final VideoGenerationInput toInput(ImageModelConfigService.RuntimeModel runtime,
            List<ImageGateway.Upload> images, ImageGateway.Upload firstFrame, ImageGateway.Upload lastFrame) {
        VideoGenerationInput input = buildInput(runtime, images, firstFrame, lastFrame);
        validateCommon(input);
        VideoTaskRules.validate(input.modelKey(), input.duration(), input.aspectRatio(), input.resolution(),
                input.imageCount(), input.referenceVideoUrls().size(), input.referenceAudioUrls().size(),
                input.hasFirstFrame(), input.hasLastFrame());
        return input;
    }

    protected abstract VideoGenerationInput buildInput(ImageModelConfigService.RuntimeModel runtime,
            List<ImageGateway.Upload> images, ImageGateway.Upload firstFrame, ImageGateway.Upload lastFrame);

    protected VideoGenerationInput input(ImageModelConfigService.RuntimeModel runtime, List<ImageGateway.Upload> images,
            ImageGateway.Upload firstFrame, ImageGateway.Upload lastFrame, boolean generateAudio,
            List<String> referenceVideoUrls, List<String> referenceAudioUrls) {
        String modelKey = runtime.model().getModelKey();
        return new VideoGenerationInput(prompt, value(count, 1), value(duration, defaultDuration(modelKey)),
                text(aspectRatio, "16:9"), text(resolution, "720p"), generateAudio,
                images == null ? List.of() : images, publicUrls(referenceImageUrls), publicUrls(referenceVideoUrls),
                publicUrls(referenceAudioUrls), firstFrame, publicUrl(firstFrameUrl), lastFrame, publicUrl(lastFrameUrl), runtime);
    }

    private void validateCommon(VideoGenerationInput input) {
        if (input.prompt() == null || input.prompt().isBlank() || input.prompt().length() > 5000
                || input.count() < 1 || input.count() > 4)
            throw new ImageApiException(422, "Invalid video generation request.");
    }

    private static List<String> publicUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        for (String url : urls) result.add(SafeUpstreamUrl.requirePublicHttps(url));
        return result;
    }

    private static String publicUrl(String url) {
        if (url == null || url.isBlank()) return null;
        return SafeUpstreamUrl.requirePublicHttps(url);
    }

    private static int defaultDuration(String modelKey) {
        return modelKey != null && modelKey.startsWith("omni-") ? 10 : 8;
    }

    private static int value(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
