package com.feng.system.module.image;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationAuditMigrationTest {
    @Test
    void addsAuditColumnsToImageAndVideoJobs() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V11__generation_request_response_audit.sql"));
        assertTrue(sql.contains("ALTER TABLE generation_jobs"));
        assertTrue(sql.contains("ALTER TABLE video_generation_jobs"));
        assertTrue(sql.contains("raw_request jsonb"));
        assertTrue(sql.contains("raw_responses jsonb"));
    }
}
