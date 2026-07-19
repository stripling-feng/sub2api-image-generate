package com.feng.system.module.image.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("api_sessions")
public class ApiSession {
    @TableId(type = IdType.INPUT) private String id;
    @TableField("\"tokenHash\"") private String tokenHash;
    @TableField("\"profileId\"") private String profileId;
    @TableField("\"expiresAt\"") private LocalDateTime expiresAt;
    @TableField("\"createdAt\"") private LocalDateTime createdAt;
}
