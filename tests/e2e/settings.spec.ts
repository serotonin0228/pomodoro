import { test, expect } from '@playwright/test';

test('menus open on first tap, switches are blue, and timer settings match in both modes', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('button', { name: '开始专注', exact: true })).toBeEnabled();
  const openTimer = async () => {
    await page.getByRole('button', { name: '设置', exact: true }).click();
    await page.getByRole('button', { name: '番茄钟设置', exact: true }).click();
  };
  await openTimer();
  await expect(page.getByLabel('自定义分钟数')).toBeVisible();
  await page.getByRole('button', { name: /^长休息间隔：/ }).click();
  await expect(page.getByRole('menuitem', { name: '每 4 轮', exact: true })).toBeVisible();
  await page.getByRole('menuitem', { name: '每 4 轮', exact: true }).click();
  await expect(page.getByRole('switch', { name: '结束提示音' }).locator('div').first()).toHaveCSS('background-color', 'rgb(0, 122, 255)');
  await page.getByLabel('关闭面板', { exact: true }).click({ position: { x: 4, y: 4 } });
  await page.getByRole('tab', { name: '正计时', exact: true }).click();
  await openTimer();
  await expect(page.getByLabel('自定义分钟数')).toBeVisible();
  await expect(page.getByRole('button', { name: /^长休息间隔：/ })).toBeVisible();
  await expect(page.getByRole('switch', { name: '结束提示音' })).toBeVisible();
});
