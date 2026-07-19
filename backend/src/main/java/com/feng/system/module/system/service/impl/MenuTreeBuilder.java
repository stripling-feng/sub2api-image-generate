package com.feng.system.module.system.service.impl;

import com.feng.system.module.system.entity.SysDept;
import com.feng.system.module.system.entity.SysMenu;
import com.feng.system.module.system.vo.MenuTreeVO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class MenuTreeBuilder {

    private MenuTreeBuilder() {
    }

    public static List<MenuTreeVO> buildMenuTree(List<SysMenu> menus) {
        List<MenuTreeVO> nodes = menus.stream().map(menu -> {
            MenuTreeVO vo = new MenuTreeVO();
            vo.setId(menu.getId());
            vo.setParentId(menu.getParentId());
            vo.setMenuName(menu.getMenuName());
            vo.setMenuType(menu.getMenuType());
            vo.setPath(menu.getPath());
            vo.setComponent(menu.getComponent());
            vo.setPermission(menu.getPermission());
            vo.setIcon(menu.getIcon());
            vo.setMenuSort(menu.getMenuSort());
            vo.setVisible(menu.getVisible());
            return vo;
        }).toList();
        return build(nodes);
    }

    public static List<MenuTreeVO> buildDeptTree(List<SysDept> depts) {
        List<MenuTreeVO> nodes = depts.stream().map(dept -> {
            MenuTreeVO vo = new MenuTreeVO();
            vo.setId(dept.getId());
            vo.setParentId(dept.getParentId());
            vo.setMenuName(dept.getDeptName());
            vo.setDeptName(dept.getDeptName());
            vo.setMenuSort(dept.getDeptSort());
            vo.setDeptSort(dept.getDeptSort());
            vo.setVisible(1);
            vo.setLeader(dept.getLeader());
            vo.setPhone(dept.getPhone());
            vo.setEmail(dept.getEmail());
            return vo;
        }).toList();
        return build(nodes);
    }

    private static List<MenuTreeVO> build(List<MenuTreeVO> nodes) {
        Map<Long, List<MenuTreeVO>> childrenMap = nodes.stream()
                .collect(Collectors.groupingBy(MenuTreeVO::getParentId));
        List<MenuTreeVO> roots = new ArrayList<>(childrenMap.getOrDefault(0L, List.of()));
        fillChildren(roots, childrenMap);
        roots.sort(Comparator.comparing(MenuTreeVO::getMenuSort).thenComparing(MenuTreeVO::getId));
        return roots;
    }

    private static void fillChildren(List<MenuTreeVO> parents, Map<Long, List<MenuTreeVO>> childrenMap) {
        for (MenuTreeVO parent : parents) {
            List<MenuTreeVO> children = new ArrayList<>(childrenMap.getOrDefault(parent.getId(), List.of()));
            children.sort(Comparator.comparing(MenuTreeVO::getMenuSort).thenComparing(MenuTreeVO::getId));
            parent.setChildren(children);
            fillChildren(children, childrenMap);
        }
    }
}