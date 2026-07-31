package com.feng.system.module.gpt.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class GptAccountDeleteBatchDTO {
    @NotEmpty(message = "请选择需要删除的账号")
    @Size(max = 50, message = "单次最多删除 50 个账号")
    private List<Long> ids;
}
