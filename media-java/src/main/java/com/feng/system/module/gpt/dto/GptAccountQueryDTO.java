package com.feng.system.module.gpt.dto;

import lombok.Data;

@Data
public class GptAccountQueryDTO {
    private String keyword;
    private String planType;
    private String accountStatus;
    private String plusEligibility;
    private Boolean used;
    private long pageNum = 1;
    private long pageSize = 20;
}
