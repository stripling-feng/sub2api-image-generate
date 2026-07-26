package com.feng.system.module.image.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feng.system.module.image.entity.ApiSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * API 会话表(api_sessions)的 MyBatis-Plus Mapper。
 */
@Mapper
public interface ApiSessionMapper extends BaseMapper<ApiSession> {
}
