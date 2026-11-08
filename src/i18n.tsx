import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { Text as NativeText, TextInput as NativeTextInput, Pressable as NativePressable } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { translate, type Language } from './translate';
export { translate } from './translate';
export type { Language } from './translate';

export const LanguageContext = createContext<Language>('zh');
export function useI18n() { const language = useContext(LanguageContext); return { language, locale: language === 'en' ? 'en-US' : 'zh-CN', t: (text: string) => translate(text, language) }; }
export function useAppLanguage() {
  const [language, updateLanguage] = useState<Language>('zh');
  const changed = useRef(false);
  useEffect(() => { let live = true; void AsyncStorage.getItem('pomodoro.language.v1').then(saved => { if (live && !changed.current && (saved === 'zh' || saved === 'en')) updateLanguage(saved); }).catch(() => {}); return () => { live = false; }; }, []);
  const setLanguage = useCallback((value: Language) => { changed.current = true; updateLanguage(value); void AsyncStorage.setItem('pomodoro.language.v1', value).catch(() => {}); }, []);
  return { language, setLanguage };
}
function localizedChildren(children: React.ReactNode, language: Language) {
  const result: React.ReactNode[] = []; let pending = '';
  function flush() { if (pending) { result.push(translate(pending, language)); pending = ''; } }
  React.Children.forEach(children, child => { if (typeof child === 'string' || typeof child === 'number') pending += child; else { flush(); result.push(child); } }); flush(); return React.Children.toArray(result);
}
export function LocalizedText({ children, ...props }: React.ComponentProps<typeof NativeText>) {
  const { language, t } = useI18n(); return <NativeText {...props} accessibilityLabel={props.accessibilityLabel ? t(props.accessibilityLabel) : undefined}>{localizedChildren(children, language)}</NativeText>;
}
export function LocalizedTextInput(props: React.ComponentProps<typeof NativeTextInput>) {
  const { t } = useI18n(); return <NativeTextInput {...props} accessibilityLabel={props.accessibilityLabel ? t(props.accessibilityLabel) : undefined} placeholder={props.placeholder ? t(props.placeholder) : undefined}/>;
}
export function LocalizedPressable(props: React.ComponentProps<typeof NativePressable>) {
  const { t } = useI18n(); return <NativePressable {...props} accessibilityLabel={props.accessibilityLabel ? t(props.accessibilityLabel) : undefined} accessibilityHint={props.accessibilityHint ? t(props.accessibilityHint) : undefined}/>;
}
