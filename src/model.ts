import { timeNow } from './time';
export type Mode = 'countdown' | 'stopwatch';
export type Phase = 'focus' | 'break';
export type Timer = {
  id: string; mode: Mode; phase: Phase; status: 'idle' | 'running' | 'paused' | 'completed';
  duration: number; elapsed: number; startedAt: number | null; createdAt: number; device: string;
};
export type FocusRecord = { id: string; mode: Mode; start: number; end: number; seconds: number; segments: Segment[]; deleted?: boolean };
export type Segment = { start: number; end: number };
export type Settings = {
  focusMinutes: number; breakMinutes: number; longBreakMinutes: number; rounds: number;
  dailyGoal: number; theme: 'system' | 'light' | 'dark'; notifications: boolean;
  autoBreak: boolean; autoFocus: boolean; sound: boolean; musicDuringBreak: boolean; musicEnabled: boolean; musicTrack: number;
};
export type State = { timer: Timer; records: FocusRecord[]; settings: Settings; segments: Segment[]; round: number; updatedAt: number };
export const defaults: Settings = { focusMinutes: 25, breakMinutes: 5, longBreakMinutes: 15, rounds: 4, dailyGoal: 120, theme: 'system', notifications: true, autoBreak: false, autoFocus: false, sound: true, musicDuringBreak: false, musicEnabled: true, musicTrack: 0 };
export function uid() { return `${timeNow().toString(36)}-${Math.random().toString(36).slice(2, 12)}`; }
export function newTimer(device: string, settings = defaults, mode: Mode = 'countdown', phase: Phase = 'focus', round = 0): Timer {
  return { id: uid(), mode, phase, status: 'idle', duration: (phase === 'focus' ? settings.focusMinutes : round > 0 && round % settings.rounds === 0 ? settings.longBreakMinutes : settings.breakMinutes) * 60, elapsed: 0, startedAt: null, createdAt: timeNow(), device };
}
export function initialState(device: string): State { return { timer: newTimer(device), records: [], settings: { ...defaults }, segments: [], round: 0, updatedAt: timeNow() }; }
export function elapsed(t: Timer, now = timeNow()) { return Math.max(0, t.elapsed + (t.status === 'running' && t.startedAt !== null ? (now - t.startedAt) / 1000 : 0)); }
export function remaining(t: Timer, now = timeNow()) { return Math.max(0, t.duration - elapsed(t, now)); }
export function displaySeconds(t: Timer, now: number) { return t.mode === 'stopwatch' ? Math.floor(elapsed(t, now)) : Math.ceil(remaining(t, now)); }
export function clock(seconds: number) { const s = Math.max(0, Math.floor(seconds)); return s >= 3600 ? `${Math.floor(s / 3600)}:${String(Math.floor(s / 60) % 60).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}` : `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`; }
export function closeSegments(s: State, now: number): Segment[] {
  if (s.timer.status !== 'running' || s.timer.startedAt === null) return s.segments;
  const end = s.timer.mode === 'countdown' ? Math.min(now, s.timer.startedAt + Math.max(0, s.timer.duration - s.timer.elapsed) * 1000) : now;
  return [...s.segments, { start: s.timer.startedAt, end: Math.max(s.timer.startedAt, end) }];
}
export function pauseState(s: State, now = timeNow()): State {
  return { ...s, segments: closeSegments(s, now), timer: { ...s.timer, status: 'paused', elapsed: Math.min(elapsed(s.timer, now), s.timer.mode === 'countdown' ? s.timer.duration : Infinity), startedAt: null }, updatedAt: now };
}
export function finishState(s: State, now = timeNow(), completed = false): State {
  const segments = closeSegments(s, now);
  const seconds = Math.floor(segments.reduce((n, x) => n + (x.end - x.start) / 1000, 0));
  let records = s.records;
  if (s.timer.phase === 'focus' && seconds >= 1 && !records.some(r => r.id === s.timer.id)) {
    records = [...records, { id: s.timer.id, mode: s.timer.mode, start: segments[0].start, end: segments.at(-1)!.end, seconds, segments }];
  }
  return { ...s, records, segments: [], round: s.round + (s.timer.phase === 'focus' && seconds >= 1 ? 1 : 0), timer: { ...s.timer, status: completed ? 'completed' : 'idle', elapsed: completed ? s.timer.duration : 0, startedAt: null, id: completed ? s.timer.id : uid() }, updatedAt: now };
}
export function dayKey(date: Date | number) { const d = new Date(date); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; }
// Merge actual intervals first: concurrent devices must never double-count time.
export function dailyTotals(records: FocusRecord[]): Record<string, number> {
  const intervals = records.filter(r => !r.deleted).flatMap(r => r.segments).sort((a, b) => a.start - b.start);
  const merged: Segment[] = [];
  for (const x of intervals) { const last = merged.at(-1); if (last && x.start <= last.end) last.end = Math.max(last.end, x.end); else merged.push({ ...x }); }
  const totals: Record<string, number> = {};
  for (const x of merged) {
    let cursor = x.start;
    while (cursor < x.end) { const d = new Date(cursor); const next = new Date(d.getFullYear(), d.getMonth(), d.getDate() + 1).getTime(); const end = Math.min(next, x.end); const key = dayKey(cursor); totals[key] = (totals[key] || 0) + (end - cursor) / 1000; cursor = end; }
  }
  return totals;
}
export function mergeRecords(a: FocusRecord[], b: FocusRecord[]) {
  const map = new Map(a.map(r => [r.id, r]));
  for (const r of b) {
    const local = map.get(r.id);
    if (!local) { map.set(r.id, r); continue; }
    const segments: Segment[] = [];
    for (const x of [...local.segments, ...r.segments].sort((x, y) => x.start - y.start)) {
      const last = segments.at(-1);
      if (last && x.start <= last.end) last.end = Math.max(last.end, x.end); else segments.push({ ...x });
    }
    map.set(r.id, { ...r, start: Math.min(local.start, r.start), end: Math.max(local.end, r.end), segments, seconds: Math.floor(segments.reduce((n, x) => n + (x.end - x.start) / 1000, 0)), deleted: local.deleted || r.deleted });
  }
  return [...map.values()].sort((x, y) => x.start - y.start);
}
export function formatDuration(seconds: number) { const m = Math.floor(seconds / 60); return m >= 60 ? `${Math.floor(m / 60)}小时${m % 60 ? `${m % 60}分` : ''}` : m > 0 ? `${m}分钟` : `${Math.floor(seconds)}秒`; }
