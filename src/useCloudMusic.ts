import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { cloud, musicDownloadRequest } from './cloud';
import { MusicTrack, musicKey, validMusic } from './musicCatalog';
import { cachedMusic, downloadMusic, releaseMusic } from './musicCache';

type LocalState = { tracks: MusicTrack[]; sources: Record<string, string>; versions: Record<string, number> };
const empty = (): LocalState => ({ tracks: [], sources: {}, versions: {} });
export function useCloudMusic(userId: string | null) {
  const [state, setState] = useState<LocalState>(empty);
  const [ready, setReady] = useState(false), [refreshing, setRefreshing] = useState(false);
  const [progress, setProgress] = useState<Record<string, number>>({});
  const [error, setError] = useState('');
  const generation = useRef(0), current = useRef(state), account = useRef(userId);
  const tasks = useRef(new Map<string, Promise<string>>());
  const registry = useRef<Record<string, number>>({});
  const writes = useRef(Promise.resolve());
  const refreshingLock = useRef(false);
  current.current = state; account.current = userId;
  const refresh = useCallback(async () => {
    if (!cloud || !userId || refreshingLock.current) return;
    const serial = generation.current;
    refreshingLock.current = true; setRefreshing(true);
    try {
      const { data, error } = await cloud.from('music_tracks').select('id,user_id,title,artist,audio_revision,bytes,sha256,md5,sort_order,updated_at').eq('user_id', userId).eq('enabled', true).order('sort_order').order('id');
      if (error) throw error;
      const tracks = (data ?? []).filter(validMusic).filter(t => t.user_id === userId);
      if (serial !== generation.current || account.current !== userId) return;
      await AsyncStorage.setItem(`pomodoro.music.catalog.${userId}`, JSON.stringify(tracks));
      if (serial !== generation.current) return;
      setState(s => JSON.stringify(s.tracks) === JSON.stringify(tracks) ? s : { ...s, tracks }); setError('');
    } catch { /* Keep cached music and refresh silently on the next foreground/realtime tick. */ }
    finally { if (serial === generation.current) { refreshingLock.current = false; setRefreshing(false); } }
  }, [userId]);
  useEffect(() => {
    const serial = ++generation.current;
    current.current.sources && Object.values(current.current.sources).forEach(releaseMusic);
    setState(empty()); setReady(false); setError(''); setProgress({}); refreshingLock.current = false; registry.current = {};
    if (!userId) { setReady(true); return; }
    void (async () => {
      const [raw, versions] = await Promise.all([AsyncStorage.getItem(`pomodoro.music.catalog.${userId}`), AsyncStorage.getItem(`pomodoro.music.versions.${userId}`)]);
      const tracks: MusicTrack[] = raw ? JSON.parse(raw).filter(validMusic).filter((t: MusicTrack) => t.user_id === userId) : [];
      const sources: Record<string, string> = {};
      for (const track of tracks) { const uri = await cachedMusic(track); if (uri) sources[musicKey(track)] = uri; }
      if (serial !== generation.current) { Object.values(sources).forEach(releaseMusic); return; }
      registry.current = versions ? JSON.parse(versions) : {};
      setState({ tracks, sources, versions: registry.current });
    })().catch(() => { if (serial === generation.current) setError('音乐读取失败'); }).finally(() => {
      if (serial === generation.current) { setReady(true); void refresh(); }
    });
    return () => { generation.current++; };
  }, [userId, refresh]);
  useEffect(() => {
    if (!userId || !cloud) return;
    const channel = cloud.channel(`music-${userId}`).on('postgres_changes', { event: '*', schema: 'public', table: 'music_tracks', filter: `user_id=eq.${userId}` }, () => void refresh()).subscribe();
    const app = AppState.addEventListener('change', value => { if (value === 'active') void refresh(); });
    const timer = setInterval(() => { if (AppState.currentState === 'active') void refresh(); }, 30000);
    return () => { clearInterval(timer); app.remove(); void cloud!.removeChannel(channel); };
  }, [userId, refresh]);
  const ensureDownloaded = useCallback((track: MusicTrack): Promise<string> => {
    const key = musicKey(track), known = current.current.sources[key];
    if (known) return Promise.resolve(known);
    if (!userId || track.user_id !== userId) return Promise.reject(new Error('Login required'));
    const pending = tasks.current.get(key);
    if (pending) return pending;
    const serial = generation.current;
    setProgress(p => ({ ...p, [key]: 0 })); setError('');
    const task = (async () => {
      const existing = await cachedMusic(track);
      const request = existing ? null : await musicDownloadRequest(track.id, track.audio_revision);
      const uri = existing ?? await downloadMusic(track, request!.url, request!.headers, value => {
        if (serial === generation.current) setProgress(p => ({ ...p, [key]: value }));
      });
      if (serial !== generation.current || account.current !== userId) { releaseMusic(uri); throw new Error('Account changed'); }
      const versions = { ...registry.current, [track.id]: track.audio_revision };
      registry.current = versions;
      writes.current = writes.current.catch(() => {}).then(() => AsyncStorage.setItem(`pomodoro.music.versions.${userId}`, JSON.stringify(versions)));
      await writes.current;
      if (serial !== generation.current) { releaseMusic(uri); throw new Error('Account changed'); }
      setState(s => ({ ...s, sources: { ...s.sources, [key]: uri }, versions: { ...s.versions, [track.id]: track.audio_revision } }));
      return uri;
    })().catch(error => { if (serial === generation.current) setError('下载失败'); throw error; }).finally(() => {
      tasks.current.delete(key);
      if (serial === generation.current) setProgress(p => { const next = { ...p }; delete next[key]; return next; });
    });
    tasks.current.set(key, task); return task;
  }, [userId]);
  const source = (track: MusicTrack) => state.sources[musicKey(track)];
  return { ...state, source, ready, refreshing, refresh, progress, error, ensureDownloaded };
}
