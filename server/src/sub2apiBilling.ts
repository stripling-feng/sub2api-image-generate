import pg, { type PoolClient } from "pg";
import { config } from "./config.js";

const pool = new pg.Pool({
  connectionString: config.sub2apiDatabaseUrl,
  options: "-c default_transaction_read_only=off",
  max: 10,
  connectionTimeoutMillis: 10_000,
  idleTimeoutMillis: 30_000
});

export async function assertSub2apiBillingReady(): Promise<void> {
  const client = await pool.connect();
  try {
    const state = await client.query(`
      SELECT pg_is_in_recovery() AS recovery,
             current_setting('transaction_read_only') AS transaction_read_only,
             has_column_privilege(current_user, 'users', 'balance', 'UPDATE')
               AND has_column_privilege(current_user, 'users', 'frozen_balance', 'UPDATE') AS users_update,
             has_column_privilege(current_user, 'api_keys', 'quota_used', 'UPDATE')
               AND has_column_privilege(current_user, 'api_keys', 'usage_5h', 'UPDATE') AS api_keys_update,
             has_table_privilege(current_user, 'usage_logs', 'INSERT') AS usage_insert,
             has_table_privilege(current_user, 'billing_usage_entries', 'INSERT') AS billing_insert
    `);
    const row = state.rows[0];
    if (row.recovery || row.transaction_read_only !== "off" || !row.users_update || !row.api_keys_update || !row.usage_insert || !row.billing_insert) {
      throw new Error("SUB2API_DATABASE_URL is not a writable sub2api billing connection.");
    }
    await client.query("BEGIN");
    await client.query("SELECT id FROM users ORDER BY id LIMIT 1 FOR UPDATE");
    await client.query("ROLLBACK");
  } catch (error) {
    await client.query("ROLLBACK").catch(() => undefined);
    throw error;
  } finally {
    client.release();
  }
}

export type BillingReservation = {
  apiKeyId: string;
  userId: string;
  accountId: string;
  amountUsd: string;
  availableBalanceUsd: string;
};

export class BillingError extends Error {
  status: number;
  code: string;

  constructor(message: string, status: number, code: string) {
    super(message);
    this.name = "BillingError";
    this.status = status;
    this.code = code;
  }
}

export function imageChargeUsd(count: number): string {
  const safeCount = Math.max(1, Math.min(10, Math.floor(Number(count) || 1)));
  return (config.gptImage2UnitPriceUsd * safeCount).toFixed(10);
}

export function billingUsageEntryDeltaSql(parameter: string): string {
  return `-(${parameter}::numeric)`;
}

export async function validateSub2apiApiKey(apiKey: string): Promise<{
  apiKeyId: string;
  userId: string;
  balanceUsd: string;
  availableBalanceUsd: string;
}> {
  const result = await pool.query(`
    SELECT k.id AS api_key_id, k.user_id, u.balance, u.frozen_balance
    FROM api_keys k
    JOIN users u ON u.id = k.user_id
    WHERE k.key = $1
      AND k.status = 'active'
      AND k.deleted_at IS NULL
      AND (k.expires_at IS NULL OR k.expires_at > NOW())
      AND u.status = 'active'
      AND u.deleted_at IS NULL
    LIMIT 1
  `, [apiKey]);
  if (!result.rowCount) throw new BillingError("Invalid, expired, or disabled sub2api API Key.", 401, "INVALID_API_KEY");
  const row = result.rows[0];
  const balance = Number(row.balance ?? 0);
  const frozen = Number(row.frozen_balance ?? 0);
  return {
    apiKeyId: String(row.api_key_id),
    userId: String(row.user_id),
    balanceUsd: balance.toFixed(10),
    availableBalanceUsd: (balance - frozen).toFixed(10)
  };
}

export async function getBillingAccountGateway(accountId: string): Promise<{ baseUrl: string; apiKey: string }> {
  const result = await pool.query(`
    SELECT credentials->>'base_url' AS base_url, credentials->>'api_key' AS api_key
    FROM accounts
    WHERE id = $1 AND status = 'active' AND deleted_at IS NULL
    LIMIT 1
  `, [accountId]);
  const baseUrl = result.rows[0]?.base_url;
  const apiKey = result.rows[0]?.api_key;
  if (!result.rowCount || typeof baseUrl !== "string" || !baseUrl.trim() || typeof apiKey !== "string" || !apiKey.trim()) {
    throw new BillingError("The selected sub2api data source has no usable upstream credentials.", 503, "UPSTREAM_ACCOUNT_INVALID");
  }
  return { baseUrl: baseUrl.trim(), apiKey: apiKey.trim() };
}

async function transaction<T>(callback: (client: PoolClient) => Promise<T>): Promise<T> {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const result = await callback(client);
    await client.query("COMMIT");
    return result;
  } catch (error) {
    await client.query("ROLLBACK").catch(() => undefined);
    throw error;
  } finally {
    client.release();
  }
}

async function resolveAccountId(client: PoolClient, apiKeyId: string): Promise<string> {
  if (config.sub2apiBillingAccountId) {
    const configured = await client.query("SELECT id FROM accounts WHERE id = $1 AND deleted_at IS NULL", [config.sub2apiBillingAccountId]);
    if (!configured.rowCount) throw new BillingError("Configured sub2api billing account does not exist.", 503, "BILLING_ACCOUNT_INVALID");
    return String(configured.rows[0].id);
  }

  const result = await client.query(`
    SELECT account_id
    FROM usage_logs
    WHERE api_key_id = $1
      AND (model = 'gpt-image-2' OR requested_model = 'gpt-image-2' OR upstream_model = 'cy-img1-gpt-image-2')
    ORDER BY created_at DESC
    LIMIT 1
  `, [apiKeyId]);
  if (result.rowCount) return String(result.rows[0].account_id);

  const modelFallback = await client.query(`
    SELECT account_id
    FROM usage_logs
    WHERE model = 'gpt-image-2' OR upstream_model = 'cy-img1-gpt-image-2'
    ORDER BY created_at DESC
    LIMIT 1
  `);
  if (modelFallback.rowCount) return String(modelFallback.rows[0].account_id);

  throw new BillingError("No sub2api account is available for GPT-Image-2 billing details.", 503, "BILLING_ACCOUNT_MISSING");
}

function effectiveWindowUsage(row: Record<string, unknown>, usageKey: string, startKey: string, durationMs: number) {
  const start = row[startKey] instanceof Date ? (row[startKey] as Date).getTime() : 0;
  return start && Date.now() - start < durationMs ? Number(row[usageKey] ?? 0) : 0;
}

export async function reserveImageCharge(apiKey: string, count: number): Promise<BillingReservation> {
  const amountUsd = imageChargeUsd(count);
  return transaction(async (client) => {
    const result = await client.query(`
      SELECT
        k.id AS api_key_id, k.user_id, k.quota, k.quota_used,
        k.rate_limit_5h, k.rate_limit_1d, k.rate_limit_7d,
        k.usage_5h, k.usage_1d, k.usage_7d,
        k.window_5h_start, k.window_1d_start, k.window_7d_start,
        u.balance, u.frozen_balance
      FROM api_keys k
      JOIN users u ON u.id = k.user_id
      WHERE k.key = $1
        AND k.status = 'active'
        AND k.deleted_at IS NULL
        AND (k.expires_at IS NULL OR k.expires_at > NOW())
        AND u.status = 'active'
        AND u.deleted_at IS NULL
      FOR UPDATE OF k, u
    `, [apiKey]);
    if (!result.rowCount) throw new BillingError("Invalid, expired, or disabled sub2api API Key.", 401, "INVALID_API_KEY");

    const row = result.rows[0] as Record<string, unknown>;
    const amount = Number(amountUsd);
    const quota = Number(row.quota ?? 0);
    const quotaUsed = Number(row.quota_used ?? 0);
    if (quota > 0 && quotaUsed + amount > quota) {
      throw new BillingError("API Key quota is insufficient for this generation.", 402, "API_KEY_QUOTA_EXCEEDED");
    }

    const windows = [
      ["rate_limit_5h", "usage_5h", "window_5h_start", 5 * 60 * 60 * 1000],
      ["rate_limit_1d", "usage_1d", "window_1d_start", 24 * 60 * 60 * 1000],
      ["rate_limit_7d", "usage_7d", "window_7d_start", 7 * 24 * 60 * 60 * 1000]
    ] as const;
    for (const [limitKey, usageKey, startKey, durationMs] of windows) {
      const limit = Number(row[limitKey] ?? 0);
      if (limit > 0 && effectiveWindowUsage(row, usageKey, startKey, durationMs) + amount > limit) {
        throw new BillingError(`API Key ${limitKey.replace("rate_limit_", "")} usage limit is insufficient.`, 429, "API_KEY_RATE_LIMIT_EXCEEDED");
      }
    }

    const balance = Number(row.balance ?? 0);
    const frozen = Number(row.frozen_balance ?? 0);
    if (balance - frozen < amount) {
      throw new BillingError("Insufficient sub2api balance for this generation.", 402, "INSUFFICIENT_BALANCE");
    }

    await client.query("UPDATE users SET frozen_balance = frozen_balance + $1, updated_at = NOW() WHERE id = $2", [amountUsd, row.user_id]);
    const accountId = await resolveAccountId(client, String(row.api_key_id));
    return {
      apiKeyId: String(row.api_key_id),
      userId: String(row.user_id),
      accountId,
      amountUsd,
      availableBalanceUsd: (balance - frozen - amount).toFixed(10)
    };
  });
}

export async function releaseImageCharge(args: {
  jobId: string;
  apiKeyId: string;
  userId: string;
  amountUsd: string;
}): Promise<"released" | "settled"> {
  return transaction(async (client) => {
    const requestId = `image-workbench:${args.jobId}`;
    const existing = await client.query("SELECT id FROM usage_logs WHERE request_id = $1 AND api_key_id = $2", [requestId, args.apiKeyId]);
    if (existing.rowCount) return "settled";
    const releaseId = `image-workbench-release:${args.jobId}`;
    const marker = await client.query(`
      INSERT INTO usage_billing_dedup (request_id, api_key_id, request_fingerprint)
      VALUES ($1, $2, $3)
      ON CONFLICT (request_id, api_key_id) DO NOTHING
      RETURNING id
    `, [releaseId, args.apiKeyId, args.jobId]);
    if (!marker.rowCount) return "released";
    await client.query("SELECT id FROM users WHERE id = $1 FOR UPDATE", [args.userId]);
    await client.query(`
      UPDATE users
      SET frozen_balance = GREATEST(frozen_balance - $1, 0), updated_at = NOW()
      WHERE id = $2
    `, [args.amountUsd, args.userId]);
    return "released";
  });
}

export async function settleImageCharge(args: {
  jobId: string;
  apiKeyId: string;
  userId: string;
  accountId: string;
  amountUsd: string;
  count: number;
  size: string;
  operation: "generations" | "edits";
  durationMs?: number | null;
}): Promise<{ usageLogId: string }> {
  return transaction(async (client) => {
    const requestId = `image-workbench:${args.jobId}`;
    const existing = await client.query("SELECT id FROM usage_logs WHERE request_id = $1 AND api_key_id = $2", [requestId, args.apiKeyId]);
    if (existing.rowCount) return { usageLogId: String(existing.rows[0].id) };

    await client.query("SELECT id FROM users WHERE id = $1 FOR UPDATE", [args.userId]);
    const charged = await client.query(`
      UPDATE users
      SET balance = balance - $1,
          frozen_balance = frozen_balance - $1,
          updated_at = NOW()
      WHERE id = $2 AND balance >= $1 AND frozen_balance >= $1
      RETURNING id
    `, [args.amountUsd, args.userId]);
    if (!charged.rowCount) throw new BillingError("Reserved sub2api balance could not be settled.", 409, "BILLING_RESERVATION_INVALID");

    await client.query(`
      UPDATE api_keys
      SET quota_used = quota_used + $1,
          usage_5h = CASE WHEN window_5h_start IS NULL OR window_5h_start <= NOW() - INTERVAL '5 hours' THEN $1 ELSE usage_5h + $1 END,
          usage_1d = CASE WHEN window_1d_start IS NULL OR window_1d_start <= NOW() - INTERVAL '1 day' THEN $1 ELSE usage_1d + $1 END,
          usage_7d = CASE WHEN window_7d_start IS NULL OR window_7d_start <= NOW() - INTERVAL '7 days' THEN $1 ELSE usage_7d + $1 END,
          window_5h_start = CASE WHEN window_5h_start IS NULL OR window_5h_start <= NOW() - INTERVAL '5 hours' THEN NOW() ELSE window_5h_start END,
          window_1d_start = CASE WHEN window_1d_start IS NULL OR window_1d_start <= NOW() - INTERVAL '1 day' THEN NOW() ELSE window_1d_start END,
          window_7d_start = CASE WHEN window_7d_start IS NULL OR window_7d_start <= NOW() - INTERVAL '7 days' THEN NOW() ELSE window_7d_start END,
          last_used_at = NOW(), updated_at = NOW()
      WHERE id = $2
    `, [args.amountUsd, args.apiKeyId]);

    const key = await client.query("SELECT group_id FROM api_keys WHERE id = $1", [args.apiKeyId]);
    const endpoint = `/v1/images/${args.operation}`;
    const usage = await client.query(`
      INSERT INTO usage_logs (
        user_id, api_key_id, account_id, request_id, model,
        total_cost, actual_cost, image_output_cost, image_count, image_size,
        billing_type, billing_mode, request_type, stream, duration_ms, group_id,
        inbound_endpoint, upstream_endpoint, upstream_model, requested_model,
        image_output_size, image_size_source
      ) VALUES (
        $1, $2, $3, $4, 'gpt-image-2',
        $5, $5, $5, $6, '1K',
        0, 'image', 1, FALSE, $7, $8,
        $9, $9, 'cy-img1-gpt-image-2', 'gpt-image-2',
        $10, 'output'
      ) RETURNING id
    `, [args.userId, args.apiKeyId, args.accountId, requestId, args.amountUsd, args.count, args.durationMs ?? null, key.rows[0]?.group_id ?? null, endpoint, args.size]);
    const usageLogId = String(usage.rows[0].id);

    await client.query(`
      INSERT INTO billing_usage_entries (usage_log_id, user_id, api_key_id, billing_type, applied, delta_usd)
      VALUES ($1, $2, $3, 0, TRUE, ${billingUsageEntryDeltaSql("$4")})
      ON CONFLICT (usage_log_id) DO NOTHING
    `, [usageLogId, args.userId, args.apiKeyId, args.amountUsd]);
    await client.query(`
      INSERT INTO usage_billing_dedup (request_id, api_key_id, request_fingerprint)
      VALUES ($1, $2, $3)
      ON CONFLICT (request_id, api_key_id) DO NOTHING
    `, [requestId, args.apiKeyId, args.jobId]);
    return { usageLogId };
  });
}
