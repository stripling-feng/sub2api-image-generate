package com.feng.system.module.system.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LoginVO {
    private String token;
    private UserInfoVO userInfo;
    private List<String> permissions;
    private List<MenuTreeVO> menuTree;
    private PublicSystemConfigVO siteConfig;
}
