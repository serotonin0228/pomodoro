import { LocalizedText as Text, LocalizedPressable as Pressable } from './i18n';
import React, { createContext, useContext, useState, useEffect } from 'react';
import { Animated, Platform, StyleSheet, View, ViewStyle } from 'react-native';
import { Icon, IconName } from './Icon';

export const light = { bg: '#F2F2F7', surface: '#FFFFFF', text: '#151519', secondary: '#71717A', line: '#E6E6EC', blue: '#007AFF', blueTint: '#EAF3FF', muted: '#C7C7D1', green: '#238256', overlay: 'rgba(15,20,30,.30)' };
export const dark = { bg: '#000000', surface: '#1C1C1E', text: '#F5F5F7', secondary: '#9C9CA6', line: '#303034', blue: '#0A84FF', blueTint: '#102A46', muted: '#48484F', green: '#65CA8C', overlay: 'rgba(0,0,0,.60)' };
export const Theme = createContext({ c: light, isDark: false, reduceMotion: false });
export const useTheme = () => useContext(Theme);

// iOS continuous corners; modern browsers use the equivalent superellipse.
// Older browsers gracefully retain the specified radius.
export function iosCorners(radius: number): ViewStyle {
  return Platform.OS === 'web'
    ? { borderRadius: radius, cornerShape: 'superellipse(1.15)' } as ViewStyle
    : { borderRadius: radius, borderCurve: 'continuous' };
}

export function Button({ label, onPress, secondary = false, danger = false, icon, disabled = false }: { label: string; onPress: () => void; secondary?: boolean; danger?: boolean; icon?: IconName; disabled?: boolean }) {
  const { c, reduceMotion } = useTheme();
  const [scale] = useState(() => new Animated.Value(1));
  const press = (value: number) => { if (!reduceMotion) Animated.spring(scale, { toValue: value, damping: 20, stiffness: 450, useNativeDriver: true }).start(); };
  return <Animated.View style={{ transform: [{ scale }] }}><Pressable accessibilityRole="button" accessibilityLabel={label} disabled={disabled} onPress={onPress} onPressIn={() => press(.97)} onPressOut={() => press(1)} style={({ pressed }) => [ui.button, { backgroundColor: secondary || danger ? c.surface : c.blue, opacity: disabled ? .4 : pressed ? .78 : 1 }]}>{icon && <Icon name={icon} size={20} color={danger ? '#FF3B30' : secondary ? c.blue : '#fff'}/>}<Text style={{ color: danger ? '#FF3B30' : secondary ? c.blue : '#fff', fontSize: 17, fontWeight: '600' }}>{label}</Text></Pressable></Animated.View>;
}
export function IconButton({ name, label, onPress, disabled = false }: { name: IconName; label: string; onPress: () => void; disabled?: boolean }) {
  const { c } = useTheme();
  return <Pressable accessibilityRole="button" accessibilityLabel={label} disabled={disabled} onPress={onPress} style={({ pressed }) => [ui.iconButton, { backgroundColor: 'transparent', opacity: disabled ? .25 : 1, transform: [{ scale: pressed ? .94 : 1 }] }]}><Icon name={name} color={c.blue}/></Pressable>;
}
export function Segments<T extends string>({ options, value, onChange }: { options: T[]; value: T; onChange: (v: T) => void }) {
  const { c, isDark } = useTheme();
  return <View accessibilityRole="tablist" style={[ui.segments, { backgroundColor: isDark ? '#2C2C2E' : '#E7E7ED' }]}>{options.map(option => <Pressable key={option} accessibilityRole="tab" accessibilityState={{ selected: value === option }} aria-selected={value === option} onPress={() => onChange(option)} style={[ui.segment, value === option && { backgroundColor: isDark ? '#636366' : '#fff', ...(Platform.OS === 'web' ? { boxShadow: '0 1px 4px rgba(0,0,0,.10)' } : {}) }]}><Text style={{ color: c.text, fontSize: 14, fontWeight: value === option ? '600' : '400' }}>{option}</Text></Pressable>)}</View>;
}
export function Group({ title, children }: { title?: string; children: React.ReactNode }) {
  const { c } = useTheme();
  return <View style={{ marginBottom: 24 }}>{title && <Text style={[ui.groupTitle, { color: c.secondary }]}>{title}</Text>}<View style={{ ...iosCorners(24), overflow: 'hidden', backgroundColor: c.surface }}>{children}</View></View>;
}
export function Row({ label, value, onPress, icon, last = false, children, danger }: { label: string; value?: string; onPress?: () => void; icon?: IconName; last?: boolean; children?: React.ReactNode; danger?: boolean }) {
  const { c } = useTheme();
  const content = <>{icon && <Icon name={icon} size={20} color={c.blue}/>}<Text style={{ flex: 1, fontSize: 15, color: danger ? '#FF3B30' : c.text }}>{label}</Text>{value && <Text style={{ color: c.secondary, fontSize: 13, flexShrink: 1 }}>{value}</Text>}{children}{onPress && <Icon name="chevron" color={c.muted} size={15}/>}</>;
  const rowStyle = [ui.row, { borderBottomColor: c.line, borderBottomWidth: last ? 0 : StyleSheet.hairlineWidth }];
  return onPress ? <Pressable accessibilityRole="button" onPress={onPress} style={({ pressed }) => [rowStyle, { opacity: pressed ? .65 : 1 }]}>{content}</Pressable> : <View style={rowStyle}>{content}</View>;
}
export function Toggle({ label, value, onChange, last = false }: { label: string; value: boolean; onChange: (v: boolean) => void; last?: boolean }) {
  const { isDark, reduceMotion } = useTheme();
  const [position] = useState(() => new Animated.Value(value ? 20 : 0));
  useEffect(() => { Animated.spring(position, { toValue: value ? 20 : 0, damping: 22, stiffness: 350, useNativeDriver: true, ...(reduceMotion ? { overshootClamping: true, stiffness: 1000 } : {}) }).start(); }, [position, value, reduceMotion]);
  return <Row label={label} last={last}><Pressable accessibilityRole="switch" accessibilityLabel={label} accessibilityState={{ checked: value }} aria-checked={value} onPress={() => onChange(!value)} hitSlop={6} style={{ minWidth: 51, minHeight: 44, justifyContent: 'center' }}><View style={{ width: 51, height: 31, borderRadius: 16, backgroundColor: value ? '#34C759' : isDark ? '#39393D' : '#E5E5EA' }}><Animated.View style={{ position: 'absolute', left: 2, top: 2, width: 27, height: 27, borderRadius: 14, backgroundColor: '#FFFFFF', boxShadow: '0 1px 4px rgba(0,0,0,0.18)', transform: [{ translateX: position }] }}/></View></Pressable></Row>;
}
export function Stepper({ label, value, unit = '分钟', onChange, min = 1, max = 180, step = 1, last = false }: { label: string; value: number; unit?: string; onChange: (v: number) => void; min?: number; max?: number; step?: number; last?: boolean }) {
  const { c } = useTheme();
  return <Row label={label} last={last}><View style={{ backgroundColor: c.bg, flexDirection: 'row', ...iosCorners(18), alignItems: 'center' }}><Pressable accessibilityRole="button" accessibilityLabel={`减少${label}`} disabled={value <= min} onPress={() => onChange(Math.max(min, value - step))} style={ui.stepButton}><Text style={{ color: value <= min ? c.muted : c.blue, fontSize: 22 }}>−</Text></Pressable><Text style={{ color: c.text, fontVariant: ['tabular-nums'], fontSize: 13, minWidth: 58, textAlign: 'center' }}>{value} {unit}</Text><Pressable accessibilityRole="button" accessibilityLabel={`增加${label}`} disabled={value >= max} onPress={() => onChange(Math.min(max, value + step))} style={ui.stepButton}><Text style={{ color: value >= max ? c.muted : c.blue, fontSize: 22 }}>+</Text></Pressable></View></Row>;
}
export const ui = StyleSheet.create({ button: { minHeight: 52, ...iosCorners(999), flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 10, paddingHorizontal: 20 }, iconButton: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', borderRadius: 22 }, segments: { padding: 3, ...iosCorners(18), flexDirection: 'row', gap: 2 }, segment: { flex: 1, minHeight: 30, ...iosCorners(15), alignItems: 'center', justifyContent: 'center' }, groupTitle: { fontSize: 14, paddingHorizontal: 14, marginBottom: 9 }, row: { minHeight: 55, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, gap: 10 }, stepButton: { width: 36, height: 42, alignItems: 'center', justifyContent: 'center' }, textButton: { minHeight: 46, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 12 }, between: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, input: { minHeight: 55, ...iosCorners(18), paddingHorizontal: 16, fontSize: 16 } });
