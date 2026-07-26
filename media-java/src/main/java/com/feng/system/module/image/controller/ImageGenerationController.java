package com.feng.system.module.image.controller;

import com.feng.system.common.api.ApiResponse;
import com.feng.system.module.image.dto.GenerationAcceptedResponse;
import com.feng.system.module.image.dto.ImageGenerateRequest;
import com.feng.system.module.image.dto.UploadResponse;
import com.feng.system.module.image.service.ImageGenerationService;
import com.feng.system.module.image.service.ImageReferenceUploadService;
import com.feng.system.module.image.service.ImageSessionService;
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
 * 图片生成入口控制器。
 * 负责接收图片生成请求(JSON)与参考图上传请求,校验会话密钥后交由生成服务异步处理。
 */
@RestController
public class ImageGenerationController {
    private final ImageSessionService sessions;
    private final ImageGenerationService generation;
    private final ImageReferenceUploadService uploads;
    private final MediaTaskAuditService audit;

    /**
     * 兼容旧调用方的构造器,不注入审计服务(audit 为 null 时跳过审计记录)。
     */
    public ImageGenerationController(ImageSessionService sessions, ImageGenerationService generation,
                                     ImageReferenceUploadService uploads) {
        this(sessions, generation, uploads, null);
    }

    @Autowired
    public ImageGenerationController(ImageSessionService sessions, ImageGenerationService generation,
                                     ImageReferenceUploadService uploads, MediaTaskAuditService audit) {
        this.sessions = sessions;
        this.generation = generation;
        this.uploads = uploads;
        this.audit = audit;
    }

    /**
     * POST /api/images/generate:提交 JSON 格式的图片生成请求,任务受理后立即返回请求 ID 与任务数量。
     */
    @PostMapping(value = "/api/images/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<GenerationAcceptedResponse> json(@RequestBody ImageGenerateRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        ApiProfile profile = profile(request, response);
        ImageGenerationService.Accepted accepted = generation.generate(profile, input, List.of(), null);
        ApiResponse<GenerationAcceptedResponse> result = accepted(accepted);
        // 审计服务可选注入,存在时记录系统受理响应
        if (audit != null) audit.recordSystemResponse(profile.getEncryptedKey(), "IMAGE", accepted.requestId(), result);
        return result;
    }

    /**
     * POST /api/images/uploads:上传参考图文件,返回可供后续生成请求引用的 URL。
     */
    @PostMapping(value = "/api/images/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadResponse> uploadReference(@RequestParam("file") MultipartFile file,
            HttpServletRequest request, HttpServletResponse response) {
        // 仅做鉴权,不使用返回的档案信息
        profile(request, response);
        ImageReferenceUploadService.Uploaded uploaded = uploads.upload(file, request);
        return ApiResponse.success(new UploadResponse(uploaded.url(), uploaded.mimeType(), uploaded.sizeBytes()));
    }

    private ApiResponse<GenerationAcceptedResponse> accepted(ImageGenerationService.Accepted value) {
        return ApiResponse.success(new GenerationAcceptedResponse(value.requestId(), value.count()));
    }
    // 从请求中解析并校验会话对应的 API 档案(未通过时由会话服务抛出异常)
    private ApiProfile profile(HttpServletRequest request, HttpServletResponse response) { return sessions.requireProfile(request, response); }
}
