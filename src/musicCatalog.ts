export type MusicTrack = {
  id: string; user_id: string; title: string; artist: string; audio_revision: number;
  bytes: number; sha256: string; md5: string; sort_order: number; updated_at: string;
};
export const musicKey = (track: MusicTrack) => `${track.user_id}-${track.id}-${track.audio_revision}`;
export function validMusic(value: unknown): value is MusicTrack {
  if (!value || typeof value !== 'object') return false;
  const t = value as MusicTrack;
  return /^[a-f0-9-]{36}$/.test(t.id) && /^[a-f0-9-]{36}$/.test(t.user_id) && typeof t.title === 'string'
    && typeof t.artist === 'string' && Number.isInteger(t.audio_revision) && t.audio_revision > 0
    && Number.isSafeInteger(t.bytes) && t.bytes > 0 && t.bytes <= 30000000
    && /^[a-f0-9]{64}$/.test(t.sha256) && /^[a-f0-9]{32}$/.test(t.md5);
}
