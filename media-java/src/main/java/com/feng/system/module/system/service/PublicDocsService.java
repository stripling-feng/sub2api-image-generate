package com.feng.system.module.system.service;

import java.time.LocalDateTime;

public interface PublicDocsService {
    Document getDocument(String key);

    record Document(String key, String title, String content, LocalDateTime updatedAt) {}
}
