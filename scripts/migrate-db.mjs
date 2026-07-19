import { spawnSync } from "node:child_process";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const password = process.env.MEDIA_DB_PASSWORD;
if (!password) throw new Error("MEDIA_DB_PASSWORD is required");
const url = process.env.MEDIA_DB_URL || "jdbc:postgresql://5.104.85.25:5432/sub2api_media";
const username = process.env.MEDIA_DB_USERNAME || "root";
const migrationDir = resolve(dirname(fileURLToPath(import.meta.url)), "../backend/src/main/resources/db/migration");

const result = spawnSync(process.platform === "win32" ? "docker.exe" : "docker", [
  "run", "--rm", "-v", `${migrationDir}:/flyway/sql`, "flyway/flyway:12.11.0",
  `-url=${url}`, `-user=${username}`, `-password=${password}`, "migrate"
], { stdio: "inherit" });

if (result.error) throw result.error;
if (result.status !== 0) process.exit(result.status ?? 1);
