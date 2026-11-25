import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const project = new URL('../', import.meta.url);
const callback = process.argv[2] ?? 'https://mywebapp.id.vn/api/auth/google/callback';
const redirect = new URL(callback);
if (redirect.protocol !== 'https:' || redirect.pathname !== '/api/auth/google/callback' ||
    redirect.search || redirect.hash || redirect.username || redirect.password) {
  throw new Error('Callback production phải dùng HTTPS và đường dẫn /api/auth/google/callback.');
}

const input = await readFile(new URL('.dev/google-oauth.json', project), 'utf8');
let credentials;
try {
  credentials = JSON.parse(input.replace(/^\uFEFF/, '')).web;
} catch {
  throw new Error('Tệp .dev/google-oauth.json không đúng định dạng Google OAuth.');
}
if (!/^[0-9]+-[A-Za-z0-9_-]+\.apps\.googleusercontent\.com$/.test(credentials?.client_id) ||
    typeof credentials?.client_secret !== 'string' || !credentials.client_secret.trim() ||
    !Array.isArray(credentials.redirect_uris) || !credentials.redirect_uris.includes(callback)) {
  throw new Error('Tệp OAuth cần Client ID, Client Secret và callback production đã đăng ký.');
}

const secrets = new URL('deploy/secrets/', project);
await mkdir(secrets, { recursive: true, mode: 0o700 });
await writeFile(new URL('google_oauth_client_id', secrets), credentials.client_id.trim(), { mode: 0o600 });
await writeFile(new URL('google_oauth_client_secret', secrets), credentials.client_secret.trim(), { mode: 0o600 });
console.log('Đã chuẩn bị cấu hình Google OAuth cho ' + redirect.hostname + ' trong ' + fileURLToPath(secrets));
