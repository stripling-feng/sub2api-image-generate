package com.feng.system.module.image.dto;

/**
 * 批量删除任务的响应。
 *
 * @param ok           删除操作是否成功
 * @param deletedCount 实际删除的任务条数
 */
public record DeleteJobsResponse(boolean ok, int deletedCount) {
}
