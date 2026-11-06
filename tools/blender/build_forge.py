"""The forge Divan's Alloy is made in (the Crystal Nucleus's jackpot ending), built from boxes in mod pixels (x right, y up,
z towards the camera), mirrored into Blender as (x, -z, y). Run inside Blender: exec(open(this).read(), g) with
g = {"FORGE_DIR": <folder with tex/ from forge_textures.py>}. It starts an empty file, stages the cameras and light
(the game's angle, as the vault's), and builds the set. The parameters at the top are the design.

The helpers are build_vault.py's (one box a mesh's six faces, a texel a pixel; a moving part a mesh whose origin is its
pivot) - copied, not shared, because both are exec()'d inside Blender with nothing on their path."""
import bpy, bmesh, math, os
from mathutils import Vector

HERE = globals().get("FORGE_DIR") or os.path.join(os.path.dirname(bpy.data.filepath) or os.getcwd(), "forge")
TEX = os.path.join(HERE, "tex")

# ---------------------------------------------------------------- design
# The forge as the Dwarven Mines builds it (the user's screenshot, 2026-10-27): a stone-brick furnace with a red-brick
# core banded in gold, stepped stone-brick buttresses, an arched mouth with the fire in it, a terracotta cap; red-brick
# pillars with terracotta capitals under acacia slabs; a stone floor with a smooth-stone slab rim; prismarine crates on
# spruce. All of it whole blocks on a 16 px grid centred on x 0 (Minecraft's rules - the user); only the crucible, the
# anvil, the ingot and the Automaton's arm are free.
B = 16                            # a block
GX = 8                            # the grid's x offset: block i spans x -8 + 16 i .. +16
CRUCIBLE = (-20, 54, 26)          # the crucible's pivot: up on a tall cradle beside the anvil, its spout over the
                                  # mould's end, so the metal falls nearly straight down into it (the user: the long
                                  # pour across looked goofy)
ANVIL_X, ANVIL_Z = 4, 26          # the anvil's middle, under the elbow: at rest the hammer is on the ingot
ANVIL_K = 1.5                     # Minecraft's own anvil model, half as big again
SHOULDER = (56, 67, 26)           # the Automaton arm's shoulder (turns about z)
ELBOW = (4, 67, 26)               # its elbow, at the end of the upper arm
HAMMER_DROP = 38                  # how far below the elbow the hammer's head is, at rest (straight down, struck)

KIND = {  # kind -> (texture, tint)
    "floor": ("stone", (1, 1, 1)),
    "floor2": ("andesite", (1, 1, 1)),
    "bricks": ("stone_bricks", (1, 1, 1)),
    "cracked": ("cracked_stone_bricks", (1, 1, 1)),
    "red": ("bricks", (1, 1, 1)),
    "gold": ("gold_block", (1, 1, 1)),
    "terracotta": ("terracotta", (1, 1, 1)),
    "acacia": ("acacia_planks", (1, 1, 1)),
    "slab": ("smooth_stone", (1, 1, 1)),
    "spruce": ("spruce_planks", (1, 1, 1)),
    "ceiling": ("dark_oak_planks", (1, 1, 1)),
    "beam": ("stripped_dark_oak_log", (1, 1, 1)),
    "andesite_wall": ("polished_andesite", (1, 1, 1)),
    "prismarine": ("prismarine", (1, 1, 1)),
    "lava": ("lava_still", (1, 1, 1)),
    "magma": ("magma", (1, 1, 1)),
    "bars": ("iron_bars", (1, 1, 1)),
    "pot": ("iron_block", (0.42, 0.42, 0.46)),
    "pot_rim": ("gold_block", (1, 1, 1)),
    "molten": ("shroomlight", (1.0, 0.95, 0.8)),
    "stand": ("polished_blackstone", (0.8, 0.8, 0.8)),
    "anvil": ("anvil", (1, 1, 1)),
    "anvil_top": ("anvil_top", (1, 1, 1)),
    "mould": ("polished_deepslate", (0.6, 0.6, 0.65)),
    "ingot": ("gold_block", (1, 1, 1)),
    "column": ("polished_deepslate", (1, 1, 1)),
    "copper": ("copper_block", (1, 1, 1)),
    "verdigris": ("oxidized_copper", (1, 1, 1)),
    "aged": ("weathered_copper", (1, 1, 1)),
    "iron": ("iron_block", (0.7, 0.72, 0.78)),
    "iron_dark": ("iron_block", (0.45, 0.46, 0.52)),
    "gear": ("gold_block", (1, 1, 1)),
    "notch_gear": ("polished_blackstone", (1, 1, 1)),
    "eye": ("sea_lantern", (0.7, 1.0, 0.95)),
    "jade": ("lime_stained_glass", (1, 1, 1)),
    "amber": ("orange_stained_glass", (1, 1, 1)),
    "topaz": ("yellow_stained_glass", (1, 1, 1)),
    "sapphire": ("light_blue_stained_glass", (1, 1, 1)),
    "amethyst": ("purple_stained_glass", (1, 1, 1)),
}
# The kinds that are Minecraft blocks: shaded as the game shades a block, their texture from their own block's edge.
MC = {"floor", "floor2", "bricks", "cracked", "red", "gold", "terracotta", "acacia", "slab", "spruce", "prismarine",
      "lava", "magma", "bars", "ceiling", "beam", "andesite_wall"}
# The room, in blocks: x from ROOM_I0 to ROOM_I1, z from ROOM_K0 (the back wall) to ROOM_K1 (the front wall), a ceiling
# at ROOM_J. The walls are parts of their own, so the game can cut away whichever is between the camera and the room.
ROOM_I0, ROOM_I1, ROOM_K0, ROOM_K1, ROOM_J = -12, 12, -5, 9, 13
GLOW = {"lava_still": 3.0, "magma": 1.2, "shroomlight": 4.0, "sea_lantern": 2.0, "lime_stained_glass": 2.2,
        "orange_stained_glass": 2.2, "yellow_stained_glass": 2.2, "light_blue_stained_glass": 2.2, "purple_stained_glass": 2.2}

# ---------------------------------------------------------------- a clean file, the stage
bpy.ops.wm.read_homefile(use_empty=True)
scene = bpy.context.scene
col = bpy.data.collections.new("Forge")
scene.collection.children.link(col)

world = bpy.data.worlds.new("World")
scene.world = world
world.use_nodes = True
bg = world.node_tree.nodes["Background"]
bg.inputs[0].default_value = (0.03, 0.03, 0.04, 1)
bg.inputs[1].default_value = 6

def to_b(p): return Vector((p[0], -p[2], p[1]))

sun = bpy.data.objects.new("Key", bpy.data.lights.new("Key", "SUN"))
sun.data.energy = 2.2
sun.rotation_euler = (math.radians(36.97), 0, math.radians(-41.63))
scene.collection.objects.link(sun)
# The hearth's own light, warm, from its mouth.
fire = bpy.data.objects.new("Fire", bpy.data.lights.new("Fire", "POINT"))
fire.data.energy = 900000
fire.data.color = (1.0, 0.5, 0.2)
fire.data.shadow_soft_size = 12
fire.location = to_b((0, 16, -30))
scene.collection.objects.link(fire)

def camera(name, target, rot, scale, dist=200):
    cam = bpy.data.objects.new(name, bpy.data.cameras.new(name))
    cam.data.type = "ORTHO"
    cam.data.ortho_scale = scale
    cam.data.clip_end = 2000
    cam.rotation_euler = tuple(math.radians(v) for v in rot)
    fwd = cam.rotation_euler.to_matrix() @ Vector((0, 0, -1))
    cam.location = to_b(target) - fwd * dist
    scene.collection.objects.link(cam)
    return cam

camera("CamGame", (0, 72, -8), (78, 0, 20), 330)
camera("CamFront", (0, 80, -8), (90, 0, 0), 340)
camera("CamArm", (14, 52, 26), (76, 0, 32), 120)
camera("CamRoom", (0, 88, 24), (64, 0, 38), 460)          # high in a corner, the whole room
camera("CamLow", (-30, 70, 30), (96, 0, -28), 300)       # low, looking up at the furnace and the ceiling
scene.camera = bpy.data.objects["CamGame"]

r = scene.render
r.engine = "BLENDER_EEVEE"
r.resolution_x, r.resolution_y = 1600, 1000
r.use_freestyle = True
r.line_thickness = 2.5
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
    src = tn.outputs["Color"]
    if tint != (1, 1, 1):
        mix = nt.nodes.new("ShaderNodeMix"); mix.data_type = "RGBA"; mix.blend_type = "MULTIPLY"
        mix.inputs["Factor"].default_value = 1.0
        nt.links.new(tn.outputs["Color"], mix.inputs[6]); mix.inputs[7].default_value = (*tint, 1)
        src = mix.outputs[2]
    nt.links.new(src, bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 1.0
    if name in GLOW:
        nt.links.new(src, bsdf.inputs["Emission Color"])
        bsdf.inputs["Emission Strength"].default_value = GLOW[name]
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
        # A block's texture starts at its own edge: the grid is GX off x 0.
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

def disc(p, kind, cx, cy, r, z0, z1, step=1):
    """A disc facing the camera, in rows of `step`: a gear's face, a lamp."""
    for yb in range(-r, r, step):
        yc = yb + step / 2
        w = round(math.sqrt(max(0, r * r - yc * yc)) / step) * step
        if w > 0: box(p, kind, cx - w, cx + w, cy + yb, cy + yb + step, z0, z1)

def tiles(p, kinds, x0, x1, y0, y1, z0, z1, along):
    """A slab cut into whole 16 px blocks along two axes, the kind picked per block, so no two neighbours repeat."""
    xs = list(range(x0, x1, 16)); ys = list(range(y0, y1, 16)) if along == "xy" else [y0]
    zs = list(range(z0, z1, 16)) if along == "xz" else [z0]
    for i, x in enumerate(xs):
        for j, y in enumerate(ys):
            for k, z in enumerate(zs):
                kind = kinds[(i * 7 + j * 3 + k * 5 + i * j) % len(kinds)]
                box(p, kind, x, min(x + 16, x1), y, y + 16 if along == "xy" else y1, z, z + 16 if along == "xz" else z1)

# ---------------------------------------------------------------- the build
def blk(p, kind, i, j, k, w=1, h=1.0, d=1):
    """Blocks on the grid: cell (i, j, k), w wide, h high (0.5 a slab), d deep - each its own box, a whole block."""
    for di in range(w):
        for dk in range(d):
            x0, z0 = -GX + B * (i + di), B * (k + dk)
            hh = h
            jj = j
            while hh > 0:
                step = min(1.0, hh)
                box(p, kind, x0, x0 + B, B * jj, B * (jj + step), z0, z0 + B)
                jj += step; hh -= step

static = part("Static", (0, 0, 0))
# The floor: stone, an andesite block here and there; the back wall: stone bricks, a cracked one now and then, a
# course of polished andesite at the foot.
for i in range(ROOM_I0 + 1, ROOM_I1):
    for k in range(ROOM_K0 + 1, ROOM_K1):
        blk(static, "floor2" if (i * 7 + k * 5) % 11 == 0 else "floor", i, -1, k)
    for j in range(0, ROOM_J):
        kind = "andesite_wall" if j == 0 else ("cracked" if (i * 5 + j * 3) % 13 == 0 else "bricks")
        blk(static, kind, i, j, ROOM_K0)

# The side walls and the front wall: stone bricks with red-brick pilasters standing out of them, an andesite course.
def wall_part(name, inward):
    w = part(name, (0, 0, 0))
    w["inward"] = inward
    return w
left, right, front = wall_part("WallL", (1, 0, 0)), wall_part("WallR", (-1, 0, 0)), wall_part("WallF", (0, 0, -1))
for side, i in ((left, ROOM_I0), (right, ROOM_I1)):
    inner = i + (1 if i < 0 else -1)
    for k in range(ROOM_K0, ROOM_K1 + 1):
        for j in range(0, ROOM_J):
            kind = "andesite_wall" if j == 0 else ("cracked" if (k * 5 + j * 7) % 11 == 0 else "bricks")
            blk(side, kind, i, j, k)
        if k in (-1, 4):                                                    # a pilaster every five blocks
            for j in range(0, ROOM_J):
                blk(side, "bricks" if j == 0 else ("gold" if j == 6 else "red"), inner, j, k)
            blk(side, "terracotta", inner, 9, k)
for i in range(ROOM_I0 + 1, ROOM_I1):
    for j in range(0, ROOM_J):
        kind = "andesite_wall" if j == 0 else ("cracked" if (i * 3 + j * 5) % 13 == 0 else "bricks")
        blk(front, kind, i, j, ROOM_K1)

# The ceiling: dark oak, crossed by beams of stripped dark oak, one along the room over the furnace's middle.
ceiling = part("Ceiling", (0, 0, 0))
ceiling["inward"] = (0, -1, 0)
for i in range(ROOM_I0, ROOM_I1 + 1):
    for k in range(ROOM_K0, ROOM_K1 + 1):
        blk(ceiling, "ceiling", i, ROOM_J, k)
for k in (-1, 4):
    for i in range(ROOM_I0 + 1, ROOM_I1):
        blk(ceiling, "beam", i, ROOM_J - 1, k)

# Braziers either side of the casting floor: a stone-brick bowl, a magma block burning in it.
for i in (-6, 6):
    blk(static, "slab", i, 0, 2, h=0.5)
    blk(static, "bricks", i, 0.5, 2, h=1)
    blk(static, "magma", i, 1.5, 2, h=1)

# The furnace. Its red-brick core, two blocks deep, banded in gold twice; the mouth at its foot.
for i in (-1, 0, 1):
    for j in range(0, 10):
        for k in (-4, -3):
            if i == 0 and j < 3 and k == -3: continue                     # the mouth
            front = k == -3
            kind = "gold" if front and j in (5, 7) else "red"
            blk(static, kind, i, j, k)
blk(static, "magma", 0, 0, -4); blk(static, "lava", 0, 1, -4); blk(static, "lava", 0, 2, -4)
blk(static, "bars", 0, 2, -3)                                             # the grate hung in the mouth's top
# The arch in front of the mouth: jambs, a lintel, a keystone.
for j in range(0, 4):
    blk(static, "bricks", -1, j, -2); blk(static, "bricks", 1, j, -2)
blk(static, "bricks", 0, 3, -2)
blk(static, "cracked", 0, 4, -2)
# Stepped buttresses either side, a block further out at their feet, two at the floor.
for i0 in (-3, 2):
    blk(static, "bricks", i0, 0, -4, w=2, h=9, d=2)
    blk(static, "bricks", i0, 0, -2, w=2, h=3, d=1)
    blk(static, "bricks", i0, 0, -1, w=2, h=1, d=1)
# The cap: terracotta over the core and buttresses, a stone-brick ridge, a slab on it.
blk(static, "terracotta", -3, 9, -4, w=7, h=1, d=3)
blk(static, "bricks", -2, 10, -4, w=5, h=1, d=2)
blk(static, "bricks", -1, 11, -4, w=3, h=1, d=2)
blk(static, "slab", 0, 12, -4, w=1, h=0.5, d=2)

# The pillars: stone-brick bases, red brick, terracotta capitals under acacia slabs, red brick on up.
for i0 in (-9, 8):
    blk(static, "bricks", i0, 0, -4, w=2, h=1, d=2)
    blk(static, "red", i0, 1, -4, w=2, h=3, d=2)
    blk(static, "terracotta", i0, 4, -4, w=2, h=1, d=2)
    blk(static, "acacia", i0 - 1, 5, -5, w=4, h=0.5, d=4)
    blk(static, "red", i0, 5.5, -4, w=2, h=0.5, d=2)
    blk(static, "red", i0, 6, -4, w=2, h=6, d=2)

# The rim of smooth stone slabs round the casting floor in front of the furnace (the pit in the screenshot).
for i in range(-2, 3):
    blk(static, "slab", i, 0, 4, h=0.5)
for k in range(0, 4):
    blk(static, "slab", -2, 0, k, h=0.5); blk(static, "slab", 2, 0, k, h=0.5)

# Prismarine crates on spruce, by the right pillar.
blk(static, "spruce", 5, 0, -4, w=2, h=0.5, d=2)
blk(static, "prismarine", 5, 0.5, -4, w=2, h=1, d=1)
blk(static, "prismarine", 6, 0.5, -3, w=1, h=1, d=1)
blk(static, "prismarine", 5, 1.5, -4, w=1, h=1, d=1)

# The crucible's cradle: two posts either side, the pins it tips on at the pivot.
cx, cy, cz = CRUCIBLE
for zz in (cz - 14, cz + 12):
    box(static, "stand", cx - 3, cx + 3, 0, cy - 2, zz, zz + 2)
    box(static, "stand", cx - 5, cx + 5, 0, 3, zz - 1, zz + 3)
    box(static, "stand", cx - 4, cx + 4, cy - 6, cy - 2, zz - 0.5, zz + 2.5)          # the bearing
crucible = part("Crucible", CRUCIBLE)
# A thick iron pot, a gold rim, a spout towards the anvil; the metal inside, white hot.
box(crucible, "pot", cx - 10, cx + 10, cy - 12, cy - 10, cz - 10, cz + 10)
box(crucible, "pot", cx - 10, cx - 8, cy - 10, cy + 4, cz - 10, cz + 10)
box(crucible, "pot", cx + 8, cx + 10, cy - 10, cy + 4, cz - 10, cz + 10)
box(crucible, "pot", cx - 8, cx + 8, cy - 10, cy + 4, cz - 10, cz - 8)
box(crucible, "pot", cx - 8, cx + 8, cy - 10, cy + 4, cz + 8, cz + 10)
box(crucible, "pot_rim", cx - 11, cx + 11, cy + 4, cy + 6, cz - 11, cz - 8)
box(crucible, "pot_rim", cx - 11, cx + 11, cy + 4, cy + 6, cz + 8, cz + 11)
box(crucible, "pot_rim", cx - 11, cx - 8, cy + 4, cy + 6, cz - 8, cz + 8)
box(crucible, "pot_rim", cx + 8, cx + 11, cy + 4, cy + 6, cz - 8, cz + 8)
box(crucible, "pot_rim", cx + 10, cx + 18, cy + 2, cy + 5, cz - 3, cz + 3)        # the spout, long: tipped, its lip is over the mould
box(crucible, "pot_rim", cx - 2, cx + 2, cy - 4, cy, cz - 13, cz - 10)             # the pins
box(crucible, "pot_rim", cx - 2, cx + 2, cy - 4, cy, cz + 10, cz + 13)
molten = part("Molten", (cx, cy, cz), crucible)
box(molten, "molten", cx - 8, cx + 8, cy - 10, cy + 1, cz - 8, cz + 8)

# The anvil: Minecraft's own model (models/block/anvil.json - a base, a step, the waist, the top), its long side across
# the camera, half as big again; the ingot mould on it and the ingot in the mould. These boxes are for the Blender preview
# only: the game skips them and builds vanilla's model face by face with its own UVs (ForgeScene.anvil(), which keeps
# ANVIL_X, ANVIL_Z and ANVIL_K in step with these).
ax, az, K = ANVIL_X, ANVIL_Z, ANVIL_K
def anvil_box(kind, x0, y0, z0, x1, y1, z1):
    # the model's (x, y, z) 0..16, turned a quarter so its length runs along x, centred on the anvil
    box(static, kind, ax + (z0 - 8) * K, ax + (z1 - 8) * K, y0 * K, y1 * K, az + (x0 - 8) * K, az + (x1 - 8) * K)
anvil_box("anvil", 2, 0, 2, 14, 4, 14)
anvil_box("anvil", 4, 4, 3, 12, 5, 13)
anvil_box("anvil", 6, 5, 4, 10, 10, 12)
anvil_box("anvil_top", 3, 10, 0, 13, 16, 16)
top = 16 * K
box(static, "mould", ax - 10, ax + 10, top, top + 3, az - 5, az - 3)
box(static, "mould", ax - 10, ax + 10, top, top + 3, az + 3, az + 5)
box(static, "mould", ax - 10, ax - 8, top, top + 3, az - 3, az + 3)
box(static, "mould", ax + 8, ax + 10, top, top + 3, az - 3, az + 3)
box(static, "mould", ax - 8, ax + 8, top, top + 0.5, az - 3, az + 3)
ingot = part("Ingot", (ax, top + 0.5, az))
box(ingot, "ingot", ax - 8, ax + 8, top + 0.5, top + 2.5, az - 3, az + 3)
box(ingot, "ingot", ax - 7, ax + 7, top + 2.5, top + 3.5, az - 2, az + 2)

# The Automaton's arm on its column: clockwork - iron and old copper, gold gears, a lamp of an eye on the shoulder.
sx, sy, sz = SHOULDER
box(static, "column", sx - 8, sx + 8, 0, sy - 8, sz - 8, sz + 8)
for yy in (10, 34, 58):
    box(static, "copper", sx - 9, sx + 9, yy, yy + 4, sz - 9, sz + 9)
gear0 = part("Gear0", (sx, 46, sz + 8))
disc(gear0, "gear", sx, 46, 5, sz + 8, sz + 9)
box(gear0, "iron_dark", sx - 1, sx + 1, 45, 47, sz + 9, sz + 10)
box(gear0, "notch_gear", sx - 0.5, sx + 0.5, 41, 51, sz + 9, sz + 9.5)                # a spoke, so the turn shows
box(static, "verdigris", sx - 10, sx + 10, 0, 4, sz - 10, sz + 10)
shoulder = part("Shoulder", SHOULDER)
box(shoulder, "iron", sx - 9, sx + 9, sy - 9, sy + 9, sz - 9, sz + 9)
gear1 = part("Gear1", (sx, sy, sz + 9), shoulder)
disc(gear1, "gear", sx, sy, 7, sz + 9, sz + 10)
box(gear1, "iron_dark", sx - 2, sx + 2, sy - 2, sy + 2, sz + 10, sz + 11.5)
box(gear1, "notch_gear", sx - 0.5, sx + 0.5, sy - 7, sy + 7, sz + 10, sz + 10.5)
box(shoulder, "eye", sx + 2, sx + 7, sy + 5, sy + 9, sz + 9, sz + 10.5)
upper = part("UpperArm", SHOULDER, shoulder)
ex, ey, ez = ELBOW
box(upper, "aged", ex + 4, sx - 8, sy - 4, sy + 4, sz - 6, sz + 6)                 # the beam
box(upper, "copper", ex + 12, sx - 14, sy + 4, sy + 6, sz - 5, sz + 5)            # a plate on top
box(upper, "iron_dark", ex + 10, sx - 10, sy - 7, sy - 4, sz - 3, sz + 3)         # the piston under it
for gx in (ex + 16, sx - 18):
    box(upper, "gear", gx - 1.5, gx + 1.5, sy - 1.5, sy + 1.5, sz + 6, sz + 7)
elbow = part("Forearm", ELBOW, upper)
gear2 = part("Gear2", (ex, ey, ez + 6), elbow)
disc(gear2, "gear", ex, ey, 6, ez + 6, ez + 7.5)
box(gear2, "notch_gear", ex - 6, ex + 6, ey - 0.5, ey + 0.5, ez + 7.5, ez + 8)
box(elbow, "iron", ex - 6, ex + 6, ey - 6, ey + 6, ez - 7, ez + 7)
box(elbow, "aged", ex - 4, ex + 4, ey - HAMMER_DROP + 10, ey - 6, ez - 5, ez + 5)  # straight down at rest
box(elbow, "copper", ex - 5, ex + 5, ey - 22, ey - 18, ez - 6, ez + 6)
hammer = part("Hammer", (ex, ey - HAMMER_DROP + 6, ez), elbow)
hy = ey - HAMMER_DROP
box(hammer, "iron_dark", ex - 12, ex + 12, hy, hy + 10, ez - 7, ez + 7)            # the head, across
box(hammer, "gear", ex - 13, ex - 11, hy - 1, hy + 11, ez - 8, ez + 8)            # gold-bound faces
box(hammer, "gear", ex + 11, ex + 13, hy - 1, hy + 11, ez - 8, ez + 8)
box(hammer, "iron", ex - 10, ex + 10, hy - 2, hy, ez - 5, ez + 5)                 # the striking face

# The five crystals over the crucible, where they hang before they melt (in game: their heads).
for k, (kind, dx, dy) in enumerate((("jade", -14, 80), ("amber", -2, 88), ("topaz", 10, 82), ("sapphire", -22, 72), ("amethyst", 4, 72))):
    c = part(f"Crystal{k}", (cx + dx, dy, cz))
    box(c, kind, cx + dx - 3, cx + dx + 3, dy - 3, dy + 3, cz - 3, cz + 3)

for name, bm in PARTS.items():
    bm.normal_update(); bm.to_mesh(bpy.data.objects[name].data); bm.free()
bpy.context.view_layer.update()
RESULT = {"boxes": len(BOXES), "parts": len(PARTS)}
