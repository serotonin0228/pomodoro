export function authErrorMessage(error: { code?: string; message?: string; status?: number }): string {
  const messages: Record<string, string> = {
    invalid_credentials: '邮箱或密码不正确',
    email_not_confirmed: '请先点击邮件中的链接确认邮箱，再登录',
    user_already_exists: '该邮箱已注册，请直接登录',
    weak_password: '密码强度不足，请使用更长的密码',
    over_email_send_rate_limit: '邮件发送过于频繁，请稍后重试',
    over_request_rate_limit: '操作过于频繁，请稍后重试',
    email_address_not_authorized: '当前邮件服务暂不能向此邮箱发送确认邮件',
    email_address_invalid: '请输入有效的邮箱地址',
    signup_disabled: '当前暂未开放新账号注册',
    email_provider_disabled: '邮箱登录暂未开启',
    request_timeout: '连接超时，请检查网络后重试',
  };
  if (error.code && messages[error.code]) return messages[error.code];
  if (/invalid login credentials/i.test(error.message || '')) return messages.invalid_credentials;
  if (/email not confirmed/i.test(error.message || '')) return messages.email_not_confirmed;
  if (/rate limit|too many requests/i.test(error.message || '')) return messages.over_request_rate_limit;
  if (/fetch|network|timeout/i.test(error.message || '')) return '连接失败，请检查网络后重试';
  return '暂时无法完成，请稍后重试';
}
