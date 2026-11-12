import { test, expect, request, type Page, type APIRequestContext } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

// This suite targets the local dev instance only, using a disposable product.
// Credentials stay in the ignored dev config and are never printed.
const root = fileURLToPath(new URL('../../', import.meta.url));
function sql(statement: string) {
  const config = JSON.parse(readFileSync(join(root, '.dev/config.json'), 'utf8'));
  return execFileSync(
    join(process.env.PG_BIN || 'C:/Program Files/PostgreSQL/18/bin', 'psql.exe'),
    [
      '-h',
      '127.0.0.1',
      '-p',
      '54329',
      '-U',
      'xuannha',
      '-d',
      'xuannhashop_dev',
      '-X',
      '-q',
      '-v',
      'ON_ERROR_STOP=1',
    ],
    {
      input: statement,
      encoding: 'utf8',
      windowsHide: true,
      env: { ...process.env, PGPASSWORD: config.databasePassword, PGCLIENTENCODING: 'UTF8' },
      stdio: ['pipe', 'pipe', 'pipe'],
    },
  );
}
function fixture() {
  const id = crypto.randomUUID(),
    code = 'E2EP-' + id.slice(0, 8),
    asset = crypto.randomUUID();
  sql(
    `begin; insert into equipment_type(id,code,name,category,description,specifications,icon,daily_rate,deposit) values('${id}','${code}','E2E DEV price ${code}','Test','Disposable price test','Test','fan',100000,200000); insert into equipment(id,code,type_id,warehouse_id,state) select '${asset}','${code}-001','${id}',id,'READY' from warehouse limit 1; commit;`,
  );
  return {
    id,
    code,
    cleanup: () =>
      sql(
        `begin; update equipment_type set active=false where id='${id}'; update equipment set state='DISPOSED' where id='${asset}' and state='READY'; commit;`,
      ),
  };
}
async function post(api: APIRequestContext, path: string, data?: unknown) {
  const csrf = await (await api.get('/api/auth/csrf')).json();
  const res = await api.post('/api' + path, {
    data,
    headers: { [csrf.headerName]: csrf.token, 'Idempotency-Key': crypto.randomUUID() },
  });
  expect(res.ok(), `${path}: ${res.status()} ${await res.text()}`).toBeTruthy();
  return res;
}
async function login(page: Page, name: string) {
  await page.goto('/login');
  await page.getByLabel('Tên đăng nhập', { exact: true }).fill(name);
  await page.getByLabel('Mật khẩu', { exact: true }).fill('XuanNhaDev!2026');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await page.waitForURL((u) => u.pathname.startsWith(name === 'customer' ? '/my-' : '/app'));
}
async function accessible(page: Page) {
  const result = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
    .analyze();
  expect(
    result.violations
      .filter((v) => ['critical', 'serious'].includes(v.impact || ''))
      .map((v) => ({ id: v.id, nodes: v.nodes.map((n) => n.failureSummary) })),
  ).toEqual([]);
}

test('Chủ shop sửa giá, xem lịch sử; cửa sổ cũ không ghi đè và kho chỉ được xem', async ({
  page,
  context,
}) => {
  const f = fixture();
  try {
    await login(page, 'owner');
    await page.goto('/app/equipment');
    await page.getByPlaceholder('Tìm sản phẩm trong bảng giá…').fill(f.code);
    const row = page.locator('.product-prices tr').filter({ hasText: f.code });
    await row.getByRole('button', { name: 'Điều chỉnh giá', exact: true }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByLabel('Giá thuê / ngày (VND)')).toHaveValue('100000');
    const stale = await context.newPage();
    await stale.goto('/app/equipment');
    await stale
      .locator('.product-prices tr')
      .filter({ hasText: f.code })
      .getByRole('button', { name: 'Điều chỉnh giá', exact: true })
      .click();
    await dialog.getByLabel('Giá thuê / ngày (VND)').fill('0');
    await dialog.getByLabel('Lý do điều chỉnh').fill('E2E DEV price update');
    await dialog.getByRole('button', { name: 'Lưu giá mới' }).click();
    await expect(dialog.locator('input:invalid')).toHaveCount(1);
    await dialog.getByLabel('Giá thuê / ngày (VND)').fill('125000');
    await dialog.getByLabel('Tiền cọc / chiếc (VND)').fill('0');
    await accessible(page);
    await page.screenshot({ path: '../.dev/qa/product-price-dialog-desktop.png', fullPage: true });
    await page.setViewportSize({ width: 375, height: 812 });
    await accessible(page);
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    ).toBeTruthy();
    await page.screenshot({ path: '../.dev/qa/product-price-dialog-mobile.png', fullPage: true });
    await dialog.getByRole('button', { name: 'Lưu giá mới' }).click();
    await expect(dialog).not.toBeVisible();
    await expect(row).toContainText('125.000');
    await row.getByRole('button', { name: 'Lịch sử giá', exact: true }).click();
    await expect(page.getByRole('dialog')).toContainText('E2E DEV price update');
    await expect(page.getByRole('dialog')).toContainText('owner');
    await page.getByRole('button', { name: 'Đóng lịch sử' }).click();
    const staleDialog = stale.getByRole('dialog');
    await staleDialog.getByLabel('Giá thuê / ngày (VND)').fill('150000');
    await staleDialog.getByLabel('Lý do điều chỉnh').fill('E2E DEV stale update');
    await staleDialog.getByRole('button', { name: 'Lưu giá mới' }).click();
    await expect(staleDialog.getByRole('alert')).toContainText('người khác thay đổi');
    await expect(staleDialog.getByRole('alert')).toBeFocused();
    await stale.close();
    const catalog = await (await page.request.get('/api/catalog')).json();
    expect(catalog.find((p: any) => p.id === f.id).dailyRate).toBe(125000);
    await page.setViewportSize({ width: 1440, height: 1000 });
    await accessible(page);
    await page.screenshot({ path: '../.dev/qa/product-prices-desktop.png', fullPage: true });
    await page.setViewportSize({ width: 375, height: 812 });
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    ).toBeTruthy();
    await accessible(page);
    await page.screenshot({ path: '../.dev/qa/product-prices-mobile.png', fullPage: true });
    const stockContext = await context.browser()!.newContext({ baseURL: 'http://127.0.0.1:5175' });
    const stock = await stockContext.newPage();
    try {
      await login(stock, 'stock');
      await stock.goto('/app/equipment');
      await expect(stock.getByRole('heading', { name: 'Bảng giá sản phẩm' })).toBeVisible();
      await expect(stock.getByRole('button', { name: 'Điều chỉnh giá', exact: true })).toHaveCount(
        0,
      );
    } finally {
      await stockContext.close();
    }
  } finally {
    f.cleanup();
  }
});

test('Giá đổi sau báo giá: khách phải xem giá mới và đồng ý lại trước giữ chỗ', async ({
  page,
}) => {
  const f = fixture();
  const owner = await request.newContext({ baseURL: 'http://127.0.0.1:5175' });
  let order = '';
  try {
    await post(owner, '/auth/login', { username: 'owner', password: 'XuanNhaDev!2026' });
    await login(page, 'customer');
    await page.goto('/book?type=' + f.id);
    await page
      .getByLabel('Địa chỉ nhận / địa điểm sử dụng')
      .fill('E2E DEV price test, no delivery');
    await page.getByRole('checkbox').check();
    const submit = page.getByRole('button', { name: 'Giữ chỗ thiết bị' });
    await expect(submit).toBeEnabled();
    await post(owner, `/catalog/${f.id}/price`, {
      expectedVersion: 0,
      dailyRate: 130000,
      deposit: 250000,
      reason: 'E2E DEV change after quote',
    });
    const rejection = page.waitForResponse(
      (r) => r.url().endsWith('/api/rentals') && r.request().method() === 'POST',
    );
    await submit.click();
    expect((await rejection).status()).toBe(409);
    await expect(page.getByRole('checkbox')).not.toBeChecked();
    await expect(page.locator('.booking-summary')).toContainText('130.000');
    await expect(submit).toBeDisabled();
    await page.getByRole('checkbox').check();
    await expect(submit).toBeEnabled();
    await submit.click();
    await page.waitForURL('**/my-rentals/*');
    order = page.url().split('/').at(-1)!;
    const detail = await (await owner.get('/api/rentals/' + order)).json();
    expect(detail.order.rentalTotal).toBe(130000);
    expect(detail.order.depositRequired).toBe(250000);
    expect(detail.items[0].daily_rate).toBe(130000);
  } finally {
    try {
      if (order) await post(owner, `/rentals/${order}/cancel`);
    } finally {
      f.cleanup();
      await owner.dispose();
    }
  }
});
