package com.feng.system.module.image.dto;

import java.util.List;

/**
 * 任务列表响应,jobs 为某次生成请求下的任务明细集合。
 */
public record JobsResponse(List<?> jobs) {
}
