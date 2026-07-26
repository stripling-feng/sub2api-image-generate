package com.feng.system.module.image;

import com.feng.system.module.image.dto.ImageGenerateRequest;
import com.feng.system.module.image.formatter.ImageGenerateRequestFormatters;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageGenerateRequestFormattersTest {

    @Test
    void formatsBaseGptImage2WithExactWhitelist() {
        ImageGenerateRequest request = new ImageGenerateRequest();
        request.setModel("gpt-image-2");
        request.setPrompt("city");
        request.setAsync(true);
        request.setSize("1:1");
        request.setImages(List.of());

        Map<String, Object> params = new ImageGenerateRequestFormatters().format(request.getModel(), request);

        assertEquals(Set.of("model", "prompt", "async", "size", "images"), params.keySet());
        assertEquals("1:1", params.get("size"));
    }

    @Test
    void sizedGptImage2ModelsShareExactWhitelist() {
        for (String model : new String[] {"gpt-image-2-1k", "gpt-image-2-2k", "gpt-image-2-4k"}) {
            ImageGenerateRequest request = new ImageGenerateRequest();
            request.setModel(model);
            request.setPrompt("city");
            request.setAsync(true);
            request.setAspectRatio("16:9");
            request.setSize("3840x2160");
            request.setQuality("high");
            request.setImages(List.of("https://cdn.example.com/ref.png"));

            Map<String, Object> params = new ImageGenerateRequestFormatters().format(request.getModel(), request);

            assertEquals(Set.of("model", "prompt", "async", "images", "size", "aspect_ratio", "quality"), params.keySet());
            assertEquals("16:9", params.get("aspect_ratio"));
            assertEquals("3840x2160", params.get("size"));
            assertEquals("high", params.get("quality"));
        }
    }
}
