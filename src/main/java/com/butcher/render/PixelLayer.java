package com.butcher.render;

import com.butcher.ButcherClient;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * A whole screen of pixel art drawn as ONE image: the frost and the crystal veins paint their cells
 * into a small buffer - one texel per grid cell, about 213 x 120 - which is uploaded and blitted once,
 * scaled up with no smoothing, so every cell is still a crisp square.
 *
 * Why: they used to send every cell to the GUI as its own filled rectangle, twice with the outline.
 * At full growth that was thousands of rectangles a frame, more with every cut - the user's frame
 * rate fell with each slice of the Scatha (2026-10-02). Now it is one blit, whatever the growth.
 *
 * Both ThawFrost.Fill and CrystalVeins.Fill, so the pure classes - and their offline previews - are
 * unchanged. Everything they draw must be squares on the grid; that is what they already draw.
 * Render thread only. One instance per purpose, reused across cutscenes.
 */
public final class PixelLayer implements ThawFrost.Fill, CrystalVeins.Fill {

    private final String name;
    private Identifier id;
    private DynamicTexture texture;
    private NativeImage image;
    private int gw, gh, unit = 1;
    private int[] buf = new int[0];
    /** The painted box, packed for the upload (ABGR, native order): only it goes to the GPU. */
    private java.nio.ByteBuffer upload;
    /** Off after the first failure to write a part of the texture: the whole image goes up instead, as before. */
    private static boolean regionWrites = true;
    private boolean drawn;
    /** The cells painted this frame, so only they are converted and drawn: [x0, x1) x [y0, y1). */
    private int px0, py0, px1, py1;
    private boolean fresh = true;

    public PixelLayer(String name) {
        this.name = name;
    }

    /** Starts a frame for a {@code w x h} GUI screen on a grid of {@code unit} GUI pixels. */
    public void begin(int w, int h, int unit) {
        this.unit = Math.max(1, unit);
        int nw = (w + this.unit - 1) / this.unit, nh = (h + this.unit - 1) / this.unit;
        if (nw != gw || nh != gh || texture == null) {
            gw = nw;
            gh = nh;
            // No close here: registering under the same id closes the old texture itself (26.2's
            // TextureManager.register -> safeClose), and closing it twice is not safe.
            image = new NativeImage(gw, gh, false);
            texture = new DynamicTexture(() -> name, image);
            id = Identifier.fromNamespaceAndPath(ButcherClient.MOD_ID, "layer/" + name);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            buf = new int[gw * gh];
            fresh = true;
            upload = java.nio.ByteBuffer.allocateDirect(gw * gh * 4).order(java.nio.ByteOrder.nativeOrder());
        }
        // Only what was painted last time needs clearing - a spark's layer is a few cells of a 25,000.
        if (fresh) {
            java.util.Arrays.fill(buf, 0);
            fresh = false;
        } else if (px0 < px1 && py0 < py1) {
            for (int y = py0; y < py1; y++) java.util.Arrays.fill(buf, y * gw + px0, y * gw + px1, 0);
        }
        drawn = false;
        px0 = py0 = Integer.MAX_VALUE;
        px1 = py1 = Integer.MIN_VALUE;
    }

    /** A square (or rect) of whole cells, blended over what is already there. */
    @Override
    public void fill(float x, float y, float w, float h, int argb) {
        int a = argb >>> 24;
        if (a == 0) return;
        int x0 = Math.round(x / unit), y0 = Math.round(y / unit);
        int x1 = Math.max(x0 + 1, Math.round((x + w) / unit)), y1 = Math.max(y0 + 1, Math.round((y + h) / unit));
        int cx0 = Math.max(0, x0), cx1 = Math.min(gw, x1), cy0 = Math.max(0, y0), cy1 = Math.min(gh, y1);
        if (cx0 >= cx1 || cy0 >= cy1) return;
        for (int cy = cy0; cy < cy1; cy++) {
            int row = cy * gw;
            for (int cx = cx0; cx < cx1; cx++) {
                int i = row + cx;
                buf[i] = over(argb, buf[i]);
            }
        }
        drawn = true;
        if (cx0 < px0) px0 = cx0;
        if (cy0 < py0) py0 = cy0;
        if (cx1 > px1) px1 = cx1;
        if (cy1 > py1) py1 = cy1;
    }

    /**
     * Uploads the frame and draws it - only the box of cells painted (2026-10-24): the rest of the image is never drawn,
     * so what is left there from an earlier frame does not matter, and the GUI gets a small rectangle, not the screen.
     */
    public void end(GuiGraphicsExtractor g) {
        if (!drawn || texture == null || px0 >= px1 || py0 >= py1) return;
        if (!regionWrites || !writeRegion()) {
            for (int y = py0; y < py1; y++) {
                int row = y * gw;
                for (int x = px0; x < px1; x++) {
                    int c = buf[row + x];
                    // ARGB to the ABGR NativeImage wants.
                    image.setPixelABGR(x, y, (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16));
                }
            }
            texture.upload();
        }
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.scale(unit, unit);
        g.blit(RenderPipelines.GUI_TEXTURED, id, px0, py0, (float) px0, (float) py0, px1 - px0, py1 - py0, gw, gh);
        pose.popMatrix();
    }

    /**
     * Writes only the painted box to the texture (2026-10-24): a few hundred texels for a spark's layer, not the whole
     * image - the RNG cutscenes cut a dozen layers a frame. False if the game would not take it.
     */
    private boolean writeRegion() {
        int bw = px1 - px0, bh = py1 - py0;
        java.nio.ByteBuffer bb = upload;
        bb.clear();
        java.nio.IntBuffer ib = bb.asIntBuffer();
        for (int y = py0; y < py1; y++) {
            int row = y * gw;
            for (int x = px0; x < px1; x++) {
                int c = buf[row + x];
                ib.put((c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16));
            }
        }
        bb.position(0).limit(bw * bh * 4);
        try {
            com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder()
                    .writeToTexture(texture.getTexture(), bb, 0, 0, px0, py0, bw, bh);
            return true;
        } catch (RuntimeException e) {
            regionWrites = false;
            ButcherClient.LOGGER.warn("Butcher: partial texture writes failed ({}), uploading whole layers instead", e.toString());
            return false;
        }
    }

    /** Source over destination, both straight (non-premultiplied) ARGB. */
    private static int over(int src, int dst) {
        int sa = src >>> 24, da = dst >>> 24;
        if (sa == 255 || da == 0) return src;
        float s = sa / 255f, d = da / 255f * (1f - s);
        float a = s + d;
        int r = Math.round((((src >> 16) & 255) * s + ((dst >> 16) & 255) * d) / a);
        int gg = Math.round((((src >> 8) & 255) * s + ((dst >> 8) & 255) * d) / a);
        int b = Math.round(((src & 255) * s + (dst & 255) * d) / a);
        return (Math.round(a * 255) << 24) | (r << 16) | (gg << 8) | b;
    }
}
