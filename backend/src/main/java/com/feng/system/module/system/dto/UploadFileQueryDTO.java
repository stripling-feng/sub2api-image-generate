package com.feng.system.module.system.dto;

import lombok.Data;

@Data
public class UploadFileQueryDTO {
    private Long pageNum = 1L;
    private Long pageSize = 10L;
    private String originalName;
    private String currentName;
    private String fileType;
}
