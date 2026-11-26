"""v4 (2026-11-25, the user: 视觉效果要大幅提升): new high-resolution textures (assets_lab/clock_v4/tex), crown, chair rail and
skirting everywhere, coffered ceilings, window sills and curtains, wall sconces, about twice the light, 4096 atlases.
Bakes the six rooms' shells: walls, floor, ceiling and woodwork as one mesh and one atlas each.
blender -b -P build_shells.py -- <clock_v2 dir> <old textures dir> <out dir> <atlas px> <samples> [room ...]
Writes shell_<room>.glb (mesh with atlas UVs) and shell_<room>.jpg. The game draws them unlit and tints for the hour.
"""
import bpy, bmesh, math, os, sys
from mathutils import Matrix, Vector
a = sys.argv[sys.argv.index("--") + 1:]
LAB, OLD, OUT, PX, SPP = a[0], a[1], a[2], int(a[3]), int(a[4])
ONLY = set(a[5:])
os.makedirs(OUT, exist_ok=True)

def material(name, path=None, color=(.3, .2, .12), rough=.8, emit=0.0, tint=None):
    m = bpy.data.materials.new(name); m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*color, 1); b.inputs["Roughness"].default_value = rough
    if path:
        t = m.node_tree.nodes.new("ShaderNodeTexImage"); t.image = bpy.data.images.load(path)
        if tint:
            mx = m.node_tree.nodes.new("ShaderNodeMix"); mx.data_type = "RGBA"; mx.blend_type = "MULTIPLY"; mx.inputs[0].default_value = 1.0
            m.node_tree.links.new(t.outputs[0], mx.inputs[6]); mx.inputs[7].default_value = (*tint, 1)
            m.node_tree.links.new(mx.outputs[2], b.inputs["Base Color"])
        else:
            m.node_tree.links.new(t.outputs[0], b.inputs["Base Color"])
    if emit:
        b.inputs["Emission Color"].default_value = (*color, 1); b.inputs["Emission Strength"].default_value = emit
    return m

class Room:
    """Authored in glTF axes (y up, player at the origin facing -z), then rotated to Blender's."""
    def __init__(self, name):
        bpy.ops.wm.read_factory_settings(use_empty=True)
        self.name = name; self.sc = bpy.context.scene; self.bm = bmesh.new(); self.uv = self.bm.loops.layers.uv.new("UV"); self.mats = []
    def mat(self, m):
        if m not in self.mats: self.mats.append(m)
        return self.mats.index(m)
    def quad(self, p, u, v, m, tile=(1, 1)):
        """Corner p, edges u and v; normal is u x v. tile: metres per texture repeat."""
        p, u, v = Vector(p), Vector(u), Vector(v)
        f = self.bm.faces.new([self.bm.verts.new(c) for c in (p, p + u, p + u + v, p + v)])
        f.material_index = self.mat(m)
        lu, lv = u.length / tile[0], v.length / tile[1]
        for l, q in zip(f.loops, ((0, 0), (lu, 0), (lu, lv), (0, lv))): l[self.uv].uv = q
    def box(self, lo, hi, m, tile=(1, 1)):
        x0, y0, z0 = lo; x1, y1, z1 = hi
        dx, dy, dz = x1 - x0, y1 - y0, z1 - z0
        self.quad((x0, y0, z1), (dx, 0, 0), (0, dy, 0), m, tile)
        self.quad((x1, y0, z0), (-dx, 0, 0), (0, dy, 0), m, tile)
        self.quad((x0, y1, z1), (dx, 0, 0), (0, 0, -dz), m, tile)
        self.quad((x0, y0, z0), (dx, 0, 0), (0, 0, dz), m, tile)
        self.quad((x0, y0, z0), (0, 0, dz), (0, dy, 0), m, tile)
        self.quad((x1, y0, z1), (0, 0, -dz), (0, dy, 0), m, tile)
    def shell(self, half, h, wall, floor, ceil, wall_tile=(2, 2), floor_tile=(1.5, 1.5)):
        s = half
        self.quad((-s, 0, s), (2 * s, 0, 0), (0, 0, -2 * s), floor, floor_tile)
        self.quad((-s, h, -s), (2 * s, 0, 0), (0, 0, 2 * s), ceil, (2, 2))
        self.quad((-s, 0, -s), (2 * s, 0, 0), (0, h, 0), wall, wall_tile)
        self.quad((s, 0, s), (-2 * s, 0, 0), (0, h, 0), wall, wall_tile)
        self.quad((-s, 0, s), (0, 0, -2 * s), (0, h, 0), wall, wall_tile)
        self.quad((s, 0, -s), (0, 0, 2 * s), (0, h, 0), wall, wall_tile)
    def trim(self, half, y0, y1, depth, m, tile=(1.5, 1.5)):
        s = half
        self.box((-s, y0, -s), (s, y1, -s + depth), m, tile); self.box((-s, y0, s - depth), (s, y1, s), m, tile)
        self.box((-s, y0, -s), (-s + depth, y1, s), m, tile); self.box((s - depth, y0, -s), (s, y1, s), m, tile)
    def lamp(self, at, color, watt, size=.3):
        d = bpy.data.lights.new("l", "POINT"); d.color = color; d.energy = watt * 1.5; d.shadow_soft_size = size
        o = bpy.data.objects.new("l", d); o.location = (at[0], -at[2], at[1]); self.sc.collection.objects.link(o)
    def finish(self):
        sc = self.sc; sc.render.engine = "CYCLES"; sc.cycles.device = "CPU"; sc.cycles.samples = SPP; sc.cycles.use_denoising = False
        w = bpy.data.worlds.new("w"); sc.world = w; w.use_nodes = True
        w.node_tree.nodes["Background"].inputs[0].default_value = (.5, .5, .55, 1); w.node_tree.nodes["Background"].inputs[1].default_value = .1
        me = bpy.data.meshes.new(self.name); self.bm.to_mesh(me); self.bm.free()
        for m in self.mats: me.materials.append(m)
        o = bpy.data.objects.new(self.name, me); sc.collection.objects.link(o)
        o.matrix_world = Matrix(((1, 0, 0, 0), (0, 0, -1, 0), (0, 1, 0, 0), (0, 0, 0, 1)))
        bpy.context.view_layer.objects.active = o; o.select_set(True); bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
        atlas = me.uv_layers.new(name="Atlas"); me.uv_layers.active = atlas
        bpy.ops.object.mode_set(mode="EDIT"); bpy.ops.mesh.select_all(action="SELECT")
        bpy.ops.uv.smart_project(angle_limit=math.radians(50), island_margin=.006, area_weight=.5)
        bpy.ops.object.mode_set(mode="OBJECT")
        img = bpy.data.images.new("atlas", PX, PX, float_buffer=True, alpha=False)
        for m in me.materials:
            n = m.node_tree.nodes.new("ShaderNodeTexImage"); n.image = img; m.node_tree.nodes.active = n
            for node in m.node_tree.nodes:
                if node.type == "TEX_IMAGE" and node is not n:
                    uvn = m.node_tree.nodes.new("ShaderNodeUVMap"); uvn.uv_map = "UV"; m.node_tree.links.new(uvn.outputs[0], node.inputs[0])
        me.uv_layers.active = atlas
        sc.render.bake.margin = 8; sc.render.bake.target = "IMAGE_TEXTURES"
        bpy.ops.object.bake(type="COMBINED", pass_filter={"DIRECT", "INDIRECT", "DIFFUSE", "EMIT"}, use_clear=True)
        sc.view_settings.view_transform = "Standard"; sc.view_settings.look = "None"
        sc.use_nodes = True; ct = sc.node_tree; ct.nodes.clear()
        ni = ct.nodes.new("CompositorNodeImage"); ni.image = img
        nd = ct.nodes.new("CompositorNodeDenoise"); nd.use_hdr = True; nd.prefilter = "NONE"
        nc = ct.nodes.new("CompositorNodeComposite")
        ct.links.new(ni.outputs[0], nd.inputs[0]); ct.links.new(nd.outputs[0], nc.inputs[0])
        sc.render.resolution_x = PX; sc.render.resolution_y = PX; sc.render.resolution_percentage = 100
        sc.render.filepath = f"{OUT}/shell_{self.name}.jpg"; sc.render.image_settings.file_format = "JPEG"; sc.render.image_settings.quality = 88
        sc.render.use_compositing = True; bpy.ops.render.render(write_still=True); sc.use_nodes = False
        for i in range(len(me.uv_layers) - 1, -1, -1):
            if me.uv_layers[i].name != "Atlas": me.uv_layers.remove(me.uv_layers[i])
        me.materials.clear(); pm = bpy.data.materials.new("Shell"); me.materials.append(pm)
        bpy.ops.object.select_all(action="DESELECT"); o.select_set(True)
        bpy.ops.export_scene.gltf(filepath=f"{OUT}/shell_{self.name}.glb", use_selection=True, export_format="GLB", export_tangents=False, export_normals=False)
        print("SHELL-DONE", self.name, len(me.polygons), flush=True)

def tex(n, lab=True): return f"{LAB}/{n}.jpeg"
def want(n): return not ONLY or n in ONLY
def m_all():
    return dict(
        wallpaper=material("wallpaper", tex("wallpaper", False)), planks=material("planks", tex("planks", False)), stone=material("stone", tex("stone", False)),
        parquet=material("parquet", tex("parquet"), tint=(.62, .5, .42)), wainscot=material("wainscot", tex("wainscot")), books=material("books", tex("books")),
        wood=material("wood", None, (.16, .085, .045), .6), beam=material("beam", tex("planks", False)),
        sky=material("sky", None, (1, .86, .62), 1, 6.0), moon=material("moonlight", None, (.55, .65, .95), 1, 2.5), brass=material("brass", None, (.75, .55, .22), .4),
        ceiling=material("ceiling", tex("ceiling")), towerstone=material("towerstone", tex("towerstone")),
        curtain=material("curtain", None, (.2, .025, .03), .9), bulb=material("bulb", None, (1, .82, .55), 1, 6.0),
    )

def dress(r, m, S, H, rail=.95, crown=.12):
    """Skirting, chair rail and crown moulding round the room (stepped boxes read as profiles)."""
    r.trim(S, 0, .16, .045, m["wood"]); r.trim(S, .16, .19, .03, m["wood"])
    r.trim(S, rail, rail + .045, .05, m["wood"]); r.trim(S, rail + .045, rail + .06, .035, m["wood"])
    r.trim(S, H - crown, H, .09, m["wood"]); r.trim(S, H - crown - .05, H - crown, .055, m["wood"])

def sconce(r, m, x, y, z, wall):
    """A brass wall lamp with a lit shade, and its light. [wall]: which wall it is on (-1 left / 1 right in x, 2 back in -z)."""
    if wall == 2:
        r.box((x - .06, y - .1, z), (x + .06, y + .1, z + .03), m["brass"]); r.box((x - .055, y + .08, z + .03), (x + .055, y + .2, z + .14), m["bulb"])
        r.lamp((x, y + .14, -z - .3 if False else z + .25), (1, .8, .55), 26, .15)
    else:
        sx = 1 if wall < 0 else -1
        r.box((x, y - .1, z - .06), (x + sx * .03, y + .1, z + .06), m["brass"]) if sx > 0 else r.box((x - .03, y - .1, z - .06), (x, y + .1, z + .06), m["brass"])
        x2 = x + sx * .03
        r.box((min(x2, x2 + sx * .11), y + .08, z - .055), (max(x2, x2 + sx * .11), y + .2, z + .055), m["bulb"])
        r.lamp((x + sx * .3, y + .14, z), (1, .8, .55), 26, .15)

if want("front"):
    r = Room("front"); m = m_all(); H = 3.2; S = 3.1
    r.shell(S, H, m["wallpaper"], m["parquet"], m["ceiling"], (1.3, 1.3), (2.2, 2.2))
    r.trim(S, 0, .95, .03, m["wainscot"], (2.6, 1.9)); dress(r, m, S, H)
    # The window's sill and its two red curtains; sconces either side of the back wall and on the right wall.
    r.box((-S + .02, 1.0, -1.0), (-S + .16, 1.05, .3), m["wood"])
    for z0, z1 in ((-1.35, -1.0), (.3, .65)): r.box((-S + .05, .95, z0), (-S + .14, H - .25, z1), m["curtain"])
    for x in (-1.6, 1.6): sconce(r, m, x, 1.75, -S + .005, 2)
    for z in (-1.4, 1.2): sconce(r, m, S - .005, 1.75, z, 1)
    for z in (-2, 0, 2): r.box((-S, H - .16, z - .1), (S, H, z + .1), m["wood"])
    # Tall window in the left wall, the afternoon outside.
    r.box((-S + .005, 1.05, -.95), (-S + .02, 2.65, .25), m["sky"]); r.box((-S + .02, 1.03, -.34), (-S + .05, 2.67, -.3), m["wood"]); r.box((-S + .02, 1.8, -.97), (-S + .05, 1.84, .27), m["wood"])
    for z0, z1 in ((-1.05, -.95), (.25, .35)): r.box((-S + .02, 1.0, z0), (-S + .06, 2.72, z1), m["wood"])
    for at in ((-1.3, 2.7, -1), (1.3, 2.7, -1), (0, 2.7, 1.5)): r.lamp(at, (1, .82, .58), 34)
    r.lamp((-2.6, 1.9, -.35), (1, .85, .6), 40, .6); r.finish()
if want("workshop"):
    r = Room("workshop"); m = m_all(); H = 3.2; S = 3.1
    r.shell(S, H, m["planks"], m["parquet"], m["beam"], (1.5, 3.2), (2.2, 2.2))
    r.trim(S, 0, 1.1, .03, m["wainscot"], (2.6, 1.9)); dress(r, m, S, H, 1.1)
    # Two long wall shelves on the side walls (the clocks stand on the game's own props).
    for x0, x1 in ((-S, -S + .28), (S - .28, S)):
        for y in (1.55, 2.05): r.box((x0, y, -2.4), (x1, y + .035, 2.4), m["wood"])
    for z in (-1.2, 1.2): sconce(r, m, -S + .005, 1.8, z, -1)
    for z in (-2.2, -.7, .8, 2.3): r.box((-S, H - .2, z - .09), (S, H, z + .09), m["wood"])
    for at in ((-1.4, 2.7, -1), (1.4, 2.7, -1)): r.lamp(at, (1, .8, .55), 30)
    r.finish()
if want("attic"):
    r = Room("attic"); m = m_all(); H = 2.7; S = 3.1
    r.shell(S, H, m["planks"], m["planks"], m["beam"], (1.5, 2.7), (1.5, 3))
    r.trim(S, 0, .14, .045, m["wood"]); r.box((-.5, 1.15, -S + .02), (.5, 1.2, -S + .14), m["wood"])
    for x in (-2.4, -1.2, 0, 1.2, 2.4):
        r.box((x - .08, H - .22, -S), (x + .08, H, S), m["wood"])
    for z in (-2.5, -.8, .9, 2.6):
        r.box((-S, H - .4, z - .07), (S, H - .24, z + .07), m["wood"])
    r.box((-.45, 1.2, -S + .005), (.45, 2.1, -S + .02), m["sky"]); r.box((-.02, 1.2, -S + .02), (.02, 2.1, -S + .05), m["wood"]); r.box((-.45, 1.63, -S + .02), (.45, 1.67, -S + .05), m["wood"])
    r.lamp((0, 2.2, -2.2), (1, .85, .62), 34, .5); r.lamp((1.6, 2.2, 0), (1, .8, .55), 18); r.lamp((-1.6, 2.2, 0), (1, .8, .55), 18); r.finish()
if want("cellar"):
    r = Room("cellar"); m = m_all(); H = 3.2; S = 3.1
    r.shell(S, H, m["stone"], m["towerstone"], m["stone"], (1.3, 1.3), (1.4, 1.4))
    for z in (-2.2, 0, 2.2):
        for x in (-S, S - .35): r.box((x, 0, z - .22), (x + .35, H, z + .22), m["stone"], (1.6, 1.6))
        r.box((-S, H - .4, z - .22), (S, H, z + .22), m["stone"], (1.6, 1.6))
    r.lamp((-2.2, 2.2, -1.6), (1, .66, .36), 22, .15); r.lamp((2.2, 2.2, -1.6), (1, .66, .36), 22, .15); r.lamp((0, 2.6, -.6), (1, .72, .45), 26, .3); r.finish()
if want("tower"):
    r = Room("tower"); m = m_all(); H = 3.4; S = 3.1
    r.shell(S, H, m["towerstone"], m["planks"], m["beam"], (1.5, 1.5), (1.5, 3))
    r.trim(S, 0, .18, .06, m["towerstone"], (1.5, 1.5))
    for x in (-2.6, -1.3, 0, 1.3, 2.6): r.box((x - .1, H - .26, -S), (x + .1, H, S), m["wood"])
    for z in (-2.7, 2.7):
        r.box((-S, 2.7, z - .1), (S, 2.92, z + .1), m["wood"])
        for x in (-S + .1, S - .1): r.box((x - .1, 0, z - .1), (x + .1, H, z + .1), m["wood"])
    # Light through the louvres behind the clock face.
    r.box((-1.1, 1.3, -S + .005), (-.75, 2.6, -S + .02), m["sky"]); r.box((.75, 1.3, -S + .005), (1.1, 2.6, -S + .02), m["sky"])
    r.lamp((0, 2.4, -1.6), (1, .82, .6), 34, .5); r.lamp((-2, 2.4, 0), (1, .75, .5), 16); r.lamp((2, 2.4, 0), (1, .75, .5), 16); r.finish()
if want("study"):
    r = Room("study"); m = m_all(); H = 3.0; S = 3.1
    r.shell(S, H, m["wallpaper"], m["parquet"], m["ceiling"], (1.3, 1.3), (2.2, 2.2))
    r.quad((-S + .03, .2, -S + .08), (6.14, 0, 0), (0, H - .55, 0), m["books"], (2.6, 2.25))
    r.quad((-S + .08, .2, S - .03), (0, 0, -6.14), (0, H - .55, 0), m["books"], (2.6, 2.25))
    r.trim(S, 0, .2, .08, m["wood"]); r.trim(S, H - .35, H, .1, m["wood"])
    # Shelf boards in front of the books, and the book walls' side posts.
    for y in (.2, .78, 1.36, 1.94, 2.52):
        r.box((-S + .03, y - .02, -S + .03), (S - .03, y + .02, -S + .12), m["wood"]); r.box((-S + .03, y - .02, -S + .03), (-S + .12, y + .02, S - .03), m["wood"])
    for x in (-2.0, -.6, .8, 2.2): r.box((x - .04, .2, -S + .03), (x + .04, H - .35, -S + .13), m["wood"])
    for z in (-1.0, 1.4): sconce(r, m, S - .005, 1.9, z, 1)
    r.lamp((0, 2.4, -1.3), (1, .82, .58), 30, .6); r.lamp((0, 2.4, 1.2), (1, .8, .55), 16, .6); r.finish()

# Gears and the round plate blank.
def gear(name, teeth, spokes):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bm = bmesh.new(); R, rr, hub, t = .5, .455, .08, .05
    pts = []
    for i in range(teeth * 4):
        an = 2 * math.pi * i / (teeth * 4); rad = R if i % 4 in (1, 2) else rr
        pts.append((rad * math.cos(an), rad * math.sin(an)))
    def prism(poly, z0, z1):
        a0 = [bm.verts.new((x, y, z0)) for x, y in poly]; a1 = [bm.verts.new((x, y, z1)) for x, y in poly]
        bm.faces.new(a1); bm.faces.new(list(reversed(a0)))
        for i in range(len(poly)):
            j = (i + 1) % len(poly); bm.faces.new((a0[i], a0[j], a1[j], a1[i]))
    inner = [(.38 * math.cos(2 * math.pi * i / 48), .38 * math.sin(2 * math.pi * i / 48)) for i in range(48)]
    for i in range(len(pts)):
        j = (i + 1) % len(pts); k = i * 48 // len(pts); l = (k + 1) % 48
        for z, flip in ((t / 2, False), (-t / 2, True)):
            q = [(pts[i][0], pts[i][1], z), (pts[j][0], pts[j][1], z), (inner[l][0], inner[l][1], z), (inner[k][0], inner[k][1], z)]
            if k == l: q.pop(2)
            f = [bm.verts.new(c) for c in q]
            bm.faces.new(list(reversed(f)) if flip else f)
    prism([(hub * math.cos(2 * math.pi * i / 24), hub * math.sin(2 * math.pi * i / 24)) for i in range(24)], -t * .9, t * .9)
    for s in range(spokes):
        an = 2 * math.pi * s / spokes; c, sn = math.cos(an), math.sin(an); w = .022
        prism([(hub * c - w * sn, hub * sn + w * c), (.39 * c - w * sn, .39 * sn + w * c), (.39 * c + w * sn, .39 * sn - w * c), (hub * c + w * sn, hub * sn - w * c)], -t * .4, t * .4)
    me = bpy.data.meshes.new(name); bm.to_mesh(me); bm.free()
    o = bpy.data.objects.new(name, me); bpy.context.scene.collection.objects.link(o)
    mt = material("brass", None, (.72, .52, .2), .38); mt.node_tree.nodes["Principled BSDF"].inputs["Metallic"].default_value = .85; me.materials.append(mt)
    o.matrix_world = Matrix(((1, 0, 0, 0), (0, 0, -1, 0), (0, 1, 0, 0), (0, 0, 0, 1)))
    bpy.context.view_layer.objects.active = o; o.select_set(True); bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    bpy.ops.export_scene.gltf(filepath=f"{OUT}/{name}.glb", use_selection=True, export_format="GLB", export_tangents=False)
if want("gears"):
    gear("wheel_big", 48, 6); gear("wheel_small", 24, 4)
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bm = bmesh.new(); uv = bm.loops.layers.uv.new("UV"); N = 64
    top = [bm.verts.new((math.cos(2 * math.pi * i / N), math.sin(2 * math.pi * i / N), .04)) for i in range(N)]
    bot = [bm.verts.new((math.cos(2 * math.pi * i / N), math.sin(2 * math.pi * i / N), -.04)) for i in range(N)]
    faces = [bm.faces.new(top), bm.faces.new(list(reversed(bot)))]
    for i in range(N):
        j = (i + 1) % N; faces.append(bm.faces.new((bot[i], bot[j], top[j], top[i])))
    for f in faces:
        for l in f.loops: l[uv].uv = (.5 + l.vert.co.x * .49, .5 + l.vert.co.y * .49)
    me = bpy.data.meshes.new("plate_disc"); bm.to_mesh(me); bm.free()
    o = bpy.data.objects.new("plate_disc", me); bpy.context.scene.collection.objects.link(o); me.materials.append(material("plate", None, (.7, .55, .3), .4))
    o.matrix_world = Matrix(((1, 0, 0, 0), (0, 0, -1, 0), (0, 1, 0, 0), (0, 0, 0, 1)))
    bpy.context.view_layer.objects.active = o; o.select_set(True); bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    bpy.ops.export_scene.gltf(filepath=f"{OUT}/plate_disc.glb", use_selection=True, export_format="GLB", export_tangents=False)
print("SHELLS-ALL-DONE", flush=True)
