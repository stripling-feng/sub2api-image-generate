package com.feng.system.module.image;

import com.feng.system.module.image.service.ImageTaskRules;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageTaskRulesTest {

    @Test
    void completedStatusWinsOverTimeoutAndChargeIsPerImage() {
        assertEquals(ImageTaskRules.Decision.COMPLETED, ImageTaskRules.decide("completed", 40, 30));
        assertEquals(ImageTaskRules.Decision.FAILED, ImageTaskRules.decide("failed", 1, 30));
        assertEquals(ImageTaskRules.Decision.TIMEOUT, ImageTaskRules.decide("running", 31, 30));
        assertEquals(ImageTaskRules.Decision.PENDING, ImageTaskRules.decide("running", 10, 30));
        assertEquals(new BigDecimal("1.5000000000"), ImageTaskRules.charge(3, new BigDecimal("0.5")));
        assertEquals(new BigDecimal("0.1200000000"), ImageTaskRules.charge(3, new BigDecimal("0.04")));
        assertEquals(new BigDecimal("0E-10"), ImageTaskRules.charge(1, BigDecimal.ZERO));
    }
}
