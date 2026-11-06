"""The Crystal Nucleus vault door, built from boxes in mod pixels (x right, y up, z towards the camera), mirrored into
Blender as (x, -z, y). Run inside Blender: exec(open(this).read()). The parameters at the top are the design."""
import bpy, bmesh, math, os
from mathutils import Vector, Matrix

# Where the renders and the .blend go, with the block textures under tex/ (vault_textures.py) - set VAULT_DIR before
# exec(), or this falls back to a "vault" folder beside the open .blend. Never commit the textures: they are Mojang's,
# read from the local client jar for previews only.
HERE = globals().get("VAULT_DIR") or os.path.join(os.path.dirname(bpy.data.filepath) or os.getcwd(), "vault")
TEX = os.path.join(HERE, "tex")

# ---------------------------------------------------------------- design
G = 2                  # the pixel-art step of every circle
HOLE, FRAME_O = 34, 44 # the opening and the frame ring round it
DOOR_R = 32            # the door (a 2 px seam inside the frame)
DOOR_Z = (-4, 7)       # its back and its face: 2 px proud of the frame
WALL = 60              # half the wall
HX, HZ = -47, 7        # the hinge axis (vertical), on the left, level with the door's face
SR = 20                # the socket ring's radius

KIND = {  # kind -> (texture, tint)
    # The wall and the floor: the Dwarven Mines' forge's palette (the user's screenshot, 2026-10-27), untinted.
    "bricks": ("stone_bricks", (1, 1, 1)),
    "cracked": ("cracked_stone_bricks", (1, 1, 1)),
    "red": ("bricks", (1, 1, 1)),
    "terracotta": ("terracotta", (1, 1, 1)),
    "acacia": ("acacia_planks", (1, 1, 1)),
    "gold": ("gold_block", (1, 1, 1)),
    "floor": ("stone", (1, 1, 1)),
    "rim_slab": ("smooth_stone", (1, 1, 1)),
    "jade": ("lime_stained_glass", (1, 1, 1)),
    "amber": ("orange_stained_glass", (1, 1, 1)),
    "topaz": ("yellow_stained_glass", (1, 1, 1)),
    "sapphire": ("light_blue_stained_glass", (1, 1, 1)),
    "amethyst": ("purple_stained_glass", (1, 1, 1)),
    "frame": ("netherite_block", (1, 1, 1)),
    "tunnel": ("polished_blackstone", (0.7, 0.7, 0.7)),
    "back": ("blackstone", (0.45, 0.45, 0.45)),
    "door": ("iron_block", (0.44, 0.46, 0.52)),
    "rim": ("iron_block", (0.34, 0.36, 0.42)),
    "rivet": ("polished_deepslate", (1, 1, 1)),
    "socket": ("gold_block", (1, 1, 1)),
    "socket_hole": ("chiseled_polished_blackstone", (0.55, 0.55, 0.6)),
    "housing": ("polished_deepslate", (1, 1, 1)),
    "bolt": ("gold_block", (1, 1, 1)),
    "dial": ("gold_block", (1, 1, 1)),
    "notch": ("polished_blackstone", (1, 1, 1)),
    "wheel": ("polished_deepslate", (1, 1, 1)),
    "knob": ("raw_gold_block", (1, 1, 1)),
    "hinge": ("netherite_block", (0.85, 0.85, 0.85)),
    "strap": ("polished_deepslate", (1, 1, 1)),
    "frame_step": ("netherite_block", (0.72, 0.72, 0.72)),
    "frame_lip": ("polished_deepslate", (0.9, 0.9, 0.95)),
    "stud": ("iron_block", (0.5, 0.52, 0.58)),
    "keeper": ("netherite_block", (0.9, 0.9, 0.9)),
    "keeper_rim": ("polished_deepslate", (0.8, 0.8, 0.85)),
    "rail": ("iron_block", (0.4, 0.42, 0.48)),
    "collar": ("gold_block", (0.85, 0.85, 0.85)),
    "groove": ("polished_blackstone", (0.6, 0.6, 0.6)),
    "pin": ("gold_block", (1, 1, 1)),
    "leaf": ("netherite_block", (0.78, 0.78, 0.78)),
}

# The kinds drawn as Minecraft draws a block (its own face shading, no tint, whole blocks on the grid) - the wall, the
# floor and what is built into them. The machined door and frame keep their own light.
MC = {"bricks", "cracked", "red", "terracotta", "acacia", "gold", "floor", "rim_slab",
      "jade", "amber", "topaz", "sapphire", "amethyst"}

# ---------------------------------------------------------------- helpers
for o in list(bpy.data.objects):
    if o.type != "CAMERA" and o.type != "LIGHT":
        bpy.data.objects.remove(o, do_unlink=True)
for m in list(bpy.data.meshes): bpy.data.meshes.remove(m)
for m in list(bpy.data.materials): bpy.data.materials.remove(m)
col = bpy.data.collections.get("Vault") or bpy.data.collections.new("Vault")
if col.name not in bpy.context.scene.collection.children: bpy.context.scene.collection.children.link(col)

MATS = {}
def mat(name, tint):
    key = (name, tint)
    if key in MATS: return MATS[key]
    m = bpy.data.materials.new(name if tint == (1, 1, 1) else name + "_t")
    m.use_nodes = True
    nt = m.node_tree; bsdf = nt.nodes["Principled BSDF"]
    tn = nt.nodes.new("ShaderNodeTexImage")
    tn.image = bpy.data.images.load(os.path.join(TEX, name + ".png"), check_existing=True); tn.interpolation = "Closest"
    if tint != (1, 1, 1):
        mix = nt.nodes.new("ShaderNodeMix"); mix.data_type = "RGBA"; mix.blend_type = "MULTIPLY"
        mix.inputs["Factor"].default_value = 1.0
        nt.links.new(tn.outputs["Color"], mix.inputs[6]); mix.inputs[7].default_value = (*tint, 1)
        nt.links.new(mix.outputs[2], bsdf.inputs["Base Color"])
    else:
        nt.links.new(tn.outputs["Color"], bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 1.0
    if name.endswith("stained_glass"):
        # The veins glow: their own colour, brightened (the game will light them per crystal).
        nt.links.new(tn.outputs["Color"], bsdf.inputs["Emission Color"])
        bsdf.inputs["Emission Strength"].default_value = 2.2
    for k in ("Specular IOR Level", "Specular"):
        if k in bsdf.inputs: bsdf.inputs[k].default_value = 0.0
    MATS[key] = m
    return m

def to_b(p): return Vector((p[0], -p[2], p[1]))

BOXES = []   # every box, for the bake: (part, kind, centre, half sizes, rot about z, origin, alpha)
def box(part, kind, x0, x1, y0, y1, z0, z1, rot=0.0, origin=(0, 0), alpha=1.0):
    """One box in mod pixels into the part's mesh; rot turns it about mod Z through origin. UVs a texel a pixel.
    alpha: how much of it shows - the wall and floor fade out at their edges, block by block (the game applies it)."""
    BOXES.append((part.name, kind, ((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2), ((x1 - x0) / 2, (y1 - y0) / 2, (z1 - z0) / 2), rot, origin, alpha))
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
        # A block's texture starts at its own edge: the wall's grid is GX off the door's middle in x.
        shift = GX if kind in MC else 0
        for loop, q in zip(f.loops, corners):
            a = {"x": q[0] + shift, "y": q[1], "z": q[2]}
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

def ring_rows(ro, ri, clip=None):
    out = []
    lim = clip if clip else ro
    for yb in range(-lim, lim, G):
        yc = yb + G / 2
        if clip: wo = clip
        else:
            if abs(yc) >= ro: continue
            wo = round(math.sqrt(ro * ro - yc * yc) / G) * G
        if wo <= 0: continue
        if ri and abs(yc) < ri:
            wi = round(math.sqrt(ri * ri - yc * yc) / G) * G
            segs = [(-wo, -wi), (wi, wo)] if wi < wo else []
        else:
            segs = [(-wo, wo)]
        out.append((yb, yb + G, segs))
    return out

GX = 8                  # the block grid's x offset: blocks centred on the door's middle (a keystone on x 0)
FLOOR_Y = -48           # the floor's top, on the grid: the frame (r 44) stands just above it
WALL_X, WALL_TOP = 160, 112   # the wall's half width and its top
PIL = ((-120, -72), (72, 120))   # the two pilasters, three blocks wide each

def fade(e, i, j):
    """A block's share of the edge: solid inside, then out in quarter steps over the last stretch, each block its own
    share of it (the Revenant's plot - the user) - by a seeded number per block so it is the same every bake."""
    import random
    ragged = 0.86 + 0.14 * random.Random(i * 7919 + j * 104729 + 17).random()
    f = max(0.0, min(1.0, (ragged - e) / 0.22))
    return round(f * 4) / 4

def dwarven_wall(p):
    """The wall round the door, in the Dwarven Mines' forge's palette and build (the user's screenshot): stone bricks on
    a block grid centred on the door, a cracked one here and there; two red-brick pilasters on stone-brick bases with a
    gold band and terracotta capitals under acacia slabs, a stained-glass window of each crystal's colour in them; a
    stepped stone-brick arch over the frame with a topaz keystone. Blocks the frame covers break into halves, quarters
    and eighths behind it, so the opening stays clear. The wall fades out at its top and sides, never along the floor."""
    T = 16
    def clear(x0, y0, size):
        return min(math.hypot(x0 + dx, y0 + dy) for dx in (0, size) for dy in (0, size)) >= HOLE + 1
    def block(x0, y0, size, kind, alpha):
        if clear(x0, y0, size):
            front = 0
            # under the frame's step it sits back, or it pokes through
            if min(math.hypot(x0 + dx, y0 + dy) for dx in (0, size) for dy in (0, size)) < FRAME_O + 1: front = -0.5
            box(p, kind, x0, x0 + size, y0, y0 + size, -16, front, alpha=alpha)
        elif size > 2:
            h = size // 2
            for dx in (0, h):
                for dy in (0, h):
                    block(x0 + dx, y0 + dy, h, kind, alpha)
    def in_pilaster(x0, y0):
        return any(a <= x0 < b for a, b in PIL) and y0 < 104
    for i, x0 in enumerate(range(-WALL_X - GX, WALL_X, T)):
        for j, y0 in enumerate(range(FLOOR_Y, WALL_TOP, T)):
            if max(math.hypot(x0 + dx, y0 + dy) for dx in (0, T) for dy in (0, T)) < HOLE + 1: continue
            if in_pilaster(x0, y0): continue
            cx, cy = x0 + T / 2, y0 + T / 2
            e = math.hypot(cx / (WALL_X + 8), max(0.0, cy - 24) / 96)
            a = fade(e, i, j)
            if a <= 0: continue
            kind = "cracked" if (i * 5 + j * 3) % 9 == 0 else "bricks"
            block(x0, y0, T, kind, a)
    # The pilasters, a block proud of the wall.
    windows = {(-104, -32): "amber", (-104, -16): "amber", (-104, 16): "amethyst", (-104, 32): "amethyst",
               (88, -32): "sapphire", (88, -16): "sapphire", (88, 16): "jade", (88, 32): "jade"}
    for a0, a1 in PIL:
        for x0 in range(a0, a1, T):
            box(p, "bricks", x0, x0 + T, FLOOR_Y, FLOOR_Y + T, 0, 16)                       # the base
            for y0 in range(FLOOR_Y + T, 48, T):
                kind = windows.get((x0, y0)) or ("gold" if y0 == 0 else "red")
                box(p, kind, x0, x0 + T, y0, y0 + T, 0, 16)
            box(p, "terracotta", x0, x0 + T, 48, 64, 0, 16)                                   # the capital
            for y0 in range(72, 104, T):
                box(p, "red", x0, x0 + T, y0, y0 + T, 0, 16)
        box(p, "acacia", a0 - T, a1 + T, 64, 72, -16, 16)                                     # the slab over it
    # The arch over the frame: two stepped rows of stone bricks, the topaz window its keystone, a terracotta slab on top.
    for y0, half in ((48, 56), (64, 40)):
        for x0 in range(-half, half, T):
            box(p, "topaz" if x0 == -GX else "bricks", x0, x0 + T, y0, y0 + T, 0, 16)
    box(p, "terracotta", -40, 40, 80, 88, 0, 16)

def floor_ledge(p):
    """The floor before the door, stone blocks on the same grid, and a rim of smooth stone slabs round the patch the
    haul lands in (the forge's pit, in the screenshot). Solid behind the pile and along the wall; fading at its sides
    and its front only."""
    T = 16
    for i, x0 in enumerate(range(-WALL_X - GX, WALL_X, T)):
        for j, z0 in enumerate(range(0, 112, T)):
            cx, cz = x0 + T / 2, z0 + T / 2
            e = math.hypot(cx / (WALL_X + 8), max(0.0, cz - 64) / 48)
            a = fade(e, i, j + 40)
            if a <= 0: continue
            box(p, "floor", x0, x0 + T, FLOOR_Y - T, FLOOR_Y, z0, z0 + T, alpha=a)
    for x0 in range(-72, 72, T):
        box(p, "rim_slab", x0, x0 + T, FLOOR_Y, FLOOR_Y + 8, 64, 80)
    for x0 in (-72, 56):
        for z0 in range(16, 64, T):
            box(p, "rim_slab", x0, x0 + T, FLOOR_Y, FLOOR_Y + 8, z0, z0 + T)

def ring(p, kind, ro, ri, z0, z1, clip=None):
    for y0, y1, segs in ring_rows(ro, ri, clip=clip):
        for x0, x1 in segs:
            box(p, kind, x0, x1, y0, y1, z0, z1)

# ---------------------------------------------------------------- the build
static = part("Static", (0, 0, 0))
dwarven_wall(static)
floor_ledge(static)
# the frame: a stepped, machined ring - an outer step, the main band, a raised lip round the door
ring(static, "frame_step", FRAME_O, FRAME_O - 4, 0, 3)
ring(static, "frame", FRAME_O - 4, HOLE + 3, 0, 5)
ring(static, "frame_lip", HOLE + 3, HOLE, 0, 6)
# studs round the main band: at the sockets' angles and either side of each bolt, none by the hinge
for deg in [90 + 72 * k for k in range(5)] + [126 + 72 * k + d for k in range(5) for d in (-22, 22)]:
    if 140 < deg % 360 < 220: continue
    a = math.radians(deg)
    sx, sy = 39 * math.cos(a), 39 * math.sin(a)
    box(static, "stud", sx - 1.5, sx + 1.5, sy - 1.5, sy + 1.5, 5, 6.5)
# a keeper on the frame for each bolt: two cheeks and a bridge the bolt's tip slides under
for k in range(5):
    a = math.radians(90 + 36 + 72 * k)
    box(static, "keeper", 37, 43, -4.5, -2.5, 5, 10.5, rot=a)
    box(static, "keeper", 37, 43, 2.5, 4.5, 5, 10.5, rot=a)
    box(static, "keeper_rim", 38.5, 42.5, -4.5, 4.5, 10.5, 11.5, rot=a)
ring(static, "tunnel", HOLE + 2, HOLE, -30, -8)
ring(static, "back", HOLE, 0, -32, -30)
# the hinge, two of them: knuckles round a pin on the axis, alternating frame / door / frame, a gold cap at each end;
# the frame's leaf bolted to the frame's outer step
def knuckle(p, y0, y1):
    box(p, "hinge", HX - 3, HX + 3, y0, y1, HZ - 2, HZ + 2)
    box(p, "hinge", HX - 2, HX + 2, y0, y1, HZ - 3, HZ + 3)
for hy in (22, -22):
    knuckle(static, hy - 9, hy - 3.25)
    knuckle(static, hy + 3.25, hy + 9)
    box(static, "pin", HX - 1.5, HX + 1.5, hy + 9, hy + 10.5, HZ - 1.5, HZ + 1.5)
    box(static, "pin", HX - 1.5, HX + 1.5, hy - 10.5, hy - 9, HZ - 1.5, HZ + 1.5)
    fx = -math.sqrt(FRAME_O ** 2 - hy ** 2) + 3          # into the frame's outer step
    box(static, "leaf", HX + 2, fx, hy - 8, hy - 3.5, 3, 5.5)
    box(static, "leaf", HX + 2, fx, hy + 3.5, hy + 8, 3, 5.5)
    for ly in (hy - 5.75, hy + 5.75):
        box(static, "stud", fx - 3.5, fx - 1.5, ly - 1, ly + 1, 5.5, 6.5)

door = part("Door", (HX, 0, HZ))
ring(door, "door", DOOR_R, 0, *DOOR_Z)
ring(door, "rim", DOOR_R, DOOR_R - 4, DOOR_Z[1], DOOR_Z[1] + 1)
for hy in (22, -22):
    # the door's knuckle, between the frame's two, and its strap across the door's face with three rivets
    knuckle(door, hy - 3, hy + 3)
    box(door, "strap", HX + 2, -20, hy - 2.5, hy + 2.5, DOOR_Z[1], DOOR_Z[1] + 2)
    box(door, "strap", -24, -18, hy - 3.5, hy + 3.5, DOOR_Z[1], DOOR_Z[1] + 2)        # the strap's broad end
    for rx in (-38, -30, -21):
        box(door, "stud", rx - 1, rx + 1, hy - 1, hy + 1, DOOR_Z[1] + 2, DOOR_Z[1] + 2.75)
# rivets round the rim, clear of the bolts and the straps
for k in range(20):
    a = math.radians(9 + 18 * k)
    deg = (9 + 18 * k) % 72
    if abs(deg - 54) < 10: continue
    rx, ry = 30 * math.cos(a), 30 * math.sin(a)
    if rx < -20 and abs(abs(ry) - 22) < 5: continue
    box(door, "rivet", rx - 1, rx + 1, ry - 1, ry + 1, DOOR_Z[1] + 1, DOOR_Z[1] + 2)

for k in range(5):
    a = math.radians(90 + 72 * k)
    cx, cy = round(SR * math.cos(a) / G) * G, round(SR * math.sin(a) / G) * G
    z = DOOR_Z[1]
    s = part(f"Socket{k}", (cx, cy, z + 1), door)
    box(s, "socket", cx - 5, cx + 5, cy + 3, cy + 5, z, z + 3)
    box(s, "socket", cx - 5, cx + 5, cy - 5, cy - 3, z, z + 3)
    box(s, "socket", cx - 5, cx - 3, cy - 3, cy + 3, z, z + 3)
    box(s, "socket", cx + 3, cx + 5, cy - 3, cy + 3, z, z + 3)
    box(s, "socket_hole", cx - 3, cx + 3, cy - 3, cy + 3, z, z + 0.5)
for k in range(5):
    a = math.radians(90 + 36 + 72 * k)
    z = DOOR_Z[1]
    # the housing: a dark body between two rails, a gold collar where the bolt comes out, two rivets on top
    box(door, "housing", 23, 30, -2.75, 2.75, z, z + 3.5, rot=a)
    box(door, "rail", 22.5, 30.5, -3.5, -2.75, z, z + 4, rot=a)
    box(door, "rail", 22.5, 30.5, 2.75, 3.5, z, z + 4, rot=a)
    box(door, "collar", 30, 31, -3.25, 3.25, z, z + 4.25, rot=a)
    for ry in (-1.5, 1.5):
        box(door, "stud", 24, 25, ry - 0.5, ry + 0.5, z + 3.5, z + 4, rot=a)
    b = part(f"Bolt{k}", (0, 0, z + 2), door)
    b["slide"] = (math.cos(a), math.sin(a))     # retracts along -slide
    # out of the collar, over the seam and the lip, its tip under the keeper's bridge; a narrower tip, a grip groove
    box(b, "bolt", 27, 41, -1.75, 1.75, z + 0.5, z + 3.25, rot=a)
    box(b, "bolt", 41, 42.5, -1.25, 1.25, z + 0.75, z + 3, rot=a)
    box(b, "groove", 33, 34, -1.8, 1.8, z + 3.25, z + 3.4, rot=a)

z = DOOR_Z[1]
dial = part("Dial", (0, 0, z + 1), door)
ring(dial, "dial", 10, 0, z, z + 2)
for k in range(12):
    box(dial, "notch", 7, 10, -0.75, 0.75, z + 2, z + 2.5, rot=math.radians(30 * k))
box(dial, "wheel", -2, 2, -2, 2, z + 2, z + 5.5)
for k in range(3):
    a = math.radians(90 + 60 * k)
    box(dial, "wheel", -8.5, 8.5, -1, 1, z + 3.5, z + 5, rot=a)
    for end in (1, -1):
        ex, ey = 8.5 * end * math.cos(a), 8.5 * end * math.sin(a)
        box(dial, "knob", ex - 1.5, ex + 1.5, ey - 1.5, ey + 1.5, z + 3, z + 5.5)

for name, bm in PARTS.items():
    bm.normal_update(); bm.to_mesh(bpy.data.objects[name].data); bm.free()
bpy.context.view_layer.update()
RESULT = {"boxes": len(BOXES), "parts": len(PARTS)}
