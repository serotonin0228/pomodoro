import { createHash } from 'node:crypto';
import type { Page } from '@playwright/test';

export async function cloudMusicFixture(page: Page) {
  const owner = '11111111-1111-4111-8111-111111111111';
  const samples = 8000 * 10, wave = Buffer.alloc(44 + samples * 2);
  wave.write('RIFF', 0); wave.writeUInt32LE(wave.length - 8, 4); wave.write('WAVEfmt ', 8);
  wave.writeUInt32LE(16, 16); wave.writeUInt16LE(1, 20); wave.writeUInt16LE(1, 22);
  wave.writeUInt32LE(8000, 24); wave.writeUInt32LE(16000, 28);
  wave.writeUInt16LE(2, 32); wave.writeUInt16LE(16, 34);
  wave.write('data', 36); wave.writeUInt32LE(samples * 2, 40);
  const titles = ['小狗安妮', 'Fresh Air', 'No Worries', '礁石海浪'];
  const tracks = titles.map((title, index) => ({
    id: `22222222-2222-4222-8222-${String(index + 1).padStart(12, '0')}`,
    user_id: owner, title, artist: 'Test artist', audio_revision: 1, bytes: wave.length,
    sha256: createHash('sha256').update(wave).digest('hex'), md5: createHash('md5').update(wave).digest('hex'), sort_order: index, updated_at: new Date().toISOString(),
  }));
  const session = {
    access_token: `${Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url')}.${Buffer.from(JSON.stringify({ sub: owner, role: 'authenticated', exp: Math.floor(Date.now() / 1000) + 3600 })).toString('base64url')}.test-signature`,
    refresh_token: 'test-refresh', token_type: 'bearer', expires_in: 3600, expires_at: Math.floor(Date.now() / 1000) + 3600,
    user: { id: owner, aud: 'authenticated', role: 'authenticated', email: 'music-test@example.invalid', app_metadata: {}, user_metadata: {}, created_at: new Date().toISOString() },
  };
  await page.addInitScript(session => {
    if (!localStorage.getItem('music-test-auth-seeded')) {
      localStorage.setItem('sb-atbbjgjlrphoumpfaonz-auth-token', JSON.stringify(session));
      localStorage.setItem('music-test-auth-seeded', 'true');
    }
    const observed = window as unknown as { playedMusic: HTMLMediaElement[] };
    observed.playedMusic = [];
    const play = HTMLMediaElement.prototype.play;
    HTMLMediaElement.prototype.play = function () { observed.playedMusic.push(this); return play.call(this); };
  }, session);
  await page.routeWebSocket(/supabase\.co/, socket => socket.close());
  const state = { tracks, downloads: 0, corrupt: false, failCatalog: false };
  await page.route('https://atbbjgjlrphoumpfaonz.supabase.co/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname.endsWith('/music_tracks')) {
      if (state.failCatalog) return route.fulfill({ status: 400, json: { code: 'TEST_OFFLINE', message: 'Library unavailable' } });
      return route.fulfill({ json: state.tracks });
    }
    if (url.pathname.endsWith('/rpc/download_music')) {
      state.downloads++;
      await new Promise(resolve => setTimeout(resolve, 150));
      return route.fulfill({ contentType: 'audio/mpeg', body: state.corrupt ? wave.subarray(0, 40) : wave });
    }
    if (url.pathname.endsWith('/rpc/server_time')) return route.fulfill({ json: Date.now() });
    if (url.pathname.endsWith('/workspaces')) return route.fulfill({ json: [] });
    if (url.pathname.endsWith('/rpc/save_workspace')) {
      const body = route.request().postDataJSON();
      return route.fulfill({ json: { user_id: owner, revision: body.expected_revision + 1, payload: body.new_payload, writer: body.device_id, updated_at: new Date().toISOString() } });
    }
    if (url.pathname.endsWith('/auth/v1/user')) return route.fulfill({ json: session.user });
    return route.fulfill({ json: {} });
  });
  return state;
}
