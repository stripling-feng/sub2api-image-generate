package com.feng.system.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feng.system.module.system.entity.SysUserRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    @Delete("delete from sys_user_role where user_id = #{userId}")
    void deleteByUserId(Long userId);

    @Select("select role_id from sys_user_role where user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(Long userId);

    @Select("select user_id from sys_user_role where role_id = #{roleId}")
    List<Long> selectUserIdsByRoleId(Long roleId);
}
