import { execFileSync, spawn } from "node:child_process";
import {
  readFileSync,
  writeFileSync,
  copyFileSync,
  mkdirSync,
  openSync,
  closeSync,
} from "node:fs";
import { resolve, join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { createHash } from "node:crypto";
import { createServer } from "node:net";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const config = JSON.parse(readFileSync(join(root, ".dev/config.json"), "utf8"));
const started = Date.now(),
  port = Number(process.env.DRILL_PORT || 8086);
if (
  !Number.isInteger(port) ||
  port < 1024 ||
  port > 65535 ||
  [8085, 5175, 54329].includes(port)
)
  throw new Error("DRILL_PORT phải là cổng riêng hợp lệ.");
await new Promise((yes, no) => {
  const server = createServer();
  server.once("error", no);
  server.listen(port, "127.0.0.1", () => server.close(yes));
});
function recovery(...args) {
  return JSON.parse(
    execFileSync(
      process.execPath,
      [join(root, "scripts/recovery.mjs"), ...args],
      {
        cwd: root,
        encoding: "utf8",
        windowsHide: true,
        stdio: ["pipe", "pipe", "pipe"],
      },
    ),
  );
}
const backup = process.argv[2]
  ? { folder: resolve(process.argv[2]) }
  : recovery("backup");
const checksum = recovery("verify", backup.folder);
// Corrupt a separate copy; never alter the source backup or the active database.
const negative = join(root, ".dev/recovery-negative", String(started));
mkdirSync(negative, { recursive: true });
copyFileSync(
  join(backup.folder, "manifest.json"),
  join(negative, "manifest.json"),
);
writeFileSync(
  join(negative, "database.dump"),
  Buffer.concat([
    readFileSync(join(backup.folder, "database.dump")),
    Buffer.from("CORRUPT_TEST"),
  ]),
);
let corruptRejected = false;
try {
  recovery("verify", negative);
} catch (e) {
  corruptRejected = String(e.stderr).includes("Checksum database.dump");
}
if (!corruptRejected) throw new Error("Bản dump hỏng chưa được từ chối.");
const restored = recovery("restore", backup.folder),
  reportPath = join(restored.folder, "restore-report.json");
const restoreReport = JSON.parse(readFileSync(reportPath, "utf8"));
const jar = join(restored.folder, "recovery-backend.jar");
copyFileSync(join(root, "backend/target/rental-backend.jar"), jar);
const log = openSync(join(restored.folder, "application-drill.log"), "a");
const child = spawn("java", ["-Dfile.encoding=UTF-8", "-jar", jar], {
  cwd: root,
  windowsHide: true,
  stdio: ["ignore", log, log],
  env: {
    ...process.env,
    DATABASE_URL: `jdbc:postgresql://127.0.0.1:54329/${restored.database}`,
    DATABASE_USER: "xuannha",
    DATABASE_PASSWORD: config.databasePassword,
    DEV_PASSWORD: config.devPassword,
    DEV_SEED: "false",
    JOBS_ENABLED: "false",
    SEPAY_WEBHOOK_ENABLED: "false",
    SEPAY_WEBHOOK_SECRET: "",
    LOGGING_LEVEL_ORG_SPRINGFRAMEWORK: "INFO",
    PORT: String(port),
    SERVER_ADDRESS: "127.0.0.1",
    PRIVATE_FILES: restoreReport.privateFiles,
    NOTIFICATION_FILE: join(restored.folder, "notifications.ndjson"),
    SPRING_PROFILES_ACTIVE: "dev",
  },
});
closeSync(log);
const cookies = new Map();
async function call(path, init = {}) {
  const response = await fetch(`http://127.0.0.1:${port}${path}`, {
    ...init,
    headers: {
      Cookie: [...cookies].map(([k, v]) => `${k}=${v}`).join("; "),
      ...init.headers,
    },
    signal: AbortSignal.timeout(5000),
  });
  for (const value of response.headers.getSetCookie()) {
    const pair = value.split(";")[0],
      i = pair.indexOf("=");
    cookies.set(pair.slice(0, i), pair.slice(i + 1));
  }
  return response;
}
try {
  let ready = false;
  for (let i = 0; i < 90; i++) {
    if (child.exitCode !== null)
      throw new Error("Ứng dụng phục hồi dừng sớm; xem application-drill.log.");
    try {
      const r = await call("/actuator/health");
      if (r.ok && (await r.json()).status === "UP") {
        ready = true;
        break;
      }
    } catch {}
    await new Promise((r) => setTimeout(r, 500));
  }
  if (!ready) throw new Error("Ứng dụng phục hồi chưa healthy.");
  const csrf = await (await call("/api/auth/csrf")).json();
  const login = await call("/api/auth/login", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify({ username: "owner", password: config.devPassword }),
  });
  if (!login.ok)
    throw new Error(
      "Không đăng nhập được vào bản phục hồi bằng tài khoản dev owner.",
    );
  for (const path of [
    "/api/stock",
    "/api/accounting",
    "/api/payroll-extras",
    "/api/rentals",
  ]) {
    const result = await call(path);
    if (!result.ok) throw new Error("API phục hồi lỗi: " + path);
    await result.json();
  }
  const env = {
    ...process.env,
    PGPASSWORD: config.databasePassword,
    PGCLIENTENCODING: "UTF8",
  };
  const rows = JSON.parse(
    execFileSync(
      join(
        process.env.PG_BIN || "C:/Program Files/PostgreSQL/18/bin",
        "psql.exe",
      ),
      [
        "-h",
        "127.0.0.1",
        "-p",
        "54329",
        "-U",
        "xuannha",
        "-d",
        restored.database,
        "-X",
        "-A",
        "-t",
        "-v",
        "ON_ERROR_STOP=1",
      ],
      {
        env,
        input:
          "select coalesce(json_agg(json_build_object('id',id,'checksum',checksum)),'[]') from evidence;",
        encoding: "utf8",
        windowsHide: true,
        stdio: ["pipe", "pipe", "pipe"],
      },
    ).trim(),
  );
  for (const e of rows) {
    const r = await call("/api/evidence/" + e.id);
    if (
      !r.ok ||
      createHash("sha256")
        .update(Buffer.from(await r.arrayBuffer()))
        .digest("hex") !== e.checksum
    )
      throw new Error("Không tải đúng bằng chứng sau phục hồi.");
  }
  const result = {
    status: "DRILL_PASSED",
    backup: backup.folder,
    database: restored.database,
    folder: restored.folder,
    schemaVersion: restoreReport.schemaVersion,
    tableCount: Object.keys(restoreReport.counts).length,
    evidenceFilesDownloaded: rows.length,
    corruptBackupRejected: corruptRejected,
    health: "UP",
    login: "OK",
    businessApis: "OK",
    elapsedSeconds: Math.round((Date.now() - started) / 1000),
    limits:
      "Diễn tập local; không chứng minh RTO/RPO production, PITR hoặc sao lưu ngoài máy.",
  };
  writeFileSync(
    join(restored.folder, "drill-report.json"),
    JSON.stringify(result, null, 2),
  );
  console.log(JSON.stringify(result));
} finally {
  child.kill();
}
