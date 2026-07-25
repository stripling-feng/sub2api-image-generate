package com.feng.system.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feng.system.module.system.entity.SysRoleMenu;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {

    @Delete("delete from sys_role_menu where role_id = #{roleId}")
    void deleteByRoleId(Long roleId);

    @Select("select menu_id from sys_role_menu where role_id = #{roleId}")
    List<Long> selectMenuIdsByRoleId(Long roleId);

    @Insert("<script>insert into sys_role_menu (role_id, menu_id) values " +
            "<foreach collection='roleMenus' item='rm' separator=','>(#{rm.roleId}, #{rm.menuId})</foreach></script>")
    int batchInsert(@Param("roleMenus") List<SysRoleMenu> roleMenus);
}
