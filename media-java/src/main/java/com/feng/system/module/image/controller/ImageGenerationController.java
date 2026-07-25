package com.feng.system.module.image.controller;

import com.feng.system.module.image.dto.ImageGenerateRequest;
import com.feng.system.module.image.service.ImageGenerationService;
import com.feng.system.module.image.service.ImageReferenceUploadService;
import com.feng.system.module.image.service.ImageSessionService;

import com.feng.system.module.image.entity.ApiProfile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ImageGenerationController {
    private final ImageSessionService sessions;
    private final ImageGenerationService generation;
    private final ImageReferenceUploadService uploads;

    @PostMapping(value = "/api/images/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> json(@RequestBody ImageGenerateRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        return accepted(generation.generate(profile(request, response), input, List.of(), null));
    }

    @PostMapping(value = "/api/images/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadReference(@RequestParam("file") MultipartFile file,
            HttpServletRequest request, HttpServletResponse response) {
        profile(request, response);
        ImageReferenceUploadService.Uploaded uploaded = uploads.upload(file, request);
        return Map.of("url", uploaded.url(), "publicUrl", uploaded.url(), "mimeType", uploaded.mimeType(),
                "sizeBytes", uploaded.sizeBytes());
    }

    private ResponseEntity<Map<String, Object>> accepted(ImageGenerationService.Accepted value) {
        return ResponseEntity.status(202).body(Map.of("requestId", value.requestId(), "count", value.count()));
    }
    private ApiProfile profile(HttpServletRequest request, HttpServletResponse response) { return sessions.requireProfile(request, response); }
}
