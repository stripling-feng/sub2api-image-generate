package com.feng.system.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feng.system.common.api.PageResult;
import com.feng.system.common.exception.BusinessException;
import com.feng.system.module.system.dto.UserDTO;
import com.feng.system.module.system.dto.UserQueryDTO;
import com.feng.system.module.system.entity.SysUser;
import com.feng.system.module.system.entity.SysUserRole;
import com.feng.system.module.system.mapper.SysUserMapper;
import com.feng.system.module.system.mapper.SysUserRoleMapper;
import com.feng.system.module.system.service.AuthService;
import com.feng.system.module.system.service.SystemConfigService;
import com.feng.system.module.system.service.UserService;
import com.feng.system.module.system.vo.UserInfoVO;
import cn.dev33.satoken.secure.SaSecureUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SystemConfigService systemConfigService;
    private final AuthService authService;

    @Override
    public PageResult<UserInfoVO> page(UserQueryDTO queryDTO) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0)
                .like(StringUtils.hasText(queryDTO.getUsername()), SysUser::getUsername, queryDTO.getUsername())
                .eq(queryDTO.getDeptId() != null, SysUser::getDeptId, queryDTO.getDeptId())
                .eq(queryDTO.getPostId() != null, SysUser::getPostId, queryDTO.getPostId())
                .orderByDesc(SysUser::getId);
        if (queryDTO.getRoleId() != null) {
            List<Long> userIds = userRoleMapper.selectUserIdsByRoleId(queryDTO.getRoleId());
            if (userIds == null || userIds.isEmpty()) {
                return PageResult.empty(queryDTO.getPageNum(), queryDTO.getPageSize());
            }
            wrapper.in(SysUser::getId, userIds);
        }
        Page<SysUser> page = userMapper.selectPage(new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize()), wrapper);
        List<UserInfoVO> records = page.getRecords().stream().map(this::toVOBase).toList();
        // Batch query role IDs for all users in one query instead of N+1
        Map<Long, List<Long>> userRoleMap = batchGetRoleIds(
                records.stream().map(UserInfoVO::getId).toList());
        for (UserInfoVO vo : records) {
            vo.setRoleIds(userRoleMap.getOrDefault(vo.getId(), Collections.emptyList()));
        }
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    @Override
    public UserInfoVO detail(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null || user.getDeleted() == 1) {
            throw new BusinessException("用户不存在");
        }
        return toVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(UserDTO dto) {
        long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername())
                .eq(SysUser::getDeleted, 0));
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }
        validatePhoneUnique(dto.getPhone(), null);
        SysUser user = new SysUser();
        BeanUtils.copyProperties(dto, user, "password");
        user.setPassword(SaSecureUtil.sha256(dto.getUsername() + "#" + systemConfigService.getDefaultPassword()));
        userMapper.insert(user);
        dto.setId(user.getId());
        saveRoles(user.getId(), dto.getRoleIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UserDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("用户ID不能为空");
        }
        SysUser user = userMapper.selectById(dto.getId());
        if (user == null || user.getDeleted() == 1) {
            throw new BusinessException("用户不存在");
        }
        validatePhoneUnique(dto.getPhone(), user.getId());
        BeanUtils.copyProperties(dto, user, "password", "username");
        userMapper.updateById(user);
        userRoleMapper.deleteByUserId(user.getId());
        saveRoles(user.getId(), dto.getRoleIds());
        evictAuthCache(dto.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String password) {
        SysUser user = userMapper.selectById(id);
        if (user == null || user.getDeleted() == 1) {
            throw new BusinessException("用户不存在");
        }
        String finalPassword = (password != null && !password.isEmpty()) ? password : systemConfigService.getDefaultPassword();
        user.setPassword(SaSecureUtil.sha256(user.getUsername() + "#" + finalPassword));
        userMapper.updateById(user);
        evictAuthCache(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchResetPassword(List<Long> ids, String password) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择需要重置密码的用户");
        }
        String rawPassword = (password != null && !password.isEmpty()) ? password : systemConfigService.getDefaultPassword();
        List<SysUser> users = userMapper.selectBatchIds(ids);
        for (SysUser user : users) {
            user.setPassword(SaSecureUtil.sha256(user.getUsername() + "#" + rawPassword));
            userMapper.updateById(user);
            evictAuthCache(user.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        userRoleMapper.deleteByUserId(id);
        userMapper.deleteById(id);
    }

    private Map<Long, List<Long>> batchGetRoleIds(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<SysUserRole> mappings = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId, userIds));
        return mappings.stream().collect(Collectors.groupingBy(
                SysUserRole::getUserId,
                Collectors.mapping(SysUserRole::getRoleId, Collectors.toList())));
    }

    private void saveRoles(Long userId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        List<SysUserRole> userRoles = roleIds.stream().map(roleId -> {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            return ur;
        }).toList();
        for (SysUserRole ur : userRoles) {
            userRoleMapper.insert(ur);
        }
    }

    private void validatePhoneUnique(String phone, Long excludeUserId) {
        if (!StringUtils.hasText(phone)) {
            return;
        }
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone)
                .eq(SysUser::getDeleted, 0);
        if (excludeUserId != null) {
            wrapper.ne(SysUser::getId, excludeUserId);
        }
        long count = userMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException("手机号已存在");
        }
    }

    private void evictAuthCache(Long userId) {
        authService.refreshUserSession(userId);
    }

    private UserInfoVO toVOBase(SysUser user) {
        UserInfoVO vo = new UserInfoVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setDeptId(user.getDeptId());
        vo.setPostId(user.getPostId());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }

    private UserInfoVO toVO(SysUser user) {
        UserInfoVO vo = toVOBase(user);
        vo.setRoleIds(userRoleMapper.selectRoleIdsByUserId(user.getId()));
        return vo;
    }
}
