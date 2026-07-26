package com.feng.system.module.video.controller;

import com.feng.system.common.api.ApiResponse;
import com.feng.system.module.image.service.ImageSessionService;
import com.feng.system.module.image.dto.GenerationAcceptedResponse;
import com.feng.system.module.image.dto.UploadResponse;
import com.feng.system.module.video.dto.VideoGenerationRequest;
import com.feng.system.module.video.service.VideoGenerationService;
import com.feng.system.module.video.service.VideoMaterialUploadService;
import com.feng.system.module.media.service.MediaTaskAuditService;

import com.feng.system.module.image.entity.ApiProfile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 视频生成入口控制器：接收视频生成请求与素材上传请求,
 * 校验会话/API Key 后交由 VideoGenerationService 创建异步生成任务。
 */
@RestController
public class VideoGenerationController {
    private final ImageSessionService sessions;
    private final VideoGenerationService generation;
    private final VideoMaterialUploadService uploads;
    private final MediaTaskAuditService audit;

    /** 便捷构造(无审计服务),主要用于测试场景。 */
    public VideoGenerationController(ImageSessionService sessions, VideoGenerationService generation,
                                     VideoMaterialUploadService uploads) {
        this(sessions, generation, uploads, null);
    }

    @Autowired
    public VideoGenerationController(ImageSessionService sessions, VideoGenerationService generation,
                                     VideoMaterialUploadService uploads, MediaTaskAuditService audit) {
        this.sessions = sessions;
        this.generation = generation;
        this.uploads = uploads;
        this.audit = audit;
    }

    /**
     * POST /api/videos/generate:接收 JSON 视频生成请求,创建异步任务并返回 requestId 与任务数。
     */
    @PostMapping(value = "/api/videos/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<GenerationAcceptedResponse> json(@RequestBody VideoGenerationRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        ApiProfile profile = profile(request, response);
        VideoGenerationService.Accepted accepted = generation.generate(profile, input, List.of(), null, null);
        ApiResponse<GenerationAcceptedResponse> result = accepted(accepted);
        if (audit != null) audit.recordSystemResponse(profile.getEncryptedKey(), "VIDEO", accepted.requestId(), result);
        return result;
    }

    /**
     * POST /api/videos/uploads:上传视频生成用的参考素材(视频/音频/图片),返回可公开访问的 URL。
     */
    @PostMapping(value = "/api/videos/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadResponse> uploadMaterial(@RequestParam("file") MultipartFile file,
            HttpServletRequest request, HttpServletResponse response) {
        profile(request, response);
        VideoMaterialUploadService.Uploaded uploaded = uploads.upload(file, request);
        return ApiResponse.success(new UploadResponse(uploaded.url(), uploaded.mimeType(), uploaded.sizeBytes()));
    }

    private ApiResponse<GenerationAcceptedResponse> accepted(VideoGenerationService.Accepted value) {
        return ApiResponse.success(new GenerationAcceptedResponse(value.requestId(), value.count()));
    }
    private ApiProfile profile(HttpServletRequest request, HttpServletResponse response) {
        return sessions.requireProfile(request, response);
    }
}
