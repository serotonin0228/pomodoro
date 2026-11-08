import { english } from './locales/en';
export type Language = 'zh' | 'en';
export function translate(text: string, language: Language): string {
  if (language === 'zh' || !text) return text;
  if (Object.hasOwn(english, text)) return english[text];
  const trimmed = text.trim();
  if (Object.hasOwn(english, trimmed)) return text.replace(trimmed, english[trimmed]);
  let m: RegExpMatchArray | null;
  if ((m = text.match(/^(\d+)\s*小时(?:(\d+)分)?$/))) return `${m[1]}h${m[2] ? ` ${m[2]}m` : ''}`;
  if ((m = text.match(/^(\d+)\s*(分钟|分|秒)$/))) return `${m[1]} ${m[2] === '秒' ? 'sec' : 'min'}`;
  if ((m = text.match(/^每 (\d+) 轮$/))) return `${m[1]} sessions`;
  if ((m = text.match(/^(\d+)点$/))) return `${m[1].padStart(2, '0')}:00`;
  if ((m = text.match(/^(\d{4})年(\d{1,2})月$/))) return new Date(+m[1], +m[2] - 1, 1).toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
  if ((m = text.match(/^(\d{1,2})月(\d{1,2})日$/))) return new Date(2000, +m[1] - 1, +m[2]).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  if (text.includes(' — ')) return text.split(' — ').map(part => translate(part, language)).join(' — ');
  if ((m = text.match(/^每日目标 (.+) · 已完成 (\d+)%$/))) return `Daily goal ${translate(m[1], language)} · ${m[2]}% completed`;
  if (text.includes(' · ')) return text.split(' · ').map(part => translate(part, language)).join(' · ');
  if ((m = text.match(/^(增加|减少)(.+)$/))) return `${m[1] === '增加' ? 'Increase' : 'Decrease'} ${translate(m[2], language)}`;
  if ((m = text.match(/^(.+)：(.+)$/))) return `${translate(m[1], language)}: ${translate(m[2], language)}`;
  if ((m = text.match(/^(\d+月\d+日)(?:日历)?专注(.+)$/))) return `${translate(m[1], language)}: ${translate(m[2], language)} of focus`;
  if ((m = text.match(/^(\d+)点专注(.+)$/))) return `${m[1]}:00: ${translate(m[2], language)} of focus`;
  if ((m = text.match(/^请打开 (.+) 收到的确认邮件，点击确认链接，然后返回这里使用密码登录。$/))) return `Open the confirmation email sent to ${m[1]}, confirm your address, then return here to sign in with your password.`;
  return text;
}
