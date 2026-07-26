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

/**
 * 视频生成请求的抽象基类:承载各模型通用的字段(模型、提示词、数量、时长、比例、分辨率、参考图等),
 * 由 VideoGenerationRequestDeserializer 按 model 字段分发到具体子类;
 * toInput 负责补默认值、通用校验并调用 VideoTaskRules 做模型级参数校验。
 */
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

    /** 收集 JSON 中未声明的额外字段,便于审计原始请求并识别不支持的参数。 */
    @JsonAnySetter
    public void addExtraField(String key, Object value) {
        extraFields.put(key, value);
    }

    /** 返回未声明的额外字段集合。 */
    @JsonAnyGetter
    public Map<String, Object> getExtraFields() {
        return extraFields;
    }

    /**
     * 将请求转换为规范化的生成输入:先由子类构建输入,再做通用校验(提示词、数量),
     * 最后按模型执行 VideoTaskRules 的参数规则校验。
     */
    public final VideoGenerationInput toInput(ImageModelConfigService.RuntimeModel runtime,
            List<ImageGateway.Upload> images, ImageGateway.Upload firstFrame, ImageGateway.Upload lastFrame) {
        VideoGenerationInput input = buildInput(runtime, images, firstFrame, lastFrame);
        validateCommon(input);
        VideoTaskRules.validate(input.modelKey(), input.duration(), input.aspectRatio(), input.resolution(),
                input.imageCount(), input.referenceVideoUrls().size(), input.referenceAudioUrls().size(),
                input.hasFirstFrame(), input.hasLastFrame());
        return input;
    }

    /** 由子类实现:按各自模型的能力(音频、参考视频/音频等)构建生成输入。 */
    protected abstract VideoGenerationInput buildInput(ImageModelConfigService.RuntimeModel runtime,
            List<ImageGateway.Upload> images, ImageGateway.Upload firstFrame, ImageGateway.Upload lastFrame);

    /** 组装生成输入并补默认值:数量默认 1,比例默认 16:9,分辨率默认 720p,所有 URL 需为公网 HTTPS。 */
    protected VideoGenerationInput input(ImageModelConfigService.RuntimeModel runtime, List<ImageGateway.Upload> images,
            ImageGateway.Upload firstFrame, ImageGateway.Upload lastFrame, boolean generateAudio,
            List<String> referenceVideoUrls, List<String> referenceAudioUrls) {
        String modelKey = runtime.model().getModelKey();
        return new VideoGenerationInput(prompt, value(count, 1), value(duration, defaultDuration(modelKey)),
                text(aspectRatio, "16:9"), text(resolution, "720p"), generateAudio,
                images == null ? List.of() : images, publicUrls(referenceImageUrls), publicUrls(referenceVideoUrls),
                publicUrls(referenceAudioUrls), firstFrame, publicUrl(firstFrameUrl), lastFrame, publicUrl(lastFrameUrl), runtime);
    }

    // 通用校验:提示词非空且不超过 5000 字符,生成数量 1~4
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

    // 默认时长:omni 系列固定 10 秒,其余模型默认 8 秒
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
