package com.butcher.render;

import com.butcher.anim.SlayerTimeline;
import com.butcher.anim.SlayerTimeline.Ending;

import java.util.ArrayList;
import java.util.List;

import static com.butcher.anim.SlayerTimeline.REVEAL;
import static com.butcher.anim.SlayerTimeline.backOut;
import static com.butcher.anim.SlayerTimeline.clamp01;
import static com.butcher.anim.SlayerTimeline.ease;
import static com.butcher.render.SlayerFx.U;
import static com.butcher.render.SlayerFx.a;
import static com.butcher.render.SlayerFx.rnd;
import static com.butcher.render.SlayerFx.square;

/**
 * The Cocoon's own endings, one per Tarantula drop worth 8M and up (the user's picks, 2026-10-26). Pure; SlayerScene
 * calls in through the hooks below, all after the reveal.
 * <ul>
 *   <li><b>A Thousand Eyes</b> (Primordial Eye): the cocoon splits dark inside; spiders' eyes open along every web on
 *       screen - glossy red beads in pairs, a few and then hundreds, blinking - they pour into the cocoon, and the
 *       Primordial Eye opens in its middle: a cluster of eight, as a spider's face has, the great one in the middle.</li>
 *   <li><b>Red Thread</b> (Brick Red Dye, the grandest): time stops; every strand of every web turns red and reels into
 *       the cocoon, web by web; it becomes a red spool spinning faster and faster, and the dye unwinds out of it.</li>
 *   <li><b>Digested</b> (Digested Mosquito): the cocoon goes see-through, a mosquito dissolves in the venom rising inside,
 *       it bulges and pops.</li>
 *   <li><b>Husk</b> (Shriveled Wasp): the cocoon dries out - browns, shrivels and cracks like paper - and crumbles.</li>
 * </ul>
 */
final class CocoonEndings {
    private CocoonEndings() {}

    static final int BRICK = 0xCB4154, EYE_RED = 0xD81E1E, VENOM = 0x9BE83A, DRY = 0x9A7A4A;

    // ---- A Thousand Eyes (ms after the reveal)
    static final long E_OPEN = 150, E_OPENED = 1900, E_LOOK = 1350, E_POUR = 2150, E_POURED = 2900, E_BIG = 2950, E_BIG_OPEN = 3450;
    // ---- Red Thread
    static final long T_FREEZE = 160, T_REEL = 350, T_REEL_STEP = 430, T_SPOOL = 2650, T_SPOOLED = 3250, T_SPIN = 3250, T_UNWIND = 4550;
    // ---- Digested
    static final long G_FILL = 1250, G_BULGE = 1250, G_POP = 1550;
    // ---- Husk
    static final long H_DRY = 1250, H_CRUMBLE = 1300;

    /** The web points this frame (screen px), recorded only when an ending wants eyes on them. */
    static final List<float[]> WEB_POINTS = new ArrayList<>();

    static Ending ending(SlayerTimeline tl) {
        return tl.drop().win() ? tl.ending() : Ending.BUST;
    }

    // ================================================================== hooks

    static long phys(SlayerTimeline tl, long t) {
        if (ending(tl) != Ending.RED_THREAD) return t;
        long freeze = REVEAL + T_FREEZE, resume = REVEAL + T_UNWIND + 600;
        if (t <= freeze) return t;
        if (t < resume) return freeze;
        return t - (resume - freeze);
    }

    /** One web's {reel 0..1, red 0..1, record eyes 0/1}; webs 0-3 are the corners, 4 the great web behind. */
    static float[] webState(SlayerTimeline tl, long t, int web) {
        long since = t - REVEAL;
        Ending e = ending(tl);
        if (since < 0) return NONE;
        if (e == Ending.THOUSAND_EYES) return since < E_POURED ? RECORD : NONE;
        if (e != Ending.RED_THREAD) return NONE;
        long start = T_REEL + web * T_REEL_STEP;
        float red = clamp01((since - start + 260) / 260f);
        float reel = ease(clamp01((since - start) / (float) T_REEL_STEP));
        return new float[]{reel, red, 0f};
    }

    private static final float[] NONE = {0f, 0f, 0f}, RECORD = {0f, 0f, 1f};

    /** How far the dangling spiders have gone (the Red Thread reels their threads in with the rest). */
    static float danglersGone(SlayerTimeline tl, long t) {
        if (ending(tl) != Ending.RED_THREAD) return 0f;
        return clamp01((t - REVEAL - T_REEL) / 500f);
    }

    /** The thread it hangs by stays for these (the cocoon is not split). */
    static boolean keepsThread(SlayerTimeline tl) {
        Ending e = ending(tl);
        return e == Ending.RED_THREAD || e == Ending.DIGESTED || e == Ending.HUSK;
    }

    static boolean threadRed(SlayerTimeline tl, long t) {
        return ending(tl) == Ending.RED_THREAD && t - REVEAL >= T_REEL;
    }

    static boolean ownsBody(SlayerTimeline tl) {
        Ending e = ending(tl);
        return e == Ending.RED_THREAD || e == Ending.DIGESTED || e == Ending.HUSK;
    }

    // ================================================================== the body, for the endings that keep it

    static void body(ChopScene.Canvas c, SlayerTimeline tl, long t, long since, float sway) {
        switch (ending(tl)) {
            case RED_THREAD -> spool(c, tl, t, since, sway);
            case DIGESTED -> digested(c, tl, t, since, sway);
            case HUSK -> husk(c, tl, t, since, sway);
            default -> { }
        }
    }

    /** A cocoon of layers with its own radius, tint, alpha and turn - the endings' versions of SlayerScene.body. */
    static void layers(ChopScene.Canvas c, float sway, java.util.function.IntFunction<float[]> radius, float r, float g, float b, float alpha,
                       float turn) {
        int n = SlayerScene.LAYER_R.length;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < n; i++) {
                float y = SlayerScene.COCOON_BASE + (i + 0.5f) * SlayerScene.LAYER_H;
                float[] rr = radius.apply(i);
                if (rr[0] <= 0.05f) continue;
                float px = (float) Math.sin(sway) * y + rr[1];
                if (pass == 0) {
                    SlayerScene.inkOnly(px, y + rr[2], 0f, 0f, 0.4f + turn, -sway, rr[0], SlayerScene.LAYER_H * 0.52f, rr[0],
                            SlayerScene.WOOL, SlayerScene.TILE, alpha, c);
                } else {
                    SlayerScene.plain(px, y + rr[2], 0f, 0f, 0.4f + turn, -sway, rr[0], SlayerScene.LAYER_H * 0.52f, rr[0],
                            SlayerScene.WOOL, SlayerScene.TILE, r, g, b, alpha, c);
                }
            }
        }
    }

    // ---- Red Thread: the spool

    static void spool(ChopScene.Canvas c, SlayerTimeline tl, long t, long since, float sway) {
        int n = SlayerScene.LAYER_R.length;
        float form = ease(clamp01((since - T_SPOOL) / (float) (T_SPOOLED - T_SPOOL)));
        // The spin: slow, faster and faster, to a blur.
        float spinT = Math.max(0f, (since - T_SPIN) / 1000f);
        float turn = spinT * spinT * 9f;
        // Red wound on as the webs come in.
        float wound = clamp01((since - T_REEL) / (float) (4 * T_REEL_STEP + T_REEL_STEP));
        float rr = ChopScene.mix(0xEBEBE6, BRICK, wound) >> 16 & 255, gg = ChopScene.mix(0xEBEBE6, BRICK, wound) >> 8 & 255,
                bb = ChopScene.mix(0xEBEBE6, BRICK, wound) & 255;
        float fade = since > T_UNWIND ? clamp01(1f - (since - T_UNWIND) / 700f) : 1f;
        layers(c, sway, i -> {
            float base = SlayerScene.LAYER_R[i];
            float cyl = 3.4f;
            return new float[]{(base + (cyl - base) * form) * (0.4f + 0.6f * fade), 0f, 0f};
        }, rr / 255f, gg / 255f, bb / 255f, fade, turn);
        if (form > 0.05f) {
            // The flanges, dark wood, top and bottom.
            float top = SlayerScene.COCOON_BASE + SlayerScene.LAYER_H * n, bot = SlayerScene.COCOON_BASE;
            float fr = 4.8f * form * (0.4f + 0.6f * fade);
            for (float y : new float[]{bot - 0.2f, top + 0.2f}) {
                float px = (float) Math.sin(sway) * y;
                SlayerScene.inked(px, y, 0f, 0f, 0.4f + turn, -sway, fr, 0.55f, fr, SlayerScene.DARK_OAK, SlayerScene.TILE, 0.7f, 0.62f, 0.54f,
                        form * fade, c);
            }
        }
        // The thread round it: red helices, turning with it.
        for (int s = 0; s < 4 && wound > 0f; s++) {
            for (int q = 0; q <= 70; q++) {
                float f = q / 70f;
                if (f > wound) break;
                float y = SlayerScene.COCOON_BASE + 0.5f + f * (SlayerScene.LAYER_H * n - 1f);
                int li = Math.min(n - 1, Math.max(0, (int) ((y - SlayerScene.COCOON_BASE) / SlayerScene.LAYER_H)));
                float r = (SlayerScene.LAYER_R[li] + (3.4f - SlayerScene.LAYER_R[li]) * form) * (0.4f + 0.6f * fade) + 0.08f;
                double ang = s * 1.57 + f * Math.PI * 2 * 3.1 + turn;
                float x = (float) Math.cos(ang) * r, z = (float) Math.sin(ang) * r;
                if (ChopScene.depth(x, y, z) < ChopScene.depth(0f, y, 0f)) continue;
                ChopScene.project(x + (float) Math.sin(sway) * y, y, z);
                square(c, ChopScene.P[0], ChopScene.P[1], 1, a(0.9f * fade) | ChopScene.mix(BRICK, 0xFF8A90, 0.3f));
            }
        }
    }

    // ---- Digested

    static void digested(ChopScene.Canvas c, SlayerTimeline tl, long t, long since, float sway) {
        if (since >= G_POP) return;
        float bulge = since > G_BULGE ? 1f + 0.28f * ease((since - G_BULGE) / (float) (G_POP - G_BULGE)) : 1f;
        float wob = since > G_BULGE ? 0.12f * (float) Math.sin(since / 25.0) : 0f;
        layers(c, sway, i -> new float[]{SlayerScene.LAYER_R[i] * (bulge + wob * (i % 2 == 0 ? 1 : -1)), 0f, 0f}, 0.92f, 0.94f, 0.88f, 0.38f, 0f);
    }

    // ---- Husk

    static void husk(ChopScene.Canvas c, SlayerTimeline tl, long t, long since, float sway) {
        if (since >= H_CRUMBLE) return;
        float dry = ease(since / (float) H_DRY);
        layers(c, sway, i -> {
            float shrink = 1f - 0.3f * dry * (0.7f + 0.3f * (float) Math.sin(i * 2.3));
            return new float[]{SlayerScene.LAYER_R[i] * shrink, 0.25f * dry * (float) Math.sin(i * 1.7), 0f};
        }, 0.92f - 0.32f * dry, 0.92f - 0.45f * dry, 0.88f - 0.6f * dry, 1f, 0f);
        // Cracks spreading over it: dark lines of cells on the near side.
        for (int k = 0; k < 6; k++) {
            float grow = clamp01(dry * 1.6f - k * 0.12f);
            if (grow <= 0f) continue;
            float y0 = SlayerScene.COCOON_BASE + SlayerScene.LAYER_H * (2.2f + k * 1.3f);
            float x0 = (rnd(tl.seed(), k, 61) - 0.5f) * 3f;
            float px = x0, py = y0;
            for (int s = 0; s < 6; s++) {
                if (s / 6f > grow) break;
                float nx = px + (rnd(tl.seed(), k * 9 + s, 62) - 0.5f) * 2.2f, ny = py + (rnd(tl.seed(), k * 9 + s, 63) - 0.5f) * 1.6f;
                int li = Math.min(SlayerScene.LAYER_R.length - 1, Math.max(0, (int) ((py - SlayerScene.COCOON_BASE) / SlayerScene.LAYER_H)));
                float r = SlayerScene.LAYER_R[li] * (1f - 0.25f * dry);
                ChopScene.project(px + (float) Math.sin(sway) * py, py, (float) Math.sqrt(Math.max(0.1f, r * r - px * px)));
                float ax = ChopScene.P[0], ay = ChopScene.P[1];
                ChopScene.project(nx + (float) Math.sin(sway) * ny, ny, (float) Math.sqrt(Math.max(0.1f, r * r - nx * nx)));
                SlayerFx.pline(c, ax, ay, ChopScene.P[0], ChopScene.P[1], 1, a(0.9f) | 0x3A2A14, s > 0);
                px = nx;
                py = ny;
            }
        }
    }

    // ================================================================== the effects, after the scene

    static void draw(ChopScene.Canvas c, SlayerTimeline tl, SlayerScene.Show show, long t, long since, int w, int h) {
        switch (ending(tl)) {
            case THOUSAND_EYES -> eyes(c, tl, t, since, w, h);
            case RED_THREAD -> redThread(c, tl, t, since, w, h);
            case DIGESTED -> digestedFx(c, tl, t, since, w, h);
            case HUSK -> huskFx(c, tl, t, since, w, h);
            default -> { }
        }
    }

    // ---- A Thousand Eyes

    static void eyes(ChopScene.Canvas c, SlayerTimeline tl, long t, long since, int w, int h) {
        long seed = tl.seed();
        ChopScene.project(0f, 7f, 3f);
        float cx = ChopScene.P[0], cy = ChopScene.P[1];
        // It goes dark round it as they open.
        float dark = clamp01((since - E_OPEN) / 900f) * (since > E_BIG_OPEN ? clamp01(1f - (since - E_BIG_OPEN) / 800f) : 1f);
        c.fill(0, 0, w, h, a(0.35f * dark) | 0x050204);
        SlayerFx.burst(c, cx, cy, since, 900, 26, h * 0.4f, seed + 91, new int[]{0x0A0606, 0x2A1010, EYE_RED}, 1f, h * 0.5f);
        // The eyes, one per chosen web point: a few at first, then hundreds.
        int n = WEB_POINTS.size();
        int shown = 0;
        for (int i = 0; i < n; i++) {
            if (rnd(seed, i, 1) > 0.42f) continue;
            float order = rnd(seed, i, 2);
            long opens = E_OPEN + (long) ((E_OPENED - E_OPEN) * Math.pow(order, 1.8));
            long d = since - opens;
            if (d < 0) continue;
            float[] p = WEB_POINTS.get(i);
            float x = p[0], y = p[1];
            // Poured into the cocoon at the end, each on its own spiral.
            float pour = ease(clamp01((since - E_POUR - order * 300) / (float) (E_POURED - E_POUR - 300)));
            if (pour >= 1f) continue;
            if (pour > 0f) {
                double ang = Math.atan2(y - cy, x - cx) + pour * 2.2;
                float r = (float) Math.hypot(x - cx, y - cy) * (1f - pour);
                x = cx + (float) Math.cos(ang) * r;
                y = cy + (float) Math.sin(ang) * r;
            }
            // Each blinks now and then, on its own time.
            boolean shut = rnd(seed, i, 7 + (int) ((since + order * 900) / 700)) < 0.12f && ((since + (long) (order * 900)) % 700) < 110;
            eye(c, x, y, d, shut, pour > 0f ? 1f - pour * 0.6f : 1f);
            shown++;
        }
        // The great eye in the cocoon.
        if (since >= E_BIG) {
            float open = ease(clamp01((since - E_BIG) / (float) (E_BIG_OPEN - E_BIG)));
            float fade = tl.rise(t) > 0f ? clamp01(1f - tl.rise(t) * 1.6f) : 1f;
            bigEye(c, cx, cy, open, fade, since);
            ChopScene.glow(c, cx, cy, h * 0.3f * open, EYE_RED, 0.5f * open * fade);
        }
    }

    /**
     * A spider's pair of eyes in the dark: two glossy red beads a cell apart, each 2 x 2 with a pale glint on its upper
     * left - a dot as it opens, a dark line as it blinks. No whites and no pupils: those made cartoon eyes (the user).
     */
    static void eye(ChopScene.Canvas c, float x, float y, long d, boolean shut, float alpha) {
        int gx = SlayerFx.g(x), gy = SlayerFx.g(y);
        for (int side = -1; side <= 1; side += 2) {
            int ex = side < 0 ? gx - 2 : gx + 1;
            if (d < 90 || shut) {
                SlayerFx.cells(c, ex, gy, 2, 1, a(alpha * (d < 90 ? d / 90f : 0.8f)) | 0x5A0808);
                continue;
            }
            SlayerFx.cells(c, ex, gy - 1, 2, 2, a(alpha) | EYE_RED);
            SlayerFx.cells(c, ex, gy, 2, 1, a(alpha) | 0x8C0A0A);
            SlayerFx.cells(c, ex, gy - 1, 1, 1, a(alpha) | 0xFF9A90);
        }
    }

    /** A bead of a bigger eye, radius {@code r} cells: a dark rim, red, darker below, a glint up and to the left. */
    static void bead(ChopScene.Canvas c, int gx, int gy, int r, float alpha) {
        if (r <= 0) return;
        for (int j = -r; j <= r; j++) {
            for (int i = -r; i <= r; i++) {
                float d2 = i * i + j * j;
                if (d2 > r * r + r * 0.5f) continue;
                boolean rim = d2 > (r - 1) * (r - 1) + (r - 1) * 0.5f;
                int col = rim ? 0x3A0606 : j > 0 ? 0x9A0C0C : EYE_RED;
                SlayerFx.cells(c, gx + i, gy + j, 1, 1, a(alpha) | col);
            }
        }
        int gl = Math.max(1, r / 2);
        SlayerFx.cells(c, gx - gl, gy - gl, r >= 3 ? 2 : 1, r >= 3 ? 2 : 1, a(alpha) | 0xFFC8C0);
    }

    /** Where the eight eyes of the Primordial Eye sit, in cells from its middle, and how big each is. {x, y, r} */
    static final int[][] CLUSTER = {{0, 0, 5}, {-9, -3, 2}, {9, -3, 2}, {-6, -8, 1}, {6, -8, 1}, {-12, 3, 1}, {12, 3, 1}, {0, -9, 1}};

    /** The Primordial Eye opening in the cocoon: a spider's cluster of eight, the great one first, the rest after it. */
    static void bigEye(ChopScene.Canvas c, float x, float y, float open, float alpha, long since) {
        if (alpha <= 0.02f) return;
        int gx = SlayerFx.g(x), gy = SlayerFx.g(y);
        for (int i = 0; i < CLUSTER.length; i++) {
            int[] e = CLUSTER[i];
            float o = clamp01(open * 1.6f - i * 0.08f);
            bead(c, gx + e[0], gy + e[1], Math.round(e[2] * backOut(o)), alpha);
        }
        // A pulse of it.
        long d = since - E_BIG_OPEN;
        if (d >= 0 && d < 700) SlayerFx.ring(c, x, y, U * (14 + 30 * ease(d / 700f)), U * (10 + 20 * ease(d / 700f)), a((1f - d / 700f) * alpha) | EYE_RED);
    }

    // ---- Red Thread

    static void redThread(ChopScene.Canvas c, SlayerTimeline tl, long t, long since, int w, int h) {
        ChopScene.project(0f, 7f, 3f);
        float cx = ChopScene.P[0], cy = ChopScene.P[1];
        // Time stops: a stopped world, a held breath of grey at the edges.
        float hold = since >= T_FREEZE && since < T_UNWIND + 600 ? 1f : 0f;
        SlayerFx.edgeBands(c, w, h, 0x24181A, 0.3f * hold, 8);
        // The unwinding: a red thread out of the spool, up to where the dye forms.
        if (since >= T_UNWIND) {
            float k = clamp01((since - T_UNWIND) / 700f);
            float ty = cy - h * 0.18f * ease(k);
            int segs = 18;
            for (int i = 0; i < segs; i++) {
                float f0 = i / (float) segs, f1 = (i + 1) / (float) segs;
                float x0 = cx + (float) Math.sin(f0 * 9 + since / 90.0) * U * 3 * (1 - f0), x1 = cx + (float) Math.sin(f1 * 9 + since / 90.0) * U * 3 * (1 - f1);
                SlayerFx.pline(c, x0, cy + (ty - cy) * f0, x1, cy + (ty - cy) * f1, 1, a(1f) | BRICK, i > 0);
            }
            ChopScene.glow(c, cx, ty, h * 0.2f * k, BRICK, 0.5f * k);
        }
    }

    // ---- Digested: the mosquito inside, the venom rising, the pop

    static void digestedFx(ChopScene.Canvas c, SlayerTimeline tl, long t, long since, int w, int h) {
        long seed = tl.seed();
        ChopScene.project(0f, 7.5f, 2f);
        float cx = ChopScene.P[0], cy = ChopScene.P[1];
        if (since < G_POP) {
            float fill = ease(since / (float) G_FILL);
            // Venom rising inside the see-through body: cells within its outline, below the level.
            float bottom = cy + h * 0.17f, top = cy - h * 0.17f;
            float level = bottom + (top - bottom) * fill * 0.85f;
            for (int gy = SlayerFx.g(level); gy <= SlayerFx.g(bottom); gy++) {
                float yy = gy * U;
                float f = (yy - top) / (bottom - top);
                float half = h * 0.1f * (float) Math.sin(Math.PI * clamp01(f));
                int gx0 = SlayerFx.g(cx - half), gx1 = SlayerFx.g(cx + half);
                for (int gx = gx0; gx <= gx1; gx++) {
                    boolean bubble = rnd(seed, gx * 31 + gy, (int) (since / 120)) < 0.05f;
                    SlayerFx.cells(c, gx, gy, 1, 1, a(0.55f) | (bubble ? 0xD8FF9A : gy == SlayerFx.g(level) ? 0xC8F070 : VENOM));
                }
            }
            // The mosquito, dissolving: its cells dropping out as the venom takes it.
            String[] bug = {
                    "...w...w...",
                    "....w.w....",
                    "..wwwbwww..",
                    ".wwww.wwww.",
                    "....bbb....",
                    "...bbbbb...",
                    "....bbb....",
                    "...b.b.b...",
                    "..b..b..b..",
                    ".....p.....",
                    ".....p.....",
                    ".....p....."};
            int bx = SlayerFx.g(cx) - 5, by = SlayerFx.g(cy) - 6;
            for (int r = 0; r < bug.length; r++) {
                for (int q = 0; q < bug[r].length(); q++) {
                    char ch = bug[r].charAt(q);
                    if (ch == '.') continue;
                    float gone = clamp01(fill * 1.4f - rnd(seed, r * 13 + q, 71) * 0.9f);
                    if (gone >= 1f) continue;
                    int col = ch == 'w' ? 0xD8E2E8 : ch == 'p' ? 0x5A2A2A : ChopScene.mix(0x2A2420, VENOM, gone);
                    SlayerFx.cells(c, bx + q, by + r + Math.round(gone * 2), 1, 1, a(0.9f * (1f - gone)) | col);
                }
            }
        } else {
            // The pop: wool chunks and venom everywhere.
            long d = since - G_POP;
            SlayerFx.burst(c, cx, cy, d, 900, 40, h * 0.8f, seed + 93, new int[]{VENOM, 0xC8F070, 0x5CB01A}, 1f, h * 1.1f);
            float k = d / 900f;
            if (k < 1.2f) {
                for (int i = 0; i < 9; i++) {
                    double ang = rnd(seed, i, 95) * Math.PI * 2;
                    float sp = 10f + 8f * rnd(seed, i, 96);
                    float x = (float) Math.cos(ang) * sp * k, y = 7f + (float) Math.sin(ang) * sp * k * 0.6f - 16f * k * k, z = 3f;
                    SlayerScene.inked(x, y, z, 3f * k, 2f * k * i, k, 1.1f, 0.8f, 1.1f, SlayerScene.WOOL, SlayerScene.TILE, 0.9f, 0.92f, 0.86f,
                            clamp01(1.2f - k), c);
                }
            }
        }
    }

    // ---- Husk: flakes, then the crumble

    static void huskFx(ChopScene.Canvas c, SlayerTimeline tl, long t, long since, int w, int h) {
        long seed = tl.seed();
        ChopScene.project(0f, 7f, 3f);
        float cx = ChopScene.P[0], cy = ChopScene.P[1];
        // Dry flakes off it all the while.
        for (int i = 0; i < 18; i++) {
            float life = 900f + 500f * rnd(seed, i, 101);
            float ph = ((since + rnd(seed, i, 102) * life) % life) / life;
            if (since > H_CRUMBLE + 600) break;
            float x = cx + (rnd(seed, i, 103) - 0.5f) * h * 0.2f + (float) Math.sin(ph * 6 + i) * U * 2, y = cy - h * 0.1f + ph * h * 0.35f;
            square(c, x, y, 1, a((1f - ph) * 0.8f) | (i % 2 == 0 ? DRY : 0xC8A878));
        }
        if (since >= H_CRUMBLE) {
            long d = since - H_CRUMBLE;
            float k = d / 1000f;
            // It falls apart: chunks of dry silk tumbling down, dust.
            for (int i = 0; i < 14 && k < 1.3f; i++) {
                float x = (rnd(seed, i, 111) - 0.5f) * 8f + (rnd(seed, i, 112) - 0.5f) * 6f * k;
                float y = SlayerScene.COCOON_BASE + 2f + rnd(seed, i, 113) * 13f - 20f * k * k;
                SlayerScene.inked(x, y, 1f, 2f * k * i, k, 3f * k, 0.9f, 0.6f, 0.9f, SlayerScene.WOOL, SlayerScene.TILE, 0.62f, 0.5f, 0.34f,
                        clamp01(1.3f - k), c);
            }
            SlayerFx.burst(c, cx, cy, d, 900, 30, h * 0.3f, seed + 117, new int[]{DRY, 0xC8A878, 0x6A5030}, 0.9f, h * 0.5f);
        }
    }
}
