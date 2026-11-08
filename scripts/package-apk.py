"""Recompress, align, sign and verify the ARM64-only release artifact.

Signing credentials remain outside the source tree. Requires Android build-tools.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('--input', type=Path, default=Path('android/app/build/outputs/apk/release/app-release.apk'))
parser.add_argument('--output', type=Path, default=Path('/workspace/番茄钟-v1.0.0-arm64-v8a.apk'))
parser.add_argument('--sdk', type=Path, default=Path(os.environ.get('ANDROID_HOME', '/workspace/android-sdk')))
parser.add_argument('--credentials', type=Path, default=Path('/workspace/.pomodoro-signing/release.json'))
args = parser.parse_args()
tools = args.sdk / 'build-tools' / '37.0.0'
credentials = json.loads(args.credentials.read_text())
env = {**os.environ, **credentials}

with zipfile.ZipFile(args.input) as source:
    assert source.testzip() is None, 'Corrupt APK'
    abis = {name.split('/')[1] for name in source.namelist() if name.startswith('lib/') and name.endswith('.so')}
    assert abis == {'arm64-v8a'}, f'Unexpected ABIs: {abis}'
    assert not any(name.lower().endswith('.wav') for name in source.namelist()), 'Uncompressed source audio was bundled'
    with tempfile.TemporaryDirectory(prefix='pomodoro-apk-', dir=args.output.parent) as folder:
        unsigned = Path(folder) / 'unsigned.apk'
        aligned = Path(folder) / 'aligned.apk'
        with zipfile.ZipFile(unsigned, 'w') as target:
            for item in source.infolist():
                if re.match(r'^META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))$', item.filename, re.I):
                    continue
                # Preserve stored entries (notably resources.arsc); maximize only existing deflate streams.
                target.writestr(item, source.read(item.filename), compress_type=item.compress_type, compresslevel=9)
        subprocess.run([str(tools / 'zipalign'), '-P', '16', '-f', '4', str(unsigned), str(aligned)], check=True)
        subprocess.run([str(tools / 'apksigner'), 'sign', '--ks', credentials['POMODORO_STORE_FILE'], '--ks-key-alias', credentials['POMODORO_KEY_ALIAS'], '--ks-pass', 'env:POMODORO_STORE_PASSWORD', '--key-pass', 'env:POMODORO_KEY_PASSWORD', '--v1-signing-enabled', 'false', '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'true', '--v4-signing-enabled', 'false', '--out', str(args.output), str(aligned)], env=env, check=True)

subprocess.run([str(tools / 'apksigner'), 'verify', '--verbose', str(args.output)], check=True)
subprocess.run([str(tools / 'zipalign'), '-c', '-P', '16', '4', str(args.output)], check=True)
with zipfile.ZipFile(args.output) as final:
    assert final.testzip() is None
    assert {n.split('/')[1] for n in final.namelist() if n.startswith('lib/') and n.endswith('.so')} == {'arm64-v8a'}
report = {'file': args.output.name, 'bytes': args.output.stat().st_size, 'MiB': round(args.output.stat().st_size / 1024 ** 2, 2), 'sha256': hashlib.sha256(args.output.read_bytes()).hexdigest(), 'abi': 'arm64-v8a', 'signing': 'APK signature v2/v3'}
args.output.with_suffix('.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
print(json.dumps(report, ensure_ascii=False, indent=2))
