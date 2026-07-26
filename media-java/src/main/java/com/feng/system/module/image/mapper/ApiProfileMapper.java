package com.feng.system.module.image.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feng.system.module.image.entity.ApiProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * API 接入配置表(api_profiles)的 MyBatis-Plus Mapper。
 */
@Mapper
public interface ApiProfileMapper extends BaseMapper<ApiProfile> {
}
