import { defineConfig, devices } from '@playwright/test';

// E2E runs against the real stack, no mocks (plan §4.5).
// Default: dev server (T17). T41 sets E2E_BASE_URL=http://localhost:8080 (JAR).
export default defineConfig({
  testDir: './tests',
  outputDir: './test-results',
  reporter: [['list'], ['html', { outputFolder: 'test-results/html-report', open: 'never' }]],
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
