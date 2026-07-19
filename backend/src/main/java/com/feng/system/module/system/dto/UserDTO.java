package com.feng.system.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class UserDTO {
    private Long id;
    @NotBlank(message = "用户名不能为空")
    private String username;
    private String password;
    @NotBlank(message = "昵称不能为空")
    private String nickname;
    private String phone;
    private String email;
    @NotNull(message = "部门不能为空")
    private Long deptId;
    @NotNull(message = "岗位不能为空")
    private Long postId;
    @NotNull(message = "状态不能为空")
    private Integer status;
    private List<Long> roleIds;
}
