import test from 'node:test';
import assert from 'node:assert/strict';
import { replaceMusicQueue } from '../src/nativePlaylist';

test('downloaded file URLs cross the Android playlist bridge as records, including after restart', () => {
  const received: { uri: string }[] = [];
  let selected = -1;
  const player = {
    loop: 'all' as 'single' | 'all', clear() { received.length = 0; },
    add(value: unknown) {
      // Match AudioModule.kt's AudioSource Record boundary; a bare string fails here on Android.
      assert.equal(typeof value, 'object');
      assert.ok(value && typeof value === 'object' && 'uri' in value && typeof value.uri === 'string');
      received.push(value as { uri: string });
    },
    skipTo(index: number) { selected = index; },
  };
  replaceMusicQueue(player, [{ source: 'file:///data/user/0/com.pomodoro.focus/files/cloud-music/a.mp3' }], false, 0);
  assert.equal(received[0].uri.endsWith('/a.mp3'), true);
  replaceMusicQueue(player, [{ source: 'file:///cached/a.mp3' }, { source: 'file:///cached/b.mp3' }], true, 1);
  assert.deepEqual(received, [{ uri: 'file:///cached/a.mp3' }, { uri: 'file:///cached/b.mp3' }]);
  assert.equal(selected, 1); assert.equal(player.loop, 'single');
});
