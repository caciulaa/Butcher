package com.butcher.render;

import com.butcher.render.model.Mesh;
import com.butcher.render.model.ModelProjection;
import com.butcher.render.model.PlayerHeadMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws a {@link DropArt} in the game, as SkyBlock shows the item:
 * <ul>
 *   <li>a head: its skin on a real 3D head once the skin is in, and the game's own player head item until then;</li>
 *   <li>a Hypixel pack item: its own texture turning a texel thick ({@link ItemSprite}), or its model through the item
 *       renderer if the texture is not a flat one;</li>
 *   <li>a vanilla model: the game's item, glinting where the real one does.</li>
 * </ul>
 * One per screen (it keeps a mesh renderer and a few stacks). Render thread only.
 */
public final class DropIcon {
    private final MeshRenderer meshes = new MeshRenderer();
    private static final Mesh HEAD = PlayerHeadMesh.head();
    private static final float HEAD_SPAN = PlayerHeadMesh.HEAD + 2 * PlayerHeadMesh.HAT_INFLATE;
    private final Map<DropArt, ItemStack> stacks = new HashMap<>();

    /**
     * {@code art} centred at ({@code cx}, {@code cy}), {@code size} GUI px, turned {@code yawDeg} about its upright.
     * {@code canvas} is the screen's own (pack textures go through its quad).
     */
    public void draw(GuiGraphicsExtractor g, ChopScene.Canvas canvas, DropArt art, float cx, float cy, float size, float alpha, float yawDeg) {
        if (art == null || alpha <= 0.015f || size < 2f) return;
        int a = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
        if (art.isHead()) {
            HeadSkins set = HeadSkins.named(art.headSet());
            String skin = set == null ? null : set.texture(art.head());
            if (skin != null) {
                meshes.begin();
                meshes.camera(ModelProjection.Camera.of(yawDeg, -22f, size / (HEAD_SPAN * 1.4f)));
                meshes.add(HEAD, skin, 0f, 0f, 0f, 0f, (a << 24) | 0xFFFFFF, true, false, true);
                meshes.flush(g, cx, cy, a << 24, Math.max(1.5f, size / 16f));
                return;
            }
            stackItem(g, art, cx, cy, size, a, yawDeg);
            return;
        }
        String tex = art.packTexture();
        if (tex != null) {
            String key = PackTextures.get(tex);
            if (key != null) {
                // Face-on, always: turned edge-on a flat item read as a sliver (the user, 2026-10-26).
                ItemSprite.spin(canvas, key, cx, cy, size, 0f, alpha);
                return;
            }
        }
        stackItem(g, art, cx, cy, size, a, yawDeg);
    }

    /** Through the item renderer - opaque, so held back until it is mostly faded in. */
    private void stackItem(GuiGraphicsExtractor g, DropArt art, float cx, float cy, float size, int a, float yawDeg) {
        if (a < 110) return;
        ItemStack stack = stack(art);
        if (stack == null) return;
        // Face-on, never squeezed: the item renderer already draws a head item as the game shows it.
        float turn = 1f;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx - size * turn / 2f, cy - size / 2f);
        pose.scale(size / 16f * turn, size / 16f);
        g.item(stack, 0, 0);
        pose.popMatrix();
    }

    /** The stack the game draws for {@code art}; looked up again each time for a pack model, which comes and goes with the server. */
    public ItemStack stack(DropArt art) {
        if (art.isHead()) {
            return stacks.computeIfAbsent(art, x -> {
                HeadSkins set = HeadSkins.named(x.headSet());
                return set == null ? null : set.headItem(x.head());
            });
        }
        if (art.model() == null) return null;
        if (art.packItem() && !modelLoaded(art.model())) return paper(art);
        return stacks.computeIfAbsent(art, DropIcon::modelStack);
    }

    private static ItemStack modelStack(DropArt art) {
        try {
            ItemStack stack = new ItemStack(Items.PAPER);
            stack.set(DataComponents.ITEM_MODEL, Identifier.parse(art.model()));
            if (art.glint()) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            return stack;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** No Hypixel pack: what the server sends without it - paper. */
    private static ItemStack paper(DropArt art) {
        ItemStack stack = new ItemStack(Items.PAPER);
        if (art.glint()) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    public static boolean modelLoaded(String model) {
        try {
            var m = Minecraft.getInstance().getModelManager().getItemModel(Identifier.parse(model));
            return m != null && !(m instanceof MissingItemModel);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
