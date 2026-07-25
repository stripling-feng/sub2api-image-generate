package com.feng.system.common.xss;

import org.springframework.web.util.HtmlUtils;

public final class XssSanitizer {

    private XssSanitizer() {
    }

    public static String clean(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return HtmlUtils.htmlEscape(value.trim());
    }
}
