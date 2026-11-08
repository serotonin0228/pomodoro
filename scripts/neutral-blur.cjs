const fs = require('fs');
const p = 'node_modules/expo-blur/android/src/main/java/expo/modules/blur/enums/TintStyle.kt';
if (fs.existsSync(p)) { const source = fs.readFileSync(p, 'utf8'); if (!source.includes('DEFAULT -> 0')) fs.writeFileSync(p, source.replace('      DARK -> ((255', '      DEFAULT -> 0\n      DARK -> ((255')); }
