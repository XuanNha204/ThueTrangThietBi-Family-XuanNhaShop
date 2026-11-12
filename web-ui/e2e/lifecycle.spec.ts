import {
  test,
  expect,
  type Page,
  type APIRequestContext,
  request as browserRequest,
} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

async function login(page: Page, username: string) {
  await page.goto('/login');
  await page.getByLabel('Tên đăng nhập', { exact: true }).fill(username);
  await page.getByLabel('Mật khẩu', { exact: true }).fill('XuanNhaDev!2026');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await page.waitForURL((url) =>
    username === 'customer' ? /^\/my-/.test(url.pathname) : /^\/app(?:\/|$)/.test(url.pathname),
  );
  expect((await (await page.request.get('/api/auth/me')).json()).username).toBe(username);
}
async function logout(page: Page) {
  const menu = page.locator('.account-menu summary');
  if (await menu.count()) await menu.click();
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await page.waitForURL('**/login');
}
async function post(api: APIRequestContext, path: string, data?: unknown) {
  const token = await (await api.get('/api/auth/csrf')).json();
  const res = await api.post('/api' + path, {
    data,
    headers: { [token.headerName]: token.token, 'Idempotency-Key': crypto.randomUUID() },
  });
  expect(res.ok(), `${path}: ${res.status()} ${await res.text()}`).toBeTruthy();
  return res;
}
async function apiLogin(username: string) {
  const api = await browserRequest.newContext({ baseURL: 'http://127.0.0.1:5175' });
  await post(api, '/auth/login', { username, password: 'XuanNhaDev!2026' });
  return api;
}
async function fixture(api: APIRequestContext, code = 'TB05') {
  const products = await (await api.get('/api/catalog')).json();
  const customers = await (await api.get('/api/customers')).json();
  const from = new Date(Date.now() + 10 * 60000);
  const created = await (
    await post(api, '/rentals', {
      customerId: customers.find((c: { phone: string }) => c.phone === '0901000001').id,
      lines: [{ typeId: products.find((p: { code: string }) => p.code === code).id, quantity: 1 }],
      startsAt: from.toISOString(),
      endsAt: new Date(from.getTime() + 86400000).toISOString(),
      address: 'E2E DEV · địa chỉ thỏa thuận ban đầu',
      acceptTerms: true,
    })
  ).json();
  const detail = await (await api.get('/api/rentals/' + created.id)).json();
  return detail;
}
async function accessibility(page: Page) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
    .analyze();
  expect(
    results.violations
      .filter((v) => ['serious', 'critical'].includes(v.impact ?? ''))
      .map((v) => ({
        id: v.id,
        nodes: v.nodes.map((n) => ({ target: n.target, summary: n.failureSummary })),
      })),
  ).toEqual([]);
}
async function approveChange(page: Page) {
  await page.getByRole('button', { name: 'Duyệt áp dụng', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Duyệt áp dụng thay đổi' });
  await dialog.getByLabel('Căn cứ quyết định').fill('E2E DEV · đã đối chiếu khách và lịch thuê');
  await dialog.getByRole('button', { name: 'Duyệt và cập nhật thỏa thuận' }).click();
  await expect(dialog).not.toBeVisible();
  await expect(page.getByText('Đã áp dụng', { exact: true })).toBeVisible();
}

test('Khách đề nghị gia hạn, cửa hàng duyệt và giữ lịch sử giá cũ', async ({ page }) => {
  const api = await apiLogin('owner');
  const detail = await fixture(api);
  const id = detail.order.id;
  try {
    await login(page, 'customer');
    await page.goto('/my-rentals/' + id);
    await page.getByRole('button', { name: 'Đề nghị thay đổi' }).click();
    const dialog = page.getByRole('dialog', { name: 'Đề nghị thay đổi đơn thuê' });
    await dialog.getByLabel('Lý do và nội dung đề nghị').fill('E2E DEV · cần dùng thêm một ngày');
    await dialog.getByRole('button', { name: 'Xem báo giá thay đổi' }).click();
    await expect(dialog.locator('.change-quote')).toBeVisible();
    await accessibility(page);
    await dialog.getByRole('checkbox').check();
    await dialog.getByRole('button', { name: 'Gửi đề nghị' }).click();
    await expect(dialog).not.toBeVisible();
    await expect(page.getByText('Khách chấp thuận: customer', { exact: true })).toBeVisible();
    await logout(page);
    await login(page, 'owner');
    await page.goto('/app/orders/' + id);
    await approveChange(page);
    const updated = await (await api.get('/api/rentals/' + id)).json();
    expect(updated.order.rentalTotal).toBe(detail.order.rentalTotal * 2);
    await expect(page.getByText('Thỏa thuận #2', { exact: true })).toBeVisible();
    await page.getByText('Thỏa thuận #1', { exact: true }).click();
    await expect(
      page
        .locator('.revision-list details')
        .filter({ has: page.getByText('Thỏa thuận #1', { exact: true }) })
        .locator('.revision-content'),
    ).toBeVisible();
  } finally {
    await post(api, '/rentals/' + id + '/cancel');
    await api.dispose();
  }
});

test('Đổi địa chỉ cần khách chấp thuận riêng trước khi cửa hàng duyệt', async ({ page }) => {
  const api = await apiLogin('owner');
  const detail = await fixture(api);
  const id = detail.order.id;
  try {
    await login(page, 'owner');
    await page.goto('/app/orders/' + id);
    await page.getByRole('button', { name: 'Đề nghị thay đổi' }).click();
    let dialog = page.getByRole('dialog', { name: 'Đề nghị thay đổi đơn thuê' });
    await dialog.getByRole('combobox', { name: 'Nội dung thay đổi' }).selectOption('ADDRESS');
    await dialog.getByLabel('Địa chỉ nhận mới').fill('E2E DEV · địa điểm nhận mới đã trao đổi');
    await dialog
      .getByLabel('Lý do và nội dung đề nghị')
      .fill('E2E DEV · khách thay đổi địa điểm nhận');
    await dialog.getByRole('button', { name: 'Xem báo giá thay đổi' }).click();
    await expect(dialog.locator('.change-quote')).toBeVisible();
    await dialog.getByRole('checkbox').check();
    await dialog.getByRole('button', { name: 'Gửi đề nghị' }).click();
    await expect(dialog).not.toBeVisible();
    await expect(page.getByRole('button', { name: 'Duyệt áp dụng', exact: true })).toBeDisabled();
    await logout(page);
    await login(page, 'customer');
    await page.goto('/my-rentals/' + id);
    await page.getByRole('button', { name: 'Chấp thuận báo giá', exact: true }).click();
    dialog = page.getByRole('dialog', { name: 'Chấp thuận báo giá thay đổi?' });
    await dialog.getByRole('button', { name: 'Xác nhận', exact: true }).click();
    await expect(dialog).not.toBeVisible();
    await expect(page.getByRole('button', { name: 'Duyệt áp dụng', exact: true })).toHaveCount(0);
    await logout(page);
    await login(page, 'owner');
    await page.goto('/app/orders/' + id);
    await approveChange(page);
    expect((await (await api.get('/api/rentals/' + id)).json()).order.address).toBe(
      'E2E DEV · địa điểm nhận mới đã trao đổi',
    );
  } finally {
    await post(api, '/rentals/' + id + '/cancel');
    await api.dispose();
  }
});

test('Hồ sơ liên hệ sửa được và không thay địa chỉ đơn đã lập', async ({ page }) => {
  const owner = await apiLogin('owner');
  const detail = await fixture(owner);
  const id = detail.order.id;
  await login(page, 'customer');
  const previous = await (await page.request.get('/api/auth/profile')).json();
  try {
    await page.goto('/my-profile');
    await accessibility(page);
    await page.getByLabel('Địa chỉ liên hệ').fill('E2E DEV · liên hệ mới của khách');
    await page.getByRole('button', { name: 'Lưu thông tin liên hệ' }).click();
    await expect
      .poll(async () => (await (await page.request.get('/api/auth/profile')).json()).address)
      .toBe('E2E DEV · liên hệ mới của khách');
    expect((await (await owner.get('/api/rentals/' + id)).json()).order.address).toBe(
      detail.order.address,
    );
  } finally {
    const profile = await (await page.request.get('/api/auth/profile')).json();
    await post(page.request, '/auth/profile', { ...previous, version: profile.version });
    await post(owner, '/rentals/' + id + '/cancel');
    await owner.dispose();
  }
});

test('Báo mất chờ duyệt hiện trên lịch cần chú ý; người khác có thể từ chối sau xác minh', async ({
  page,
}) => {
  const owner = await apiLogin('owner'),
    accountant = await apiLogin('accountant');
  const detail = await fixture(owner, 'TB06');
  const id = detail.order.id,
    item = detail.items[0];
  await post(owner, '/finance/receive', {
    orderId: id,
    kind: 'DEPOSIT_IN',
    amount: detail.order.depositRequired,
    note: 'E2E DEV · cọc giả lập',
  });
  await post(owner, '/finance/receive', {
    orderId: id,
    kind: 'RENTAL_IN',
    amount: detail.order.rentalTotal,
    note: 'E2E DEV · tiền thuê giả lập',
  });
  await post(owner, '/rentals/' + id + '/confirm');
  await post(owner, '/rentals/' + id + '/handover', {
    items: [item.id],
    acceptedBy: 'E2E DEV',
    note: 'E2E DEV · bàn giao thử',
  });
  try {
    await login(page, 'owner');
    await page.goto('/app/orders/' + id);
    await page.getByRole('button', { name: 'Lập phương án xử lý', exact: true }).click();
    let dialog = page.getByRole('dialog', { name: 'Lập phương án xử lý sự cố' });
    await dialog.getByRole('combobox', { name: 'Thiết bị liên quan' }).selectOption(item.id);
    await dialog
      .getByLabel('Diễn biến và tình trạng thực tế')
      .fill('E2E DEV · đang kiểm tra vị trí thiết bị');
    await dialog
      .getByLabel('Căn cứ thỏa thuận: biên bản, chứng từ hoặc trao đổi đã đối chiếu')
      .fill('E2E DEV · chờ xác minh, chưa kết luận mất');
    await dialog.getByRole('button', { name: 'Lập phương án', exact: true }).click();
    await expect(dialog).not.toBeVisible();
    await expect(page.getByRole('button', { name: 'Duyệt phương án', exact: true })).toHaveCount(0);
    await page.goto('/app/attention');
    await expect(page.locator('tbody tr').filter({ hasText: detail.order.code })).toContainText(
      'Đang xác minh mất thiết bị',
    );
    await page.setViewportSize({ width: 375, height: 812 });
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    ).toBeTruthy();
    await accessibility(page);
    await page.setViewportSize({ width: 1440, height: 1000 });
    await logout(page);
    await login(page, 'accountant');
    await page.goto('/app/orders/' + id);
    await page.getByRole('button', { name: 'Từ chối phương án', exact: true }).click();
    dialog = page.getByRole('dialog', { name: 'Từ chối phương án xử lý' });
    await dialog
      .getByLabel('Căn cứ quyết định và kết quả đối chiếu')
      .fill('E2E DEV · đã tìm thấy, không có mất thiết bị');
    await dialog.getByRole('button', { name: 'Lưu từ chối' }).click();
    await expect(dialog).not.toBeVisible();
  } finally {
    const cases = await (await owner.get(`/api/rentals/${id}/incidents`)).json();
    for (const entry of cases.filter((x: { status: string }) => x.status === 'PENDING'))
      await post(accountant, `/rentals/${id}/incidents/${entry.id}/decide`, {
        approved: false,
        note: 'E2E DEV · kết thúc kiểm thử, thiết bị đã xác minh còn đủ',
      });
    await post(owner, '/rentals/' + id + '/return', {
      items: [item.id],
      acceptedBy: 'E2E DEV',
      condition: 'GOOD',
      note: 'E2E DEV · thu hồi kết thúc thử nghiệm',
    });
    await post(owner, `/equipment/${item.equipment_id}/inspection`, {
      powerSafe: true,
      clean: true,
      complete: true,
      findings: 'E2E DEV · kiểm tra sau thu hồi',
    });
    const refund = await (
      await post(owner, '/finance/refunds', {
        orderId: id,
        amount: detail.order.depositRequired,
        reason: 'E2E DEV · trả đủ, kiểm tra đạt',
      })
    ).json();
    await post(accountant, `/finance/approvals/${refund.id}`, { approved: true });
    await post(owner, `/rentals/${id}/settle`);
    await owner.dispose();
    await accountant.dispose();
  }
});
