package com.feng.system.module.image;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
public class Sub2apiBillingService {

    private final JdbcTemplate jdbc;
    @Value("${image.billing-account-id:}") private String configuredAccountId;

    public Sub2apiBillingService(@Qualifier("sub2apiJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BillingAccount validateApiKey(String apiKey) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT k.id AS api_key_id, k.user_id, u.balance, u.frozen_balance
                FROM api_keys k JOIN users u ON u.id = k.user_id
                WHERE k.key = ? AND k.status = 'active' AND k.deleted_at IS NULL
                  AND (k.expires_at IS NULL OR k.expires_at > NOW())
                  AND u.status = 'active' AND u.deleted_at IS NULL
                LIMIT 1
                """, apiKey);
        if (rows.isEmpty()) {
            throw new ImageApiException(401, "Invalid, expired, or disabled sub2api API Key.", "INVALID_API_KEY", null);
        }
        Map<String, Object> row = rows.get(0);
        BigDecimal balance = decimal(row.get("balance"));
        BigDecimal frozen = decimal(row.get("frozen_balance"));
        return new BillingAccount(String.valueOf(row.get("api_key_id")), String.valueOf(row.get("user_id")),
                money(balance), money(balance.subtract(frozen)));
    }

    public Gateway gateway(String accountId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT credentials->>'base_url' AS base_url, credentials->>'api_key' AS api_key
                FROM accounts WHERE id = ?::bigint AND status = 'active' AND deleted_at IS NULL LIMIT 1
                """, accountId);
        if (rows.isEmpty() || rows.get(0).get("base_url") == null || rows.get(0).get("api_key") == null) {
            throw new ImageApiException(503, "The selected sub2api data source has no usable upstream credentials.",
                    "UPSTREAM_ACCOUNT_INVALID", null);
        }
        return new Gateway(String.valueOf(rows.get(0).get("base_url")).trim(), String.valueOf(rows.get(0).get("api_key")).trim());
    }

    @Transactional(transactionManager = "sub2apiTransactionManager")
    public Reservation reserve(String apiKey, int count, BigDecimal unitPrice) {
        return reserveAmount(apiKey, ImageTaskRules.charge(count, unitPrice));
    }

    @Transactional(transactionManager = "sub2apiTransactionManager")
    public Reservation reserveAmount(String apiKey, BigDecimal amount) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT k.id AS api_key_id, k.user_id, k.quota, k.quota_used,
                       k.rate_limit_5h, k.rate_limit_1d, k.rate_limit_7d,
                       k.usage_5h, k.usage_1d, k.usage_7d,
                       k.window_5h_start, k.window_1d_start, k.window_7d_start,
                       u.balance, u.frozen_balance
                FROM api_keys k JOIN users u ON u.id = k.user_id
                WHERE k.key = ? AND k.status = 'active' AND k.deleted_at IS NULL
                  AND (k.expires_at IS NULL OR k.expires_at > NOW())
                  AND u.status = 'active' AND u.deleted_at IS NULL
                FOR UPDATE OF k, u
                """, apiKey);
        if (rows.isEmpty()) throw new ImageApiException(401, "Invalid, expired, or disabled sub2api API Key.", "INVALID_API_KEY", null);
        Map<String, Object> row = rows.get(0);
        BigDecimal quota = decimal(row.get("quota"));
        if (quota.signum() > 0 && decimal(row.get("quota_used")).add(amount).compareTo(quota) > 0)
            throw new ImageApiException(402, "API Key quota is insufficient for this generation.", "API_KEY_QUOTA_EXCEEDED", null);
        checkWindow(row, "rate_limit_5h", "usage_5h", "window_5h_start", 5L * 60 * 60 * 1000, amount);
        checkWindow(row, "rate_limit_1d", "usage_1d", "window_1d_start", 24L * 60 * 60 * 1000, amount);
        checkWindow(row, "rate_limit_7d", "usage_7d", "window_7d_start", 7L * 24 * 60 * 60 * 1000, amount);
        BigDecimal balance = decimal(row.get("balance"));
        BigDecimal frozen = decimal(row.get("frozen_balance"));
        if (balance.subtract(frozen).compareTo(amount) < 0)
            throw new ImageApiException(402, "Insufficient sub2api balance for this generation.", "INSUFFICIENT_BALANCE", null);
        String userId = String.valueOf(row.get("user_id"));
        String apiKeyId = String.valueOf(row.get("api_key_id"));
        jdbc.update("UPDATE users SET frozen_balance=frozen_balance+?, updated_at=NOW() WHERE id=?::bigint", amount, userId);
        return new Reservation(apiKeyId, userId, resolveAccountId(apiKeyId), amount, balance.subtract(frozen).subtract(amount));
    }

    @Transactional(transactionManager = "sub2apiTransactionManager")
    public String release(String jobId, String apiKeyId, String userId, BigDecimal amount) {
        return release("image-workbench", jobId, apiKeyId, userId, amount);
    }

    @Transactional(transactionManager = "sub2apiTransactionManager")
    public String releaseVideo(String jobId, String apiKeyId, String userId, BigDecimal amount) {
        return release("video-workbench", jobId, apiKeyId, userId, amount);
    }

    private String release(String prefix, String jobId, String apiKeyId, String userId, BigDecimal amount) {
        String requestId = prefix + ":" + jobId;
        if (!jdbc.queryForList("SELECT id FROM usage_logs WHERE request_id=? AND api_key_id=?::bigint", requestId, apiKeyId).isEmpty())
            return "settled";
        int marker = jdbc.update("""
                INSERT INTO usage_billing_dedup(request_id,api_key_id,request_fingerprint)
                VALUES (?,?::bigint,?) ON CONFLICT(request_id,api_key_id) DO NOTHING
                """, prefix + "-release:" + jobId, apiKeyId, jobId);
        if (marker == 0) return "released";
        jdbc.queryForObject("SELECT id FROM users WHERE id=?::bigint FOR UPDATE", Long.class, userId);
        jdbc.update("UPDATE users SET frozen_balance=GREATEST(frozen_balance-?,0),updated_at=NOW() WHERE id=?::bigint", amount, userId);
        return "released";
    }

    @Transactional(transactionManager = "sub2apiTransactionManager")
    public String settle(String jobId, String apiKeyId, String userId, String accountId, BigDecimal amount,
                         int count, String size, String operation, Integer durationMs) {
        String requestId = "image-workbench:" + jobId;
        List<Map<String, Object>> existing = jdbc.queryForList("SELECT id FROM usage_logs WHERE request_id=? AND api_key_id=?::bigint", requestId, apiKeyId);
        if (!existing.isEmpty()) return String.valueOf(existing.get(0).get("id"));
        jdbc.queryForObject("SELECT id FROM users WHERE id=?::bigint FOR UPDATE", Long.class, userId);
        List<Map<String, Object>> charged = jdbc.queryForList("""
                UPDATE users SET balance=balance-?, frozen_balance=frozen_balance-?, updated_at=NOW()
                WHERE id=?::bigint AND balance>=? AND frozen_balance>=? RETURNING id
                """, amount, amount, userId, amount, amount);
        if (charged.isEmpty()) throw new ImageApiException(409, "Reserved sub2api balance could not be settled.", "BILLING_RESERVATION_INVALID", null);
        jdbc.update("""
                UPDATE api_keys SET quota_used=quota_used+?,
                usage_5h=CASE WHEN window_5h_start IS NULL OR window_5h_start<=NOW()-INTERVAL '5 hours' THEN ? ELSE usage_5h+? END,
                usage_1d=CASE WHEN window_1d_start IS NULL OR window_1d_start<=NOW()-INTERVAL '1 day' THEN ? ELSE usage_1d+? END,
                usage_7d=CASE WHEN window_7d_start IS NULL OR window_7d_start<=NOW()-INTERVAL '7 days' THEN ? ELSE usage_7d+? END,
                window_5h_start=CASE WHEN window_5h_start IS NULL OR window_5h_start<=NOW()-INTERVAL '5 hours' THEN NOW() ELSE window_5h_start END,
                window_1d_start=CASE WHEN window_1d_start IS NULL OR window_1d_start<=NOW()-INTERVAL '1 day' THEN NOW() ELSE window_1d_start END,
                window_7d_start=CASE WHEN window_7d_start IS NULL OR window_7d_start<=NOW()-INTERVAL '7 days' THEN NOW() ELSE window_7d_start END,
                last_used_at=NOW(),updated_at=NOW() WHERE id=?::bigint
                """, amount, amount, amount, amount, amount, amount, amount, apiKeyId);
        Long groupId = jdbc.queryForObject("SELECT group_id FROM api_keys WHERE id=?::bigint", Long.class, apiKeyId);
        String endpoint = "/v1/images/" + operation;
        Long usageId = jdbc.queryForObject("""
                INSERT INTO usage_logs(user_id,api_key_id,account_id,request_id,model,total_cost,actual_cost,image_output_cost,
                  image_count,image_size,billing_type,billing_mode,request_type,stream,duration_ms,group_id,inbound_endpoint,
                  upstream_endpoint,upstream_model,requested_model,image_output_size,image_size_source)
                VALUES (?::bigint,?::bigint,?::bigint,?,'gpt-image-2',?,?,?,?,'1K',0,'image',1,FALSE,?,?,?,?,'cy-img1-gpt-image-2','gpt-image-2',?,'output') RETURNING id
                """, Long.class, userId, apiKeyId, accountId, requestId, amount, amount, amount, count, durationMs, groupId,
                endpoint, endpoint, size);
        jdbc.update("""
                INSERT INTO billing_usage_entries(usage_log_id,user_id,api_key_id,billing_type,applied,delta_usd)
                VALUES (?,?::bigint,?::bigint,0,TRUE,-(?::numeric)) ON CONFLICT(usage_log_id) DO NOTHING
                """, usageId, userId, apiKeyId, amount);
        jdbc.update("""
                INSERT INTO usage_billing_dedup(request_id,api_key_id,request_fingerprint)
                VALUES (?,?::bigint,?) ON CONFLICT(request_id,api_key_id) DO NOTHING
                """, requestId, apiKeyId, jobId);
        return String.valueOf(usageId);
    }

    @Transactional(transactionManager = "sub2apiTransactionManager")
    public String settleVideo(String jobId, String apiKeyId, String userId, String accountId, BigDecimal amount,
                              String requestedModel, String upstreamModel, String endpoint, int requestedSeconds) {
        String requestId = "video-workbench:" + jobId;
        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT id FROM usage_logs WHERE request_id=? AND api_key_id=?::bigint", requestId, apiKeyId);
        if (!existing.isEmpty()) return String.valueOf(existing.get(0).get("id"));
        jdbc.queryForObject("SELECT id FROM users WHERE id=?::bigint FOR UPDATE", Long.class, userId);
        List<Map<String, Object>> charged = jdbc.queryForList("""
                UPDATE users SET balance=balance-?, frozen_balance=frozen_balance-?, updated_at=NOW()
                WHERE id=?::bigint AND balance>=? AND frozen_balance>=? RETURNING id
                """, amount, amount, userId, amount, amount);
        if (charged.isEmpty()) throw new ImageApiException(409, "Reserved sub2api balance could not be settled.", "BILLING_RESERVATION_INVALID", null);
        jdbc.update("""
                UPDATE api_keys SET quota_used=quota_used+?,
                usage_5h=CASE WHEN window_5h_start IS NULL OR window_5h_start<=NOW()-INTERVAL '5 hours' THEN ? ELSE usage_5h+? END,
                usage_1d=CASE WHEN window_1d_start IS NULL OR window_1d_start<=NOW()-INTERVAL '1 day' THEN ? ELSE usage_1d+? END,
                usage_7d=CASE WHEN window_7d_start IS NULL OR window_7d_start<=NOW()-INTERVAL '7 days' THEN ? ELSE usage_7d+? END,
                window_5h_start=CASE WHEN window_5h_start IS NULL OR window_5h_start<=NOW()-INTERVAL '5 hours' THEN NOW() ELSE window_5h_start END,
                window_1d_start=CASE WHEN window_1d_start IS NULL OR window_1d_start<=NOW()-INTERVAL '1 day' THEN NOW() ELSE window_1d_start END,
                window_7d_start=CASE WHEN window_7d_start IS NULL OR window_7d_start<=NOW()-INTERVAL '7 days' THEN NOW() ELSE window_7d_start END,
                last_used_at=NOW(),updated_at=NOW() WHERE id=?::bigint
                """, amount, amount, amount, amount, amount, amount, amount, apiKeyId);
        Long groupId = jdbc.queryForObject("SELECT group_id FROM api_keys WHERE id=?::bigint", Long.class, apiKeyId);
        Long usageId = jdbc.queryForObject("""
                INSERT INTO usage_logs(user_id,api_key_id,account_id,request_id,model,total_cost,actual_cost,
                  billing_type,billing_mode,request_type,stream,duration_ms,group_id,inbound_endpoint,
                  upstream_endpoint,upstream_model,requested_model)
                VALUES (?::bigint,?::bigint,?::bigint,?,?,?,?,0,'video',1,FALSE,?,?,?,?,?,?) RETURNING id
                """, Long.class, userId, apiKeyId, accountId, requestId, requestedModel, amount, amount,
                requestedSeconds * 1000, groupId, "/api/videos/generate", endpoint, upstreamModel, requestedModel);
        jdbc.update("""
                INSERT INTO billing_usage_entries(usage_log_id,user_id,api_key_id,billing_type,applied,delta_usd)
                VALUES (?,?::bigint,?::bigint,0,TRUE,-(?::numeric)) ON CONFLICT(usage_log_id) DO NOTHING
                """, usageId, userId, apiKeyId, amount);
        jdbc.update("""
                INSERT INTO usage_billing_dedup(request_id,api_key_id,request_fingerprint)
                VALUES (?,?::bigint,?) ON CONFLICT(request_id,api_key_id) DO NOTHING
                """, requestId, apiKeyId, jobId);
        return String.valueOf(usageId);
    }

    private String resolveAccountId(String apiKeyId) {
        if (configuredAccountId != null && !configuredAccountId.isBlank()) {
            if (jdbc.queryForList("SELECT id FROM accounts WHERE id=?::bigint AND deleted_at IS NULL", configuredAccountId).isEmpty())
                throw new ImageApiException(503, "Configured sub2api billing account does not exist.", "BILLING_ACCOUNT_INVALID", null);
            return configuredAccountId;
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT account_id FROM usage_logs WHERE api_key_id=?::bigint
                  AND (model='gpt-image-2' OR requested_model='gpt-image-2' OR upstream_model='cy-img1-gpt-image-2')
                ORDER BY created_at DESC LIMIT 1
                """, apiKeyId);
        if (rows.isEmpty()) rows = jdbc.queryForList("""
                SELECT account_id FROM usage_logs WHERE model='gpt-image-2' OR upstream_model='cy-img1-gpt-image-2'
                ORDER BY created_at DESC LIMIT 1
                """);
        if (rows.isEmpty()) throw new ImageApiException(503, "No sub2api account is available for GPT-Image-2 billing details.", "BILLING_ACCOUNT_MISSING", null);
        return String.valueOf(rows.get(0).get("account_id"));
    }

    private void checkWindow(Map<String, Object> row, String limitKey, String usageKey, String startKey, long duration, BigDecimal amount) {
        BigDecimal limit = decimal(row.get(limitKey));
        if (limit.signum() <= 0) return;
        long started = epoch(row.get(startKey));
        BigDecimal usage = started > 0 && System.currentTimeMillis() - started < duration ? decimal(row.get(usageKey)) : BigDecimal.ZERO;
        if (usage.add(amount).compareTo(limit) > 0)
            throw new ImageApiException(429, "API Key usage limit is insufficient.", "API_KEY_RATE_LIMIT_EXCEEDED", null);
    }

    private long epoch(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.getTime();
        if (value instanceof OffsetDateTime offset) return offset.toInstant().toEpochMilli();
        return 0;
    }

    private static BigDecimal decimal(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }

    private static String money(BigDecimal value) {
        return value.setScale(10).toPlainString();
    }

    public record BillingAccount(String apiKeyId, String userId, String balanceUsd, String availableBalanceUsd) {}
    public record Gateway(String baseUrl, String apiKey) {}
    public record Reservation(String apiKeyId, String userId, String accountId, BigDecimal amountUsd, BigDecimal availableBalanceUsd) {}
}
