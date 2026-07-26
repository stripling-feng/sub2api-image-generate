package com.feng.system.module.video.dto;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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
 * grok-video 模型的生成请求:在通用字段基础上支持参考视频 URL 列表,不支持音频生成。
 */
@JsonDeserialize(using = JsonDeserializer.None.class)
public class GrokVideoRequest extends VideoGenerationRequest {
    private List<String> referenceVideoUrls;

    /** 构建生成输入:grok 系列固定不生成音频,不支持参考音频。 */
    @Override
    protected VideoGenerationInput buildInput(ImageModelConfigService.RuntimeModel runtime,
            List<ImageGateway.Upload> images, ImageGateway.Upload firstFrame, ImageGateway.Upload lastFrame) {
        return input(runtime, images, firstFrame, lastFrame, false, referenceVideoUrls, List.of());
    }
}
