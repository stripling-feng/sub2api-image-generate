package com.feng.system.module.image.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * API 会话实体,对应表 api_sessions。
 * 记录基于 API 配置创建的会话令牌及其有效期,用于会话鉴权。
 */
@Data
@TableName("media_api_sessions")
public class ApiSession {
    @TableId(type = IdType.INPUT) private String id;
    /** 会话令牌的哈希值 */
    @TableField("\"tokenHash\"") private String tokenHash;
    /** 关联的 API 配置 ID(api_profiles 表) */
    @TableField("\"profileId\"") private String profileId;
    /** 会话过期时间 */
    @TableField("\"expiresAt\"") private LocalDateTime expiresAt;
    @TableField("\"createdAt\"") private LocalDateTime createdAt;
}
