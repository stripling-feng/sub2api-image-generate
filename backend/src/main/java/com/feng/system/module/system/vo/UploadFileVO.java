package com.feng.system.module.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UploadFileVO {
    private Long id;
    private String originalName;
    private String currentName;
    private String filePath;
    private Long fileSize;
    private String fileType;
    private String md5Value;
    private LocalDateTime createTime;
}
