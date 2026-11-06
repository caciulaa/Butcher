"""Extracts the block textures the forge preview uses from the local Minecraft 26.2 client jar (Loom's cache) into
<out>/tex, each cut to its first 16 x 16 frame (lava and magma are animated strips). Preview only - never commit or
bundle them.   python tools/blender/forge_textures.py <out dir>   (needs Pillow)"""
import glob, io, os, sys, zipfile
from PIL import Image

NAMES = ["deepslate_bricks", "deepslate_tiles", "polished_deepslate", "cracked_deepslate_bricks", "chiseled_deepslate",
         "polished_blackstone_bricks", "gilded_blackstone", "blackstone", "polished_blackstone", "magma", "lava_still",
         "anvil", "anvil_top", "iron_block", "gold_block", "raw_gold_block", "copper_block", "exposed_copper",
         "weathered_copper", "oxidized_copper", "cut_copper", "shroomlight", "sea_lantern", "nether_bricks",
         "lime_stained_glass", "orange_stained_glass", "yellow_stained_glass", "light_blue_stained_glass",
         "purple_stained_glass", "stone", "andesite", "tuff",
         # the Dwarven Mines' forge (the user's screenshot, 2026-10-27)
         "stone_bricks", "cracked_stone_bricks", "bricks", "terracotta", "acacia_planks", "smooth_stone", "prismarine",
         "spruce_planks", "polished_andesite", "iron_bars", "dark_oak_planks", "stripped_dark_oak_log"]

out = os.path.join(sys.argv[1], "tex")
os.makedirs(out, exist_ok=True)
root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
jar = glob.glob(os.path.join(root, ".gradle", "loom-cache", "minecraftMaven", "net", "minecraft", "minecraft-merged-*", "26.2", "*.jar"))[0]
z = zipfile.ZipFile(jar)
missing = []
for n in NAMES:
    try:
        img = Image.open(io.BytesIO(z.read(f"assets/minecraft/textures/block/{n}.png"))).convert("RGBA")
    except KeyError:
        missing.append(n)
        continue
    w = img.width
    img.crop((0, 0, w, w)).resize((16, 16), Image.NEAREST).save(os.path.join(out, n + ".png"))
print("extracted", len(NAMES) - len(missing), "to", out, "missing:", missing)
