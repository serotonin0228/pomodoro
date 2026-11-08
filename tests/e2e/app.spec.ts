import { test, expect } from '@playwright/test';
test.beforeEach(async ({ context }) => { await context.grantPermissions(['notifications']); });

test('stopwatch survives refresh, pause excludes time, record reaches statistics', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', e => errors.push(e.message));
  await page.goto('/');
  await expect(page.getByRole('button', { name: '开始专注', exact: true })).toBeEnabled();
  await expect(page.getByTestId('timer-digits')).toHaveText('25:00');
  await page.getByRole('tab', { name: '正计时', exact: true }).click();
  await expect(page.getByTestId('timer-digits')).toHaveText('00:00');
  await page.getByRole('button', { name: '开始专注', exact: true }).click();
  await expect(page.getByTestId('timer-digits')).not.toHaveText('00:00', { timeout: 5000 });
  await page.reload();
  await expect(page.getByRole('button', { name: '暂停', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '暂停', exact: true }).click();
  const paused = await page.getByTestId('timer-digits').textContent();
  await page.waitForTimeout(1200);
  await expect(page.getByTestId('timer-digits')).toHaveText(paused!);
  await page.getByRole('button', { name: '结束专注', exact: true }).click();
  await page.getByRole('button', { name: '结束并保存', exact: true }).last().click();
  await page.getByRole('tab', { name: '统计', exact: true }).click();
  await expect(page.getByText('正计时专注', { exact: true })).toBeVisible();
  await expect(page.getByTestId('stats-total')).not.toHaveText('0秒');
  expect(errors).toEqual([]);
});
test('custom duration, music playback, settings theme, and responsive layout', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: '调整计时时长', exact: true }).click();
  const input = page.getByRole('textbox', { name: '自定义分钟数' });
  await input.fill('45');
  await page.getByRole('button', { name: '设定时长', exact: true }).click();
  await expect(page.getByTestId('timer-digits')).toHaveText('45:00');
  await page.getByRole('button', { name: '打开背景音乐', exact: true }).click();
  await page.getByRole('button', { name: '试听音乐', exact: true }).click();
  await expect(page.getByRole('button', { name: '暂停播放', exact: true })).toBeVisible();
  await page.getByRole('button', { name: /^曲目：/ }).click();
  await page.getByRole('menuitem', { name: 'JRPG Piano', exact: true }).click();
  await expect(page.getByRole('button', { name: '曲目：JRPG Piano', exact: true })).toBeVisible();
  await page.getByLabel('关闭面板', { exact: true }).click({ position: { x: 4, y: 4 } });
  await page.getByRole('button', { name: '切换深色模式', exact: true }).click();
  await page.reload();
  await expect(page.getByTestId('timer-digits')).toHaveText('45:00');
  await expect(page.getByTestId('timer-digits').locator(':scope > *').first()).toHaveCSS('color', 'rgb(245, 245, 247)');
  await page.setViewportSize({ width: 320, height: 700 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  await page.setViewportSize({ width: 1280, height: 900 });
  await expect(page.getByText('专注当下', { exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
});
test('expired countdown restores at its deadline, saves one record, and exports CSV', async ({ page }) => {
  await page.addInitScript(() => {
    const start = Date.now() - 90000;
    localStorage.setItem('pomodoro.device', 'test-device');
    localStorage.setItem('pomodoro.guest.v1', JSON.stringify({ pending: false, revision: 0, state: {
      timer: { id: 'expired', mode: 'countdown', phase: 'focus', status: 'running', duration: 60, elapsed: 0, startedAt: start, createdAt: start, device: 'test-device' },
      records: [], segments: [], round: 0, updatedAt: start,
      settings: { focusMinutes: 1, breakMinutes: 5, longBreakMinutes: 15, rounds: 4, dailyGoal: 120, theme: 'light', notifications: false, autoBreak: false, autoFocus: false, sound: false, musicDuringBreak: false },
    } }));
  });
  await page.goto('/');
  await expect(page.getByRole('button', { name: '开始休息', exact: true })).toBeVisible();
  await expect(page.getByTestId('timer-digits')).toHaveText('00:00');
  await page.getByRole('tab', { name: '统计', exact: true }).click();
  await expect(page.getByTestId('stats-total')).toHaveText('1分钟');
  await expect(page.getByText('倒计时专注', { exact: true })).toHaveCount(1);
  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: '导出专注记录', exact: true }).click();
  expect((await download).suggestedFilename()).toMatch(/Pomodoro-.*\.csv/);
});
