package com.feng.system.module.image.controller;

import com.feng.system.common.api.ApiResponse;
import com.feng.system.module.image.dto.DeleteJobsResponse;
import com.feng.system.module.image.dto.DeleteResponse;
import com.feng.system.module.image.dto.HistoryResponse;
import com.feng.system.module.image.dto.JobsResponse;
import com.feng.system.module.image.service.ImageQueryService;
import com.feng.system.module.image.service.ImageSessionService;

import com.feng.system.module.image.entity.ApiProfile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 图片任务查询控制器。
 * 提供当前会话密钥下的历史分页、按请求 ID 查询结果、批量/单条删除任务等接口。
 */
@RestController
@RequiredArgsConstructor
public class ImageQueryController {
    private final ImageSessionService sessions;
    private final ImageQueryService queries;

    /**
     * GET /api/images/history:分页查询当前密钥下的图片生成历史。
     *
     * @param page     页码,从 1 开始
     * @param pageSize 每页条数
     */
    @GetMapping("/api/images/history")
    public ApiResponse<HistoryResponse> history(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int pageSize,
                                      HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.success(history(queries.history(profile(request, response).getEncryptedKey(), page, pageSize)));
    }

    /**
     * GET /api/images/results/{requestId}:查询某次生成请求下的全部任务结果。
     */
    @GetMapping("/api/images/results/{requestId}")
    public ApiResponse<JobsResponse> results(@PathVariable String requestId, HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.success(new JobsResponse(queries.results(profile(request, response).getEncryptedKey(), requestId)));
    }

    /**
     * GET /api/images/{requestId}:面向用户对接的精简批次结果查询。
     */
    @GetMapping("/api/images/{requestId}")
    public ApiResponse<List<?>> compactResults(@PathVariable String requestId, HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.success(queries.compactResults(profile(request, response).getEncryptedKey(), requestId));
    }

    /**
     * GET /api/images/{id}/download:以附件形式下载当前密钥下的生成图片。
     */
    @GetMapping("/api/images/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) {
        ImageQueryService.DownloadedImage image = queries.download(profile(request, response).getEncryptedKey(), id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(image.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(image.bytes());
    }

    /**
     * DELETE /api/jobs:删除当前密钥下的全部任务,返回删除条数。
     */
    @DeleteMapping("/api/jobs")
    public ApiResponse<DeleteJobsResponse> deleteJobs(HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.success(new DeleteJobsResponse(true, queries.deleteJobs(profile(request, response).getEncryptedKey())));
    }

    /**
     * DELETE /api/jobs/{id}:删除当前密钥下指定 ID 的单个任务。
     */
    @DeleteMapping("/api/jobs/{id}")
    public ApiResponse<DeleteResponse> deleteJob(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) {
        queries.deleteJob(profile(request, response).getEncryptedKey(), id); return ApiResponse.success(new DeleteResponse(true));
    }

    // 将服务层返回的 Map 结构转换为强类型的分页响应,缺失字段取默认值
    private HistoryResponse history(Map<String, Object> value) {
        return new HistoryResponse((List<?>) value.getOrDefault("jobs", List.of()),
                number(value.get("page")), number(value.get("pageSize")), longNumber(value.get("total")),
                longNumber(value.get("totalPages")));
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private long longNumber(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    // 从请求中解析并校验会话对应的 API 档案(未通过时由会话服务抛出异常)
    private ApiProfile profile(HttpServletRequest request, HttpServletResponse response) { return sessions.requireProfile(request, response); }
}
