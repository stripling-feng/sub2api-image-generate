package com.feng.system.module.image.service;

import com.feng.system.module.image.exception.ImageApiException;

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

/**
 * sub2api 计费服务:直接操作 sub2api 数据库,实现 API Key 校验、余额冻结(预留)、
 * 成功结算扣费、失败释放等计费流程,并通过去重表保证各步骤幂等。
 */
@Service
public class Sub2apiBillingService {

    private final JdbcTemplate jdbc;
    @Value("${image.billing-account-id:}") private String configuredAccountId;

    public Sub2apiBillingService(@Qualifier("sub2apiJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 校验 API Key:要求 Key 与所属用户均为 active、未删除且未过期,
     * 返回账户余额与可用余额(余额减冻结金额)。
     */
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

    /**
     * 读取指定 sub2api 账号的上游网关凭证(base_url 与 api_key),缺失时抛出 503。
     *
     * @param accountId sub2api accounts 表主键
     */
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

    /**
     * 按张数与单价计算费用后冻结余额(预留)。
     */
    @Transactional(transactionManager = "sub2apiTransactionManager")
    public Reservation reserve(String apiKey, int count, BigDecimal unitPrice) {
        return reserveAmount(apiKey, ImageTaskRules.charge(count, unitPrice));
    }

    /**
     * 冻结指定金额:在行锁(FOR UPDATE)保护下依次校验 Key 配额、5h/1d/7d 滑动窗口限额
     * 与可用余额,校验通过后增加用户 frozen_balance,实际扣费延后到结算阶段。
     */
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
        // 配额校验:quota 为 0 或负数视为不限额
        BigDecimal quota = decimal(row.get("quota"));
        if (quota.signum() > 0 && decimal(row.get("quota_used")).add(amount).compareTo(quota) > 0)
            throw new ImageApiException(402, "API Key quota is insufficient for this generation.", "API_KEY_QUOTA_EXCEEDED", null);
        // 三个滑动时间窗限额校验:5 小时 / 1 天 / 7 天
        checkWindow(row, "rate_limit_5h", "usage_5h", "window_5h_start", 5L * 60 * 60 * 1000, amount);
        checkWindow(row, "rate_limit_1d", "usage_1d", "window_1d_start", 24L * 60 * 60 * 1000, amount);
        checkWindow(row, "rate_limit_7d", "usage_7d", "window_7d_start", 7L * 24 * 60 * 60 * 1000, amount);
        // 可用余额 = 余额 - 已冻结金额
        BigDecimal balance = decimal(row.get("balance"));
        BigDecimal frozen = decimal(row.get("frozen_balance"));
        if (balance.subtract(frozen).compareTo(amount) < 0)
            throw new ImageApiException(402, "Insufficient sub2api balance for this generation.", "INSUFFICIENT_BALANCE", null);
        String userId = String.valueOf(row.get("user_id"));
        String apiKeyId = String.valueOf(row.get("api_key_id"));
        jdbc.update("UPDATE users SET frozen_balance=frozen_balance+?, updated_at=NOW() WHERE id=?::bigint", amount, userId);
        return new Reservation(apiKeyId, userId, resolveAccountId(apiKeyId), amount, balance.subtract(frozen).subtract(amount));
    }

    /**
     * 释放图片任务的冻结金额(任务失败/取消时调用)。
     *
     * @return "settled" 表示该任务已被结算过(不再释放),"released" 表示已释放或此前已释放
     */
    @Transactional(transactionManager = "sub2apiTransactionManager")
    public String release(String jobId, String apiKeyId, String userId, BigDecimal amount) {
        return release("image-workbench", jobId, apiKeyId, userId, amount);
    }

    /**
     * 释放视频任务的冻结金额,语义同 {@link #release}。
     */
    @Transactional(transactionManager = "sub2apiTransactionManager")
    public String releaseVideo(String jobId, String apiKeyId, String userId, BigDecimal amount) {
        return release("video-workbench", jobId, apiKeyId, userId, amount);
    }

    private String release(String prefix, String jobId, String apiKeyId, String userId, BigDecimal amount) {
        String requestId = prefix + ":" + jobId;
        // 已存在结算流水说明该任务已扣费,不能再释放冻结
        if (!jdbc.queryForList("SELECT id FROM usage_logs WHERE request_id=? AND api_key_id=?::bigint", requestId, apiKeyId).isEmpty())
            return "settled";
        // 幂等标记:插入去重记录失败(冲突)说明已释放过,直接返回
        int marker = jdbc.update("""
                INSERT INTO usage_billing_dedup(request_id,api_key_id,request_fingerprint)
                VALUES (?,?::bigint,?) ON CONFLICT(request_id,api_key_id) DO NOTHING
                """, prefix + "-release:" + jobId, apiKeyId, jobId);
        if (marker == 0) return "released";
        // 锁定用户行后扣减冻结金额,GREATEST 保证不会出现负数
        jdbc.queryForObject("SELECT id FROM users WHERE id=?::bigint FOR UPDATE", Long.class, userId);
        jdbc.update("UPDATE users SET frozen_balance=GREATEST(frozen_balance-?,0),updated_at=NOW() WHERE id=?::bigint", amount, userId);
        return "released";
    }

    /**
     * 图片任务成功结算:从余额与冻结金额中同时扣除费用,累计 Key 配额与各时间窗用量,
     * 写入 usage_logs 计费流水与去重记录;整个过程以 request_id 幂等,重复调用返回已有流水 ID。
     *
     * @param amount     结算金额(美元)
     * @param count      生成图片张数
     * @param size       图片尺寸(如 1024x1024)
     * @param durationMs 任务耗时(毫秒)
     * @return usage_logs 流水记录 ID
     */
    @Transactional(transactionManager = "sub2apiTransactionManager")
    public String settle(String jobId, String apiKeyId, String userId, String accountId, BigDecimal amount,
                         int count, String size, Integer durationMs) {
        String requestId = "image-workbench:" + jobId;
        // 幂等:同一任务已有流水则直接返回,避免重复扣费
        List<Map<String, Object>> existing = jdbc.queryForList("SELECT id FROM usage_logs WHERE request_id=? AND api_key_id=?::bigint", requestId, apiKeyId);
        if (!existing.isEmpty()) return String.valueOf(existing.get(0).get("id"));
        jdbc.queryForObject("SELECT id FROM users WHERE id=?::bigint FOR UPDATE", Long.class, userId);
        // 原子扣费:余额与冻结金额同时减少,条件不满足(余额被动过)则不更新任何行
        List<Map<String, Object>> charged = jdbc.queryForList("""
                UPDATE users SET balance=balance-?, frozen_balance=frozen_balance-?, updated_at=NOW()
                WHERE id=?::bigint AND balance>=? AND frozen_balance>=? RETURNING id
                """, amount, amount, userId, amount, amount);
        if (charged.isEmpty()) throw new ImageApiException(409, "Reserved sub2api balance could not be settled.", "BILLING_RESERVATION_INVALID", null);
        // 累计 Key 用量:各时间窗过期则重置为本次金额并刷新窗口起点,否则累加
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
        String endpoint = "/v1/images/generations";
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

    /**
     * 视频任务成功结算:流程与 {@link #settle} 相同(扣费、累计用量、写流水),
     * 仅流水字段按视频计费格式填写。
     *
     * @param requestedSeconds 请求的视频时长(秒),用于折算流水中的 duration_ms
     * @return usage_logs 流水记录 ID
     */
    @Transactional(transactionManager = "sub2apiTransactionManager")
    public String settleVideo(String jobId, String apiKeyId, String userId, String accountId, BigDecimal amount,
                              String requestedModel, String upstreamModel, String endpoint, int requestedSeconds) {
        String requestId = "video-workbench:" + jobId;
        // 幂等:已有流水直接返回
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

    // 解析计费挂靠账号:优先使用配置的账号 ID,否则从历史流水中回溯 GPT-Image-2 使用过的账号
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

    // 滑动窗口限额校验:limit<=0 表示不限;窗口已过期则本窗口用量按 0 计算
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

    /** API Key 校验结果:Key ID、用户 ID、余额与可用余额(美元字符串) */
    public record BillingAccount(String apiKeyId, String userId, String balanceUsd, String availableBalanceUsd) {}
    /** 上游网关凭证:基础地址与 API Key */
    public record Gateway(String baseUrl, String apiKey) {}
    /** 冻结结果:Key ID、用户 ID、计费账号 ID、冻结金额及冻结后的可用余额 */
    public record Reservation(String apiKeyId, String userId, String accountId, BigDecimal amountUsd, BigDecimal availableBalanceUsd) {}
}
