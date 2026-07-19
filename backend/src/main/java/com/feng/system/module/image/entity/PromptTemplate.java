package com.feng.system.module.image.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.feng.system.module.image.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "prompt_templates", autoResultMap = true)
public class PromptTemplate {
    @TableId(type = IdType.INPUT) private String id;
    @TableField("\"profileId\"") private String profileId;
    private String title;
    private String prompt;
    @TableField(typeHandler = JsonbTypeHandler.class) private String params;
    @TableField("\"createdAt\"") private LocalDateTime createdAt;
    @TableField("\"updatedAt\"") private LocalDateTime updatedAt;
}
