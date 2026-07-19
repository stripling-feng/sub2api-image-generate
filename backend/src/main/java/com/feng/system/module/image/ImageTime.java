package com.feng.system.module.image;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class ImageTime {
    private ImageTime() {}
    public static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
}
