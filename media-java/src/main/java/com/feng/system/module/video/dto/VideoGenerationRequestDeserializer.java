package com.feng.system.module.video.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.image.exception.ImageApiException;

import java.io.IOException;

/**
 * 视频生成请求的多态反序列化器:读取 JSON 中的 model 字段,
 * 按前缀/精确匹配分发到对应的请求子类(seedance- 前缀 -> Seedance,grok-video-1.5 -> Grok1.5,
 * grok-video -> Grok,omni-fast 前缀 -> OmniFast,omni-v2v 前缀 -> OmniV2v),未匹配则报 422。
 */
public class VideoGenerationRequestDeserializer extends JsonDeserializer<VideoGenerationRequest> {
    /** 反序列化入口:model 缺失或为空直接报 422,否则按模型类型二次反序列化为具体子类。 */
    @Override
    public VideoGenerationRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        ObjectMapper mapper = (ObjectMapper) parser.getCodec();
        JsonNode node = mapper.readTree(parser);
        String model = node.path("model").asText(null);
        if (model == null || model.isBlank()) throw new ImageApiException(422, "Invalid video generation request.");
        Class<? extends VideoGenerationRequest> type = type(model);
        return mapper.readerFor(type).readValue(node);
    }

    private Class<? extends VideoGenerationRequest> type(String model) {
        if (model.startsWith("seedance-")) return SeedanceVideoRequest.class;
        if ("grok-video-1.5".equals(model)) return GrokVideo15Request.class;
        if ("grok-video".equals(model)) return GrokVideoRequest.class;
        if (model.startsWith("omni-fast")) return OmniFastVideoRequest.class;
        if (model.startsWith("omni-v2v")) return OmniV2vVideoRequest.class;
        throw new ImageApiException(422, "Unknown or disabled video model.");
    }
}
