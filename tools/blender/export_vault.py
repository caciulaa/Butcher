"""Bakes the vault door built by build_vault.py into the mod's model, src/main/resources/assets/butcher/nucleus_vault.json.
Run inside Blender after building:  g = {"VAULT_DIR": ...}; exec(build); exec(open(this).read(), g)  - it reads the build's
globals (BOXES, PARTS, KIND). The JSON is the mod's source for the door: re-export here, never edit it by hand."""
import bpy, json, os

# The forge is exported by the same code: set OUT_JSON (and NOTE) instead of VAULT_JSON.
OUT = globals().get("OUT_JSON") or globals().get("VAULT_JSON") or os.path.join(os.path.dirname(os.path.abspath(bpy.data.filepath or ".")), "nucleus_vault.json")

parts = {}
for name in PARTS.keys():
    ob = bpy.data.objects[name]
    parts[name] = {"pivot": list(ob["pivot"]), "parent": ob.parent.name if ob.parent else None}
    if "slide" in ob:
        parts[name]["slide"] = [round(v, 5) for v in ob["slide"]]
    if "inward" in ob:
        # a wall or the ceiling: the way its inside faces, so the game can cut it away when it is in front
        parts[name]["inward"] = [round(v, 5) for v in ob["inward"]]

def r(v):
    return round(float(v), 4)

boxes = [[b[0], b[1], r(b[2][0]), r(b[2][1]), r(b[2][2]), r(b[3][0]), r(b[3][1]), r(b[3][2]), r(b[4]), r(b[5][0]), r(b[5][1])]
         + ([r(b[6])] if len(b) > 6 and b[6] < 1 else [])
         for b in BOXES]
# A kind's fifth number is 1 when the game shades it as Minecraft shades a block (MC in build_vault.py).
kinds = {k: [v[0], v[1][0], v[1][1], v[1][2]] + ([1] if k in MC else []) for k, v in KIND.items()}
doc = {"note": globals().get("NOTE") or "The Crystal Nucleus vault door, approved by the user 2026-10-27. Built by tools/blender/build_vault.py and "
               "baked by export_vault.py - re-export there, never edit by hand. Mod pixels: x right, y up, z towards the "
               "camera. A box: [part, kind, cx, cy, cz, hx, hy, hz, rot (radians about z through origin), ox, oy, alpha?] - "
               "alpha only where under 1 (the wall's and floor's fading edge). Kinds name a block texture, a tint, and 1 "
               "for Minecraft's own face shading.",
       "parts": parts, "kinds": kinds, "boxes": boxes}
with open(OUT, "w", encoding="utf-8") as f:
    f.write(json.dumps(doc, separators=(",", ":")))
EXPORTED = {"boxes": len(boxes), "parts": list(parts.keys()), "bytes": os.path.getsize(OUT), "to": OUT}
