package com.feng.system.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feng.system.common.api.PageResult;
import com.feng.system.common.exception.BusinessException;
import com.feng.system.module.system.dto.RoleDTO;
import com.feng.system.module.system.dto.RoleQueryDTO;
import com.feng.system.module.system.entity.SysRole;
import com.feng.system.module.system.entity.SysRoleMenu;
import com.feng.system.module.system.mapper.SysRoleMapper;
import com.feng.system.module.system.mapper.SysRoleMenuMapper;
import com.feng.system.module.system.mapper.SysUserRoleMapper;
import com.feng.system.module.system.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResult<SysRole> page(RoleQueryDTO queryDTO) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getDeleted, 0)
                .like(queryDTO.getRoleName() != null && !queryDTO.getRoleName().isBlank(), SysRole::getRoleName, queryDTO.getRoleName())
                .like(queryDTO.getRoleKey() != null && !queryDTO.getRoleKey().isBlank(), SysRole::getRoleKey, queryDTO.getRoleKey())
                .orderByAsc(SysRole::getRoleSort)
                .orderByDesc(SysRole::getId);
        Page<SysRole> page = roleMapper.selectPage(new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize()), wrapper);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SysRole> options() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getDeleted, 0)
                .orderByAsc(SysRole::getRoleSort));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(RoleDTO dto) {
        SysRole role = new SysRole();
        BeanUtils.copyProperties(dto, role);
        roleMapper.insert(role);
        dto.setId(role.getId());
        saveMenus(role.getId(), dto.getMenuIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(RoleDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("角色ID不能为空");
        }
        SysRole role = new SysRole();
        BeanUtils.copyProperties(dto, role);
        roleMapper.updateById(role);
        roleMenuMapper.deleteByRoleId(dto.getId());
        saveMenus(dto.getId(), dto.getMenuIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (!userRoleMapper.selectUserIdsByRoleId(id).isEmpty()) {
            throw new BusinessException("该角色已被用户使用，不能删除");
        }
        roleMenuMapper.deleteByRoleId(id);
        roleMapper.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> menuIds(Long roleId) {
        return roleMenuMapper.selectMenuIdsByRoleId(roleId);
    }

    private void saveMenus(Long roleId, List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        List<SysRoleMenu> roleMenus = menuIds.stream().map(menuId -> {
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(roleId);
            rm.setMenuId(menuId);
            return rm;
        }).toList();
        roleMenuMapper.batchInsert(roleMenus);
    }
}
