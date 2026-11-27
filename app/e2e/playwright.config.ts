import { defineConfig, devices } from '@playwright/test';

// E2E runs against the real stack, no mocks (plan §4.5).
// Default: dev server (T17). T41 sets E2E_BASE_URL=http://localhost:8080 (JAR).
export default defineConfig({
  testDir: './tests',
  // T41: real OpenRouter turns take 20-60 s; one worker keeps turns sequential (rate limit, budget).
  timeout: 300_000,
  workers: 1,
  // Coverage proof: every endpoint and every AI step ran for real (ADR-000 §10). The JAR's log path comes
  // from E2E_APP_LOG and must be outside test-results/, which Playwright empties at the start of a run.
  globalTeardown: './global-teardown.ts',
  outputDir: './test-results',
  reporter: [['list'], ['html', { outputFolder: 'test-results/html-report', open: 'never' }]],
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
