"""The Gauntlet (the Crystal Nucleus's second style, 2026-10-28): a blocky gold gauntlet, left hand, built from boxes in
mod pixels (x right, y up, z towards the camera), mirrored into Blender as (x, -z, y). Run inside Blender:
exec(open(this).read(), g) with g = {"GAUNTLET_DIR": <folder with tex/ - gold_block, raw_gold_block, polished_blackstone>}.
Then export_vault.py with OUT_JSON = .../assets/butcher/nucleus_gauntlet.json and NOTE.

Held up with its back to the camera at yaw 0: the thumb on +x (a left hand), the fingers up +y, the palm on -z. Every
finger is three parts (a segment each, its pivot at its knuckle) so it can clench and snap; the thumb is two. The six
stones' sockets are parts with no boxes - SockThumb, SockIndex, SockMiddle, SockRing, SockPinky, SockCentre - their
pivot the stone's middle and their "slide" the way the stone faces, so the game puts each crystal where it belongs. The
four finger stones sit on the knuckle band above their fingers (the user, 2026-10-28), so they stay put when the hand
closes; the thumb's rides on the thumb's first segment, the centre's on the back of the hand. The helpers are build_vault.py's, copied (both run inside Blender with nothing on their path).
Minecraft's own blocks for the faces, a texel a pixel, Minecraft's face shading in game (MC)."""
import bpy, bmesh, math, os
from mathutils import Vector

HERE = globals().get("GAUNTLET_DIR") or os.path.join(os.path.dirname(bpy.data.filepath) or os.getcwd(), "gauntlet")
TEX = os.path.join(HERE, "tex")

# ---------------------------------------------------------------- design
KIND = {  # kind -> (texture, tint)
    "gold": ("gold_block", (1, 1, 1)),
    "trim": ("raw_gold_block", (1, 1, 1)),
    "dark": ("polished_blackstone", (1, 1, 1)),
}
MC = set(KIND)
GX = 0
GLOW = {}
# finger -> (x middle, knuckle y, segment lengths): the index on +x beside the thumb (a left hand, back to the camera)
FINGERS = {
    "Index": (12.4, 28, (12, 9, 7)),
    "Middle": (4.1, 28, (13, 10, 8)),
    "Ring": (-4.1, 28, (12, 9, 7)),
    "Pinky": (-12.4, 28, (10, 7, 6)),
}
FW, FD = 3.7, 4.4              # a finger's half width and half depth (the user: fatter, 2026-10-28)
HW, HD = 18.0, 10.5            # the hand's half width and half depth
KY = 24.5                      # the knuckle band's middle, where the four finger stones sit (the user: on the knuckles)
THUMB = (20.0, 9.0)            # the thumb's knuckle, on the pad at the hand's +x side
THUMB_ROT = -0.7               # its rest lean, outwards (radians about z)

# ---------------------------------------------------------------- a clean file, the stage
bpy.ops.wm.read_homefile(use_empty=True)
scene = bpy.context.scene
col = bpy.data.collections.new("Gauntlet")
scene.collection.children.link(col)
world = bpy.data.worlds.new("World")
scene.world = world
world.use_nodes = True
bg = world.node_tree.nodes["Background"]
bg.inputs[0].default_value = (0.05, 0.05, 0.06, 1)
bg.inputs[1].default_value = 4

def to_b(p): return Vector((p[0], -p[2], p[1]))

sun = bpy.data.objects.new("Key", bpy.data.lights.new("Key", "SUN"))
sun.data.energy = 3.0
sun.rotation_euler = (math.radians(50), 0, math.radians(-30))
scene.collection.objects.link(sun)

def camera(name, target, rot, scale, dist=300):
    cam = bpy.data.objects.new(name, bpy.data.cameras.new(name))
    cam.data.type = "ORTHO"
    cam.data.ortho_scale = scale
    cam.data.clip_end = 2000
    cam.rotation_euler = tuple(math.radians(v) for v in rot)
    fwd = cam.rotation_euler.to_matrix() @ Vector((0, 0, -1))
    cam.location = to_b(target) - fwd * dist
    scene.collection.objects.link(cam)
    return cam

camera("CamBack", (2, 6, 0), (86, 0, 0), 140)          # the back of the hand, as the game sees it at yaw 0
camera("CamThumb", (6, 8, 0), (86, 0, 62), 140)         # from the thumb's side, where it starts
camera("CamThree", (2, 8, 0), (78, 0, 28), 140)
scene.camera = bpy.data.objects["CamBack"]

r = scene.render
r.engine = "BLENDER_EEVEE"
r.resolution_x, r.resolution_y = 900, 1100
r.use_freestyle = True
r.line_thickness = 2.0
scene.view_settings.view_transform = "Standard"
vl = scene.view_layers[0]
ls = vl.freestyle_settings.linesets[0] if vl.freestyle_settings.linesets else vl.freestyle_settings.linesets.new("Outline")
ls.select_silhouette = False; ls.select_border = False; ls.select_crease = False
ls.select_external_contour = True

# ---------------------------------------------------------------- helpers (build_vault.py's)
MATS = {}
def mat(name, tint):
    key = (name, tint)
    if key in MATS: return MATS[key]
    m = bpy.data.materials.new(name if tint == (1, 1, 1) else name + "_t")
    m.use_nodes = True
    nt = m.node_tree; bsdf = nt.nodes["Principled BSDF"]
    tn = nt.nodes.new("ShaderNodeTexImage")
    tn.image = bpy.data.images.load(os.path.join(TEX, name + ".png"), check_existing=True); tn.interpolation = "Closest"
    nt.links.new(tn.outputs["Color"], bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 1.0
    for k in ("Specular IOR Level", "Specular"):
        if k in bsdf.inputs: bsdf.inputs[k].default_value = 0.0
    MATS[key] = m
    return m

BOXES = []
def box(part, kind, x0, x1, y0, y1, z0, z1, rot=0.0, origin=(0, 0)):
    """One box in mod pixels into the part's mesh; rot turns it about mod Z through origin. UVs a texel a pixel."""
    BOXES.append((part.name, kind, ((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2), ((x1 - x0) / 2, (y1 - y0) / 2, (z1 - z0) / 2), rot, origin))
    bm = PARTS[part.name]
    uv = bm.loops.layers.uv.active
    c, s = math.cos(rot), math.sin(rot)
    inv = part["pivot"]
    def P(x, y, z):
        if rot:
            dx, dy = x - origin[0], y - origin[1]
            x, y = origin[0] + dx * c - dy * s, origin[1] + dx * s + dy * c
        return to_b((x - inv[0], y - inv[1], z - inv[2]))
    faces = [
        ([(x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (x1, y0, z1)], "zy"),
        ([(x0, y0, z1), (x0, y1, z1), (x0, y1, z0), (x0, y0, z0)], "zy"),
        ([(x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0)], "xz"),
        ([(x0, y0, z1), (x0, y0, z0), (x1, y0, z0), (x1, y0, z1)], "xz"),
        ([(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)], "xy"),
        ([(x1, y0, z0), (x0, y0, z0), (x0, y1, z0), (x1, y1, z0)], "xy"),
    ]
    t, tint = KIND[kind]
    m = mat(t, tint)
    mats = part.data.materials
    if m.name not in [x.name for x in mats]: mats.append(m)
    mi = [x.name for x in mats].index(m.name)
    for corners, ax in faces:
        f = bm.faces.new([bm.verts.new(P(*q)) for q in corners])
        f.material_index = mi
        for loop, q in zip(f.loops, corners):
            a = {"x": q[0], "y": q[1], "z": q[2]}
            loop[uv].uv = (a[ax[0]] / 16.0, a[ax[1]] / 16.0)

PARTS = {}
def part(name, pivot, parent=None):
    """A moving part: one mesh object whose origin is its pivot (mod pixels), parented without offset."""
    me = bpy.data.meshes.new(name)
    ob = bpy.data.objects.new(name, me); col.objects.link(ob)
    ob["pivot"] = pivot
    if parent:
        ob.parent = parent
        pp = parent["pivot"]
        ob.location = to_b((pivot[0] - pp[0], pivot[1] - pp[1], pivot[2] - pp[2]))
    else:
        ob.location = to_b(pivot)
    bm = bmesh.new(); bm.loops.layers.uv.new(); PARTS[name] = bm
    return ob

def socket(name, at, facing, parent):
    """Where a stone sits: a part with no boxes, its pivot the stone's middle, its "slide" the way the stone faces."""
    s = part(name, at, parent)
    s["slide"] = facing
    return s

def ring(p, x0, x1, y0, y1, z0, z1, w):
    """A square rim, w thick, round an opening (the socket's setting)."""
    box(p, "trim", x0, x1, y1 - w, y1, z0, z1)
    box(p, "trim", x0, x1, y0, y0 + w, z0, z1)
    box(p, "trim", x0, x0 + w, y0 + w, y1 - w, z0, z1)
    box(p, "trim", x1 - w, x1, y0 + w, y1 - w, z0, z1)
    box(p, "dark", x0 + w, x1 - w, y0 + w, y1 - w, z0, z0 + 0.4)

# ---------------------------------------------------------------- the build
static = part("Static", (0, 0, 0))

def disc(p, kind, cx, cy, r, z0, z1):
    """A disc facing the camera, a row of boxes a pixel high: a round setting, a well."""
    for yb in range(-int(r), int(r)):
        yc = yb + 0.5
        w = round(math.sqrt(max(0.0, r * r - yc * yc)))
        if w > 0: box(p, kind, cx - w, cx + w, cy + yb, cy + yb + 1, z0, z1)

# The cuff: a broad flared bracer in three steps, rims at the top and the foot, a raised spine and chevrons.
box(static, "gold", -25, 25, -44, -35, -17, 17)
box(static, "gold", -22, 22, -35, -22, -15, 15)
box(static, "gold", -19.5, 19.5, -22, -8, -13, 13)
box(static, "trim", -26, 26, -45, -42, -17.6, 17.6)          # the foot's rim
box(static, "trim", -20.5, 20.5, -10, -7, -13.6, 13.6)       # the top's rim
box(static, "trim", -3, 3, -42, -35, 17, 18.6)               # the spine down the front, stepping in with the cuff
box(static, "trim", -3, 3, -35, -22, 15, 16.8)
box(static, "trim", -3, 3, -22, -10, 13, 14.6)
for y, z in ((-40, 18.6), (-32, 16.8), (-27, 16.8), (-19, 14.6), (-14, 14.6)):
    box(static, "dark", -1.8, 1.8, y, y + 1, z, z + 0.3)     # its ribs
for side in (-1, 1):
    for y in (-40, -31, -25, -17):                           # chevrons up each side of the spine, leaning out
        xa, xb = (5, 13) if side > 0 else (-13, -5)
        z0 = 13 if y >= -22 else (15 if y >= -35 else 17)
        box(static, "trim", xa, xb, y, y + 2.2, z0 - 0.4, z0 + 0.9, rot=side * 0.32, origin=(side * 5, y + 1))
# The hand: broad and deep - the back plate with a raised panel round the centre socket, the knuckle band, the wrist's
# joint, the thumb's pad on the +x side.
box(static, "gold", -HW, HW, -8, KY + 3, -HD, HD)
box(static, "gold", -14, 14, -4, KY - 4, HD, HD + 1)
box(static, "trim", -HW - 1, HW + 1, -8, -5, -HD - 0.6, HD + 0.6)
box(static, "trim", -HW - 1, HW + 1, KY - 3.5, KY + 3.5, -HD - 0.8, HD + 1.6)   # the knuckle band, standing proud
box(static, "gold", HW - 2, HW + 6, -6, 11, -8, 8)              # the thumb's pad
box(static, "trim", HW - 2, HW + 6.4, -6, -4, -8.4, 8.4)
# The four finger stones: on the knuckle band, one over each finger, each in its own round setting.
for name, (cx, ky, lens) in FINGERS.items():
    disc(static, "trim", cx, KY, 3.4, HD + 1.6, HD + 2.6)
    disc(static, "dark", cx, KY, 2.4, HD + 2.6, HD + 2.8)
    socket(f"Sock{name}", (cx, KY, HD + 4.6), (0, 0, 1), static)
# The centre socket: a big round setting, raised, a dark well, wings either side and points above and below.
CX, CY = 0.0, 7.5
disc(static, "trim", CX, CY, 10, HD + 1, HD + 2.6)
disc(static, "dark", CX, CY, 7.5, HD + 2.6, HD + 2.8)
for side in (-1, 1):
    xa, xb = (10, 17) if side > 0 else (-17, -10)
    box(static, "trim", xa, xb, CY - 2.5, CY + 2.5, HD + 1, HD + 2.2)                  # the wings
    xa, xb = (14, 16.5) if side > 0 else (-16.5, -14)
    box(static, "trim", xa, xb, CY - 6, CY + 6, HD + 1, HD + 2)
box(static, "trim", CX - 2, CX + 2, CY + 10, CY + 13, HD + 1, HD + 2.2)                 # the point below the knuckles
box(static, "trim", CX - 2, CX + 2, CY - 14, CY - 10, HD + 1, HD + 2.2)
socket("SockCentre", (CX, CY, HD + 3.4), (0, 0, 1), static)
for x in (-12, 12):                                          # rivets
    for y in (-2, 16):
        box(static, "dark", x - 0.7, x + 0.7, y - 0.7, y + 0.7, HD + 1, HD + 1.4)

# The fingers: three segments each over the knuckle band, a gold band at every joint, rounded tips.
for name, (cx, ky, lens) in FINGERS.items():
    y = ky
    parent = static
    for i, ln in enumerate(lens):
        seg = part(f"{name}{i}", (cx, y, 0), parent)
        tip = i == len(lens) - 1
        box(seg, "gold", cx - FW, cx + FW, y, y + ln - (1.6 if tip else 0), -FD, FD)
        if tip:
            box(seg, "gold", cx - FW + 1, cx + FW - 1, y + ln - 1.6, y + ln, -FD + 1, FD - 1)       # the rounded tip
        box(seg, "trim", cx - FW - 0.4, cx + FW + 0.4, y, y + 1.4, -FD - 0.4, FD + 0.4)          # the joint's band
        parent = seg
        y += ln

# The thumb: two thick segments leaning out from its pad, its stone on the outer face of the first.
tx, ty = THUMB
c, s_ = math.cos(THUMB_ROT), math.sin(THUMB_ROT)
def lean(x, y):
    dx, dy = x - tx, y - ty
    return (tx + dx * c - dy * s_, ty + dx * s_ + dy * c)
TW = 4.2
th0 = part("Thumb0", (tx, ty, 0), static)
box(th0, "gold", tx - TW, tx + TW, ty, ty + 11, -FD - 0.6, FD + 0.6, rot=THUMB_ROT, origin=(tx, ty))
box(th0, "trim", tx - TW - 0.4, tx + TW + 0.4, ty, ty + 1.4, -FD - 1, FD + 1, rot=THUMB_ROT, origin=(tx, ty))
for (y0, y1) in ((ty + 2.5, ty + 3.4), (ty + 8.1, ty + 9.0)):
    box(th0, "trim", tx + TW, tx + TW + 1.1, y0, y1, -2.8, 2.8, rot=THUMB_ROT, origin=(tx, ty))
box(th0, "trim", tx + TW, tx + TW + 1.1, ty + 3.4, ty + 8.1, -2.8, -1.9, rot=THUMB_ROT, origin=(tx, ty))
box(th0, "trim", tx + TW, tx + TW + 1.1, ty + 3.4, ty + 8.1, 1.9, 2.8, rot=THUMB_ROT, origin=(tx, ty))
box(th0, "dark", tx + TW, tx + TW + 0.4, ty + 3.4, ty + 8.1, -1.9, 1.9, rot=THUMB_ROT, origin=(tx, ty))
sx, sy = lean(tx + TW + 2.2, ty + 5.75)
socket("SockThumb", (sx, sy, 0), (c, s_, 0), th0)
jx, jy = lean(tx, ty + 11)
th1 = part("Thumb1", (jx, jy, 0), th0)
box(th1, "gold", jx - TW + 0.4, jx + TW - 0.4, jy, jy + 7.5, -FD, FD, rot=THUMB_ROT, origin=(jx, jy))
box(th1, "gold", jx - TW + 1.4, jx + TW - 1.4, jy + 7.5, jy + 9, -FD + 1, FD - 1, rot=THUMB_ROT, origin=(jx, jy))
box(th1, "trim", jx - TW, jx + TW, jy, jy + 1.4, -FD - 0.4, FD + 0.4, rot=THUMB_ROT, origin=(jx, jy))

# ---------------------------------------------------------------- the meshes
for name, bm in PARTS.items():
    me = bpy.data.objects[name].data
    bm.to_mesh(me); bm.free()
RESULT = {"boxes": len(BOXES), "parts": list(PARTS.keys())}
