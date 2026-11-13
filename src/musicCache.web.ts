import { MusicTrack, musicKey } from './musicCatalog';

function database(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open('pomodoro.cloud-music.v1', 1);
    request.onupgradeneeded = () => request.result.createObjectStore('audio');
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}
async function transact<T>(mode: IDBTransactionMode, action: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  const db = await database();
  return new Promise((resolve, reject) => {
    const tx = db.transaction('audio', mode), request = action(tx.objectStore('audio'));
    tx.oncomplete = () => { db.close(); resolve(request.result); };
    tx.onabort = () => { db.close(); reject(tx.error); };
    tx.onerror = () => { db.close(); reject(tx.error); };
  });
}
export async function cachedMusic(track: MusicTrack): Promise<string | null> {
  const blob = await transact<Blob | undefined>('readonly', store => store.get(musicKey(track)));
  return blob?.size === track.bytes ? URL.createObjectURL(blob) : null;
}
export async function downloadMusic(track: MusicTrack, url: string, headers: Record<string, string>, progress: (value: number) => void): Promise<string> {
  const response = await fetch(url, { headers });
  if (!response.ok || !response.body) throw new Error('Music download failed');
  const reader = response.body.getReader(), chunks: Uint8Array<ArrayBuffer>[] = [];
  let size = 0;
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      size += value.length;
      if (size > track.bytes) throw new Error('Invalid audio size');
      chunks.push(new Uint8Array(value)); progress(Math.min(.99, size / track.bytes));
    }
  } finally { reader.releaseLock(); }
  const blob = new Blob(chunks, { type: 'audio/mpeg' });
  const digest = Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', await blob.arrayBuffer()))).map(n => n.toString(16).padStart(2, '0')).join('');
  if (size !== track.bytes || digest !== track.sha256) throw new Error('Music integrity check failed');
  await transact('readwrite', store => store.put(blob, musicKey(track)));
  progress(1);
  return URL.createObjectURL(blob);
}
export function releaseMusic(uri: string) { URL.revokeObjectURL(uri); }
