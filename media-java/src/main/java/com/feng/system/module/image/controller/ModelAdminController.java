package com.feng.system.module.image.controller;

import com.feng.system.module.image.service.ImageModelConfigService;

import com.feng.system.common.api.ApiResponse;
import com.feng.system.common.api.PageResult;
import com.feng.system.module.image.entity.AiModel;
import com.feng.system.module.image.entity.ModelProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ModelAdminController {
    private final ImageModelConfigService service;

    @GetMapping("/api/model/providers")
    public ApiResponse<PageResult<ModelProvider>> providers(@RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") long pageNum, @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.success(service.providerPage(name, pageNum, pageSize));
    }

    @GetMapping("/api/model/providers/options")
    public ApiResponse<List<ModelProvider>> providerOptions() { return ApiResponse.success(service.providerOptions()); }

    @PostMapping("/api/model/providers")
    public ApiResponse<Void> saveProvider(@RequestBody ModelProvider provider) { service.saveProvider(provider); return ApiResponse.success(null); }

    @PutMapping("/api/model/providers/{id}")
    public ApiResponse<Void> updateProvider(@PathVariable Long id, @RequestBody ModelProvider provider) { service.updateProvider(id, provider); return ApiResponse.success(null); }

    @DeleteMapping("/api/model/providers/{id}")
    public ApiResponse<Void> deleteProvider(@PathVariable Long id) { service.deleteProvider(id); return ApiResponse.success(null); }

    @GetMapping("/api/model/images")
    public ApiResponse<PageResult<AiModel>> images(@RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") long pageNum, @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.success(service.imagePage(name, pageNum, pageSize));
    }

    @PostMapping("/api/model/images")
    public ApiResponse<Void> saveImage(@RequestBody AiModel model) { service.saveImage(model); return ApiResponse.success(null); }

    @PutMapping("/api/model/images/{id}")
    public ApiResponse<Void> updateImage(@PathVariable Long id, @RequestBody AiModel model) { service.updateImage(id, model); return ApiResponse.success(null); }

    @DeleteMapping("/api/model/images/{id}")
    public ApiResponse<Void> deleteImage(@PathVariable Long id) { service.deleteImage(id); return ApiResponse.success(null); }

    @GetMapping("/api/images/models")
    public Map<String, Object> publicImages() { return Map.of("models", service.publicImages()); }

    @GetMapping("/api/videos/models")
    public Map<String, Object> publicVideos() { return Map.of("models", service.publicVideos()); }
}
