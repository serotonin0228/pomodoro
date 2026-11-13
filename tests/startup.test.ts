import test from 'node:test';
import assert from 'node:assert/strict';
import { initialState } from '../src/model';
import { replayStartupChanges } from '../src/startup';

test('early actions merge onto loaded data and retain their start time and device', () => {
  const loaded = initialState('device');
  loaded.settings.focusMinutes = 45;
  loaded.timer.duration = 45 * 60;
  const early = (s: typeof loaded) => ({ ...s, timer: { ...s.timer, status: 'running' as const, startedAt: 1000, device: '' } });
  const result = replayStartupChanges(loaded, [early], 'device');
  assert.equal(result.timer.duration, 2700);
  assert.equal(result.timer.startedAt, 1000);
  assert.equal(result.timer.device, 'device');
  assert.equal(result.settings.focusMinutes, 45);
  const running = { ...loaded, timer: { ...loaded.timer, status: 'running' as const, startedAt: 500 } };
  assert.equal(replayStartupChanges(running, [early], 'device').timer.startedAt, 500);
});
