package com.feng.system.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feng.system.common.exception.BusinessException;
import com.feng.system.module.system.dto.MenuDTO;
import com.feng.system.module.system.entity.SysMenu;
import com.feng.system.module.system.mapper.SysMenuMapper;
import com.feng.system.module.system.service.MenuService;
import com.feng.system.module.system.vo.MenuTreeVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

    private static final String ALL_MENUS_CACHE_KEY = "menu:all:list";
    private static final long CACHE_TTL_MINUTES = 10;

    private final SysMenuMapper menuMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public List<MenuTreeVO> tree() {
        List<SysMenu> menus = getAllMenus();
        return MenuTreeBuilder.buildMenuTree(menus);
    }

    @Override
    public List<MenuTreeVO> userMenuTree(Long userId) {
        List<SysMenu> assignedMenus = menuMapper.selectMenusByUserId(userId);
        if (assignedMenus.isEmpty()) {
            return List.of();
        }

        List<SysMenu> allMenus = getAllMenus();
        Map<Long, SysMenu> menuMap = new LinkedHashMap<>();
        Map<Long, List<SysMenu>> childrenMap = new HashMap<>();
        for (SysMenu menu : allMenus) {
            menuMap.put(menu.getId(), menu);
            childrenMap.computeIfAbsent(menu.getParentId(), key -> new ArrayList<>()).add(menu);
        }

        Map<Long, SysMenu> result = new LinkedHashMap<>();
        for (SysMenu menu : assignedMenus) {
            appendMenuWithRelations(menu, menuMap, childrenMap, result);
        }
        return MenuTreeBuilder.buildMenuTree(new ArrayList<>(result.values()));
    }

    private List<SysMenu> getAllMenus() {
        try {
            String cached = stringRedisTemplate.opsForValue().get(ALL_MENUS_CACHE_KEY);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<List<SysMenu>>() {});
            }
        } catch (Exception e) {
            log.warn("Failed to read menu cache", e);
        }
        List<SysMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getDeleted, 0)
                .orderByAsc(SysMenu::getMenuSort));
        try {
            stringRedisTemplate.opsForValue().set(ALL_MENUS_CACHE_KEY, objectMapper.writeValueAsString(menus), CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Failed to write menu cache", e);
        }
        return menus;
    }

    private void evictMenuCache() {
        stringRedisTemplate.delete(ALL_MENUS_CACHE_KEY);
    }

    @Override
    public void save(MenuDTO dto) {
        SysMenu menu = new SysMenu();
        BeanUtils.copyProperties(dto, menu);
        menuMapper.insert(menu);
        dto.setId(menu.getId());
        evictMenuCache();
    }

    @Override
    public void update(MenuDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("菜单ID不能为空");
        }
        SysMenu menu = new SysMenu();
        BeanUtils.copyProperties(dto, menu);
        menuMapper.updateById(menu);
        evictMenuCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        menuMapper.deleteById(id);
        evictMenuCache();
    }

    private void appendMenuWithRelations(SysMenu menu,
                                         Map<Long, SysMenu> menuMap,
                                         Map<Long, List<SysMenu>> childrenMap,
                                         Map<Long, SysMenu> result) {
        if (menu == null || result.containsKey(menu.getId())) {
            return;
        }
        if (!Objects.equals(menu.getParentId(), 0L)) {
            appendMenuWithRelations(menuMap.get(menu.getParentId()), menuMap, childrenMap, result);
        }
        result.put(menu.getId(), menu);
        for (SysMenu child : childrenMap.getOrDefault(menu.getId(), List.of())) {
            appendMenuWithRelations(child, menuMap, childrenMap, result);
        }
    }
}
