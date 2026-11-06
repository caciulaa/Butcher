"""Renders the forge from the game's angle with the arm raised, front on, and the arm close up as it strikes; then the
crucible tipped. exec() inside Blender after build_forge.py, with FORGE_DIR set as for it."""
import bpy, math, os
scene = bpy.context.scene
HERE = globals().get("FORGE_DIR") or os.path.join(os.path.dirname(bpy.data.filepath) or os.getcwd(), "forge")
TAG = globals().get("TAG", "f")
ob = bpy.data.objects
# A turn about mod z is a turn about Blender's -y (mod (x, y, z) is Blender (x, -z, y)).
def pose(shoulder=0.0, forearm=0.0, crucible=0.0):
    ob["Shoulder"].rotation_euler = (0, -shoulder, 0)
    ob["Forearm"].rotation_euler = (0, -forearm, 0)
    ob["Crucible"].rotation_euler = (0, -crucible, 0)
VIEWS = globals().get("VIEWS", (
    ("CamGame", (-0.25, 1.75, 0), "game"),
    ("CamFront", (-0.25, 1.75, 0), "front"),
    ("CamArm", (0, 0, 0), "strike"),
    ("CamGame", (-0.25, 1.75, -1.3), "pour"),
))
def cutaway(cam):
    """Hides each wall or the ceiling whose inside is turned away from the camera - between it and the room."""
    fwd = cam.matrix_world.to_3x3() @ __import__("mathutils").Vector((0, 0, -1))
    for o in ob:
        if "inward" in o.keys():
            n = o["inward"]
            nb = (n[0], -n[2], n[1])                          # mod (x, y, z) -> Blender (x, -z, y)
            o.hide_render = (nb[0] * fwd[0] + nb[1] * fwd[1] + nb[2] * fwd[2]) > -0.02
for camname, p, tag in VIEWS:
    scene.camera = ob[camname]
    cutaway(ob[camname])
    pose(*p)
    scene.render.filepath = os.path.join(HERE, f"{TAG}_{tag}.png")
    bpy.ops.render.render(write_still=True)
pose()
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(HERE, "forge.blend"))
