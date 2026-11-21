"""Shared Blender helpers for the LubanBox models: voxel solids, wood materials, GLB export.

Imported by the make_*.py scripts (run inside Blender 4.2: blender -b --factory-startup --python ...).
Object frame everywhere: meters, +x right, +y up, +z toward the player. Blender is z-up, so a game
point (x, y, z) is Blender (x, -z, y); the glTF exporter (export_yup) turns it back.
"""
import os
import bmesh
import bpy

_materials = {}
_font = None

TEX_ROOT = os.environ.get("LUBAN_TEX", os.path.join(os.path.dirname(__file__), "..", "..", "..", "assets_lab", "luban", "tex"))


def reset():
    """Empties the scene (and forgets materials and the font, which go with it)."""
    global _font
    bpy.ops.wm.read_factory_settings(use_empty=True)
    _materials.clear()
    _font = None


def to_blender(p):
    x, y, z = p
    return (x, -z, y)


def voxel_mesh(name, cells, size, center=(0.0, 0.0, 0.0), grain_axis=0):
    """A closed mesh of unit cubes [cells] (game frame, integer x,y,z) scaled by [size].

    Only faces between a filled and an empty cell are made, then vertices are merged, so the
    result is one watertight solid. UVs run the wood grain along [grain_axis] (0=x, 1=y, 2=z in
    the game frame): on each face, u follows the grain axis when it lies in the face, else the
    first other axis; v follows the remaining one. One UV unit is 0.25 m of wood.
    """
    filled = set(map(tuple, cells))
    bm = bmesh.new()
    uv_layer = bm.loops.layers.uv.new("UVMap")
    verts = {}

    def vert(p):
        key = tuple(round(c, 6) for c in p)
        v = verts.get(key)
        if v is None:
            v = bm.verts.new(to_blender([(p[i] - center[i]) * size for i in range(3)]))
            verts[key] = v
        return v

    dirs = [(1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)]
    for c in filled:
        for d in dirs:
            n = (c[0] + d[0], c[1] + d[1], c[2] + d[2])
            if n in filled:
                continue
            axis = [i for i in range(3) if d[i] != 0][0]
            sign = d[axis]
            others = [i for i in range(3) if i != axis]
            # Face corners (game frame, in cell units), wound so the normal points outward.
            base = list(c)
            base[axis] += 1 if sign > 0 else 0
            a, b = others
            quad = []
            for (da, db) in [(0, 0), (1, 0), (1, 1), (0, 1)]:
                p = list(base)
                p[a] += da
                p[b] += db
                quad.append(p)
            # Game frame is right-handed; flip winding so the normal points along d.
            normal_sign = sign if (a, b) in [(1, 2), (2, 0), (0, 1)] else -sign
            if normal_sign < 0:
                quad.reverse()
            # The game→Blender swap (x,-z,y) is a proper rotation, so winding is preserved.
            face = bm.faces.new([vert(p) for p in quad])
            u_axis = grain_axis if grain_axis in others else others[0]
            v_axis = [i for i in others if i != u_axis][0]
            for loop, p in zip(face.loops, quad):
                loop[uv_layer].uv = (p[u_axis] * size / 0.25, p[v_axis] * size / 0.25)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-6)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    return ob


def bevel(ob, width=0.0015, segments=2):
    """Softens every edge a little: real wood never has knife edges, and it catches the light."""
    m = ob.modifiers.new("bevel", "BEVEL")
    m.width = width
    m.segments = segments
    m.limit_method = "ANGLE"
    m.harden_normals = True
    bpy.context.view_layer.objects.active = ob
    ob.select_set(True)
    bpy.ops.object.modifier_apply(modifier="bevel")
    ob.select_set(False)
    for p in ob.data.polygons:
        p.use_smooth = True


def wood(name, tint=(1.0, 1.0, 1.0), roughness_scale=1.0, res="diff"):
    """A Principled material from a Poly Haven wood set in TEX_ROOT/<name>/ (diff, nor, rough)."""
    key = (name, tint, roughness_scale)
    if key in _materials:
        return _materials[key]
    mat = bpy.data.materials.new(f"{name}")
    mat.use_nodes = True
    nt = mat.node_tree
    bsdf = nt.nodes["Principled BSDF"]
    folder = os.path.join(TEX_ROOT, name)

    def image(file, non_color=False):
        path = os.path.join(folder, file)
        if not os.path.exists(path):
            return None
        node = nt.nodes.new("ShaderNodeTexImage")
        node.image = bpy.data.images.load(path, check_existing=True)
        if non_color:
            node.image.colorspace_settings.name = "Non-Color"
        return node

    diff = image("diff.jpg")
    if diff:
        if tint != (1.0, 1.0, 1.0):
            mix = nt.nodes.new("ShaderNodeMix")
            mix.data_type = "RGBA"
            mix.blend_type = "MULTIPLY"
            mix.inputs["Factor"].default_value = 1.0
            mix.inputs[7].default_value = (*tint, 1.0)
            nt.links.new(diff.outputs["Color"], mix.inputs[6])
            nt.links.new(mix.outputs[2], bsdf.inputs["Base Color"])
        else:
            nt.links.new(diff.outputs["Color"], bsdf.inputs["Base Color"])
    rough = image("rough.jpg", non_color=True)
    if rough:
        nt.links.new(rough.outputs["Color"], bsdf.inputs["Roughness"])
    else:
        bsdf.inputs["Roughness"].default_value = 0.55
    nor = image("nor.jpg", non_color=True)
    if nor:
        nm = nt.nodes.new("ShaderNodeNormalMap")
        nm.inputs["Strength"].default_value = 1.0
        nt.links.new(nor.outputs["Color"], nm.inputs["Color"])
        nt.links.new(nm.outputs["Normal"], bsdf.inputs["Normal"])
    _materials[key] = mat
    return mat


def metal(name):
    """A metallic material from a generated set in TEX_ROOT/<name>/ (make_metal_tex.py)."""
    key = ("metal", name)
    if key in _materials:
        return _materials[key]
    mat = wood(name)
    mat.name = name
    mat.node_tree.nodes["Principled BSDF"].inputs["Metallic"].default_value = 1.0
    _materials[key] = mat
    return mat


def flat(name, color, roughness=0.5, metallic=0.0, emission=None):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    bsdf = mat.node_tree.nodes["Principled BSDF"]
    bsdf.inputs["Base Color"].default_value = (*color, 1.0)
    bsdf.inputs["Roughness"].default_value = roughness
    bsdf.inputs["Metallic"].default_value = metallic
    if emission:
        bsdf.inputs["Emission Color"].default_value = (*emission, 1.0)
        bsdf.inputs["Emission Strength"].default_value = 1.0
    return mat


def assign(ob, mat):
    ob.data.materials.clear()
    ob.data.materials.append(mat)


def export(path, objects):
    """Writes [objects] (each a named node) to one GLB, with materials and tangents."""
    for ob in objects:
        # Tangents need triangles or quads; text and booleans leave n-gons.
        m = ob.modifiers.new("tri", "TRIANGULATE")
        bpy.context.view_layer.objects.active = ob
        bpy.ops.object.modifier_apply(modifier="tri")
    bpy.ops.object.select_all(action="DESELECT")
    for ob in objects:
        ob.select_set(True)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    bpy.ops.export_scene.gltf(
        filepath=path,
        export_format="GLB",
        use_selection=True,
        export_yup=True,
        export_tangents=True,
        export_image_format="JPEG",
        export_jpeg_quality=88,
    )
    print("wrote", path, sum(len(o.data.polygons) for o in objects), "faces")


FONT = os.environ.get("LUBAN_FONT", os.path.join(TEX_ROOT, "..", "fonts", "NotoSerifSC-Bold.otf"))


def box(name, size, center, mat, bevel_width=0.002, uv_scale=4.0):
    """A beveled box: [size] (game frame, meters) centered at [center], wood UVs by cube projection."""
    bpy.ops.mesh.primitive_cube_add(size=1)
    ob = bpy.context.object
    ob.name = name
    sx, sy, sz = size
    ob.scale = (sx, sz, sy)
    ob.location = to_blender(center)
    bpy.ops.object.transform_apply(location=True, scale=True)
    if bevel_width > 0:
        bevel(ob, width=bevel_width, segments=3)
    cube_uv(ob, uv_scale)
    assign(ob, mat)
    return ob


def cube_uv(ob, scale=4.0):
    bpy.context.view_layer.objects.active = ob
    ob.select_set(True)
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.cube_project(cube_size=1.0 / scale)
    bpy.ops.object.mode_set(mode="OBJECT")
    ob.select_set(False)


def subtract(ob, cutter):
    m = ob.modifiers.new("cut", "BOOLEAN")
    m.operation = "DIFFERENCE"
    m.object = cutter
    m.solver = "EXACT"
    bpy.context.view_layer.objects.active = ob
    bpy.ops.object.modifier_apply(modifier="cut")
    bpy.data.objects.remove(cutter)


def join(objects, name):
    bpy.ops.object.select_all(action="DESELECT")
    for o in objects:
        o.select_set(True)
    bpy.context.view_layer.objects.active = objects[0]
    bpy.ops.object.join()
    ob = bpy.context.object
    ob.name = name
    return ob


def carve_text(block, text, top, size, mat, columns=False, depth=0.0):
    """Lays [text] on the top face of [block] (top face height top[1], centered at top x, z) as a
    thin gold-leaf inlay standing 0.2 mm proud of the wood, joined to [block]. (Cutting the letters
    in with booleans left shading streaks across the smooth-shaded top.) Returns [block]."""
    global _font
    if _font is None:
        _font = bpy.data.fonts.load(FONT)
    objs = []
    n = len(text)
    for k, ch in enumerate(text):
        bpy.ops.object.text_add()
        t = bpy.context.object
        t.data.body = ch
        t.data.font = _font
        t.data.size = size
        t.data.align_x = "CENTER"
        t.data.align_y = "CENTER"
        t.data.extrude = 0.0002
        off = (k - (n - 1) / 2) * size * 1.02
        x, y = (off, 0) if not columns else (0, -off)
        t.location = (x + top[0], y - top[2], top[1] + 0.0002)
        bpy.ops.object.convert(target="MESH")
        objs.append(bpy.context.object)
    inlay = join(objs, block.name + "_inlay")
    for p in inlay.data.polygons:
        p.use_smooth = False
    assign(inlay, mat)
    if mat.name not in [m.name for m in block.data.materials]:
        block.data.materials.append(mat)
    return join([block, inlay], block.name)


# ---- More shapes (game frame) -------------------------------------------------------------------

def _axis_rotation(axis):
    """Blender euler that turns a primitive's own axis (Blender z = game y) onto game [axis]."""
    import math
    return {"y": (0, 0, 0), "x": (0, math.pi / 2, 0), "z": (math.pi / 2, 0, 0)}[axis]


def _finish(ob, name, mat, uv=4.0, smooth=True):
    ob.name = name
    bpy.context.view_layer.objects.active = ob
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    if smooth:
        for p in ob.data.polygons:
            p.use_smooth = True
    cube_uv(ob, uv)
    assign(ob, mat)
    return ob


def cylinder(name, radius, depth, center, mat, axis="y", verts=48, bevel_width=0.0):
    bpy.ops.mesh.primitive_cylinder_add(radius=radius, depth=depth, vertices=verts)
    ob = bpy.context.object
    ob.rotation_euler = _axis_rotation(axis)
    ob.location = to_blender(center)
    ob = _finish(ob, name, mat)
    if bevel_width > 0:
        bevel(ob, width=bevel_width, segments=2)
    return ob


def torus(name, major, minor, center, mat, axis="y", segments=64):
    bpy.ops.mesh.primitive_torus_add(major_radius=major, minor_radius=minor, major_segments=segments, minor_segments=12)
    ob = bpy.context.object
    ob.rotation_euler = _axis_rotation(axis)
    ob.location = to_blender(center)
    return _finish(ob, name, mat)


def sphere(name, radius, center, mat, segments=32):
    bpy.ops.mesh.primitive_uv_sphere_add(radius=radius, segments=segments, ring_count=segments // 2)
    ob = bpy.context.object
    ob.location = to_blender(center)
    return _finish(ob, name, mat)


def tube(name, points, radius, mat, closed=False):
    """A round rod along [points] (game frame)."""
    cu = bpy.data.curves.new(name, "CURVE")
    cu.dimensions = "3D"
    cu.bevel_depth = radius
    cu.bevel_resolution = 3
    cu.use_fill_caps = True
    sp = cu.splines.new("POLY")
    sp.points.add(len(points) - 1)
    for p, q in zip(sp.points, points):
        p.co = (*to_blender(q), 1)
    sp.use_cyclic_u = closed
    ob = bpy.data.objects.new(name, cu)
    bpy.context.scene.collection.objects.link(ob)
    bpy.context.view_layer.objects.active = ob
    ob.select_set(True)
    bpy.ops.object.convert(target="MESH")
    ob = bpy.context.object
    ob.select_set(False)
    return _finish(ob, name, mat)


def arc(center, radius, a0, a1, plane="yz", n=32):
    """Points on a circular arc from angle a0 to a1 (degrees) in a plane through [center]:
    "yz" (angle from +z toward +y), "xy" (from +x toward +y), "xz" (from +x toward +z)."""
    import math
    pts = []
    for k in range(n + 1):
        a = math.radians(a0 + (a1 - a0) * k / n)
        c, s = math.cos(a) * radius, math.sin(a) * radius
        off = {"yz": (0, s, c), "xy": (c, s, 0), "xz": (c, 0, s)}[plane]
        pts.append(tuple(center[i] + off[i] for i in range(3)))
    return pts


def turn(ob, axis, degrees, pivot=(0, 0, 0)):
    """Turns [ob] (applied) by [degrees] about game [axis] through game [pivot]."""
    import math
    from mathutils import Matrix
    bl_axis = {"x": "X", "y": "Z", "z": "Y"}[axis]
    sign = -1 if axis == "z" else 1
    p = to_blender(pivot)
    m = Matrix.Translation(p) @ Matrix.Rotation(math.radians(degrees) * sign, 4, bl_axis) @ Matrix.Translation((-p[0], -p[1], -p[2]))
    ob.data.transform(m)
    ob.data.update()
    return ob


def move(ob, offset):
    from mathutils import Matrix
    ob.data.transform(Matrix.Translation(to_blender(offset)))
    ob.data.update()
    return ob


def text_mesh(name, text, size, center, facing="+z", mat=None, depth=0.0004, columns=False):
    """Gold-leaf letters standing [depth] proud, centered at [center], facing game +z / -z / +y,
    upright (their top toward +y, or toward -z when facing +y)."""
    import math
    global _font
    if _font is None:
        _font = bpy.data.fonts.load(FONT)
    objs = []
    n = len(text)
    for k, ch in enumerate(text):
        bpy.ops.object.text_add()
        t = bpy.context.object
        t.data.body = ch
        t.data.font = _font
        t.data.size = size
        t.data.align_x = "CENTER"
        t.data.align_y = "CENTER"
        t.data.extrude = depth / 2
        off = (k - (n - 1) / 2) * size * 1.02
        t.location = (off, 0, 0) if not columns else (0, -off, 0)
        bpy.ops.object.convert(target="MESH")
        objs.append(bpy.context.object)
    ob = join(objs, name)
    bpy.ops.object.transform_apply(location=True)
    # Now lying in Blender XY (facing game +y, top toward game -z); stand it up as asked.
    if facing == "+z":
        turn(ob, "x", 90)
    elif facing == "-z":
        turn(ob, "x", 90)
        turn(ob, "y", 180)
    move(ob, center)
    for p in ob.data.polygons:
        p.use_smooth = False
    if mat is not None:
        assign(ob, mat)
    return ob
