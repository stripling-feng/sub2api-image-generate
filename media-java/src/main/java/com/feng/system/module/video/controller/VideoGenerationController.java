package com.feng.system.module.video.controller;

import com.feng.system.module.image.exception.ImageApiException;
import com.feng.system.module.image.service.ImageGateway;
import com.feng.system.module.image.service.ImageSessionService;
import com.feng.system.module.video.service.VideoGenerationService;
import com.feng.system.module.video.service.VideoMaterialUploadService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.image.entity.ApiProfile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class VideoGenerationController {
    private final ImageSessionService sessions;
    private final VideoGenerationService generation;
    private final VideoMaterialUploadService uploads;
    private final ObjectMapper json;

    @PostMapping(value = "/api/videos/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> json(@RequestBody Map<String, Object> input,
            HttpServletRequest request, HttpServletResponse response) {
        return accepted(generation.generate(profile(request, response), input, List.of(), null, null));
    }

    @PostMapping(value = "/api/videos/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> multipart(@RequestPart("payload") String payload,
            @RequestPart(name = "referenceImage", required = false) List<MultipartFile> images,
            @RequestPart(name = "firstFrame", required = false) MultipartFile firstFrame,
            @RequestPart(name = "lastFrame", required = false) MultipartFile lastFrame,
            HttpServletRequest request, HttpServletResponse response) {
        try {
            List<ImageGateway.Upload> uploads = new ArrayList<>();
            if (images != null) for (MultipartFile image : images) uploads.add(upload(image));
            Map<String, Object> input = json.readValue(payload, new TypeReference<>() {});
            return accepted(generation.generate(profile(request, response), input, uploads,
                    firstFrame == null ? null : upload(firstFrame), lastFrame == null ? null : upload(lastFrame)));
        } catch (ImageApiException error) { throw error; }
        catch (Exception error) { throw new ImageApiException(422, "Multipart payload must be valid JSON."); }
    }

    @PostMapping(value = "/api/videos/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadMaterial(@RequestParam("file") MultipartFile file,
            HttpServletRequest request, HttpServletResponse response) {
        profile(request, response);
        VideoMaterialUploadService.Uploaded uploaded = uploads.upload(file, request);
        return Map.of("url", uploaded.url(), "publicUrl", uploaded.url(), "mimeType", uploaded.mimeType(),
                "sizeBytes", uploaded.sizeBytes());
    }

    private ImageGateway.Upload upload(MultipartFile file) throws Exception {
        return new ImageGateway.Upload(file.getOriginalFilename() == null ? "image" : file.getOriginalFilename(),
                file.getContentType() == null ? "application/octet-stream" : file.getContentType(), file.getBytes());
    }
    private ResponseEntity<Map<String, Object>> accepted(VideoGenerationService.Accepted value) {
        return ResponseEntity.status(202).body(Map.of("requestId", value.requestId(), "count", value.count()));
    }
    private ApiProfile profile(HttpServletRequest request, HttpServletResponse response) {
        return sessions.requireProfile(request, response);
    }
}
