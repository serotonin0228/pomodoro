import type { AudioPlaylist } from 'expo-audio';

type QueuePlayer = Pick<AudioPlaylist, 'clear' | 'add' | 'loop' | 'skipTo'>;
/** Playlist.add is not normalized by expo-audio's JS wrapper. Android requires an AudioSource record. */
export function replaceMusicQueue(player: QueuePlayer, sources: readonly { source: string }[], single: boolean, target: number) {
  const records = sources.map(item => {
    if (typeof item.source !== 'string' || !item.source) throw new Error('Invalid music source');
    return { uri: item.source };
  });
  player.clear();
  for (const record of records) player.add(record);
  player.loop = single ? 'single' : 'all';
  if (records.length) player.skipTo(Math.max(0, Math.min(records.length - 1, target)));
}
