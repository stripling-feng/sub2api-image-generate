package com.feng.system.module.image.dto;

import java.util.List;

/**
 * 历史任务分页查询响应。
 *
 * @param jobs       当前页的任务列表
 * @param page       当前页码(从 1 开始)
 * @param pageSize   每页条数
 * @param total      任务总条数
 * @param totalPages 总页数
 */
public record HistoryResponse(List<?> jobs, int page, int pageSize, long total, long totalPages) {
}
