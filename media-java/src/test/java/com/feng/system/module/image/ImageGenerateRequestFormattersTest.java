package com.feng.system.module.image;

import com.feng.system.module.image.dto.ImageGenerateRequest;
import com.feng.system.module.image.formatter.ImageGenerateRequestFormatters;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageGenerateRequestFormattersTest {

    @Test
    void formatsBaseGptImage2AspectRatioAsSizeParameter() {
        ImageGenerateRequest request = new ImageGenerateRequest();
        request.setModel("gpt-image-2");
        request.setAspectRatio("16:9");
        request.setSize("1:1");

        Map<String, Object> params = new ImageGenerateRequestFormatters().format(request.getModel(), request);

        assertEquals("16:9", params.get("size"));
    }

    @Test
    void sizedGptImage2ModelsShareTheSameFormatter() {
        for (String model : new String[] {"gpt-image-2-1k", "gpt-image-2-2k", "gpt-image-2-4k"}) {
            ImageGenerateRequest request = new ImageGenerateRequest();
            request.setModel(model);
            request.setAspectRatio("16:9");
            request.setSize("3840x2160");
            request.setQuality("high");

            Map<String, Object> params = new ImageGenerateRequestFormatters().format(request.getModel(), request);

            assertEquals("16:9", params.get("aspect_ratio"));
            assertEquals("3840x2160", params.get("size"));
            assertEquals("high", params.get("quality"));
        }
    }
}
