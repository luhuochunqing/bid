import { defineConfig, devices } from '@playwright/test'

const baseURL = process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:1314'
const workers = Number(process.env.PLAYWRIGHT_WORKERS || 1)

export default defineConfig({
  testDir: './e2e',
  globalSetup: './e2e/api-global-setup.js',
  globalTeardown: './e2e/api-global-teardown.js',
  workers,
  timeout: 90_000,
  expect: {
    timeout: 15_000,
  },
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  reporter: [
    ['list'],
    ['html', { outputFolder: '.rehearsal/playwright/report', open: 'never' }],
  ],
  use: {
    baseURL,
    headless: true,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1440, height: 900 },
      },
    },
    // 跨浏览器测试：本地手动执行 npx playwright test --project=firefox --project=webkit
    // CI 仅运行 chromium
    ...(process.env.CI ? [] : [
      {
        name: 'firefox',
        use: {
          ...devices['Desktop Firefox'],
          viewport: { width: 1440, height: 900 },
        },
      },
      {
        name: 'webkit',
        use: {
          ...devices['Desktop Safari'],
          viewport: { width: 1440, height: 900 },
        },
      },
    ]),
  ],
})
