package com.feng.system.module.system.service;

import com.feng.system.module.system.dto.DeptDTO;
import com.feng.system.module.system.vo.MenuTreeVO;

import java.util.List;

public interface DeptService {
    List<MenuTreeVO> tree();
    void save(DeptDTO dto);
    void update(DeptDTO dto);
    void delete(Long id);
}
