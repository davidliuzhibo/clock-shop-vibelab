"""Turns a sky panorama into the HDR source for an image-based light.

blender -b --factory-startup --python ibl_source.py -- sky.jpg out.hdr [gain]
then: cmgen --format=ktx --size=256 --deploy=<dir> out.hdr   (Filament's cmgen; the app loads
<dir>/out/out_ibl.ktx). The photo is linearized, brightened so the Milky Way shows in reflections,
and lifted by a faint blue floor so the night side of a metal model is not pure black.
"""
import sys
import bpy
import numpy as np

GAIN = 2.5
FLOOR = np.array([0.010, 0.013, 0.022])

_a = sys.argv[sys.argv.index("--") + 1:]
src, out = _a[:2]
if len(_a) > 2:
    GAIN = float(_a[2])
im = bpy.data.images.load(src)
im.scale(1024, 512)
w, h = im.size
px = np.array(im.pixels[:], dtype=np.float32).reshape(h, w, 4)
rgb = px[..., :3]
lin = np.where(rgb <= 0.04045, rgb / 12.92, ((rgb + 0.055) / 1.055) ** 2.4)
px[..., :3] = lin * GAIN + FLOOR
px[..., 3] = 1
o = bpy.data.images.new("ibl", w, h, float_buffer=True)
o.pixels = px.ravel()
o.filepath_raw = out
o.file_format = "HDR"
o.save()
print("wrote", out, w, h, "mean", px[..., :3].mean())
