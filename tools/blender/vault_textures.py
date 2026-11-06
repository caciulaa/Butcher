"""Extracts the block textures the vault preview uses from the local Minecraft 26.2 client jar (Loom's cache) into
<out>/tex. Preview only - never commit or bundle them.   python tools/blender/vault_textures.py <out dir>"""
import glob, os, sys, zipfile

NAMES = ["netherite_block", "iron_block", "gold_block", "raw_gold_block", "polished_blackstone", "blackstone",
         "chiseled_polished_blackstone", "polished_deepslate", "stone", "andesite", "tuff",
         "lime_stained_glass", "orange_stained_glass", "yellow_stained_glass", "light_blue_stained_glass",
         "purple_stained_glass", "red_stained_glass", "magenta_stained_glass",
         # the Dwarven Mines' palette round the door (the user's forge screenshot, 2026-10-27)
         "stone_bricks", "cracked_stone_bricks", "bricks", "terracotta", "acacia_planks", "smooth_stone"]

out = os.path.join(sys.argv[1], "tex")
os.makedirs(out, exist_ok=True)
root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
jar = glob.glob(os.path.join(root, ".gradle", "loom-cache", "minecraftMaven", "net", "minecraft", "minecraft-merged-*", "26.2", "*.jar"))[0]
z = zipfile.ZipFile(jar)
for n in NAMES:
    open(os.path.join(out, n + ".png"), "wb").write(z.read(f"assets/minecraft/textures/block/{n}.png"))
print("extracted", len(NAMES), "to", out)
