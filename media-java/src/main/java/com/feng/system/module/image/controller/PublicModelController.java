package com.feng.system.module.image.controller;

import com.feng.system.common.api.ApiResponse;
import com.feng.system.module.image.dto.PublicModelsResponse;
import com.feng.system.module.image.service.ImageModelConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开模型列表控制器。
 * 对外暴露当前可用的图片/视频生成模型清单,无需鉴权。
 */
@RestController
@RequiredArgsConstructor
public class PublicModelController {
    private final ImageModelConfigService service;

    /**
     * GET /api/images/models:获取对外公开的图片生成模型列表。
     */
    @GetMapping("/api/images/models")
    public ApiResponse<PublicModelsResponse> publicImages() {
        return ApiResponse.success(new PublicModelsResponse(service.publicImages()));
    }

    /**
     * GET /api/videos/models:获取对外公开的视频生成模型列表。
     */
    @GetMapping("/api/videos/models")
    public ApiResponse<PublicModelsResponse> publicVideos() {
        return ApiResponse.success(new PublicModelsResponse(service.publicVideos()));
    }
}
