package com.butcher.render;

import com.butcher.anim.ChopTimeline;

/**
 * A canvas in front of another that gathers the scene's pixel squares into layers. Pure - no Minecraft - so the
 * offline tools run the very same logic. Written for a fork's RNG cutscenes (2026-10-24), which lagged.
 *
 * Why: every spark, ray, ring and confetti square of a busy cutscene is a square on the scene's grid, and each
 * went to the GUI as a rectangle of its own - 4,000 to 16,000 a frame at the height of an ending.
 * The game places each GUI element by testing it against the elements already placed, so the cost of a frame grows with
 * the square of their number: that was the lag. Here a square on the grid goes to a {@link Layer} instead (in game a
 * {@link PixelLayer}: one texel a cell, one image to the GUI).
 *
 * Order is kept. Anything else - a block face, a sprite, a line, the drop, the words - first asks {@link #before} with
 * where it will draw; if the open layer has squares there, the layer is cut (drawn now, as it stands) and the next square
 * opens a new one. So what was under a square stays under it, what was over it stays over it, and things drawn elsewhere
 * on the screen do not cut it. Where squares are kept as tiles of 8 x 8 cells, so a spark in one corner does not cut the
 * layer for a block in the other. Squares too big for it (a dimming of the whole screen) and anything off the grid go
 * straight through, after the same check.
 */
public final class BatchCanvas implements ChopScene.Canvas {

    /** Where the squares go. */
    public interface Layer {
        /** A square of whole cells, into the open layer - opening one if none is. */
        void fill(float x, float y, float w, float h, int argb);

        /** Draws the open layer now, in order, and closes it. */
        void cut();
    }

    static final int TILE = 8;

    private ChopScene.Canvas target;
    private Layer layer;
    private float unit = 1f;
    private int sw, sh;
    private int maxCells;
    private boolean open;
    private int tw, th;
    private boolean[] tiles = new boolean[0];
    private int[] set = new int[0];
    private int nSet;

    /** For the log and the tools: squares batched, draws passed straight through, and layers cut, this frame. */
    public int batched, direct, cuts, offscreen;

    /** Starts a frame on a {@code w x h} screen whose grid is {@code unit} pixels, drawing to {@code target}. */
    public void begin(ChopScene.Canvas target, Layer layer, int w, int h, float unit) {
        this.target = target;
        this.layer = layer;
        this.unit = Math.max(1f, unit);
        sw = w;
        sh = h;
        int gw = (int) Math.ceil(w / this.unit), gh = (int) Math.ceil(h / this.unit);
        maxCells = Math.max(64, gw * gh / 6);
        tw = (gw + TILE - 1) / TILE + 1;
        th = (gh + TILE - 1) / TILE + 1;
        if (tiles.length < tw * th) {
            tiles = new boolean[tw * th];
            set = new int[tw * th];
        }
        java.util.Arrays.fill(tiles, false);
        nSet = 0;
        open = false;
        batched = direct = cuts = offscreen = 0;
    }

    /** The frame's end: draws what is left. */
    public void end() {
        if (open) cut();
        target = null;
        layer = null;
    }

    // ------------------------------------------------------------------ the check

    /** Something else is about to draw over [x0, x1] x [y0, y1]: cut the layer first if it has squares there. */
    public void before(float x0, float y0, float x1, float y1) {
        if (!open) return;
        if (!(x1 >= x0) || !(y1 >= y0)) {
            // NaN or nonsense: cut, to be safe.
            cut();
            return;
        }
        if (x1 + unit < 0 || y1 + unit < 0 || x0 - unit > sw || y0 - unit > sh) return;
        float span = unit * TILE;
        int tx0 = clamp((int) Math.floor((x0 - unit) / span), 0, tw - 1), tx1 = clamp((int) Math.floor((x1 + unit) / span), 0, tw - 1);
        int ty0 = clamp((int) Math.floor((y0 - unit) / span), 0, th - 1), ty1 = clamp((int) Math.floor((y1 + unit) / span), 0, th - 1);
        for (int ty = ty0; ty <= ty1; ty++) {
            int row = ty * tw;
            for (int tx = tx0; tx <= tx1; tx++) {
                if (tiles[row + tx]) {
                    cut();
                    return;
                }
            }
        }
    }

    /** Before something over the whole screen. */
    public void beforeAll() {
        if (open) cut();
    }

    private void cut() {
        open = false;
        for (int i = 0; i < nSet; i++) tiles[set[i]] = false;
        nSet = 0;
        cuts++;
        layer.cut();
    }

    private void mark(float x0, float y0, float x1, float y1) {
        float span = unit * TILE;
        int tx0 = clamp((int) Math.floor(x0 / span), 0, tw - 1), tx1 = clamp((int) Math.floor((x1 - 0.01f) / span), 0, tw - 1);
        int ty0 = clamp((int) Math.floor(y0 / span), 0, th - 1), ty1 = clamp((int) Math.floor((y1 - 0.01f) / span), 0, th - 1);
        for (int ty = ty0; ty <= ty1; ty++) {
            int row = ty * tw;
            for (int tx = tx0; tx <= tx1; tx++) {
                int i = row + tx;
                if (!tiles[i]) {
                    tiles[i] = true;
                    set[nSet++] = i;
                }
            }
        }
    }

    private boolean onGrid(float v) {
        float k = v / unit;
        return Math.abs(k - Math.round(k)) < 0.002f;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    // ------------------------------------------------------------------ the canvas

    @Override
    public void fill(float x, float y, float w, float h, int argb) {
        if ((argb >>> 24) < 3 || !(w > 0f) || !(h > 0f)) return;
        // Wholly off the screen it draws nothing - long rays send thousands of squares out there a frame.
        if (x + w <= 0f || y + h <= 0f || x >= sw || y >= sh) {
            offscreen++;
            return;
        }
        if (onGrid(x) && onGrid(y) && onGrid(w) && onGrid(h) && (w / unit) * (h / unit) <= maxCells) {
            layer.fill(x, y, w, h, argb);
            open = true;
            mark(x, y, x + w, y + h);
            batched++;
            return;
        }
        before(x, y, x + w, y + h);
        direct++;
        target.fill(x, y, w, h, argb);
    }

    @Override
    public void quad(String tex, float ox, float oy, float ux, float uy, float vx, float vy, int u0, int v0, int uw, int vh, int argb) {
        if (open) {
            float ax = ox + ux, ay = oy + uy, bx = ox + vx, by = oy + vy, cx = ax + vx, cy = ay + vy;
            before(min4(ox, ax, bx, cx), min4(oy, ay, by, cy), max4(ox, ax, bx, cx), max4(oy, ay, by, cy));
        }
        direct++;
        target.quad(tex, ox, oy, ux, uy, vx, vy, u0, v0, uw, vh, argb);
    }

    @Override
    public void sprite(String tex, float cx, float cy, float w, float h, float rot, int argb) {
        if (open) {
            double r = Math.toRadians(rot);
            float c = Math.abs((float) Math.cos(r)), s = Math.abs((float) Math.sin(r));
            float ex = (w * c + h * s) / 2f, ey = (w * s + h * c) / 2f;
            before(cx - ex, cy - ey, cx + ex, cy + ey);
        }
        direct++;
        target.sprite(tex, cx, cy, w, h, rot, argb);
    }

    @Override
    public void line(float x0, float y0, float x1, float y1, float width, int argb) {
        if (open) {
            float pad = width;
            before(Math.min(x0, x1) - pad, Math.min(y0, y1) - pad, Math.max(x0, x1) + pad, Math.max(y0, y1) + pad);
        }
        direct++;
        target.line(x0, y0, x1, y1, width, argb);
    }

    @Override
    public void item(float cx, float cy, float size, float alpha, float yaw) {
        beforeItem(cx, cy, size);
        direct++;
        target.item(cx, cy, size, alpha, yaw);
    }

    /** Before an item of {@code size} at (cx, cy) - a head, a stack, an icon and its ink. */
    public void beforeItem(float cx, float cy, float size) {
        float r = size * 0.85f + 4f;
        before(cx - r, cy - r, cx + r, cy + r);
    }

    @Override
    public void name(float cx, float cy, float scale, int argb, int letters) {
        // The title is not known here: the whole width, to be safe.
        beforeText(cx, cy, scale, 64);
        direct++;
        target.name(cx, cy, scale, argb, letters);
    }

    @Override
    public void text(String text, float cx, float cy, float scale, int argb) {
        beforeText(cx, cy, scale, text == null ? 0 : text.length());
        direct++;
        target.text(text, cx, cy, scale, argb);
    }

    @Override
    public void nametag(String fallback, float cx, float cy, float scale, int argb) {
        beforeText(cx, cy, scale, fallback == null ? 0 : fallback.length() + 12);
        direct++;
        target.nametag(fallback, cx, cy, scale, argb);
    }

    private void beforeText(float cx, float cy, float scale, int letters) {
        if (!open) return;
        // The game's font: 6 px a letter at most, 7 bold, the plate 8 more; 12 high with the plate, its ink a pixel round.
        float hw = (letters * 7f + 12f) * scale / 2f, hh = 8f * scale;
        before(cx - hw, cy - hh, cx + hw, cy + hh);
    }

    @Override
    public void image(String key, int[] argb, int iw, int ih, float x, float y, float w, float h) {
        before(x, y, x + w, y + h);
        direct++;
        target.image(key, argb, iw, ih, x, y, w, h);
    }

    @Override
    public void imageCached(String key, int[] argb, int iw, int ih, int version, float x, float y, float w, float h, float alpha) {
        before(x, y, x + w, y + h);
        direct++;
        target.imageCached(key, argb, iw, ih, version, x, y, w, h, alpha);
    }

    @Override
    public void imageAffine(String key, int[] argb, int iw, int ih, float ox, float oy, float ux, float uy, float vx, float vy) {
        if (open) {
            float ax = ox + ux, ay = oy + uy, bx = ox + vx, by = oy + vy, cx = ax + vx, cy = ay + vy;
            before(min4(ox, ax, bx, cx), min4(oy, ay, by, cy), max4(ox, ax, bx, cx), max4(oy, ay, by, cy));
        }
        direct++;
        target.imageAffine(key, argb, iw, ih, ox, oy, ux, uy, vx, vy);
    }

    @Override
    public boolean modelItem(String model, float cx, float cy, float size, float alpha, float spin) {
        beforeItem(cx, cy, size);
        direct++;
        return target.modelItem(model, cx, cy, size, alpha, spin);
    }

    // Lookups, no drawing: straight through.

    @Override
    public String creatureSkin(ChopTimeline.Creature creature) {
        return target.creatureSkin(creature);
    }

    @Override
    public String dropSkin() {
        return target.dropSkin();
    }

    @Override
    public String bestiarySkin(String key) {
        return target.bestiarySkin(key);
    }

    @Override
    public String packTexture(String path) {
        return target.packTexture(path);
    }

    @Override
    public int textureSize(String key) {
        return target.textureSize(key);
    }

    @Override
    public float[] frameNow(String key) {
        return target.frameNow(key);
    }

    private static float min4(float a, float b, float c, float d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    private static float max4(float a, float b, float c, float d) {
        return Math.max(Math.max(a, b), Math.max(c, d));
    }
}
