package com.feng.system.module.video;

import com.feng.system.module.image.ImageSessionService;
import com.feng.system.module.image.entity.ApiProfile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class VideoQueryController {
    private final ImageSessionService sessions;
    private final VideoQueryService queries;

    @GetMapping("/api/videos/history")
    public Map<String, Object> history(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize, HttpServletRequest request, HttpServletResponse response) {
        return queries.history(profile(request, response).getId(), page, pageSize);
    }
    @GetMapping("/api/videos/results/{requestId}")
    public Map<String, Object> results(@PathVariable String requestId, HttpServletRequest request, HttpServletResponse response) {
        return Map.of("jobs", queries.results(profile(request, response).getId(), requestId));
    }
    @DeleteMapping("/api/videos/{id}")
    public Map<String, Object> deleteVideo(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) {
        queries.deleteVideo(profile(request, response).getId(), id); return Map.of("ok", true);
    }
    @DeleteMapping("/api/video-jobs/{id}")
    public Map<String, Object> deleteJob(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) {
        queries.deleteJob(profile(request, response).getId(), id); return Map.of("ok", true);
    }
    @DeleteMapping("/api/video-jobs")
    public Map<String, Object> deleteJobs(HttpServletRequest request, HttpServletResponse response) {
        return Map.of("ok", true, "deletedCount", queries.deleteJobs(profile(request, response).getId()));
    }
    private ApiProfile profile(HttpServletRequest request, HttpServletResponse response) {
        return sessions.requireProfile(request, response);
    }
}
