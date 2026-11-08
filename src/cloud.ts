import { createClient, Session } from '@supabase/supabase-js';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform } from 'react-native';
import { State } from './model';
import { setTimeCorrection } from './time';

const url = process.env.EXPO_PUBLIC_SUPABASE_URL;
const key = process.env.EXPO_PUBLIC_SUPABASE_PUBLISHABLE_KEY || process.env.EXPO_PUBLIC_SUPABASE_ANON_KEY;
export const cloud = url && key ? createClient(url, key, { auth: { storage: AsyncStorage, persistSession: true, autoRefreshToken: true, detectSessionInUrl: Platform.OS === 'web' } }) : null;
export type CloudRow = { user_id: string; revision: number; payload: State; writer: string; updated_at: string };
let lastClockCheck = 0;
export async function fetchCloud(session: Session): Promise<CloudRow | null> {
  if (Date.now() - lastClockCheck > 60000) {
    const start = Date.now();
    const { data: serverTime, error: clockError } = await cloud!.rpc('server_time');
    if (!clockError && typeof serverTime === 'number') {
      const offset = serverTime - (start + Date.now()) / 2;
      setTimeCorrection(offset); lastClockCheck = Date.now();
      await AsyncStorage.setItem('pomodoro.clockOffset', String(offset));
    }
  }
  const { data, error } = await cloud!.from('workspaces').select('*').eq('user_id', session.user.id).maybeSingle();
  if (error) throw error;
  return data;
}
export async function saveCloud(state: State, revision: number, device: string): Promise<CloudRow> {
  const { data, error } = await cloud!.rpc('save_workspace', { expected_revision: revision, new_payload: state, device_id: device });
  if (error) throw error;
  return data as CloudRow;
}
