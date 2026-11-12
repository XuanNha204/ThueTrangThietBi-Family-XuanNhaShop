import { spawn, spawnSync } from "node:child_process";
import {
  existsSync,
  mkdirSync,
  readFileSync,
  writeFileSync,
  openSync,
  copyFileSync,
} from "node:fs";
import { resolve, dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { randomBytes } from "node:crypto";
import crossSpawn from "cross-spawn";
const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const state = join(root, ".dev");
mkdirSync(state, { recursive: true });
const configPath = join(state, "config.json");
if (!existsSync(configPath))
  writeFileSync(
    configPath,
    JSON.stringify(
      {
        databasePassword: randomBytes(24).toString("base64url"),
        devPassword: "XuanNhaDev!2026",
      },
      null,
      2,
    ),
  );
const config = JSON.parse(readFileSync(configPath, "utf8"));
const sepayPath = join(state, "sepay.json");
const sepay = existsSync(sepayPath)
  ? JSON.parse(readFileSync(sepayPath, "utf8"))
  : {};
const env = {
  ...process.env,
  DEBUG: "false",
  LOGGING_LEVEL_ROOT: "INFO",
  LOGGING_LEVEL_ORG_SPRINGFRAMEWORK: "INFO",
  SPRING_OUTPUT_ANSI_ENABLED: "NEVER",
  SPRING_PROFILES_ACTIVE: "dev",
  SERVER_ADDRESS: "127.0.0.1",
  PORT: "8085",
  DEV_SEED: "true",
  DATABASE_URL: "jdbc:postgresql://127.0.0.1:54329/xuannhashop_dev",
  DATABASE_USER: "xuannha",
  DATABASE_PASSWORD: config.databasePassword,
  DEV_PASSWORD: config.devPassword,
  PRIVATE_FILES: join(state, "private-files"),
  NOTIFICATION_FILE: join(state, "notifications.ndjson"),
  SEPAY_WEBHOOK_SECRET:
    process.env.SEPAY_WEBHOOK_SECRET ?? sepay.webhookSecret ?? "",
  SEPAY_WEBHOOK_ENABLED:
    process.env.SEPAY_WEBHOOK_ENABLED ?? String(sepay.enabled ?? false),
  SEPAY_CHANNEL: process.env.SEPAY_CHANNEL ?? sepay.channel ?? "dev",
};
const pg = process.env.PG_BIN || "C:/Program Files/PostgreSQL/18/bin";
const pgdata = join(state, "postgres");
function run(program, args, cwd = root, extra = {}) {
  const r = crossSpawn.sync(program, args, {
    cwd,
    env,
    stdio: "inherit",
    windowsHide: true,
    ...extra,
  });
  if (r.error) throw r.error;
  if (r.status !== 0)
    throw new Error(`${program} thất bại (exit ${r.status}).`);
  return r;
}
const maven = process.platform === "win32" ? "mvn.cmd" : "mvn";
const npm = process.platform === "win32" ? "npm.cmd" : "npm";
async function database() {
  if (!existsSync(join(pg, "initdb.exe")))
    throw new Error(
      "Chưa có PostgreSQL local. Đặt PG_BIN đến thư mục bin của PostgreSQL để dùng cụm dev riêng.",
    );
  if (!existsSync(join(pgdata, "PG_VERSION"))) {
    const pw = join(state, "initdb-password.tmp");
    writeFileSync(pw, config.databasePassword);
    try {
      run(join(pg, "initdb.exe"), [
        "-D",
        pgdata,
        "-U",
        "xuannha",
        "--encoding=UTF8",
        "--locale=C",
        "--auth-host=scram-sha-256",
        "--auth-local=scram-sha-256",
        "--pwfile",
        pw,
      ]);
    } finally {
      const { unlinkSync } = await import("node:fs");
      if (existsSync(pw)) unlinkSync(pw);
    }
  }
  const status = spawnSync(join(pg, "pg_ctl.exe"), ["-D", pgdata, "status"], {
    windowsHide: true,
    stdio: "ignore",
  });
  if (status.status !== 0)
    run(join(pg, "pg_ctl.exe"), [
      "-D",
      pgdata,
      "-l",
      join(state, "postgres.log"),
      "-o",
      "-h 127.0.0.1 -p 54329 -c max_connections=40 -c shared_buffers=64MB",
      "-w",
      "start",
    ]);
  const pgEnv = { ...env, PGPASSWORD: config.databasePassword };
  const result = spawnSync(
    join(pg, "psql.exe"),
    [
      "-h",
      "127.0.0.1",
      "-p",
      "54329",
      "-U",
      "xuannha",
      "-d",
      "postgres",
      "-tAc",
      "select 1 from pg_database where datname='xuannhashop_dev'",
    ],
    { env: pgEnv, encoding: "utf8", windowsHide: true },
  );
  if (result.status !== 0)
    throw new Error("Không kết nối được PostgreSQL dev: " + result.stderr);
  if (!result.stdout.includes("1"))
    run(
      join(pg, "createdb.exe"),
      ["-h", "127.0.0.1", "-p", "54329", "-U", "xuannha", "xuannhashop_dev"],
      root,
      { env: pgEnv },
    );
}
async function healthy(url) {
  try {
    return (await fetch(url, { signal: AbortSignal.timeout(1500) })).ok;
  } catch {
    return false;
  }
}
async function waitFor(url, seconds) {
  for (let n = 0; n < seconds; n++) {
    if (await healthy(url)) return;
    await new Promise((r) => setTimeout(r, 1000));
  }
  throw new Error(`Chưa khởi động được ${url}. Xem .dev/*.log.`);
}
function launch(name, program, args, cwd) {
  const log = openSync(join(state, name + ".log"), "a");
  const childEnv = { ...env };
  if (name === "frontend") delete childEnv.SEPAY_WEBHOOK_SECRET;
  const child = spawn(program, args, {
    cwd,
    env: childEnv,
    detached: true,
    windowsHide: true,
    stdio: ["ignore", log, log],
  });
  child.unref();
  writeFileSync(join(state, name + ".pid"), String(child.pid));
}
async function stopApp(name) {
  const path = join(state, name + ".pid");
  if (!existsSync(path)) return;
  const pid = Number(readFileSync(path, "utf8"));
  if (!Number.isSafeInteger(pid) || pid <= 0) return;
  const result = spawnSync(
    "powershell.exe",
    [
      "-NoProfile",
      "-Command",
      `(Get-CimInstance Win32_Process -Filter 'ProcessId = ${pid}').CommandLine`,
    ],
    { encoding: "utf8", windowsHide: true },
  );
  if (
    result.stdout
      ?.replaceAll("\\", "/")
      .toLowerCase()
      .includes(root.replaceAll("\\", "/").toLowerCase())
  ) {
    try {
      process.kill(pid);
    } catch {}
    await new Promise((r) => setTimeout(r, 600));
  }
}
function backend() {
  const runtime = join(state, "runtime");
  mkdirSync(runtime, { recursive: true });
  const jar = join(runtime, "rental-backend.jar");
  copyFileSync(join(root, "backend/target/rental-backend.jar"), jar);
  launch(
    "backend",
    "java",
    ["-Dfile.encoding=UTF-8", "-jar", jar],
    join(root, "backend"),
  );
}
const command = process.argv[2] || "status";
try {
  if (command === "db") {
    await database();
    console.log("PostgreSQL dev sẵn sàng.");
  } else if (command === "setup") {
    await database();
    run(npm, ["ci"], join(root, "web-ui"));
    run(maven, ["-B", "-ntp", "-DskipTests", "package"], join(root, "backend"));
    console.log("Đã chuẩn bị môi trường. Chạy npm run dev.");
  } else if (command === "start") {
    await database();
    if (!existsSync(join(root, "backend/target/rental-backend.jar")))
      run(
        maven,
        ["-B", "-ntp", "-DskipTests", "package"],
        join(root, "backend"),
      );
    if (!existsSync(join(root, "web-ui/node_modules")))
      run(npm, ["ci"], join(root, "web-ui"));
    if (!(await healthy("http://127.0.0.1:8085/actuator/health"))) backend();
    await waitFor("http://127.0.0.1:8085/actuator/health", 60);
    if (!(await healthy("http://127.0.0.1:5175")))
      launch(
        "frontend",
        process.execPath,
        [
          join(root, "web-ui/node_modules/vite/bin/vite.js"),
          "--host",
          "127.0.0.1",
          "--port",
          "5175",
          "--strictPort",
        ],
        join(root, "web-ui"),
      );
    await waitFor("http://127.0.0.1:5175", 20);
    console.log(
      "XuanNhaShop dev: http://127.0.0.1:5175\nAPI: http://127.0.0.1:8085/api\nTài khoản mẫu: owner / accountant / stock / delivery / technician / hr / customer\nMật khẩu dev: " +
        config.devPassword,
    );
  } else if (command === "restart-backend") {
    await database();
    await stopApp("backend");
    run(maven, ["-B", "-ntp", "-DskipTests", "package"], join(root, "backend"));
    backend();
    await waitFor("http://127.0.0.1:8085/actuator/health", 60);
    console.log("Đã build lại và khởi động backend dev.");
  } else if (command === "build") {
    run(maven, ["-B", "-ntp", "-DskipTests", "package"], join(root, "backend"));
    run(npm, ["run", "build"], join(root, "web-ui"));
  } else if (command === "test") {
    await database();
    const db = "xuannhashop_test_" + Date.now();
    const pgEnv = { ...env, PGPASSWORD: config.databasePassword };
    run(
      join(pg, "createdb.exe"),
      ["-h", "127.0.0.1", "-p", "54329", "-U", "xuannha", db],
      root,
      { env: pgEnv },
    );
    try {
      run(maven, ["-B", "-ntp", "clean", "test"], join(root, "backend"), {
        env: {
          ...env,
          DATABASE_URL: `jdbc:postgresql://127.0.0.1:54329/${db}`,
          JOBS_ENABLED: "false",
          SEPAY_WEBHOOK_ENABLED: "false",
          SEPAY_WEBHOOK_SECRET: "",
          PRIVATE_FILES: join(state, "tests", db, "private-files"),
          NOTIFICATION_FILE: join(state, "tests", db, "notifications.ndjson"),
        },
      });
    } finally {
      if (!/^xuannhashop_test_[0-9]+$/.test(db))
        throw new Error("Tên database test không hợp lệ.");
      run(
        join(pg, "dropdb.exe"),
        ["-h", "127.0.0.1", "-p", "54329", "-U", "xuannha", db],
        root,
        { env: pgEnv },
      );
    }
  } else if (command === "stop") {
    for (const name of ["frontend", "backend"]) await stopApp(name);
    if (
      existsSync(join(pgdata, "PG_VERSION")) &&
      spawnSync(join(pg, "pg_ctl.exe"), ["-D", pgdata, "status"], {
        windowsHide: true,
        stdio: "ignore",
      }).status === 0
    )
      run(join(pg, "pg_ctl.exe"), ["-D", pgdata, "-m", "fast", "stop"]);
  } else {
    console.log(
      "Frontend:",
      (await healthy("http://127.0.0.1:5175")) ? "đang chạy" : "đang dừng",
    );
    console.log(
      "Backend:",
      (await healthy("http://127.0.0.1:8085/actuator/health"))
        ? "đang chạy"
        : "đang dừng",
    );
  }
} catch (e) {
  console.error(e.message);
  process.exitCode = 1;
}
