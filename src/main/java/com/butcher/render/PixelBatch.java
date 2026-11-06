package com.butcher.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link BatchCanvas}'s layers in the game: a pool of {@link PixelLayer}s, one taken for each run of squares the batch
 * gathers and drawn when the batch cuts it. Each is a texture of its own - the GUI draws them all at the frame's end, so a
 * layer cannot be reused within a frame - and the pool is kept from frame to frame, so nothing is made after the first.
 * Render thread only.
 */
public final class PixelBatch implements BatchCanvas.Layer {
    private final String name;
    private final List<PixelLayer> pool = new ArrayList<>();
    private int used;
    private PixelLayer open;
    private GuiGraphicsExtractor g;
    private int w, h, unit;
    /** For the log: layers drawn last frame. */
    public int lastLayers;

    public PixelBatch(String name) {
        this.name = name;
    }

    /** Starts a frame on a {@code w x h} GUI screen, its grid {@code unit} GUI pixels. */
    public void begin(GuiGraphicsExtractor g, int w, int h, int unit) {
        this.g = g;
        this.w = w;
        this.h = h;
        this.unit = Math.max(1, unit);
        used = 0;
        open = null;
    }

    @Override
    public void fill(float x, float y, float fw, float fh, int argb) {
        if (open == null) {
            if (used == pool.size()) pool.add(new PixelLayer(name + "_" + used));
            open = pool.get(used++);
            open.begin(w, h, unit);
        }
        open.fill(x, y, fw, fh, argb);
    }

    @Override
    public void cut() {
        PixelLayer l = open;
        open = null;
        if (l != null && g != null) l.end(g);
    }

    /** The frame's end. */
    public void end() {
        cut();
        lastLayers = used;
        g = null;
    }
}
