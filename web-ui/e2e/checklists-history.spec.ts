import { test, expect, request, type Page, type APIRequestContext } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
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
  const type = crypto.randomUUID(),
    asset = crypto.randomUUID(),
    code = 'E2EC-' + type.slice(0, 8);
  sql(
    `begin; insert into equipment_type(id,code,name,category,description,specifications,icon,daily_rate,deposit) values('${type}','${code}','E2E DEV Checklist ${code}','Test','Disposable checklist test','Test','fan',100,200); insert into equipment(id,code,type_id,warehouse_id,state) select '${asset}','${code}-001','${type}',id,'AWAIT_ACCEPTANCE' from warehouse limit 1; commit;`,
  );
  return {
    type,
    asset,
    code,
    cleanup: () =>
      sql(
        `begin; update maintenance_window set active=false where equipment_id='${asset}'; update maintenance_plan set status='CANCELLED',completion_note='E2E cleanup' where equipment_id='${asset}' and status in ('SCHEDULED','IN_PROGRESS'); update equipment_type set active=false where id='${type}'; update equipment set state='DISPOSED' where id='${asset}'; commit;`,
      ),
  };
}
async function post(api: APIRequestContext, path: string, data?: unknown) {
  const csrf = await (await api.get('/api/auth/csrf')).json();
  const result = await api.post('/api' + path, {
    data,
    headers: { [csrf.headerName]: csrf.token, 'Idempotency-Key': crypto.randomUUID() },
  });
  expect(result.ok(), `${path}: ${result.status()} ${await result.text()}`).toBeTruthy();
  return result;
}
async function apiLogin() {
  const api = await request.newContext({ baseURL: 'http://127.0.0.1:5175' });
  await post(api, '/auth/login', { username: 'owner', password: 'XuanNhaDev!2026' });
  return api;
}
async function login(page: Page, name: string) {
  await page.goto('/login');
  await page.getByLabel('Tên đăng nhập', { exact: true }).fill(name);
  await page.getByLabel('Mật khẩu', { exact: true }).fill('XuanNhaDev!2026');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await page.waitForURL('**/app');
}
async function accessible(page: Page) {
  const r = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
  expect(
    r.violations
      .filter((v) => ['serious', 'critical'].includes(v.impact || ''))
      .map((v) => ({ id: v.id, nodes: v.nodes.map((n) => n.failureSummary) })),
  ).toEqual([]);
}

test('Checklist theo loại chặn thiết bị chưa đạt, lưu phiên bản và kết quả trong lịch sử', async ({
  page,
  browser,
}) => {
  const f = fixture(),
    techContext = await browser.newContext({
      baseURL: 'http://127.0.0.1:5175',
      viewport: { width: 1440, height: 1000 },
    });
  try {
    await login(page, 'owner');
    await page.goto('/app/maintenance');
    await page.getByLabel('Loại thiết bị áp dụng checklist').selectOption(f.type);
    await page.getByRole('button', { name: 'Chỉnh checklist', exact: true }).click();
    let dialog = page.getByRole('dialog');
    await dialog.getByRole('button', { name: 'Thêm tiêu chí', exact: true }).click();
    await dialog.getByLabel('Nội dung tiêu chí 1', { exact: true }).fill('Dây nguồn không hở');
    await dialog
      .getByLabel('Căn cứ áp dụng checklist')
      .fill('E2E DEV checklist, không áp dụng cho sản phẩm thật');
    await dialog.getByRole('button', { name: 'Áp dụng checklist' }).click();
    await expect(dialog).not.toBeVisible();
    await expect(page.locator('.checklist-management')).toContainText('Phiên bản 1');
    await accessible(page);
    await page.screenshot({ path: '../.dev/qa/checklist-management-desktop.png', fullPage: true });
    const tech = await techContext.newPage();
    await login(tech, 'technician');
    await tech.goto('/app/equipment');
    await tech.getByPlaceholder('Tìm mã hoặc tên thiết bị…').fill(f.code);
    const row = tech.getByRole('row').filter({ hasText: f.code });
    await row.getByRole('button', { name: 'Kiểm tra', exact: true }).click();
    dialog = tech.getByRole('dialog');
    for (const label of [
      'Vận hành và kết cấu an toàn',
      'Đã làm sạch, vệ sinh phù hợp',
      'Đủ phụ kiện và đã đối chiếu',
    ])
      await dialog.getByLabel(label, { exact: true }).check();
    await dialog.getByLabel('Dây nguồn không hở', { exact: true }).selectOption('false');
    await dialog.getByLabel('Ghi chú: Dây nguồn không hở').fill('E2E dây chưa đạt');
    await dialog.getByLabel('Kết quả, căn cứ kiểm tra và lưu ý').fill('E2E DEV cần xử lý dây');
    await accessible(tech);
    await tech.screenshot({ path: '../.dev/qa/checklist-inspection-desktop.png' });
    await tech.setViewportSize({ width: 375, height: 812 });
    await accessible(tech);
    expect(
      await tech.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    ).toBeTruthy();
    await tech.screenshot({ path: '../.dev/qa/checklist-inspection-mobile.png' });
    await dialog.getByRole('button', { name: 'Lưu kiểm tra', exact: true }).click();
    await expect(dialog).not.toBeVisible();
    await expect(row.getByText('Đang bảo trì', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Chỉnh checklist', exact: true }).click();
    const edit = page.getByRole('dialog');
    await edit.getByLabel('Nội dung tiêu chí 1', { exact: true }).fill('Dây và phích cắm an toàn');
    await edit.getByLabel('Căn cứ áp dụng checklist').fill('E2E DEV cập nhật phiên bản');
    await edit.getByRole('button', { name: 'Áp dụng checklist' }).click();
    await expect(edit).not.toBeVisible();
    await row.getByRole('button', { name: 'Lịch sử thiết bị', exact: true }).click();
    dialog = tech.getByRole('dialog');
    await dialog.getByText('Xem kết quả từng tiêu chí', { exact: true }).click();
    await expect(dialog).toContainText('Dây nguồn không hở: Chưa đạt');
    await expect(dialog).not.toContainText('Dây và phích cắm an toàn');
    await accessible(tech);
    await tech.screenshot({ path: '../.dev/qa/checklist-history-mobile.png' });
    await dialog.getByRole('button', { name: 'Đóng lịch sử', exact: true }).click();
    await row.getByRole('button', { name: 'Kiểm tra', exact: true }).click();
    dialog = tech.getByRole('dialog');
    for (const label of [
      'Vận hành và kết cấu an toàn',
      'Đã làm sạch, vệ sinh phù hợp',
      'Đủ phụ kiện và đã đối chiếu',
    ])
      await dialog.getByLabel(label, { exact: true }).check();
    await dialog.getByLabel('Dây và phích cắm an toàn', { exact: true }).selectOption('true');
    await dialog.getByLabel('Kết quả, căn cứ kiểm tra và lưu ý').fill('E2E DEV đã kiểm tra đạt');
    await dialog.getByRole('button', { name: 'Lưu kiểm tra', exact: true }).click();
    await expect(dialog).not.toBeVisible();
    await expect(row.getByText('Sẵn sàng', { exact: true })).toBeVisible();
    await row.getByRole('button', { name: 'Lịch sử thiết bị', exact: true }).click();
    dialog = tech.getByRole('dialog');
    await expect(dialog).toContainText('Checklist bổ sung phiên bản 2');
    await tech.setViewportSize({ width: 1440, height: 1000 });
    await accessible(tech);
    await tech.screenshot({ path: '../.dev/qa/checklist-history-desktop.png' });
  } finally {
    f.cleanup();
    await techContext.close();
  }
});

test('Kết thúc bảo trì từ chối checklist cũ và dùng tiêu chí hiện hành', async ({ page }) => {
  const f = fixture(),
    api = await apiLogin();
  try {
    const config = (version: number, label: string) => ({
      expectedVersion: version,
      checks: [{ code: 'CABLE', label, blocking: true }],
      reason: 'E2E DEV bảo trì',
    });
    await post(api, `/maintenance/checklists/${f.type}`, config(0, 'Dây nguồn phiên bản 1'));
    const from = new Date(Date.now() + 86400000),
      to = new Date(from.getTime() + 3600000);
    const plan = (
      await (
        await post(api, '/maintenance/plans', {
          equipmentId: f.asset,
          startsAt: from.toISOString(),
          endsAt: to.toISOString(),
          reason: 'E2E DEV kế hoạch checklist',
        })
      ).json()
    ).id;
    sql(
      `update maintenance_plan set starts_at=now()-interval '1 minute' where id='${plan}'; update maintenance_window set occupied_from=now()-interval '1 minute' where id='${plan}';`,
    );
    await post(api, `/maintenance/plans/${plan}/start`);
    await login(page, 'owner');
    await page.goto('/app/maintenance');
    const row = page.locator('.maintenance-plans tr').filter({ hasText: f.code });
    await row.getByRole('button', { name: 'Kết thúc và kiểm tra' }).click();
    let dialog = page.getByRole('dialog');
    for (const label of [
      'Vận hành và kết cấu an toàn',
      'Đã làm sạch, vệ sinh phù hợp',
      'Đủ phụ kiện và đã đối chiếu',
    ])
      await dialog.getByLabel(label, { exact: true }).check();
    await dialog.getByLabel('Dây nguồn phiên bản 1', { exact: true }).selectOption('true');
    await dialog.getByLabel('Kết quả, căn cứ kiểm tra và lưu ý').fill('E2E DEV kiểm tra');
    await post(api, `/maintenance/checklists/${f.type}`, config(1, 'Dây nguồn phiên bản 2'));
    await dialog.getByRole('button', { name: 'Lưu kết quả bảo trì' }).click();
    await expect(dialog.getByRole('alert')).toContainText('Checklist đã thay đổi');
    await dialog.getByRole('button', { name: 'Quay lại', exact: true }).click();
    await row.getByRole('button', { name: 'Kết thúc và kiểm tra' }).click();
    dialog = page.getByRole('dialog');
    for (const label of [
      'Vận hành và kết cấu an toàn',
      'Đã làm sạch, vệ sinh phù hợp',
      'Đủ phụ kiện và đã đối chiếu',
    ])
      await dialog.getByLabel(label, { exact: true }).check();
    await dialog.getByLabel('Dây nguồn phiên bản 2', { exact: true }).selectOption('false');
    await dialog
      .getByLabel('Ghi chú: Dây nguồn phiên bản 2')
      .fill('E2E DEV chưa đạt, tiếp tục xử lý');
    await dialog.getByLabel('Kết quả, căn cứ kiểm tra và lưu ý').fill('E2E DEV kiểm tra lại');
    await dialog.getByRole('button', { name: 'Lưu kết quả bảo trì' }).click();
    await expect(dialog).not.toBeVisible();
    await expect(row).toContainText('Chưa đạt — vẫn bảo trì');
    const assets = await (await api.get('/api/equipment')).json();
    expect(assets.find((e: any) => e.id === f.asset).state).toBe('MAINTENANCE');
  } finally {
    f.cleanup();
    await api.dispose();
  }
});
