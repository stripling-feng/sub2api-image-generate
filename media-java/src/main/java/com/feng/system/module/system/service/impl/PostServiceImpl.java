package com.feng.system.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feng.system.common.api.PageResult;
import com.feng.system.common.exception.BusinessException;
import com.feng.system.module.system.dto.PostDTO;
import com.feng.system.module.system.dto.PostQueryDTO;
import com.feng.system.module.system.entity.SysPost;
import com.feng.system.module.system.entity.SysUser;
import com.feng.system.module.system.mapper.SysPostMapper;
import com.feng.system.module.system.mapper.SysUserMapper;
import com.feng.system.module.system.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final SysPostMapper postMapper;
    private final SysUserMapper userMapper;

    @Override
    public PageResult<SysPost> page(PostQueryDTO queryDTO) {
        LambdaQueryWrapper<SysPost> wrapper = new LambdaQueryWrapper<SysPost>()
                .eq(SysPost::getDeleted, 0)
                .like(queryDTO.getPostName() != null && !queryDTO.getPostName().isBlank(), SysPost::getPostName, queryDTO.getPostName())
                .like(queryDTO.getPostCode() != null && !queryDTO.getPostCode().isBlank(), SysPost::getPostCode, queryDTO.getPostCode())
                .orderByAsc(SysPost::getPostSort)
                .orderByDesc(SysPost::getId);
        Page<SysPost> page = postMapper.selectPage(new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize()), wrapper);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    @Override
    public List<SysPost> options() {
        return postMapper.selectList(new LambdaQueryWrapper<SysPost>()
                .eq(SysPost::getDeleted, 0)
                .orderByAsc(SysPost::getPostSort));
    }

    @Override
    public void save(PostDTO dto) {
        SysPost post = new SysPost();
        BeanUtils.copyProperties(dto, post);
        postMapper.insert(post);
        dto.setId(post.getId());
    }

    @Override
    public void update(PostDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("岗位ID不能为空");
        }
        SysPost post = new SysPost();
        BeanUtils.copyProperties(dto, post);
        postMapper.updateById(post);
    }

    @Override
    public void delete(Long id) {
        long userCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPostId, id)
                .eq(SysUser::getDeleted, 0));
        if (userCount > 0) {
            throw new BusinessException("该岗位已被用户使用，不能删除");
        }
        postMapper.deleteById(id);
    }
}