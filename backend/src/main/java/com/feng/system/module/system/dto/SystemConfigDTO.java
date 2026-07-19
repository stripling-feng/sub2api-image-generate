package com.feng.system.module.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SystemConfigDTO {

    @NotBlank(message = "网站名称不能为空")
    private String siteName;

    @NotBlank(message = "版权信息不能为空")
    private String copyright;

    @NotBlank(message = "默认密码不能为空")
    private String defaultPassword;

    @Min(value = 1, message = "登录失败限制次数不能小于1")
    @Max(value = 20, message = "登录失败限制次数不能大于20")
    private Integer loginFailMaxAttempts;

    @Min(value = 1, message = "登录失败统计时间不能小于1分钟")
    @Max(value = 1440, message = "登录失败统计时间不能大于1440分钟")
    private Integer loginFailWindowMinutes;

    @Min(value = 1, message = "登录锁定时间不能小于1分钟")
    @Max(value = 1440, message = "登录锁定时间不能大于1440分钟")
    private Integer loginFailLockMinutes;

    @NotBlank(message = "上传存储方式不能为空")
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
}
