import { cloudMusicFixture } from './music-fixture';
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
  await page.getByRole('button', { name: '设置', exact: true }).click();
  await page.getByRole('button', { name: '番茄钟设置', exact: true }).click();
  const input = page.getByRole('textbox', { name: '自定义分钟数' });
  await input.fill('45');
  await page.getByRole('button', { name: '设定时长', exact: true }).click();
  await expect(page.getByTestId('timer-digits')).toHaveText('45:00');
  await page.getByRole('button', { name: '打开背景音乐', exact: true }).click();
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeVisible();
  await page.getByLabel('关闭面板', { exact: true }).click({ position: { x: 4, y: 4 } });
  await page.getByRole('button', { name: '切换深色模式', exact: true }).click();
  await page.reload();
  await expect(page.getByTestId('timer-digits')).toHaveText('45:00');
  await expect(page.getByTestId('timer-digits').locator(':scope > *').first()).toHaveCSS('color', 'rgb(245, 245, 247)');
  await page.setViewportSize({ width: 320, height: 700 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  await page.setViewportSize({ width: 1280, height: 900 });
  await expect(page.getByText('专注当下', { exact: true })).toHaveCount(0);
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

test('cloud music downloads, persists and invalidates when the database version changes', async ({ page }) => {
  const fixture = await cloudMusicFixture(page);
  await page.goto('/');
  await page.getByRole('button', { name: '打开背景音乐', exact: true }).click();
  await expect(page.getByRole('button', { name: '下载 小狗安妮', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '下载 小狗安妮', exact: true }).click();
  await expect(page.getByRole('button', { name: /^(播放|暂停) 小狗安妮$/, exact: true })).toBeVisible();
  expect(fixture.downloads).toBe(1);
  await page.reload();
  await page.getByRole('button', { name: '打开背景音乐', exact: true }).click();
  await expect(page.getByRole('button', { name: /^(播放|暂停) 小狗安妮$/, exact: true })).toBeVisible();
  fixture.failCatalog = true;
  await page.getByLabel('关闭面板', { exact: true }).click({ position: { x: 4, y: 4 } });
  await page.getByRole('button', { name: '打开背景音乐', exact: true }).click();
  await expect(page.getByText('曲库暂时无法更新，已下载的音乐仍可播放。', { exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: /^(播放|暂停) 小狗安妮$/ })).toBeVisible();
  await page.getByRole('button', { name: /^(播放|暂停) 小狗安妮$/, exact: true }).click();
  expect(fixture.downloads).toBe(1);
  fixture.failCatalog = false;
  await page.getByRole('button', { name: '暂停 小狗安妮', exact: true }).click();
  fixture.tracks[0].audio_revision = 2;
  await page.getByLabel('关闭面板', { exact: true }).click({ position: { x: 4, y: 4 } });
  await page.getByRole('button', { name: '打开背景音乐', exact: true }).click();
  await expect(page.getByRole('button', { name: '下载 小狗安妮', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '下载 小狗安妮', exact: true }).click();
  await expect(page.getByRole('button', { name: /^(播放|暂停) 小狗安妮$/, exact: true })).toBeVisible();
  expect(fixture.downloads).toBe(2);
  fixture.tracks[1].title = 'Fresh Air Updated';
  fixture.tracks = fixture.tracks.slice(0, 2);
  await page.getByLabel('关闭面板', { exact: true }).click({ position: { x: 4, y: 4 } });
  await page.getByRole('button', { name: '打开背景音乐', exact: true }).click();
  await expect(page.getByRole('button', { name: '下载 Fresh Air Updated', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: /No Worries/ })).toHaveCount(0);
});

test('failed downloads never become downloaded and can be retried', async ({ page }) => {
  const fixture = await cloudMusicFixture(page); fixture.corrupt = true;
  await page.goto('/');
  await page.getByRole('button', { name: '打开背景音乐', exact: true }).click();
  await page.getByRole('button', { name: '下载 Fresh Air', exact: true }).click();
  await expect(page.getByText('下载失败', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '下载 Fresh Air', exact: true })).toBeEnabled();
  fixture.corrupt = false;
  await page.getByRole('button', { name: '下载 Fresh Air', exact: true }).click();
  await expect(page.getByRole('button', { name: /^(播放|暂停) Fresh Air$/, exact: true })).toBeVisible();
});

test('downloaded native-style queue advances, wraps, repeats and shuffles', async ({ page }) => {
  test.setTimeout(60000);
  await cloudMusicFixture(page);
  await page.goto('/');
  await page.getByRole('button', { name: '打开背景音乐', exact: true }).click();
  await expect(page.getByRole('button', { name: '下载全部', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '刷新曲库', exact: true })).toHaveCount(0);
  await expect(page.getByText('云端曲库', { exact: true })).toHaveCount(0);
  await expect(page.getByText(/\d+(\.\d+)? MB/)).toHaveCount(0);
  for (const title of ['小狗安妮', 'Fresh Air', 'No Worries', '礁石海浪']) {
    await page.getByRole('button', { name: `下载 ${title}`, exact: true }).click();
    await expect(page.getByRole('button', { name: new RegExp(`^(播放|暂停) ${title}$`) })).toBeVisible();
  }
  await expect(page.getByRole('button', { name: /^(播放|暂停) / })).toHaveCount(4);
  const chooseMode = async (name: string) => {
    await page.getByRole('button', { name: /^播放模式：/ }).click();
    await page.getByRole('menuitem', { name, exact: true }).click();
  };
  const chooseTrack = async (name: string) => page.getByRole('button', { name: `播放 ${name}`, exact: true }).click();
  const active = () => page.evaluate(() => {
    const observed = window as unknown as { playedMusic: HTMLMediaElement[] };
    return [...observed.playedMusic].reverse().find(a => !a.paused && a.readyState >= 2)?.src ?? '';
  });
  const finishTrack = async () => {
    // Let a mode switch commit and release its previous audio element before seeking.
    await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))));
    await expect.poll(() => page.evaluate(() => {
      const observed = window as unknown as { playedMusic: HTMLMediaElement[] };
      return [...observed.playedMusic].reverse().some(a => !a.paused && a.readyState >= 2 && a.currentTime > .05);
    })).toBe(true);
    await expect.poll(active).not.toBe('');
    await expect.poll(() => page.evaluate(() => {
      const observed = window as unknown as { playedMusic: HTMLMediaElement[] };
      const media = [...observed.playedMusic].reverse().find(a => !a.paused && a.readyState >= 2 && Number.isFinite(a.duration));
      if (!media) return false;
      media.currentTime = media.duration - .15;
      return true;
    })).toBe(true);
  };
  await chooseMode('顺序播放'); await chooseTrack('小狗安妮');
  await expect.poll(active).not.toBe('');
  await finishTrack();
  await expect(page.getByRole('button', { name: /^(播放|暂停) Fresh Air$/, exact: true })).toHaveAttribute('aria-selected', 'true');
  await chooseTrack('礁石海浪'); await finishTrack();
  await expect(page.getByRole('button', { name: /^(播放|暂停) 小狗安妮$/, exact: true })).toHaveAttribute('aria-selected', 'true');
  await chooseMode('单曲循环'); await chooseTrack('No Worries');
  await finishTrack();
  await expect.poll(() => page.evaluate(() => {
    const observed = window as unknown as { playedMusic: HTMLMediaElement[] };
    return [...observed.playedMusic].reverse().find(a => !a.paused)?.currentTime ?? 999;
  })).toBeLessThan(1);
  await expect(page.getByRole('button', { name: '暂停 No Worries', exact: true })).toHaveAttribute('aria-selected', 'true');
  await chooseMode('随机播放');
  await expect.poll(active).not.toBe(''); const before = await active();
  await finishTrack(); await expect.poll(active).not.toBe(before);
});
