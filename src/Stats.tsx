import { LocalizedText as Text, LocalizedPressable as Pressable, LocalizedTextInput as TextInput, useI18n } from './i18n';
import React, { useMemo, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { FocusRecord, Settings, dailyTotals, dayKey, formatDuration } from './model';
import { Icon } from './Icon';
import { rangeGoal } from './goal';
import { IconButton, Segments, iosCorners, ui, useTheme } from './ui';

export function RecordRow({ record: r, onPress }: { record: FocusRecord; onPress: () => void }) {
  const { c } = useTheme();
  const { locale } = useI18n();
  return <Pressable accessibilityRole="button" onPress={onPress} style={[styles.record, { borderBottomColor: c.line }]}><View style={{ width: 34, height: 34, ...iosCorners(12), alignItems: 'center', justifyContent: 'center' }}><Icon name="timer" size={19} color={c.blue}/></View><View style={{ flex: 1 }}><Text style={{ color: c.text, fontSize: 14, fontWeight: '500' }}>{r.mode === 'countdown' ? '倒计时专注' : '正计时专注'}</Text><Text style={{ color: c.secondary, fontSize: 13, marginTop: 4 }}>{new Date(r.start).toLocaleDateString(locale, { month: 'numeric', day: 'numeric' })} · {new Date(r.start).toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' })}</Text></View><Text style={{ color: c.text, fontSize: 14, fontWeight: '600' }}>{formatDuration(r.seconds)}</Text><Icon name="chevron" size={13} color={c.muted}/></Pressable>;
}
export function Stats({ records, settings, wide, now, onStart, onExport, onHistory, onRecord, onGoal }: { records: FocusRecord[]; settings: Settings; wide: boolean; now: number; onStart: () => void; onExport: () => void; onHistory: () => void; onGoal: () => void; onRecord: (r: FocusRecord) => void }) {
  const { c } = useTheme();
  const { language, t } = useI18n();
  const weekLabels = language === 'en' ? ['M', 'T', 'W', 'T', 'F', 'S', 'S'] : ['一', '二', '三', '四', '五', '六', '日'];
  const [period, setPeriod] = useState<'日' | '周' | '月' | '自定义'>('周');
  const [offset, setOffset] = useState(0);
  const [fromDate, setFromDate] = useState(dayKey(new Date(now - 6 * 86400000)));
  const [toDate, setToDate] = useState(dayKey(now));
  const [dateError, setDateError] = useState(false);
  const [customRange, setCustomRange] = useState<{ start: string; end: string } | null>(null);
  const [selectedDay, setSelectedDay] = useState<string | null>(null);
  const dailyGoal = settings.dailyGoal;
  const totals = useMemo(() => dailyTotals(records), [records]);
  const live = records.filter(r => !r.deleted);
  const todayKey = dayKey(now);
  const range = useMemo(() => {
    const date = new Date(`${period === '自定义' && customRange ? customRange.start : todayKey}T00:00:00`);
    if (period === '日') date.setDate(date.getDate() + offset);
    if (period === '周') date.setDate(date.getDate() - (date.getDay() + 6) % 7 + offset * 7);
    if (period === '月') { date.setDate(1); date.setMonth(date.getMonth() + offset); }
    const count = period === '自定义' && customRange ? Math.round((Date.parse(`${customRange.end}T12:00:00`) - Date.parse(`${customRange.start}T12:00:00`)) / 86400000) + 1 : period === '日' || period === '自定义' ? 1 : period === '周' ? 7 : new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate();
    return { start: date, end: new Date(date.getFullYear(), date.getMonth(), date.getDate() + count), days: Array.from({ length: count }, (_, i) => new Date(date.getFullYear(), date.getMonth(), date.getDate() + i)) };
  }, [period, offset, todayKey, customRange]);
  const seconds = range.days.reduce((n, d) => n + (totals[dayKey(d)] || 0), 0);
  const previousStart = new Date(range.start);
  if (period === '月') previousStart.setMonth(previousStart.getMonth() - 1);
  else previousStart.setDate(previousStart.getDate() - range.days.length);
  let previousSeconds = 0;
  for (const d = new Date(previousStart); d < range.start; d.setDate(d.getDate() + 1)) previousSeconds += totals[dayKey(d)] || 0;
  const streakDate = new Date(`${todayKey}T00:00:00`);
  if (!((totals[todayKey] || 0) >= settings.streakMinutes * 60)) streakDate.setDate(streakDate.getDate() - 1);
  let streak = 0;
  while ((totals[dayKey(streakDate)] || 0) >= settings.streakMinutes * 60 && streak < 36500) { streak++; streakDate.setDate(streakDate.getDate() - 1); }

  const streakKeys = Object.keys(totals).filter(key => totals[key] >= settings.streakMinutes * 60).sort();
  let longestStreak = 0, run = 0, previous = '';
  for (const key of streakKeys) { const yesterday = new Date(`${key}T12:00:00`); yesterday.setDate(yesterday.getDate() - 1); run = dayKey(yesterday) === previous ? run + 1 : 1; longestStreak = Math.max(longestStreak, run); previous = key; }
  const goalType = settings.goalType;
  const goalLabel = goalType === 'weeklyMinutes' ? '每周时长目标' : goalType === 'dailySessions' ? '每日次数目标' : goalType === 'none' ? '目标已关闭' : '每日目标';
  const goalTarget = goalType === 'weeklyMinutes' ? settings.weeklyMinutes * 60 : goalType === 'dailySessions' ? settings.dailySessions : settings.dailyGoal * 60;
  const rangeRecords = live.filter(r => r.segments.some(x => x.end > +range.start && x.start < +range.end));
  const history = selectedDay ? live.filter(r => r.segments.some(x => dayKey(x.start) <= selectedDay && dayKey(x.end - 1) >= selectedDay)) : rangeRecords;
  const goal = rangeGoal(settings, range.days, totals, live, now) ?? 0;
  const max = Math.max(3600, ...range.days.map(d => totals[dayKey(d)] || 0));
  const intervals = rangeRecords.flatMap(r => r.segments).map(x => ({ start: Math.max(x.start, +range.start), end: Math.min(x.end, +range.end) })).filter(x => x.end > x.start).sort((a, b) => a.start - b.start);
  const merged: { start: number; end: number }[] = [];
  for (const x of intervals) { const last = merged.at(-1); if (last && x.start <= last.end) last.end = Math.max(last.end, x.end); else merged.push({ ...x }); }
  const hours = Array(24).fill(0) as number[];
  for (const x of merged) { let cursor = x.start; while (cursor < x.end) { const d = new Date(cursor); const next = new Date(d.getFullYear(), d.getMonth(), d.getDate(), d.getHours() + 1).getTime(); const end = Math.min(x.end, next); if (end <= cursor) break; hours[d.getHours()] += (end - cursor) / 1000; cursor = end; } }
  const hourMax = Math.max(60, ...hours);
  const dateLabel = (d: Date) => `${d.getMonth() + 1}月${d.getDate()}日`;
  return <View style={{ paddingBottom: 24 }}>
    <Pressable accessibilityRole="button" accessibilityLabel="设置统计目标" onPress={onGoal} style={({ pressed }) => ({ flexDirection: 'row', alignItems: 'center', gap: 12, padding: 16, marginBottom: 18, ...iosCorners(24), backgroundColor: c.surface, opacity: pressed ? .7 : 1 })}><Icon name="target" size={21} color={c.blue}/><Text style={{ flex: 1, fontSize: 15, color: c.text }}>{goalLabel}</Text><Text style={{ color: c.blue, fontSize: 15 }}>{goalType === 'none' ? '—' : goalType === 'dailySessions' ? `${settings.dailySessions} ${language === 'en' ? 'sessions' : '次'}` : formatDuration(goalTarget)}</Text><Icon name="chevron" size={13} color={c.secondary}/></Pressable>
    <View style={{ maxWidth: 340, width: '100%', alignSelf: 'center', marginBottom: 21 }}><Segments options={['日', '周', '月', '自定义']} value={period} onChange={v => { setPeriod(v); setOffset(0); setSelectedDay(null); }}/></View>
    {period === '自定义' && <View style={{ gap: 10, marginBottom: 18 }}><TextInput accessibilityLabel="开始日期" placeholder="YYYY-MM-DD" placeholderTextColor={c.secondary} value={fromDate} onChangeText={setFromDate} style={[ui.input, { backgroundColor: c.surface, color: c.text }]}/><TextInput accessibilityLabel="结束日期" placeholder="YYYY-MM-DD" placeholderTextColor={c.secondary} value={toDate} onChangeText={setToDate} style={[ui.input, { backgroundColor: c.surface, color: c.text }]}/><Pressable accessibilityRole="button" onPress={() => { const valid = (v: string) => /^\d{4}-\d{2}-\d{2}$/.test(v) && !Number.isNaN(Date.parse(`${v}T12:00:00`)) && dayKey(new Date(`${v}T12:00:00`)) === v; if (valid(fromDate) && valid(toDate) && fromDate <= toDate && Date.parse(`${toDate}T12:00:00`) - Date.parse(`${fromDate}T12:00:00`) <= 89 * 86400000) { setCustomRange({ start: fromDate, end: toDate }); setSelectedDay(null); setDateError(false); } else setDateError(true); }} style={{ padding: 12, alignItems: 'center' }}><Text style={{ color: c.blue }}>{language === 'en' ? 'Apply date range (up to 90 days)' : '应用日期范围（最多 90 天）'}</Text></Pressable>{dateError && <Text accessibilityRole="alert" style={{ color: '#D73535', fontSize: 14 }}>{language === 'en' ? 'Enter valid dates in order, within 90 days.' : '请输入有效日期，开始日期不能晚于结束日期，范围最多 90 天。'}</Text>}</View>}
    {period !== '自定义' && <View style={[ui.between, { marginTop: -8, marginBottom: 12 }]}><IconButton name="arrow" label="上一个时间段" onPress={() => { setOffset(n => n - 1); setSelectedDay(null); }}/><Text style={{ color: c.text, fontSize: 14, fontWeight: '500' }}>{period === '月' ? `${range.start.getFullYear()}年${range.start.getMonth() + 1}月` : period === '日' ? dateLabel(range.start) : `${dateLabel(range.start)} — ${dateLabel(range.days.at(-1)!)}`}</Text><IconButton name="chevron" label="下一个时间段" disabled={offset >= 0} onPress={() => { setOffset(n => n + 1); setSelectedDay(null); }}/></View>}
    <View style={wide ? { flexDirection: 'row', gap: 22 } : undefined}>
      <View style={{ flex: wide ? 1.5 : undefined, minWidth: 0 }}>
        <View style={[styles.card, { backgroundColor: c.surface }]}>
          <View style={ui.between}><Text style={{ color: c.secondary, fontSize: 12 }}>{period === '自定义' ? '所选时段' : offset === 0 ? `本${period}` : '所选时段'}专注</Text><Icon name="chart" color={c.blue} size={18}/></View>
          <Text adjustsFontSizeToFit minimumFontScale={.55} numberOfLines={1} testID="stats-total" style={{ color: c.text, fontSize: 30, fontWeight: '600', letterSpacing: 0, flexShrink: 1, marginTop: 10, fontVariant: ['tabular-nums'] }}>{formatDuration(seconds)}</Text>
          <Text style={{ color: c.secondary, fontSize: 13, marginTop: 10 }}>{language === 'en' ? 'Previous period' : '上一时段'} · {formatDuration(previousSeconds)}{previousSeconds > 0 ? `  ${seconds >= previousSeconds ? '+' : ''}${Math.round((seconds - previousSeconds) / previousSeconds * 100)}%` : ''}</Text><Text style={{ color: c.secondary, fontSize: 13, marginTop: 8 }}>{language === 'en' ? 'Current streak' : '连续专注'} · {streak} {language === 'en' ? 'days' : '天'}  ·  {language === 'en' ? 'Best' : '最长'} {longestStreak}</Text>
          {(period === '日' || (period === '自定义' && range.days.length === 1)) ? <View style={{ paddingVertical: 30 }}><View style={{ height: 10, borderRadius: 8, overflow: 'hidden', backgroundColor: c.bg }}><View style={{ height: '100%', width: `${Math.min(100, goal)}%`, backgroundColor: c.blue, borderRadius: 8 }}/></View><Text style={{ color: c.secondary, fontSize: 13, marginTop: 16 }}>{goalType === 'none' ? (language === 'en' ? 'Goal disabled' : '目标已关闭') : `${t(goalLabel)} · ${goal}%`}</Text></View> : <View style={{ height: 166, position: 'relative', marginTop: 26 }}>
            {[0, 1, 2, 3].map(i => <View key={i} style={{ position: 'absolute', left: 0, right: 0, top: i * 47, borderTopWidth: StyleSheet.hairlineWidth, borderColor: c.line }}/>)}
            <View style={{ flexDirection: 'row', alignItems: 'flex-end', height: period === '月' ? 141 : 166, gap: range.days.length > 14 ? 2 : 13 }}>{range.days.map((d, i) => { const val = totals[dayKey(d)] || 0; const selected = selectedDay === dayKey(d); return <Pressable key={dayKey(d)} accessibilityRole="button" accessibilityLabel={`${dateLabel(d)}专注${formatDuration(val)}`} onPress={() => setSelectedDay(selected ? null : dayKey(d))} style={{ flex: 1, height: '100%', justifyContent: 'flex-end', alignItems: 'center' }}>{period === '周' && <Text style={{ color: c.secondary, fontSize: 12, marginBottom: 7 }}>{val ? `${(val / 3600).toFixed(1)}h` : '—'}</Text>}<View style={{ width: '100%', maxWidth: period === '月' ? 14 : 44, height: Math.max(3, val / max * 115), backgroundColor: val ? c.blue : c.muted, opacity: selectedDay && !selected ? .32 : val ? 1 : .35, borderTopLeftRadius: 5, borderTopRightRadius: 5 }}/>{period === '周' && <Text numberOfLines={1} style={{ color: selected ? c.blue : c.secondary, fontSize: 12, marginTop: 9 }}>{weekLabels[i]}</Text>}</Pressable>; })}</View>{period === '月' && <View pointerEvents="none" style={{ position: 'absolute', left: 0, right: 0, top: 150, height: 18 }}>{[1, 5, 10, 15, 20, 25, range.days.length].filter((n, i, a) => a.indexOf(n) === i && n <= range.days.length).map(day => <Text key={day} numberOfLines={1} maxFontSizeMultiplier={1} style={{ position: 'absolute', left: `${(day - .5) / range.days.length * 100}%`, width: 28, marginLeft: -14, textAlign: 'center', color: c.secondary, fontSize: 12 }}>{day}</Text>)}</View>}
          </View>}
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: 22, paddingTop: 18, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: c.line, gap: 8 }}>{[{ value: rangeRecords.length, unit: '次', label: '专注次数' }, { value: goalType === 'none' ? '—' : goal, unit: goalType === 'none' ? '' : '%', label: '目标完成' }, { value: Math.floor(Math.max(0, ...rangeRecords.map(r => r.seconds)) / 60), unit: '分', label: '最长一次' }].map(x => <View key={x.label}><Text style={{ color: c.text, fontSize: 21, fontWeight: '600', fontVariant: ['tabular-nums'] }}>{x.value}<Text style={{ color: c.secondary, fontSize: 12, fontWeight: '400' }}> {x.unit}</Text></Text><Text style={{ color: c.secondary, fontSize: 12, marginTop: 6 }}>{x.label}</Text></View>)}</View>
        </View>
        <View style={[styles.card, { backgroundColor: c.surface, marginTop: 16 }]}><Text style={{ color: c.text, fontWeight: '600', fontSize: 16 }}>时段分布</Text><View style={{ flexDirection: 'row', height: 100, alignItems: 'flex-end', gap: 3, marginTop: 18 }}>{hours.map((value, hour) => <View key={hour} accessibilityLabel={t(`${hour}点专注${formatDuration(value)}`)} style={{ flex: 1, height: Math.max(3, value / hourMax * 100), borderRadius: 3, backgroundColor: value ? c.blue : c.bg }}/>)}</View><View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: 8 }}>{['0点', '6点', '12点', '18点', '24点'].map(label => <Text key={label} style={{ color: c.secondary, fontSize: 12 }}>{label}</Text>)}</View></View>
        {period === '月' && <View style={[styles.card, { backgroundColor: c.surface, marginTop: 16 }]}><Text style={{ color: c.text, fontWeight: '600', fontSize: 16 }}>专注日历</Text><View style={{ flexDirection: 'row', marginTop: 16 }}>{weekLabels.map((label, index) => <Text key={`${label}-${index}`} style={{ width: '14.285%', textAlign: 'center', color: c.secondary, fontSize: 12 }}>{label}</Text>)}</View><View style={{ flexDirection: 'row', flexWrap: 'wrap', marginTop: 8 }}>{Array.from({ length: (range.start.getDay() + 6) % 7 }, (_, i) => <View key={`blank-${i}`} style={{ width: '14.285%', height: 42 }}/>)}{range.days.map(date => { const key = dayKey(date); const value = totals[key] || 0; const selected = selectedDay === key; return <Pressable key={key} accessibilityRole="button" accessibilityLabel={`${dateLabel(date)}专注${formatDuration(value)}`} onPress={() => setSelectedDay(selected ? null : key)} style={{ width: '14.285%', height: 42, alignItems: 'center', justifyContent: 'center' }}><View style={{ width: 34, height: 34, borderRadius: 17, backgroundColor: value ? c.blue : c.bg, opacity: value ? .35 + Math.min(.65, value / (dailyGoal * 60) * .65) : 1, borderWidth: selected ? 2 : 0, borderColor: c.blue, alignItems: 'center', justifyContent: 'center' }}><Text style={{ color: value ? '#fff' : c.text, fontSize: 12 }}>{date.getDate()}</Text></View></Pressable>; })}</View></View>}
      </View>
      <View style={{ flex: wide ? 1 : undefined, minWidth: 0 }}>{selectedDay && <Text style={{ color: c.secondary, fontSize: 14, marginTop: 16 }}>{selectedDay} · {formatDuration(totals[selectedDay] || 0)}</Text>}<View style={[styles.card, { backgroundColor: c.surface, marginTop: wide ? 0 : 16 }]}><View style={ui.between}><Text style={{ color: c.text, fontWeight: '600', fontSize: 16 }}>{selectedDay ? `${selectedDay.slice(5).replace('-', '月')}日` : '专注记录'}</Text><Pressable accessibilityRole="button" onPress={() => selectedDay ? setSelectedDay(null) : onHistory()} style={{ padding: 8 }}><Text style={{ color: c.blue, fontSize: 12 }}>{selectedDay ? '清除筛选' : '全部'}</Text></Pressable></View>{history.length ? [...history].reverse().slice(0, wide ? 8 : 4).map(r => <RecordRow key={r.id} record={r} onPress={() => onRecord(r)}/>) : <View style={{ alignItems: 'center', paddingVertical: 34 }}><Icon name="timer" size={30} color={c.muted}/><Text style={{ color: c.text, marginTop: 16, fontSize: 15, fontWeight: '500' }}>还没有专注记录</Text><Pressable accessibilityRole="button" onPress={onStart} style={{ padding: 16 }}><Text style={{ color: c.blue, fontSize: 14 }}>开始一次专注</Text></Pressable></View>}</View><Pressable accessibilityRole="button" onPress={onExport} style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, minHeight: 54, marginTop: 6 }}><Icon name="download" size={16} color={c.blue}/><Text style={{ color: c.blue, fontSize: 13 }}>导出专注记录</Text></Pressable></View>
    </View>
  </View>;
}
const styles = StyleSheet.create({ card: { ...iosCorners(28), padding: 20, overflow: 'hidden' }, record: { flexDirection: 'row', gap: 10, alignItems: 'center', paddingVertical: 16, borderBottomWidth: StyleSheet.hairlineWidth } });
