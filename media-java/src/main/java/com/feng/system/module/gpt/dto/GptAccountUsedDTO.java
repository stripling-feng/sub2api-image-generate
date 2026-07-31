package com.feng.system.module.gpt.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GptAccountUsedDTO {
    @NotNull(message = "使用状态不能为空")
    private Boolean used;
}
