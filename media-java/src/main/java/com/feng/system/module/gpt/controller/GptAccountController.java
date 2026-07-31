package com.feng.system.module.gpt.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.feng.system.common.api.ApiResponse;
import com.feng.system.common.api.PageResult;
import com.feng.system.common.log.BusinessOperationType;
import com.feng.system.common.log.OperLog;
import com.feng.system.common.submit.PreventDuplicateSubmit;
import com.feng.system.module.gpt.dto.GptAccountDeleteBatchDTO;
import com.feng.system.module.gpt.dto.GptAccountImportDTO;
import com.feng.system.module.gpt.dto.GptAccountQueryDTO;
import com.feng.system.module.gpt.dto.GptAccountRefreshDTO;
import com.feng.system.module.gpt.dto.GptAccountUsedDTO;
import com.feng.system.module.gpt.service.GptAccountService;
import com.feng.system.module.gpt.vo.GptAccountImportResultVO;
import com.feng.system.module.gpt.vo.GptAccountVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/gpt/accounts")
@RequiredArgsConstructor
public class GptAccountController {
    private final GptAccountService service;

    @GetMapping
    @SaCheckPermission("gpt:account:list")
    public ApiResponse<PageResult<GptAccountVO>> list(GptAccountQueryDTO query) {
        return ApiResponse.success(service.page(query));
    }

    @PostMapping("/import")
    @SaCheckPermission("gpt:account:import")
    @PreventDuplicateSubmit
    public ApiResponse<GptAccountImportResultVO> importTokens(@Valid @RequestBody GptAccountImportDTO dto) {
        return ApiResponse.success(service.importTokens(dto.getAccessTokens()));
    }

    @PostMapping("/{id}/refresh")
    @SaCheckPermission("gpt:account:refresh")
    public ApiResponse<GptAccountVO> refresh(@PathVariable Long id) {
        return ApiResponse.success(service.refresh(id));
    }

    @PostMapping("/refresh")
    @SaCheckPermission("gpt:account:refresh")
    public ApiResponse<List<GptAccountVO>> refreshBatch(@Valid @RequestBody GptAccountRefreshDTO dto) {
        return ApiResponse.success(service.refreshBatch(dto.getIds()));
    }

    @PutMapping("/{id}/used")
    @SaCheckPermission("gpt:account:refresh")
    public ApiResponse<GptAccountVO> updateUsed(@PathVariable Long id, @Valid @RequestBody GptAccountUsedDTO dto) {
        return ApiResponse.success(service.updateUsed(id, dto.getUsed()));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("gpt:account:remove")
    @PreventDuplicateSubmit
    @OperLog(name = "删除 GPT 账号", type = BusinessOperationType.DELETE)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success("删除成功", null);
    }

    @DeleteMapping
    @SaCheckPermission("gpt:account:remove")
    @PreventDuplicateSubmit
    @OperLog(name = "批量删除 GPT 账号", type = BusinessOperationType.DELETE)
    public ApiResponse<Void> deleteBatch(@Valid @RequestBody GptAccountDeleteBatchDTO dto) {
        int count = service.deleteBatch(dto.getIds());
        return ApiResponse.success("已删除 " + count + " 个账号", null);
    }
}
