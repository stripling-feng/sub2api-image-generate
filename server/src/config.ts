import path from "node:path";
import { fileURLToPath } from "node:url";
import dotenv from "dotenv";

dotenv.config();

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const serverRoot = path.resolve(__dirname, "..");

export const config = {
  port: Number(process.env.PORT ?? 3000),
  clientOrigin: process.env.CLIENT_ORIGIN ?? "http://localhost:5173",
  encryptionSecret: process.env.ENCRYPTION_SECRET ?? "",
  uploadDir: path.resolve(serverRoot, process.env.UPLOAD_DIR ?? "./uploads"),
  sessionDays: Number(process.env.SESSION_DAYS ?? 30)
};

if (config.encryptionSecret.length < 32) {
  throw new Error("ENCRYPTION_SECRET must be at least 32 characters long.");
}
