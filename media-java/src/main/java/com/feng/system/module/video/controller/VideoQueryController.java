package com.feng.system.module.video.controller;

import com.feng.system.common.api.ApiResponse;
import com.feng.system.module.image.dto.DeleteJobsResponse;
import com.feng.system.module.image.dto.DeleteResponse;
import com.feng.system.module.image.dto.HistoryResponse;
import com.feng.system.module.image.dto.JobsResponse;
import com.feng.system.module.video.service.VideoQueryService;

import com.feng.system.module.image.service.ImageSessionService;
import com.feng.system.module.image.entity.ApiProfile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 视频任务查询控制器:提供历史记录分页查询、按 requestId 查询结果,
 * 以及删除单个/全部视频任务的接口,均按调用方 API Key 进行数据隔离。
 */
@RestController
@RequiredArgsConstructor
public class VideoQueryController {
    private final ImageSessionService sessions;
    private final VideoQueryService queries;

    /**
     * GET /api/videos/history:分页查询当前 API Key 的视频任务历史。
     */
    @GetMapping("/api/videos/history")
    public ApiResponse<HistoryResponse> history(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize, HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.success(history(queries.history(profile(request, response).getEncryptedKey(), page, pageSize)));
    }
    /**
     * GET /api/videos/results/{requestId}:按生成请求 ID 查询该批次的全部视频任务及结果。
     */
    @GetMapping("/api/videos/results/{requestId}")
    public ApiResponse<JobsResponse> results(@PathVariable String requestId, HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.success(new JobsResponse(queries.results(profile(request, response).getEncryptedKey(), requestId)));
    }
    /**
     * DELETE /api/video-jobs/{id}:删除指定的视频任务及其产物文件(进行中的任务不允许删除)。
     */
    @DeleteMapping("/api/video-jobs/{id}")
    public ApiResponse<DeleteResponse> deleteJob(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) {
        queries.deleteJob(profile(request, response).getEncryptedKey(), id); return ApiResponse.success(new DeleteResponse(true));
    }
    /**
     * DELETE /api/video-jobs:批量删除当前 API Key 下所有非进行中的视频任务,返回删除数量。
     */
    @DeleteMapping("/api/video-jobs")
    public ApiResponse<DeleteJobsResponse> deleteJobs(HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.success(new DeleteJobsResponse(true, queries.deleteJobs(profile(request, response).getEncryptedKey())));
    }
    private HistoryResponse history(Map<String, Object> value) {
        return new HistoryResponse((List<?>) value.getOrDefault("jobs", List.of()),
                number(value.get("page")), number(value.get("pageSize")), longNumber(value.get("total")),
                longNumber(value.get("totalPages")));
    }
    private int number(Object value) { return value instanceof Number number ? number.intValue() : 0; }
    private long longNumber(Object value) { return value instanceof Number number ? number.longValue() : 0L; }
    private ApiProfile profile(HttpServletRequest request, HttpServletResponse response) {
        return sessions.requireProfile(request, response);
    }
}
