package com.butcher.render;

import static com.butcher.anim.SlayerTimeline.clamp01;

/**
 * Pixel helpers for The Grave and The Cocoon: everything is whole cells on one grid ({@link #U} GUI px, a texel of the
 * scene), drawn through ChopScene's box renderer and Canvas. Pure. From a viewer's fork's RngScene (2026-10-26), minus
 * its lightning and full-screen flashes (the user, and the project's rule: never a full-screen white or grey fill).
 */
final class SlayerFx {
    private SlayerFx() {}

    /** The grid, in GUI px: set each frame from the screen's height. */
    static float U = 2f;

    static float gridFor(int h) {
        return Math.max(2, Math.round(h / 120f));
    }

    static int a(float alpha) {
        return ChopScene.alpha(clamp01(alpha) * ChopScene.sceneAlpha);
    }

    static float rnd(long seed, int i, int k) {
        return ChopScene.rnd((int) (seed ^ (seed >>> 32)) + i * 7919, k * 104729 + 17);
    }

    static int g(float v) {
        return (int) Math.floor(v / U);
    }

    /** A rectangle of whole cells, its corner the cell (gx, gy). */
    static void cells(ChopScene.Canvas c, int gx, int gy, int w, int h, int argb) {
        if ((argb >>> 24) < 3 || w <= 0 || h <= 0) return;
        c.fill(gx * U, gy * U, w * U, h * U, argb);
    }

    /** A square of {@code n} cells centred near (x, y), snapped to the grid. */
    static void square(ChopScene.Canvas c, float x, float y, int n, int argb) {
        if ((argb >>> 24) < 3 || n <= 0) return;
        cells(c, Math.round(x / U - n / 2f), Math.round(y / U - n / 2f), n, n, argb);
    }

    /** Any rectangle snapped out to whole cells. */
    static void grid(ChopScene.Canvas c, float x, float y, float w, float h, int argb) {
        if ((argb >>> 24) < 3) return;
        int gx = Math.round(x / U), gy = Math.round(y / U);
        int gw = Math.max(1, Math.round((x + w) / U) - gx), gh = Math.max(1, Math.round((y + h) / U) - gy);
        cells(c, gx, gy, gw, gh, argb);
    }

    /** A line of cells, {@code thick} across, each cell once. */
    static void pline(ChopScene.Canvas c, float x0, float y0, float x1, float y1, int thick, int argb, boolean skipFirst) {
        if ((argb >>> 24) < 3) return;
        thick = Math.max(1, thick);
        int ax = g(x0), ay = g(y0), bx = g(x1), by = g(y1);
        int dx = bx - ax, dy = by - ay, steps = Math.max(Math.abs(dx), Math.abs(dy));
        int off = thick / 2;
        boolean horiz = Math.abs(dx) >= Math.abs(dy);
        for (int i = skipFirst ? 1 : 0; i <= steps; i++) {
            int gx = steps == 0 ? ax : ax + Math.round(dx * i / (float) steps), gy = steps == 0 ? ay : ay + Math.round(dy * i / (float) steps);
            if (horiz) cells(c, gx, gy - off, 1, thick, argb);
            else cells(c, gx - off, gy, thick, 1, argb);
        }
    }

    static void pline(ChopScene.Canvas c, float x0, float y0, float x1, float y1, int thick, int argb) {
        pline(c, x0, y0, x1, y1, thick, argb, false);
    }

    /** An ellipse of cells {@code w x h} across, one cell thick, each cell once (no doubled corners). */
    static void ring(ChopScene.Canvas c, float cx, float cy, float w, float h, int argb) {
        if ((argb >>> 24) < 3 || w < U * 2) return;
        float rx = w / 2f, ry = h / 2f;
        int n = (int) (Math.PI * 2 * Math.max(rx, ry) / U * 1.4f) + 8;
        int lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE, firstX = 0, firstY = 0;
        for (int i = 0; i < n; i++) {
            double ang = i * Math.PI * 2 / n;
            int gx = g(cx + (float) Math.cos(ang) * rx), gy = g(cy + (float) Math.sin(ang) * ry);
            if (i == 0) {
                firstX = gx;
                firstY = gy;
            } else if ((gx == lastX && gy == lastY) || (i == n - 1 && gx == firstX && gy == firstY)) {
                continue;
            }
            lastX = gx;
            lastY = gy;
            cells(c, gx, gy, 1, 1, argb);
        }
    }

    /**
     * The glow round the screen's edges as pixel art: {@code bands} rings of cells, each a step weaker inwards (in place
     * of the fork's smooth vignette sprite).
     */
    static void edgeBands(ChopScene.Canvas c, int w, int h, int rgb, float strength, int bands) {
        if (strength < 0.01f) return;
        int gw = (int) Math.ceil(w / U), gh = (int) Math.ceil(h / U);
        for (int b = 0; b < bands; b++) {
            float k = 1f - b / (float) bands;
            int argb = a(strength * k * k) | (rgb & 0xFFFFFF);
            cells(c, b, b, gw - 2 * b, 1, argb);
            cells(c, b, gh - 1 - b, gw - 2 * b, 1, argb);
            cells(c, b, b + 1, 1, gh - 2 * b - 2, argb);
            cells(c, gw - 1 - b, b + 1, 1, gh - 2 * b - 2, argb);
        }
    }

    /** Hard-edged rays of cells turning slowly round (x, y): a win's light. */
    static void rays(ChopScene.Canvas c, float x, float y, int h, long t, float alpha, int colA, int colB, int n, float reach) {
        if (alpha <= 0.01f) return;
        float turn = t / 2600f;
        float len = h * reach;
        for (int i = 0; i < n; i++) {
            float ang = turn + i * (float) Math.PI * 2f / n;
            float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
            for (float r = h * 0.08f; r < len; r += U * 1.5f) {
                float fall = 1f - r / len;
                // Stepped: three strengths, not a smooth fade.
                float st = fall > 0.66f ? 0.7f : fall > 0.33f ? 0.42f : 0.18f;
                square(c, x + cs * r, y + sn * r * 0.8f, fall > 0.6f ? 2 : 1, a(alpha * st) | (i % 2 == 0 ? colA : colB));
            }
        }
    }

    /** A column of light up out of (x, y): three widths of cells, brightest in the middle, rippling. */
    static void beam(ChopScene.Canvas c, float x, float y, float height, float width, long t, int rgb, float alpha) {
        if (alpha <= 0.01f) return;
        int gx = g(x), gTop = g(y - height), gBot = g(y);
        int half = Math.max(1, Math.round(width / U / 2f));
        for (int gy = gTop; gy <= gBot; gy++) {
            float up = (gBot - gy) / (float) Math.max(1, gBot - gTop);
            float fall = 1f - up;
            int ripple = ((gy + (int) (t / 70)) % 5 == 0) ? 1 : 0;
            cells(c, gx - half - 1 - ripple, gy, 2 * half + 3 + 2 * ripple, 1, a(alpha * 0.16f * fall) | rgb);
            cells(c, gx - half, gy, 2 * half + 1, 1, a(alpha * 0.4f * fall) | rgb);
            cells(c, gx - Math.max(0, half - 1), gy, 2 * Math.max(0, half - 1) + 1, 1, a(alpha * 0.75f * fall) | ChopScene.mix(rgb, 0xFFFFFF, 0.55f));
        }
    }

    /**
     * Light pouring up out of an opening - the quad (near edge nx0..nx1, far edge fx0..fx1, on screen) - as a cone: as
     * wide as the opening where it leaves it and widening as it rises (the user: a cone, not a square), stepping down
     * to nothing, a few rays leaning out along its sides. Cells on the grid, so it goes into the batch.
     */
    static void cone(ChopScene.Canvas c, float nx0, float ny0, float nx1, float ny1, float fx0, float fy0, float fx1, float fy1,
                     float height, long t, int rgb, float alpha, long seed) {
        if (alpha <= 0.01f || height < U) return;
        float left = Math.min(nx0, fx0), right = Math.max(nx1, fx1);
        float cx = (left + right) / 2f, hw0 = (right - left) / 2f, spread = 0.42f;
        float soft = 3f * U;
        int steps = 7;
        int g0 = g(cx - hw0 - spread * height), g1 = g(cx + hw0 + spread * height);
        for (int gx = g0; gx <= g1; gx++) {
            float x = (gx + 0.5f) * U, off = Math.abs(x - cx);
            float near = along(nx0, ny0, nx1, ny1, x), far = along(fx0, fy0, fx1, fy1, x);
            // The veil over the inside of the opening.
            float inEdge = Math.min(1f, Math.min((x - left) / soft, (right - x) / soft));
            int gn = g(near), gf = g(far);
            if (inEdge > 0f && gn > gf) cells(c, gx, gf, 1, gn - gf, a(alpha * inEdge * 0.2f) | ChopScene.mix(rgb, 0xFFFFFF, 0.4f));
            // The cone: this column's own height, a little alive; lit from where the cone has grown out to it.
            float hc = height * (0.74f + 0.26f * rnd(seed, gx, 61)) * (1f + 0.08f * (float) Math.sin(t / 260.0 + gx * 0.7));
            for (int b = 0; b < steps; b++) {
                float d0 = hc * b / steps, d1 = hc * (b + 1) / steps;
                float width = hw0 + spread * (d0 + d1) / 2f;
                float edge = Math.min(1f, (width - off) / soft);
                if (edge <= 0f) continue;
                // Where the cone's side crosses this column, the cell starts there, not at the opening.
                float from = Math.max(d0, (off - hw0) / spread);
                if (from >= d1) continue;
                int ya = g(far - d1), yb = g(far - from);
                float f = 1f - b / (float) steps;
                cells(c, gx, ya, 1, Math.max(1, yb - ya), a(alpha * edge * 0.42f * f * (1f - 0.35f * off / (width + 1f))) | ChopScene.mix(rgb, 0xFFFFFF, 0.5f * f));
            }
        }
        // Rays: lines of cells leaning out from the opening, the outer ones further, flickering.
        int rays = 7;
        for (int i = 0; i < rays; i++) {
            float k = (i + 0.5f) / rays * 2f - 1f;
            float bx = cx + k * hw0 * 0.8f, by = along(fx0, fy0, fx1, fy1, bx);
            float lean = k * spread * 1.15f, len = height * (0.55f + 0.35f * rnd(seed, i, 65));
            float fl = 0.55f + 0.45f * (float) Math.sin(t / 120.0 + rnd(seed, i, 63) * 9.0);
            int n = (int) (len / U);
            for (int s = 0; s < n; s++) {
                float d = s * U, f = 1f - s / (float) n;
                cells(c, g(bx + lean * d), g(by - d), 1, 1, a(alpha * 0.32f * fl * f) | ChopScene.mix(rgb, 0xFFFFFF, 0.6f));
            }
        }
    }

    /** The y of the line (x0, y0) - (x1, y1) at {@code x}, held at its ends past them. */
    private static float along(float x0, float y0, float x1, float y1, float x) {
        if (Math.abs(x1 - x0) < 1e-3f) return (y0 + y1) / 2f;
        float k = Math.max(0f, Math.min(1f, (x - x0) / (x1 - x0)));
        return y0 + (y1 - y0) * k;
    }

    /** Squares flung out of (x, y) and falling: dust, chips, sparks. {@code ms} the life. */
    static void burst(ChopScene.Canvas c, float x, float y, long since, long ms, int n, float speed, long seed, int[] cols, float alpha, float gravity) {
        if (since < 0 || since > ms) return;
        float s = since / 1000f, k = since / (float) ms;
        for (int i = 0; i < n; i++) {
            double ang = rnd(seed, i, 1) * Math.PI * 2;
            float v = speed * (0.35f + 0.65f * rnd(seed, i, 2));
            float px = x + (float) Math.cos(ang) * v * s, py = y + (float) Math.sin(ang) * v * s * 0.7f - 0.4f * speed * s + gravity * s * s;
            square(c, px, py, rnd(seed, i, 3) < 0.3f ? 2 : 1, a(alpha * (1f - k)) | cols[i % cols.length]);
        }
    }

    static final int[] RAINBOW = {0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x5555FF, 0xFF55FF};
}
