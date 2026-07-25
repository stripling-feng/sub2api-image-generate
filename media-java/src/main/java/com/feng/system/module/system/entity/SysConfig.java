package com.feng.system.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_config")
public class SysConfig extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String configKey;
    private String configName;
    private String configValue;
    private String configGroup;
    private String remark;
}
