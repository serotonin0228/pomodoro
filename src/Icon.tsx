import React from 'react';
import Svg, { Path, Circle, Rect, Line, Polyline } from 'react-native-svg';

export type IconName = 'timer' | 'chart' | 'gear' | 'music' | 'cloud' | 'chevron' | 'close' | 'play' | 'pause' | 'check' | 'download' | 'volume' | 'phone' | 'computer' | 'moon' | 'arrow' | 'bell' | 'wave' | 'stop' | 'sun' | 'target';
export function Icon({ name, size = 22, color = '#007AFF', stroke = 1.7 }: { name: IconName; size?: number; color?: string; stroke?: number }) {
  const common = { fill: 'none', stroke: color, strokeWidth: stroke, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const };
  return <Svg width={size} height={size} viewBox="0 0 24 24" {...common}>
    {name === 'timer' && <><Circle cx="12" cy="13" r="8"/><Path d="M9 2h6M12 2v3m6 1 2-2M12 8v5l3 2"/></>}
    {name === 'chart' && <><Rect x="3" y="14" width="4" height="7" rx="1"/><Rect x="10" y="9" width="4" height="12" rx="1"/><Rect x="17" y="3" width="4" height="18" rx="1"/></>}
    {name === 'gear' && <><Path d="m9 3 .6-1h4.8l.6 1 .5 2 2 .9 1.8-.6 2.4 4.2-1.4 1.4v2.2l1.4 1.4-2.4 4.2-1.8-.6-2 .9-.5 2H9l-.5-2-2-.9-1.8.6-2.4-4.2 1.4-1.4v-2.2L2.3 9.5l2.4-4.2 1.8.6 2-.9L9 3Z"/><Circle cx="12" cy="11.9" r="3.3"/></>}
    {name === 'music' && <><Path d="M9 18V5l12-3v13M9 9l12-3"/><Path d="M9 18c0 2-2 3-4 3s-3-1-3-2.5S4 16 6 16s3 .5 3 2Zm12-3c0 2-2 3-4 3s-3-1-3-2.5S16 13 18 13s3 .5 3 2Z"/></>}
    {name === 'cloud' && <><Path d="M7 18H5a4 4 0 0 1-.5-8A7 7 0 0 1 18 8a5 5 0 0 1 1 10h-2"/><Path d="m9 16 2 2 4-4"/></>}
    {name === 'chevron' && <Polyline points="9,5 16,12 9,19"/>}
    {name === 'arrow' && <Polyline points="15,5 8,12 15,19"/>}
    {name === 'close' && <Path d="m6 6 12 12M18 6 6 18"/>}
    {name === 'play' && <Path d="m8 4 12 8-12 8Z" fill={color} stroke="none"/>}
    {name === 'pause' && <><Rect x="6" y="4" width="4" height="16" rx="1" fill={color} stroke="none"/><Rect x="14" y="4" width="4" height="16" rx="1" fill={color} stroke="none"/></>}
    {name === 'stop' && <Rect x="5" y="5" width="14" height="14" rx="3" fill={color} stroke="none"/>}
    {name === 'check' && <Path d="m5 12 4 4L19 6"/>}
    {name === 'download' && <Path d="M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5"/>}
    {name === 'volume' && <><Path d="M3 9h4l5-4v14l-5-4H3ZM16 8a6 6 0 0 1 0 8m3-11a10 10 0 0 1 0 14"/></>}
    {name === 'phone' && <><Rect x="6" y="2" width="12" height="20" rx="3"/><Line x1="10" y1="19" x2="14" y2="19"/></>}
    {name === 'computer' && <><Rect x="3" y="3" width="18" height="13" rx="2"/><Path d="M12 16v5m-5 0h10"/></>}
    {name === 'sun' && <><Circle cx="12" cy="12" r="4"/><Path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5"/></>}
    {name === 'target' && <><Circle cx="12" cy="12" r="9"/><Circle cx="12" cy="12" r="4"/><Circle cx="12" cy="12" r="1" fill={color}/></>}
    {name === 'moon' && <Path d="M20 15A8.5 8.5 0 0 1 9 4a8.5 8.5 0 1 0 11 11Z"/>}
    {name === 'bell' && <Path d="M4 17h16l-2-4V9A6 6 0 0 0 6 9v4l-2 4ZM10 21h4"/>}
    {name === 'wave' && <Path d="M3 10v4m4-8v12m5-16v20m5-16v12m4-8v4"/>}
  </Svg>;
}
