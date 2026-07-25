package com.feng.system.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_upload_file")
public class SysUploadFile extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String originalName;
    private String currentName;
    private Long fileSize;
    private String fileType;
    private String md5Value;
    private String filePath;
}
