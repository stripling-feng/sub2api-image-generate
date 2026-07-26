package com.feng.system.module.image.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.feng.system.module.system.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模型服务商实体,对应表 media_model_providers。
 * 保存上游服务商的接入地址与图片/视频接口的 API Key。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("media_model_providers")
public class ModelProvider extends BaseEntity {
    @TableId(type = IdType.AUTO) private Long id;
    /** 服务商名称 */
    private String name;
    /** 服务商 API 基础地址 */
    private String baseUrl;
    /** 图片生成接口使用的 API Key */
    private String imageApiKey;
    /** 视频生成接口使用的 API Key */
    private String videoApiKey;
    /** 是否启用(1=启用,0=禁用) */
    private Integer enabled;
    /** 排序值 */
    private Integer providerSort;
}
