package com.feng.system.module.video.dto;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.feng.system.module.image.exception.ImageApiException;
import com.feng.system.module.image.service.ImageGateway;
import com.feng.system.module.image.service.ImageModelConfigService;

import java.util.List;

/**
 * omni-fast 模型的生成请求:仅支持文本/图片生视频,不支持参考视频、参考音频与音频生成。
 */
@JsonDeserialize(using = JsonDeserializer.None.class)
public class OmniFastVideoRequest extends VideoGenerationRequest {
    /** 构建生成输入:omni-fast 不支持音频生成,显式传 generateAudio=true 直接拒绝。 */
    @Override
    protected VideoGenerationInput buildInput(ImageModelConfigService.RuntimeModel runtime,
            List<ImageGateway.Upload> images, ImageGateway.Upload firstFrame, ImageGateway.Upload lastFrame) {
        if (Boolean.TRUE.equals(getExtraFields().get("generateAudio")))
            throw new ImageApiException(422, "Invalid video generation parameters.");
        return input(runtime, images, firstFrame, lastFrame, false, List.of(), List.of());
    }
}
