package com.feng.system.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SchemaGuard {
    private static final int REQUIRED_VERSION = 16;
    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void verify() {
        try {
            Integer version = jdbcTemplate.queryForObject("SELECT max(version::integer) FROM flyway_schema_history WHERE success", Integer.class);
            if (version == null || version < REQUIRED_VERSION) throw new IllegalStateException("Database schema is below Flyway version " + REQUIRED_VERSION);
        } catch (Exception e) {
            throw new IllegalStateException("Database schema is not initialized; run npm run db:migrate", e);
        }
    }
}
