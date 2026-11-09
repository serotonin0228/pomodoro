import { rangeGoal } from '../src/goal';
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { clock, dailyTotals, elapsed, finishState, initialState, mergeRecords, newTimer, pauseState } from '../src/model';

test('timer follows wall-clock time and excludes a pause, even after a long suspension', () => {
  let s = initialState('device');
  s.timer = { ...s.timer, status: 'running', startedAt: 1000 };
  s = pauseState(s, 61000);
  assert.equal(elapsed(s.timer, 600000), 60);
  s.timer = { ...s.timer, status: 'running', startedAt: 600000 };
  const done = finishState(s, 660000);
  assert.equal(done.records[0].seconds, 120);
  assert.equal(done.records[0].segments.length, 2);
});
test('countdown recovery clips time at the original deadline and saves exactly once', () => {
  let s = initialState('device');
  s.timer = { ...s.timer, duration: 60, status: 'running', startedAt: 1000 };
  s = finishState(s, 999000, true);
  assert.equal(s.records[0].seconds, 60);
  assert.equal(s.records[0].end, 61000);
  assert.equal(finishState(s, 1000000, true).records.length, 1);
});
test('rest never becomes focus data', () => {
  const s = initialState('device');
  s.timer = { ...s.timer, phase: 'break', status: 'running', startedAt: 1000 };
  assert.equal(finishState(s, 61000).records.length, 0);
});
test('statistics split at local midnight and do not double-count overlapping devices', () => {
  const start = +new Date(2026, 10, 6, 23, 50);
  const a = { id: 'a', mode: 'stopwatch' as const, start, end: start + 1200000, seconds: 1200, segments: [{ start, end: start + 1200000 }] };
  const b = { ...a, id: 'b', start: start + 300000, end: start + 900000, segments: [{ start: start + 300000, end: start + 900000 }] };
  assert.deepEqual(dailyTotals([a, b]), { '2026-11-06': 600, '2026-11-07': 600 });
  assert.deepEqual(dailyTotals([{ ...a, deleted: true }]), {});
});
test('deleted records cannot be resurrected by an older cloud copy', () => {
  const r = { id: 'a', mode: 'countdown' as const, start: 1000, end: 61000, seconds: 60, segments: [{ start: 1000, end: 61000 }] };
  assert.equal(mergeRecords([{ ...r, deleted: true }], [r])[0].deleted, true);
});
test('long breaks respect the configured round interval', () => {
  const s = initialState('device');
  assert.equal(newTimer('device', s.settings, 'countdown', 'break', 4).duration, 900);
  assert.equal(newTimer('device', s.settings, 'countdown', 'break', 3).duration, 300);
  assert.equal(clock(3661), '1:01:01');
});
test('merging divergent copies of one session preserves unique intervals without duplicates', () => {
  const a = { id: 'same', mode: 'stopwatch' as const, start: 1000, end: 11000, seconds: 10, segments: [{ start: 1000, end: 11000 }] };
  const b = { ...a, end: 26000, seconds: 15, segments: [{ start: 6000, end: 16000 }, { start: 21000, end: 26000 }] };
  const merged = mergeRecords([a], [b])[0];
  assert.equal(merged.seconds, 20);
  assert.deepEqual(merged.segments, [{ start: 1000, end: 16000 }, { start: 21000, end: 26000 }]);
});


test('goal progress follows the selected dates, excludes future targets and deleted sessions', () => {
  const state = initialState('device');
  const d1 = new Date(2025, 0, 6), d2 = new Date(2025, 0, 7), d3 = new Date(2025, 0, 8);
  const totals = { '2025-01-06': 3600, '2025-01-07': 1800, '2025-01-08': 7200 };
  assert.equal(rangeGoal({ ...state.settings, dailyGoal: 60 }, [d1, d2, d3], totals, [], +d2), 75);
  assert.equal(rangeGoal({ ...state.settings, goalType: 'none' }, [d1], totals, [], +d2), null);
  const record = { id: '1', mode: 'countdown' as const, start: +d1, end: +d1 + 1000, seconds: 1, segments: [{ start: +d1, end: +d1 + 1000 }] };
  assert.equal(rangeGoal({ ...state.settings, goalType: 'dailySessions', dailySessions: 1 }, [d1], totals, [record, { ...record, id: '2', deleted: true }], +d2), 100);
});
