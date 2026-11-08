import { LocalizedText as Text, LocalizedPressable as Pressable } from './i18n';
import React, { useRef, useState } from 'react';
import { Animated, Modal, StyleSheet, View, useWindowDimensions } from 'react-native';
import { Icon } from './Icon';
import { iosCorners, ui, useTheme } from './ui';

export function IOSMenu<T extends string | number>({ label, value, options, onChange, last = false }: { label: string; value: T; options: { value: T; label: string }[]; onChange: (value: T) => void; last?: boolean }) {
  const { c, isDark, reduceMotion } = useTheme();
  const anchor = useRef<React.ComponentRef<typeof View>>(null);
  const [progress] = useState(() => new Animated.Value(0));
  const [position, setPosition] = useState<{ left: number; top: number } | null>(null);
  const { width, height } = useWindowDimensions();
  const menuWidth = Math.min(270, width - 32);
  const menuHeight = options.length * 46 + 8;
  function open() { anchor.current?.measureInWindow((x, y, w, h) => { const below = y + h + 6; setPosition({ left: Math.max(16, Math.min(width - menuWidth - 16, x + w - menuWidth)), top: Math.max(16, below + menuHeight <= height - 16 ? below : y - menuHeight - 6) }); progress.setValue(reduceMotion ? 1 : 0); Animated.spring(progress, { toValue: 1, damping: 24, stiffness: 300, useNativeDriver: true }).start(); }); }
  function close(action?: () => void) { Animated.timing(progress, { toValue: 0, duration: reduceMotion ? 0 : 130, useNativeDriver: true }).start(({ finished }) => { if (finished) { setPosition(null); action?.(); } }); }
  return <View ref={anchor} collapsable={false}>
    <Pressable accessibilityRole="button" accessibilityLabel={`${label}：${options.find(option => option.value === value)?.label || ''}`} accessibilityState={{ expanded: !!position }} onPress={open} style={({ pressed }) => [ui.row, { backgroundColor: pressed ? c.bg : 'transparent', borderBottomColor: c.line, borderBottomWidth: last ? 0 : StyleSheet.hairlineWidth }]}><Text style={{ flex: 1, fontSize: 16, color: c.text }}>{label}</Text><Text style={{ color: c.secondary, fontSize: 15 }}>{options.find(option => option.value === value)?.label}</Text><View style={{ transform: [{ rotate: '90deg' }] }}><Icon name="chevron" size={13} color={c.secondary}/></View></Pressable>
    <Modal visible={!!position} transparent animationType="none" statusBarTranslucent onRequestClose={() => close()}>
      <Pressable accessibilityLabel="关闭选择菜单" onPress={() => close()} style={StyleSheet.absoluteFill}/>
      {position && <Animated.View accessibilityViewIsModal style={{ position: 'absolute', ...position, width: menuWidth, paddingVertical: 4, ...iosCorners(20), backgroundColor: isDark ? '#2C2C2EF5' : '#FFFFFFF5', overflow: 'hidden', opacity: progress, transform: [{ scale: progress.interpolate({ inputRange: [0, 1], outputRange: [.94, 1] }) }], boxShadow: '0 10px 40px rgba(0,0,0,0.17)' }}>
        {options.map((option, index) => <Pressable key={String(option.value)} accessibilityRole="menuitem" accessibilityState={{ selected: option.value === value }} onPress={() => close(() => onChange(option.value))} style={({ pressed }) => ({ minHeight: 46, flexDirection: 'row', gap: 12, alignItems: 'center', paddingHorizontal: 16, backgroundColor: pressed ? c.blueTint : 'transparent', borderBottomWidth: index === options.length - 1 ? 0 : StyleSheet.hairlineWidth, borderBottomColor: c.line })}><View style={{ width: 19 }}>{option.value === value && <Icon name="check" size={17} color={c.blue}/>}</View><Text numberOfLines={1} style={{ flex: 1, color: c.text, fontSize: 16 }}>{option.label}</Text></Pressable>)}
      </Animated.View>}
    </Modal>
  </View>;
}
