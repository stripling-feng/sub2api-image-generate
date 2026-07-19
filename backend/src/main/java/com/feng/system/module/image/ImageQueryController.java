package com.feng.system.module.image;

import com.feng.system.module.image.entity.ApiProfile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ImageQueryController {
    private final ImageSessionService sessions;
    private final ImageQueryService queries;

    @GetMapping("/api/health") public Map<String, Object> health() { return Map.of("ok", true); }

    @GetMapping("/api/images/history")
    public Map<String, Object> history(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int pageSize,
                                      HttpServletRequest request, HttpServletResponse response) {
        return queries.history(profile(request, response).getId(), page, pageSize);
    }

    @GetMapping("/api/images/results/{requestId}")
    public Map<String, Object> results(@PathVariable String requestId, HttpServletRequest request, HttpServletResponse response) {
        return Map.of("jobs", queries.results(profile(request, response).getId(), requestId));
    }

    @DeleteMapping("/api/images/{id}")
    public Map<String, Object> deleteImage(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) {
        queries.deleteImage(profile(request, response).getId(), id); return Map.of("ok", true);
    }

    @DeleteMapping("/api/jobs")
    public Map<String, Object> deleteJobs(HttpServletRequest request, HttpServletResponse response) {
        return Map.of("ok", true, "deletedCount", queries.deleteJobs(profile(request, response).getId()));
    }

    @DeleteMapping("/api/jobs/{id}")
    public Map<String, Object> deleteJob(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) {
        queries.deleteJob(profile(request, response).getId(), id); return Map.of("ok", true);
    }

    @GetMapping("/api/templates")
    public Map<String, Object> templates(HttpServletRequest request, HttpServletResponse response) {
        return Map.of("templates", queries.templates(profile(request, response).getId()));
    }

    @PostMapping("/api/templates")
    public ResponseEntity<Map<String, Object>> createTemplate(@RequestBody TemplateRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.status(201).body(Map.of("template", queries.createTemplate(profile(request, response).getId(),
                input.title(), input.prompt(), input.params())));
    }

    @DeleteMapping("/api/templates/{id}")
    public Map<String, Object> deleteTemplate(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) {
        queries.deleteTemplate(profile(request, response).getId(), id); return Map.of("ok", true);
    }

    private ApiProfile profile(HttpServletRequest request, HttpServletResponse response) { return sessions.requireProfile(request, response); }
    public record TemplateRequest(String title, String prompt, Object params) {}
}
