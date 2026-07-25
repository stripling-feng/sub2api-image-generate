package com.feng.system.module.system.service;

import com.feng.system.module.system.dto.MenuDTO;
import com.feng.system.module.system.vo.MenuTreeVO;

import java.util.List;

public interface MenuService {
    List<MenuTreeVO> tree();
    List<MenuTreeVO> userMenuTree(Long userId);
    void save(MenuDTO dto);
    void update(MenuDTO dto);
    void delete(Long id);
}
