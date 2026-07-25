package com.feng.system.module.system.service;

import com.feng.system.common.api.PageResult;
import com.feng.system.module.system.dto.PostDTO;
import com.feng.system.module.system.dto.PostQueryDTO;
import com.feng.system.module.system.entity.SysPost;

import java.util.List;

public interface PostService {
    PageResult<SysPost> page(PostQueryDTO queryDTO);
    List<SysPost> options();
    void save(PostDTO dto);
    void update(PostDTO dto);
    void delete(Long id);
}
