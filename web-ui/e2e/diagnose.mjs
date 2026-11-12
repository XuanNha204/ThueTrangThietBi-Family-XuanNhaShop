import { chromium } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { writeFile } from 'node:fs/promises';
const browser = await chromium.launch();
const context = await browser.newContext({
  viewport: { width: 1440, height: 1000 },
  reducedMotion: 'reduce',
});
const page = await context.newPage();
const results = {};
async function axe(name) {
  const r = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
  results[name] = r.violations.map((v) => ({
    id: v.id,
    impact: v.impact,
    nodes: v.nodes.map((n) => ({ target: n.target, summary: n.failureSummary })),
  }));
}
await page.goto('http://127.0.0.1:5175/');
await page.locator('.product-card').first().waitFor();
await axe('store');
await page.goto('http://127.0.0.1:5175/login');
await axe('login');
await page.getByLabel('Tên đăng nhập', { exact: true }).fill('owner');
await page.getByLabel('Mật khẩu', { exact: true }).fill('XuanNhaDev!2026');
await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
await page.waitForURL('**/app');
await page.getByText('Tổng thiết bị', { exact: true }).waitFor();
await axe('dashboard');
await page.setViewportSize({ width: 375, height: 812 });
results.mobile = await page.evaluate(() => ({
  width: innerWidth,
  scroll: document.documentElement.scrollWidth,
  overflow: [...document.querySelectorAll('body *')]
    .map((el) => ({
      tag: el.tagName,
      class: el.className,
      box: el.getBoundingClientRect().toJSON(),
    }))
    .filter((v) => v.box.right > innerWidth + 1 && v.box.left >= 0)
    .slice(0, 12),
}));
await writeFile('../.dev/qa/diagnostics.json', JSON.stringify(results, null, 2));
console.log(JSON.stringify(results));
await browser.close();
