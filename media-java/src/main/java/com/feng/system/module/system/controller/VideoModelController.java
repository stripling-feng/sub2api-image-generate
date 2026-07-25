package com.feng.system.module.system.controller;

import com.feng.system.common.api.ApiResponse;
import com.feng.system.common.api.PageResult;
import com.feng.system.module.image.entity.AiModel;
import com.feng.system.module.image.service.ImageModelConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class VideoModelController {
    private final ImageModelConfigService service;

    @GetMapping("/api/model/videos")
    public ApiResponse<PageResult<AiModel>> videos(@RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") long pageNum, @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.success(service.videoPage(name, pageNum, pageSize));
    }

    @PostMapping("/api/model/videos")
    public ApiResponse<Void> saveVideo(@RequestBody AiModel model) {
        service.saveVideo(model);
        return ApiResponse.success(null);
    }

    @PutMapping("/api/model/videos/{id}")
    public ApiResponse<Void> updateVideo(@PathVariable Long id, @RequestBody AiModel model) {
        service.updateVideo(id, model);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/api/model/videos/{id}")
    public ApiResponse<Void> deleteVideo(@PathVariable Long id) {
        service.deleteVideo(id);
        return ApiResponse.success(null);
    }
}
