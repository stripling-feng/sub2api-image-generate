package com.feng.system.module.gpt.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class GptAccountRefreshDTO {
    @NotEmpty(message = "请选择需要刷新的账号")
    @Size(max = 50, message = "单次最多刷新 50 个账号")
    private List<Long> ids;
}
