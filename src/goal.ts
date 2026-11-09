import { FocusRecord, Settings, dayKey } from './model';

/** Progress for the selected range; days after today are excluded from the target. */
export function rangeGoal(settings: Settings, days: Date[], totals: Record<string, number>, records: FocusRecord[], now: number): number | null {
  if (settings.goalType === 'none') return null;
  const eligible = days.filter(day => dayKey(day) <= dayKey(now));
  if (!eligible.length) return 0;
  const keys = new Set(eligible.map(dayKey));
  if (settings.goalType === 'dailySessions') {
    const count = records.filter(r => !r.deleted && keys.has(dayKey(r.start))).length;
    return Math.round(count / (Math.max(1, settings.dailySessions) * eligible.length) * 100);
  }
  const seconds = eligible.reduce((sum, day) => sum + (totals[dayKey(day)] || 0), 0);
  if (settings.goalType === 'weeklyMinutes') {
    const weeks = new Set(eligible.map(day => { const start = new Date(day); start.setDate(start.getDate() - (start.getDay() + 6) % 7); return dayKey(start); }));
    return Math.round(seconds / (Math.max(1, settings.weeklyMinutes) * 60 * weeks.size) * 100);
  }
  return Math.round(seconds / (Math.max(1, settings.dailyGoal) * 60 * eligible.length) * 100);
}
