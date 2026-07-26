package com.feng.system.module.media.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.image.support.ImageTime;
import com.feng.system.module.media.entity.MediaTaskResult;
import com.feng.system.module.media.mapper.MediaTaskResultMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaTaskResultService {
    private final MediaTaskResultMapper results;
    private final ObjectMapper json;

    public MediaTaskResult save(String taskId, int sortOrder, String address, Map<String, Object> metadata) {
        MediaTaskResult result = new MediaTaskResult();
        result.setId(id());
        result.setTaskId(taskId);
        result.setAddress(address);
        result.setMetadata(write(metadata == null ? Map.of() : metadata));
        result.setSortOrder(sortOrder);
        result.setCreatedAt(ImageTime.now());
        results.insert(result);
        return result;
    }

    public MediaTaskResult saveIfAbsent(String taskId, int sortOrder, String address, Map<String, Object> metadata) {
        MediaTaskResult existing = find(taskId, sortOrder);
        if (existing != null) return existing;
        try {
            return save(taskId, sortOrder, address, metadata);
        } catch (DataIntegrityViolationException duplicate) {
            MediaTaskResult concurrent = find(taskId, sortOrder);
            if (concurrent != null) return concurrent;
            throw duplicate;
        }
    }

    public boolean exists(String taskId, int sortOrder) {
        return find(taskId, sortOrder) != null;
    }

    public MediaTaskResult find(String taskId, int sortOrder) {
        return results.selectOne(new LambdaQueryWrapper<MediaTaskResult>()
                .eq(MediaTaskResult::getTaskId, taskId)
                .eq(MediaTaskResult::getSortOrder, sortOrder));
    }

    public List<MediaTaskResult> list(List<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) return List.of();
        return results.selectList(new LambdaQueryWrapper<MediaTaskResult>()
                .in(MediaTaskResult::getTaskId, taskIds)
                .orderByAsc(MediaTaskResult::getSortOrder)
                .orderByAsc(MediaTaskResult::getCreatedAt));
    }

    public MediaTaskResult findOwnedImageResult(String apiKey, String id) {
        if (apiKey == null || apiKey.isBlank() || id == null || id.isBlank()) return null;
        return results.selectOwnedImageResult(apiKey, id);
    }

    public void deleteByTaskIds(List<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) return;
        results.delete(new LambdaQueryWrapper<MediaTaskResult>().in(MediaTaskResult::getTaskId, taskIds));
    }

    public Map<String, Object> metadata(MediaTaskResult result) {
        if (result == null || result.getMetadata() == null) return Map.of();
        try {
            return json.readValue(result.getMetadata(), new TypeReference<>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("Unable to serialize media result metadata", e); }
    }

    private static String id() { return UUID.randomUUID().toString().replace("-", ""); }
}
