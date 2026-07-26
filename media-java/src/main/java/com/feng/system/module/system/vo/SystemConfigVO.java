package com.feng.system.module.system.vo;

import lombok.Data;

@Data
public class SystemConfigVO {
    private String siteName;
    private String copyright;
    private String defaultPassword;
    private Integer loginFailMaxAttempts;
    private Integer loginFailWindowMinutes;
    private Integer loginFailLockMinutes;
    private String uploadProvider;
    private String uploadServerBasePath;
    private String uploadServerBaseUrl;
    private String uploadOssEndpoint;
    private String uploadOssBucket;
    private String uploadOssAccessKeyId;
    private String uploadOssAccessKeySecret;
    private String uploadOssDomain;
    private String uploadMinioEndpoint;
    private String uploadMinioBucket;
    private String uploadMinioAccessKey;
    private String uploadMinioSecretKey;
    private String uploadMinioDomain;
    private String uploadR2Endpoint;
    private String uploadR2Bucket;
    private String uploadR2AccessKeyId;
    private String uploadR2AccessKeySecret;
    private String uploadR2Domain;
}
