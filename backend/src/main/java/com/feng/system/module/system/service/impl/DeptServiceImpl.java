package com.feng.system.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feng.system.common.exception.BusinessException;
import com.feng.system.module.system.dto.DeptDTO;
import com.feng.system.module.system.entity.SysDept;
import com.feng.system.module.system.entity.SysUser;
import com.feng.system.module.system.mapper.SysDeptMapper;
import com.feng.system.module.system.mapper.SysUserMapper;
import com.feng.system.module.system.service.DeptService;
import com.feng.system.module.system.vo.MenuTreeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeptServiceImpl implements DeptService {

    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;

    @Override
    public List<MenuTreeVO> tree() {
        List<SysDept> depts = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getDeleted, 0)
                .orderByAsc(SysDept::getDeptSort));
        return MenuTreeBuilder.buildDeptTree(depts);
    }

    @Override
    public void save(DeptDTO dto) {
        SysDept dept = new SysDept();
        BeanUtils.copyProperties(dto, dept);
        deptMapper.insert(dept);
        dto.setId(dept.getId());
    }

    @Override
    public void update(DeptDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("部门ID不能为空");
        }
        SysDept dept = new SysDept();
        BeanUtils.copyProperties(dto, dept);
        deptMapper.updateById(dept);
    }

    @Override
    public void delete(Long id) {
        long childDeptCount = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getParentId, id)
                .eq(SysDept::getDeleted, 0));
        if (childDeptCount > 0) {
            throw new BusinessException("该部门存在下级部门，不能删除");
        }

        long userCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeptId, id)
                .eq(SysUser::getDeleted, 0));
        if (userCount > 0) {
            throw new BusinessException("该部门已被用户使用，不能删除");
        }

        deptMapper.deleteById(id);
    }
}