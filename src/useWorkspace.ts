import { useCallback, useEffect, useRef, useState } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { AppState, Platform } from 'react-native';
import { Session } from '@supabase/supabase-js';
import { cloud, CloudRow, fetchCloud, saveCloud } from './cloud';
import { defaults, finishState, initialState, mergeRecords, pauseState, State, uid } from './model';
import { setTimeCorrection } from './time';

export function useWorkspace() {
  const [state, setState] = useState<State>(() => initialState(''));
  const [ready, setReady] = useState(false);
  const [device, setDevice] = useState('');
  const [session, setSession] = useState<Session | null>(null);
  const [status, setStatus] = useState('仅保存在本机');
  const [lastSyncedAt, setLastSyncedAt] = useState<number | null>(null);
  const [conflict, setConflict] = useState<CloudRow | null>(null);
  const [storageError, setStorageError] = useState('');
  const current = useRef(state), revision = useRef(0), dirty = useRef(false), busy = useRef(false), account = useRef<string | null>(null), storageKey = useRef('pomodoro.guest.v1'), seq = useRef(0), loading = useRef(true), conflictRef = useRef<CloudRow | null>(null);
  const writeQueue = useRef<Promise<void>>(Promise.resolve());
  const persist = useCallback((s: State, pending: boolean) => {
    // Capture the account and revision when queued; later switches cannot redirect a write.
    const key = storageKey.current;
    const value = JSON.stringify({ state: s, pending, revision: revision.current });
    writeQueue.current = writeQueue.current.then(async () => { await AsyncStorage.setItem(key, value); setStorageError(''); }).catch(() => { setStorageError('本机存储失败，请导出记录备份后检查存储空间。'); });
    return writeQueue.current;
  }, []);
  const accept = useCallback((s: State, pending = false) => { current.current = s; dirty.current = pending; seq.current++; setState(s); void persist(s, pending); }, [persist]);
  const markConflict = useCallback((row: CloudRow) => { conflictRef.current = row; setConflict(row); setStatus('有待处理的同步冲突'); }, []);
  const pull = useCallback(async (s: Session) => {
    const row = await fetchCloud(s);
    if (account.current !== s.user.id) return;
    if (!row) return;
    if (row.revision <= revision.current) { if (!dirty.current) { setLastSyncedAt(Date.now()); setStatus('已同步'); } return; }
    if (dirty.current) { markConflict(row); return; }
    revision.current = row.revision;
    accept({ ...row.payload, settings: { ...defaults, ...row.payload.settings }, records: mergeRecords(current.current.records, row.payload.records) });
    setLastSyncedAt(Date.now()); setStatus('已同步');
  }, [accept, markConflict]);
  const push = useCallback(async () => {
    if (!cloud || !account.current || !dirty.current || busy.current || loading.current || conflictRef.current) return;
    busy.current = true;
    const sent = seq.current, user = account.current;
    setStatus('正在同步');
    try {
      const row = await saveCloud(current.current, revision.current, device);
      if (account.current !== user) return;
      revision.current = row.revision;
      setLastSyncedAt(Date.now());
      if (sent === seq.current) { dirty.current = false; void persist(current.current, false); setStatus('已同步'); }
      else setStatus('等待同步');
    } catch (e) {
      if (account.current !== user) return;
      if (String((e as Error).message).includes('SYNC_CONFLICT')) {
        const { data } = await cloud.auth.getSession();
        if (data.session) { try { const row = await fetchCloud(data.session); if (row) markConflict(row); } catch { setStatus('离线 · 等待同步'); } }
      } else setStatus(String((e as Error).message).includes('Workspace limit reached') ? '云端空间已满，请导出记录' : '连接失败 · 等待同步');
    } finally { busy.current = false; }
  }, [device, markConflict, persist]);
  const update = useCallback((fn: (s: State) => State) => {
    const next = fn(current.current);
    const s = { ...next, updatedAt: Date.now() };
    accept(s, true);
    if (cloud && account.current) setStatus(conflictRef.current ? '有待处理的同步冲突' : '等待同步');
  }, [accept]);

  useEffect(() => {
    let live = true;
    const hydrate = async (s: Session | null) => {
      loading.current = true;
      setTimeCorrection(Number(await AsyncStorage.getItem('pomodoro.clockOffset')) || 0);
      const id = (await AsyncStorage.getItem('pomodoro.device')) || `${Platform.OS}-${uid()}`;
      await AsyncStorage.setItem('pomodoro.device', id);
      if (!live) return;
      setDevice(id); account.current = s?.user.id || null; setSession(s); setLastSyncedAt(null);
      storageKey.current = `pomodoro.${s?.user.id || 'guest'}.v1`;
      await writeQueue.current;
      const raw = await AsyncStorage.getItem(storageKey.current);
      let local = initialState(id);
      revision.current = 0; dirty.current = false; conflictRef.current = null; setConflict(null);
      if (raw) { try { const saved = JSON.parse(raw); if (saved.state?.timer && Array.isArray(saved.state.records)) { local = { ...saved.state, settings: { ...defaults, ...saved.state.settings } }; revision.current = saved.revision || 0; dirty.current = !!saved.pending; } } catch { setStorageError('本机数据无法读取，已启动新会话。'); } }
      if (!live) return;
      current.current = local; setState(local); setReady(true); loading.current = false;
      setStatus(s ? '正在连接' : '仅保存在本机');
      if (s) { try { await pull(s); if (!dirty.current && !conflictRef.current && revision.current === 0) setStatus('等待同步'); } catch { setStatus('连接失败 · 等待同步'); } }
    };
    void (async () => {
      if (!cloud) { await hydrate(null); return; }
      const sessionPromise = cloud.auth.getSession();
      const result = await sessionPromise;
      if (live) await hydrate(result.data.session);
    })().catch(() => { setStorageError('初始化失败，请重新打开应用。'); setReady(true); });
    const auth = cloud?.auth.onAuthStateChange((event, s) => {
      if (event === 'INITIAL_SESSION' || (s?.user.id || null) === account.current) return;
      // Defer Supabase calls until after the auth event lock is released.
      setTimeout(() => { if (live) void hydrate(s); }, 0);
    });
    return () => { live = false; auth?.data.subscription.unsubscribe(); };
  }, [pull]);
  useEffect(() => {
    if (!session || !cloud || !device) return;
    const channel = cloud.channel(`workspace-${session.user.id}`).on('postgres_changes', { event: '*', schema: 'public', table: 'workspaces', filter: `user_id=eq.${session.user.id}` }, payload => {
      const row = payload.new as CloudRow;
      if (row.writer !== device && row.revision > revision.current) void pull(session).catch(() => setStatus('连接失败 · 等待同步'));
    }).subscribe();
    const timer = setInterval(() => { if (AppState.currentState !== 'active') return; if (dirty.current) void push(); else void pull(session).catch(() => setStatus('连接失败 · 等待同步')); }, 30000);
    const sub = AppState.addEventListener('change', value => { if (value === 'active') { cloud!.auth.startAutoRefresh(); void pull(session).then(push).catch(() => setStatus('连接失败 · 等待同步')); } else cloud!.auth.stopAutoRefresh(); });
    return () => { clearInterval(timer); sub.remove(); void cloud!.removeChannel(channel); };
  }, [session, device, pull, push]);
  useEffect(() => { if (!ready || !session) return; const t = setTimeout(() => void push(), 450); return () => clearTimeout(t); }, [state, session, ready, push]);
  const resolve = useCallback((choice: 'local' | 'remote') => {
    const row = conflictRef.current;
    if (!row) return;
    const local = current.current;
    const archived = local.timer.status === 'running' ? pauseState(local) : local;
    // Store a local recovery snapshot before resolving active timers/settings.
    void AsyncStorage.setItem(`pomodoro.recovery.${Date.now()}`, JSON.stringify(archived));
    revision.current = row.revision; conflictRef.current = null; setConflict(null);
    const chosen = choice === 'local' ? local : row.payload;
    const discarded = choice === 'local' ? row.payload : local;
    const archivedRecords = ['running', 'paused'].includes(discarded.timer.status) ? finishState(discarded).records : discarded.records;
    accept({ ...chosen, records: mergeRecords(chosen.records, archivedRecords), updatedAt: Date.now() }, true);
    setStatus('等待同步');
  }, [accept]);
  const takeover = useCallback(() => update(s => ({ ...s, timer: { ...s.timer, device } })), [device, update]);
  return { state, ready, device, session, status, lastSyncedAt, conflict, storageError, update, resolve, takeover, retry: async () => { if (session) { await pull(session); await push(); } } };
}
