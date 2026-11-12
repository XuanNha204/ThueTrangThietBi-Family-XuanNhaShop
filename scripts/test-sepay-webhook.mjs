import { readFileSync, writeFileSync } from "node:fs";
import { resolve, dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createHmac, randomUUID } from "node:crypto";
import { spawnSync } from "node:child_process";
const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const config = JSON.parse(readFileSync(join(root, ".dev/sepay.json"), "utf8"));
const database = JSON.parse(
  readFileSync(join(root, ".dev/config.json"), "utf8"),
);
const base = new URL(config.publicBaseUrl);
if (base.protocol !== "https:" || base.username || base.password)
  throw new Error("Cần URL HTTPS công khai không chứa thông tin xác thực.");
const endpoint = new URL("/api/integrations/sepay/webhook", base);
const reference = "DEVTEST-" + randomUUID();
const parts = Object.fromEntries(
  new Intl.DateTimeFormat("en-GB", {
    timeZone: "Asia/Ho_Chi_Minh",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23",
  })
    .formatToParts(new Date())
    .map((p) => [p.type, p.value]),
);
const payload = {
  id: 0,
  gateway: "DEV_TEST",
  transactionDate: `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`,
  accountNumber: "TEST-XUANNHA",
  subAccount: "",
  code: "XNSMOKE",
  content: "XuanNhaShop webhook connectivity test only",
  transferType: "in",
  transferAmount: 10000,
  referenceCode: reference,
  accumulated: 0,
};
const raw = JSON.stringify(payload),
  checks = [];
function signed(body, timestamp) {
  return (
    "sha256=" +
    createHmac("sha256", config.webhookSecret)
      .update(timestamp + ".")
      .update(body)
      .digest("hex")
  );
}
async function check(name, body, headers, expected) {
  const started = Date.now();
  const response = await fetch(endpoint, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...headers },
    body,
    signal: AbortSignal.timeout(20000),
  });
  const text = await response.text();
  let result;
  try {
    result = JSON.parse(text);
  } catch {
    throw new Error(
      `${name}: HTTP ${response.status}, phản hồi không phải JSON.`,
    );
  }
  if (
    response.status !== expected ||
    (expected === 200 &&
      (result.success !== true || Object.keys(result).length !== 1))
  )
    throw new Error(
      `${name}: HTTP ${response.status}, khác kết quả mong đợi ${expected}.`,
    );
  const row = {
    name,
    status: response.status,
    elapsedMs: Date.now() - started,
    ...(expected === 200 ? { body: result } : {}),
  };
  checks.push(row);
  console.log(JSON.stringify(row));
}
const time = String(Math.floor(Date.now() / 1000)),
  signature = signed(raw, time);
await check("unsigned-rejected", raw, {}, 401);
await check(
  "wrong-signature-rejected",
  raw,
  {
    "X-SePay-Timestamp": time,
    "X-SePay-Signature": "sha256=" + "0".repeat(64),
  },
  401,
);
await check(
  "tampered-body-rejected",
  raw.replace("10000", "10001"),
  { "X-SePay-Timestamp": time, "X-SePay-Signature": signature },
  401,
);
const stale = String(Number(time) - 600);
await check(
  "expired-request-rejected",
  raw,
  { "X-SePay-Timestamp": stale, "X-SePay-Signature": signed(raw, stale) },
  401,
);
await check(
  "signed-sample-accepted",
  raw,
  { "X-SePay-Timestamp": time, "X-SePay-Signature": signature },
  200,
);
await check(
  "duplicate-acknowledged",
  raw,
  { "X-SePay-Timestamp": time, "X-SePay-Signature": signature },
  200,
);
const whitespace = "\n" + raw + "\n";
await check(
  "equivalent-json-deduplicated",
  whitespace,
  { "X-SePay-Timestamp": time, "X-SePay-Signature": signed(whitespace, time) },
  200,
);
if (!/^DEVTEST-[a-f0-9-]+$/.test(reference))
  throw new Error("Invalid test reference");
const pg = process.env.PG_BIN || "C:/Program Files/PostgreSQL/18/bin";
const sql = `select count(*)||'|'||min(delivery_count)||'|'||min(status) from sepay_webhook_event where reference_code='${reference}'`;
const query = spawnSync(
  join(pg, "psql.exe"),
  [
    "-h",
    "127.0.0.1",
    "-p",
    "54329",
    "-U",
    "xuannha",
    "-d",
    "xuannhashop_dev",
    "-tAc",
    sql,
  ],
  {
    env: { ...process.env, PGPASSWORD: database.databasePassword },
    encoding: "utf8",
    windowsHide: true,
  },
);
if (query.status !== 0 || query.stdout.trim() !== "1|3|TEST")
  throw new Error(
    "Đối chiếu inbox chưa đạt: cần một bản ghi TEST với ba lần nhận.",
  );
const report = {
  url: endpoint.href,
  checkedAt: new Date().toISOString(),
  checks,
  reference,
  receiptRows: 1,
  deliveryCount: 3,
  receiptStatus: "TEST",
  samplePostsMoney: false,
};
writeFileSync(
  join(root, ".dev/sepay-test-report.json"),
  JSON.stringify(report, null, 2),
);
console.log(
  JSON.stringify({
    result: "passed",
    receiptRows: 1,
    deliveryCount: 3,
    samplePostsMoney: false,
    report: ".dev/sepay-test-report.json",
  }),
);
