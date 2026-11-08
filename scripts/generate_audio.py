"""Original procedural instrument sketches. No sampled or third-party music."""
from pathlib import Path
import wave
import numpy as np

RATE = 22050
LENGTH = 32
OUT = Path(__file__).resolve().parents[1] / 'assets' / 'audio'
OUT.mkdir(parents=True, exist_ok=True)

def render(kind):
    audio = np.zeros(RATE * LENGTH)
    # Four calm, original arpeggiated chords at 60 bpm.
    chords = [[48, 55, 60, 64, 67, 64, 60, 55], [45, 52, 57, 60, 64, 60, 57, 52], [41, 48, 53, 57, 60, 57, 53, 48], [43, 50, 55, 59, 62, 59, 55, 50]]
    for bar, chord in enumerate(chords):
        for step, note in enumerate(chord):
            start = int((bar * 8 + step) * RATE)
            length = min(RATE * 6, len(audio) - start)
            t = np.arange(length) / RATE
            freq = 440 * 2 ** ((note - 69) / 12)
            if kind == 'piano':
                tone = sum((0.52 ** (h - 1)) * np.sin(2 * np.pi * freq * h * t) * np.exp(-t * (0.7 + h * .28)) for h in range(1, 7))
                env = np.minimum(t / .008, 1)
            elif kind == 'guitar':
                tone = sum((1 / h ** 1.5) * np.sin(2 * np.pi * freq * h * t) * np.exp(-t * (1 + h * .24)) for h in range(1, 9))
                env = np.minimum(t / .012, 1)
            else:
                tone = sum((1 / h ** 2) * np.sin(2 * np.pi * freq * h * t + .02 * np.sin(2 * np.pi * 4.8 * t)) for h in range(1, 7))
                env = np.minimum(t / .6, 1) * np.exp(-t * .9)
            audio[start:start + length] += .22 * tone * env
    # Gentle room reflections and fade edges keep the loop unobtrusive.
    dry = audio.copy()
    for delay, gain in [(0.13, .12), (.29, .08), (.43, .04)]:
        offset = int(delay * RATE)
        audio[offset:] += dry[:-offset] * gain
    audio[:RATE // 4] *= np.linspace(0, 1, RATE // 4)
    audio[-RATE * 2:] *= np.linspace(1, 0, RATE * 2)
    audio = audio / max(1, np.max(np.abs(audio))) * .65
    with wave.open(str(OUT / f'{kind}.wav'), 'wb') as f:
        f.setnchannels(1); f.setsampwidth(2); f.setframerate(RATE)
        f.writeframes((audio * 32767).astype('<i2').tobytes())

for instrument in ['piano', 'guitar', 'strings']:
    render(instrument)
t = np.arange(RATE * 2) / RATE
bell = sum(np.sin(2 * np.pi * f * t) for f in [523.25, 659.25, 783.99]) / 3 * np.exp(-t * 3) * np.minimum(t / .01, 1) * .5
with wave.open(str(OUT / 'chime.wav'), 'wb') as f:
    f.setnchannels(1); f.setsampwidth(2); f.setframerate(RATE); f.writeframes((bell * 32767).astype('<i2').tobytes())
