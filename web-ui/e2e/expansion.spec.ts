import { test, expect, request, type Page, type APIRequestContext } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
const root = fileURLToPath(new URL('../../', import.meta.url));
const baseURL = 'http://127.0.0.1:5175';
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
  const type = crypto.randomUUID(),
    asset = crypto.randomUUID(),
    warehouse = crypto.randomUUID(),
    code = 'E2EX-' + type.slice(0, 8).toUpperCase();
  sql(
    `begin; insert into warehouse(id,name,address) values('${warehouse}','${code}','E2E DEV'); insert into equipment_type(id,code,name,category,description,specifications,icon,daily_rate,deposit) values('${type}','${code}','E2E DEV ${code}','Test','Test','Test','fan',100,200); insert into equipment(id,code,type_id,warehouse_id,state) values('${asset}','${code}-001','${type}','${warehouse}','READY'); commit;`,
  );
  return {
    type,
    asset,
    warehouse,
    code,
    cleanup: () =>
      sql(
        `begin; update reservation set status='RELEASED' where equipment_id in(select id from equipment where type_id='${type}'); update rental_order set status='CANCELLED' where id in(select order_id from rental_item where equipment_id in(select id from equipment where type_id='${type}')) and status='HELD'; update equipment_type set active=false where id='${type}'; update equipment set state='DISPOSED' where type_id='${type}'; commit;`,
      ),
  };
}
async function post(api: APIRequestContext, path: string, data?: unknown) {
  const csrf = await (await api.get('/api/auth/csrf')).json();
  const r = await api.post('/api' + path, {
    data,
    headers: { [csrf.headerName]: csrf.token, 'Idempotency-Key': crypto.randomUUID() },
  });
  expect(r.ok(), `${path}: ${r.status()} ${await r.text()}`).toBeTruthy();
  return r;
}
async function apiLogin(username = 'owner') {
  const api = await request.newContext({ baseURL });
  await post(api, '/auth/login', { username, password: 'XuanNhaDev!2026' });
  return api;
}
async function login(page: Page, username: string) {
  await page.goto('/login');
  await page.getByLabel('Tên đăng nhập', { exact: true }).fill(username);
  await page.getByLabel('Mật khẩu', { exact: true }).fill('XuanNhaDev!2026');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await page.waitForURL('**/app');
}
async function save(page: Page) {
  const d = page.getByRole('dialog');
  await d.getByRole('button', { name: 'Lưu thông tin', exact: true }).click();
  await expect(d).not.toBeVisible();
}
async function decision(page: Page) {
  await page
    .getByRole('dialog')
    .getByLabel('Căn cứ và kết luận')
    .fill('E2E DEV · đối chiếu hồ sơ thử nghiệm');
  await save(page);
}

test('Lô nhận được duyệt khác người, chuyển kho cần xác nhận và kiểm tra lại', async ({
  page,
  browser,
}) => {
  const f = fixture(),
    ctx = await browser.newContext({ baseURL }),
    owner = await ctx.newPage();
  try {
    await login(page, 'accountant');
    await page.goto('/app/stock-operations');
    await page.getByRole('button', { name: 'Tiếp nhận lô thiết bị', exact: true }).click();
    let d = page.getByRole('dialog');
    await d.getByLabel('Kho tiếp nhận').selectOption(f.warehouse);
    await d.getByLabel('Loại thiết bị').selectOption(f.type);
    await d.getByLabel('Nhà cung cấp').fill('E2E DEV supplier');
    await d.getByLabel('Số chứng từ').fill(f.code);
    await d.getByLabel('Giá mua mỗi thiết bị').fill('1000');
    await d.getByLabel('Thời gian phân bổ').fill('12');
    await d.getByLabel('Các mã thiết bị').fill(f.code + '-NEW | SERIAL-DEV');
    await d.getByLabel('Căn cứ đối chiếu').fill('E2E DEV tiếp nhận thử');
    await save(page);
    await expect(page.locator('tbody tr').filter({ hasText: f.code })).toContainText('Chờ duyệt');
    await login(owner, 'owner');
    await owner.goto('/app/stock-operations');
    const receipt = owner.locator('tbody tr').filter({ hasText: f.code });
    await receipt.getByRole('button', { name: 'Duyệt nhận lô' }).click();
    await decision(owner);
    await expect(receipt).toContainText('Đã tiếp nhận');
    const api = await apiLogin(),
      assets = await (await api.get('/api/equipment')).json();
    const added = assets.find((e: any) => e.code === f.code + '-NEW');
    expect(added.state).toBe('AWAIT_ACCEPTANCE');
    const destination = (await (await api.get('/api/warehouses')).json()).find(
      (w: any) => w.id !== f.warehouse,
    );
    await post(api, '/stock/actions', {
      equipmentId: added.id,
      kind: 'TRANSFER',
      toWarehouse: destination.id,
      recoveryValue: 0,
      reason: 'E2E DEV chuyển kho',
    });
    await api.dispose();
    await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
    await login(page, 'stock');
    await page.goto('/app/stock-operations');
    await page.getByRole('button', { name: 'Chuyển kho / thanh lý', exact: true }).click();
    const transfer = page.locator('tbody tr').filter({ hasText: f.code + '-NEW' });
    await transfer.getByRole('button', { name: 'Xác nhận nhận', exact: true }).click();
    await decision(page);
    await expect(transfer).toContainText('Đã hoàn thành');
    await page.goto('/app/equipment');
    await page.getByRole('searchbox', { name: 'Tìm mã hoặc tên thiết bị…' }).fill(f.code + '-NEW');
    await expect(page.locator('tbody tr').filter({ hasText: f.code + '-NEW' })).toContainText(
      'Chờ kiểm tra',
    );
  } finally {
    f.cleanup();
    await ctx.close();
  }
});

test('Chi phí qua duyệt, trả từng phần và đối soát giữ nguyên chênh lệch', async ({
  page,
  browser,
}) => {
  const ref = 'E2E DEV EXP ' + crypto.randomUUID().slice(0, 8),
    ctx = await browser.newContext({ baseURL }),
    owner = await ctx.newPage();
  try {
    await login(page, 'accountant');
    await page.goto('/app/accounting');
    await page.getByRole('button', { name: 'Lập khoản chi phí / phải trả', exact: true }).click();
    let d = page.getByRole('dialog');
    await d.getByLabel('Loại chi phí').selectOption('TRANSPORT');
    await d.getByLabel('Giá trị phải trả').fill('2');
    await d.getByLabel('Chứng từ / nơi lưu hồ sơ').fill(ref);
    await d.getByLabel('Nội dung và căn cứ').fill('E2E DEV mô phỏng, không chuyển tiền thật');
    await save(page);
    await login(owner, 'owner');
    await owner.goto('/app/accounting');
    const row = owner.locator('tbody tr').filter({ hasText: ref });
    await row.getByRole('button', { name: 'Duyệt chi phí', exact: true }).click();
    await decision(owner);
    await row.getByRole('button', { name: 'Ghi khoản đã chi' }).click();
    d = owner.getByRole('dialog');
    await d.getByLabel('Số tiền thực chi').fill('1');
    await d
      .getByLabel('Chứng từ chi thực tế')
      .fill('E2E DEV · giao dịch giả lập kiểm thử giao diện');
    await save(owner);
    await expect(row).toContainText('Còn 1');
    await page.getByRole('button', { name: 'Đối soát ca / ngày', exact: true }).first().click();
    await page.getByRole('button', { name: 'Đối soát ca / ngày', exact: true }).last().click();
    d = page.getByRole('dialog');
    await d.getByLabel('Bắt đầu ca').fill('2007-01-01T08:00');
    await d.getByLabel('Kết thúc ca').fill('2007-01-01T09:00');
    await d.getByLabel('Số dư đầu ca').fill('10');
    await d.getByLabel('Số dư cuối ca').fill('9');
    await d.getByLabel('Phạm vi tài khoản').fill(ref);
    await save(page);
    await expect(page.locator('tbody tr').filter({ hasText: ref })).toContainText('-1');
  } finally {
    await ctx.close();
  }
});

test('Bảo hành chặn sử dụng, nhận lại chuyển về chờ kiểm tra', async ({ page }) => {
  const f = fixture();
  try {
    await login(page, 'technician');
    await page.goto('/app/warranty');
    await page.getByRole('button', { name: 'Thêm hồ sơ bảo hành' }).click();
    let d = page.getByRole('dialog');
    await d.getByLabel('Thiết bị', { exact: false }).selectOption(f.asset);
    await d.getByLabel('Đơn vị bảo hành').fill('E2E DEV partner');
    await d.getByLabel('Chứng từ / mã hồ sơ').fill(f.code);
    await d.getByLabel('Điều kiện và phạm vi').fill('E2E DEV điều kiện mô phỏng');
    await d.getByLabel('Ngày kết thúc').fill('2099-12-31');
    await save(page);
    const coverage = page.locator('tbody tr').filter({ hasText: f.code }).first();
    await coverage.getByRole('button', { name: 'Gửi bảo hành', exact: true }).click();
    d = page.getByRole('dialog');
    await d.getByLabel('Vấn đề và tình trạng').fill('E2E DEV kết nối cần kiểm tra');
    await d.getByLabel('Mã tiếp nhận').fill(f.code);
    await save(page);
    await page
      .locator('tbody tr')
      .filter({ hasText: f.code })
      .filter({ has: page.getByRole('button', { name: 'Nhận lại / kết thúc' }) })
      .getByRole('button', { name: 'Nhận lại / kết thúc' })
      .click();
    d = page.getByRole('dialog');
    await d.getByLabel('Kết quả của đối tác').selectOption('REPAIRED');
    await d.getByLabel('Biên bản nhận lại').fill('E2E DEV nhận lại; cần kiểm tra an toàn');
    await save(page);
    const api = await apiLogin();
    expect(
      (await (await api.get('/api/equipment')).json()).find((e: any) => e.id === f.asset).state,
    ).toBe('AWAIT_INSPECTION');
    await api.dispose();
  } finally {
    f.cleanup();
  }
});

test('Hướng dẫn công bố và xác nhận được lưu theo phiên bản trên đơn', async ({ page }) => {
  const f = fixture(),
    api = await apiLogin();
  try {
    await login(page, 'owner');
    await page.goto('/app/dispatch');
    await page.getByLabel('Loại thiết bị xem hướng dẫn').selectOption(f.type);
    await page.getByRole('button', { name: 'Công bố hướng dẫn sử dụng', exact: true }).click();
    let d = page.getByRole('dialog');
    await d.getByLabel('Tên hướng dẫn').fill('E2E DEV hướng dẫn');
    await d.getByLabel('Các bước sử dụng').fill('E2E DEV các bước thử nghiệm');
    await d.getByLabel('Lưu ý an toàn').fill('E2E DEV ngừng sử dụng khi có lỗi');
    await d.getByLabel('Nguồn tài liệu').fill('E2E DEV fixture, không phải hướng dẫn thật');
    await save(page);
    await expect(page.getByText('Phiên bản 1', { exact: true })).toBeVisible();
    const customer = (await (await api.get('/api/customers')).json())[0],
      from = new Date(Date.now() + 86400000),
      to = new Date(+from + 3600000);
    const o = await (
      await post(api, '/rentals', {
        customerId: customer.id,
        lines: [{ typeId: f.type, quantity: 1 }],
        startsAt: from.toISOString(),
        endsAt: to.toISOString(),
        address: 'E2E DEV',
        acceptTerms: true,
      })
    ).json();
    await page.goto('/app/orders/' + o.id);
    await page.getByRole('button', { name: 'Xác nhận đã được hướng dẫn', exact: true }).click();
    d = page.getByRole('dialog');
    await d.getByLabel('Người đã nhận hướng dẫn').fill('E2E DEV khách');
    await d.getByLabel('Căn cứ đã hướng dẫn').fill('E2E DEV xác nhận thử');
    await save(page);
    await expect(page.getByText(/Đã xác nhận: E2E DEV khách/)).toBeVisible();
    const csrf = await (await api.get('/api/auth/csrf')).json();
    const bytes = await page.screenshot({ clip: { x: 0, y: 0, width: 20, height: 20 } });
    const uploaded = await api.post('/api/rentals/' + o.id + '/evidence', {
      headers: { [csrf.headerName]: csrf.token },
      multipart: { file: { name: 'E2E-DEV-recovery.png', mimeType: 'image/png', buffer: bytes } },
    });
    expect(uploaded.ok(), await uploaded.text()).toBeTruthy();
  } finally {
    f.cleanup();
    await api.dispose();
  }
});

test('Khoản thưởng vào phiếu lương riêng và nhân viên gửi yêu cầu đối chiếu', async ({
  page,
  browser,
}) => {
  const staff = crypto.randomUUID(),
    account = crypto.randomUUID(),
    username = 'e2e-pay-' + account.slice(0, 8),
    period = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).slice(0, 7),
    api = await apiLogin(),
    ctx = await browser.newContext({ baseURL }),
    employee = await ctx.newPage();
  sql(
    `begin; insert into user_account(id,username,password_hash,display_name,role) select '${account}','${username}',password_hash,'E2E DEV payslip','TECHNICIAN' from user_account where username='owner'; insert into staff(id,name,position,hourly_rate) values('${staff}','${username}','E2E DEV',100); commit;`,
  );
  try {
    await post(api, '/payroll-extras/links', { staffId: staff, accountId: account });
    await login(page, 'accountant');
    await page.goto('/app/payroll-extras');
    await page.getByRole('button', { name: 'Lập khoản lương bổ sung', exact: true }).click();
    let d = page.getByRole('dialog');
    await d.getByLabel('Nhân viên', { exact: false }).selectOption(staff);
    await d.getByLabel('Loại khoản').selectOption('BONUS');
    await d.getByLabel('Số tiền').fill('100');
    await d.getByLabel('Chính sách, chứng từ').fill('E2E DEV bonus');
    await save(page);
    const data = await (await api.get('/api/payroll-extras')).json(),
      adjustment = data.adjustments.find((a: any) => a.staff_id === staff);
    await post(api, '/payroll-extras/adjustments/' + adjustment.id + '/decide', {
      accepted: true,
      note: 'E2E DEV approved',
    });
    const payroll = await (await post(api, '/payroll', { staffId: staff, period })).json();
    const accountant = await apiLogin('accountant');
    await post(accountant, '/payroll/' + payroll.id + '/approve');
    await accountant.dispose();
    await login(employee, username);
    await employee.goto('/app/my-payslips');
    await expect(employee.getByText('Thưởng', { exact: true })).toBeVisible();
    await employee
      .getByRole('button', { name: 'Đề nghị kiểm tra phiếu lương', exact: true })
      .click();
    await employee
      .getByRole('dialog')
      .getByLabel('Nội dung cần đối chiếu')
      .fill('E2E DEV đề nghị giải thích khoản thưởng');
    await save(employee);
    await expect(employee.getByText('E2E DEV đề nghị giải thích khoản thưởng')).toBeVisible();
    const other = await apiLogin('technician');
    expect(
      JSON.stringify(await (await other.get('/api/payroll-extras/mine')).json()),
    ).not.toContain(payroll.id);
    await other.dispose();
  } finally {
    sql(
      `update user_account set enabled=false where id='${account}'; update staff set active=false where id='${staff}';`,
    );
    await api.dispose();
    await ctx.close();
  }
});

test('Các màn mới có thể dùng trên desktop và điện thoại, không lỗi truy cập', async ({ page }) => {
  test.setTimeout(120000);
  await login(page, 'owner');
  for (const route of [
    'stock-operations',
    'accounting',
    'warranty',
    'verifications',
    'dispatch',
    'payroll-extras',
    'my-payslips',
    'performance',
  ]) {
    await page.goto('/app/' + route);
    await expect(page.locator('h1')).toBeVisible();
    await expect(page.getByText('Đang tải dữ liệu…', { exact: true })).not.toBeVisible();
    await page.waitForTimeout(200);
    for (const width of [1440, 375]) {
      await page.setViewportSize({ width, height: 900 });
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
        route + ' ' + width,
      ).toBeTruthy();
    }
    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
      .analyze();
    expect(
      results.violations
        .filter((v) => ['serious', 'critical'].includes(v.impact || ''))
        .map((v) => ({ id: v.id, nodes: v.nodes.map((n) => n.failureSummary) })),
      route,
    ).toEqual([]);
    await page.screenshot({
      path: join(root, '.dev/qa/expansion-' + route + '-mobile.png'),
      fullPage: true,
    });
  }
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('/app/performance');
  await page.screenshot({
    path: join(root, '.dev/qa/expansion-performance-desktop.png'),
    fullPage: true,
  });
});
