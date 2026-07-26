package com.feng.system.module.image.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feng.system.module.image.entity.AiModel;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 模型配置表(media_ai_models)的 MyBatis-Plus Mapper。
 */
@Mapper
public interface AiModelMapper extends BaseMapper<AiModel> {}
