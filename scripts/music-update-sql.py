"""Generate an admin-only, transactional database audio replacement. Does not execute SQL."""
import argparse
import base64
from pathlib import Path
from uuid import UUID

parser = argparse.ArgumentParser()
parser.add_argument('--track-id', required=True, type=UUID)
parser.add_argument('--audio', required=True, type=Path)
parser.add_argument('--output', required=True, type=Path)
args = parser.parse_args()
data = args.audio.read_bytes()
if not 0 < len(data) <= 30000000:
    parser.error('Audio must be between 1 byte and 30 MB.')
encoded = base64.b64encode(data).decode('ascii')
args.output.write_text(
    'begin;\n'
    f"update private.music_audio set data = decode('{encoded}', 'base64') where track_id = '{args.track_id}';\n"
    f"select id, title, audio_revision, bytes from public.music_tracks where id = '{args.track_id}';\n"
    'commit;\n'
)
print(f'Generated {args.output} for {len(data)} audio bytes. Execute with your database administrator account.')
