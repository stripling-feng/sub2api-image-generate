package com.feng.system.module.image;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class SafeUpstreamUrlTest {
    @Test
    void blocksLocalAndNonHttpsTargetsAndUsesUtcClock() {
        assertThrows(ImageApiException.class, () -> SafeUpstreamUrl.requirePublicHttps("http://example.com"));
        assertThrows(ImageApiException.class, () -> SafeUpstreamUrl.requirePublicHttps("https://127.0.0.1"));
        assertEquals("https://8.8.8.8", SafeUpstreamUrl.requirePublicHttps("https://8.8.8.8"));
        assertTrue(Math.abs(java.time.Duration.between(LocalDateTime.now(ZoneOffset.UTC), ImageTime.now()).toMillis()) < 1000);
    }
}
