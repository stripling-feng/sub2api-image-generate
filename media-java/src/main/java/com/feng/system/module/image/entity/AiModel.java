package com.feng.system.module.image.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.feng.system.module.image.handler.JsonbTypeHandler;
import com.feng.system.module.system.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * AI 模型配置实体,对应表 media_ai_models。
 * 保存图片/视频生成模型的接入配置,如上游模型名、请求路径、参数约束与计费信息。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "media_ai_models", autoResultMap = true)
public class AiModel extends BaseEntity {
    @TableId(type = IdType.AUTO) private Long id;
    /** 所属服务商 ID,关联 media_model_providers 表 */
    private Long providerId;
    /** 模型类型,如 image(图片)/ video(视频) */
    private String modelType;
    /** 模型标识(对外暴露的唯一 key) */
    private String modelKey;
    /** 展示名称 */
    private String displayName;
    /** 调用上游接口时实际使用的模型名 */
    private String upstreamModel;
    /** 生成接口的请求路径 */
    private String generationPath;
    /** 是否异步模式(1=提交任务后轮询结果,0=同步返回) */
    private Integer asyncMode;
    /** 单次请求最大生成数量 */
    private Integer maxCount;
    /** 最大参考图数量 */
    private Integer maxReferenceImages;
    /** 是否支持蒙版(mask)编辑 */
    private Integer supportsMask;
    /** 参数校验规则(JSONB 格式的参数 schema) */
    @TableField(typeHandler = JsonbTypeHandler.class) private String parameterSchema;
    /** 默认参数(JSONB 格式) */
    @TableField(typeHandler = JsonbTypeHandler.class) private String defaultParams;
    /** 单价(美元) */
    private BigDecimal unitPriceUsd;
    /** 计费模式 */
    private String billingMode;
    /** 是否启用(1=启用,0=禁用) */
    private Integer enabled;
    /** 排序值 */
    private Integer modelSort;
}
