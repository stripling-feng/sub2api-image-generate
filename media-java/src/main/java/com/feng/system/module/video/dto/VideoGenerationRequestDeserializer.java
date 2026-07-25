package com.feng.system.module.video.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.image.exception.ImageApiException;

import java.io.IOException;

public class VideoGenerationRequestDeserializer extends JsonDeserializer<VideoGenerationRequest> {
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
