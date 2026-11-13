import { LocalizedText as Text, LocalizedPressable as Pressable, LocalizedTextInput as TextInput, LanguageContext, useAppLanguage, translate } from './src/i18n';
import React, { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { AccessibilityInfo, ActivityIndicator, Animated, AppState, Linking, Modal, NativeModules, PanResponder, Platform, ScrollView, StyleSheet, useColorScheme, useWindowDimensions, View } from 'react-native';
import { SafeAreaProvider, useSafeAreaInsets } from 'react-native-safe-area-context';
import { StatusBar } from 'expo-status-bar';
import { setBackgroundColorAsync } from 'expo-system-ui';
import { BlurTargetView, BlurView } from 'expo-blur';
import { setAudioModeAsync, useAudioPlayer, useAudioPlaylist, useAudioPlaylistStatus } from 'expo-audio';
import { Icon } from './src/Icon';
import { IOSMenu, MenuHost } from './src/IOSMenu';
import { useWorkspace } from './src/useWorkspace';
import { clock, displaySeconds, elapsed, finishState, FocusRecord, formatDuration, Mode, newTimer, pauseState, Settings } from './src/model';
import { cancelReminder, notificationPermission, notifyWeb, requestNotifications, scheduleReminder } from './src/notifications';
import { cloud } from './src/cloud';
import { exportRecords } from './src/export';
import { Button, dark, Group, IconButton, light, Row, Segments, Stepper, Theme, Toggle, iosCorners, ui } from './src/ui';
import { RecordRow, Stats } from './src/Stats';
import { timeNow } from './src/time';
import { authErrorMessage } from './src/auth';
import { useCloudMusic } from './src/useCloudMusic';
import { musicKey } from './src/musicCatalog';
import { replaceMusicQueue } from './src/nativePlaylist';
import { FocusGuardPanel, useFocusGuard } from './src/FocusGuard';

type Sheet = 'settings' | 'guard' | 'music' | 'duration' | 'cloud' | 'history' | 'goal' | null;
type Dialog = { title: string; body: string; action?: string; danger?: boolean; onConfirm?: () => void; discard?: () => void } | null;
const titles: Record<NonNullable<Sheet>, string> = { settings: '设置', guard: '应用限制', music: '背景音乐', duration: '计时', cloud: '云同步', history: '全部专注记录', goal: '统计目标' };
export default function App() { return <SafeAreaProvider><Workspace /></SafeAreaProvider>; }
function Workspace() {
  const ws = useWorkspace();
  const { state, update, device, ready, readState } = ws;
  const { timer, settings, records } = state;
  const { language, setLanguage } = useAppLanguage();
  const locale = language === 'en' ? 'en-US' : 'zh-CN';
  const t = (text: string) => translate(text, language);
  const systemTheme = useColorScheme();
  const isDark = settings.theme === 'dark' || (settings.theme === 'system' && systemTheme === 'dark');
  const c = isDark ? dark : light;
  useEffect(() => { if (!ready) return; void setBackgroundColorAsync(c.bg).catch(() => {}); if (Platform.OS === 'android') NativeModules.FocusStatus?.setLaunchTheme(settings.theme); }, [c.bg, ready, settings.theme]);
  const { width, height } = useWindowDimensions();
  const wide = width >= 850;
  const insets = useSafeAreaInsets();
  const blurTarget = useRef<React.ComponentRef<typeof View>>(null);
  const [tab, setTab] = useState<'timer' | 'stats'>('timer');
  const [sheet, setSheetState] = useState<Sheet>(null);
  const dismissMenu = useRef<(() => boolean) | null>(null);
  const sheetY = useRef(new Animated.Value(height)).current;
  const setSheet = useCallback((value: Sheet) => { if (value) { sheetY.setValue(height); setSheetState(value); Animated.spring(sheetY, { toValue: 0, useNativeDriver: true, damping: 27, stiffness: 240 }).start(); } else { Animated.timing(sheetY, { toValue: height, duration: 240, useNativeDriver: true }).start(({ finished }) => { if (finished) setSheetState(null); }); } }, [height, sheetY]);
  const sheetScrollY = useRef(0);
  const sheetDrag = useRef({ lastDy: 0, direction: 0 });
  const sheetGesture = useMemo(() => PanResponder.create({ onStartShouldSetPanResponder: () => true, onMoveShouldSetPanResponderCapture: (_, g) => sheetScrollY.current <= 0 && g.dy > 12 && Math.abs(g.dy) > Math.abs(g.dx), onMoveShouldSetPanResponder: (_, g) => g.dy > 8 && Math.abs(g.dy) > Math.abs(g.dx), onPanResponderGrant: () => { sheetDrag.current = { lastDy: 0, direction: 0 }; }, onPanResponderMove: (_, g) => { const delta = g.dy - sheetDrag.current.lastDy; if (Math.abs(delta) > .5) sheetDrag.current.direction = Math.sign(delta); sheetDrag.current.lastDy = g.dy; sheetY.setValue(Math.max(0, g.dy)); }, onPanResponderRelease: (_, g) => { if (sheetDrag.current.direction >= 0 && (g.dy > 70 || g.vy > .6)) setSheet(null); else Animated.spring(sheetY, { toValue: 0, useNativeDriver: true }).start(); } }), [sheetY, setSheet]);
  const [dialog, setDialog] = useState<Dialog>(null);
  const [toast, setToast] = useState('');
  useEffect(() => { if (!toast) return; const id = setTimeout(() => setToast(''), 2400); return () => clearTimeout(id); }, [toast]);
  const [now, setNow] = useState(timeNow());
  const recoveryShown = useRef(false);
  useEffect(() => { if (!ready || recoveryShown.current) return; recoveryShown.current = true; if (timer.status === 'running' || timer.status === 'paused') setToast(t('已恢复上次计时')); }, [ready, timer.status]);
  const music = useCloudMusic(ws.session?.user.id ?? null);
  const tracks = useMemo(() => music.tracks.filter(item => item.user_id === ws.session?.user.id).map(item => ({ ...item, name: item.title, source: music.source(item) })), [music.tracks, music.sources, ws.session?.user.id]);
  const selectedIndex = tracks.findIndex(item => item.id === settings.musicTrackId);
  const savedTrack = selectedIndex >= 0 ? selectedIndex : Math.max(0, Math.min(tracks.length - 1, settings.musicTrack || 0));
  // Queue order is chosen up front so native playback never waits for JavaScript at a track boundary.
  const queueOrder = useMemo(() => {
    const order = tracks.map((item, index) => item.source ? index : -1).filter(index => index >= 0);
    if (settings.musicMode === 'shuffle') {
      for (let i = order.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [order[i], order[j]] = [order[j], order[i]];
      }
    }
    return order;
  }, [settings.musicMode, tracks]);
  const queueKey = JSON.stringify(queueOrder.map(index => [tracks[index].id, tracks[index].source]));
  const selectionRequest = useRef(0);
  const setTrack = (value: number) => {
    const item = tracks[value];
    if (!item) return;
    const request = ++selectionRequest.current;
    update(s => ({ ...s, settings: { ...s.settings, musicTrack: value, musicTrackId: item.id } }));
    setMusicOverride('play');
    if (item.source) retryQueue(value => value + 1);
    if (!item.source) void music.ensureDownloaded(item).catch(() => { if (selectionRequest.current === request) setMusicOverride('pause'); });
  };
  const [musicOverride, setMusicOverride] = useState<'play' | 'pause' | null>(null);
  const musicEnabled = settings.musicEnabled;
  const wantsMusic = musicOverride === 'play' || (musicOverride !== 'pause' && musicEnabled && timer.status === 'running' && (timer.phase === 'focus' || settings.musicDuringBreak));
  const setMusicEnabled = (value: boolean) => update(s => ({ ...s, settings: { ...s.settings, musicEnabled: value } }));
  const [musicError, setMusicError] = useState('');
  const [permission, setPermission] = useState('');
  const [email, setEmail] = useState('');
  const [authMode, setAuthMode] = useState<'signin' | 'signup'>('signin');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [needsConfirmation, setNeedsConfirmation] = useState(false);
  const [authBusy, setAuthBusy] = useState(false);
  const [authError, setAuthError] = useState('');
  const authAttempt = useRef(0);
  const authLock = useRef(false);
  const [durationDraft, setDurationDraft] = useState('25');
  const [historyQuery, setHistoryQuery] = useState('');
  const [historyLimit, setHistoryLimit] = useState(50);
  const [historyMode, setHistoryMode] = useState<'全部' | '倒计时' | '正计时'>('全部');
  const [reduceMotion, setReduceMotion] = useState(false);
  const fade = useRef(new Animated.Value(1)).current;
  const pageFade = useRef(new Animated.Value(1)).current;
  useEffect(() => { if (reduceMotion) { pageFade.setValue(1); return; } pageFade.setValue(0); Animated.timing(pageFade, { toValue: 1, duration: 220, useNativeDriver: true }).start(); }, [tab, reduceMotion, pageFade]);
  // Keep one native player alive across mode switches and catalog/cache updates.
  const player = useAudioPlaylist({ sources: [], loop: 'all' });
  const [queueAttempt, retryQueue] = useState(0);
  const latestPlayer = useRef(player);
  useLayoutEffect(() => { latestPlayer.current = player; }, [player]);
  const buildingQueue = useRef(false);
  const builtQueue = useRef('');
  const queuedSources = useRef<{ id: string; source: string }[]>([]);
  useLayoutEffect(() => {
    if (builtQueue.current === queueKey) return;
    const previous = queuedSources.current[player.currentIndex];
    const previousTime = player.currentTime;
    const latest = readState().settings;
    const desired = tracks.findIndex(item => item.id === latest.musicTrackId);
    const value = desired >= 0 ? desired : Math.max(0, Math.min(tracks.length - 1, latest.musicTrack || 0));
    const target = Math.max(0, queueOrder.indexOf(value));
    const sources = queueOrder.map(index => ({ id: tracks[index].id, source: tracks[index].source! }));
    buildingQueue.current = true;
    try {
      replaceMusicQueue(player, sources, latest.musicMode === 'single', target);
      if (sources[target]?.id === previous?.id && sources[target]?.source === previous?.source && previousTime > 0) void player.seekTo(previousTime).catch(() => {});
      queuedSources.current = sources;
      builtQueue.current = queueKey;
    } catch {
      builtQueue.current = '';
      setMusicError('播放失败');
      setMusicOverride('pause');
      try { player.pause(); } catch { /* Keep queue failures out of React's render lifecycle. */ }
    } finally { buildingQueue.current = false; }
  }, [player, queueKey, queueOrder, tracks, readState, queueAttempt]);
  const audio = useAudioPlaylistStatus(player);
  const track = queueOrder[audio.currentIndex] ?? savedTrack;
  const currentTrack = tracks[track];
  const [audioModeReady, setAudioModeReady] = useState(false);
  const chime = useAudioPlayer(require('./assets/audio/chime.m4a'));
  const owner = timer.device === device || timer.status === 'idle';
  const active = timer.status === 'running' || timer.status === 'paused';
  const liveRecords = records.filter(r => !r.deleted);
  const [nativeReady, setNativeReady] = useState(Platform.OS !== 'android');
  const focusGuard = useFocusGuard(timer, owner, ready);
  useEffect(() => {
    if (!ready || Platform.OS !== 'android' || !NativeModules.FocusStatus) return;
    let busy = false;
    const drain = async () => { if (busy) return; busy = true; try { const actions = JSON.parse(await NativeModules.FocusStatus.drain()) as { status: string; at: number; id: string }[]; for (const action of actions) update(s => { if (s.timer.id !== action.id || !['running', 'paused'].includes(s.timer.status)) return s; if (action.status === 'paused') return pauseState(s, action.at); return { ...s, timer: { ...s.timer, status: 'running', startedAt: action.at } }; }); setNativeReady(true); } finally { busy = false; } };
    void drain(); const t = setInterval(() => void drain(), 700); return () => clearInterval(t);
  }, [ready, update]);
  useEffect(() => { if (!ready || !nativeReady || Platform.OS !== 'android' || !NativeModules.FocusStatus) return; void NativeModules.FocusStatus.update(owner && settings.notifications && permission === 'granted' ? { ...timer, sound: settings.sound, language } : { ...timer, status: 'idle' }).catch(() => setPermission('error')); }, [ready, nativeReady, timer, owner, settings.notifications, settings.sound, permission, language]);

  useEffect(() => {
    if (sheet === 'cloud') return;
    authAttempt.current++; authLock.current = false;
    setPassword(''); setConfirmPassword(''); setShowPassword(false);
    setAuthBusy(false); setAuthError(''); setNeedsConfirmation(false);
  }, [sheet]);
  useEffect(() => { void AccessibilityInfo.isReduceMotionEnabled().then(setReduceMotion); const sub = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduceMotion); return () => sub.remove(); }, []);
  useEffect(() => { const t = setInterval(() => { if (AppState.currentState === 'active') setNow(timeNow()); }, timer.status === 'running' ? 1000 : 60000); const sub = AppState.addEventListener('change', v => { if (v === 'active') { setNow(timeNow()); void notificationPermission().then(setPermission); } }); return () => { clearInterval(t); sub.remove(); }; }, [timer.status]);
  useEffect(() => { void notificationPermission().then(setPermission).catch(() => setPermission('unsupported')); }, []);
  useEffect(() => {
    let live = true;
    void setAudioModeAsync({ playsInSilentMode: true, shouldPlayInBackground: true, interruptionMode: 'doNotMix' })
      .then(() => { if (live) setAudioModeReady(true); })
      .catch(() => { if (live) setMusicError('播放失败'); });
    return () => { live = false; };
  }, []);
  useEffect(() => { try { player.loop = readState().settings.musicMode === 'single' ? 'single' : 'all'; } catch { setMusicError('播放失败'); } }, [player, settings.musicMode, readState]);
  useEffect(() => {
    const item = tracks[savedTrack];
    if (!wantsMusic || !music.ready || !item || item.source) return;
    void music.ensureDownloaded(item).catch(() => setMusicOverride('pause'));
  }, [wantsMusic, music.ready, savedTrack, tracks, music.ensureDownloaded]);
  useEffect(() => { if (sheet === 'music' && music.ready) void music.refresh(); }, [sheet, music.ready, music.refresh]);
  useEffect(() => {
    const changed = player.addListener('trackChanged', ({ currentIndex }) => {
      // Android can deliver old events together when JavaScript resumes.
      if (buildingQueue.current || builtQueue.current !== queueKey || latestPlayer.current !== player || player.currentIndex !== currentIndex) return;
      const requested = tracks.find(item => item.id === readState().settings.musicTrackId);
      if (requested && !requested.source) return;
      const value = queueOrder[currentIndex];
      if (value !== undefined && readState().settings.musicTrackId !== tracks[value].id) update(s => ({ ...s, settings: { ...s.settings, musicTrack: value, musicTrackId: tracks[value].id } }));
    });
    const status = player.addListener('playlistStatusUpdate', value => {
      if ('error' in value && value.error) { setMusicError('播放失败'); setMusicOverride('pause'); }
    });
    return () => { changed.remove(); status.remove(); };
  }, [player, queueKey, queueOrder, tracks, readState, update]);
  useEffect(() => {
    if (!ready) return;
    const latest = readState().settings;
    const desired = tracks.findIndex(item => item.id === latest.musicTrackId);
    const value = desired >= 0 ? desired : Math.max(0, Math.min(tracks.length - 1, latest.musicTrack || 0));
    const index = queueOrder.indexOf(value);
    try { if (builtQueue.current === queueKey && index >= 0 && player.currentIndex !== index) player.skipTo(index); } catch { setMusicError('播放失败'); setMusicOverride('pause'); }
  }, [ready, player, queueKey, queueOrder, savedTrack, readState, tracks]);
  useEffect(() => {
    try {
      if (!audioModeReady || !ready || !queueOrder.length || builtQueue.current !== queueKey) { player.pause(); if (Platform.OS !== 'web') player.setActiveForLockScreen(false); return; }
      // All playback operations are immediate native commands. A JS volume ramp can freeze mid-ramp in the background.
      player.volume = 1;
      if (wantsMusic) {
        if (Platform.OS !== 'web') player.setActiveForLockScreen(true, { title: 'Pomodoro' });
        player.play();
      } else {
        player.pause();
        if (Platform.OS !== 'web') player.setActiveForLockScreen(false);
      }
      setMusicError('');
    } catch { setMusicError('播放失败'); setMusicOverride('pause'); }
  }, [player, wantsMusic, audioModeReady, ready, queueKey, queueOrder.length]);
  useEffect(() => { if (musicError) setToast(t('播放失败')); }, [musicError]);
  useEffect(() => { if (music.error) setToast(t('下载失败')); }, [music.error]);
  useEffect(() => () => { try { player.pause(); player.clearLockScreenControls(); if (Platform.OS === 'web') player.destroy(); } catch { /* The released playlist has already detached. */ } }, [player]);
  useEffect(() => {
    if (!ready) return;
    if (settings.notifications && owner && permission === 'granted') void scheduleReminder(timer, settings.sound, language).catch(() => setPermission('error'));
    else void cancelReminder().catch(() => {});
  }, [ready, timer.id, timer.status, timer.startedAt, timer.device, settings.notifications, settings.sound, owner, permission, language]);
  useEffect(() => {
    if (!ready || !owner || timer.mode !== 'countdown' || timer.status !== 'running' || elapsed(timer, now) < timer.duration) return;
    update(s => finishState(s, timeNow(), true));
    if (settings.sound) void chime.seekTo(0).then(() => chime.play()).catch(() => {});
    if (settings.notifications) notifyWeb(timer.phase, language);
  }, [now, ready, owner, timer, settings.sound, settings.notifications, update, chime, language]);
  useEffect(() => {
    if (timer.status !== 'completed' || !owner || !(timer.phase === 'focus' ? settings.autoBreak : settings.autoFocus)) return;
    const t = setTimeout(() => nextPhase(timer.phase === 'focus' ? 'break' : 'focus', true), 1200);
    return () => clearTimeout(t);
  }, [timer.status, timer.phase, owner, settings.autoBreak, settings.autoFocus]);
  useEffect(() => { if (Platform.OS === 'web') document.title = timer.status === 'running' ? `${clock(displaySeconds(timer, now))} · Pomodoro` : 'Pomodoro'; }, [timer.status, Math.floor(now / 1000)]);
  useEffect(() => {
    if (Platform.OS !== 'web') return;
    const keydown = (e: KeyboardEvent) => { if (e.code === 'Space' && !sheet && !dialog && !ws.conflict && !['INPUT', 'TEXTAREA', 'BUTTON'].includes((e.target as HTMLElement).tagName)) { e.preventDefault(); primary(); } };
    window.addEventListener('keydown', keydown); return () => window.removeEventListener('keydown', keydown);
  });
  function animateChange(fn: () => void) {
    // Commit immediately: a visual transition must never overwrite a fast Start tap.
    fn();
    if (reduceMotion) return;
    Animated.sequence([Animated.timing(fade, { toValue: .92, duration: 90, useNativeDriver: true }), Animated.timing(fade, { toValue: 1, duration: 180, useNativeDriver: true })]).start();
  }
  function changeMode(mode: Mode) {
    if (mode === timer.mode) return;
    if (!owner && active) { setSheet('cloud'); return; }
    const run = () => animateChange(() => update(s => ({ ...finishState(s), timer: newTimer(device, s.settings, mode), segments: [] })));
    if (active) setDialog({ title: '切换计时模式？', body: '当前专注会结束，已经完成的专注时间将保存到统计。', action: '保存并切换', onConfirm: run }); else run();
  }
  function nextPhase(phase: 'focus' | 'break', start = false) {
    setMusicOverride(null);
    animateChange(() => update(s => { const t = newTimer(device, s.settings, phase === 'break' ? 'countdown' : s.timer.mode, phase, s.round); return { ...s, timer: start ? { ...t, status: 'running', startedAt: timeNow(), createdAt: timeNow() } : t, segments: [] }; }));
  }
  function primary() {
    if (!active && settings.notifications && permission !== 'granted') void enableNotifications(true);
    if (!owner && active) { setSheet('cloud'); return; }
    if (timer.status === 'completed') { nextPhase(timer.phase === 'focus' ? 'break' : 'focus', true); return; }
    if (timer.status === 'running') { setMusicOverride(null); update(s => pauseState(s)); }
    else { setMusicOverride(null); const started = timeNow(); update(s => ({ ...s, timer: { ...s.timer, status: 'running', startedAt: started, createdAt: s.timer.status === 'idle' ? started : s.timer.createdAt, device } })); }
  }
  function end() { setDialog({ title: timer.phase === 'focus' ? '结束本次专注？' : '结束休息？', body: timer.phase === 'focus' ? '实际专注时间会保存，暂停时间不会计入。' : '休息时间不会计入专注统计。', action: timer.phase === 'focus' ? '结束并保存' : '结束休息', discard: timer.phase === 'focus' && elapsed(timer, timeNow()) <= 300 ? () => update(s => ({ ...s, timer: newTimer(device, s.settings, s.timer.mode), segments: [] })) : undefined, onConfirm: () => update(s => ({ ...finishState(s), timer: newTimer(device, s.settings, s.timer.mode), segments: [] })) }); }
  function setSetting<K extends keyof Settings>(key: K, value: Settings[K]) {
    update(s => { const next = { ...s.settings, [key]: value }; return { ...s, settings: next, timer: s.timer.status === 'idle' && ['focusMinutes', 'breakMinutes', 'longBreakMinutes', 'rounds'].includes(key) ? newTimer(device, next, s.timer.mode, s.timer.phase, s.round) : s.timer }; });
  }
  async function enableNotifications(value: boolean) {
    if (!value) { setSetting('notifications', false); return; }
    try {
      const granted = await requestNotifications(language); setPermission(granted ? 'granted' : 'denied'); setSetting('notifications', granted);
      if (!granted) setDialog({ title: '通知权限未开启', body: Platform.OS === 'web' ? '请在浏览器的网站设置中允许通知。网页关闭后的可靠到时提醒需要使用原生 App。' : '请在系统设置中允许Pomodoro发送通知。', action: Platform.OS === 'web' ? undefined : '打开系统设置', onConfirm: Platform.OS === 'web' ? undefined : () => { void Linking.openSettings(); } });
    } catch { setDialog({ title: '无法开启通知', body: '当前环境不支持通知。请使用原生安装包，或在支持通知的浏览器中打开安全地址。' }); }
  }
  async function signIn() {
    if (!cloud || authLock.current) return;
    if (!/^\S+@\S+\.\S+$/.test(email.trim())) { setAuthError('请输入有效的邮箱地址'); return; }
    if (!password) { setAuthError('请输入密码'); return; }
    if (authMode === 'signup' && password.length < 8) { setAuthError('密码至少需要 8 位'); return; }
    if (authMode === 'signup' && password !== confirmPassword) { setAuthError('两次输入的密码不一致'); return; }
    const attempt = ++authAttempt.current;
    authLock.current = true; setAuthBusy(true); setAuthError(''); setNeedsConfirmation(false);
    try {
      const { data, error } = authMode === 'signup'
        ? await cloud.auth.signUp({ email: email.trim(), password })
        : await cloud.auth.signInWithPassword({ email: email.trim(), password });
      if (attempt !== authAttempt.current) return;
      if (error) {
        setAuthError(authErrorMessage(error));
        setNeedsConfirmation(error.code === 'email_not_confirmed');
      } else if (data.session) {
        setPassword(''); setConfirmPassword(''); setShowPassword(false); setSheet(null);
      } else {
        setPassword(''); setConfirmPassword(''); setShowPassword(false); setAuthMode('signin'); setNeedsConfirmation(true);
        setDialog({ title: '请确认邮箱', body: `请打开 ${email.trim()} 收到的确认邮件，点击确认链接，然后返回这里使用密码登录。`, action: '返回登录' });
      }
    } catch { if (attempt === authAttempt.current) setAuthError('连接失败，请检查网络后重试'); }
    finally { if (attempt === authAttempt.current) { authLock.current = false; setAuthBusy(false); } }
  }
  async function resendConfirmation() {
    if (!cloud || authLock.current) return;
    const attempt = ++authAttempt.current;
    authLock.current = true; setAuthBusy(true); setAuthError('');
    try {
      const { error } = await cloud.auth.resend({ type: 'signup', email: email.trim() });
      if (attempt !== authAttempt.current) return;
      if (error) setAuthError(authErrorMessage(error));
      else setDialog({ title: '确认邮件已发送', body: '点击邮件中的确认链接后，返回Pomodoro登录。' });
    } catch { if (attempt === authAttempt.current) setAuthError('连接失败，请检查网络后重试'); }
    finally { if (attempt === authAttempt.current) { authLock.current = false; setAuthBusy(false); } }
  }
  async function doExport() { try { await exportRecords(records, language); } catch (e) { setDialog({ title: '导出失败', body: (e as Error).message }); } }
  function showRecord(r: FocusRecord) { setDialog({ title: `${r.mode === 'countdown' ? '倒计时' : '正计时'} · ${formatDuration(r.seconds)}`, body: `${new Date(r.start).toLocaleString(locale)} ${t('至')} ${new Date(r.end).toLocaleString(locale)}\n${t('仅统计有效专注时间。删除后同步到其他设备。')}`, action: '删除记录', danger: true, onConfirm: () => { update(s => ({ ...s, records: s.records.map(x => x.id === r.id ? { ...x, deleted: true } : x) })); setToast(t('记录已删除')); } }); }
  const buttonLabel = !owner && active ? '在此设备接管' : timer.status === 'running' ? '暂停' : timer.status === 'paused' ? '继续专注' : timer.status === 'completed' ? timer.phase === 'focus' ? '开始休息' : '开始专注' : timer.phase === 'focus' ? '开始专注' : '开始休息';
  function renderTimer() {
    const timeText = clock(displaySeconds(timer, now));
    const units = [...timeText].reduce((total, digit) => total + (digit === ':' ? .34 : .7), 0);
    const timeFont = Math.min(wide ? 128 : 108, (Math.min(width - 48, 560) - 24) / units);
    return <View style={[s.timerPage, { minHeight: wide ? Math.max(520, height - 205) : Math.max(480, height - Math.max(insets.top, 14) - Math.max(insets.bottom, 12) - 124) }]}>
      <View style={{ width: '100%', maxWidth: 320, alignSelf: 'center' }}><Segments options={['倒计时', '正计时']} value={timer.mode === 'countdown' ? '倒计时' : '正计时'} onChange={v => changeMode(v === '倒计时' ? 'countdown' : 'stopwatch')}/></View>
      <Animated.View style={[s.clockArea, { opacity: fade, paddingTop: wide ? 96 : Math.max(100, height * .18), paddingBottom: wide ? 72 : 54 }]}>
        <View testID="timer-digits" accessibilityLabel={clock(displaySeconds(timer, now))} style={{ flexDirection: 'row', justifyContent: 'center', width: Math.min(width - 48, 560), paddingHorizontal: 8 }}>{timeText.split('').map((digit, i) => <Text key={i} maxFontSizeMultiplier={1} style={[s.digits, { color: c.text, textAlign: 'center', width: (digit === ':' ? .34 : .7) * timeFont, fontSize: timeFont, lineHeight: Math.ceil(timeFont * 1.2), paddingHorizontal: 0 }]}>{digit}</Text>)}</View>
        {<View style={{ flexDirection: 'row', gap: 5, marginTop: 22, alignItems: 'center', minHeight: 15, opacity: timer.mode === 'countdown' ? 1 : 0 }}>{Array.from({ length: settings.rounds }, (_, i) => <View key={i} style={{ width: 5, height: 5, borderRadius: 3, backgroundColor: i < state.round % settings.rounds ? c.blue : c.muted, opacity: i < state.round % settings.rounds ? 1 : .5 }}/>)}</View>}
      </Animated.View>
      <View style={{ width: 220, alignSelf: 'center', minHeight: 104 }}><Button label={buttonLabel} onPress={primary} icon={timer.status === 'running' && owner ? 'pause' : 'play'}/>{active && owner ? <Pressable accessibilityRole="button" onPress={end} style={ui.textButton}><Text style={{ color: c.secondary, fontSize: 14 }}>结束专注</Text></Pressable> : timer.status === 'completed' ? <Pressable accessibilityRole="button" onPress={() => nextPhase('focus', true)} style={ui.textButton}><Text style={{ color: c.blue, fontSize: 14 }}>返回专注</Text></Pressable> : null}</View>
      <View style={{ flex: 1, minHeight: 30 }}/>
      <Pressable accessibilityRole="button" accessibilityLabel="打开背景音乐" onPress={() => setSheet('music')} style={({ pressed }) => [s.musicBar, { backgroundColor: c.surface, opacity: pressed ? .7 : 1 }]}><View style={s.musicTile}><Icon name={audio.playing ? 'wave' : 'music'} color={c.blue} size={22}/></View><View style={{ flex: 1 }}><Text style={{ color: c.text, fontSize: 16, fontWeight: '500' }}>{currentTrack?.name ?? '背景音乐'}</Text></View><Icon name="chevron" color={c.muted} size={16}/></Pressable>
    </View>;
  }
  function renderAccount() {
    return <>{ws.session ? <>
      <Group title="当前账号"><Row label={ws.session.user.email || '已登录'} last/></Group>
      <Group><Row label="退出登录" danger onPress={() => setDialog({ title: '退出登录？', danger: true, body: ws.status === '已同步' ? '云端记录会保留在你的账号中。' : '本机可能还有未同步的更改，退出后会保留在此账号的本机空间。', action: '退出登录', onConfirm: () => { void cloud?.auth.signOut(); setSheet(null); } })} last/></Group>
    </> : !cloud ? <View style={{ paddingVertical: 14 }}>
      <View style={{ width: 72, height: 72, ...iosCorners(18), alignItems: 'center', justifyContent: 'center', alignSelf: 'center' }}><Icon name="cloud" size={32} color={c.blue}/></View>
      <Text style={{ color: c.text, fontSize: 20, fontWeight: '600', textAlign: 'center', marginTop: 22 }}>同步服务尚未配置</Text>
    </View> : <>
      <View style={{ marginBottom: 24 }}><Segments options={['登录', '注册']} value={authMode === 'signin' ? '登录' : '注册'} onChange={value => {
        if (authLock.current) return;
        setAuthMode(value === '登录' ? 'signin' : 'signup'); setPassword(''); setConfirmPassword(''); setShowPassword(false); setAuthError(''); setNeedsConfirmation(false);
      }}/></View>
      <TextInput accessibilityLabel="邮箱地址" placeholder="邮箱地址" placeholderTextColor={c.secondary} autoCapitalize="none" autoCorrect={false} keyboardType="email-address" autoComplete="email" value={email} onChangeText={value => { setEmail(value); setNeedsConfirmation(false); setAuthError(''); }} editable={!authBusy} style={[ui.input, { backgroundColor: c.surface, color: c.text }]}/>
      <View style={{ marginTop: 12, ...iosCorners(18), backgroundColor: c.surface, flexDirection: 'row', alignItems: 'center' }}>
        <TextInput accessibilityLabel="密码" placeholder={authMode === 'signup' ? '设置密码（至少 8 位）' : '密码'} placeholderTextColor={c.secondary} autoCapitalize="none" autoCorrect={false} secureTextEntry={!showPassword} autoComplete={authMode === 'signup' ? 'new-password' : 'current-password'} textContentType={authMode === 'signup' ? 'newPassword' : 'password'} value={password} onChangeText={setPassword} editable={!authBusy} onSubmitEditing={() => { if (authMode === 'signin') void signIn(); }} returnKeyType={authMode === 'signup' ? 'next' : 'go'} style={[ui.input, { flex: 1, minWidth: 0, color: c.text }]}/>
        <Pressable accessibilityRole="button" accessibilityLabel={showPassword ? '隐藏密码' : '显示密码'} accessibilityState={{ expanded: showPassword }} disabled={authBusy} onPress={() => setShowPassword(v => !v)} style={{ minWidth: 60, minHeight: 55, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 12 }}><Text style={{ color: c.blue, fontSize: 14 }}>{showPassword ? '隐藏' : '显示'}</Text></Pressable>
      </View>
      {authMode === 'signup' && <TextInput accessibilityLabel="确认密码" placeholder="再次输入密码" placeholderTextColor={c.secondary} autoCapitalize="none" autoCorrect={false} secureTextEntry={!showPassword} autoComplete="new-password" textContentType="newPassword" value={confirmPassword} onChangeText={setConfirmPassword} editable={!authBusy} onSubmitEditing={() => void signIn()} returnKeyType="go" style={[ui.input, { backgroundColor: c.surface, color: c.text, marginTop: 12 }]}/>}
      <View style={{ marginTop: 24 }}><Button label={authBusy ? '正在处理…' : authMode === 'signup' ? '创建账号' : '登录'} onPress={signIn} disabled={authBusy}/></View>
      {needsConfirmation && <Pressable accessibilityRole="button" disabled={authBusy} onPress={() => void resendConfirmation()} style={ui.textButton}><Text style={{ color: c.blue, fontSize: 15 }}>重新发送确认邮件</Text></Pressable>}
      {!!authError && <Text accessibilityRole="alert" style={{ color: '#D73535', fontSize: 16, lineHeight: 24, marginTop: 16 }}>{authError}</Text>}
    </>}</>;
  }
  function renderSheet() {
    if (sheet === 'settings') return <><Group><IOSMenu label="语言" value={language} options={[{ value: 'zh', label: '简体中文' }, { value: 'en', label: 'English' }]} onChange={setLanguage}/><Row label="云同步" onPress={() => setSheet('cloud')}/>{Platform.OS === 'android' && <Row label="应用限制" onPress={() => setSheet('guard')}/>}<Row label="番茄钟设置" onPress={() => { setDurationDraft(String(settings.focusMinutes)); setSheet('duration'); }} last/></Group></>;
    if (sheet === 'guard') return <FocusGuardPanel guard={focusGuard}/>;
    if (sheet === 'music') return <>
      <Group><Toggle label="专注时播放背景音乐" value={musicEnabled} onChange={v => { setMusicEnabled(v); setMusicOverride(null); }}/><IOSMenu label="播放模式" value={settings.musicMode} options={[{ value: 'single', label: '单曲循环' }, { value: 'sequence', label: '顺序播放' }, { value: 'shuffle', label: '随机播放' }]} onChange={v => setSetting('musicMode', v)}/><Toggle label="休息时继续播放" value={settings.musicDuringBreak} onChange={v => setSetting('musicDuringBreak', v)} last/></Group>
      {!ws.session ? <Button label="登录" onPress={() => setSheet('cloud')}/> : <>
        {!music.ready && !tracks.length && <ActivityIndicator color={c.blue} style={{ marginVertical: 24 }}/>}
        {!!tracks.length && <Group>{tracks.map((item, index) => {
          const downloading = music.progress[musicKey(item)];
          const playing = item.id === currentTrack?.id && audio.playing;
          const action = downloading !== undefined ? '下载中' : item.source ? (playing ? '暂停' : '播放') : '下载';
          return <View key={item.id} style={{ minHeight: 62, flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 16, borderBottomWidth: index === tracks.length - 1 ? 0 : StyleSheet.hairlineWidth, borderBottomColor: c.line }}><Text numberOfLines={2} style={{ flex: 1, color: playing ? c.blue : c.text, fontSize: 16 }}>{item.name}</Text><Pressable accessibilityRole="button" accessibilityLabel={`${t(action)} ${item.name}`} accessibilityState={{ selected: currentTrack?.id === item.id }} aria-selected={currentTrack?.id === item.id} disabled={downloading !== undefined} onPress={() => { if (playing) { selectionRequest.current++; setMusicOverride('pause'); } else setTrack(index); }} style={{ minWidth: 54, minHeight: 48, alignItems: 'center', justifyContent: 'center' }}>{downloading !== undefined ? <Text style={{ color: c.blue, fontSize: 12 }}>{Math.round(downloading * 100)}%</Text> : <Icon name={item.source ? (playing ? 'pause' : 'play') : 'download'} color={c.blue} size={22}/>}</Pressable></View>;
        })}</Group>}
      </>}
    </>;
    if (sheet === 'duration') return <>
      {<><View style={{ flexDirection: 'row', gap: 10 }}>{[15, 25, 45, 60].map(v => <Pressable key={v} accessibilityRole="button" onPress={() => setDurationDraft(String(v))} style={{ flex: 1, paddingVertical: 17, alignItems: 'center', ...iosCorners(999), backgroundColor: Number(durationDraft) === v ? c.blue : 'transparent' }}><Text style={{ color: Number(durationDraft) === v ? '#fff' : c.text, fontWeight: '600' }}>{v} 分钟</Text></Pressable>)}</View>
      <Text style={[ui.groupTitle, { color: c.secondary, marginTop: 28 }]}>自定义时长（分钟）</Text>
      <TextInput accessibilityLabel="自定义分钟数" value={durationDraft} onChangeText={setDurationDraft} keyboardType="number-pad" maxLength={3} style={[ui.input, { backgroundColor: c.surface, color: c.text, fontSize: 24, textAlign: 'center' }]}/>
      <View style={{ marginTop: 26 }}><Button label={active ? '应用到下次专注' : '设定时长'} disabled={!/^\d+$/.test(durationDraft) || +durationDraft < 1 || +durationDraft > 180} onPress={() => { const v = +durationDraft; setSetting('focusMinutes', v); setSheet(null); }}/></View></>}
      <View style={{ marginTop: 26 }}><Group title="休息与循环"><Stepper label="短休息" value={settings.breakMinutes} onChange={v => setSetting('breakMinutes', v)} max={60}/><Stepper label="长休息" value={settings.longBreakMinutes} onChange={v => setSetting('longBreakMinutes', v)} step={5} min={5} max={60}/><IOSMenu label="长休息间隔" value={settings.rounds} options={[2, 3, 4, 5, 6, 7, 8].map(value => ({ value, label: `每 ${value} 轮` }))} onChange={v => setSetting('rounds', v)}/><Toggle label="自动开始休息" value={settings.autoBreak} onChange={v => setSetting('autoBreak', v)}/><Toggle label="自动开始专注" value={settings.autoFocus} onChange={v => setSetting('autoFocus', v)} last/></Group><Group title="通知"><Toggle label="专注计时通知" value={settings.notifications} onChange={enableNotifications}/><Toggle label="结束提示音" value={settings.sound} onChange={v => setSetting('sound', v)} last/></Group></View>
    </>;
    if (sheet === 'goal') return <><Group><IOSMenu label="目标类型" value={settings.goalType} options={[{ value: 'dailyMinutes', label: '每日时长' }, { value: 'dailySessions', label: '每日次数' }, { value: 'weeklyMinutes', label: '每周时长' }, { value: 'none', label: '关闭目标' }]} onChange={v => setSetting('goalType', v)} last={settings.goalType === 'none'}/>{settings.goalType === 'dailyMinutes' && <Stepper label="每日目标" value={settings.dailyGoal} onChange={v => setSetting('dailyGoal', v)} min={15} max={720} step={15} last/>}{settings.goalType === 'dailySessions' && <Stepper label="每日次数" value={settings.dailySessions} onChange={v => setSetting('dailySessions', v)} unit="次" min={1} max={50} last/>}{settings.goalType === 'weeklyMinutes' && <Stepper label="每周时长" value={settings.weeklyMinutes} onChange={v => setSetting('weeklyMinutes', v)} min={15} max={5040} step={15} last/>}</Group><Group><Stepper label="连续专注门槛" value={settings.streakMinutes} onChange={v => setSetting('streakMinutes', v)} min={1} max={180} last/></Group></>;
    if (sheet === 'cloud') return <>
      <Group title="同步状态"><Row label={ws.status}/>{ws.session && <Row label="上次连接" value={ws.lastSyncedAt ? new Date(ws.lastSyncedAt).toLocaleString(locale) : t('尚无')}/>}{ws.session && <Row label={owner ? '由此设备控制' : '由其他设备控制'} value={timer.status === 'running' ? '正在计时' : timer.status === 'paused' ? '已暂停' : '尚未开始'} last/>}</Group>
      {ws.session && <>{!owner && active && <Button label="在此设备接管" onPress={() => setDialog({ title: '接管当前计时？', body: '计时保持连续，后续操作和到时提醒转移到此设备。离线设备上已安排的提醒可能无法及时取消。', action: '接管计时', onConfirm: () => { ws.takeover(); setSheet(null); } })}/>}<View style={{ marginBottom: 24 }}><Button label="立即同步" secondary onPress={() => { void ws.retry().catch(() => setDialog({ title: '同步失败', body: '请检查网络连接，记录仍保存在本机。' })); }}/></View></>}
      {renderAccount()}
    </>;
    if (sheet === 'history') { const filtered = liveRecords.filter(r => (historyMode === '全部' || (historyMode === '倒计时' ? r.mode === 'countdown' : r.mode === 'stopwatch')) && (!historyQuery.trim() || `${new Date(r.start).toLocaleDateString(locale)} ${new Date(r.start).toISOString().slice(0, 10)} ${formatDuration(r.seconds)}`.includes(historyQuery.trim()))); return <><TextInput accessibilityLabel="搜索日期或时长" placeholder="搜索日期或时长" placeholderTextColor={c.secondary} value={historyQuery} onChangeText={value => { setHistoryQuery(value); setHistoryLimit(50); }} style={[ui.input, { backgroundColor: c.surface, color: c.text, marginBottom: 12 }]}/><Segments options={['全部', '倒计时', '正计时']} value={historyMode} onChange={value => { setHistoryMode(value); setHistoryLimit(50); }}/>{filtered.length ? [...filtered].reverse().slice(0, historyLimit).map(r => <RecordRow key={r.id} record={r} onPress={() => showRecord(r)}/>) : <Text style={{ color: c.secondary, textAlign: 'center', padding: 30 }}>没有符合条件的记录</Text>}{filtered.length > historyLimit && <Button label="加载更多" secondary onPress={() => setHistoryLimit(n => n + 50)}/>}<Button label="导出 CSV" secondary onPress={doExport}/></>; }
    return null;
  }
  return <LanguageContext.Provider value={language}><Theme.Provider value={{ c, isDark, reduceMotion }}><View style={{ flex: 1, backgroundColor: c.bg }}>
    <StatusBar style={isDark ? 'light' : 'dark'}/>
    <BlurTargetView ref={blurTarget} style={{ flex: 1, backgroundColor: c.bg }}><View collapsable={false} pointerEvents="none" style={[StyleSheet.absoluteFill, { backgroundColor: c.bg }]}/><ScrollView style={{ flex: 1, backgroundColor: c.bg }} contentContainerStyle={{ flexGrow: 1, paddingHorizontal: wide ? 36 : 24, paddingTop: Math.max(insets.top, wide ? 22 : 14) + 60, paddingBottom: wide ? 28 : Math.max(insets.bottom, 12) + 70 }} showsVerticalScrollIndicator={false}>
      <Animated.View style={{ width: '100%', maxWidth: tab === 'timer' ? wide ? 680 : 420 : 1040, alignSelf: 'center', paddingTop: wide ? 35 : 5, opacity: pageFade, transform: [{ translateY: pageFade.interpolate({ inputRange: [0, 1], outputRange: [6, 0] }) }] }}>
        {wide && tab === 'stats' && <View style={[ui.between, { marginBottom: 27 }]}><View><Text style={{ color: c.text, fontSize: 25, fontWeight: '700', letterSpacing: -.7 }}>Statistics</Text></View><IconButton name="download" label="导出专注记录" onPress={doExport}/></View>}
        {!!ws.storageError && <Text accessibilityRole="alert" style={{ color: '#D73535', padding: 12, marginBottom: 12 }}>{ws.storageError}</Text>}
        {tab === 'timer' ? renderTimer() : <Stats onGoal={() => setSheet('goal')} records={records} settings={settings} wide={wide} now={now} onStart={() => setTab('timer')} onExport={doExport} onHistory={() => setSheet('history')} onRecord={showRecord}/>}
      </Animated.View>
    </ScrollView></BlurTargetView>
    <View style={[s.topbar, { position: 'absolute', zIndex: 2, top: 0, left: 0, right: 0, overflow: 'hidden', paddingTop: Math.max(insets.top, wide ? 22 : 14), borderBottomColor: c.line, borderBottomWidth: StyleSheet.hairlineWidth }]}><BlurView blurTarget={blurTarget} blurMethod="dimezisBlurViewSdk31Plus" intensity={65} tint={isDark ? 'dark' : 'default'} style={StyleSheet.absoluteFill}/><View style={[s.topbarInner, { maxWidth: 1040 }]}>
      {wide ? <View style={s.brand}><View style={s.brandIcon}><Icon name="timer" color={c.blue} size={23}/></View><View><Text style={{ color: c.text, fontWeight: '700', fontSize: 19 }}>Pomodoro</Text></View></View> : <Text style={[s.largeTitle, { color: c.text }]}>{tab === 'timer' ? 'Pomodoro' : 'Statistics'}</Text>}
      {wide && <View style={[s.desktopTabs, { backgroundColor: c.bg }]}>{(['timer', 'stats'] as const).map(v => <Pressable key={v} accessibilityRole="tab" accessibilityState={{ selected: tab === v }} onPress={() => setTab(v)} style={[s.desktopTab, { backgroundColor: tab === v ? c.surface : 'transparent' }]}><Icon name={v === 'timer' ? 'timer' : 'chart'} size={17} color={tab === v ? c.blue : c.secondary}/><Text style={{ color: tab === v ? c.blue : c.secondary, fontSize: 13, fontWeight: '600' }}>{v === 'timer' ? '计时' : '统计'}</Text></Pressable>)}</View>}
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}><Pressable accessibilityRole="button" accessibilityLabel="设置" onPress={() => setSheet('settings')} style={s.syncLink}><Icon name="gear" size={20} color={c.secondary}/></Pressable><IconButton name={isDark ? 'sun' : 'moon'} label={isDark ? '切换浅色模式' : '切换深色模式'} onPress={() => setSetting('theme', isDark ? 'light' : 'dark')}/></View>
    </View></View>
    {!wide && <View style={[s.tabbar, { position: 'absolute', bottom: 0, left: 0, right: 0, overflow: 'hidden', borderTopColor: c.line, paddingBottom: Math.max(insets.bottom, 12) }]}><BlurView blurTarget={blurTarget} blurMethod="dimezisBlurViewSdk31Plus" intensity={65} tint={isDark ? 'dark' : 'default'} style={StyleSheet.absoluteFill}/>{(['timer', 'stats'] as const).map(v => <Pressable key={v} accessibilityRole="tab" accessibilityLabel={v === 'timer' ? '计时' : '统计'} accessibilityState={{ selected: tab === v }} onPress={() => setTab(v)} style={s.tab}><Icon name={v === 'timer' ? 'timer' : 'chart'} color={tab === v ? c.blue : c.secondary} size={23}/><Text style={{ color: tab === v ? c.blue : c.secondary, fontSize: 10, fontWeight: '500', marginTop: 4 }}>{v === 'timer' ? '计时' : '统计'}</Text></Pressable>)}</View>}
    {!sheet && !dialog && !ws.conflict && !!toast && <View pointerEvents="none" accessibilityLiveRegion="polite" style={{ position: 'absolute', top: insets.top + 60, alignSelf: 'center', backgroundColor: c.surface, paddingHorizontal: 20, paddingVertical: 12, ...iosCorners(20), zIndex: 10 }}><Text style={{ color: c.text }}>{toast}</Text></View>}
    <Modal visible={!!sheet || !!dialog || !!ws.conflict} transparent animationType={reduceMotion ? 'none' : 'fade'} onRequestClose={() => { if (dismissMenu.current?.()) return; if (ws.conflict) return; if (dialog) setDialog(null); else setSheet(null); }} statusBarTranslucent><MenuHost dismissRef={dismissMenu}><View style={{ flex: 1 }}>{!!sheet && <View style={[s.modalLayer, { backgroundColor: c.overlay, justifyContent: wide ? 'center' : 'flex-end', padding: wide ? 24 : 0 }]}><Pressable accessibilityLabel="关闭面板" onPress={() => setSheet(null)} style={StyleSheet.absoluteFill}/><Animated.View accessibilityViewIsModal style={[s.sheet, { transform: [{ translateY: sheetY }] }, { backgroundColor: sheet === 'music' ? c.surface : c.bg, maxHeight: height * .91, borderBottomLeftRadius: wide ? 38 : 0, borderBottomRightRadius: wide ? 38 : 0, paddingBottom: Math.max(insets.bottom, 20) }]}>{!wide && <View {...sheetGesture.panHandlers} style={{ paddingTop: 9, paddingBottom: 12 }}><View style={[s.grabber, { backgroundColor: c.muted }]}/></View>}<View {...sheetGesture.panHandlers} style={[s.sheetHeader, { paddingTop: wide ? 22 : 12 }]}><Text style={{ color: c.text, fontSize: 23, fontWeight: '700', letterSpacing: -.5 }}>{sheet === 'duration' ? '番茄钟设置' : sheet ? titles[sheet] : ''}</Text></View><ScrollView onScroll={e => { sheetScrollY.current = e.nativeEvent.contentOffset.y; }} scrollEventThrottle={16} keyboardShouldPersistTaps="handled" contentContainerStyle={{ paddingHorizontal: 24, paddingTop: 6, paddingBottom: 20 }}>{renderSheet()}</ScrollView></Animated.View></View>}
    {(!!dialog || !!ws.conflict) && <View style={[StyleSheet.absoluteFill, s.dialogLayer, { backgroundColor: c.overlay }]}><View accessibilityViewIsModal style={[s.dialog, { backgroundColor: c.surface }]}><Text style={{ color: c.text, fontSize: 19, fontWeight: '600', textAlign: 'center' }}>{ws.conflict ? '发现两份计时状态' : dialog?.title}</Text><Text style={{ color: c.secondary, fontSize: 16, lineHeight: 24, textAlign: 'center', marginTop: 14, marginBottom: 24 }}>{ws.conflict ? '另一台设备也修改了计时或设置。请选择要继续使用的状态；双方已保存的记录会合并，本机状态会另存恢复备份。' : dialog?.body}</Text>{ws.conflict ? <><Button label="使用云端计时与设置" onPress={() => ws.resolve('remote')}/><View style={{ height: 10 }}/><Button label="保留本机计时与设置" secondary onPress={() => ws.resolve('local')}/></> : <>{dialog?.action && <><Button label={dialog.action} danger={dialog.danger} onPress={() => { const fn = dialog.onConfirm; setDialog(null); fn?.(); }}/><View style={{ height: 8 }}/></>}{dialog?.discard && elapsed(timer, now) <= 300 && <><Button label="结束但不保存" danger onPress={() => { const fn = dialog.discard; setDialog(null); fn?.(); }}/><View style={{ height: 8 }}/></>}<Button label={dialog?.action ? '取消' : '知道了'} secondary onPress={() => setDialog(null)}/></>}</View></View>}{!!toast && <View pointerEvents="none" accessibilityLiveRegion="polite" style={{ position: 'absolute', top: insets.top + 60, alignSelf: 'center', backgroundColor: c.surface, paddingHorizontal: 20, paddingVertical: 12, ...iosCorners(20), zIndex: 10 }}><Text style={{ color: c.text }}>{toast}</Text></View>}</View></MenuHost></Modal>
  </View></Theme.Provider></LanguageContext.Provider>;
}
const s = StyleSheet.create({
  topbar: { paddingHorizontal: 24, paddingBottom: 15 }, topbarInner: { width: '100%', alignSelf: 'center', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, brand: { flexDirection: 'row', alignItems: 'center', gap: 12 }, brandIcon: { width: 43, height: 43, ...iosCorners(18), alignItems: 'center', justifyContent: 'center' }, largeTitle: { fontSize: 32, fontWeight: '700', letterSpacing: -.9 }, syncLink: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' }, desktopTabs: { flexDirection: 'row', padding: 3, ...iosCorners(18), gap: 3 }, desktopTab: { flexDirection: 'row', alignItems: 'center', gap: 7, paddingVertical: 9, paddingHorizontal: 25, ...iosCorners(15) }, timerPage: { paddingTop: 3 }, clockArea: { alignItems: 'center' }, digits: { fontWeight: '700', letterSpacing: 0, includeFontPadding: false, fontVariant: ['tabular-nums'], fontFamily: Platform.OS === 'web' ? '-apple-system, BlinkMacSystemFont, "SF Pro Rounded", "Helvetica Neue", sans-serif' : undefined }, musicBar: { flexDirection: 'row', gap: 13, alignItems: 'center', padding: 14, ...iosCorners(26) }, musicTile: { width: 40, height: 40, ...iosCorners(14), alignItems: 'center', justifyContent: 'center' }, tabbar: { flexDirection: 'row', paddingTop: 10, borderTopWidth: StyleSheet.hairlineWidth }, tab: { flex: 1, alignItems: 'center', justifyContent: 'center', minHeight: 42 }, modalLayer: { flex: 1, alignItems: 'center' }, sheet: { width: '100%', maxWidth: 520, ...iosCorners(38), overflow: 'hidden' }, grabber: { height: 5, width: 36, borderRadius: 3, alignSelf: 'center', marginTop: 9 }, sheetHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 24, paddingBottom: 22 }, closeButton: { width: 32, height: 32, borderRadius: 16, alignItems: 'center', justifyContent: 'center' }, dialogLayer: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 26 }, dialog: { width: '100%', maxWidth: 365, ...iosCorners(30), padding: 25 },
});
