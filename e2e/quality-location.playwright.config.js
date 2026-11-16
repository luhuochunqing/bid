import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: '.',
  testMatch: 'quality-location-render.spec.js',
  workers: 1,
  timeout: 60_000,
  reporter: 'list',
  outputDir: '../.rehearsal/quality-location/results',
  use: { ...devices['Desktop Chrome'], channel: process.env.PLAYWRIGHT_CHANNEL, headless: true, viewport: { width: 1440, height: 900 } }
})
