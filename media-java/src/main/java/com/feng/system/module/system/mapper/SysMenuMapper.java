package com.feng.system.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feng.system.module.system.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    @Select("""
            SELECT DISTINCT m.permission
            FROM sys_menu m
            INNER JOIN sys_role_menu rm ON rm.menu_id = m.id
            INNER JOIN sys_user_role ur ON ur.role_id = rm.role_id
            WHERE ur.user_id = #{userId}
              AND m.deleted = 0
              AND m.permission IS NOT NULL
              AND m.permission <> ''
            """)
    List<String> selectPermissionsByUserId(Long userId);

    @Select("""
            SELECT DISTINCT m.*
            FROM sys_menu m
            INNER JOIN sys_role_menu rm ON rm.menu_id = m.id
            INNER JOIN sys_user_role ur ON ur.role_id = rm.role_id
            WHERE ur.user_id = #{userId}
              AND m.deleted = 0
            ORDER BY m.menu_sort ASC, m.id ASC
            """)
    List<SysMenu> selectMenusByUserId(Long userId);
}