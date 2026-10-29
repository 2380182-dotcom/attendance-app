import { test, expect } from '@playwright/test';

/**
 * Opens every dashboard page as the demo admin and fails on any page error,
 * console error, server error (5xx) or visible error message. Runs against
 * DEMO data only: the backend's DataInitializer (admin ADMIN001 / admin, demo
 * products) plus a demo area, shop, salesman and one voucher created here
 * through the API, so the voucher pages have something to show.
 */
const API = process.env.E2E_API || 'http://localhost:8080/api';
const COMPANY = 'DAWNBREAD';

async function api(request, method, path, token, data) {
  const res = await request.fetch(`${API}${path}`, {
    method, data, headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok()) throw new Error(`${method} ${path} -> ${res.status()} ${JSON.stringify(body)}`);
  return body.data;
}

const login = async (request, agentId, password) =>
  (await api(request, 'POST', '/auth/login', null, { companyCode: COMPANY, agentId, password })).token;

/** Demo shop + salesman + one voucher; returns ids for the pages that need one. */
async function seedDemo(request) {
  const admin = await login(request, 'ADMIN001', 'admin');
  const stamp = Date.now();
  const area = await api(request, 'POST', '/lmt/areas', admin, { name: `E2E Area ${stamp}` });
  const shop = await api(request, 'POST', '/lmt/customer-shops', admin, {
    shopCode: `E2E-${stamp}`, shopName: `E2E Demo Shop ${stamp}`, areaId: area.id,
    latitude: 31.5, longitude: 74.3, radius: 100, geoFencingEnabled: true, qrRequired: false,
  });
  const salesmanId = `E2ELOCAL${stamp}`;
  const salesman = await api(request, 'POST', '/agents', admin, {
    agentId: salesmanId, name: 'E2E Local Salesman', email: `${salesmanId.toLowerCase()}@demo.local`,
    password: 'demo-password-1', role: 'SALESMAN_LOCAL', department: 'SALES', isActive: true,
  });
  const salesmanToken = await login(request, salesmanId, 'demo-password-1');
  const products = await api(request, 'GET', '/sales/products', salesmanToken);
  await api(request, 'POST', '/sales/shop-visit', salesmanToken, {
    agentId: salesman.id, shopCode: shop.shopCode, latitude: 31.5, longitude: 74.3,
    items: [{ productId: products[0].id, quantity: 2, transactionType: 'SALE' }],
  });
  const agents = await api(request, 'GET', '/agents', admin);
  const demoAgent = agents.find((a) => a.agentId === 'DEMO001');
  return { shopId: shop.id, salesmanId: salesman.id, demoAgentId: demoAgent.id };
}

const ERROR_TEXT = /Could not load|Failed to load|Something went wrong|Unable to load/i;

/** Watches one page for errors from now on. */
function watch(page) {
  const problems = [];
  page.on('pageerror', (e) => problems.push(`page error: ${e.message}`));
  page.on('console', (m) => { if (m.type() === 'error') problems.push(`console error: ${m.text()}`); });
  page.on('response', (r) => { if (r.status() >= 500) problems.push(`HTTP ${r.status()} ${r.url()}`); });
  return problems;
}

async function visit(page, problems, path) {
  const before = problems.length;
  await page.goto(path);
  await page.waitForLoadState('networkidle');
  // Lazy sections (cockpits) load as they scroll into view.
  for (let y = 0; y < 12000; y += 800) {
    await page.evaluate((v) => window.scrollTo(0, v), y);
    await page.waitForTimeout(150);
  }
  await page.waitForLoadState('networkidle');
  await expect(page, `${path} must not bounce to login`).not.toHaveURL(/\/login/);
  const errorAlerts = await page.locator('.MuiAlert-colorError:visible, .MuiAlert-standardError:visible').count();
  if (errorAlerts > 0) problems.push(`${errorAlerts} error alert(s) shown`);
  if (await page.getByText(ERROR_TEXT).first().isVisible().catch(() => false)) problems.push('error text shown');
  return problems.slice(before).map((p) => `${path}: ${p}`);
}

test('every dashboard page opens without errors (demo data)', async ({ page, request }) => {
  const ids = await seedDemo(request);
  const problems = watch(page);

  await page.goto('/login');
  await page.getByLabel('Company Code').fill(COMPANY);
  await page.getByLabel('Agent ID').fill('ADMIN001');
  await page.getByLabel('Password').fill('admin');
  await page.getByRole('button', { name: /log ?in|sign ?in/i }).click();
  await page.waitForURL((u) => !u.pathname.startsWith('/login'));

  const pages = [
    '/hr', '/hr/today', '/hr/status', '/hr/history', '/hr/analytics', '/hr/agents', `/hr/agents/${ids.demoAgentId}`,
    '/sales', '/sales/overview', '/sales/history', '/sales/reconciliation', '/sales/pricing',
    '/sales/vouchers/local/shops', '/sales/vouchers/lmt/shops', `/sales/vouchers/local/shops/${ids.shopId}`,
    '/sales/qr-visits', `/sales/qr-visits/${ids.salesmanId}`,
    '/admin', '/admin/areas', '/admin/customer-shops', '/admin/lmt-settings', '/admin/holidays',
  ];
  const failures = [];
  for (const path of pages) failures.push(...await visit(page, problems, path));

  // Voucher detail: open the demo voucher from the shop's voucher list.
  await page.goto(`/sales/vouchers/local/shops/${ids.shopId}`);
  await page.waitForLoadState('networkidle');
  await page.locator('tbody tr').first().click();
  await page.waitForURL(/\/sales\/vouchers\/\d+$/);
  failures.push(...await visit(page, problems, new URL(page.url()).pathname));

  expect(failures, failures.join('\n')).toEqual([]);
});
