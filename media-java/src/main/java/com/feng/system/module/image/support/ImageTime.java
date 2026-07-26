package com.feng.system.module.image.support;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 图片模块统一时间工具:所有时间均使用 UTC,避免各处混用时区导致过期判断错乱。
 */
public final class ImageTime {
    private ImageTime() {}
    /** 获取当前 UTC 时间 */
    public static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
}
