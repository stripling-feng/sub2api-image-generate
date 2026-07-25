package com.feng.system.module.video.service;

import com.feng.system.module.image.exception.ImageApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class VideoTaskRules {
    private VideoTaskRules() {}

    public static BigDecimal charge(String mode, int duration, BigDecimal unitPrice) {
        BigDecimal amount = "PER_SECOND".equals(mode)
                ? unitPrice.multiply(BigDecimal.valueOf(duration)) : unitPrice;
        return amount.setScale(10, RoundingMode.HALF_UP);
    }

    public static void validate(String model, int duration, String ratio, String resolution, int images,
                                int videos, int audios, boolean firstFrame, boolean lastFrame) {
        if (duration < 4 || duration > 15 || !List.of("480p", "720p").contains(resolution)) invalid();
        if (model.startsWith("omni-fast")) {
            if (duration != 10 || !"720p".equals(resolution) || !List.of("16:9", "9:16").contains(ratio)
                    || images > 5 || videos != 0 || audios != 0 || images > 0 && (firstFrame || lastFrame)) invalid();
            return;
        }
        if (model.startsWith("omni-v2v")) {
            if (duration != 10 || !"720p".equals(resolution) || !List.of("16:9", "9:16").contains(ratio)
                    || images > 2 || videos > 2 || audios != 0 || firstFrame || lastFrame) invalid();
            return;
        }
        if (model.startsWith("seedance-")) {
            if (!List.of("16:9", "9:16", "1:1", "21:9", "3:4", "4:3").contains(ratio)
                    || images > 4 || videos > 3 || audios > 1 || firstFrame != lastFrame
                    || firstFrame && images + videos + audios > 0) invalid();
            return;
        }
        if ("grok-video-1.5".equals(model)) {
            if (!List.of(4, 6, 8, 10, 12, 15).contains(duration)
                    || !List.of("16:9", "9:16").contains(ratio)
                    || images != 1 || videos != 0 || audios != 0 || firstFrame || lastFrame) invalid();
            return;
        }
        if ("grok-video".equals(model)) {
            if (!List.of(4, 6, 8, 10, 12, 15).contains(duration)
                    || !List.of("1:1", "16:9", "9:16", "4:3", "3:4", "3:2", "2:3").contains(ratio)
                    || images > 7 || videos > 1 || audios != 0 || firstFrame || lastFrame
                    || images > 1 && duration > 10) invalid();
            return;
        }
        invalid();
    }

    private static void invalid() { throw new ImageApiException(422, "Invalid video generation parameters."); }
}
