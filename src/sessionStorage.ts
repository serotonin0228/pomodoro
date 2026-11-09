import AsyncStorage from '@react-native-async-storage/async-storage';
import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

const chunkSize = 1200;
const metaKey = (key: string) => `${key}.secure-meta`;
const chunkKey = (key: string, version: string, index: number) => `${key}.secure-${version}-${index}`;
type Metadata = { version: string; count: number };
async function metadata(key: string): Promise<Metadata | null> {
  const value = await SecureStore.getItemAsync(metaKey(key));
  if (value === null) return null;
  const parsed = JSON.parse(value) as Metadata;
  if (!/^[a-z0-9]+$/.test(parsed.version) || !Number.isInteger(parsed.count) || parsed.count < 1 || parsed.count > 100) throw new Error('Invalid secure session metadata');
  return parsed;
}
async function clearChunks(key: string, meta: Metadata | null) {
  if (meta) for (let i = 0; i < meta.count; i++) await SecureStore.deleteItemAsync(chunkKey(key, meta.version, i));
}
async function writeSecure(key: string, value: string) {
  const old = await metadata(key);
  const version = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 10)}`;
  const count = Math.max(1, Math.ceil(value.length / chunkSize));
  if (count > 100) throw new Error('Session is too large');
  for (let i = 0; i < count; i++) await SecureStore.setItemAsync(chunkKey(key, version, i), value.slice(i * chunkSize, (i + 1) * chunkSize));
  await SecureStore.setItemAsync(metaKey(key), JSON.stringify({ version, count }));
  await clearChunks(key, old).catch(() => {});
}
export const sessionStorage = Platform.OS === 'web' ? AsyncStorage : {
  async getItem(key: string) {
    const meta = await metadata(key);
    if (meta) {
      const parts = await Promise.all(Array.from({ length: meta.count }, (_, i) => SecureStore.getItemAsync(chunkKey(key, meta.version, i))));
      if (parts.some(part => part === null)) throw new Error('Secure session is incomplete');
      return parts.join('');
    }
    const legacy = await AsyncStorage.getItem(key);
    if (legacy !== null) { await writeSecure(key, legacy); await AsyncStorage.removeItem(key); }
    return legacy;
  },
  async setItem(key: string, value: string) { await writeSecure(key, value); await AsyncStorage.removeItem(key); },
  async removeItem(key: string) { const meta = await metadata(key); await SecureStore.deleteItemAsync(metaKey(key)); await clearChunks(key, meta); await AsyncStorage.removeItem(key); },
};
