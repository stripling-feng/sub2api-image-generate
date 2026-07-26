package com.feng.system.module.video.dto;

import com.feng.system.module.image.service.ImageGateway;
import com.feng.system.module.image.service.ImageModelConfigService;

import java.util.List;

/**
 * 规范化后的视频生成输入:由各模型请求 DTO 校验并填充默认值后生成,
 * 携带提示词、时长、参考素材以及运行时模型配置,供生成服务构造上游请求。
 */
public record VideoGenerationInput(String prompt, int count, int duration, String aspectRatio, String resolution,
                                   boolean generateAudio, List<ImageGateway.Upload> images,
                                   List<String> referenceImageUrls,
                                   List<String> referenceVideoUrls, List<String> referenceAudioUrls,
                                   ImageGateway.Upload firstFrame, String firstFrameUrl,
                                   ImageGateway.Upload lastFrame, String lastFrameUrl,
                                   ImageModelConfigService.RuntimeModel runtime) {
    /** 返回本次请求使用的模型标识(modelKey)。 */
    public String modelKey() {
        return runtime.model().getModelKey();
    }

    /** 参考图片总数 = 上传图片数 + 参考图片 URL 数。 */
    public int imageCount() {
        return images.size() + referenceImageUrls.size();
    }

    /** 是否指定了首帧图(上传文件或 URL 任一)。 */
    public boolean hasFirstFrame() {
        return firstFrame != null || firstFrameUrl != null;
    }

    /** 是否指定了尾帧图(上传文件或 URL 任一)。 */
    public boolean hasLastFrame() {
        return lastFrame != null || lastFrameUrl != null;
    }
}
