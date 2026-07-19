package com.feng.system.module.system.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserInfoVO {
    private Long id;
    private String username;
    private String nickname;
    private String phone;
    private String email;
    private Long deptId;
    private Long postId;
    private Integer status;
    private List<Long> roleIds;
    private LocalDateTime createTime;
}
