import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import pg from "pg";

const password = process.env.MEDIA_DB_PASSWORD;
if (!password) throw new Error("MEDIA_DB_PASSWORD is required");

const sql = await readFile(resolve(dirname(fileURLToPath(import.meta.url)),
  "../backend/src/main/resources/db/data/model_menus.sql"), "utf8");
const databaseUrl = new URL((process.env.MEDIA_DB_URL ||
  "jdbc:postgresql://5.104.85.25:5432/sub2api_media").replace("jdbc:", ""));
const client = new pg.Client({
  host: databaseUrl.hostname,
  port: Number(databaseUrl.port || 5432),
  database: databaseUrl.pathname.slice(1),
  user: process.env.MEDIA_DB_USERNAME || "root",
  password,
});

await client.connect();
try {
  await client.query("BEGIN");
  await client.query(sql);
  await client.query("COMMIT");
} catch (error) {
  await client.query("ROLLBACK");
  throw error;
} finally {
  await client.end();
}
