import { defineConfig } from '@playwright/test';

/**
 * Browser check (CI): the built dashboard, served by `vite preview`, against
 * a backend running on demo data only (DataInitializer, never real data).
 * E2E_API is the backend base URL; the dashboard is built with
 * VITE_API_URL pointing at it.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 180_000,
  retries: 0,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:4173',
    viewport: { width: 1440, height: 900 },
    // Locally: E2E_CHROME=/usr/bin/google-chrome; in CI Playwright's own Chromium.
    launchOptions: process.env.E2E_CHROME ? { executablePath: process.env.E2E_CHROME } : {},
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: {
    command: 'npx vite preview --port 4173 --strictPort',
    url: 'http://localhost:4173',
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
});
