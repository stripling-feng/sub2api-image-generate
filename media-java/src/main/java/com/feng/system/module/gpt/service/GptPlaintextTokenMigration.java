package com.feng.system.module.gpt.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@DependsOn("schemaGuard")
@RequiredArgsConstructor
public class GptPlaintextTokenMigration {
    private final JdbcTemplate jdbcTemplate;
    private final GptTokenStore tokenStore;

    @PostConstruct
    void migrateLegacyTokens() {
        List<LegacyToken> tokens = jdbcTemplate.query(
                "SELECT id, access_token FROM gpt_accounts WHERE access_token LIKE 'v1:%'",
                (rs, rowNum) -> new LegacyToken(rs.getLong("id"), rs.getString("access_token")));
        for (LegacyToken token : tokens) {
            jdbcTemplate.update("UPDATE gpt_accounts SET access_token = ? WHERE id = ?",
                    tokenStore.read(token.value()), token.id());
        }
    }

    private record LegacyToken(long id, String value) {}
}
