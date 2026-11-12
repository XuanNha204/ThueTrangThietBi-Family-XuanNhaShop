import {
  mkdirSync,
  existsSync,
  writeFileSync,
  copyFileSync,
  chmodSync,
} from "node:fs";
import { randomBytes } from "node:crypto";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
const root = dirname(fileURLToPath(import.meta.url));
mkdirSync(join(root, "secrets"), { recursive: true, mode: 0o700 });
for (const name of [
  "postgres_password",
  "owner_password",
  "runtime_password",
  "bootstrap_password",
  "sepay_secret",
]) {
  const path = join(root, "secrets", name);
  if (!existsSync(path))
    writeFileSync(path, randomBytes(32).toString("hex"), {
      mode: 0o444,
      flag: "wx",
    });
  if (process.platform !== "win32") chmodSync(path, 0o444);
}
if (!existsSync(join(root, ".env")))
  copyFileSync(join(root, ".env.example"), join(root, ".env"));
console.log(
  "Prepared local deployment files. Edit deploy/.env. Read bootstrap password privately from deploy/secrets/bootstrap_password; never commit or send these files.",
);
