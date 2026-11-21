"""Synthesizes the clock shop's own sounds (no samples): the hour chime, the music box playing
3-1-5 (mi, do, sol), the clockwork bird, and the shop's tick-tock.

python3 make_sfx.py <out_dir>   (needs numpy; Blender's bundled Python has it)
"""
import sys
import wave
import numpy as np

RATE = 44100


def tone(freq, dur, decay, partials=((1, 1.0),), attack=0.004):
    t = np.arange(int(RATE * dur)) / RATE
    s = sum(a * np.sin(2 * np.pi * freq * k * t) for k, a in partials)
    env = np.exp(-t / decay) * np.minimum(1, t / attack)
    return s * env


def put(buf, at, s):
    i = int(at * RATE)
    buf[i:i + len(s)] += s[:len(buf) - i]


def save(name, s, gain=0.8):
    s = s / max(1e-6, np.abs(s).max()) * gain
    with wave.open(f"{OUT}/{name}.wav", "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes((s * 32767).astype(np.int16).tobytes())


OUT = sys.argv[1]
# The hour chime: three strokes of a warm bell (inharmonic partials like a real bell).
bell = ((1, 1.0), (2.0, 0.6), (2.4, 0.35), (3.0, 0.25), (4.2, 0.15), (0.5, 0.4))
buf = np.zeros(int(RATE * 4.2))
for k in range(3):
    put(buf, k * 1.0, tone(392, 2.6, 0.9, bell))
save("chime", buf)
# The music box: mi, do, sol (3, 1, 5), with a small ornament between, twice.
box = ((1, 1.0), (2, 0.25), (3.01, 0.12), (5.4, 0.05))
note = {"do": 1046.5, "mi": 1318.5, "sol": 1568.0, "re": 1174.7, "la": 1760.0}
buf = np.zeros(int(RATE * 4.6))
seq = [("mi", 0.0), ("do", 0.55), ("sol", 1.1), ("mi", 2.0), ("do", 2.55), ("sol", 3.1), ("do", 3.6)]
for n, at in seq:
    put(buf, at, tone(note[n], 1.0, 0.45, box, attack=0.002))
save("musicbox", buf, 0.7)
# The clockwork bird: quick rising chirps.
buf = np.zeros(int(RATE * 2.0))
for k, at in enumerate([0, 0.18, 0.36, 0.9, 1.05, 1.2, 1.35]):
    t = np.arange(int(RATE * 0.12)) / RATE
    f = 2600 + 1800 * t / 0.12 + 150 * k
    ph = 2 * np.pi * np.cumsum(f) / RATE
    put(buf, at, np.sin(ph) * np.sin(np.pi * t / 0.12) ** 2)
save("bird", buf, 0.6)
# Tick-tock: two short wooden clicks, the second a little lower, one second apart.
buf = np.zeros(int(RATE * 1.0))
rng = np.random.default_rng(3)
for at, f in [(0.0, 3200), (0.5, 2500)]:
    t = np.arange(int(RATE * 0.03)) / RATE
    click = (np.sin(2 * np.pi * f * t) + 0.5 * rng.standard_normal(len(t))) * np.exp(-t / 0.004)
    put(buf, at, click)
save("ticktock", buf, 0.6)
print("ok")
