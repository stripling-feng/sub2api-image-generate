package com.feng.system.module.image.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.feng.system.module.system.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("model_providers")
public class ModelProvider extends BaseEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String name;
    private String baseUrl;
    private String imageApiKey;
    private String videoApiKey;
    private Integer enabled;
    private Integer providerSort;
}
