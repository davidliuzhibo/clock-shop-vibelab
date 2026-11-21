"""Cuts the Seedream illustrations out of their white ground for the pop-up cards.

blender -b --factory-startup --python tools/cutout.py -- <in_dir> <out_dir> [max_px] [white]
For each <name>.jpeg: the white ground connected to the image border becomes transparent, the
figure keeps a white paper rim a few pixels wide (the look of a cut-out card), and the result is
cropped to the figure and scaled so its longer side is max_px (default 512), as <name>.png.
Backdrops (<page>_back) keep a full rectangle cropped to a wide strip (2.6:1) along the horizon.
"""
import os
import sys

import bpy
import numpy as np

args = sys.argv[sys.argv.index("--") + 1:]
src, dst = args[0], args[1]
MAX = int(args[2]) if len(args) > 2 else 512
# How light a pixel must be to count as ground (lower it to drop a soft grey floor shadow too).
WHITE = float(args[3]) if len(args) > 3 else 0.9
WORK = 768
os.makedirs(dst, exist_ok=True)
PAPER = np.array([0.97, 0.95, 0.9])


def load(path, size):
    im = bpy.data.images.load(path)
    im.scale(size, size)
    px = np.array(im.pixels[:], dtype=np.float32).reshape(size, size, 4)[::-1]  # top row first
    bpy.data.images.remove(im)
    return px[..., :3]


def dilate(m, r):
    out = m.copy()
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            if dx * dx + dy * dy > r * r:
                continue
            out |= np.roll(np.roll(m, dy, 0), dx, 1)
    return out


def save(rgba, path):
    h, w = rgba.shape[:2]
    im = bpy.data.images.new("out", w, h, alpha=True)
    im.pixels[:] = rgba[::-1].reshape(-1)
    im.filepath_raw = path
    im.file_format = "PNG"
    im.save()
    bpy.data.images.remove(im)


for f in sorted(os.listdir(src)):
    if not f.endswith(".jpeg"):
        continue
    name = f[:-5]
    rgb = load(os.path.join(src, f), WORK)
    n = WORK
    if name.endswith("_back"):
        # A wide strip around the horizon (the lower middle of the picture), as a full card.
        h = int(n / 2.6)
        top = int(n * 0.62) - h // 2
        top = max(0, min(n - h, top))
        crop = rgb[top:top + h]
        alpha = np.ones(crop.shape[:2], dtype=np.float32)
    else:
        white = (rgb.min(axis=2) > WHITE) & (rgb.max(axis=2) - rgb.min(axis=2) < 0.08)
        # Thin gaps in an outline must not let the ground flood into a white figure (a white fox,
        # a white tail): pixels near any drawn line act as a wall for the flood.
        white &= ~dilate(~white, 4)
        # Ground = white pixels connected to the border (grown until it stops).
        bg = np.zeros_like(white)
        bg[0, :] = white[0, :]; bg[-1, :] = white[-1, :]; bg[:, 0] = white[:, 0]; bg[:, -1] = white[:, -1]
        for _ in range(n * 2):
            grown = dilate(bg, 1) & white
            if (grown == bg).all():
                break
            bg = grown
        fig = ~bg
        # Specks of figure smaller than a few pixels are noise.
        rim = dilate(fig, 4) & ~fig
        keep = fig | rim
        ys, xs = np.nonzero(keep)
        if len(ys) == 0:
            print("empty", name)
            continue
        y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
        out_rgb = np.where(rim[..., None], PAPER, rgb)
        crop = out_rgb[y0:y1, x0:x1]
        alpha = keep[y0:y1, x0:x1].astype(np.float32)
    h, w = crop.shape[:2]
    rgba = np.concatenate([crop, alpha[..., None]], axis=2)
    # Scale so the longer side is MAX (nearest-neighbour on alpha keeps the cut crisp after masking).
    s = MAX / max(h, w)
    nh, nw = max(1, int(round(h * s))), max(1, int(round(w * s)))
    yi = (np.arange(nh) / s).astype(int).clip(0, h - 1)
    xi = (np.arange(nw) / s).astype(int).clip(0, w - 1)
    small = rgba[yi][:, xi]
    save(small, os.path.join(dst, name + ".png"))
    print(name, nw, nh, round(nh / nw, 3))
