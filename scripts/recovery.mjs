import { execFileSync } from "node:child_process";
import {
  mkdirSync,
  readFileSync,
  writeFileSync,
  copyFileSync,
  existsSync,
  realpathSync,
  lstatSync,
} from "node:fs";
import { resolve, join, dirname, relative, isAbsolute } from "node:path";
import { fileURLToPath } from "node:url";
import { createHash, randomBytes } from "node:crypto";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const config = JSON.parse(readFileSync(join(root, ".dev/config.json"), "utf8"));
const pg = process.env.PG_BIN || "C:/Program Files/PostgreSQL/18/bin";
const env = {
  ...process.env,
  PGPASSWORD: config.databasePassword,
  PGCLIENTENCODING: "UTF8",
};
const connection = ["-h", "127.0.0.1", "-p", "54329", "-U", "xuannha"];
const stamp =
  new Date().toISOString().replace(/[^0-9]/g, "") +
  "_" +
  randomBytes(3).toString("hex");
function run(name, args, input) {
  return execFileSync(
    join(pg, name + (process.platform === "win32" ? ".exe" : "")),
    args,
    {
      env,
      input,
      encoding: "utf8",
      windowsHide: true,
      stdio: ["pipe", "pipe", "pipe"],
      maxBuffer: 32 * 1024 * 1024,
    },
  );
}
function sql(database, query) {
  return run(
    "psql",
    [...connection, "-X", "-A", "-t", "-v", "ON_ERROR_STOP=1", "-d", database],
    query,
  ).trim();
}
function sha(path) {
  return createHash("sha256").update(readFileSync(path)).digest("hex");
}
function contained(base, path) {
  const rel = relative(realpathSync(base), realpathSync(path));
  if (
    !rel ||
    isAbsolute(rel) ||
    rel === ".." ||
    rel.startsWith(".." + (process.platform === "win32" ? "\\" : "/")) ||
    lstatSync(path).isSymbolicLink()
  )
    throw new Error("Đường dẫn bản sao lưu không hợp lệ.");
  return path;
}
function facts(db) {
  const tables = JSON.parse(
    sql(
      db,
      "select coalesce(json_agg(tablename order by tablename),'[]') from pg_tables where schemaname='public';",
    ),
  );
  const counts = {};
  for (const table of tables) {
    if (!/^[a-z_][a-z0-9_]*$/.test(table))
      throw new Error("Tên bảng không được hỗ trợ.");
    counts[table] = Number(sql(db, `select count(*) from "${table}";`));
  }
  const evidence = JSON.parse(
    sql(
      db,
      "select coalesce(json_agg(json_build_object('key',file_key,'checksum',checksum,'size',size)),'[]') from evidence;",
    ),
  );
  return {
    counts,
    evidence,
    schemaVersion: sql(
      db,
      "select version from flyway_schema_history where success order by installed_rank desc limit 1;",
    ),
  };
}
function createRestore(dump) {
  const db = "xuannhashop_restore_" + stamp;
  run("createdb", [...connection, db]);
  try {
    run("pg_restore", [
      ...connection,
      "--no-owner",
      "--no-privileges",
      "--single-transaction",
      "--exit-on-error",
      "-d",
      db,
      dump,
    ]);
    return db;
  } catch (error) {
    throw new Error(
      `Phục hồi thất bại; DB cách ly ${db} được giữ để điều tra. Dữ liệu dev không thay đổi.`,
      { cause: error },
    );
  }
}
function validateEvidence(e) {
  if (
    !/^[0-9a-f-]{36}\.(png|jpg|pdf)$/.test(e.key) ||
    !/^[0-9a-f]{64}$/.test(e.checksum) ||
    !Number.isSafeInteger(Number(e.size))
  )
    throw new Error("Metadata bằng chứng không hợp lệ.");
}

const mode = process.argv[2];
try {
  if (mode === "backup") {
    const folder = join(root, ".dev/backups", stamp);
    mkdirSync(join(folder, "private-files"), { recursive: true });
    const dump = join(folder, "database.dump");
    run("pg_dump", [
      ...connection,
      "--format=custom",
      "--no-owner",
      "--no-privileges",
      "--file",
      dump,
      "xuannhashop_dev",
    ]);
    // Inspect the exact dump snapshot in an isolated database; live evidence files are immutable.
    const db = createRestore(dump);
    const snapshot = facts(db);
    for (const e of snapshot.evidence) {
      validateEvidence(e);
      const source = contained(
        join(root, ".dev/private-files"),
        join(root, ".dev/private-files", e.key),
      );
      if (
        sha(source) !== e.checksum ||
        lstatSync(source).size !== Number(e.size)
      )
        throw new Error("Bằng chứng không khớp checksum: " + e.key);
      copyFileSync(source, join(folder, "private-files", e.key));
    }
    writeFileSync(
      join(folder, "manifest.json"),
      JSON.stringify(
        {
          format: 1,
          createdAt: new Date().toISOString(),
          database: "xuannhashop_dev",
          dumpSha256: sha(dump),
          ...snapshot,
        },
        null,
        2,
      ),
    );
    // Drop only the uniquely named temporary database created by this invocation.
    if (!/^xuannhashop_restore_[0-9]+_[0-9a-f]{6}$/.test(db))
      throw new Error("Tên DB tạm không hợp lệ.");
    run("dropdb", [...connection, db]);
    console.log(
      JSON.stringify({
        status: "VERIFIED_BACKUP",
        folder,
        schemaVersion: snapshot.schemaVersion,
        evidenceFiles: snapshot.evidence.length,
        tableCount: Object.keys(snapshot.counts).length,
      }),
    );
  } else if (mode === "verify" || mode === "restore") {
    if (!process.argv[3])
      throw new Error("Cần đường dẫn thư mục chứa manifest.json.");
    const folder = realpathSync(resolve(process.argv[3]));
    const manifest = JSON.parse(
      readFileSync(contained(folder, join(folder, "manifest.json")), "utf8"),
    );
    if (
      manifest.format !== 1 ||
      !manifest.counts ||
      !Array.isArray(manifest.evidence)
    )
      throw new Error("Manifest không được hỗ trợ.");
    const dump = contained(folder, join(folder, "database.dump"));
    if (sha(dump) !== manifest.dumpSha256)
      throw new Error("Checksum database.dump không khớp.");
    for (const e of manifest.evidence) {
      validateEvidence(e);
      const file = contained(folder, join(folder, "private-files", e.key));
      if (sha(file) !== e.checksum || lstatSync(file).size !== Number(e.size))
        throw new Error("Checksum bằng chứng không khớp.");
    }
    if (mode === "verify") {
      console.log(
        JSON.stringify({
          status: "CHECKSUM_OK",
          folder,
          evidenceFiles: manifest.evidence.length,
        }),
      );
    } else {
      const db = createRestore(dump);
      const snapshot = facts(db);
      if (
        JSON.stringify(snapshot.counts) !== JSON.stringify(manifest.counts) ||
        snapshot.schemaVersion !== manifest.schemaVersion
      )
        throw new Error("Số lượng dữ liệu sau phục hồi không khớp.");
      if (
        JSON.stringify(
          [...snapshot.evidence].sort((a, b) => a.key.localeCompare(b.key)),
        ) !==
        JSON.stringify(
          [...manifest.evidence].sort((a, b) => a.key.localeCompare(b.key)),
        )
      )
        throw new Error("Metadata bằng chứng phục hồi không khớp.");
      const target = join(root, ".dev/restores", db);
      mkdirSync(join(target, "private-files"), { recursive: true });
      for (const e of snapshot.evidence)
        copyFileSync(
          join(folder, "private-files", e.key),
          join(target, "private-files", e.key),
        );
      writeFileSync(
        join(target, "restore-report.json"),
        JSON.stringify(
          {
            status: "RESTORED_ISOLATED",
            database: db,
            privateFiles: join(target, "private-files"),
            source: folder,
            verifiedAt: new Date().toISOString(),
            counts: snapshot.counts,
            schemaVersion: snapshot.schemaVersion,
          },
          null,
          2,
        ),
      );
      console.log(
        JSON.stringify({
          status: "RESTORED_ISOLATED",
          database: db,
          folder: target,
          evidenceFiles: snapshot.evidence.length,
          tableCount: Object.keys(snapshot.counts).length,
        }),
      );
    }
  } else
    throw new Error(
      "Dùng: node scripts/recovery.mjs backup | verify <folder> | restore <folder>",
    );
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
