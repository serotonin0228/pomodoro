import { Platform } from 'react-native';
import { translate, type Language } from './translate';
import { FocusRecord } from './model';

export async function exportRecords(records: FocusRecord[], language: Language = 'zh') {
  const t = (text: string) => translate(text, language);
  const rows = [['记录ID', '模式', '开始时间', '结束时间', '有效专注秒数'].map(t), ...records.filter(r => !r.deleted).map(r => [r.id, t(r.mode === 'countdown' ? '倒计时' : '正计时'), new Date(r.start).toISOString(), new Date(r.end).toISOString(), String(r.seconds)])];
  const csv = '\uFEFF' + rows.map(row => row.map(c => `"${c.replaceAll('"', '""')}"`).join(',')).join('\r\n');
  const filename = `Pomodoro-${new Date().toISOString().slice(0, 10)}.csv`;
  if (Platform.OS === 'web') {
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const a = document.createElement('a'); a.href = url; a.download = filename; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
  } else {
    const { File, Paths } = await import('expo-file-system');
    const { shareAsync, isAvailableAsync } = await import('expo-sharing');
    if (!(await isAvailableAsync())) throw new Error('此设备不支持分享文件');
    const file = new File(Paths.cache, filename); file.write(csv);
    await shareAsync(file.uri, { mimeType: 'text/csv', UTI: 'public.comma-separated-values-text' });
  }
}
