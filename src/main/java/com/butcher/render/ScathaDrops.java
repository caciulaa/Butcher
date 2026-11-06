package com.butcher.render;

import com.butcher.render.art.PropArt;
import com.butcher.render.model.CrystalMesh;
import com.butcher.render.model.Mesh;
import com.butcher.render.model.ModelProjection;
import com.butcher.render.model.PlayerHeadMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;

import java.util.HashMap;
import java.util.Map;

/**
 * What a Scatha gives up when it is not a pet, drawn as the real SkyBlock items: the gemstones and Dwarven O's
 * Block Bran.
 *
 * Until 0.3.20 these were two pictures copied from Scatha-Pro, which is All Rights Reserved - not ours to ship. Now:
 * <ul>
 *   <li><b>Gemstones</b> are paper items on Hypixel today, drawn by its own resource pack
 *       ({@code hypixel_skyblock:item/collections/gemstone/<gem>/flawed_<gem>_gem}, the NEU repo's ItemModel). With
 *       the pack loaded they are drawn exactly as in an inventory; without it, crystals of our own in the gems'
 *       colours.</li>
 *   <li><b>Block Bran</b> is a player head: its NEU skin ({@code scatha_heads.json}), as a turning 3D head once the
 *       skin is in, and as the game's own head item until then.</li>
 * </ul>
 */
public final class ScathaDrops {
    private ScathaDrops() {}

    /** The Crystal Hollows' seven gems, and the colour each one's crystal takes without Hypixel's pack. */
    private static final String[] GEMS = {"ruby", "amber", "sapphire", "jade", "amethyst", "topaz", "jasper"};
    private static final int[] GEM_RGB = {0xE8344A, 0xF39A2B, 0x4EA9F0, 0x5CD66A, 0xB15CF0, 0xF2E14C, 0xD6457E};

    private static final Map<String, Boolean> PACK = new HashMap<>();
    private static final Map<String, ItemStack> STACKS = new HashMap<>();
    private static final Mesh CRYSTAL = CrystalMesh.cluster(3, 12f, 9127L);
    private static final Mesh HEAD = PlayerHeadMesh.head();
    private static final float HEAD_SPAN = PlayerHeadMesh.HEAD + 2 * PlayerHeadMesh.HAT_INFLATE;

    /** Whether Hypixel's pack has this gem's item model loaded. Asked once per gem a session. */
    private static boolean packHas(String gem) {
        return PACK.computeIfAbsent(gem, g -> {
            try {
                Identifier def = Identifier.fromNamespaceAndPath("hypixel_skyblock",
                        "items/item/collections/gemstone/" + g + "/flawed_" + g + "_gem.json");
                return Minecraft.getInstance().getResourceManager().getResource(def).isPresent();
            } catch (RuntimeException e) {
                return false;
            }
        });
    }

    /** A Flawed gemstone as Hypixel's items are made: paper wearing the pack's model. */
    private static ItemStack gemStack(String gem) {
        return STACKS.computeIfAbsent(gem, g -> {
            ItemStack stack = new ItemStack(Items.PAPER);
            stack.applyComponents(DataComponentPatch.builder()
                    .set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("hypixel_skyblock",
                            "item/collections/gemstone/" + g + "/flawed_" + g + "_gem"))
                    .build());
            return stack;
        });
    }

    /**
     * Three gemstones in a little fan, centred on {@code (cx, cy)}, about {@code size} across. Which three is fixed
     * by {@code seed}, so a run does not reshuffle them every frame.
     */
    public static void gems(GuiGraphicsExtractor g, MeshRenderer meshes, float cx, float cy, float size, long seed,
                            float yawDeg) {
        if (size < 3f) return;
        int first = (int) Math.floorMod(seed, (long) GEMS.length);
        // Left, right, then the middle one last and largest, in front.
        float[][] at = {{-0.30f, 0.10f, 0.58f}, {0.30f, 0.10f, 0.58f}, {0f, -0.06f, 0.70f}};
        for (int k = 0; k < 3; k++) {
            int gem = (first + k * 3) % GEMS.length;
            float x = cx + at[k][0] * size, y = cy + at[k][1] * size, s = at[k][2] * size;
            if (packHas(GEMS[gem])) {
                Matrix3x2fStack pose = g.pose();
                pose.pushMatrix();
                float k16 = s / 16f;
                pose.translate(x - 8f * k16, y - 8f * k16);
                pose.scale(k16, k16);
                g.item(gemStack(GEMS[gem]), 0, 0);
                pose.popMatrix();
            } else {
                meshes.begin();
                meshes.camera(ModelProjection.Camera.of(yawDeg + k * 40f, -22f, s / 14f));
                meshes.add(CRYSTAL, PropArt.TEX_CRYSTAL, 0f, 0f, -6f, 0f, 0xFF000000 | GEM_RGB[gem], true, false, true);
                meshes.flush(g, x, y, 0xFF000000, Math.max(1f, s / 26f));
            }
        }
    }

    /** Dwarven O's Block Bran, centred on {@code (cx, cy)}, about {@code size} across, turned {@code yawDeg}. */
    public static void blockBran(GuiGraphicsExtractor g, MeshRenderer meshes, float cx, float cy, float size, float yawDeg) {
        if (size < 3f) return;
        String skin = HeadSkins.SCATHA.texture(BLOCK_BRAN);
        if (skin != null) {
            meshes.begin();
            meshes.camera(ModelProjection.Camera.of(yawDeg, -22f, size / (HEAD_SPAN * 1.4f)));
            meshes.add(HEAD, skin, 0f, 0f, 0f, 0f, 0xFFFFFFFF, true, false, true);
            meshes.flush(g, cx, cy, 0xFF000000, Math.max(1f, size / 26f));
            return;
        }
        // The skin is still downloading: the game's own head item, which fetches it as well.
        ItemStack stack = HeadSkins.SCATHA.headItem(BLOCK_BRAN);
        if (stack == null) return;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        float k = size / 16f;
        pose.translate(cx - 8f * k, cy - 8f * k);
        pose.scale(k, k);
        g.item(stack, 0, 0);
        pose.popMatrix();
    }

    /** The Block Bran's name in {@code scatha_heads.json}. */
    public static final String BLOCK_BRAN = "Dwarven O's Block Bran";
}
