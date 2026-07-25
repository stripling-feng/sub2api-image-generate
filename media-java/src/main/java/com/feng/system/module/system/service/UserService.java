package com.feng.system.module.system.service;

import com.feng.system.common.api.PageResult;
import com.feng.system.module.system.dto.UserDTO;
import com.feng.system.module.system.dto.UserQueryDTO;
import com.feng.system.module.system.vo.UserInfoVO;

import java.util.List;

public interface UserService {
    PageResult<UserInfoVO> page(UserQueryDTO queryDTO);
    UserInfoVO detail(Long id);
    void save(UserDTO dto);
    void update(UserDTO dto);
    void resetPassword(Long id, String password);
    void batchResetPassword(List<Long> ids, String password);
    void delete(Long id);
}
