package com.feng.system.module.system.controller;

import com.feng.system.common.api.ApiResponse;
import com.feng.system.common.api.PageResult;
import com.feng.system.common.log.BusinessOperationType;
import com.feng.system.common.log.OperLog;
import com.feng.system.common.submit.PreventDuplicateSubmit;
import com.feng.system.module.system.dto.PostDTO;
import com.feng.system.module.system.dto.PostQueryDTO;
import com.feng.system.module.system.entity.SysPost;
import com.feng.system.module.system.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping
    @SaCheckPermission("system:post:list")
    public ApiResponse<PageResult<SysPost>> list(PostQueryDTO queryDTO) {
        return ApiResponse.success(postService.page(queryDTO));
    }

    @PostMapping
    @SaCheckPermission("system:post:add")
    @PreventDuplicateSubmit
    @OperLog(name = "新增岗位", type = BusinessOperationType.INSERT)
    public ApiResponse<Void> save(@Valid @RequestBody PostDTO dto) {
        postService.save(dto);
        return ApiResponse.success("新增成功", null);
    }

    @PutMapping("/{id}")
    @SaCheckPermission("system:post:edit")
    @PreventDuplicateSubmit
    @OperLog(name = "编辑岗位", type = BusinessOperationType.UPDATE)
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody PostDTO dto) {
        dto.setId(id);
        postService.update(dto);
        return ApiResponse.success("修改成功", null);
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("system:post:remove")
    @PreventDuplicateSubmit
    @OperLog(name = "删除岗位", type = BusinessOperationType.DELETE)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        postService.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
