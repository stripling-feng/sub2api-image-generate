package com.feng.system.module.gpt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class GptAccountImportDTO {
    @NotEmpty(message = "请至少输入一个 Access Token")
    @Size(max = 50, message = "单次最多导入 50 个 Access Token")
    private List<@NotBlank(message = "Access Token 不能为空") String> accessTokens;
}
