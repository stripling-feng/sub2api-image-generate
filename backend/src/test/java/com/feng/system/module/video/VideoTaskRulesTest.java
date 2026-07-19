package com.feng.system.module.video;

import com.feng.system.module.image.ImageApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VideoTaskRulesTest {

    @Test
    void chargesPerRequestOrRequestedSecond() {
        assertEquals(new BigDecimal("0.5000000000"),
                VideoTaskRules.charge("PER_REQUEST", 8, new BigDecimal("0.5")));
        assertEquals(new BigDecimal("2.0000000000"),
                VideoTaskRules.charge("PER_SECOND", 8, new BigDecimal("0.25")));
    }

    @Test
    void grok15RequiresExactlyOneImage() {
        assertThrows(ImageApiException.class, () -> VideoTaskRules.validate(
                "grok-video-1.5", 6, "16:9", "720p", 0, 0, 0, false, false));
        VideoTaskRules.validate("grok-video-1.5", 6, "16:9", "720p", 1, 0, 0, false, false);
    }

    @Test
    void seedanceFramesMustBePairedAndCannotMixWithReferences() {
        assertThrows(ImageApiException.class, () -> VideoTaskRules.validate(
                "seedance-2.0", 8, "16:9", "720p", 0, 0, 0, true, false));
        assertThrows(ImageApiException.class, () -> VideoTaskRules.validate(
                "seedance-2.0", 8, "16:9", "720p", 1, 0, 0, true, true));
    }
}
