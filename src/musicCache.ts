import * as FS from 'expo-file-system/legacy';
import { MusicTrack, musicKey } from './musicCatalog';

function path(track: MusicTrack) {
  if (!FS.documentDirectory) throw new Error('Music storage unavailable');
  return `${FS.documentDirectory}cloud-music/${musicKey(track)}.mp3`;
}
export async function cachedMusic(track: MusicTrack): Promise<string | null> {
  const uri = path(track), info = await FS.getInfoAsync(uri);
  return info.exists && !info.isDirectory && info.size === track.bytes ? uri : null;
}
export async function downloadMusic(track: MusicTrack, url: string, headers: Record<string, string>, progress: (value: number) => void): Promise<string> {
  const uri = path(track), temporary = `${uri}.part`;
  await FS.makeDirectoryAsync(`${FS.documentDirectory}cloud-music`, { intermediates: true });
  try {
    const task = FS.createDownloadResumable(url, temporary, { headers }, event => progress(Math.min(.99, event.totalBytesWritten / track.bytes)));
    const result = await task.downloadAsync();
    if (!result || result.status !== 200) throw new Error('Music download failed');
    const info = await FS.getInfoAsync(temporary, { md5: true });
    if (!info.exists || info.isDirectory || info.size !== track.bytes || info.md5 !== track.md5) throw new Error('Music integrity check failed');
    await FS.deleteAsync(uri, { idempotent: true });
    await FS.moveAsync({ from: temporary, to: uri });
    progress(1);
    return uri;
  } catch (error) { await FS.deleteAsync(temporary, { idempotent: true }).catch(() => {}); throw error; }
}
export function releaseMusic(_uri: string) {}
