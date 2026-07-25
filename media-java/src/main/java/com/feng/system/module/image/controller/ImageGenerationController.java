package com.feng.system.module.image.controller;

import com.feng.system.module.image.exception.ImageApiException;
import com.feng.system.module.image.service.ImageGateway;
import com.feng.system.module.image.service.ImageGenerationService;
import com.feng.system.module.image.service.ImageSessionService;

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
public class ImageGenerationController {
    private final ImageSessionService sessions;
    private final ImageGenerationService generation;
    private final ObjectMapper json;

    @PostMapping(value = "/api/images/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> json(@RequestBody Map<String, Object> input,
            HttpServletRequest request, HttpServletResponse response) {
        return accepted(generation.generate(profile(request, response), input, List.of(), null));
    }

    @PostMapping(value = "/api/images/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> multipart(@RequestPart("payload") String payload,
            @RequestPart(name = "image", required = false) List<MultipartFile> one,
            @RequestPart(name = "image[]", required = false) List<MultipartFile> many,
            @RequestPart(name = "mask", required = false) MultipartFile mask,
            HttpServletRequest request, HttpServletResponse response) {
        try {
            List<ImageGateway.Upload> images = new ArrayList<>();
            if (one != null) for (MultipartFile file : one) images.add(upload(file));
            if (many != null) for (MultipartFile file : many) images.add(upload(file));
            Map<String, Object> input = json.readValue(payload, new TypeReference<>() {});
            return accepted(generation.generate(profile(request, response), input, images, mask == null ? null : upload(mask)));
        } catch (ImageApiException e) { throw e; }
        catch (Exception e) { throw new ImageApiException(422, "Multipart payload must be valid JSON."); }
    }

    private ImageGateway.Upload upload(MultipartFile file) throws Exception {
        return new ImageGateway.Upload(file.getOriginalFilename() == null ? "image" : file.getOriginalFilename(),
                file.getContentType() == null ? "application/octet-stream" : file.getContentType(), file.getBytes());
    }
    private ResponseEntity<Map<String, Object>> accepted(ImageGenerationService.Accepted value) {
        return ResponseEntity.status(202).body(Map.of("requestId", value.requestId(), "count", value.count()));
    }
    private ApiProfile profile(HttpServletRequest request, HttpServletResponse response) { return sessions.requireProfile(request, response); }
}
