package com.butcher.render;

import com.butcher.anim.NucleusHaul;
import com.butcher.anim.NucleusTimeline;
import com.butcher.anim.NucleusTimeline.Tier;

/**
 * The Vault's spotlight for its three special drops - Divan's Alloy, the Jade Dye, the Quick Claw (the user, 2026-10-28:
 * they "should be the main focus ... dim the background and add more special effects, like the locket and frostbitten
 * dye"). The scene behind has stopped where it was (NucleusScene draws it frozen at the spotlight's start, without its
 * own showcase). Over it: a dark dim and black bars closing in; a flash, rings and a burst as the drop arrives; then the
 * drop large in the middle of the screen, turning slowly in an aura and rays of its colour, motes rising across the screen
 * and sparkles round it; its name stamped a letter at a time, the price counting up, the odds and "your Nth", how much
 * more the haul had. Each has a flourish of its own: gold coins fountaining for the Alloy, paint dripping from the top of
 * the screen for the Dye, three slashes across it for the Claw.
 */
final class NucleusSpotlight {
    private NucleusSpotlight() {}

    private static final ModelRaster ITEM = new ModelRaster();

    static {
        ITEM.texture(1, NucleusScene.ATLAS, NucleusScene.AW, NucleusScene.AH);
    }

    /** The drop's half size at full size (model pixels; the camera's size is the screen's height / 132). */
    static final float HALF = 15f, PITCH = -10f;
    static final long DIM_MS = 380, POP_MS = 560, LETTER_MS = 55;

    static void draw(ChopScene.Canvas c, NucleusTimeline tl, long t, int w, int h) {
        NucleusHaul.Item it = tl.show();
        if (it == null) return;
        long d = t - tl.spotAt();
        float master = tl.master(t);
        if (master <= 0.01f) return;
        ChopScene.sceneAlpha = master;
        SlayerFx.U = SlayerFx.gridFor(h);
        int col = colour(tl);
        float dim = clamp01(d / (float) DIM_MS);
        c.fill(0, 0, w, h, ChopScene.alpha(0.82f * dim * master) | 0x05040A);
        camera(w, h);
        float cx = w / 2f, cy = h * 0.42f;
        float size = ChopScene.size;
        float pop = d < POP_MS ? backOut(d / (float) POP_MS) : 1f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(d / 260.0);

        motes(c, tl, d, w, h, col);
        // The aura: a broad glow, a hotter one, rays turning behind.
        NucleusScene.glow(c, cx, cy, size * HALF * (3.6f + 0.25f * pulse) * pop, col, 0.42f * dim);
        NucleusScene.glow(c, cx, cy, size * HALF * 2f * pop, ChopScene.mix(col, 0xFFFFFF, 0.4f), 0.5f * dim);
        rays(c, cx, cy, col, d, Math.min(1f, d / 500f), size);
        flourish(c, tl, d, w, h, cx, cy, col, size);
        // The arrival: a flash, rings, a burst.
        if (d < 200) NucleusScene.glow(c, cx, cy, size * HALF * 3.2f, 0xFFFFFF, 1f - d / 200f);
        for (int r = 0; r < 3; r++) {
            long e = d - r * 110L;
            if (e < 0 || e > 900) continue;
            float u = e / 900f;
            ring(c, cx, cy, size * (HALF * 0.9f + 85f * (1f - (1f - u) * (1f - u))), SlayerFx.a(0.85f * (1f - u)) | (r == 1 ? 0xFFFFFF : col));
        }
        if (d < 1000) SlayerFx.burst(c, cx, cy, d, 1000, 44, size * 95f, 1200L, new int[]{col, 0xFFFFFF, ChopScene.mix(col, 0xFFFFFF, 0.5f)}, 1f, size * 40f);
        sparkles(c, d, cx, cy, size, col);

        // The drop: big, turning slowly, lit in its colour.
        int[] cell = NucleusScene.cellOf(it.id());
        if (cell != null) {
            float half = HALF * pop;
            float[] m = NucleusModel.moved(NucleusModel.identity(1f, 0f, 0f, 0f), 0f, 0.8f * (float) Math.sin(d / 520.0), 0f);
            m = NucleusModel.yawed(m, 0.5f + 0.32f * (float) Math.sin(d / 950.0) + (1f - Math.min(1f, pop)) * 4f);
            m = NucleusModel.tilted(m, -0.16f);
            NucleusModel.Cam cam = NucleusModel.Cam.current();
            float[] acc = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            NucleusModel.headBounds(m, half * 1.3f, acc, cam);
            float inkW = Math.max(1.2f, size / 1.2f), pad = inkW + 2f;
            if (ITEM.begin(Math.max(0f, acc[0] - pad), Math.max(0f, acc[1] - pad), Math.min(w, acc[2] + pad), Math.min(h, acc[3] + pad),
                    NucleusScene.rasterScale)) {
                NucleusScene.drawItem(ITEM, it.id(), cell, m, half, Math.round(0x18 + 0x22 * pulse) << 24 | col, cam);
                ITEM.ink(Math.max(1, Math.round(inkW * ITEM.scale)));
                ITEM.fade(master);
                c.image("nucleus_spot", ITEM.px, ITEM.w, ITEM.h, ITEM.x, ITEM.y, ITEM.w / ITEM.scale, ITEM.h / ITEM.scale);
            }
        }

        // Black bars closing in.
        float bar = h * 0.085f * (1f - (1f - dim) * (1f - dim));
        c.fill(0, 0, w, bar, ChopScene.alpha(master) | 0x000000);
        c.fill(0, h - bar, w, bar, ChopScene.alpha(master) | 0x000000);
        words(c, tl, t, cx, cy + HALF * size * 1.5f, h, col);
        ChopScene.sceneAlpha = 1f;
    }

    /** The spotlight's own camera: square on, a little above, the drop at the origin in the upper middle. */
    private static void camera(int w, int h) {
        double pitch = Math.toRadians(PITCH);
        ChopScene.cy = 1f;
        ChopScene.sy = 0f;
        ChopScene.cp = (float) Math.cos(pitch);
        ChopScene.sp = (float) Math.sin(pitch);
        ChopScene.size = h / 132f;
        ChopScene.fx = 0f;
        ChopScene.fy = 0f;
        ChopScene.fz = 0f;
        ChopScene.sx0 = w / 2f;
        ChopScene.sy0 = h * 0.42f;
    }

    /** The drop's own colour: the Alloy's gold, the Dye's jade, the Claw's amber. */
    static int colour(NucleusTimeline tl) {
        return switch (tl.tier()) {
            case ALLOY -> 0xFFC93C;
            case JADE -> NucleusTimeline.JADE_COLOUR;
            case CLAW -> NucleusTimeline.CLAW_COLOUR;
            default -> 0xFFFFFF;
        };
    }

    /** Its name stamped a letter at a time where each sits in the whole name; the price counting up; odds, "your Nth", more. */
    private static void words(ChopScene.Canvas c, NucleusTimeline tl, long t, float cx, float y, int h, int col) {
        NucleusHaul.Item it = tl.show();
        long d = t - tl.textAt();
        if (d < 0) return;
        float sc = Math.max(1f, Math.min(1.6f, h / 260f));
        String name = it.name() + (it.count() > 1 ? " x" + it.count() : "");
        int letters = (int) Math.min(name.length(), d / LETTER_MS + 1);
        float big = sc * 1.6f;
        float full = c.textWidth(name, big), part = c.textWidth(name.substring(0, letters), big);
        // the newest letter lands a touch big
        long since = d - (letters - 1) * LETTER_MS;
        float stamp = letters < name.length() || since < 120 ? 1f + 0.12f * Math.max(0f, 1f - since / 120f) : 1f;
        c.text(name.substring(0, letters), cx - full / 2f + part / 2f, y, big * stamp, SlayerFx.a(1f) | it.colour());
        long named = name.length() * LETTER_MS;
        float a1 = clamp01((d - named - 150) / 260f);
        if (a1 > 0f && tl.price() >= 0) {
            float k = clamp01((d - named - 150) / 1000f);
            long shown = Math.round(tl.price() * (1.0 - Math.pow(1.0 - k, 3)));
            c.text(String.format(java.util.Locale.ROOT, "%,d coins", shown), cx, y + 17f * sc, sc * 1.15f, SlayerFx.a(a1) | 0xFFAA00);
        }
        float a2 = clamp01((d - named - 450) / 300f);
        if (a2 <= 0f) return;
        float ly = y + 30f * sc;
        String odds = NucleusHaul.odds(it);
        String nth = NucleusScene.forgeShow == null ? null : NucleusScene.forgeShow.nth;
        if (odds != null || nth != null) {
            c.text((odds == null ? "" : odds) + (odds != null && nth != null ? " · " : "") + (nth == null ? "" : nth), cx, ly, sc * 0.9f,
                    SlayerFx.a(a2 * 0.9f) | 0xDDDDDD);
            ly += 11f * sc;
        }
        if (tl.more() > 0) c.text("+" + tl.more() + " more", cx, ly, sc * 0.8f, SlayerFx.a(a2 * 0.8f) | 0xAAAAAA);
    }

    /** Sixteen wedges of cells turning slowly round the drop, widening outwards. */
    private static void rays(ChopScene.Canvas c, float x, float y, int col, long d, float k, float size) {
        int n = 16;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n + d / 3200.0;
            float len = size * (34f + 12f * (float) Math.sin(d / 340.0 + i * 1.7)) * k;
            int steps = Math.max(1, Math.round(len / SlayerFx.U));
            for (int s = 0; s < steps; s++) {
                float r = size * HALF * 1.15f + s * SlayerFx.U, f = 1f - s / (float) steps;
                SlayerFx.square(c, x + r * (float) Math.cos(a), y + r * (float) Math.sin(a), 1 + Math.round(2.5f * s / steps),
                        SlayerFx.a(0.38f * f * k) | ChopScene.mix(col, 0xFFFFFF, 0.35f * f));
            }
        }
    }

    /** Motes of its colour rising across the whole screen. */
    private static void motes(ChopScene.Canvas c, NucleusTimeline tl, long d, int w, int h, int col) {
        for (int i = 0; i < 64; i++) {
            float life = (d / (2600f + 900f * SlayerFx.rnd(1210, i, 1)) + SlayerFx.rnd(1210, i, 2)) % 1f;
            float x = SlayerFx.rnd(1210, i, 3) * w + 10f * (float) Math.sin(life * 6 + i);
            float y = h * (1.05f - life * 1.1f);
            float a = Math.min(1f, d / 600f) * (float) Math.sin(Math.PI * life) * 0.75f;
            SlayerFx.square(c, x, y, i % 5 == 0 ? 2 : 1, SlayerFx.a(a) | (i % 3 == 0 ? 0xFFFFFF : ChopScene.mix(col, 0xFFFFFF, 0.2f)));
        }
    }

    /** Sparkles round the drop: plus shapes of cells that blink in and out, each in its own place and time. */
    private static void sparkles(ChopScene.Canvas c, long d, float cx, float cy, float size, int col) {
        for (int i = 0; i < 12; i++) {
            float period = 900f + 500f * SlayerFx.rnd(1220, i, 1);
            float ph = ((d + SlayerFx.rnd(1220, i, 2) * period) % period) / period;
            if (ph > 0.4f || d < 300) continue;
            float k = (float) Math.sin(Math.PI * ph / 0.4f);
            double a = SlayerFx.rnd(1220, i, 3) * Math.PI * 2;
            float r = size * HALF * (1.3f + 0.9f * SlayerFx.rnd(1220, i, 4));
            float x = cx + r * (float) Math.cos(a), y = cy + r * (float) Math.sin(a);
            int arm = k > 0.6f ? 2 : 1;
            for (int j = -arm; j <= arm; j++) {
                SlayerFx.square(c, x + j * SlayerFx.U, y, 1, SlayerFx.a(k) | (j == 0 ? 0xFFFFFF : ChopScene.mix(col, 0xFFFFFF, 0.6f)));
                if (j != 0) SlayerFx.square(c, x, y + j * SlayerFx.U, 1, SlayerFx.a(k) | ChopScene.mix(col, 0xFFFFFF, 0.6f));
            }
        }
    }

    /** Each drop's own: the Alloy's gold coins fountaining, the Dye's paint running down from the top, the Claw's slashes. */
    private static void flourish(ChopScene.Canvas c, NucleusTimeline tl, long d, int w, int h, float cx, float cy, int col, float size) {
        if (tl.tier() == Tier.ALLOY) {
            // Coins: thrown up from under it, turning (their width flickering), falling away.
            for (int i = 0; i < 30; i++) {
                float period = 1500f + 600f * SlayerFx.rnd(1230, i, 1);
                float u = ((d - 200 + SlayerFx.rnd(1230, i, 2) * period) % period) / period;
                if (d < 200) continue;
                float vx = (SlayerFx.rnd(1230, i, 3) - 0.5f) * size * 120f, vy = -size * (110f + 60f * SlayerFx.rnd(1230, i, 4));
                float s = u * 1.6f;
                float x = cx + vx * s, y = cy + size * HALF * 0.8f + vy * s + 0.5f * size * 260f * s * s;
                boolean edge = ((int) (d / 90) + i) % 3 == 0;
                int coin = edge ? 0xB8860B : 0xFFD24A;
                SlayerFx.square(c, x, y, 1, SlayerFx.a(1f - u) | coin);
                if (!edge) SlayerFx.square(c, x + SlayerFx.U, y, 1, SlayerFx.a(1f - u) | 0xFFF0A0);
            }
        } else if (tl.tier() == Tier.JADE) {
            // Paint running down from the top bar: drips growing, a bead at each tip, some dropping off.
            for (int i = 0; i < 22; i++) {
                float x = (i + 0.5f + (SlayerFx.rnd(1240, i, 1) - 0.5f) * 0.6f) * w / 22f;
                float start = 250f + 600f * SlayerFx.rnd(1240, i, 2);
                float len = clamp01((d - start) / (1400f + 900f * SlayerFx.rnd(1240, i, 3))) * h * (0.12f + 0.2f * SlayerFx.rnd(1240, i, 4));
                if (len <= 0f) continue;
                float top = h * 0.085f;
                int steps = Math.max(1, Math.round(len / SlayerFx.U));
                for (int s = 0; s < steps; s++) {
                    SlayerFx.square(c, x, top + s * SlayerFx.U, s > steps - 3 ? 2 : 1, SlayerFx.a(0.9f) | (s % 4 == 0 ? 0x2ED48A : col));
                }
                SlayerFx.square(c, x, top + len + SlayerFx.U, 2, SlayerFx.a(0.95f) | 0x9CFFD0);
            }
        } else if (tl.tier() == Tier.CLAW) {
            // Three slashes torn across the screen, one after another, leaving glowing scars.
            for (int k = 0; k < 3; k++) {
                long e = d - 160 - k * 130L;
                if (e < 0) continue;
                float u = clamp01(e / 140f), glow = 0.35f + 0.65f * (1f - clamp01((e - 140) / 500f));
                float off = (k - 1) * w * 0.12f;
                float x0 = cx - w * 0.38f + off, y0 = cy - h * 0.34f, x1 = cx + w * 0.22f + off, y1 = cy + h * 0.42f;
                int n = Math.max(2, Math.round((float) Math.hypot(x1 - x0, y1 - y0) / SlayerFx.U));
                for (int i = 0; i <= n * u; i++) {
                    float f = i / (float) n;
                    float x = x0 + (x1 - x0) * f, y = y0 + (y1 - y0) * f;
                    int th = Math.sin(Math.PI * f) > 0.4 ? 2 : 1;
                    SlayerFx.square(c, x, y, th, SlayerFx.a(0.8f * glow) | ChopScene.mix(col, 0xFFFFFF, 0.5f * glow));
                }
            }
        }
    }

    /** A ring of cells round a screen point. */
    private static void ring(ChopScene.Canvas c, float x, float y, float r, int argb) {
        if ((argb >>> 24) < 3 || r < 1f) return;
        int n = Math.max(12, (int) (Math.PI * 2 * r / SlayerFx.U * 1.2f));
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            SlayerFx.square(c, x + r * (float) Math.cos(a), y + r * (float) Math.sin(a), 1, argb);
        }
    }

    private static float backOut(float u) {
        u = clamp01(u);
        float s = 1.9f;
        float v = u - 1f;
        return 1f + v * v * ((s + 1f) * v + s);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
