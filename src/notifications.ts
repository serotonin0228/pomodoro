import { Platform } from 'react-native';
import * as Notifications from 'expo-notifications';
import { AndroidImportance } from 'expo-notifications';
import { translate, type Language } from './translate';
import { Timer, remaining } from './model';

Notifications.setNotificationHandler({ handleNotification: async () => ({ shouldPlaySound: true, shouldSetBadge: false, shouldShowBanner: true, shouldShowList: true }) });
export async function notificationPermission() {
  if (Platform.OS === 'web') return typeof Notification !== 'undefined' ? Notification.permission : 'unsupported';
  return (await Notifications.getPermissionsAsync()).status;
}
export async function requestNotifications(language: Language = 'zh') {
  if (Platform.OS === 'web') return typeof Notification !== 'undefined' ? (await Notification.requestPermission()) === 'granted' : false;
  if (Platform.OS === 'android') await Notifications.setNotificationChannelAsync('timer', { name: translate('计时提醒', language), importance: AndroidImportance.HIGH, sound: 'default' });
  return (await Notifications.requestPermissionsAsync()).granted;
}
export async function cancelReminder() { if (Platform.OS !== 'web') await Notifications.cancelAllScheduledNotificationsAsync(); }
export async function scheduleReminder(timer: Timer, sound: boolean, language: Language = 'zh') {
  if (Platform.OS === 'web') return;
  await cancelReminder();
  if (Platform.OS === 'android') return; // Native foreground service owns Android deadlines and pause actions.
  if (timer.status !== 'running' || timer.mode !== 'countdown') return;
  await Notifications.scheduleNotificationAsync({ content: { title: translate(timer.phase === 'focus' ? '专注完成，休息一下吧' : '休息结束，准备好了吗？', language), body: translate('打开Pomodoro，继续你的节奏。', language), sound: sound ? 'default' : false }, trigger: { type: Notifications.SchedulableTriggerInputTypes.TIME_INTERVAL, seconds: Math.max(1, Math.ceil(remaining(timer))), channelId: 'timer' } });
}
export function notifyWeb(phase: string, language: Language = 'zh') {
  if (Platform.OS === 'web' && typeof Notification !== 'undefined' && Notification.permission === 'granted') {
    const n = new Notification(translate(phase === 'focus' ? '专注完成，休息一下吧' : '休息结束', language), { body: translate('回到Pomodoro，继续你的节奏。', language), tag: 'pomodoro-complete' });
    n.onclick = () => { window.focus(); n.close(); };
  }
}
