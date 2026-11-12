import { readFileSync, writeFileSync, mkdirSync, existsSync } from "node:fs";
import { resolve, dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const lines = readFileSync(join(root, "TaiLieu.md"), "utf8")
  .replace(/^\uFEFF/, "")
  .split(/\r?\n/);
const matches = lines.filter(
  (line) =>
    line.includes(":") && /hmac/i.test(line.slice(0, line.indexOf(":"))),
);
if (matches.length !== 1)
  throw new Error(
    "TaiLieu.md cần đúng một dòng HMAC-SHA256 Secret Key: <giá trị>.",
  );
let secret = matches[0].slice(matches[0].indexOf(":") + 1).trim();
if (
  secret.length >= 2 &&
  ['"', "'", "`"].some(
    (quote) => secret.startsWith(quote) && secret.endsWith(quote),
  )
)
  secret = secret.slice(1, -1);
if (secret.length < 16 || secret.length > 1024 || secret.startsWith("spsk_"))
  throw new Error(
    "Khóa HMAC webhook chưa hợp lệ. Giữ Merchant Secret ở cấu hình riêng.",
  );
const state = join(root, ".dev");
mkdirSync(state, { recursive: true });
const previousPath = join(state, "sepay.json");
const previous = existsSync(previousPath)
  ? JSON.parse(readFileSync(previousPath, "utf8"))
  : {};
writeFileSync(
  join(state, "sepay.json"),
  JSON.stringify(
    {
      enabled: previous.enabled ?? true,
      webhookSecret: secret,
      channel: previous.channel ?? "dev",
      publicBaseUrl:
        previous.publicBaseUrl ?? "https://bm4mbskm-5175.asse.devtunnels.ms",
    },
    null,
    2,
  ),
  { mode: 0o600 },
);
console.log(
  "Đã lưu cấu hình HMAC riêng tư vào .dev/sepay.json. Chạy npm run dev:restart để áp dụng. Tài khoản nhận và tùy chọn ghi sổ quản lý tại Thu chi & tiền cọc → Webhook SePay.",
);
