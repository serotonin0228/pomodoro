import test from 'node:test';
import assert from 'node:assert/strict';
import { translate } from '../src/translate';

test('English translates composed durations, records and controls', () => {
  assert.equal(translate('2小时15分', 'en'), '2h 15m');
  assert.equal(translate('每日目标 2小时15分 · 已完成 42%', 'en'), 'Daily goal 2h 15m · 42% completed');
  assert.equal(translate('倒计时 · 42分钟', 'en'), 'Countdown · 42 min');
  assert.equal(translate('增加每日目标', 'en'), 'Increase Daily goal');
  assert.equal(translate('语言：English', 'en'), 'Language: English');
});
test('calendar labels handle month changes, ranges and leap days', () => {
  assert.equal(translate('2028年2月', 'en'), 'February 2028');
  assert.equal(translate('2月29日', 'en'), 'Feb 29');
  assert.equal(translate('11月2日 — 11月8日', 'en'), 'Nov 2 — Nov 8');
  assert.equal(translate('18点', 'en'), '18:00');
});
test('Chinese text and user data are preserved', () => {
  assert.equal(translate('本月专注', 'zh'), '本月专注');
  assert.equal(translate('name@example.com', 'en'), 'name@example.com');
  assert.equal(translate('Forget Me Not', 'en'), 'Forget Me Not');
});
