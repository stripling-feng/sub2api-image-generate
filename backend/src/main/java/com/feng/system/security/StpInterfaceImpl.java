package com.feng.system.security;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.feng.system.module.system.mapper.SysMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final SysMenuMapper menuMapper;

    private static final String PERMISSION_KEY = "user_permissions";

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return StpUtil.getSessionByLoginId(loginId).get(PERMISSION_KEY, () ->
                menuMapper.selectPermissionsByUserId(Long.parseLong(loginId.toString()))
        );
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return Collections.emptyList();
    }
}
