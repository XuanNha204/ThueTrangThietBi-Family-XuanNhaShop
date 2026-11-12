import { test, expect } from '@playwright/test';

test('Kiểm tra thanh toán nền giữ vị trí cuộn, nội dung và chỉ cập nhật đơn đang xem', async ({
  page,
}) => {
  await page.goto('/login');
  await page.getByLabel('Tên đăng nhập', { exact: true }).fill('owner');
  await page.getByLabel('Mật khẩu', { exact: true }).fill('XuanNhaDev!2026');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await page.waitForURL('**/app');
  const orders = await (await page.request.get('/api/rentals')).json();
  const id = orders[0].id;
  const detail = await (await page.request.get(`/api/rentals/${id}`)).json();
  // Mock only browser reads: no QR, payment or ledger is written to the dev database.
  detail.order.status = 'HELD';
  detail.order.expiresAt = new Date(Date.now() + 1200000).toISOString();
  detail.order.rentalTotal = 5000;
  detail.balance.rentalPaid = 0;
  const payment = {
    id: 'polling-ui-fixture',
    status: 'OPEN',
    payable: true,
    kind: 'RENTAL_IN',
    amount: 5000,
    gateway: 'MBBank',
    account_number: '000000000',
    account_holder: 'UI TEST',
    code: 'XN0123456789ABCDEF01234567',
    expires_at: detail.order.expiresAt,
    money_id: null as string | null,
  };
  let reads = 0,
    fail = false;
  await page.route(`**/api/rentals/${id}`, (route) => route.fulfill({ json: detail }));
  await page.route(`**/api/rentals/${id}/payments`, async (route) => {
    reads++;
    await new Promise((resolve) => setTimeout(resolve, 250));
    if (fail)
      await route.fulfill({
        status: 503,
        json: { message: 'Tạm thời chưa kiểm tra được thanh toán.' },
      });
    else await route.fulfill({ json: [payment] });
  });
  await page.route('https://vietqr.app/**', (route) => route.abort());
  await page.goto('/app/orders/' + id);
  const panel = page.locator('.bank-payments');
  await expect(panel.getByText('Chờ chuyển khoản', { exact: true })).toBeVisible();
  await panel.scrollIntoViewIfNeeded();
  await page.evaluate(() => {
    const el = document.querySelector('.bank-payments')!;
    window.scrollTo(0, el.getBoundingClientRect().top + window.scrollY - 120);
    (window as any).pollPanel = el;
    (window as any).pollMinY = window.scrollY;
    (window as any).pollMonitor = setInterval(() => {
      (window as any).pollMinY = Math.min((window as any).pollMinY, window.scrollY);
    }, 16);
  });
  const initialY = await page.evaluate(() => window.scrollY);
  const initialReads = reads;
  expect(initialY).toBeGreaterThan(100);
  const requested: string[] = [];
  page.on('request', (request) => {
    const path = new URL(request.url()).pathname;
    if (path.startsWith('/api/')) requested.push(path);
  });
  await expect.poll(() => reads, { timeout: 15000 }).toBeGreaterThan(initialReads);
  await expect(panel.getByRole('button', { name: 'Kiểm tra thanh toán' })).toBeEnabled();
  expect(await page.evaluate(() => (window as any).pollMinY)).toBeGreaterThanOrEqual(initialY - 2);
  expect(await page.evaluate(() => window.scrollY)).toBeCloseTo(initialY, 0);
  expect(
    requested.every((path) => [`/api/rentals/${id}`, `/api/rentals/${id}/payments`].includes(path)),
  ).toBe(true);
  expect(
    await page.evaluate(
      () => (window as any).pollPanel === document.querySelector('.bank-payments'),
    ),
  ).toBe(true);

  // Temporary network errors retain the QR instead of unmounting the page.
  fail = true;
  await panel.getByRole('button', { name: 'Kiểm tra thanh toán' }).click();
  await expect(panel.getByRole('alert')).toContainText('Tạm thời');
  await expect(panel.getByText('Chờ chuyển khoản', { exact: true })).toBeVisible();
  expect(
    await page.evaluate(
      () => (window as any).pollPanel === document.querySelector('.bank-payments'),
    ),
  ).toBe(true);
  expect(await page.evaluate(() => window.scrollY)).toBeGreaterThan(100);

  // A successful manual check also updates the displayed rental balance without a global refresh.
  fail = false;
  payment.status = 'PAID';
  payment.payable = false;
  payment.money_id = 'ui-test-only';
  detail.balance.rentalPaid = 5000;
  await panel.getByRole('button', { name: 'Kiểm tra thanh toán' }).click();
  await expect(panel.getByText('Đã ghi nhận vào sổ tiền', { exact: true })).toBeVisible();
  await expect(
    page.locator('.finance-summary .summary-line').filter({ hasText: 'Tiền thuê đã nhận' }),
  ).toContainText('5.000');
  await expect(panel.getByRole('button', { name: 'Tạo QR tiền thuê', exact: true })).toHaveCount(0);
  expect(
    await page.evaluate(
      () => (window as any).pollPanel === document.querySelector('.bank-payments'),
    ),
  ).toBe(true);
  await page.evaluate(() => clearInterval((window as any).pollMonitor));
});
