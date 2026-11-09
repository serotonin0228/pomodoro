import { defineConfig } from '@playwright/test';
export default defineConfig({
  webServer: { command: 'npx expo start --web --port 8081', url: 'http://127.0.0.1:8081', reuseExistingServer: !process.env.CI, timeout: 120000 },
  testDir: './tests/e2e', timeout: 30000, use: { baseURL: 'http://127.0.0.1:8081', viewport: { width: 390, height: 844 }, launchOptions: { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || '/usr/bin/chromium', args: ['--no-sandbox', '--autoplay-policy=no-user-gesture-required'] } },
});
