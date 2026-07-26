package com.feng.system.module.video.dto;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * grok-video-1.5 模型的生成请求:字段与 GrokVideoRequest 完全一致,
 * 仅作为独立类型存在,便于按模型区分校验规则与上游报文格式。
 */
@JsonDeserialize(using = JsonDeserializer.None.class)
public class GrokVideo15Request extends GrokVideoRequest {
}
