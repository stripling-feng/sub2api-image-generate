package com.feng.system.module.system.controller;

import com.feng.system.common.api.ApiResponse;
import com.feng.system.common.api.PageResult;
import com.feng.system.module.image.entity.ModelProvider;
import com.feng.system.module.image.service.ImageModelConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ModelProviderController {
    private final ImageModelConfigService service;

    @GetMapping("/api/model/providers")
    public ApiResponse<PageResult<ModelProvider>> providers(@RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") long pageNum, @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.success(service.providerPage(name, pageNum, pageSize));
    }

    @GetMapping("/api/model/providers/options")
    public ApiResponse<List<ModelProvider>> providerOptions() {
        return ApiResponse.success(service.providerOptions());
    }

    @PostMapping("/api/model/providers")
    public ApiResponse<Void> saveProvider(@RequestBody ModelProvider provider) {
        service.saveProvider(provider);
        return ApiResponse.success(null);
    }

    @PutMapping("/api/model/providers/{id}")
    public ApiResponse<Void> updateProvider(@PathVariable Long id, @RequestBody ModelProvider provider) {
        service.updateProvider(id, provider);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/api/model/providers/{id}")
    public ApiResponse<Void> deleteProvider(@PathVariable Long id) {
        service.deleteProvider(id);
        return ApiResponse.success(null);
    }
}
