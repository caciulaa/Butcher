package com.butcher.render;

/**
 * Crop Fever's screen effect - a fevered glow round the edges while it lasts, from a viewer's fork (2026-10-26), its soft
 * vignette redrawn as stepped pixel bands. Pure: no Minecraft; {@link CropFeverOverlay} plays it in the game, under the
 * HUD's own pop-ups.
 *
 * Nothing touches the middle of the screen, where the crops are:
 * <ul>
 *   <li>the edges burn - bands of pixel cells round the frame, stepping down in strength inwards, beating like a
 *       fevered heart (a double beat), the colour drifting gold to orange to rose; in the last ten seconds it beats
 *       faster and reddens;</li>
 *   <li>sparks of it rise up the two sides, pixel squares as the game's own particles;</li>
 *   <li>a hairline timer across the top, shrinking to the middle as the minute runs out;</li>
 *   <li>as it starts, "CROP FEVER!" stamped high in the middle for a moment, a burst off the edges; as it is cured the
 *       glow cools to blue and goes.</li>
 * </ul>
 */
public final class CropFeverScene {
    private CropFeverScene() {}

    /** One fever: its length, and when (ms after its start) it was cured - -1 while it lasts. */
    public static final class Fever {
        public long duration = 60_000;
        public long curedAt = -1;
        public long seed;
    }

    static final int GOLD = 0xFFB52E, ORANGE = 0xFF7A1E, ROSE = 0xFF4F6A, COOL = 0x6FB4FF;

    /** How long it shows: to its end and the fade after, or the cure's fade. */
    public static long end(Fever f) {
        return f.curedAt >= 0 ? f.curedAt + 1200 : f.duration + 1500;
    }

    /** Draws the fever at {@code t} ms after it started on a {@code w x h} GUI screen. */
    public static void draw(ChopScene.Canvas c, Fever f, long t, int w, int h) {
        if (t < 0 || t >= end(f)) return;
        float in = clamp01(t / 600f);
        float out = f.curedAt >= 0 ? 1f - clamp01((t - f.curedAt) / 1200f) : clamp01((f.duration + 1500 - t) / 1500f);
        float alpha = Math.min(in, out);
        if (alpha <= 0.01f) return;
        long left = Math.max(0, f.duration - t);
        float late = f.curedAt >= 0 ? 0f : clamp01(1f - left / 10_000f);
        float cool = f.curedAt >= 0 ? clamp01((t - f.curedAt) / 500f) : 0f;

        // The heartbeat: a double beat, quicker in the last ten seconds.
        long period = Math.round(860 - 300 * late);
        long ph = t % period;
        float beat = pulse(ph, 0, 160) + 0.6f * pulse(ph, 190, 150);
        int col = mix(cycle(t), 0xFF2A1E, late * 0.7f);
        col = mix(col, COOL, cool);

        // ---- the burning edges: six stepped bands of whole cells, strongest at the edge, a ragged inner rim
        int u = Math.max(2, Math.round(h / 130f));
        float glow = (0.40f + 0.18f * beat + 0.12f * late) * alpha;
        bands(c, f.seed, t, w, h, u, col, glow);

        // ---- sparks up the sides
        int n = 44;
        for (int i = 0; i < n; i++) {
            float period2 = 2600f + 1800f * rnd(f.seed, i, 1);
            float p2 = ((t + rnd(f.seed, i, 2) * period2) % period2) / period2;
            boolean leftSide = i % 2 == 0;
            float band = w * 0.1f;
            float x = leftSide ? band * rnd(f.seed, i, 3) * (1f - 0.4f * p2) : w - band * rnd(f.seed, i, 3) * (1f - 0.4f * p2);
            x += (float) Math.sin(t / 380.0 + i) * u;
            float y = h * (1.02f - 1.0f * p2);
            float sa = alpha * (float) Math.sin(Math.PI * p2) * (0.6f + 0.4f * beat) * (1f - cool);
            if (sa < 0.03f) continue;
            int sc = i % 4 == 0 ? 0xFFF4C0 : i % 4 == 1 ? GOLD : i % 4 == 2 ? ORANGE : col;
            boolean tall = ((t / 150 + i) % 3) == 0;
            c.fill(snap(x, u), snap(y, u), u, tall ? u * 2 : u, a(sa) | sc);
        }

        // ---- the timer: a hairline across the top, shrinking to the middle
        if (f.curedAt < 0) {
            float frac = clamp01(left / (float) f.duration);
            float tw = w * 0.5f * frac;
            int ty = 1;
            c.fill(w / 2f - w * 0.25f, ty, w * 0.5f, 2, a(0.22f * alpha) | 0x1A0E04);
            boolean blink = left < 5000 && (t / 250) % 2 == 0;
            int tc = mix(GOLD, 0xFF3A2A, late);
            if (!blink) c.fill(w / 2f - tw / 2f, ty, tw, 2, a(0.85f * alpha) | tc);
        }

        // ---- the start: CROP FEVER! stamped high in the middle, a burst off the edges
        if (t < 1900) {
            float k = clamp01(t / 260f);
            float stamp = 1f + 0.6f * (1f - backOut(k));
            float sa = alpha * clamp01((1900 - t) / 500f) * clamp01(t / 120f);
            float jitter = t < 500 ? (float) Math.sin(t * 0.9) * 1.5f * (1f - t / 500f) : 0f;
            int tc = mix(GOLD, 0xFFF4C0, 0.35f + 0.35f * beat);
            c.text("§lCROP FEVER!", w / 2f + jitter, h * 0.22f, 2.2f * stamp, a(sa) | tc);
            c.text((f.duration / 1000) + " seconds", w / 2f, h * 0.22f + 16f, 1f, a(sa * 0.85f) | 0xF0D8A0);
            long d = t - 60;
            if (d >= 0 && d < 900) {
                float bk = d / 900f;
                for (int i = 0; i < 40; i++) {
                    double ang = rnd(f.seed, i, 7) * Math.PI * 2;
                    float r = Math.max(w, h) * (0.62f - 0.42f * ease(bk));
                    float x = w / 2f + (float) Math.cos(ang) * r, y = h / 2f + (float) Math.sin(ang) * r * 0.6f;
                    c.fill(snap(x, u), snap(y, u), u, u, a(alpha * (1f - bk) * 0.8f) | (i % 2 == 0 ? GOLD : 0xFFF4C0));
                }
            }
        }
        // ---- the cure
        if (f.curedAt >= 0 && t - f.curedAt < 1200) {
            float ca = clamp01(1f - (t - f.curedAt) / 1200f);
            c.text("Crop Fever cured", w / 2f, 10f, 1f, a(ca * 0.9f) | 0xBFE0FF);
        }
    }

    static final int BANDS = 6;

    /**
     * The glow round the frame as pixel art: {@link #BANDS} rings of cells, each a step weaker than the one outside it,
     * the innermost ragged (a cell in or out by column, shifting slowly) so it never reads as a drawn rectangle.
     */
    static void bands(ChopScene.Canvas c, long seed, long t, int w, int h, int u, int col, float glow) {
        if (glow < 0.01f) return;
        int gw = (w + u - 1) / u, gh = (h + u - 1) / u;
        for (int b = 0; b < BANDS; b++) {
            float k = 1f - b / (float) BANDS;
            int argb = a(glow * k * k * 0.9f) | (b == 0 ? mix(col, 0xFFE9A8, 0.3f) : col);
            // Top and bottom rows of this band, then the two side columns between them.
            c.fill(b * u, b * u, (gw - 2 * b) * u, u, argb);
            c.fill(b * u, (gh - 1 - b) * u, (gw - 2 * b) * u, u, argb);
            c.fill(b * u, (b + 1) * u, u, (gh - 2 * b - 2) * u, argb);
            c.fill((gw - 1 - b) * u, (b + 1) * u, u, (gh - 2 * b - 2) * u, argb);
        }
        // The ragged rim: odd cells one band further in, flickering a little.
        int argb = a(glow * 0.12f) | col;
        long slot = t / 220;
        for (int x = BANDS; x < gw - BANDS; x++) {
            if (rnd(seed + slot, x, 11) < 0.35f) c.fill(x * u, BANDS * u, u, u, argb);
            if (rnd(seed + slot, x, 12) < 0.35f) c.fill(x * u, (gh - 1 - BANDS) * u, u, u, argb);
        }
        for (int y = BANDS + 1; y < gh - BANDS - 1; y++) {
            if (rnd(seed + slot, y, 13) < 0.35f) c.fill(BANDS * u, y * u, u, u, argb);
            if (rnd(seed + slot, y, 14) < 0.35f) c.fill((gw - 1 - BANDS) * u, y * u, u, u, argb);
        }
    }

    /** The colour drifting gold - orange - rose - gold, a full turn every six seconds. */
    static int cycle(long t) {
        float k = (t % 6000) / 6000f * 3f;
        if (k < 1f) return mix(GOLD, ORANGE, k);
        if (k < 2f) return mix(ORANGE, ROSE, k - 1f);
        return mix(ROSE, GOLD, k - 2f);
    }

    static float pulse(long ph, long at, long ms) {
        long d = ph - at;
        if (d < 0 || d > ms) return 0f;
        float k = d / (float) ms;
        return (float) Math.sin(Math.PI * k);
    }

    static float snap(float v, int u) {
        return Math.round(v / u) * u;
    }

    static int a(float alpha) {
        return Math.round(255 * clamp01(alpha)) << 24;
    }

    static float rnd(long seed, int i, int k) {
        long h = seed * 6364136223846793005L + i * 1442695040888963407L + k * 0x9E3779B97F4A7C15L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return (h >>> 40) / (float) (1L << 24);
    }

    static int mix(int x, int y, float k) {
        k = clamp01(k);
        int r = Math.round((x >> 16 & 255) + ((y >> 16 & 255) - (x >> 16 & 255)) * k);
        int g = Math.round((x >> 8 & 255) + ((y >> 8 & 255) - (x >> 8 & 255)) * k);
        int b = Math.round((x & 255) + ((y & 255) - (x & 255)) * k);
        return r << 16 | g << 8 | b;
    }

    static float clamp01(float x) {
        return x < 0f ? 0f : (x > 1f ? 1f : x);
    }

    static float ease(float x) {
        x = clamp01(x);
        return x * x * (3f - 2f * x);
    }

    static float backOut(float x) {
        x = clamp01(x);
        float c1 = 1.70158f, c3 = c1 + 1f, y = x - 1f;
        return 1f + c3 * y * y * y + c1 * y * y;
    }
}
