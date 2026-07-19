package com.feng.system.module.image.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.feng.system.module.image.JsonbTypeHandler;
import com.feng.system.module.system.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_models", autoResultMap = true)
public class AiModel extends BaseEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long providerId;
    private String modelType;
    private String modelKey;
    private String displayName;
    private String upstreamModel;
    private String generationPath;
    private String editPath;
    private Integer asyncMode;
    private Integer maxCount;
    private Integer maxReferenceImages;
    private Integer supportsMask;
    @TableField(typeHandler = JsonbTypeHandler.class) private String parameterSchema;
    @TableField(typeHandler = JsonbTypeHandler.class) private String defaultParams;
    private BigDecimal unitPriceUsd;
    private String billingMode;
    private Integer enabled;
    private Integer modelSort;
}
