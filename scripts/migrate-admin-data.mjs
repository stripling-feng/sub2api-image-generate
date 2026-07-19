import mysql from "mysql2/promise";
import pg from "pg";

const tables = [
  "sys_config", "sys_dept", "sys_dict_type", "sys_dict_data", "sys_district",
  "sys_job_task", "sys_job_task_log", "sys_menu", "sys_oper_log", "sys_post",
  "sys_role", "sys_role_menu", "sys_upload_file", "sys_user", "sys_user_role"
];
const identityTables = tables.filter((table) => !["sys_role_menu", "sys_user_role"].includes(table));
const required = (name) => process.env[name] || (() => { throw new Error(`${name} is required`); })();

const source = await mysql.createConnection({
  host: process.env.MYSQL_HOST || "127.0.0.1",
  port: Number(process.env.MYSQL_PORT || 3306),
  user: process.env.MYSQL_USERNAME || "root",
  password: required("MYSQL_PASSWORD"),
  database: process.env.MYSQL_DATABASE || "feng-ai-admin",
  dateStrings: true
});
const target = new pg.Client({
  connectionString: process.env.MEDIA_DB_URL?.replace(/^jdbc:/, ""),
  host: process.env.MEDIA_DB_HOST || "5.104.85.25",
  port: Number(process.env.MEDIA_DB_PORT || 5432),
  user: process.env.MEDIA_DB_USERNAME || "root",
  password: required("MEDIA_DB_PASSWORD"),
  database: process.env.MEDIA_DB_DATABASE || "sub2api_media"
});

await target.connect();
await target.query("BEGIN");
try {
  for (const table of tables) {
    const targetCount = Number((await target.query(`SELECT count(*) count FROM "${table}"`)).rows[0].count);
    if (targetCount !== 0) throw new Error(`${table} is not empty`);
    const [rows, fields] = await source.query("SELECT * FROM `" + table + "`");
    const columns = fields.map((field) => field.name);
    for (let offset = 0; offset < rows.length; offset += 200) {
      const batch = rows.slice(offset, offset + 200);
      const values = batch.flatMap((row) => columns.map((column) => row[column]));
      const tuples = batch.map((_, row) => "(" + columns.map((__, column) => `$${row * columns.length + column + 1}`).join(",") + ")").join(",");
      await target.query(`INSERT INTO "${table}" (${columns.map((column) => `"${column}"`).join(",")}) VALUES ${tuples}`, values);
    }
    process.stdout.write(`${table}: ${rows.length}\n`);
  }
  for (const table of identityTables) {
    await target.query(`SELECT setval(pg_get_serial_sequence('${table}','id'),COALESCE(MAX(id),1),MAX(id) IS NOT NULL) FROM "${table}"`);
  }
  await target.query("COMMIT");
} catch (error) {
  await target.query("ROLLBACK");
  throw error;
} finally {
  await source.end();
  await target.end();
}
