package com.feng.system.module.media.service;

import com.feng.system.module.image.exception.ImageApiException;

import java.net.URI;

public final class MediaUploadUrl {
    private MediaUploadUrl() {
    }

    public static String requireHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            boolean http = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
            if (!http || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
            return uri.toString();
        } catch (Exception error) {
            throw new ImageApiException(500, "Upload service did not return a public URL.");
        }
    }
}
