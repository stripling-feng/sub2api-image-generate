package com.feng.system.module.image.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * API 接入配置实体,对应表 api_profiles。
 * 保存用户配置的上游 API 地址与加密后的 API Key,用于直连模式调用。
 */
@Data
@TableName("media_api_profiles")
public class ApiProfile {
    @TableId(type = IdType.INPUT)
    private String id;
    /** 上游 API 基础地址 */
    @TableField("\"baseUrl\"") private String baseUrl;
    /** API Key 的哈希值,用于查找/比对而不暴露原文 */
    @TableField("\"keyHash\"") private String keyHash;
    /** 加密存储的 API Key 原文 */
    @TableField("\"encryptedKey\"") private String encryptedKey;
    /** 默认使用的模型 */
    @TableField("\"defaultModel\"") private String defaultModel;
    @TableField("\"createdAt\"") private LocalDateTime createdAt;
    @TableField("\"updatedAt\"") private LocalDateTime updatedAt;
}
