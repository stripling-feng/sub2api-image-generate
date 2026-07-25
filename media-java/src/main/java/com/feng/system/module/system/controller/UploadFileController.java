package com.feng.system.module.system.controller;

import com.feng.system.common.api.ApiResponse;
import com.feng.system.common.api.PageResult;
import com.feng.system.common.log.BusinessOperationType;
import com.feng.system.common.log.OperLog;
import com.feng.system.common.ratelimit.RateLimit;
import com.feng.system.module.system.dto.UploadFileQueryDTO;
import com.feng.system.module.system.service.UploadFileService;
import com.feng.system.module.system.vo.UploadFileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/system/upload-files")
@RequiredArgsConstructor
public class UploadFileController {

    private final UploadFileService uploadFileService;

    @GetMapping
    @SaCheckPermission("system:upload:list")
    public ApiResponse<PageResult<UploadFileVO>> list(UploadFileQueryDTO queryDTO) {
        return ApiResponse.success(uploadFileService.page(queryDTO));
    }

    @PostMapping
    @SaCheckPermission("system:upload:add")
    @RateLimit(maxRequests = 10, windowSeconds = 60, limitType = RateLimit.LimitType.PER_USER, message = "上传请求过于频繁，请稍后再试")
    @OperLog(name = "上传文件", type = BusinessOperationType.INSERT)
    public ApiResponse<UploadFileVO> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success("上传成功", uploadFileService.upload(file));
    }

    @GetMapping("/{id}/content")
    @SaCheckPermission("system:upload:list")
    public ResponseEntity<Resource> content(@PathVariable Long id,
                                            @RequestParam(defaultValue = "false") boolean download) {
        return uploadFileService.getContent(id, download);
    }
}
