package com.feng.system.module.image.controller;

import com.feng.system.module.image.service.ImageModelConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PublicModelController {
    private final ImageModelConfigService service;

    @GetMapping("/api/images/models")
    public Map<String, Object> publicImages() {
        return Map.of("models", service.publicImages());
    }

    @GetMapping("/api/videos/models")
    public Map<String, Object> publicVideos() {
        return Map.of("models", service.publicVideos());
    }
}
