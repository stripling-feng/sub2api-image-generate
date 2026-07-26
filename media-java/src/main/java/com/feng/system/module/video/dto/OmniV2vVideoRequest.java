package com.feng.system.module.video.dto;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.feng.system.module.image.exception.ImageApiException;
import com.feng.system.module.image.service.ImageGateway;
import com.feng.system.module.image.service.ImageModelConfigService;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
/**
 * omni-v2v(视频生视频)模型的生成请求:支持参考视频 URL 列表,不支持参考音频与音频生成。
 */
@JsonDeserialize(using = JsonDeserializer.None.class)
public class OmniV2vVideoRequest extends VideoGenerationRequest {
    private List<String> referenceVideoUrls;

    /** 构建生成输入:omni-v2v 不支持音频生成,显式传 generateAudio=true 直接拒绝。 */
    @Override
    protected VideoGenerationInput buildInput(ImageModelConfigService.RuntimeModel runtime,
            List<ImageGateway.Upload> images, ImageGateway.Upload firstFrame, ImageGateway.Upload lastFrame) {
        if (Boolean.TRUE.equals(getExtraFields().get("generateAudio")))
            throw new ImageApiException(422, "Invalid video generation parameters.");
        return input(runtime, images, firstFrame, lastFrame, false, referenceVideoUrls, List.of());
    }
}
