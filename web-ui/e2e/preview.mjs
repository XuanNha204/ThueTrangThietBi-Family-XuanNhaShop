import { chromium } from '@playwright/test';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
const dir = resolve('../.dev/qa');
await mkdir(dir, { recursive: true });
const browser = await chromium.launch();
const context = await browser.newContext({
  viewport: { width: 1440, height: 1000 },
  locale: 'vi-VN',
  reducedMotion: 'reduce',
  timezoneId: 'Asia/Ho_Chi_Minh',
});
const page = await context.newPage();
const errors = [];
page.on('pageerror', (e) => errors.push(e.message));
await page.goto('http://127.0.0.1:5175');
await page.getByRole('heading', { name: 'Hôm nay, bạn cần gì?' }).waitFor();
await page.locator('.product-card').first().waitFor();
await page.evaluate(() => document.fonts.ready);
await page.screenshot({
  path: resolve(dir, 'store-desktop.png'),
  fullPage: true,
  animations: 'disabled',
});
await page.setViewportSize({ width: 375, height: 812 });
await page.screenshot({
  path: resolve(dir, 'store-mobile.png'),
  fullPage: true,
  animations: 'disabled',
});
await page.setViewportSize({ width: 1440, height: 1000 });
await page.goto('http://127.0.0.1:5175/login');
await page.getByLabel('Tên đăng nhập', { exact: true }).fill('owner');
await page.getByLabel('Mật khẩu', { exact: true }).fill('XuanNhaDev!2026');
await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
await page.waitForURL('**/app');
await page.getByText('Tổng thiết bị', { exact: true }).waitFor();
await page.evaluate(() => document.fonts.ready);
await page.screenshot({
  path: resolve(dir, 'dashboard-desktop.png'),
  fullPage: true,
  animations: 'disabled',
});
await page.setViewportSize({ width: 375, height: 812 });
await page.screenshot({
  path: resolve(dir, 'dashboard-mobile.png'),
  fullPage: true,
  animations: 'disabled',
});
await page.setViewportSize({ width: 1440, height: 1000 });
await page.goto('http://127.0.0.1:5175/app/equipment');
await page.locator('tbody tr').first().waitFor();
await page.screenshot({
  path: resolve(dir, 'equipment-desktop.png'),
  fullPage: true,
  animations: 'disabled',
});
await page.goto('http://127.0.0.1:5175/app/orders');
await page.locator('tbody tr').first().waitFor();
await page.locator('a.order-code').first().click();
await page.getByRole('heading', { name: 'Thiết bị trong đơn' }).waitFor();
await page.screenshot({
  path: resolve(dir, 'order-desktop.png'),
  fullPage: true,
  animations: 'disabled',
});
await writeFile(resolve(dir, 'preview-errors.json'), JSON.stringify(errors, null, 2));
console.log(JSON.stringify({ directory: dir, errors }));
await browser.close();
