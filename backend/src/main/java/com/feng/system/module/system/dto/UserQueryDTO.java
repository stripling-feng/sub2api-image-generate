package com.feng.system.module.system.dto;

import lombok.Data;

@Data
public class UserQueryDTO {
    private Long pageNum = 1L;
    private Long pageSize = 10L;
    private String username;
    private Long deptId;
    private Long postId;
    private Long roleId;
}
