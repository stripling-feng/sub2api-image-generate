package com.feng.system.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaSchemaMigrationTest {

    @Test
    void mediaModelMigrationCopiesRowsAndKeepsIdentityValues() throws Exception {
        String sql = read("db/migration/V15__media_model_tables.sql");

        assertTrue(sql.contains("create table media_model_providers"));
        assertTrue(sql.contains("create table media_ai_models"));
        assertTrue(sql.contains("insert into media_model_providers"));
        assertTrue(sql.contains("insert into media_ai_models"));
        assertTrue(sql.contains("overriding system value"));
        assertTrue(sql.contains("pg_get_serial_sequence"));
        assertTrue(sql.contains("references media_model_providers(id)"));
        assertTrue(sql.contains("media_model_providers primary key copy mismatch"));
        assertTrue(sql.contains("media_ai_models primary key or provider copy mismatch"));
        assertFalse(sql.contains("drop table"));
    }

    @Test
    void mediaTaskMigrationCreatesEmptyPrefixedTables() throws Exception {
        String sql = read("db/migration/V16__media_task_tables.sql");

        for (String table : new String[]{"media_tasks", "media_task_results", "media_billing_records"}) {
            assertTrue(sql.contains("create table " + table), table);
        }
        for (String column : new String[]{"api_key", "task_type", "user_request", "system_response",
                "upstream_request", "upstream_response", "billing_amount", "task_fee", "deduction_status"}) {
            assertTrue(sql.contains(column), column);
        }
        assertTrue(sql.contains("media_tasks_owner_history_idx"));
        assertTrue(sql.contains("media_tasks_poll_idx"));
        assertTrue(sql.contains("media_task_results_task_order_unique"));
        assertTrue(sql.contains("task_id text not null unique references media_tasks(id)"));
        assertTrue(sql.contains("status in ('pending', 'succeeded', 'failed')"));
        assertFalse(sql.contains("next_poll_at"));
        assertFalse(sql.contains("poll_error_count"));
        assertFalse(sql.contains("poll_lease_until"));
        assertFalse(sql.contains("insert into media_tasks select"));
        assertFalse(sql.contains("insert into media_task_results select"));
        assertFalse(sql.contains("drop table"));
    }

    @Test
    void mediaApiProfileMigrationCopiesRowsWithoutDroppingLegacyTables() throws Exception {
        String sql = read("db/migration/V18__media_api_profile_tables.sql");

        assertTrue(sql.contains("media_api_profiles"));
        assertTrue(sql.contains("media_api_sessions"));
        assertTrue(sql.contains("create table if not exists media_api_profiles"));
        assertTrue(sql.contains("create table if not exists media_api_sessions"));
        assertTrue(sql.contains("references media_api_profiles(id)"));
        assertTrue(sql.contains("insert into media_api_profiles"));
        assertTrue(sql.contains("insert into media_api_sessions"));
        assertTrue(sql.contains("from api_profiles"));
        assertTrue(sql.contains("from api_sessions"));
        assertTrue(sql.contains("media_api_profiles copy count mismatch"));
        assertTrue(sql.contains("media_api_sessions copy count mismatch"));
        assertTrue(sql.contains("api_profiles_sync_to_media"));
        assertTrue(sql.contains("media_api_profiles_sync_to_legacy"));
        assertTrue(sql.contains("api_sessions_sync_to_media"));
        assertTrue(sql.contains("media_api_sessions_sync_to_legacy"));
        assertTrue(sql.contains("pg_trigger_depth() > 1"));
        assertFalse(sql.contains("drop table"));
    }

    @Test
    void gptAccountMigrationCreatesEncryptedAccountStoreAndMenus() throws Exception {
        String sql = read("db/migration/V19__gpt_account_management.sql");

        assertTrue(sql.contains("create table if not exists gpt_accounts"));
        assertTrue(sql.contains("access_token_ciphertext text not null"));
        assertTrue(sql.contains("token_hash char(64) not null"));
        assertTrue(sql.contains("eligible_offers jsonb"));
        assertTrue(sql.contains("plus_eligibility"));
        assertTrue(sql.contains("gpt账号管理"));
        assertTrue(sql.contains("账号管理"));
        assertTrue(sql.contains("gpt:account:import"));
        assertTrue(sql.contains("gpt:account:refresh"));
        assertFalse(sql.contains("drop table"));
    }

    @Test
    void gptAccountPlaintextMigrationRenamesTokenColumn() throws Exception {
        String sql = read("db/migration/V20__gpt_account_plaintext_tokens.sql");

        assertTrue(sql.contains("rename column access_token_ciphertext to access_token"));
        assertTrue(sql.contains("plaintext chatgpt access token"));
    }

    @Test
    void gptAccountUsedFlagMigrationAddsFilterableMarkerAndPermission() throws Exception {
        String sql = read("db/migration/V21__gpt_account_used_flag.sql");

        assertTrue(sql.contains("add column if not exists used boolean not null default false"));
        assertTrue(sql.contains("gpt_accounts_used_idx"));
        assertTrue(sql.contains("gpt:account:update"));
        assertTrue(sql.contains("标记gpt账号"));
        assertFalse(sql.contains("drop table"));
    }

    @Test
    void mediaModuleEntitiesUseOnlyMediaPrefixedTables() throws Exception {
        java.nio.file.Path root = java.nio.file.Path.of("src/main/java/com/feng/system/module");
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().replace('\\', '/').contains("/image/")
                            || path.toString().replace('\\', '/').contains("/video/")
                            || path.toString().replace('\\', '/').contains("/media/"))
                    .forEach(path -> {
                        try {
                            String source = java.nio.file.Files.readString(path, StandardCharsets.UTF_8);
                            java.util.regex.Matcher matcher = java.util.regex.Pattern
                                    .compile("@TableName\\(\\s*(?:value\\s*=\\s*)?\"([^\"]+)\"")
                                    .matcher(source);
                            while (matcher.find()) {
                                assertTrue(matcher.group(1).startsWith("media_"), path + " -> " + matcher.group(1));
                            }
                        } catch (Exception e) {
                            throw new AssertionError(e);
                        }
                    });
        }
    }

    private String read(String path) throws Exception {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8).toLowerCase();
    }
}
