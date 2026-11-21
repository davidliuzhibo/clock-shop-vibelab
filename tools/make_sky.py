"""The inside-out sphere the workshop panorama (make_workshop.py) is wrapped on.

blender -b --factory-startup --python tools/make_sky.py -- <out.glb> [preview.jpg panorama.jpg [turn_left_degrees]]
One node, "sky": a 12 m sphere facing inward, with equirectangular UVs laid out the way the
panorama was rendered (image middle straight ahead at -z, left half on the left, ceiling on top).
The engine's own sphere maps its texture another way, so the game loads this one and puts an
unlit material with the panorama on it. With a preview path, it also renders the view from the
middle of the sphere looking ahead, to check the panorama stands upright and is not mirrored.
"""
import math
import os
import sys

import bmesh
import bpy
from mathutils import Vector

sys.path.insert(0, os.path.dirname(__file__))
import woodkit as wk  # noqa: E402

args = sys.argv[sys.argv.index("--") + 1:]
out = args[0]
R = 12.0
SEG, RINGS = 96, 48

wk.reset()
bm = bmesh.new()
uv = bm.loops.layers.uv.new("UVMap")
# Game frame: y up, -z ahead. Blender frame (what the exporter turns into it): z up, +y ahead.
grid = []
for i in range(RINGS + 1):
    lat = math.pi / 2 - math.pi * i / RINGS
    row = []
    for j in range(SEG + 1):
        lon = 2 * math.pi * j / SEG - math.pi  # 0 straight ahead, positive to the right
        x = R * math.cos(lat) * math.sin(lon)
        ahead = R * math.cos(lat) * math.cos(lon)
        row.append((bm.verts.new((x, ahead, R * math.sin(lat))), (j / SEG, 1 - i / RINGS)))
    grid.append(row)
for i in range(RINGS):
    for j in range(SEG):
        a, b, c, d = grid[i][j], grid[i][j + 1], grid[i + 1][j + 1], grid[i + 1][j]
        # Wound so the face points at the middle of the sphere.
        quad = [a, b, c, d]
        try:
            f = bm.faces.new([q[0] for q in quad])
        except ValueError:
            continue
        for loop, q in zip(f.loops, quad):
            loop[uv].uv = q[1]
bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-6)
bm.normal_update()
for f in bm.faces:
    if f.normal.dot(f.calc_center_median()) > 0:
        f.normal_flip()
mesh = bpy.data.meshes.new("sky")
bm.to_mesh(mesh)
bm.free()
ob = bpy.data.objects.new("sky", mesh)
bpy.context.scene.collection.objects.link(ob)
mat = wk.flat("sky", (1, 1, 1), roughness=1.0)
mesh.materials.append(mat)
wk.export(out, [ob])

if len(args) >= 3:
    preview, pano = args[1], args[2]
    nodes = mat.node_tree.nodes
    for n in list(nodes):
        nodes.remove(n)
    tex = nodes.new("ShaderNodeTexImage")
    tex.image = bpy.data.images.load(pano)
    emit = nodes.new("ShaderNodeEmission")
    outn = nodes.new("ShaderNodeOutputMaterial")
    mat.node_tree.links.new(tex.outputs["Color"], emit.inputs["Color"])
    mat.node_tree.links.new(emit.outputs["Emission"], outn.inputs["Surface"])
    sc = bpy.context.scene
    cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
    cam.data.lens = 18
    cam.location = (0, 0, 0)
    cam.rotation_euler = (math.radians(90), 0, math.radians(float(args[3]) if len(args) > 3 else 0))  # +y is ahead
    sc.collection.objects.link(cam)
    sc.camera = cam
    sc.render.engine = "CYCLES"
    sc.cycles.samples = 4
    sc.view_settings.view_transform = "Standard"
    sc.render.resolution_x, sc.render.resolution_y = 960, 540
    sc.render.image_settings.file_format = "JPEG"
    sc.render.filepath = preview
    bpy.ops.render.render(write_still=True)
print("sky", out)
