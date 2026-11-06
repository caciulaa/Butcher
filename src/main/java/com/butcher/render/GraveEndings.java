package com.butcher.render;

import com.butcher.anim.SlayerTimeline;
import com.butcher.anim.SlayerTimeline.Ending;

import static com.butcher.anim.SlayerTimeline.REVEAL;
import static com.butcher.anim.SlayerTimeline.backOut;
import static com.butcher.anim.SlayerTimeline.bounce;
import static com.butcher.anim.SlayerTimeline.clamp01;
import static com.butcher.anim.SlayerTimeline.ease;
import static com.butcher.anim.SlayerTimeline.easeIn;
import static com.butcher.anim.SlayerTimeline.easeOut;
import static com.butcher.anim.SlayerTimeline.settle;
import static com.butcher.render.SlayerFx.U;
import static com.butcher.render.SlayerFx.a;
import static com.butcher.render.SlayerFx.rnd;
import static com.butcher.render.SlayerFx.square;

/**
 * The Grave's own endings, one per Revenant drop worth 8M and up (the user's picks, 2026-10-26). Pure; SlayerScene calls
 * in at the points marked by the hooks below, all of them after the reveal - nothing here touches a frame before it.
 * <ul>
 *   <li><b>Grab</b> (Severed Hand): a rotting hand punches up through the lid, snatches the shovel and drags it under,
 *       then rises holding the drop.</li>
 *   <li><b>Reaper</b> (Scythe Blade): the scythe drops in from above, raised to the right, and is drawn back with a
 *       tremble; then one swing like a pendulum's, square to the camera, through the stone and up the other side,
 *       leaving a smear as Diana's cleaver does; the top of the stone tips off backwards and falls flat in the grass.</li>
 *   <li><b>Shredded</b> (Shard of the Shredded): claw marks rip into the lid three times, then it blows apart in strips.</li>
 *   <li><b>The Deep Dark</b> (Warden Heart): sculk spreads over the graveyard, pulsing on a heartbeat; the Warden climbs
 *       out of the coffin, roars a sonic boom at the camera, and sinks back leaving its heart beating in the air.</li>
 *   <li><b>The Wake</b> (Matcha Dye, the grandest): time stops; every grave in the yard bursts and the dead climb out to
 *       face the coffin; matcha light paints the whole graveyard; they raise their arms as the dye rises on a column.</li>
 * </ul>
 */
final class GraveEndings {
    private GraveEndings() {}

    static final int MATCHA = 0x74A12E, SCULK = 0x0B3A44, SCULK_LIT = 0x2AD2E6, GOLD = 0xFFAA00;

    // ---- Grab (ms after the reveal)
    static final long G_PUNCH = 200, G_REACH = 380, G_GRAB = 620, G_UNDER = 1000, G_RISE = 1450;
    // ---- Reaper: dropped in by R_SET, drawn back to R_DRAWN, the swing (through the stone at its middle), gone.
    // 0.3.24 had it slow, swung flat about a grip off to the side - its path read wrong from above (the user, 0.3.25).
    static final long R_IN = 80, R_SET = 380, R_DRAWN = 600, R_SWING = 340, R_CUT = R_DRAWN + R_SWING / 2, R_GONE = 1260;
    // ---- Shredded
    static final long[] S_CLAWS = {150, 480, 810};
    static final long S_BURST = 1150;
    // ---- The Deep Dark
    static final long[] D_BEATS = {450, 1100, 1750, 2400, 3050, 3700};
    static final long D_SPREAD = 2300, D_RISE = 1250, D_RISEN = 2150, D_ROAR = 2450, D_SINK = 3000, D_SUNK = 3600;
    // ---- The Wake
    static final long W_FREEZE = 200, W_GRAVES = 420, W_GRAVE_STEP = 170, W_CLIMB = 650, W_PAINT = 1500, W_PAINTED = 3600,
            W_ARMS = 3700, W_COLUMN = 3950;

    /** The Warden (slot 0) and the zombies (slot 1), each through its own depth-buffered image. */
    static final BoxRaster WARDEN = new BoxRaster(), DEAD = new BoxRaster();
    static final String TEX_WARDEN_IMAGE = "slayer_warden_raster", TEX_DEAD_IMAGE = "slayer_dead_raster";
    /** Image pixels per GUI pixel for the rasterised models (the screen sets it from the GUI scale). */
    static int rasterScale = 2;

    static Ending ending(SlayerTimeline tl) {
        return tl.drop().win() ? tl.ending() : Ending.BUST;
    }

    // ================================================================== hooks

    /** Whether the ending draws the lid itself after the reveal. */
    static boolean ownsLid(SlayerTimeline tl) {
        Ending e = ending(tl);
        return e == Ending.GRAB || e == Ending.SHREDDED || e == Ending.WAKE;
    }

    /** Whether the coffin is open to the sky: a lid gone (blown off, or the Shredded's in strips), not a bust's. */
    static boolean coffinOpen(SlayerTimeline tl, long t) {
        long since = t - REVEAL;
        Ending e = ending(tl);
        if (since < 0 || e == Ending.BUST || e == Ending.GRAB) return false;
        if (e == Ending.SHREDDED) return since >= S_BURST;
        return since >= 60;
    }

    /**
     * The light inside the open coffin: {colour as float bits, 0..1}. A win's own colour, from the moment it is
     * opened (or, for the Reaper and the Shredded, once the scythe has passed and the lid has burst); none on a bust.
     */
    static float[] coffinGlow(SlayerTimeline tl, long t) {
        long since = t - REVEAL;
        Ending e = ending(tl);
        if (since < 0 || !coffinOpen(tl, t)) return new float[]{0f, 0f};
        float fade = tl.fadeOut(t);
        int rgb = tl.drop().colour;
        float k = switch (e) {
            case PLAIN -> clamp01((since - 60) / 250f);
            case REAPER -> {
                rgb = GOLD;
                yield clamp01((since - R_CUT - 200) / 300f);
            }
            case SHREDDED -> {
                rgb = GOLD;
                yield clamp01((since - S_BURST) / 200f);
            }
            case WAKE -> {
                rgb = MATCHA;
                yield 0.3f + 0.7f * clamp01((since - W_PAINT) / (float) (W_PAINTED - W_PAINT));
            }
            case DEEP_DARK -> {
                rgb = SCULK_LIT;
                yield 0.25f + 0.45f * beat(since);
            }
            default -> 0f;
        };
        return new float[]{Float.intBitsToFloat(rgb), k * fade};
    }

    /** Whether it draws the shovel itself after the reveal. */
    static boolean ownsShovel(SlayerTimeline tl) {
        Ending e = ending(tl);
        return e == Ending.GRAB || e == Ending.WAKE;
    }

    /** The clock the weather and anything loose runs on: the Wake stops it. */
    static long phys(SlayerTimeline tl, long t) {
        if (ending(tl) == Ending.WAKE && t > REVEAL + W_FREEZE && t < REVEAL + W_COLUMN + 200) return REVEAL + W_FREEZE;
        if (ending(tl) == Ending.WAKE && t >= REVEAL + W_COLUMN + 200) return t - (W_COLUMN + 200 - W_FREEZE);
        return t;
    }

    /** The texel-by-texel paint over every face this frame: sculk for the Deep Dark, matcha for the Wake. */
    static ChopScene.Gild gild(SlayerTimeline tl, long t) {
        long since = t - REVEAL;
        if (since < 0) return null;
        Ending e = ending(tl);
        if (e == Ending.DEEP_DARK) {
            float reach = 36f * ease(since / (float) D_SPREAD);
            float lit = beat(since);
            int col = ChopScene.mix(SCULK, SCULK_LIT, 0.55f * lit);
            // Its front ragged in 2-unit blocks, not texel by texel: a gild draws runs of one colour as one quad, and
            // a per-texel edge broke every face into hundreds of them.
            return (x, y, z, cell, now, sx, sy) -> {
                float d = (float) Math.sqrt(x * x + z * z * 1.4f) + y * 0.6f + 3f * ChopScene.rnd(Math.floorDiv((int) x, 2) * 73 + Math.floorDiv((int) z, 2), 3);
                return d < reach ? 0xB0000000 | col : 0;
            };
        }
        if (e == Ending.WAKE) {
            float reach = 46f * ease((since - W_PAINT) / (float) (W_PAINTED - W_PAINT));
            if (reach <= 0f) return null;
            return (x, y, z, cell, now, sx, sy) -> {
                float d = (float) Math.sqrt(x * x + z * z * 1.3f) + y * 0.5f + 4f * ChopScene.rnd(Math.floorDiv((int) x, 2) * 73 + Math.floorDiv((int) z, 2), 5);
                return d < reach ? 0xD0000000 | MATCHA : 0;
            };
        }
        return null;
    }

    /** 0..1: how lit the sculk is this moment - a double beat on each of the heart's beats. */
    static float beat(long since) {
        float b = 0f;
        for (long at : D_BEATS) {
            long d = since - at;
            if (d >= 0 && d < 160) b = Math.max(b, 1f - d / 160f);
            d -= 190;
            if (d >= 0 && d < 140) b = Math.max(b, 0.6f * (1f - d / 140f));
        }
        return b;
    }

    /** Over the plot, before anything stands on it: the sculk's or the matcha's tiles, spreading. */
    static void ground(ChopScene.Canvas c, SlayerTimeline tl, long t) {
        long since = t - REVEAL;
        if (since < 0) return;
        Ending e = ending(tl);
        float reach;
        String tex;
        int tint;
        if (e == Ending.DEEP_DARK) {
            reach = 36f * ease(since / (float) D_SPREAD);
            tex = SlayerScene.SCULK;
            tint = ChopScene.mix(0xB8C8CC, 0xFFFFFF, beat(since));
        } else if (e == Ending.WAKE) {
            reach = 46f * ease((since - W_PAINT) / (float) (W_PAINTED - W_PAINT));
            tex = SlayerScene.PODZOL;
            tint = MATCHA;
        } else {
            return;
        }
        if (reach <= 0f) return;
        float block = SlayerScene.GB;
        int n = (int) Math.ceil(reach / block) + 1;
        for (int zi = -n; zi <= n / 2; zi++) {
            for (int xi = -n; xi <= n; xi++) {
                float x = xi * block, z = zi * block;
                float d = (float) Math.sqrt((x + block / 2f) * (x + block / 2f) + (z + block / 2f) * (z + block / 2f) * 1.4f)
                        + 3f * rnd(tl.seed(), xi * 31 + zi, 9);
                if (d > reach || d > 30f) continue;
                int col = ChopScene.alpha(0.95f * ChopScene.sceneAlpha) | (e == Ending.WAKE ? ChopScene.mix(0x9A8A6A, tint, 0.85f) : tint);
                ChopScene.worldQuad(tex, new int[]{0, 0, 16, 16}, x, 0.02f, z, block, 0f, 0f, 0f, 0f, block, col, c);
            }
        }
    }

    /** Whether grave {@code i} of the yard still has its mound (the Wake bursts them). */
    static boolean moundStands(SlayerTimeline tl, long t, int i) {
        return ending(tl) != Ending.WAKE || t - REVEAL < W_GRAVES + i * W_GRAVE_STEP;
    }

    /** After the yard: the Wake's dead, climbing out of their graves. */
    static void afterYard(ChopScene.Canvas c, SlayerTimeline tl, long t, int w, int h) {
        if (ending(tl) != Ending.WAKE) return;
        long since = t - REVEAL;
        if (since < W_GRAVES) return;
        DEAD.begin();
        float reach = 46f * ease((since - W_PAINT) / (float) (W_PAINTED - W_PAINT));
        for (int i = 0; i < SlayerScene.YARD.length; i++) {
            float[] gs = SlayerScene.YARD[i];
            long burst = W_GRAVES + i * W_GRAVE_STEP;
            long d = since - burst;
            if (d < 0) continue;
            // The mound blown open.
            float mx = gs[0], mz = gs[1] + 3.45f;
            if (d < 900) {
                for (int k = 0; k < 6; k++) {
                    float kk = d / 900f;
                    float dx = (rnd(tl.seed(), i * 13 + k, 1) - 0.5f) * 10f, dz = (rnd(tl.seed(), i * 13 + k, 2) - 0.3f) * 6f;
                    float y = 0.8f + (6f + 4f * rnd(tl.seed(), i * 13 + k, 3)) * kk - 14f * kk * kk;
                    if (y < 0.2f) continue;
                    SlayerScene.inked(mx + dx * kk, y, mz + dz * kk, 3f * kk * k, 2f * kk, 0f, 0.5f, 0.5f, 0.5f, SlayerScene.DIRT,
                            SlayerScene.TILE, 0.7f, 0.64f, 0.56f, clamp01(1.4f - kk), c);
                }
            }
            // The zombie: shoving itself up out of the ground in jerks, facing the coffin; arms out as the dead hold them,
            // raised at the end.
            float u = clamp01(d / (float) W_CLIMB);
            float climb = backOut(u) * 0.85f + 0.15f * u;
            float jerk = u < 1f ? 0.25f * (float) Math.abs(Math.sin(u * 14f)) * (1f - u) : 0f;
            float feet = -8.5f + 8.5f * climb + jerk;
            double yaw = Math.atan2(-mx, -mz) + 0.15f * (float) Math.sin(u * 9f) * (1f - u);
            float arms = since < W_ARMS ? -1.45f + 0.12f * (float) Math.sin(since / 260.0 + i) : -1.45f - 1.35f * easeOut((since - W_ARMS) / 450f);
            boolean painted = reach > (float) Math.sqrt(mx * mx + mz * mz * 1.3f);
            int tint = painted ? ChopScene.mix(0xFFFFFF, MATCHA, 0.75f) : 0xFFFFFF;
            zombie(DEAD, mx, feet, mz, (float) yaw, arms, tint, 0.15f, 0.05f * (float) Math.sin(since / 300.0 + i));
        }
        DEAD.draw(c, TEX_DEAD_IMAGE, w, h, rasterScale, 1f);
    }

    /** Whether the ending draws the main gravestone (the Reaper's, split). */
    static boolean ownsStone(SlayerTimeline tl, long t) {
        return ending(tl) == Ending.REAPER && t - REVEAL >= R_CUT;
    }

    /** The lid, for the endings that own it. */
    static void lid(ChopScene.Canvas c, SlayerTimeline tl, long t, long since) {
        Ending e = ending(tl);
        float y = SlayerScene.LID_Y;
        switch (e) {
            case GRAB -> {
                float jolt = since < G_PUNCH ? 0.5f * (float) Math.sin(Math.PI * since / (float) G_PUNCH) : 0f;
                SlayerScene.inked(0f, y + jolt, 0f, 0f, 0f, 0f, 6.8f, 0.42f, 3.2f, SlayerScene.DARK_OAK, SlayerScene.TILE, 0.78f, 0.7f, 0.6f, 1f, c);
                // The hole it punched, dark, its edge splintered.
                if (since >= G_PUNCH) {
                    float top = y + 0.43f;
                    SlayerScene.decal(c, 0.5f, top, -0.5f, 2.2f, 0f, 0f, 0f, 0f, 2.2f, a(0.95f) | 0x0E0A08);
                    if (since > G_UNDER && since < G_RISE + 600) {
                        float glow = clamp01((since - G_UNDER) / 300f);
                        ChopScene.project(1.6f, top, 0.6f);
                        ChopScene.glow(c, ChopScene.P[0], ChopScene.P[1], ChopScene.size * 12f, GOLD, 0.4f * glow);
                    }
                }
            }
            case SHREDDED -> {
                if (since >= S_BURST) {
                    strips(c, tl, since - S_BURST);
                    return;
                }
                float jolt = 0f;
                for (long at : S_CLAWS) {
                    long d = since - at;
                    if (d >= 0 && d < 160) jolt = Math.max(jolt, 0.35f * (1f - d / 160f));
                }
                SlayerScene.inked(0f, y - jolt, 0f, 0f, 0f, 0f, 6.8f, 0.42f, 3.2f, SlayerScene.DARK_OAK, SlayerScene.TILE, 0.78f, 0.7f, 0.6f, 1f, c);
                claws(c, tl, since, jolt);
            }
            case WAKE -> {
                // Thrown as any win's, and stopped in the air when time does; it lands once time runs again.
                SlayerScene.thrownLid(c, phys(tl, t) - REVEAL);
            }
            default -> { }
        }
    }

    /** The shovel, for the endings that own it. */
    static void shovel(ChopScene.Canvas c, SlayerTimeline tl, long t, long since) {
        Ending e = ending(tl);
        float lid = SlayerScene.LID_Y + 0.4f;
        if (e == Ending.WAKE) {
            // Thrown, then stopped in the air with everything else.
            SlayerScene.thrownShovel(c, phys(tl, t) - REVEAL);
            return;
        }
        // Grab: stuck in the lid until the hand takes it - shaken as it closes on it - then dragged under, jerking.
        if (since < G_GRAB) {
            float shake = since > G_REACH ? 0.06f * (float) Math.sin(since * 0.12) : 0f;
            SlayerScene.shovelAt(c, 4f, lid, 2.6f, -0.42f + shake, 1f);
            return;
        }
        // Once under, it is gone for good (the hand comes back up with the drop, not the shovel).
        if (since >= G_UNDER) return;
        float k = clamp01((since - G_GRAB) / (float) (G_UNDER - G_GRAB));
        float[] hand = hand(since);
        float pull = easeIn(k) + 0.06f * (float) Math.sin(k * 26f) * (1f - k);
        float ty = hand[1] - 1.5f - 9f * pull;
        if (ty < lid - 6f) return;
        SlayerScene.shovelAt(c, hand[0], ty, hand[2], -0.42f - 0.5f * k, clamp01((ty - lid + 6f) / 2f));
    }

    /** Grab: where the hand's top is (x, y, z), and how far out of the lid the arm is (0..1). */
    static float[] hand(long since) {
        float top = SlayerScene.LID_Y + 0.43f;
        float out;
        if (since < G_PUNCH) out = 0f;
        else if (since < G_GRAB) out = backOut(clamp01((since - G_PUNCH) / 180f));
        else if (since < G_UNDER) out = 1f - ease((since - G_GRAB) / (float) (G_UNDER - G_GRAB));
        else if (since < G_RISE) out = 0f;
        else out = backOut(clamp01((since - G_RISE) / 260f)) * (since > G_RISE + 900 ? 1f - ease((since - G_RISE - 900) / 400f) : 1f);
        float reach = since >= G_REACH && since < G_UNDER ? ease(clamp01((since - G_REACH) / (float) (G_GRAB - G_REACH))) : 0f;
        float len = 6.6f * out;
        float lean = -0.45f * reach;
        float x = 1.6f + (float) Math.sin(-lean) * len, y = top + (float) Math.cos(lean) * len;
        return new float[]{x, y, 0.6f, out, lean};
    }

    /** Where the drop rises from to the showcase, projected. */
    static float[] showFrom(SlayerTimeline tl, long t) {
        long since = t - REVEAL;
        Ending e = ending(tl);
        if (e == Ending.GRAB) {
            float[] hd = hand(since);
            ChopScene.project(hd[0], hd[1] + 1.2f, hd[2]);
        } else if (e == Ending.DEEP_DARK) {
            ChopScene.project(0f, SlayerScene.LID_Y + 7.5f, 0f);
        } else {
            ChopScene.project(0f, SlayerScene.LID_Y + 2f, 0f);
        }
        return new float[]{ChopScene.P[0], ChopScene.P[1]};
    }

    // ================================================================== the effects, after the scene

    static void draw(ChopScene.Canvas c, SlayerTimeline tl, SlayerScene.Show show, long t, long since, int w, int h) {
        switch (ending(tl)) {
            case GRAB -> grab(c, tl, t, since, w, h);
            case REAPER -> reaper(c, tl, t, since, w, h);
            case SHREDDED -> shredded(c, tl, t, since, w, h);
            case DEEP_DARK -> deepDark(c, tl, t, since, w, h);
            case WAKE -> wake(c, tl, t, since, w, h);
            default -> { }
        }
    }

    // ---- Grab

    static void grab(ChopScene.Canvas c, SlayerTimeline tl, long t, long since, int w, int h) {
        float top = SlayerScene.LID_Y + 0.43f;
        if (since >= G_PUNCH && since < G_PUNCH + 700) {
            // Splinters out of the hole.
            long d = since - G_PUNCH;
            float k = d / 700f;
            for (int i = 0; i < 9; i++) {
                float dx = (rnd(tl.seed(), i, 21) - 0.5f) * 12f, dz = (rnd(tl.seed(), i, 22) - 0.3f) * 6f;
                float y = top + (5f + 5f * rnd(tl.seed(), i, 23)) * k - 16f * k * k;
                if (y < 0.3f) continue;
                SlayerScene.inked(1.6f + dx * k, y, 0.6f + dz * k, 4f * k * i, 2f * k, 1f * k, 0.9f, 0.22f, 0.22f, SlayerScene.DARK_OAK,
                        SlayerScene.TILE, 0.7f, 0.62f, 0.54f, clamp01(1.3f - k), c);
            }
        }
        float[] hd = hand(since);
        if (hd[3] > 0.02f) {
            // The arm: the zombie's own, from the lid up, as far as it is out; the hand a little wider at the top.
            float len = 6.6f * hd[3];
            float[][] ax = BoxRaster.axes(0f, 0f, hd[4]);
            float cx = 1.6f + (float) Math.sin(-hd[4]) * len / 2f, cy = top + (float) Math.cos(hd[4]) * len / 2f;
            int[][] arm = SlayerScene.uv(40, 16, 4, 12, 4);
            String[] six = SlayerScene.six(SlayerScene.ZOMBIE);
            ChopScene.inkWidth = SlayerScene.ink();
            ChopScene.boxAxes(cx, cy, 0.6f, ax[0], ax[1], ax[2], 0.92f, len / 2f + 0.02f, 0.92f, six, arm, 1f, 1f, 1f, 0f, 1f, c);
            ChopScene.inkWidth = 0f;
            ChopScene.boxAxes(cx, cy, 0.6f, ax[0], ax[1], ax[2], 0.9f, len / 2f, 0.9f, six, arm, 0.78f, 0.86f, 0.72f, 0f, 1f, c);
            // Fingers closing on the shovel.
            if (since >= G_GRAB - 60 && since < G_UNDER) {
                ChopScene.project(hd[0], hd[1], hd[2] + 0.9f);
                SlayerFx.cells(c, SlayerFx.g(ChopScene.P[0]) - 1, SlayerFx.g(ChopScene.P[1]) - 1, 3, 1, a(1f) | 0x3A5A2A);
            }
        }
        if (since >= G_UNDER) {
            // Gold light leaking up out of the hole, motes rising.
            ChopScene.project(1.6f, top, 0.6f);
            float x = ChopScene.P[0], y = ChopScene.P[1];
            float on = clamp01((since - G_UNDER) / 250f) * tl.fadeOut(t);
            SlayerFx.beam(c, x, y, h * 0.22f * on, h * 0.04f, t, GOLD, 0.7f * on);
            for (int i = 0; i < 12; i++) {
                float life = 900f + 400f * rnd(tl.seed(), i, 31);
                float ph = ((since + rnd(tl.seed(), i, 32) * life) % life) / life;
                square(c, x + (rnd(tl.seed(), i, 33) - 0.5f) * h * 0.06f, y - ph * h * 0.2f, 1, a((1f - ph) * on) | (i % 3 == 0 ? 0xFFFFFF : GOLD));
            }
        }
    }

    // ---- Reaper

    static void reaper(ChopScene.Canvas c, SlayerTimeline tl, long t, long since, int w, int h) {
        // Dark mist off the open coffin first.
        ChopScene.project(0f, SlayerScene.LID_Y, 0f);
        float cx = ChopScene.P[0], cy = ChopScene.P[1];
        SlayerFx.burst(c, cx, cy, since, 1100, 22, h * 0.25f, tl.seed() + 41, new int[]{0x3A4050, 0x2A2E38, 0x5A6070}, 0.7f, -h * 0.2f);
        // The scythe: the Blender model, swung like a pendulum from a grip above the stone.
        ChopScene.project(0f, CUT_Y, -8.5f);
        float sx = ChopScene.P[0], sy = ChopScene.P[1];
        if (since >= R_IN && since < R_GONE) {
            float fade = clamp01((R_GONE - since) / 300f);
            float[] grip = grip(since);
            // How fast it is going, 0..1 (1 at the stone): the smear and the arc only show while it is fast.
            float u = clamp01((since - R_DRAWN) / (float) R_SWING);
            float speed = since <= R_DRAWN ? 0f : clamp01(5f * u * (1f - u) - 0.15f);
            if (speed > 0.02f) {
                // The point's arc over the last 80 ms, a pale band of cells - thick and bright at the blade, thin behind.
                float now = phi(since), tail = phi(since - 80);
                float[] p0 = ScytheModel.point(grip, haft(now), lead(now), SCYTHE_PX);
                ChopScene.project(p0[0], p0[1], p0[2]);
                float x0 = ChopScene.P[0], y0 = ChopScene.P[1];
                float[] p1 = ScytheModel.point(grip, haft(tail), lead(tail), SCYTHE_PX);
                ChopScene.project(p1[0], p1[1], p1[2]);
                int n = Math.max(4, Math.min(40, Math.round((float) Math.hypot(ChopScene.P[0] - x0, ChopScene.P[1] - y0) / (U * 0.8f))));
                for (int i = 0; i < n; i++) {
                    float f = i / (float) n, at = now + (tail - now) * f;
                    float[] p = ScytheModel.point(grip, haft(at), lead(at), SCYTHE_PX);
                    ChopScene.project(p[0], p[1], p[2]);
                    square(c, ChopScene.P[0], ChopScene.P[1], f < 0.35f ? 2 : 1, a(fade * speed * 0.8f * (1f - f)) | 0xE8F2FF);
                }
                // The smear: the blade where it was 24 and 48 ms ago, pale and unoutlined, as Diana's cleaver.
                for (int g = 2; g >= 1; g--) {
                    float back = phi(since - 24L * g);
                    ScytheModel.RASTER.begin();
                    ScytheModel.pose(ScytheModel.RASTER, grip, haft(back), lead(back), SCYTHE_PX, true);
                    ScytheModel.RASTER.draw(c, ScytheModel.TEX_IMAGE + "_smear" + g, w, h, rasterScale, fade * speed * (g == 1 ? 0.45f : 0.22f), false);
                }
            }
            float k = phi(since);
            ScytheModel.RASTER.begin();
            ScytheModel.pose(ScytheModel.RASTER, grip, haft(k), lead(k), SCYTHE_PX, false);
            ScytheModel.RASTER.draw(c, ScytheModel.TEX_IMAGE, w, h, rasterScale, fade, true);
        }
        // The cut: sparks along it.
        if (since >= R_CUT && since < R_CUT + 600) {
            SlayerFx.burst(c, sx, sy, since - R_CUT, 600, 24, h * 0.5f, tl.seed() + 43, new int[]{0xFFFFFF, 0xE8F2FF, 0xFFE8A0}, 1f, h * 0.9f);
        }
        // Dust where the top lands.
        long landed = since - R_CUT - TOP_LANDS;
        if (landed >= 0 && landed < 800) {
            ChopScene.project(-1.5f, 0.6f, -8.5f - 1f - (11f - CUT_Y) / 2f);
            SlayerFx.burst(c, ChopScene.P[0], ChopScene.P[1], landed, 800, 22, h * 0.25f, tl.seed() + 45, new int[]{0x8A8478, 0x6A665E, 0xA8A294}, 0.8f, -h * 0.05f);
        }
        // The light out of the coffin, once the blade has passed.
        if (since >= R_CUT + 200) {
            float on = clamp01((since - R_CUT - 200) / 300f) * tl.fadeOut(t);
            SlayerScene.coffinLight(c, tl, t, GOLD, 0.85f * on, h * 0.4f);
        }
    }

    /** The cut's height on the gravestone: above the lettering. */
    static final float CUT_Y = 7.2f;
    /** A model pixel of the scythe in world units. */
    static final float SCYTHE_PX = 0.34f;

    /**
     * The swing's plane: upright and square to the camera at rest (yaw 18), so the swing is an arc on the screen and
     * the blade's face is seen whole all the way round. Its own right, along which the blade crosses the stone.
     */
    static final float[] SWING_R = {(float) Math.cos(Math.toRadians(18)), 0f, (float) Math.sin(Math.toRadians(18))};

    /**
     * The grip, hanging the scythe straight down: the middle of the blade's edge (model -16, 38.5) is then in the cut,
     * the blade along it. It drops in from above at the start and settles with a little overshoot.
     */
    static float[] grip(long since) {
        float drop = 26f * (1f - backOut(clamp01((since - R_IN) / (float) (R_SET - R_IN))));
        return new float[]{16f * SCYTHE_PX * SWING_R[0], CUT_Y + 38.5f * SCYTHE_PX + drop, -8.5f + 16f * SCYTHE_PX * SWING_R[2]};
    }

    static final float PHI_SET = 1.5f, PHI_BACK = 1.7f, PHI_END = -1.7f;

    /**
     * The haft's angle from hanging straight down (positive: swung back to the right) at {@code since}: raised as it
     * drops in; drawn back a little, slowing, with a tremble growing in the grip; then the swing - from rest, fastest at
     * the stone (the bottom), slowing to rest high on the other side - and a little drift on as it goes.
     */
    static float phi(long since) {
        if (since < R_SET) return PHI_SET;
        if (since < R_DRAWN) {
            float k = (since - R_SET) / (float) (R_DRAWN - R_SET);
            return PHI_SET + (PHI_BACK - PHI_SET) * easeOut(k) + 0.03f * k * k * (float) Math.sin(since / 45.0 * 2 * Math.PI);
        }
        float u = clamp01((since - R_DRAWN) / (float) R_SWING);
        float swing = PHI_BACK + (PHI_END - PHI_BACK) * (u * u * (3f - 2f * u));
        long after = since - R_DRAWN - R_SWING;
        return after <= 0 ? swing : swing - 0.12f * easeOut(clamp01(after / 300f));
    }

    /** The haft's direction from the grip at angle {@code phi}, in the swing's plane. */
    static float[] haft(float phi) {
        float s = (float) Math.sin(phi), c = (float) Math.cos(phi);
        return new float[]{s * SWING_R[0], -c, s * SWING_R[2]};
    }

    /**
     * The way the blade points at angle {@code phi}: square to the haft, the way the swing goes - so the point leads and
     * the edge faces the grip, as a real scythe's does on the stroke.
     */
    static float[] lead(float phi) {
        float s = (float) Math.sin(phi), c = (float) Math.cos(phi);
        return new float[]{-c * SWING_R[0], -s, -c * SWING_R[2]};
    }

    /** When the stone's top lands in the grass, after the cut. */
    static final long TOP_LANDS = 900;

    /**
     * The Reaper's gravestone, cut in two: the lower part where it stood; the top shoved a little along the cut by the
     * blade (slowing, as on stone), then tipping off backwards about its back edge - slowly, then faster - and falling
     * flat behind the stone, where it bounces once and lies. It is drawn behind the lower part once it has gone over.
     */
    static void splitStone(ChopScene.Canvas c, SlayerTimeline tl, long t) {
        long d = t - REVEAL - R_CUT;
        float x = 0f, z = -8.5f, height = 11f;
        float body = height * 0.78f, hw = 4.2f, hd = 0.95f, cap = height - body;
        float shove = -1.4f * easeOut(clamp01(d / 260f));
        // Tipping back about the back edge of the cut, a quarter turn; then the drop from the cut to the ground.
        float tip = -(float) Math.PI / 2f * easeIn(clamp01((d - 200) / 520f));
        float fall = d < 720 ? 0f : (d < TOP_LANDS ? easeIn((d - 720) / (float) (TOP_LANDS - 720)) : 1f - 0.04f * bounce(clamp01((d - TOP_LANDS) / 400f) * 0.45f + 0.55f));
        float pivotY = CUT_Y - (CUT_Y - hd) * fall, pivotZ = z - hd;
        float ca = (float) Math.cos(tip), sa = (float) Math.sin(tip);
        boolean over = tip < -0.9f;
        if (!over) lower(c, x, z, body, hw, hd);
        float[][] parts = {{(body + CUT_Y) / 2f, hw, (body - CUT_Y) / 2f}, {body + cap * 0.3f, hw * 0.78f, cap * 0.3f}, {body + cap * 0.8f, hw * 0.5f, cap * 0.2f}};
        for (int pass = 0; pass < 2; pass++) {
            for (float[] p : parts) {
                // A part's place, turned about the pivot (the back edge): up its own Y and forward its own Z.
                float dy = p[0] - CUT_Y, dz = hd;
                float py = pivotY + dy * ca - dz * sa, pz = pivotZ + dy * sa + dz * ca;
                int[][] rects = SlayerScene.scaled(p[1], p[2], hd, (int) (p[0] * 10f));
                if (pass == 0) SlayerScene.inkOnly(x + shove, py, pz, tip, 0f, 0f, p[1], p[2], hd, SlayerScene.MOSSY, rects, 1f, c);
                else SlayerScene.plain(x + shove, py, pz, tip, 0f, 0f, p[1], p[2], hd, SlayerScene.MOSSY, rects, 0.86f, 0.9f, 0.86f, 1f, c);
            }
        }
        if (over) lower(c, x, z, body, hw, hd);
    }

    /** The part of the stone still standing, engraved as ever. */
    static void lower(ChopScene.Canvas c, float x, float z, float body, float hw, float hd) {
        int[][] rects = SlayerScene.scaled(hw, CUT_Y / 2f, hd, 1);
        SlayerScene.inkOnly(x, CUT_Y / 2f, z, 0f, 0f, 0f, hw, CUT_Y / 2f, hd, SlayerScene.MOSSY, rects, 1f, c);
        SlayerScene.plain(x, CUT_Y / 2f, z, 0f, 0f, 0f, hw, CUT_Y / 2f, hd, SlayerScene.MOSSY, rects, 0.86f, 0.9f, 0.86f, 1f, c);
        SlayerScene.lettering(c, x, z, body, hd);
    }

    // ---- Shredded

    static void shredded(ChopScene.Canvas c, SlayerTimeline tl, long t, long since, int w, int h) {
        ChopScene.project(0f, SlayerScene.LID_Y, 0f);
        float x = ChopScene.P[0], y = ChopScene.P[1];
        for (long at : S_CLAWS) {
            long d = since - at;
            if (d >= 0 && d < 500) {
                SlayerFx.burst(c, x, y, d, 500, 12, h * 0.4f, tl.seed() + at, new int[]{0x4A3424, 0x2E2016, 0x6A4A30}, 0.9f, h * 1.2f);
            }
        }
        if (since >= S_BURST) {
            float on = clamp01((since - S_BURST) / 200f) * tl.fadeOut(t);
            SlayerScene.coffinLight(c, tl, t, GOLD, 0.85f * on, h * 0.45f);
            SlayerFx.burst(c, x, y, since - S_BURST, 900, 30, h * 0.7f, tl.seed() + 47, new int[]{GOLD, 0xFFFFFF, 0x6A4A30}, 1f, h * 0.9f);
        }
    }

    /**
     * Where each swipe rakes the lid's top: {x, z} of its middle claw's start and end. The lid's top is seen nearly
     * edge on, so each gouge runs mostly front to back and the claws sit side by side along the lid - spaced along its
     * depth, the three of a swipe came out on top of each other, one pale scribble (the user).
     */
    static final float[][] SWIPES = {{-3.0f, -2.5f, -5.2f, 2.4f}, {0.6f, -2.5f, -0.6f, 2.4f}, {5.0f, -2.4f, 3.2f, 2.3f}};
    static final float CLAW_GAP = 1.4f;

    /**
     * Three claw swipes into the lid's top, each three gouges torn into the wood in a blink - pixel cells on the lid, as
     * every effect here is (thin smooth lines the length of the lid read as stripes - the user). Each gouge curves a
     * little and tapers at both ends, the middle claw's the longest: a dark groove, two cells wide where it is deepest, the
     * torn wood pale along one lip, and the claw's tip glinting as it goes.
     */
    static void claws(ChopScene.Canvas c, SlayerTimeline tl, long since, float jolt) {
        float top = SlayerScene.LID_Y + 0.43f - jolt;
        for (int s = 0; s < S_CLAWS.length; s++) {
            long d = since - S_CLAWS[s];
            if (d < 0) continue;
            float k = easeOut(clamp01(d / 90f));
            float[] sw = SWIPES[s];
            float dx = sw[2] - sw[0], dz = sw[3] - sw[1];
            for (int g = 0; g < 3; g++) {
                float from = g == 1 ? 0f : 0.12f, to = g == 1 ? 1f : 0.82f;
                // Each gouge's cells, row by row on the screen: the run of the groove in each row.
                java.util.TreeMap<Integer, int[]> rows = new java.util.TreeMap<>();
                int tipX = 0, tipY = 0;
                int n = 32;
                for (int i = 0; i <= n; i++) {
                    float f = from + (to - from) * i / (float) n;
                    if (f > k * to + 1e-4f) break;
                    float bow = 0.4f * (float) Math.sin(Math.PI * f) * (s == 1 ? -1f : 1f);
                    float wx = sw[0] + dx * f + (g - 1) * CLAW_GAP + bow, wz = sw[1] + dz * f;
                    ChopScene.project(wx, top, wz);
                    int gx = SlayerFx.g(ChopScene.P[0]), gy = SlayerFx.g(ChopScene.P[1]);
                    float width = (float) Math.sin(Math.PI * (f - from) / (to - from));
                    int right = gx + (width > 0.5f ? 1 : 0);
                    int[] run = rows.get(gy);
                    if (run == null) rows.put(gy, new int[]{gx, right});
                    else {
                        run[0] = Math.min(run[0], gx);
                        run[1] = Math.max(run[1], right);
                    }
                    tipX = gx;
                    tipY = gy;
                }
                if (rows.isEmpty()) continue;
                for (java.util.Map.Entry<Integer, int[]> e : rows.entrySet()) {
                    int gy = e.getKey(), x0 = e.getValue()[0], x1 = e.getValue()[1];
                    // The torn lip on its left, the wood's own colour paler - most rows, not all, so it is ragged.
                    if (rnd(tl.seed(), gy * 13 + g, s * 7 + 1) < 0.75f) {
                        boolean fleck = rnd(tl.seed(), gy * 13 + g, s * 7 + 2) < 0.25f;
                        SlayerFx.cells(c, x0 - 1, gy, 1, 1, a(0.85f) | (fleck ? 0xCCA678 : 0x8E6A46));
                    }
                    // The groove: darkest where it is widest.
                    SlayerFx.cells(c, x0, gy, x1 - x0 + 1, 1, a(0.95f) | (x1 > x0 ? 0x0C0503 : 0x1E0F08));
                }
                // The claw's tip, glinting as it tears.
                if (d < 200) SlayerFx.cells(c, tipX, tipY, 1, 1, a(1f - d / 200f) | 0xFFF2E8);
            }
        }
    }

    private static long key(int gx, int gy) {
        return ((long) gx << 32) | (gy & 0xFFFFFFFFL);
    }

    /** The lid in strips, blown out and spinning. */
    static void strips(ChopScene.Canvas c, SlayerTimeline tl, long d) {
        float k = d / 900f;
        if (k > 1.3f) return;
        for (int i = 0; i < 7; i++) {
            float off = (i - 3) * 0.92f;
            float dx = (rnd(tl.seed(), i, 51) - 0.5f) * 10f, up = 8f + 8f * rnd(tl.seed(), i, 52);
            float x = off * 0.3f + dx * k, y = SlayerScene.LID_Y + up * k - 18f * k * k, z = off + (rnd(tl.seed(), i, 53) - 0.5f) * 6f * k;
            SlayerScene.inked(x, y, z, (1.5f + i) * k, 0.4f * k * i, (2f - i * 0.5f) * k, 6.8f, 0.2f, 0.44f, SlayerScene.DARK_OAK, SlayerScene.TILE,
                    0.78f, 0.7f, 0.6f, clamp01(1.3f - k), c);
        }
    }

    // ---- The Deep Dark

    static void deepDark(ChopScene.Canvas c, SlayerTimeline tl, long t, long since, int w, int h) {
        long seed = tl.seed();
        ChopScene.project(0f, SlayerScene.LID_Y, 0f);
        float cx = ChopScene.P[0], cy = ChopScene.P[1];
        // The burst: dark, not golden - black and the sculk's cyan.
        SlayerFx.burst(c, cx, cy, since, 1000, 34, h * 0.6f, seed + 61, new int[]{0x05090C, SCULK_LIT, 0x0B3A44}, 1f, h * 0.7f);
        // A ring along the ground on every beat.
        for (long at : D_BEATS) {
            long d = since - at;
            if (d < 0 || d > 700) continue;
            float k = d / 700f;
            float r = 6f + 26f * ease(k);
            int n = 64;
            for (int i = 0; i < n; i++) {
                double ang = i * Math.PI * 2 / n;
                ChopScene.project((float) Math.cos(ang) * r, 0.1f, (float) Math.sin(ang) * r * 0.7f);
                square(c, ChopScene.P[0], ChopScene.P[1], 1, a((1f - k) * 0.8f) | SCULK_LIT);
            }
        }
        // The Warden: hauls itself up out of the coffin in two heaves, breathes, roars, and sinks back - slowly at first.
        float rise = wardenRise(since);
        float roar = since >= D_ROAR && since < D_SINK ? (float) Math.sin(Math.PI * clamp01((since - D_ROAR) / (float) (D_SINK - D_ROAR))) : 0f;
        if (rise > 0.01f) {
            WARDEN.begin();
            float breath = since > D_RISEN && since < D_SINK ? 0.18f * (float) Math.sin((since - D_RISEN) / 210.0) : 0f;
            float feet = -14f + 14.4f * rise + breath;
            warden(WARDEN, 0f, feet, 0f, roar, since, 3.2f);
            WARDEN.draw(c, TEX_WARDEN_IMAGE, w, h, rasterScale, 1f);
        }
        // Its heart, glowing in its chest on every beat, left beating in the air when it sinks.
        float heartY = since < D_SINK ? -14f + 14.4f * rise + 10.5f : SlayerScene.LID_Y + 7.5f + 3f * (1f - easeOut((since - D_SINK) / 500f));
        float lit = beat(since);
        ChopScene.project(0f, heartY, 1.6f);
        if (rise > 0.2f || since >= D_SINK) {
            ChopScene.glow(c, ChopScene.P[0], ChopScene.P[1], h * (0.08f + 0.06f * lit), SCULK_LIT, 0.35f + 0.5f * lit);
        }
        // The sonic boom: rings flung at the camera from its mouth.
        if (since >= D_ROAR + 150) {
            ChopScene.project(0f, -14f + 14.4f * rise + 13.5f, 2.5f);
            float mx = ChopScene.P[0], my = ChopScene.P[1];
            for (int i = 0; i < 4; i++) {
                long d = since - D_ROAR - 150 - i * 110L;
                if (d < 0 || d > 600) continue;
                float k = d / 600f;
                float dd = h * (0.08f + 1.1f * ease(k));
                SlayerFx.ring(c, mx, my + h * 0.1f * k, dd, dd * 0.7f, a((1f - k) * 0.9f) | (i % 2 == 0 ? SCULK_LIT : 0xD8F8FF));
                SlayerFx.ring(c, mx, my + h * 0.1f * k, dd * 0.92f, dd * 0.64f, a((1f - k) * 0.5f) | 0x0B3A44);
            }
        }
    }

    /** 0..1, how far up out of the coffin the Warden is: two heaves - most of the way, a pause, the rest - and the sink. */
    static float wardenRise(long since) {
        if (since < D_RISE) return 0f;
        float one = easeOut(clamp01((since - D_RISE) / 420f)), two = easeOut(clamp01((since - D_RISE - 600) / (float) (D_RISEN - D_RISE - 600)));
        float up = 0.62f * one + 0.38f * two;
        if (since >= D_SINK) up *= 1f - easeIn(clamp01((since - D_SINK) / (float) (D_SUNK - D_SINK)));
        return up;
    }

    // ---- The Wake

    static void wake(ChopScene.Canvas c, SlayerTimeline tl, long t, long since, int w, int h) {
        ChopScene.project(0f, SlayerScene.LID_Y, 0f);
        float cx = ChopScene.P[0], cy = ChopScene.P[1];
        if (since < W_FREEZE + 200) {
            SlayerFx.burst(c, cx, cy, since, W_FREEZE + 200, 24, h * 0.5f, tl.seed() + 71, new int[]{MATCHA, 0xFFFFFF, 0xC8E89A}, 1f, h * 0.6f);
        }
        // The light in the coffin, matcha, growing as the paint spreads.
        float paint = clamp01((since - W_PAINT) / (float) (W_PAINTED - W_PAINT));
        ChopScene.glow(c, cx, cy, h * (0.2f + 0.3f * paint), MATCHA, 0.25f + 0.35f * paint);
        // The column, and the dye rising in it.
        if (since >= W_COLUMN) {
            float on = ease((since - W_COLUMN) / 400f) * tl.fadeOut(t);
            SlayerScene.coffinLight(c, tl, t, MATCHA, 0.85f * on, h * 0.62f);
            for (int i = 0; i < 14; i++) {
                float life = 1200f + 500f * rnd(tl.seed(), i, 81);
                float ph = ((since + rnd(tl.seed(), i, 82) * life) % life) / life;
                square(c, cx + (rnd(tl.seed(), i, 83) - 0.5f) * h * 0.12f, cy - ph * h * 0.6f, ph < 0.4f ? 2 : 1,
                        a((1f - ph) * on) | (i % 3 == 0 ? 0xFFFFFF : MATCHA));
            }
        }
    }

    // ================================================================== the models

    /**
     * The Warden as the game builds it (WardenModel.createBodyLayer, 26.2, read with javap; 128 x 128). Model space is
     * y down with the feet at y 24, facing -Z; turned a half about X (x kept, y and z flipped) it faces the camera, so a
     * model point is at world height {@code feet + (24 - y) * s}. The body's box is y -10..11 (its part at 3, the box
     * -13..8), x -9..9, z -4..7; the head's -26..-10 on top of it; the arms 8 x 28 x 8 hang from (+-13, -10, 1); the legs
     * 6 x 13 x 6 stand at (+-5.9, 11). One model pixel is {@link #W_PX}: it stands about 14 high, over the gravestone.
     */
    static void warden(BoxRaster r, float x, float feet, float z, float roar, long since, float cut) {
        float s = W_PX;
        float lean = -0.16f * roar;
        float[][] body = BoxRaster.axes(lean, 0f, 0f);
        // The waist (the body's bottom, model y 11) is the pivot it leans back about.
        float waist = feet + 13f * s;
        float[] bc = along(body[1], 10.5f * s);
        r.box(x + bc[0], waist + bc[1], z - 1.5f * s + bc[2], body[0], body[1], body[2], 9f * s, 10.5f * s, 5.5f * s,
                SlayerScene.uv(0, 0, 18, 21, 11), 0, 0xFFFFFF, 0, cut);
        // The ribcage: two flat planes on the chest (texOffs 90 11, 9 x 21 x 0, a tenth in front of the body's face).
        int[][] ribs = {null, null, null, null, {90, 11, 9, 21}, null};
        for (int side = -1; side <= 1; side += 2) {
            r.box(x + side * 4.5f * s + bc[0], waist + bc[1], z + 4.15f * s + bc[2], body[0], body[1], body[2], 4.5f * s, 10.5f * s, 0.02f,
                    ribs, 0, 0xFFFFFF, 0, cut);
        }
        // The neck: the body's top, 21 px up its own axis from the waist.
        float[] nk = along(body[1], 21f * s);
        float nx = x + nk[0], ny = waist + nk[1], nz = z + nk[2];
        float[][] head = BoxRaster.axes(lean - 0.5f * roar, 0f, 0f);
        float[] hc = along(head[1], 8f * s);
        r.box(nx + hc[0], ny + hc[1], nz + hc[2], head[0], head[1], head[2], 8f * s, 8f * s, 5f * s, SlayerScene.uv(0, 32, 16, 16, 10), 0,
                0xFFFFFF, 0, cut);
        // Its horns: the two tendrils, flat 16 x 16 planes on the head's sides (right_tendril at texOffs 52 32, left at
        // 58 0; each 13 above to 3 below a pivot 12 up the head and 8 out) - they twitch as it roars.
        for (int side = -1; side <= 1; side += 2) {
            float[][] tx = BoxRaster.axes(lean - 0.5f * roar, 0f, side * (0.12f * roar + 0.04f * (float) Math.sin(since / 140.0)));
            float[] tp = along(head[1], 17f * s);
            float[] out = along(tx[0], side * 8f * s);
            float[] pc = {nx + tp[0] + head[0][0] * side * 8f * s, ny + tp[1] + head[0][1] * side * 8f * s, nz + tp[2] + head[0][2] * side * 8f * s};
            int[][] rect = side < 0 ? new int[][]{null, null, null, null, {52, 32, 16, 16}, {68, 32, 16, 16}}
                    : new int[][]{null, null, null, null, {58, 0, 16, 16}, {74, 0, 16, 16}};
            r.box(pc[0] + out[0], pc[1] + out[1], pc[2] + out[2], tx[0], tx[1], tx[2], 8f * s, 8f * s, 0.02f, rect, 0, 0xFFFFFF, 0, cut);
        }
        // The arms from the shoulders (the body's top corners), hanging; flung out to the sides as it roars.
        for (int side = -1; side <= 1; side += 2) {
            float spread = side * (0.08f + 0.85f * roar);
            float[][] ax = BoxRaster.axes(-0.12f - 0.35f * roar, 0f, spread);
            float[] sh = along(body[1], 21f * s);
            float shx = x + side * 13f * s + sh[0], shy = waist + sh[1], shz = z - 1f * s + sh[2];
            float[] ac = along(ax[1], -14f * s);
            r.box(shx + ac[0], shy + ac[1], shz + ac[2], ax[0], ax[1], ax[2], 4f * s, 14f * s, 4f * s,
                    side < 0 ? SlayerScene.uv(44, 50, 8, 28, 8) : SlayerScene.uv(0, 58, 8, 28, 8), 0, 0xFFFFFF, 0, cut);
        }
        float[][] up = BoxRaster.axes(0f, 0f, 0f);
        for (int side = -1; side <= 1; side += 2) {
            r.box(x + side * 5.9f * s, feet + 6.5f * s, z, up[0], up[1], up[2], 3f * s, 6.5f * s, 3f * s,
                    side < 0 ? SlayerScene.uv(76, 48, 6, 13, 6) : SlayerScene.uv(76, 76, 6, 13, 6), 0, 0xFFFFFF, 0, cut);
        }
    }

    /** {@code axis} times {@code k}. */
    static float[] along(float[] axis, float k) {
        return new float[]{axis[0] * k, axis[1] * k, axis[2] * k};
    }

    static final float W_PX = 0.28f;

    /**
     * A zombie as HumanoidModel builds it (64 x 64; the left limbs share the right ones' texels): legs 12 high, the
     * body 12 over them, the head an 8 cube on top, the arms 4 x 12 x 4 hanging from shoulders at (+-5, 22) - their box
     * starts 2 above the pivot. {@code arms} turns them about their X (-pi/2 straight out ahead, as the dead hold them);
     * {@code yaw} faces it its way.
     */
    static void zombie(BoxRaster r, float x, float feet, float z, float yaw, float arms, int tint, float cut, float sway) {
        float s = 0.27f;
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        float[][] base = BoxRaster.axes(0f, yaw, sway);
        float[] o = at(x, feet, z, cy, sy, 0f, 28f * s, 0f);
        r.box(o[0], o[1], o[2], base[0], base[1], base[2], 4f * s, 4f * s, 4f * s, SlayerScene.uv(0, 0, 8, 8, 8), 1, tint, 0, cut);
        o = at(x, feet, z, cy, sy, 0f, 18f * s, 0f);
        r.box(o[0], o[1], o[2], base[0], base[1], base[2], 4f * s, 6f * s, 2f * s, SlayerScene.uv(16, 16, 8, 12, 4), 1, tint, 0, cut);
        for (int side = -1; side <= 1; side += 2) {
            float[][] ax = BoxRaster.axes(arms, yaw, 0f);
            float[] sh = at(x, feet, z, cy, sy, side * 6f * s, 22f * s, 0f);
            float[] ac = along(ax[1], -4f * s);
            r.box(sh[0] + ac[0], sh[1] + ac[1], sh[2] + ac[2], ax[0], ax[1], ax[2], 2f * s, 6f * s, 2f * s,
                    SlayerScene.uv(40, 16, 4, 12, 4), 1, tint, 0, cut);
            o = at(x, feet, z, cy, sy, side * 2f * s, 6f * s, 0f);
            r.box(o[0], o[1], o[2], base[0], base[1], base[2], 2f * s, 6f * s, 2f * s, SlayerScene.uv(0, 16, 4, 12, 4), 1, tint, 0, cut);
        }
    }

    /** A point in a model's own frame (right, up, forward), the model at (x, feet, z) turned by (cos, sin) of its yaw. */
    static float[] at(float x, float feet, float z, float cy, float sy, float px, float py, float pz) {
        return new float[]{x + px * cy + pz * sy, feet + py, z - px * sy + pz * cy};
    }
}
