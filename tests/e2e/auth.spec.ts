import { test, expect, Page } from '@playwright/test';

async function openAccount(page: Page) {
  await page.goto('/');
  await page.getByRole('button', { name: '云同步', exact: true }).click();
  if (await page.getByText('同步服务尚未配置', { exact: true }).isVisible()) test.skip(true, '需要在 Web 构建中配置 Supabase 公开密钥');
  await expect(page.getByRole('textbox', { name: '邮箱地址' })).toBeVisible();
}

test('password login masks credentials, reports errors in Chinese, and clears the password on close', async ({ page }) => {
  let request: Record<string, unknown> | undefined;
  await page.route(/\/auth\/v1\/token\?grant_type=password$/, async route => {
    request = route.request().postDataJSON();
    await route.fulfill({ status: 400, contentType: 'application/json', body: JSON.stringify({ code: 'invalid_credentials', message: 'Invalid login credentials' }) });
  });
  await openAccount(page);
  await page.getByRole('textbox', { name: '邮箱地址' }).fill('test@example.invalid');
  const password = page.getByLabel('密码', { exact: true });
  await password.fill('Example-pass9');
  await expect(password).toHaveAttribute('type', 'password');
  await page.getByRole('button', { name: '显示密码' }).click();
  await expect(password).toHaveJSProperty('type', 'text');
  await page.getByRole('button', { name: '隐藏密码' }).click();
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('alert')).toHaveText('邮箱或密码不正确');
  expect(request).toMatchObject({ email: 'test@example.invalid', password: 'Example-pass9' });
  await page.getByLabel('关闭面板', { exact: true }).click({ position: { x: 4, y: 4 } });
  await page.getByRole('button', { name: '云同步', exact: true }).click();
  await expect(page.getByLabel('密码', { exact: true })).toHaveValue('');
});

test('registration validates passwords and handles confirmation without an OTP template', async ({ page }) => {
  let signups = 0;
  let resend: Record<string, unknown> | undefined;
  await page.route(/\/auth\/v1\/signup(?:\?.*)?$/, async route => {
    signups++;
    expect(route.request().postDataJSON()).toMatchObject({ email: 'new@example.invalid', password: 'Example-pass9' });
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ id: '00000000-0000-4000-8000-000000000001', aud: 'authenticated', role: 'authenticated', email: 'new@example.invalid', identities: [{ id: 'test-identity' }], created_at: new Date().toISOString() }) });
  });
  await page.route(/\/auth\/v1\/resend(?:\?.*)?$/, async route => {
    resend = route.request().postDataJSON();
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
  });
  await openAccount(page);
  await page.getByRole('tab', { name: '注册', exact: true }).click();
  await page.getByRole('textbox', { name: '邮箱地址' }).fill('new@example.invalid');
  await page.getByLabel('密码', { exact: true }).fill('short');
  await page.getByLabel('确认密码', { exact: true }).fill('short');
  await page.getByRole('button', { name: '创建账号', exact: true }).click();
  await expect(page.getByRole('alert')).toHaveText('密码至少需要 8 位');
  expect(signups).toBe(0);
  await page.getByLabel('密码', { exact: true }).fill('Example-pass9');
  await page.getByRole('button', { name: '创建账号', exact: true }).click();
  await expect(page.getByRole('alert')).toHaveText('两次输入的密码不一致');
  await page.getByLabel('确认密码', { exact: true }).fill('Example-pass9');
  await page.getByRole('button', { name: '创建账号', exact: true }).click();
  await expect(page.getByText('请确认邮箱', { exact: true })).toBeVisible();
  expect(signups).toBe(1);
  await page.getByRole('button', { name: '返回登录', exact: true }).click();
  await expect(page.getByRole('tab', { name: '登录', exact: true })).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByLabel('密码', { exact: true })).toHaveValue('');
  await expect(page.getByLabel('邮箱验证码')).toHaveCount(0);
  await page.getByRole('button', { name: '重新发送确认邮件', exact: true }).click();
  await expect(page.getByText('确认邮件已发送', { exact: true })).toBeVisible();
  expect(resend).toMatchObject({ email: 'new@example.invalid', type: 'signup' });
});
