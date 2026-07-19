package com.feng.system.module.system.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MenuTreeVO {
    private Long id;
    private Long parentId;
    private String menuName;
    private String deptName;
    private Integer menuType;
    private String path;
    private String component;
    private String permission;
    private String icon;
    private Integer menuSort;
    private Integer deptSort;
    private Integer visible;
    private Integer level;
    private String leader;
    private String phone;
    private String email;
    private List<MenuTreeVO> children = new ArrayList<>();
}