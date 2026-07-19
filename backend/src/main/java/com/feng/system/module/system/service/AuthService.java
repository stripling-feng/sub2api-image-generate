package com.feng.system.module.system.service;

import com.feng.system.module.system.dto.LoginDTO;
import com.feng.system.module.system.dto.ChangePasswordDTO;
import com.feng.system.module.system.vo.LoginVO;

public interface AuthService {
    LoginVO login(LoginDTO dto);
    LoginVO current();
    void changePassword(ChangePasswordDTO dto);
    void refreshUserSession(Long userId);
}
