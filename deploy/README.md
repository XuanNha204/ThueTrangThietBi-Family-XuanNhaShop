# Ubuntu VPS deployment

Application: React/TypeScript, Spring Boot 17, PostgreSQL17. Public hostname: `mywebapp.id.vn`. Keep all `.env`, secret files, backups, data and private uploads outside Git. `Docs` is deliberately excluded at the owner's request.

## First deployment

```bash
cd /opt/xuannhashop
bash deploy/prepare.sh
# Edit deploy/.env: domain, address, owner name, bootstrap username.
# Read bootstrap password privately from deploy/secrets/bootstrap_password.
cd deploy
docker compose build
docker compose up -d
```

The default configuration owns ports80/443 and manages TLS through Caddy. For a VPS already running Nginx, use **both compose files** instead:

```bash
docker compose -f compose.yml -f compose.nginx.yml config --quiet
docker compose -f compose.yml -f compose.nginx.yml build
docker compose -f compose.yml -f compose.nginx.yml up -d
```

Configure the existing Nginx to terminate HTTPS for this hostname and proxy to `http://127.0.0.1:8088`, setting Host, X-Real-IP and X-Forwarded-Proto. Do not expose port8088 publicly. Preserve other virtual hosts. After first owner login, turn BOOTSTRAP_ENABLED=false and recreate backend. No sample data is created in prod.

## Cloudflare

Add the domain to your own Cloudflare account (Free plan is sufficient to start), preserve existing DNS records and replace the registrar nameservers with the two assigned by Cloudflare. Set A `@` to160.236.192.20, enable proxy and choose SSL/TLS **Full (strict)** with a valid origin certificate. Never use Flexible.

Bypass cache for `/api/*`, login and private pages; use managed WAF rules available on the plan. Do not challenge or cache `/api/integrations/sepay/webhook`; HMAC verification remains required. In Nginx trust CF-Connecting-IP only from the official Cloudflare CIDR list. Origin IP allowlisting/mTLS may be enabled after proxy is active and certificate renewal/other hosted domains are accounted for; do not firewall a shared VPS blindly.

Cloudflare zone activation, registrar nameservers and account settings require the owner's account access. DNS pointing directly to the VPS does not mean Cloudflare protection is enabled.

## Operations

Only web ports are public. DB and backend stay on private Docker networks. Use a dedicated SePay HMAC secret in `deploy/secrets/sepay_secret`; enable webhook only after configuring the receiving bank and verifying a real transaction. URL: `https://mywebapp.id.vn/api/integrations/sepay/webhook`.

Run `bash backup.sh` regularly, copy backups off the VPS, and periodically run `bash restore-check.sh <trusted-backup-folder>` to restore into a separate DB/volume. Backup pauses backend briefly for consistent DB/files. Timer templates are provided; daily backup is not PITR/RPO15m. Never run `down -v` on production. Keep release image tags and take a backup before migration/update.

Runtime secrets are generated on the VPS and must not be copied into Git. HTTPS cookies are Secure, sessions require login again after backend restart, PDF upload is disabled until content scanning is configured. Business rules and missing features still require shop acceptance; deployment checks alone are not full business acceptance.
