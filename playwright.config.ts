import { defineConfig } from '@playwright/test';
export default defineConfig({
  webServer: { command: 'npx expo export --platform web && python3 -m http.server 8081 --bind 127.0.0.1 --directory dist', url: 'http://127.0.0.1:8081', reuseExistingServer: !process.env.CI, timeout: 120000, env: { EXPO_NO_TELEMETRY: '1' } },
  testDir: './tests/e2e', timeout: 30000, use: { baseURL: 'http://127.0.0.1:8081', viewport: { width: 390, height: 844 }, launchOptions: { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || '/usr/bin/chromium', args: ['--no-sandbox', '--autoplay-policy=no-user-gesture-required'] } },
});
