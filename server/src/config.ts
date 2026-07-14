import path from "node:path";
import { fileURLToPath } from "node:url";
import dotenv from "dotenv";

dotenv.config();

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const serverRoot = path.resolve(__dirname, "..");
const isProduction = process.env.NODE_ENV === "production";

function defaultSub2apiDatabaseUrl() {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) return undefined;
  const url = new URL(databaseUrl);
  url.pathname = "/sub2api";
  return url.toString();
}

export const config = {
  port: Number(process.env.PORT ?? 3000),
  clientOrigin: process.env.CLIENT_ORIGIN ?? "http://localhost:5173",
  encryptionSecret: process.env.ENCRYPTION_SECRET ?? "",
  uploadDir: path.resolve(serverRoot, process.env.UPLOAD_DIR ?? "./uploads"),
  sessionDays: Number(process.env.SESSION_DAYS ?? 30),
  upstreamBaseUrl: process.env.UPSTREAM_BASE_URL?.trim() || (isProduction ? "http://127.0.0.1:8080" : undefined),
  imageTaskPollIntervalMs: Number(process.env.IMAGE_TASK_POLL_INTERVAL_MS ?? 2_000),
  imageTaskPollTimeoutMs: Number(process.env.IMAGE_TASK_POLL_TIMEOUT_MS ?? 30_000),
  imageTaskMaxDurationMs: Number(process.env.IMAGE_TASK_MAX_DURATION_MS ?? 1_800_000),
  imageTaskLeaseMs: Number(process.env.IMAGE_TASK_LEASE_MS ?? 60_000),
  sub2apiDatabaseUrl: process.env.SUB2API_DATABASE_URL?.trim() || defaultSub2apiDatabaseUrl(),
  gptImage2UnitPriceUsd: Number(process.env.GPT_IMAGE_2_UNIT_PRICE_USD ?? 0.5),
  image2ChargeOnFailure: process.env.IMAGE2_CHARGE_ON_FAILURE?.trim().toLowerCase() === "true",
  sub2apiBillingAccountId: process.env.SUB2API_BILLING_ACCOUNT_ID?.trim() || undefined
};

if (config.encryptionSecret.length < 32) {
  throw new Error("ENCRYPTION_SECRET must be at least 32 characters long.");
}

if (!config.sub2apiDatabaseUrl) {
  throw new Error("SUB2API_DATABASE_URL or DATABASE_URL is required for billing.");
}

if (!Number.isFinite(config.gptImage2UnitPriceUsd) || config.gptImage2UnitPriceUsd <= 0) {
  throw new Error("GPT_IMAGE_2_UNIT_PRICE_USD must be a positive number.");
}
