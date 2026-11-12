import {
  test,
  expect,
  request as browserRequest,
  type Page,
  type APIRequestContext,
} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

async function post(api: APIRequestContext, path: string, data?: unknown) {
  const token = await (await api.get('/api/auth/csrf')).json();
  const result = await api.post('/api' + path, {
    data,
    headers: { [token.headerName]: token.token, 'Idempotency-Key': crypto.randomUUID() },
  });
  expect(result.ok(), `${path}: ${result.status()} ${await result.text()}`).toBeTruthy();
  return result;
}
async function apiLogin(name: string, password = 'XuanNhaDev!2026') {
  const api = await browserRequest.newContext({ baseURL: 'http://127.0.0.1:5175' });
  await post(api, '/auth/login', { username: name, password });
  return api;
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
      .map((v) => ({
        id: v.id,
        targets: v.nodes.map((n) => ({ target: n.target, reason: n.failureSummary })),
      })),
  ).toEqual([]);
}
test('Khách lấy QR riêng, hủy mã không hủy đơn và không tự ghi tiền', async ({ page }) => {
  const api = await apiLogin('owner');
  let order = '';
  try {
    const catalog = await (await api.get('/api/catalog')).json(),
      customers = await (await api.get('/api/customers')).json();
    const from = new Date(Date.now() + 3600000),
      created = await (
        await post(api, '/rentals', {
          customerId: customers.find((c: any) => c.phone === '0901000001').id,
          lines: [{ typeId: catalog.find((p: any) => p.code === 'TB05').id, quantity: 1 }],
          startsAt: from.toISOString(),
          endsAt: new Date(from.getTime() + 86400000).toISOString(),
          address: 'E2E DEV · chỉ thử QR, không chuyển tiền',
          acceptTerms: true,
        })
      ).json();
    order = created.id;
    await login(page, 'customer');
    await page.goto('/my-rentals/' + order);
    await page.getByRole('button', { name: 'Tạo QR tiền cọc', exact: true }).click();
    const card = page.locator('.bank-payment-card').first();
    await expect(card.getByText('Chờ chuyển khoản', { exact: true })).toBeVisible();
    const data = await (await api.get(`/api/rentals/${order}/payments`)).json();
    expect(data).toHaveLength(1);
    expect(data[0].code).toMatch(/^XN[A-F0-9]{24}$/);
    await expect(card.locator('img')).toHaveAttribute('src', new RegExp('des=' + data[0].code));
    await accessible(page);
    await page.screenshot({ path: '../.dev/qa/payments-order-desktop.png', fullPage: true });
    await page.setViewportSize({ width: 375, height: 812 });
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    ).toBeTruthy();
    await page.screenshot({ path: '../.dev/qa/payments-order-mobile.png', fullPage: true });
    await accessible(page);
    await card.getByRole('button', { name: 'Hủy yêu cầu chuyển khoản', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: 'Hủy yêu cầu chuyển khoản?' });
    await dialog.getByRole('button', { name: 'Xác nhận', exact: true }).click();
    await expect(dialog).not.toBeVisible();
    await expect(card.getByText('Đã hủy yêu cầu', { exact: true })).toBeVisible();
    const detail = await (await api.get('/api/rentals/' + order)).json();
    expect(detail.order.status).toBe('HELD');
    expect(detail.balance.depositHeld).toBe(0);
    expect(detail.balance.rentalPaid).toBe(0);
  } finally {
    if (order) await post(api, `/rentals/${order}/cancel`);
    await api.dispose();
  }
});
test('Chủ cửa hàng tạo nhân viên, đổi quyền và khóa phiên đang sử dụng', async ({ page }) => {
  await login(page, 'owner');
  await page.goto('/app/settings');
  await page.getByRole('button', { name: 'Tạo nhân viên', exact: true }).click();
  let dialog = page.getByRole('dialog', { name: 'Tạo tài khoản nhân viên' });
  const name = 'e2e_' + crypto.randomUUID().slice(0, 8);
  await dialog.getByLabel('Tên đăng nhập').fill(name);
  await dialog.getByLabel('Họ tên nhân viên').fill('E2E DEV · tài khoản thử quyền');
  await dialog.getByLabel('Mật khẩu').fill('E2eOnly!Password2026');
  await dialog.getByLabel('Vai trò', { exact: false }).selectOption('INVENTORY');
  await dialog.getByRole('button', { name: 'Tạo tài khoản', exact: true }).click();
  await expect(dialog).not.toBeVisible();
  const staff = await apiLogin(name, 'E2eOnly!Password2026');
  try {
    expect((await staff.get('/api/finance/ledger')).status()).toBe(403);
    const row = page.getByRole('row').filter({ hasText: name });
    await row.getByRole('button', { name: 'Đổi vai trò', exact: true }).click();
    dialog = page.getByRole('dialog', { name: 'Đổi vai trò: ' + name });
    await dialog.getByLabel('Vai trò mới').selectOption('ACCOUNTANT');
    await dialog
      .getByLabel('Lý do và căn cứ cấp quyền')
      .fill('E2E DEV · đổi nhiệm vụ để thử phiên');
    await dialog.getByRole('button', { name: 'Lưu vai trò', exact: true }).click();
    await expect(dialog).not.toBeVisible();
    expect((await staff.get('/api/finance/ledger')).status()).toBe(200);
    await accessible(page);
    await row.getByRole('button', { name: 'Khóa', exact: true }).click();
    dialog = page.getByRole('dialog', { name: 'Khóa tài khoản này?' });
    await dialog.getByRole('button', { name: 'Xác nhận', exact: true }).click();
    await expect(dialog).not.toBeVisible();
    expect((await staff.get('/api/auth/me')).status()).toBe(401);
  } finally {
    await staff.dispose();
  }
});
test('Kế hoạch bảo trì chặn lịch và hủy có căn cứ; SePay hiển thị cấu hình', async ({ page }) => {
  const api = await apiLogin('owner');
  let plan = '';
  try {
    const assets = await (await api.get('/api/equipment')).json(),
      asset = assets.find((e: any) => e.state === 'READY');
    const from = new Date(Date.now() + 30 * 86400000),
      to = new Date(from.getTime() + 7200000);
    const reason = 'E2E DEV · kế hoạch thử, sẽ hủy ngay ' + crypto.randomUUID().slice(0, 8);
    plan = (
      await (
        await post(api, '/maintenance/plans', {
          equipmentId: asset.id,
          startsAt: from.toISOString(),
          endsAt: to.toISOString(),
          reason,
        })
      ).json()
    ).id;
    await login(page, 'owner');
    await page.goto('/app/maintenance');
    const row = page.getByRole('row').filter({ hasText: reason });
    await expect(row.getByText('Đã lên lịch', { exact: true })).toBeVisible();
    await accessible(page);
    await row.getByRole('button', { name: 'Hủy kế hoạch', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: 'Hủy kế hoạch ' + asset.code });
    await dialog.getByLabel('Lý do và căn cứ hủy').fill('E2E DEV · kết thúc kiểm thử');
    await dialog.getByRole('button', { name: 'Lưu thông tin', exact: true }).click();
    await expect(dialog).not.toBeVisible();
    await expect(row.getByText('Đã hủy kế hoạch', { exact: true })).toBeVisible();
    plan = '';
    await page.goto('/app/finance');
    await page.getByRole('button', { name: 'Webhook SePay', exact: true }).click();
    await expect(page.getByText('HMAC: Sẵn sàng', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Cấu hình tài khoản nhận', exact: true }).click();
    const config = page.getByRole('dialog', { name: 'Tài khoản nhận SePay', exact: true });
    await expect(config.getByLabel('Mã ngân hàng trên webhook')).toHaveValue('MBBank');
    await accessible(page);
    await config.getByRole('button', { name: 'Quay lại', exact: true }).click();
    await page.screenshot({ path: '../.dev/qa/payments-finance-desktop.png', fullPage: true });
  } finally {
    if (plan)
      await post(api, `/maintenance/plans/${plan}/cancel`, {
        reason: 'E2E DEV · dọn kế hoạch sau kiểm thử',
      });
    await api.dispose();
  }
});
