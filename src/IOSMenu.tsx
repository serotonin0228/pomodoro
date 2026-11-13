import { LocalizedText as Text, LocalizedPressable as Pressable } from './i18n';
import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { Animated, StyleSheet, View, useWindowDimensions } from 'react-native';
import { Icon } from './Icon';
import { iosCorners, ui, useTheme } from './ui';

type Menu = { left: number; top: number; width: number; labels: string[]; selected: number; choose: (index: number) => void };
type MenuContextValue = { show: (menu: Menu) => void; active: boolean };
const MenuContext = createContext<MenuContextValue | null>(null);

// The overlay lives in the existing sheet window, above its scroll view. It does not create a second native Modal.
export function MenuHost({ children, dismissRef }: { children: React.ReactNode; dismissRef: React.MutableRefObject<(() => boolean) | null> }) {
  const { c, isDark, reduceMotion } = useTheme();
  const [menu, setMenu] = useState<Menu | null>(null);
  const [progress] = useState(() => new Animated.Value(0));
  const menuRef = useRef<Menu | null>(null);
  const show = useCallback((value: Menu) => {
    progress.stopAnimation(); progress.setValue(reduceMotion ? 1 : 0);
    menuRef.current = value; setMenu(value);
    if (!reduceMotion) requestAnimationFrame(() => Animated.spring(progress, { toValue: 1, damping: 24, stiffness: 300, useNativeDriver: true }).start());
  }, [progress, reduceMotion]);
  const close = useCallback((action?: () => void) => {
    if (!menuRef.current) return false;
    menuRef.current = null;
    Animated.timing(progress, { toValue: 0, duration: reduceMotion ? 0 : 130, useNativeDriver: true }).start(({ finished }) => { if (finished) { setMenu(null); action?.(); } });
    return true;
  }, [progress, reduceMotion]);
  useEffect(() => { dismissRef.current = () => close(); return () => { dismissRef.current = null; }; }, [close, dismissRef]);
  return <MenuContext.Provider value={{ show, active: !!menu }}><View style={{ flex: 1 }}>{children}{menu && <View style={[StyleSheet.absoluteFill, { zIndex: 1000 }]}>
    <Pressable accessibilityLabel="关闭选择菜单" onPress={() => close()} style={StyleSheet.absoluteFill}/>
    <Animated.View accessibilityViewIsModal style={{ position: 'absolute', left: menu.left, top: menu.top, width: menu.width, paddingVertical: 4, ...iosCorners(20), backgroundColor: isDark ? '#2C2C2EF5' : '#FFFFFFF5', overflow: 'hidden', opacity: progress, transform: [{ scale: progress.interpolate({ inputRange: [0, 1], outputRange: [.94, 1] }) }], boxShadow: '0 10px 40px rgba(0,0,0,0.17)' }}>
      {menu.labels.map((label, index) => <Pressable key={index} accessibilityRole="menuitem" accessibilityState={{ selected: index === menu.selected }} onPress={() => close(() => menu.choose(index))} style={({ pressed }) => ({ minHeight: 46, flexDirection: 'row', gap: 12, alignItems: 'center', paddingHorizontal: 16, backgroundColor: pressed ? c.blueTint : 'transparent', borderBottomWidth: index === menu.labels.length - 1 ? 0 : StyleSheet.hairlineWidth, borderBottomColor: c.line })}><View style={{ width: 19 }}>{index === menu.selected && <Icon name="check" size={17} color={c.blue}/>}</View><Text numberOfLines={1} style={{ flex: 1, color: c.text, fontSize: 16 }}>{label}</Text></Pressable>)}
    </Animated.View></View>}</View></MenuContext.Provider>;
}

export function IOSMenu<T extends string | number>({ label, value, options, onChange, last = false }: { label: string; value: T; options: { value: T; label: string }[]; onChange: (value: T) => void; last?: boolean }) {
  const { c } = useTheme();
  const host = useContext(MenuContext);
  const anchor = useRef<React.ComponentRef<typeof View>>(null);
  const { width, height } = useWindowDimensions();
  const menuWidth = Math.min(270, width - 32);
  const menuHeight = options.length * 46 + 8;
  function open() {
    anchor.current?.measureInWindow((x, y, w, h) => {
      const below = y + h + 6;
      host?.show({ left: Math.max(16, Math.min(width - menuWidth - 16, x + w - menuWidth)), top: Math.max(16, below + menuHeight <= height - 16 ? below : y - menuHeight - 6), width: menuWidth, labels: options.map(option => option.label), selected: options.findIndex(option => option.value === value), choose: index => { if (options[index]) onChange(options[index].value); } });
    });
  }
  return <View ref={anchor} collapsable={false}><Pressable accessibilityRole="button" accessibilityLabel={`${label}：${options.find(option => option.value === value)?.label || ''}`} accessibilityState={{ expanded: !!host?.active }} onPress={open} style={({ pressed }) => [ui.row, { backgroundColor: pressed ? c.bg : 'transparent', borderBottomColor: c.line, borderBottomWidth: last ? 0 : StyleSheet.hairlineWidth }]}><Text style={{ flex: 1, fontSize: 15, color: c.text }}>{label}</Text><Text style={{ color: c.secondary, fontSize: 13, flexShrink: 1 }}>{options.find(option => option.value === value)?.label}</Text><View style={{ transform: [{ rotate: '90deg' }] }}><Icon name="chevron" size={13} color={c.secondary}/></View></Pressable></View>;
}
