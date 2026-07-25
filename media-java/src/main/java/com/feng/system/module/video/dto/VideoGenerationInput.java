package com.feng.system.module.video.dto;

import com.feng.system.module.image.service.ImageGateway;
import com.feng.system.module.image.service.ImageModelConfigService;

import java.util.List;

public record VideoGenerationInput(String prompt, int count, int duration, String aspectRatio, String resolution,
                                   boolean generateAudio, List<ImageGateway.Upload> images,
                                   List<String> referenceImageUrls,
                                   List<String> referenceVideoUrls, List<String> referenceAudioUrls,
                                   ImageGateway.Upload firstFrame, String firstFrameUrl,
                                   ImageGateway.Upload lastFrame, String lastFrameUrl,
                                   ImageModelConfigService.RuntimeModel runtime) {
    public String modelKey() {
        return runtime.model().getModelKey();
    }

    public int imageCount() {
        return images.size() + referenceImageUrls.size();
    }

    public boolean hasFirstFrame() {
        return firstFrame != null || firstFrameUrl != null;
    }

    public boolean hasLastFrame() {
        return lastFrame != null || lastFrameUrl != null;
    }
}
