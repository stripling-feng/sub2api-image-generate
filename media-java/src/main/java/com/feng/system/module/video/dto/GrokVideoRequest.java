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
@JsonDeserialize(using = JsonDeserializer.None.class)
public class GrokVideoRequest extends VideoGenerationRequest {
    private List<String> referenceVideoUrls;

    @Override
    protected VideoGenerationInput buildInput(ImageModelConfigService.RuntimeModel runtime,
            List<ImageGateway.Upload> images, ImageGateway.Upload firstFrame, ImageGateway.Upload lastFrame) {
        return input(runtime, images, firstFrame, lastFrame, false, referenceVideoUrls, List.of());
    }
}
