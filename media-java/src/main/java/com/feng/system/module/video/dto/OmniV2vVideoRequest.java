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
@JsonDeserialize(using = JsonDeserializer.None.class)
public class OmniV2vVideoRequest extends VideoGenerationRequest {
    private List<String> referenceVideoUrls;

    @Override
    protected VideoGenerationInput buildInput(ImageModelConfigService.RuntimeModel runtime,
            List<ImageGateway.Upload> images, ImageGateway.Upload firstFrame, ImageGateway.Upload lastFrame) {
        if (Boolean.TRUE.equals(getExtraFields().get("generateAudio")))
            throw new ImageApiException(422, "Invalid video generation parameters.");
        return input(runtime, images, firstFrame, lastFrame, false, referenceVideoUrls, List.of());
    }
}
