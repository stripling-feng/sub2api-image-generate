package com.feng.system.module.media.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feng.system.module.media.entity.MediaTaskResult;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MediaTaskResultMapper extends BaseMapper<MediaTaskResult> {

    @Select("""
            SELECT r.id, r.task_id AS taskId, r.address, r.metadata,
                   r.sort_order AS sortOrder, r.created_at AS createdAt
            FROM media_task_results r
            JOIN media_tasks t ON t.id = r.task_id
            WHERE t.task_type = 'IMAGE' AND r.created_at < #{cutoff}
            """)
    List<MediaTaskResult> selectExpiredImageResults(@Param("cutoff") LocalDateTime cutoff);

    @Select("""
            SELECT r.id, r.task_id AS taskId, r.address, r.metadata,
                   r.sort_order AS sortOrder, r.created_at AS createdAt
            FROM media_task_results r
            JOIN media_tasks t ON t.id = r.task_id
            WHERE r.id = #{id} AND t.api_key = #{apiKey} AND t.task_type = 'IMAGE'
            """)
    MediaTaskResult selectOwnedImageResult(@Param("apiKey") String apiKey, @Param("id") String id);
}
