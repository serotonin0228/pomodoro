import React, { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { ActivityIndicator, AppState, NativeModules, Platform, View } from 'react-native';
import { LocalizedText as Text, LocalizedTextInput as TextInput } from './i18n';
import { Button, Group, Row, Segments, Toggle, ui, useTheme } from './ui';
import { elapsed, type Timer } from './model';
import { timeNow } from './time';

type GuardConfig = { enabled: boolean; mode: 'blacklist' | 'whitelist'; blocked: string[]; allowed: string[] };
type Status = { permission: boolean; connected: boolean; active: boolean; usage: boolean; sessionStatus: string;
  bootReadable: boolean; events: number; lastSeen: string; lastMatched: string; lastError: string; lastHome: boolean; returns: number };
type InstalledApp = { packageName: string; label: string };
type NativeGuard = {
  getConfig(): Promise<GuardConfig & Status>;
  configure(value: GuardConfig): Promise<GuardConfig & Status>;
  listApps(): Promise<InstalledApp[]>;
  updateSession(value: { id: string; status: string; phase: string; mode: string; remainingMs: number }): Promise<GuardConfig & Status>;
  openPermission(): Promise<void>;
  openUsagePermission(): Promise<void>;
};
const native: NativeGuard | undefined = Platform.OS === 'android' ? NativeModules.FocusGuard : undefined;
const initial: GuardConfig = { enabled: false, mode: 'blacklist', blocked: [], allowed: [] };
export function useFocusGuard(timer: Timer, owner: boolean, ready: boolean) {
  const [config, setConfig] = useState(initial);
  const configRef = useRef(config);
  const loaded = useRef(false);
  const [available, setAvailable] = useState(false);
  const [status, setStatus] = useState<Status>({ permission: false, connected: false, active: false, usage: false, sessionStatus: 'idle', bootReadable: true, events: 0, lastSeen: '', lastMatched: '', lastError: '', lastHome: false, returns: 0 });
  const [error, setError] = useState('');
  const pending = useRef(Promise.resolve());
  const refresh = useCallback(async () => {
    if (!native) return;
    try {
      const value = await native.getConfig();
      if (!loaded.current) { loaded.current = true; configRef.current = value; setConfig(value); setAvailable(true); }
      setStatus(value);
    } catch { setError('无法读取应用限制'); }
  }, []);
  useEffect(() => {
    if (!native) return;
    void refresh();
    const sub = AppState.addEventListener('change', state => { if (state === 'active') void refresh(); });
    const poll = setInterval(() => { if (AppState.currentState === 'active') void refresh(); }, 2500);
    return () => { sub.remove(); clearInterval(poll); };
  }, [refresh]);
  const save = useCallback((change: Partial<GuardConfig>) => {
    if (!native || !loaded.current) return;
    const value = { ...configRef.current, ...change };
    configRef.current = value; setConfig(value); setError('');
    pending.current = pending.current.then(async () => {
      const result = await native.configure(value);
      setStatus(result);
    }).catch(() => { setError('保存应用限制失败'); });
  }, []);
  const latest = useRef({ timer, owner, ready, available });
  useLayoutEffect(() => { latest.current = { timer, owner, ready, available }; }, [timer, owner, ready, available]);
  const syncSession = useCallback(() => {
    const { timer: current, owner: controls, ready: hydrated, available: loaded } = latest.current;
    if (!native || !hydrated || !loaded) return;
    void native.updateSession({ id: current.id, status: controls ? current.status : 'idle', phase: current.phase, mode: current.mode,
      remainingMs: Math.max(0, (current.duration - elapsed(current, timeNow())) * 1000) })
      .then(value => setStatus(value)).catch(() => setError('应用限制未生效'));
  }, []);
  useLayoutEffect(syncSession, [syncSession, timer, owner, ready, available]);
  useEffect(() => {
    if (!native) return;
    const sub = AppState.addEventListener('change', syncSession);
    return () => sub.remove();
  }, [syncSession]);
  const expectedActive = owner && timer.status === 'running' && timer.phase === 'focus' && (timer.mode !== 'countdown' || elapsed(timer, timeNow()) < timer.duration);
  return { config, status, available, error, save, refresh, expectedActive };
}
export type FocusGuard = ReturnType<typeof useFocusGuard>;
export function FocusGuardPanel({ guard }: { guard: FocusGuard }) {
  const { c } = useTheme();
  const { config, status, available, error, save } = guard;
  const [apps, setApps] = useState<InstalledApp[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [query, setQuery] = useState('');
  useEffect(() => {
    let live = true;
    if (!native) { setLoading(false); return; }
    void native.listApps().then(value => { if (live) setApps(value); }).catch(() => { if (live) setLoadError('无法读取应用列表'); }).finally(() => { if (live) setLoading(false); });
    void guard.refresh();
    return () => { live = false; };
  }, [guard.refresh]);
  if (!native) return <Text style={{ color: c.secondary }}>仅支持 Android</Text>;
  if (!available) return <>{error ? <Text accessibilityRole="alert" style={{ color: '#D73535' }}>{error}</Text> : <ActivityIndicator color={c.blue}/>}</>;
  const key = config.mode === 'blacklist' ? 'blocked' : 'allowed';
  const selected = config[key];
  const filtered = apps.filter(app => `${app.label} ${app.packageName}`.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase()));
  const limitStatus = !config.enabled ? '已关闭' : !status.permission || !status.connected ? '待授权' : status.active ? '限制中' : guard.expectedActive ? '计时未同步' : '等待专注';
  return <>
    <Group><Toggle label="专注时限制其他应用" value={config.enabled} onChange={enabled => save({ enabled })}/><Row label="辅助功能权限" value={status.permission && status.connected ? '已开启' : '未开启'}/><Row label="限制状态" value={limitStatus}/><Row label="应用使用情况权限" value={status.usage ? '已开启' : '未开启'} onPress={() => { void native.openUsagePermission().catch(() => setLoadError('无法打开系统设置')); }} last/></Group>
    {config.enabled && (!status.permission || !status.connected) && <View style={{ marginBottom: 24 }}><Button label="开启辅助功能" onPress={() => { void native.openPermission().catch(() => setLoadError('无法打开系统设置')); }}/></View>}
    {config.enabled && !status.usage && <View style={{ marginBottom: 20 }}><Button label="开启兼容检测" secondary onPress={() => { void native.openUsagePermission().catch(() => setLoadError('无法打开系统设置')); }}/></View>}
    <View style={{ marginBottom: 20 }}><Segments options={['黑名单', '白名单']} value={config.mode === 'blacklist' ? '黑名单' : '白名单'} onChange={mode => save({ mode: mode === '黑名单' ? 'blacklist' : 'whitelist' })}/></View>
    <TextInput accessibilityLabel="搜索应用" placeholder="搜索应用" placeholderTextColor={c.secondary} value={query} onChangeText={setQuery} style={[ui.input, { backgroundColor: c.surface, color: c.text, marginBottom: 16 }]}/>
    {!!(error || loadError) && <Text accessibilityRole="alert" style={{ color: '#D73535', marginBottom: 12 }}>{error || loadError}</Text>}

    {loading ? <ActivityIndicator color={c.blue}/> : <Group title={config.mode === 'blacklist' ? '拦截的应用' : '允许的应用'}>{filtered.map((app, index) => <Toggle key={app.packageName} label={app.label} value={selected.includes(app.packageName)} onChange={checked => save({ [key]: checked ? [...selected, app.packageName] : selected.filter(pkg => pkg !== app.packageName) })} last={index === filtered.length - 1}/>)}{!filtered.length && <Row label="没有符合条件的应用" last/>}</Group>}
  </>;
}
