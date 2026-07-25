package com.feng.system.module.video.controller;

import com.feng.system.module.image.service.ImageSessionService;
import com.feng.system.module.video.dto.VideoGenerationRequest;
import com.feng.system.module.video.service.VideoGenerationService;
import com.feng.system.module.video.service.VideoMaterialUploadService;

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
public class VideoGenerationController {
    private final ImageSessionService sessions;
    private final VideoGenerationService generation;
    private final VideoMaterialUploadService uploads;

    @PostMapping(value = "/api/videos/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> json(@RequestBody VideoGenerationRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        return accepted(generation.generate(profile(request, response), input, List.of(), null, null));
    }

    @PostMapping(value = "/api/videos/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadMaterial(@RequestParam("file") MultipartFile file,
            HttpServletRequest request, HttpServletResponse response) {
        profile(request, response);
        VideoMaterialUploadService.Uploaded uploaded = uploads.upload(file, request);
        return Map.of("url", uploaded.url(), "publicUrl", uploaded.url(), "mimeType", uploaded.mimeType(),
                "sizeBytes", uploaded.sizeBytes());
    }

    private ResponseEntity<Map<String, Object>> accepted(VideoGenerationService.Accepted value) {
        return ResponseEntity.status(202).body(Map.of("requestId", value.requestId(), "count", value.count()));
    }
    private ApiProfile profile(HttpServletRequest request, HttpServletResponse response) {
        return sessions.requireProfile(request, response);
    }
}
