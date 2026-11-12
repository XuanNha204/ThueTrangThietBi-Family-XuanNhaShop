import { test, expect, type Page } from '@playwright/test';
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
async function confirm(page: Page, title: string) {
  const dialog = page.getByRole('dialog', { name: title, exact: true });
  await dialog.getByRole('button', { name: 'Xác nhận', exact: true }).click();
  await expect(dialog).not.toBeVisible();
}
async function orderStatus(page: Page, id: string, expected: string) {
  await expect
    .poll(async () => {
      const res = await page.request.get('/api/rentals/' + id);
      return (await res.json()).order?.status;
    })
    .toBe(expected);
}
async function book(page: Page, staff = false) {
  const catalog = await (await page.request.get('/api/catalog')).json();
  const type = catalog.find((p: { code: string }) => p.code === (staff ? 'TB08' : 'TB05'));
  await page.goto('/book?type=' + type.id);
  if (staff)
    await page
      .getByRole('combobox', { name: 'Khách hàng', exact: true })
      .selectOption({ index: 1 });
  await page
    .getByLabel('Địa chỉ nhận / địa điểm sử dụng')
    .fill('E2E DEV · dữ liệu kiểm thử, không giao thực tế');
  await page.getByRole('checkbox').check();
  const submit = page.getByRole('button', { name: 'Giữ chỗ thiết bị' });
  await expect(submit).toBeEnabled();
  await submit.click();
  await page.waitForURL(staff ? '**/app/orders/*' : '**/my-rentals/*');
  const id = page.url().split('/').at(-1)!;
  await orderStatus(page, id, 'HELD');
  return id;
}
async function assertAccessible(page: Page) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
    .analyze();
  const issues = results.violations.filter((v) => ['critical', 'serious'].includes(v.impact ?? ''));
  expect(
    issues.map((v) => ({
      id: v.id,
      description: v.description,
      nodes: v.nodes.map((n) => ({ target: n.target, summary: n.failureSummary })),
    })),
  ).toEqual([]);
}

test('Danh mục thật, tìm kiếm và khả năng truy cập', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.goto('/');
  await expect(page.locator('.product-card')).toHaveCount(8);
  await assertAccessible(page);
  const name = await page.locator('.product-card h3').first().innerText();
  await page.getByRole('searchbox', { name: 'Tìm thiết bị', exact: true }).fill(name);
  await expect(page.locator('.product-card')).toHaveCount(1);
  await page
    .getByRole('searchbox', { name: 'Tìm thiết bị', exact: true })
    .fill('không có thiết bị này xyz');
  await expect(page.locator('.product-card')).toHaveCount(0);
  expect(errors).toEqual([]);
});

test('Khách giữ chỗ, xem hồ sơ và hủy đơn của mình', async ({ page }) => {
  await login(page, 'customer');
  const id = await book(page);
  await page.getByRole('button', { name: 'Hủy đơn thuê', exact: true }).click();
  await confirm(page, 'Hủy đơn thuê này?');
  await orderStatus(page, id, 'CANCELLED');
  await page.locator('.account-menu summary').click();
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await page.waitForURL('**/login');
  expect((await page.request.get('/api/auth/me')).status()).toBe(401);
});

test('Một vòng thuê: thu tiền, giao, trả, kiểm tra, người khác duyệt hoàn và tất toán', async ({
  page,
}) => {
  await login(page, 'owner');
  const id = await book(page, true);
  const data = await (await page.request.get('/api/rentals/' + id)).json();
  const equipmentCode = data.items[0].equipment_code;
  for (const label of ['Ghi nhận cọc', 'Thu tiền thuê']) {
    await page.getByRole('button', { name: label, exact: true }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel('Căn cứ / số chứng từ').fill('E2E DEV · tiền giả lập đã đối chiếu');
    await dialog.getByRole('button', { name: 'Ghi nhận khoản thu' }).click();
    await expect(dialog).not.toBeVisible();
  }
  await page.getByRole('button', { name: 'Xác nhận đơn', exact: true }).click();
  await confirm(page, 'Xác nhận đơn thuê?');
  await orderStatus(page, id, 'CONFIRMED');
  await page.getByRole('button', { name: 'Bắt đầu chuẩn bị' }).click();
  await confirm(page, 'Chuẩn bị thiết bị?');
  await page.getByRole('button', { name: 'Bàn giao thiết bị', exact: true }).click();
  let dialog = page.getByRole('dialog');
  await dialog
    .getByLabel('Phụ kiện, tình trạng & ghi chú thực tế')
    .fill('E2E DEV · bàn giao đủ thiết bị và phụ kiện');
  await dialog.getByRole('button', { name: 'Lưu biên bản' }).click();
  await expect(dialog).not.toBeVisible();
  await orderStatus(page, id, 'RENTED');
  await page.getByRole('button', { name: 'Ghi nhận trả thiết bị', exact: true }).click();
  dialog = page.getByRole('dialog');
  await dialog
    .getByLabel('Phụ kiện, tình trạng & ghi chú thực tế')
    .fill('E2E DEV · đã thu hồi đủ, chờ kiểm tra');
  await dialog.getByRole('button', { name: 'Lưu biên bản' }).click();
  await expect(dialog).not.toBeVisible();
  await orderStatus(page, id, 'AWAIT_SETTLEMENT');
  await page.goto('/app/equipment');
  const row = page.locator('tbody tr').filter({ hasText: equipmentCode });
  await row.getByRole('button', { name: 'Kiểm tra' }).click();
  dialog = page.getByRole('dialog', { name: 'Kiểm tra an toàn thiết bị' });
  for (const check of await dialog.getByRole('checkbox').all()) await check.check();
  await dialog
    .getByLabel('Kết quả, căn cứ kiểm tra và lưu ý')
    .fill('E2E DEV · kiểm tra an toàn, vệ sinh, đủ phụ kiện');
  await dialog.getByRole('button', { name: 'Lưu kiểm tra' }).click();
  await expect(dialog).not.toBeVisible();
  await page.goto('/app/orders/' + id);
  await page.getByRole('button', { name: 'Lập yêu cầu hoàn cọc', exact: true }).click();
  dialog = page.getByRole('dialog');
  await dialog.getByLabel('Lý do và căn cứ').fill('E2E DEV · đã trả đủ và kiểm tra đạt');
  await dialog.getByRole('button', { name: 'Gửi yêu cầu phê duyệt' }).click();
  await expect(dialog).not.toBeVisible();
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await login(page, 'accountant');
  await page.goto('/app/finance');
  await page.getByRole('button', { name: 'Phê duyệt hoàn cọc' }).click();
  const approval = page.locator('tbody tr').filter({ hasText: data.order.code });
  await approval.getByRole('button', { name: 'Duyệt & ghi hoàn' }).click();
  await confirm(page, 'Xác nhận hoàn cọc thực tế?');
  await page.goto('/app/orders/' + id);
  await page.getByRole('button', { name: 'Tất toán đơn', exact: true }).click();
  await confirm(page, 'Hoàn tất đơn thuê?');
  await orderStatus(page, id, 'COMPLETED');
});

test('Điện thoại 375px: nội dung vừa màn hình, menu mở và đóng', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto('/');
  await expect(page.locator('.product-card')).toHaveCount(8);
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
  ).toBeTruthy();
  await login(page, 'owner');
  await expect(page.getByText('Tổng thiết bị', { exact: true })).toBeVisible();
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
  ).toBeTruthy();
  await page.getByRole('button', { name: 'Mở menu' }).click();
  await page.locator('.side-nav').getByRole('link', { name: 'Thiết bị & kho' }).click();
  await page.waitForURL('**/app/equipment');
  await expect(page.locator('.sidebar')).not.toHaveClass(/open/);
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
  ).toBeTruthy();
});

test('CSV lỗi được giữ tại vùng chờ, nhân viên kho không thể duyệt nhập', async ({ page }) => {
  await login(page, 'stock');
  await page.goto('/app/imports');
  await page.getByRole('button', { name: 'Nhập tệp CSV' }).click();
  let dialog = page.getByRole('dialog', { name: 'Chuẩn bị nhập khách hàng' });
  await dialog.getByLabel('Mã batch nguồn').fill('E2E-INVALID-' + Date.now());
  await dialog.getByLabel('Tệp CSV UTF-8').setInputFiles({
    name: 'invalid.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from(
      'sourceId,name,phone,address\nE2E-BAD,Khách kiểm thử,sai-dien-thoai,Địa chỉ dev\n',
    ),
  });
  await dialog.getByRole('button', { name: 'Kiểm tra & đưa vào vùng chờ' }).click();
  dialog = page.getByRole('dialog', { name: 'Đối chiếu batch khách hàng' });
  await expect(dialog.getByText('Có lỗi cần xử lý', { exact: true })).toBeVisible();
  await expect(dialog.locator('.inline-error')).not.toBeEmpty();
  await expect(dialog.getByRole('button', { name: 'Duyệt nhập nguyên tử' })).toHaveCount(0);
});

test('Phân quyền cả giao diện và API; trang đăng nhập và quản lý có thể truy cập', async ({
  page,
}) => {
  await page.goto('/login');
  await assertAccessible(page);
  await login(page, 'owner');
  await expect(page.getByText('Tổng thiết bị', { exact: true })).toBeVisible();
  await assertAccessible(page);
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await login(page, 'stock');
  await page.goto('/app/finance');
  await expect(page.getByRole('heading', { name: 'Trang này cần quyền phù hợp' })).toBeVisible();
  expect((await page.request.get('/api/finance/ledger')).status()).toBe(403);
});
