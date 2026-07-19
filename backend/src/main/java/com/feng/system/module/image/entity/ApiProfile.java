package com.feng.system.module.image.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("api_profiles")
public class ApiProfile {
    @TableId(type = IdType.INPUT)
    private String id;
    @TableField("\"baseUrl\"") private String baseUrl;
    @TableField("\"keyHash\"") private String keyHash;
    @TableField("\"encryptedKey\"") private String encryptedKey;
    @TableField("\"defaultModel\"") private String defaultModel;
    @TableField("\"createdAt\"") private LocalDateTime createdAt;
    @TableField("\"updatedAt\"") private LocalDateTime updatedAt;
}
