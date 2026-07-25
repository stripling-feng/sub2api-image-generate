package com.feng.system.module.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PublicDocsConfigVO {
    private DocFile image;
    private DocFile video;

    @Data
    public static class DocFile {
        private String key;
        private String configKey;
        private Long fileId;
        private String originalName;
        private String currentName;
        private String fileType;
        private Long fileSize;
        private LocalDateTime updatedAt;
    }
}
