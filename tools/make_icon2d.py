"""Builds the app icon from a cut-out picture (a card of the game) in front of a picture disc with a rim.

blender -b --factory-startup --python make_icon2d.py -- <res_dir> <figure.png> <disc_image> <rim r,g,b> <size 0..1> [preview.png]
Writes the PICO 3D icon layers (drawable/icon_3d_layer_{0,1}.png: 0 = the disc, 1 = the figure),
their SDF masks (icon_3d_sdf_*.png) and the flat icons (mipmap-xxxhdpi/ic_spatial_launcher.png,
drawable/ic_launcher_foreground.png). An SDF here is the distance inside the shape, ramping from
0 at the edge to 1 at 32 px in (1024 px canvas), in all four channels, matching the SDK template.
With a preview path, only the composite is written there.
"""
import os
import sys

import bpy
import numpy as np

args = sys.argv[sys.argv.index("--") + 1:]
res, fig_path, disc_tex = args[0], args[1], args[2]
rim_color = np.array([float(v) for v in args[3].split(",")])
size = float(args[4])
preview_path = args[5] if len(args) > 5 else None
preview = bool(preview_path)
N = 1024

# --- Foreground: the figure, fitted into the middle [size] of the canvas -----------------
im = bpy.data.images.load(fig_path)
fw, fh = im.size
fpx = np.array(im.pixels[:], dtype=np.float32).reshape(fh, fw, 4)
s = size * N / max(fw, fh)
nw, nh = int(fw * s), int(fh * s)
yi = (np.arange(nh) / s).astype(int).clip(0, fh - 1)
xi = (np.arange(nw) / s).astype(int).clip(0, fw - 1)
fg = np.zeros((N, N, 4), dtype=np.float32)
oy, ox = (N - nh) // 2, (N - nw) // 2
fg[oy:oy + nh, ox:ox + nw] = fpx[yi][:, xi]

# --- Background: a disc of dark wood with a thin gold rim ---------------------------------
wood = bpy.data.images.load(disc_tex)
ww, wh = wood.size
wpx = np.array(wood.pixels[:], dtype=np.float32).reshape(wh, ww, 4)
idx = (np.arange(N) * min(ww, wh) / N).astype(int)
crop = wpx[idx][:, idx]
yy, xx = np.mgrid[0:N, 0:N]
rr = np.hypot(xx - (N - 1) / 2, yy - (N - 1) / 2) / (N / 2)
bg = crop.copy()
bg[..., :3] *= np.clip(1.0 - 0.45 * rr ** 2, 0, 1)[..., None] * 0.8
rim = np.clip(1 - np.abs(rr - 0.955) * N / 2 / 6, 0, 1)
gold = rim_color
bg[..., :3] = bg[..., :3] * (1 - rim[..., None]) + gold * rim[..., None]
disc_alpha = np.clip((1 - rr) * N / 2 + 0.5, 0, 1)
bg[..., 3] = disc_alpha
side = min(ww, wh)
skypx = wpx
x0 = y0 = 0


def over(top, base):
    a = top[..., 3:4]
    out = base.copy()
    out[..., 3:4] = a + base[..., 3:4] * (1 - a)
    out[..., :3] = (top[..., :3] * a + base[..., :3] * base[..., 3:4] * (1 - a)) / np.maximum(out[..., 3:4], 1e-6)
    return out


def save(px, path):
    h, w = px.shape[:2]
    im = bpy.data.images.new(os.path.basename(path), w, h, alpha=True)
    im.pixels = np.ascontiguousarray(px, dtype=np.float32).ravel()
    im.filepath_raw = path
    im.file_format = "PNG"
    im.save()
    print("wrote", path)


composite = over(fg, bg)
if preview:
    save(composite, preview_path)
    sys.exit(0)


def sdf(alpha):
    """Inside distance / 32 px by repeated erosion, alternating square and cross neighborhoods
    so the ramp is close to round."""
    m = alpha > 0.5
    total = m.astype(np.float32)
    for k in range(31):
        p = np.pad(m, 1)
        nb = [p[:-2, 1:-1], p[2:, 1:-1], p[1:-1, :-2], p[1:-1, 2:]]
        if k % 2:
            nb += [p[:-2, :-2], p[:-2, 2:], p[2:, :-2], p[2:, 2:]]
        for n in nb:
            m = m & n
        total += m
    v = np.clip(total / 32, 0, 1)
    return np.repeat(v[..., None], 4, axis=2)


save(bg, os.path.join(res, "drawable/icon_3d_layer_0.png"))
save(fg, os.path.join(res, "drawable/icon_3d_layer_1.png"))
save(sdf(bg[..., 3]), os.path.join(res, "drawable/icon_3d_sdf_0.png"))
save(sdf(fg[..., 3]), os.path.join(res, "drawable/icon_3d_sdf_1.png"))
save(composite, os.path.join(res, "mipmap-xxxhdpi/ic_spatial_launcher.png"))

# Adaptive-icon foreground (1424, full bleed): the wood as a square, the artwork inside the safe zone.
M = 1424
full = np.zeros((M, M, 4), dtype=np.float32)
idx = (np.arange(M) * side / M).astype(int)
full[...] = skypx[y0 + idx][:, x0 + idx]
full[..., :3] *= 0.85
full[..., 3] = 1
off = (M - N) // 2
full[off:off + N, off:off + N] = over(fg, full[off:off + N, off:off + N])
save(full, os.path.join(res, "drawable/ic_launcher_foreground.png"))
