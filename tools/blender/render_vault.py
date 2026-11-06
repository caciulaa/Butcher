"""Renders the vault from the game's angle, front on, and swung open. exec() inside Blender after build_vault.py."""
import bpy, math, os
scene = bpy.context.scene
# Where the renders and the .blend go, with the block textures under tex/ (vault_textures.py) - set VAULT_DIR before
# exec(), or this falls back to a "vault" folder beside the open .blend. Never commit the textures: they are Mojang's,
# read from the local client jar for previews only.
HERE = globals().get("VAULT_DIR") or os.path.join(os.path.dirname(bpy.data.filepath) or os.getcwd(), "vault")
vl = scene.view_layers[0]
ls = vl.freestyle_settings.linesets[0]
# One outline round the whole silhouette, as the game's raster inks it - not every box's edge.
ls.select_silhouette = False; ls.select_border = False; ls.select_crease = False
ls.select_external_contour = True
door = bpy.data.objects["Door"]
TAG = globals().get("TAG", "v")
VIEWS = globals().get("VIEWS", (("CamGame", 0, "game"), ("CamFront", 0, "front"), ("CamGame", -105, "open")))
for camname, ang, tag in VIEWS:
    scene.camera = bpy.data.objects[camname]
    door.rotation_euler = (0, 0, math.radians(ang))
    scene.render.filepath = os.path.join(HERE, f"{TAG}_{tag}.png")
    bpy.ops.render.render(write_still=True)
door.rotation_euler = (0, 0, 0)
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(HERE, "vault.blend"))
