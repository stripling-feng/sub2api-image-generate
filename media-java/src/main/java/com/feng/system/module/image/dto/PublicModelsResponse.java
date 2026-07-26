package com.feng.system.module.image.dto;

import java.util.List;

/**
 * 公开模型列表响应,models 为对外可见的模型配置集合。
 */
public record PublicModelsResponse(List<?> models) {
}
