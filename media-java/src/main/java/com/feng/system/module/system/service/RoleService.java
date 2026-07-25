package com.feng.system.module.system.service;

import com.feng.system.common.api.PageResult;
import com.feng.system.module.system.dto.RoleDTO;
import com.feng.system.module.system.dto.RoleQueryDTO;
import com.feng.system.module.system.entity.SysRole;

import java.util.List;

public interface RoleService {
    PageResult<SysRole> page(RoleQueryDTO queryDTO);
    List<SysRole> options();
    void save(RoleDTO dto);
    void update(RoleDTO dto);
    void delete(Long id);
    List<Long> menuIds(Long roleId);
}
