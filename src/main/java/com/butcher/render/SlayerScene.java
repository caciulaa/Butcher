package com.butcher.render;

import com.butcher.anim.SlayerTimeline;
import com.butcher.anim.SlayerTimeline.Ending;

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
 * Draws a frame of The Grave (the Revenant Horror) or The Cocoon (the Tarantula Broodfather) onto a Canvas - the same
 * code for the game ({@code gui/SlayerScreen}) and the offline preview. Built on a viewer's fork's scenes (2026-10-26)
 * and rebuilt from the user's screenshots of it:
 * <ul>
 *   <li>the grave's dirt is one mound, its outline round the outside only - inked box by box it read as bricks;</li>
 *   <li>the fence stands behind the stone, not across it; "R.I.P." is cut into the stone, not floating on a plate;</li>
 *   <li>the shovel and the sword are their items with depth, and the blade bites the dirt;</li>
 *   <li>the cocoon is one tapered body wrapped in silk, not eight stacked boxes; the webs, thread and cuts are cells;</li>
 *   <li>the spider is posed as the game poses it;</li>
 *   <li>no lightning, no full-screen flashes; the floor is a plot round the grave, never grazing tiles at the horizon.</li>
 * </ul>
 * Everything before {@link SlayerTimeline#REVEAL} is the same for every outcome (tools/SlayerTest).
 */
public final class SlayerScene {
    private SlayerScene() {}

    // ------------------------------------------------------------------ textures: the game's own, adopted at runtime
    public static final String DIRT = "slayer_dirt", COARSE = "slayer_coarse_dirt", PODZOL = "slayer_podzol", MOSSY = "slayer_mossy_bricks",
            SPRUCE = "slayer_spruce", DARK_OAK = "slayer_dark_oak", SHOVEL = "slayer_iron_shovel", WOOL = "slayer_white_wool",
            SPIDER = "slayer_spider", SPIDER_EYES = "slayer_spider_eyes", SWORD = "slayer_iron_sword", MOON = "slayer_moon",
            ZOMBIE = "slayer_zombie", COBBLE = "slayer_mossy_cobble", SCULK = "slayer_sculk";
    public static final String[] ADOPT = {DIRT, COARSE, PODZOL, MOSSY, SPRUCE, DARK_OAK, SHOVEL, WOOL, SPIDER, SPIDER_EYES, SWORD, MOON,
            ZOMBIE, COBBLE, SCULK};
    public static final String[] ADOPT_PATH = {"textures/block/dirt.png", "textures/block/coarse_dirt.png", "textures/block/podzol_top.png",
            "textures/block/mossy_stone_bricks.png", "textures/block/spruce_planks.png", "textures/block/dark_oak_planks.png",
            "textures/item/iron_shovel.png", "textures/block/white_wool.png", "textures/entity/spider/spider.png",
            "textures/entity/spider/spider_eyes.png", "textures/item/iron_sword.png", "textures/environment/celestial/moon/full_moon.png",
            "textures/entity/zombie/zombie.png", "textures/block/mossy_cobblestone.png", "textures/block/sculk.png"};
    public static final int[][] ADOPT_SIZE = {{16, 16}, {16, 16}, {16, 16}, {16, 16}, {16, 16}, {16, 16}, {16, 16}, {16, 16}, {64, 32},
            {64, 32}, {16, 16}, {32, 32}, {64, 64}, {16, 16}, {16, 16}};
    /** A flat white texture (the Seraph's), tinted: engraved letters, silk. */
    static final String WHITE = SeraphScene.TEX_WHITE;

    /** What the showcase says - filled by the screen (and the preview). */
    public static final class Show {
        /** "1 in 7,143 · your 3rd". */
        public String line;
        /** The chat's word for it ("CRAZY RARE DROP!"), null for none. */
        public String banner;
        public int bannerColour = 0xFF55FF;
    }

    /**
     * The rasterised models' textures, read through the game's resources (a pack's are used): the Warden (128 x 128)
     * and the zombie (64 x 64). Set once by the screen, or by the preview from files.
     */
    public static void modelTextures(int[] warden, int ww, int wh, int[] zombie, int zw, int zh) {
        if (warden != null) GraveEndings.WARDEN.texture(0, warden, ww, wh);
        if (zombie != null) GraveEndings.DEAD.texture(1, zombie, zw, zh);
    }

    public static boolean hasModelTextures() {
        return GraveEndings.WARDEN.has(0) && GraveEndings.DEAD.has(1);
    }

    /** Image pixels a GUI pixel for the rasterised models. */
    public static void rasterScale(int scale) {
        GraveEndings.rasterScale = Math.max(1, Math.min(2, scale));
    }

    /** The player's shake slider, 0..1.5 (the menu: "Screen shake"). Set by the screen each frame. */
    public static float shake = 1f;

    static final int[][] TILE = {{0, 0, 16, 16}, {0, 0, 16, 16}, {0, 0, 16, 16}, {0, 0, 16, 16}, {0, 0, 16, 16}, {0, 0, 16, 16}};

    /** The stones' texel density: the main stone's 16 texels across its 8.4 width. */
    static final float STONE_TEXELS = 16f / 8.4f;

    /**
     * A box's face rects at the stones' one texel density, each cut from the tile where {@code seed} puts it - not the
     * whole tile squeezed onto every face, which drew the thin steps of a stone's top as stretched bricks (the user).
     */
    static int[][] scaled(float hx, float hy, float hz, int seed) {
        int sx = texels(hx), sy = texels(hy), sz = texels(hz);
        return new int[][]{cut(sz, sy, seed, 0), cut(sz, sy, seed, 1), cut(sx, sz, seed, 2), cut(sx, sz, seed, 3),
                cut(sx, sy, seed, 4), cut(sx, sy, seed, 5)};
    }

    private static int texels(float half) {
        return Math.max(1, Math.min(16, Math.round(2f * half * STONE_TEXELS)));
    }

    private static int[] cut(int w, int h, int seed, int face) {
        return new int[]{Math.floorMod(seed * 5 + face * 3, 17 - w), Math.floorMod(seed * 7 + face * 5, 17 - h), w, h};
    }

    static String[] six(String tex) {
        return new String[]{tex, tex, tex, tex, tex, tex};
    }

    public static void draw(ChopScene.Canvas c, SlayerTimeline tl, Show show, long t, int w, int h) {
        float master = tl.master(t);
        // As dark as every other cutscene's (0.8; it was 0.5 and the game showed through more - the user).
        c.fill(0, 0, w, h, ChopScene.alpha(0.8f * master * ChopScene.dimScale) | 0x07060A);
        if (master <= 0.01f) return;
        ChopScene.sceneAlpha = master;
        U = SlayerFx.gridFor(h);
        ChopScene.lightCount = 0;
        ChopScene.gild = null;
        ChopScene.glintHook = null;
        ChopScene.insideOut = false;
        ChopScene.yShift = 0f;
        ChopScene.inkWidth = 0f;
        // A box's outline four stamps a face, not eight (most of the draw calls before; ChopScene.inkFour).
        ChopScene.inkFour = true;
        try {
            if (tl.boss() == SlayerTimeline.Boss.REVENANT) grave(c, tl, show, t, w, h);
            else cocoon(c, tl, show, t, w, h);
        } finally {
            ChopScene.inkFour = false;
            ChopScene.inkWidth = 0f;
            ChopScene.lightCount = 0;
            ChopScene.sceneAlpha = 1f;
        }
    }

    // ================================================================== camera, boxes, items

    static void camera(float yawDeg, float pitchDeg, float zoom, float fx, float fy, float fz, SlayerTimeline tl, long t, int w, int h) {
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        ChopScene.cy = (float) Math.cos(yaw);
        ChopScene.sy = (float) Math.sin(yaw);
        ChopScene.cp = (float) Math.cos(pitch);
        ChopScene.sp = (float) Math.sin(pitch);
        ChopScene.size = h * 0.62f / 30f * zoom;
        ChopScene.fx = fx;
        ChopScene.fy = fy;
        ChopScene.fz = fz;
        float[] sh = ChopScene.calm ? new float[]{0f, 0f} : tl.shake(t, h, shake);
        ChopScene.sx0 = w / 2f + sh[0];
        ChopScene.sy0 = h * 0.5f + sh[1];
    }

    static float ink() {
        return Math.max(1.2f, ChopScene.size / 3.2f);
    }

    /** A box with its outline (Butcher's look). */
    static void inked(float x, float y, float z, float ax, float ay, float az, float hx, float hy, float hz, String tex, int[][] rects,
                      float r, float g, float b, float alpha, ChopScene.Canvas c) {
        if (alpha <= 0.02f) return;
        inkOnly(x, y, z, ax, ay, az, hx, hy, hz, tex, rects, alpha, c);
        plain(x, y, z, ax, ay, az, hx, hy, hz, tex, rects, r, g, b, alpha, c);
    }

    /** Only a box's outline: draw every part's first, then every part, and the seams between them get none. */
    static void inkOnly(float x, float y, float z, float ax, float ay, float az, float hx, float hy, float hz, String tex, int[][] rects,
                        float alpha, ChopScene.Canvas c) {
        if (alpha <= 0.02f) return;
        ChopScene.inkWidth = ink();
        ChopScene.box(x, y, z, ax, ay, az, hx * 1.02f, hy * 1.02f, hz * 1.02f, six(tex), rects, 1f, 1f, 1f, 0f, alpha, c);
        ChopScene.inkWidth = 0f;
    }

    static void plain(float x, float y, float z, float ax, float ay, float az, float hx, float hy, float hz, String tex, int[][] rects,
                      float r, float g, float b, float alpha, ChopScene.Canvas c) {
        if (alpha <= 0.02f) return;
        ChopScene.box(x, y, z, ax, ay, az, hx, hy, hz, six(tex), rects, r, g, b, 0f, alpha, c);
    }

    /**
     * A flat item texture standing in the world, as the game draws an item: four layers a texel apart (the back ones
     * darker, so its edge reads as thickness), outlined. Its plane is turned {@code yaw} about Y; within it the item is
     * turned {@code roll} (radians, clockwise on screen at yaw 0). (x, y, z) is the texture's centre.
     */
    static void item(ChopScene.Canvas c, String tex, float x, float y, float z, float half, float yaw, float roll, float alpha) {
        if (alpha <= 0.02f) return;
        float cs = (float) Math.cos(yaw), sn = (float) Math.sin(yaw);
        // The plane's right is (cs, 0, sn), its up is +Y, its normal (-sn, 0, cs). Turned clockwise by the roll, the
        // texture's u runs right and down the plane, its v down and left.
        float nx = -sn, nz = cs;
        float cr = (float) Math.cos(roll), sr = (float) Math.sin(roll);
        float d = 2f * half;
        float ux = cs * cr * d, uy = -sr * d, uz = sn * cr * d;
        float vx = -cs * sr * d, vy = -cr * d, vz = -sn * sr * d;
        float ox = x - ux / 2f - vx / 2f, oy = y - uy / 2f - vy / 2f, oz = z - uz / 2f - vz / 2f;
        boolean front = ChopScene.depth(x + nx, y, z + nz) > ChopScene.depth(x, y, z);
        float side = front ? 1f : -1f;
        float texel = 2f * half / 16f;
        int[] rect = {0, 0, 16, 16};
        int layers = 3;
        float offInk = (layers - 1) / 2f * texel * 0.5f * side;
        // The outline fades with the item (the ink path draws black at the face's alpha, not the tint's).
        ChopScene.inkWidth = Math.max(1f, ChopScene.size / 4f);
        float face = ChopScene.faceAlpha;
        ChopScene.faceAlpha = face * alpha;
        ChopScene.worldQuad(tex, rect, ox + nx * offInk, oy, oz + nz * offInk, ux, uy, uz, vx, vy, vz, ChopScene.alpha(alpha * ChopScene.sceneAlpha), c);
        ChopScene.faceAlpha = face;
        ChopScene.inkWidth = 0f;
        for (int l = 0; l < layers; l++) {
            float off = (-(layers - 1) / 2f + l) * texel * 0.5f * side;
            float k = l == layers - 1 ? 1f : 0.55f + 0.12f * l;
            int col = ChopScene.alpha(alpha * ChopScene.sceneAlpha) | ChopScene.channel(k) << 16 | ChopScene.channel(k) << 8 | ChopScene.channel(k);
            ChopScene.worldQuad(tex, rect, ox + nx * off, oy, oz + nz * off, ux, uy, uz, vx, vy, vz, col, c);
        }
    }

    /** A flat cell of colour lying on a face: an engraved letter's texel, a strand of silk. */
    static void decal(ChopScene.Canvas c, float x, float y, float z, float ux, float uy, float uz, float vx, float vy, float vz, int argb) {
        ChopScene.worldQuad(WHITE, new int[]{0, 0, 4, 4}, x, y, z, ux, uy, uz, vx, vy, vz, argb, c);
    }

    /**
     * A plot of {@code top} tiles round the middle out to {@code radius}, fading to nothing in steps by distance - not
     * by the horizon: tiles far back seen almost edge-on read as stray bars across the sky (the user's screenshot).
     */
    static void plot(ChopScene.Canvas c, String top, float block, float radius, float tint, long seed) {
        int reach = (int) Math.ceil(radius / block) + 1;
        for (int zi = -reach; zi <= reach / 2; zi++) {
            for (int xi = -reach; xi <= reach; xi++) {
                float x = xi * block, z = zi * block;
                float d = (float) Math.sqrt((x + block / 2f) * (x + block / 2f) + (z + block / 2f) * (z + block / 2f) * 1.6f);
                // A ragged edge: each tile's own share of the last ring.
                float edge = radius * (0.82f + 0.18f * rnd(seed, xi * 31 + zi, 5));
                float fade = clamp01((edge - d) / (radius * 0.28f));
                if (fade <= 0.02f) continue;
                fade = Math.round(fade * 4f) / 4f;
                if (fade <= 0.02f) continue;
                float k = tint * (0.86f + 0.14f * ChopScene.rnd(xi, zi)) * (0.6f + 0.4f * fade);
                int col = ChopScene.alpha(fade) | ChopScene.channel(k) << 16 | ChopScene.channel(k) << 8 | ChopScene.channel(k);
                ChopScene.worldQuad(top, new int[]{0, 0, 16, 16}, x, 0f, z, block, 0f, 0f, 0f, 0f, block, col, c);
            }
        }
    }

    // ================================================================== The Grave

    static final float GB = 4f;
    static final float COFFIN_Y = 1.6f, LID_Y = 3.55f;
    /** The rest of the graveyard, close in: {x, z, height, tilt, cross (1) or stone (0)}. */
    static final float[][] YARD = {
            {15f, -29f, 4.2f, 0.09f, 0f}, {-17f, -27f, 4.5f, -0.06f, 0f}, {35f, -25f, 5.5f, 0.11f, 0f}, {-36f, -24f, 6f, -0.1f, 1f},
            {25f, -15f, 4.6f, -0.07f, 1f}, {-26f, -14f, 5f, 0.08f, 0f}};
    /** "R.I.P." in 3 x 5 pixel letters, a column apart, each with its dot (1 = cut into the stone). */
    static final String[] RIP = {
            "11000011100011000",
            "10100001000010100",
            "11000001000011000",
            "10100001000010000",
            "10101011101010001"};

    static void grave(ChopScene.Canvas c, SlayerTimeline tl, Show show, long t, int w, int h) {
        long seed = tl.seed();
        long R = SlayerTimeline.REVEAL;
        long since = t - R;
        float push = 1f + 0.12f * ease(Math.min(t, R) / (float) R) + 0.04f * tl.tension(t);
        float appear = 0.9f + 0.1f * ease(t / (float) SlayerTimeline.APPEAR);
        camera(18f + 5f * (float) Math.sin(t / 2400.0), -21f, appear * push * 1.22f, 0f, 6f, -3f, tl, t, w, h);
        long[] beats = SlayerTimeline.BEATS;
        boolean win = tl.drop().win();
        int rgb = tl.drop().colour;

        night(c, tl, t, seed, w, h);
        plot(c, PODZOL, GB, 30f, 0.8f, seed);
        GraveEndings.ground(c, tl, t);
        // The Deep Dark's sculk and the Wake's matcha, texel by texel over every face (null before the reveal).
        ChopScene.gild = GraveEndings.gild(tl, t);

        // The rest of the yard, each stone or cross on a low mound.
        // Back to front (YARD is in that order), and each grave's stone before its mound: the mound lies in front of
        // the stone, nearer the camera - drawn first, the stone covered its back and the dirt showed round it.
        for (int gi = 0; gi < YARD.length; gi++) {
            float[] gs = YARD[gi];
            float gx = gs[0], gz = gs[1], gh = gs[2], tilt = gs[3];
            if (gs[4] > 0.5f) {
                // Planks at the stones' texel density, not a whole tile squeezed onto the thin post (the user).
                int[][] post = scaled(0.55f, gh / 2f, 0.55f, gi * 3), arm = scaled(2f, 0.5f, 0.55f, gi * 3 + 1);
                inkOnly(gx, gh / 2f, gz, 0f, 0f, tilt, 0.55f, gh / 2f, 0.55f, DARK_OAK, post, 1f, c);
                inkOnly(gx, gh * 0.7f, gz, 0f, 0f, tilt, 2f, 0.5f, 0.55f, DARK_OAK, arm, 1f, c);
                plain(gx, gh / 2f, gz, 0f, 0f, tilt, 0.55f, gh / 2f, 0.55f, DARK_OAK, post, 0.55f, 0.5f, 0.46f, 1f, c);
                plain(gx, gh * 0.7f, gz, 0f, 0f, tilt, 2f, 0.5f, 0.55f, DARK_OAK, arm, 0.55f, 0.5f, 0.46f, 1f, c);
            } else {
                headstone(c, gx, gz, gh, tilt, 0.6f, false);
            }
            // The mound, from the stone's front face forwards: a low heap, a narrower hump on a wider base.
            if (GraveEndings.moundStands(tl, t, gi)) {
                float mz = gz + 0.75f + 2.7f;
                inkOnly(gx, 0.42f, mz, 0f, 0f, 0f, 2.2f, 0.42f, 2.7f, DIRT, TILE, 1f, c);
                inkOnly(gx, 1.04f, mz, 0f, 0f, 0f, 1.5f, 0.22f, 2.1f, DIRT, TILE, 1f, c);
                ChopScene.box(gx, 0.42f, mz, 0f, 0f, 0f, 2.2f, 0.42f, 2.7f, EARTH, HEAP_LOW, 0.62f, 0.56f, 0.5f, 0f, 1f, c);
                ChopScene.box(gx, 1.04f, mz, 0f, 0f, 0f, 1.5f, 0.22f, 2.1f, EARTH, HEAP_TOP, 0.62f, 0.56f, 0.5f, 0f, 1f, c);
            }
        }
        GraveEndings.afterYard(c, tl, t, w, h);

        // The gravestone, its name cut into it.
        if (GraveEndings.ownsStone(tl, t)) GraveEndings.splitStone(c, tl, t);
        else headstone(c, 0f, -8.5f, 11f, 0f, 0.86f, true);

        // The light under the lid: from the last blow, the same pale light whatever is down there.
        long struck = beats[beats.length - 1];
        float leak = t >= struck && since < 0 ? (0.35f + 0.65f * tl.tension(t)) * (0.75f + 0.25f * (float) Math.sin(t / 70.0)) : 0f;
        if (leak > 0f) ChopScene.light(0f, LID_Y + 1f, 0f, 22f, 0xD8E8D0, Math.min(1f, leak * 0.7f));
        if (since >= 0 && win) ChopScene.light(0f, LID_Y + 2f, 0f, 30f, rgb, clamp01(1.3f * (float) Math.exp(-since / 900.0) + 0.3f));

        // The coffin, and the lid on it until the reveal: hollow, once there is no lid over it - and lit from inside.
        float[] glow = GraveEndings.coffinGlow(tl, t);
        coffin(c, GraveEndings.coffinOpen(tl, t), Float.floatToRawIntBits(glow[0]), glow[1]);
        if (since >= 0 && GraveEndings.ownsLid(tl)) GraveEndings.lid(c, tl, t, since);
        else lid(c, tl, t, since, seed);

        // The mound over it: three layers, one dug off on each of the first three blows - one silhouette, inked once.
        mound(c, tl, t);

        // Dirt thrown up on each dig.
        for (int b = 0; b < beats.length - 1; b++) {
            long d = t - beats[b];
            if (d < 0 || d > 850) continue;
            float k = d / 850f;
            for (int i = 0; i < 8; i++) {
                float dx = (rnd(seed, b * 31 + i, 1) - 0.35f) * 18f, dz = (rnd(seed, b * 31 + i, 2) - 0.2f) * 8f;
                float up = 8f + 7f * rnd(seed, b * 31 + i, 3);
                float x = 3f + dx * k, y = heapTop(tl, beats[b] - 1) + 0.5f + up * k - 24f * k * k, z = dz * k + 2f;
                if (y < 0.4f) continue;
                inked(x, y, z, 3f * k * i, 2f * k, 0f, 0.55f, 0.55f, 0.55f, i % 2 == 0 ? DIRT : COARSE, TILE, 0.8f, 0.74f, 0.66f,
                        clamp01(1.4f - k), c);
            }
            ChopScene.project(3f, heapTop(tl, beats[b] - 1), 2f);
            SlayerFx.burst(c, ChopScene.P[0], ChopScene.P[1], d, 600, 14, h * 0.5f, seed + b, new int[]{0x5A3A22, 0x7A5432, 0x3C2614}, 0.9f, h * 1.6f);
        }

        // The shovel: lifted before each blow, driven into the dirt on it; stuck in the lid on the last; thrown off at the reveal.
        if (since >= 0 && GraveEndings.ownsShovel(tl)) GraveEndings.shovel(c, tl, t, since);
        else shovel(c, tl, t, seed);
        ChopScene.gild = null;

        if (since >= 0) {
            if (win) graveWin(c, tl, show, t, since, w, h);
            else graveBust(c, tl, t, since, w, h);
        }

        long still = GraveEndings.phys(tl, t);
        rain(c, still, seed, w, h, 0.5f);
        bats(c, still, seed, w, h);
        SlayerFx.edgeBands(c, w, h, 0x0A140E, 0.45f, 6);
        if (win && since >= 0) {
            float[] from = GraveEndings.showFrom(tl, t);
            showcase(c, tl, show, t, from[0], from[1], w, h, rgb);
        }
    }

    /** A stone: body, a narrower step and a cap, so it has a rounded-ish top, on its mound; the main one engraved. */
    static void headstone(ChopScene.Canvas c, float x, float z, float height, float tilt, float tint, boolean engraved) {
        float body = height * 0.78f, hw = engraved ? 4.2f : 2.3f, hd = engraved ? 0.95f : 0.7f;
        float cap = height - body;
        float[][] parts = {{x, body / 2f, z, hw, body / 2f}, {x, body + cap * 0.3f, z, hw * 0.78f, cap * 0.3f},
                {x, body + cap * 0.8f, z, hw * 0.5f, cap * 0.2f}};
        int stone = Math.round(x * 3f + z * 7f);
        for (int i = 0; i < parts.length; i++) {
            float[] p = parts[i];
            inkOnly(p[0], p[1], p[2], 0f, 0f, tilt, p[3], p[4], hd, MOSSY, scaled(p[3], p[4], hd, stone + i), 1f, c);
        }
        for (int i = 0; i < parts.length; i++) {
            float[] p = parts[i];
            plain(p[0], p[1], p[2], 0f, 0f, tilt, p[3], p[4], hd, MOSSY, scaled(p[3], p[4], hd, stone + i), tint, tint * 1.04f, tint, 1f, c);
        }
        if (engraved) lettering(c, x, z, body, hd);
    }

    /** "R.I.P." on the main stone's front: {@code body} its body's height, {@code hd} its half depth. */
    static void lettering(ChopScene.Canvas c, float x, float z, float body, float hd) {
        // "R.I.P." cut into the front, as a chisel leaves it: each stroke a dark groove, its upper edge in shadow and a
        // pale lip along its lower edge where the light catches the cut. Centred, a little above the middle.
        float cell = 0.46f;
        int cols = RIP[0].length();
        float left = x - cols * cell / 2f, top = body * 0.62f + RIP.length * cell / 2f;
        float fz = z + hd + 0.02f;
        for (int r = 0; r < RIP.length; r++) {
            for (int q = 0; q < cols; q++) {
                if (!cut(r, q)) continue;
                float px = left + q * cell, py = top - r * cell;
                decal(c, px, py, fz, cell, 0f, 0f, 0f, -cell, 0f, a(0.92f) | 0x252925);
                if (!cut(r - 1, q)) decal(c, px, py, fz + 0.01f, cell, 0f, 0f, 0f, -cell * 0.28f, 0f, a(0.9f) | 0x121512);
                if (!cut(r, q - 1)) decal(c, px, py, fz + 0.01f, cell * 0.25f, 0f, 0f, 0f, -cell, 0f, a(0.7f) | 0x161916);
                if (!cut(r + 1, q)) decal(c, px, py - cell, fz, cell, 0f, 0f, 0f, -cell * 0.3f, 0f, a(0.6f) | 0xC4CEC4);
            }
        }
    }

    static boolean cut(int r, int q) {
        return r >= 0 && r < RIP.length && q >= 0 && q < RIP[r].length() && RIP[r].charAt(q) == '1';
    }

    static void lid(ChopScene.Canvas c, SlayerTimeline tl, long t, long since, long seed) {
        if (since < 0) {
            float rattle = tl.tension(t);
            float jx = rattle * 0.3f * (float) Math.sin(t * 0.09), jy = rattle * 0.22f * Math.abs((float) Math.sin(t * 0.13));
            float rz = rattle * 0.04f * (float) Math.sin(t * 0.07);
            inked(jx, LID_Y + jy, 0f, 0f, 0f, rz, 6.8f, 0.42f, 3.2f, DARK_OAK, LID, 0.78f, 0.7f, 0.6f, 1f, c);
            float leak = t >= SlayerTimeline.BEATS[3] ? 0.35f + 0.65f * tl.tension(t) : 0f;
            if (leak > 0f) {
                // Light through the cracks: slivers along the lid's front edge, flickering.
                for (int i = 0; i < 10; i++) {
                    ChopScene.project(-6f + i * 1.33f, LID_Y - 0.45f, 3.25f);
                    square(c, ChopScene.P[0], ChopScene.P[1], 1, a(leak * (0.35f + 0.65f * rnd(seed, i, (int) (t / 90)))) | 0xE8F4E0);
                }
            }
            return;
        }
        if (!tl.drop().win()) {
            // A bust: something pushes it up a hand's breadth - fast, slowing at the top - and it slams back down and
            // bounces twice on the coffin.
            float up = since < 200 ? easeOut(since / 200f) : bounce((since - 200) / 560f);
            float lift = 1.5f * up;
            float tip = 0.07f * up + 0.025f * settle((since - 300) / 500f, 4f) * (since > 300 ? 1f : 0f);
            inked(0f, LID_Y + lift, 0f, 0f, 0f, tip, 6.8f, 0.42f, 3.2f, DARK_OAK, LID, 0.78f, 0.7f, 0.6f, 1f, c);
            return;
        }
        thrownLid(c, since);
    }

    /**
     * A win's lid: blown up off the coffin, turning over once - slowing at the top of its arc, as a thrown thing does -
     * and landing face down on the ground beside it, where it bounces and lies. {@code s} ms since it went.
     */
    static void thrownLid(ChopScene.Canvas c, long s) {
        float k = clamp01(s / 820f);
        // Height: a parabola from the coffin to the ground, its top 7 above - constant gravity, slowest at the top.
        float y = LID_Y * (1f - k) + 0.42f * k + 4f * 7f * k * (1f - k);
        // Out to the right and towards the camera, turning as it goes, so it lands well clear of the coffin and
        // reads as a lid, not a second coffin.
        float x = 17f * k, z = 8f * k;
        float turn = (float) Math.PI * k, yaw = 0.9f * k;
        if (s > 820) {
            float b = clamp01((s - 820) / 480f);
            y = 0.42f + 1.1f * 4f * b * (1f - b) * (1f - b);
            turn = (float) Math.PI + 0.08f * settle(b, 3f);
            x = 17f + 1.2f * easeOut(b);
        }
        inked(x, y, z, 0f, yaw, turn, 6.8f, 0.42f, 3.2f, DARK_OAK, LID, 0.78f, 0.7f, 0.6f, 1f, c);
    }

    static final float COFFIN_HX = 6.4f, COFFIN_HZ = 2.9f, COFFIN_WALL = 0.45f;
    /** The lid's planks at the stones' density (its long side still one tile: 16 texels over 13.6). */
    static final int[][] LID = scaled(6.8f, 0.42f, 3.2f, 11);
    static final int[][] BODY = scaled(COFFIN_HX, COFFIN_Y, COFFIN_HZ, 5), FLOOR = scaled(COFFIN_HX - COFFIN_WALL, 0.25f, COFFIN_HZ - COFFIN_WALL, 8),
            LONG_WALL = scaled(COFFIN_HX, COFFIN_Y, COFFIN_WALL / 2f, 6), SIDE_WALL = scaled(COFFIN_WALL / 2f, COFFIN_Y, COFFIN_HZ - COFFIN_WALL, 7);

    /**
     * The coffin: solid while a lid covers it; open, a floor and four walls round it, nearest last. Open, it is lit from
     * inside by {@code glow} (0..1) of {@code rgb}: the floor and the walls' inner faces in that light instead of shadow.
     * (A line of light along every rim drew an outline round the top - the user had it taken off.)
     */
    static void coffin(ChopScene.Canvas c, boolean open, int rgb, float glow) {
        float hx = COFFIN_HX, hy = COFFIN_Y, hz = COFFIN_HZ, wall = COFFIN_WALL, floor = 0.5f;
        inkOnly(0f, hy, 0f, 0f, 0f, 0f, hx, hy, hz, SPRUCE, BODY, 1f, c);
        if (!open) {
            plain(0f, hy, 0f, 0f, 0f, 0f, hx, hy, hz, SPRUCE, BODY, 0.72f, 0.6f, 0.48f, 1f, c);
            return;
        }
        glow = clamp01(glow);
        float lr = (rgb >> 16 & 255) / 255f, lg = (rgb >> 8 & 255) / 255f, lb = (rgb & 255) / 255f;
        // The floor: in shadow, or bright in the light - the drop's colour washed towards white, as a lit thing is.
        float fr = 0.34f + (0.55f + 0.45f * lr - 0.34f) * glow, fg = 0.28f + (0.55f + 0.45f * lg - 0.28f) * glow,
                fb = 0.22f + (0.55f + 0.45f * lb - 0.22f) * glow;
        ChopScene.box(0f, floor / 2f, 0f, 0f, 0f, 0f, hx - wall, floor / 2f, hz - wall, six(SPRUCE), FLOOR, fr, fg, fb, 0.35f * glow, 1f, c);
        plain(0f, hy, -hz + wall / 2f, 0f, 0f, 0f, hx, hy, wall / 2f, SPRUCE, LONG_WALL, 0.72f, 0.6f, 0.48f, 1f, c);
        // The back wall's inside: the coffin's shadow, giving way to the light from the floor.
        float inside = 2f * hy - floor - 0.05f;
        decal(c, -hx + wall, 2f * hy - 0.05f, -hz + wall + 0.02f, 2f * (hx - wall), 0f, 0f, 0f, -inside, 0f, a(0.55f * (1f - glow)) | 0x0A0604);
        if (glow > 0.02f) {
            // Brighter low down, nearest the light: three steps up the wall.
            for (int b = 0; b < 3; b++) {
                float y0 = floor + inside * b / 3f;
                decal(c, -hx + wall, y0 + inside / 3f, -hz + wall + 0.03f, 2f * (hx - wall), 0f, 0f, 0f, -inside / 3f, 0f,
                        a(glow * (0.55f - 0.15f * b)) | ChopScene.mix(rgb, 0xFFFFFF, 0.35f - 0.1f * b));
            }
        }
        plain(-hx + wall / 2f, hy, 0f, 0f, 0f, 0f, wall / 2f, hy, hz - wall, SPRUCE, SIDE_WALL, 0.72f, 0.6f, 0.48f, 1f, c);
        plain(hx - wall / 2f, hy, 0f, 0f, 0f, 0f, wall / 2f, hy, hz - wall, SPRUCE, SIDE_WALL, 0.72f, 0.6f, 0.48f, 1f, c);
        if (glow > 0.02f) {
            // The right wall's inside, the one face of a side wall the camera sees through the opening.
            for (int b = 0; b < 3; b++) {
                float y0 = floor + inside * b / 3f;
                decal(c, hx - wall - 0.03f, y0 + inside / 3f, -hz + wall, 0f, 0f, 2f * (hz - wall), 0f, -inside / 3f, 0f,
                        a(glow * (0.5f - 0.15f * b)) | ChopScene.mix(rgb, 0xFFFFFF, 0.3f - 0.1f * b));
            }
        }
        plain(0f, hy, hz - wall / 2f, 0f, 0f, 0f, hx, hy, wall / 2f, SPRUCE, LONG_WALL, 0.72f, 0.6f, 0.48f, 1f, c);
    }

    /**
     * The light pouring up out of the open coffin, the whole of it - its full length and width, not a beam out of its
     * middle (the user) - widening as it rises, a cone (the user, 2026-10-27).
     */
    static void coffinLight(ChopScene.Canvas c, SlayerTimeline tl, long t, int rgb, float on, float height) {
        if (on <= 0.01f) return;
        float hx = COFFIN_HX - COFFIN_WALL, hz = COFFIN_HZ - COFFIN_WALL, top = 2f * COFFIN_Y;
        ChopScene.project(-hx, top, hz);
        float nx0 = ChopScene.P[0], ny0 = ChopScene.P[1];
        ChopScene.project(hx, top, hz);
        float nx1 = ChopScene.P[0], ny1 = ChopScene.P[1];
        ChopScene.project(-hx, top, -hz);
        float fx0 = ChopScene.P[0], fy0 = ChopScene.P[1];
        ChopScene.project(hx, top, -hz);
        float fx1 = ChopScene.P[0], fy1 = ChopScene.P[1];
        SlayerFx.cone(c, nx0, ny0, nx1, ny1, fx0, fy0, fx1, fy1, height * on, t, rgb, on, tl.seed());
    }

    /** The heap's faces: coarse dirt on top, dirt down the sides. */
    static final String[] EARTH = {DIRT, DIRT, COARSE, DIRT, DIRT, DIRT};
    static final int[][] HEAP_LOW = {{0, 13, 11, 3}, {5, 13, 11, 3}, {0, 0, 9, 11}, {0, 0, 9, 11}, {0, 13, 9, 3}, {7, 13, 9, 3}};
    static final int[][] HEAP_TOP = {{2, 8, 8, 2}, {6, 8, 8, 2}, {3, 2, 6, 8}, {3, 2, 6, 8}, {3, 8, 6, 2}, {5, 8, 6, 2}};
    static final float HEAP_BASE = LID_Y + 0.42f;

    /**
     * The earth over the coffin: a low heap of 2-unit columns on the lid, their heights a dome; each face cut from the
     * dirt texture where it sits, so the earth runs on across the columns (a whole texture on every block read as a wall
     * of bricks - the user). Each of the first three blows takes a layer off, the heap slumping over a moment, not
     * jumping. The same for every outcome (its shape comes from the seed alone).
     */
    static void mound(ChopScene.Canvas c, SlayerTimeline tl, long t) {
        java.util.List<float[]> cols = new java.util.ArrayList<>();
        for (int zi = -1; zi <= 1; zi++) {
            for (int xi = -3; xi <= 2; xi++) {
                float hgt = heap(tl, t, xi, zi);
                if (hgt <= 0.2f) continue;
                cols.add(new float[]{(xi + 0.5f) * 2f, zi * 1.8f, hgt, xi, zi});
            }
        }
        if (cols.isEmpty()) return;
        cols.sort((p, q) -> Float.compare(ChopScene.depth(p[0], HEAP_BASE + p[2] / 2f, p[1]), ChopScene.depth(q[0], HEAP_BASE + q[2] / 2f, q[1])));
        for (float[] k : cols) inkOnly(k[0], HEAP_BASE + k[2] / 2f, k[1], 0f, 0f, 0f, 1f, k[2] / 2f, 0.9f, DIRT, TILE, 1f, c);
        for (float[] k : cols) {
            int xi = (int) k[3], zi = (int) k[4];
            int tall = Math.max(2, Math.min(16, Math.round(k[2] * 4f)));
            int ux = Math.floorMod(xi * 8, 16), uz = Math.floorMod(zi * 7, 10), v = 16 - tall;
            int[][] rects = {{uz, v, 7, tall}, {uz, v, 7, tall}, {ux, uz, 8, 7}, {ux, uz, 8, 7}, {ux, v, 8, tall}, {ux, v, 8, tall}};
            ChopScene.box(k[0], HEAP_BASE + k[2] / 2f, k[1], 0f, 0f, 0f, 1f, k[2] / 2f, 0.9f, EARTH, rects, 0.86f, 0.8f, 0.74f, 0f, 1f, c);
        }
    }

    /** A column of the heap's height now: the dome, less what the blows have dug - each settling over 180 ms. */
    static float heap(SlayerTimeline tl, long t, int xi, int zi) {
        float dug = 0f;
        for (int b = 0; b < 3; b++) dug += easeOut((t - SlayerTimeline.BEATS[b]) / 180f);
        float x = (xi + 0.5f) * 2f, z = zi * 1.8f;
        float dome = (1f - (x / 7f) * (x / 7f)) * (1f - (z / 3.6f) * (z / 3.6f));
        float hgt = 4.6f * dome + 0.6f * rnd(tl.seed(), xi * 7 + zi, 13) - 1.62f * dug;
        return Math.max(0f, Math.round(hgt * 2f) / 2f);
    }

    /** Where the shovel's blade meets the earth: the top of the column under it, or the lid once it is bare. */
    static float heapTop(SlayerTimeline tl, long t) {
        return HEAP_BASE + heap(tl, t, 1, 0);
    }

    /**
     * The shovel, blade down (it was drawn handle first - the user). Each stroke: stuck where the last one bit, wobbling;
     * pulled out and lifted, fast and slowing; drawn back a little further at the top; then driven down, gathering speed
     * to the moment it bites. The last blow goes into the lid and it stays there, trembling. At the reveal it is thrown.
     */
    static void shovel(ChopScene.Canvas c, SlayerTimeline tl, long t, long seed) {
        long[] beats = SlayerTimeline.BEATS;
        if (t >= SlayerTimeline.REVEAL) {
            thrownShovel(c, t - SlayerTimeline.REVEAL);
            return;
        }
        int next = 0;
        while (next < beats.length && beats[next] <= t) next++;
        float biteY = heapTop(tl, t), lift, roll;
        if (next == beats.length) {
            long d = t - beats[next - 1];
            biteY = HEAP_BASE;
            lift = -0.05f;
            roll = -0.42f + 0.16f * settle(d / 650f, 4f) + 0.03f * (float) Math.sin(t * 0.09) * tl.tension(t);
        } else {
            long prev = next == 0 ? beats[0] - 760 : beats[next - 1];
            float p = (t - prev) / (float) (beats[next] - prev);
            if (p < 0f) {
                lift = 0f;
                roll = -0.62f;
            } else if (p < 0.18f) {
                lift = 0f;
                roll = -0.62f + (next == 0 ? 0f : 0.12f * settle(p / 0.18f, 3f));
            } else if (p < 0.6f) {
                float k = easeOut((p - 0.18f) / 0.42f);
                lift = k;
                roll = -0.62f - 0.5f * k;
            } else if (p < 0.76f) {
                float k = ease((p - 0.6f) / 0.16f);
                lift = 1f + 0.08f * k;
                roll = -1.12f - 0.12f * k;
            } else {
                float k = easeIn((p - 0.76f) / 0.24f);
                lift = 1.08f * (1f - k);
                roll = -1.24f + 0.62f * k;
            }
        }
        shovelAt(c, 4f, biteY + 7f * lift, 2.6f, roll, 1f);
    }

    /** The shovel with its blade's tip at (tx, ty, tz), leaning {@code roll} (0 upright, negative leaning right). */
    static void shovelAt(ChopScene.Canvas c, float tx, float ty, float tz, float roll, float alpha) {
        float half = 4.2f;
        float cx = tx + (float) Math.sin(-roll) * half * 1.15f, cy = ty + (float) Math.cos(roll) * half * 1.15f;
        // The texture's diagonal runs handle (lower left) to blade (upper right); turned to point from the centre down
        // to the tip, blade first. (It was turned by +roll, the wrong way, so a lean of 0.3 drew nearly flat.)
        item(c, SHOVEL, cx, cy, tz, half, 0.35f, -roll + (float) Math.PI * 0.75f, alpha);
    }

    /** At the reveal: flung out of the lid, over once, and stuck into the ground blade first, where it wobbles still. */
    static void thrownShovel(ChopScene.Canvas c, long s) {
        float k = clamp01(s / 720f);
        float tx = 4f + 8f * k, ty = HEAP_BASE * (1f - k) + 0.35f * k + 4f * 6.5f * k * (1f - k), tz = 2.6f + 1.6f * k;
        float roll = -0.42f + ((float) Math.PI * 2f + 0.12f) * k;
        if (s > 720) roll = -0.3f + 0.22f * settle((s - 720) / 700f, 4f);
        shovelAt(c, tx, ty, tz, roll, 1f);
    }

    static void graveBust(ChopScene.Canvas c, SlayerTimeline tl, long t, long since, int w, int h) {
        ChopScene.project(0f, LID_Y + 0.5f, 2f);
        float x = ChopScene.P[0], y = ChopScene.P[1];
        // Dust out of the gap as it drops back, and nothing else.
        SlayerFx.burst(c, x, y, since - 380, 900, 26, h * 0.32f, tl.seed() + 7, new int[]{0x6E655A, 0x4A433C, 0x8A8070}, 0.8f, -h * 0.08f);
        SlayerFx.burst(c, x, y, since - 380, 700, 10, h * 0.5f, tl.seed() + 9, new int[]{0x2A2622}, 0.7f, h * 0.9f);
    }

    static void graveWin(ChopScene.Canvas c, SlayerTimeline tl, Show show, long t, long since, int w, int h) {
        if (tl.ending() != Ending.PLAIN) {
            // The special endings (GraveEndings) draw their own moment; until the drop is up they own the screen.
            GraveEndings.draw(c, tl, show, t, since, w, h);
            return;
        }
        int rgb = tl.drop().colour;
        ChopScene.project(0f, LID_Y, 0f);
        float x = ChopScene.P[0], y = ChopScene.P[1];
        float on = clamp01(since / 160f) * clamp01((tl.arrive() + 600 - t) / 700f + 0.25f);
        coffinLight(c, tl, t, rgb, on * ease(since / 300f), h * 0.5f);
        ChopScene.glow(c, x, y, h * 0.5f, rgb, 0.45f * (float) Math.exp(-since / 900.0) + 0.1f);
        SlayerFx.burst(c, x, y, since, 900, 30, h * 0.7f, tl.seed() + 3, new int[]{rgb, 0xFFFFFF, ChopScene.mix(rgb, 0x000000, 0.4f)}, 1f, h * 0.9f);
        // Motes of the drop's colour rising out of the open coffin.
        for (int i = 0; i < 26; i++) {
            float life = 1100f + 500f * rnd(tl.seed(), i, 7);
            float ph = ((since + rnd(tl.seed(), i, 8) * life) % life) / life;
            float px = x + (rnd(tl.seed(), i, 9) - 0.5f) * h * 0.18f * (1f - 0.5f * ph), py = y - ph * h * 0.4f;
            square(c, px, py, ph < 0.3f ? 2 : 1, a((1f - ph) * 0.85f) | (i % 3 == 0 ? 0xFFFFFF : rgb));
        }
    }

    /** The night over the game: a pale moon high on the left, its glow stepped. No storm - the user wanted no lightning. */
    static void night(ChopScene.Canvas c, SlayerTimeline tl, long t, long seed, int w, int h) {
        float mx = w * 0.14f, my = h * 0.14f, ms = h * 0.1f;
        ChopScene.glow(c, mx, my, ms * 4.2f, 0x9FB6D8, 0.18f);
        ChopScene.glow(c, mx, my, ms * 1.9f, 0xE8F0FF, 0.35f);
        // A pixel moon of our own: the game's moon texture is drawn added to the sky, and blended here its black showed.
        int gx = SlayerFx.g(mx), gy = SlayerFx.g(my), r = Math.max(3, Math.round(ms / U / 2f));
        for (int j = -r; j <= r; j++) {
            for (int i = -r; i <= r; i++) {
                if (i * i + j * j > r * r + r * 0.4f) continue;
                boolean crater = SlayerFx.rnd(seed, i * 17 + j, 71) < 0.16f && i * i + j * j < (r - 1) * (r - 1);
                boolean rim = i * i + j * j > (r - 1) * (r - 1);
                SlayerFx.cells(c, gx + i, gy + j, 1, 1, a(0.95f) | (crater ? 0xB8C2D0 : rim ? 0xCFD8E6 : 0xEEF3FA));
            }
        }
    }

    /** Rain as the game's own: short streaks of cells, sparse, falling at a slant. */
    static void rain(ChopScene.Canvas c, long t, long seed, int w, int h, float alpha) {
        for (int i = 0; i < 70; i++) {
            float speed = 1.3f + 0.5f * rnd(seed, i, 1);
            int len = 2 + (int) (rnd(seed, i, 2) * 3);
            float ph = ((t / 1000f) * speed + rnd(seed, i, 4)) % 1f;
            float y = ph * (h + len * U * 2f) - len * U;
            float x = rnd(seed, i, 3) * w * 1.2f - ph * h * 0.16f;
            int col = a(alpha * (0.35f + 0.4f * rnd(seed, i, 5))) | 0xA9BBD4;
            int gx = SlayerFx.g(x), gy = SlayerFx.g(y);
            SlayerFx.cells(c, gx, gy, 1, len, col);
        }
    }

    /** Bats crossing the top: pixel silhouettes, wings beating, red eyes. */
    static void bats(ChopScene.Canvas c, long t, long seed, int w, int h) {
        for (int i = 0; i < 4; i++) {
            float period = 5200f + 3600f * rnd(seed, i, 1);
            float ph = ((t + rnd(seed, i, 2) * period) % period) / period;
            boolean ltr = rnd(seed, i, 3) < 0.5f;
            float x = ltr ? -0.1f * w + ph * 1.2f * w : 1.1f * w - ph * 1.2f * w;
            float y = h * (0.06f + 0.2f * rnd(seed, i, 4)) + (float) Math.sin(t / 160.0 + i * 2) * h * 0.012f;
            int gx = SlayerFx.g(x), gy = SlayerFx.g(y);
            boolean up = ((t / 110 + i) % 2) == 0;
            int col = a(0.95f) | 0x0C0910;
            SlayerFx.cells(c, gx - 1, gy - 1, 2, 2, col);
            SlayerFx.cells(c, gx - 1, gy - 2, 1, 1, col);
            SlayerFx.cells(c, gx, gy - 2, 1, 1, col);
            int wy = up ? -1 : 0;
            SlayerFx.cells(c, gx - 4, gy + wy, 3, 1, col);
            SlayerFx.cells(c, gx + 1, gy + wy, 3, 1, col);
            SlayerFx.cells(c, gx - 5, gy + wy + (up ? -1 : 1), 1, 1, col);
            SlayerFx.cells(c, gx + 4, gy + wy + (up ? -1 : 1), 1, 1, col);
            SlayerFx.cells(c, gx - 1, gy - 1, 1, 1, a(0.95f) | 0xFF3B30);
        }
    }

    // ================================================================== The Cocoon

    /** The cocoon's profile, bottom to top: a radius per layer. */
    static final float[] LAYER_R = {1.4f, 2.6f, 3.6f, 4.3f, 4.7f, 4.8f, 4.6f, 4.1f, 3.4f, 2.5f, 1.6f};
    static final float LAYER_H = 1.5f, COCOON_BASE = -1f;
    static final int VENOM = 0x9BE83A;

    static float cocoonTop() {
        return COCOON_BASE + LAYER_H * LAYER_R.length;
    }

    static void cocoon(ChopScene.Canvas c, SlayerTimeline tl, Show show, long t, int w, int h) {
        long seed = tl.seed();
        long R = SlayerTimeline.REVEAL;
        long since = t - R;
        // The Red Thread stops time: everything that sways, bobs or twitches runs on this.
        long still = CocoonEndings.phys(tl, t);
        float push = 1f + 0.1f * ease(Math.min(t, R) / (float) R) + 0.04f * tl.tension(t);
        float appear = 0.9f + 0.1f * ease(t / (float) SlayerTimeline.APPEAR);
        camera(-10f + 5f * (float) Math.sin(still / 2400.0), -8f, appear * push * 1.12f, 0f, 7f, 0f, tl, t, w, h);
        boolean win = tl.drop().win();
        int rgb = tl.drop().colour;

        // The great web behind it, and the corners': cells, see-through, over the game. Each can reel into the
        // cocoon (the Red Thread) and grow eyes (A Thousand Eyes): CocoonEndings says how far, per web.
        ChopScene.project(0f, 7f, 0f);
        float tx = ChopScene.P[0], ty = ChopScene.P[1];
        CocoonEndings.WEB_POINTS.clear();
        ChopScene.project(0f, cocoonTop() + 5f, -14f);
        web(c, ChopScene.P[0], ChopScene.P[1], h * 0.5f, 11, 7, seed, 0.26f, still, CocoonEndings.webState(tl, t, 4), tx, ty);
        float cr = h * 0.42f;
        cornerWeb(c, 0f, 0f, 0.0, Math.PI / 2, cr, seed + 1, 0.3f, still, CocoonEndings.webState(tl, t, 0), tx, ty);
        cornerWeb(c, w, 0f, Math.PI / 2, Math.PI, cr, seed + 2, 0.3f, still, CocoonEndings.webState(tl, t, 1), tx, ty);
        cornerWeb(c, 0f, h, -Math.PI / 2, 0.0, cr * 0.85f, seed + 3, 0.24f, still, CocoonEndings.webState(tl, t, 2), tx, ty);
        cornerWeb(c, w, h, Math.PI, Math.PI * 1.5, cr * 0.85f, seed + 4, 0.24f, still, CocoonEndings.webState(tl, t, 3), tx, ty);
        // Little spiders let down from the top, closer with every cut.
        float[] dangle = {0.12f, 0.27f, 0.72f, 0.87f};
        float drop = CocoonEndings.danglersGone(tl, t);
        for (int i = 0; i < dangle.length && drop < 1f; i++) {
            float low = 0.1f + 0.18f * rnd(seed, i, 50) + 0.04f * (tl.beat(t) + 1);
            float bob = 0.5f + 0.5f * (float) Math.sin(still / (1100.0 + 300 * i) + i * 1.7);
            dangler(c, dangle[i] * w, h * low * (0.6f + 0.4f * bob) * (1f - drop), still, i);
        }

        // The spider, up on the web to the right, watching.
        spider(c, tl, still, since);

        // The thread it hangs from, right from the top of the screen.
        float sway = 0.04f * (float) Math.sin(still / 520.0) + 0.05f * tl.tension(t) * (float) Math.sin(still / 90.0);
        ChopScene.project((float) Math.sin(sway) * cocoonTop(), cocoonTop() + 0.3f, 0f);
        if (since < 0 || !win || CocoonEndings.keepsThread(tl)) SlayerFx.pline(c, ChopScene.P[0], 0f, ChopScene.P[0], ChopScene.P[1], 1,
                a(0.85f) | (CocoonEndings.threadRed(tl, t) ? 0xCB4154 : 0xEDEDED));

        // The light inside once the last cut is in: the same pale light whatever is in there.
        long last = SlayerTimeline.BEATS[3];
        float leak = t >= last && since < 0 ? tl.tension(t) * (0.8f + 0.2f * (float) Math.sin(t / 60.0)) : 0f;
        if (leak > 0f) ChopScene.light(0f, 7f, 4f, 20f, 0xE8F0D8, Math.min(1f, leak * 0.6f));
        if (since >= 0 && win) ChopScene.light(0f, 7f, 4f, 24f, rgb, clamp01(1.2f * (float) Math.exp(-since / 800.0) + 0.3f));

        if (since >= 0 && CocoonEndings.ownsBody(tl)) CocoonEndings.body(c, tl, t, since, sway);
        else body(c, tl, t, since, sway);
        sword(c, tl, t, h);

        if (since >= 0) {
            if (win) cocoonWin(c, tl, show, t, since, w, h);
            else cocoonBust(c, tl, t, since, w, h);
        }
        SlayerFx.edgeBands(c, w, h, 0x0B1408, 0.42f, 6);
        if (win && since >= 0) {
            ChopScene.project(0f, 7f, 0f);
            showcase(c, tl, show, t, ChopScene.P[0], ChopScene.P[1], w, h, rgb);
        }
    }

    /** A web point moved {@code reel} of the way to the cocoon (tx, ty), and (A Thousand Eyes) recorded for an eye. */
    static float rx(float x, float reel, float tx) {
        return x + (tx - x) * reel;
    }

    /** The cocoon: one tapered body (layers, outlined as one), silk wound round it, the cuts so far; split at the reveal. */
    static void body(ChopScene.Canvas c, SlayerTimeline tl, long t, long since, float sway) {
        int n = LAYER_R.length;
        boolean split = since >= 0;
        if (split && since > 1150) return;
        float alpha = split ? 1f - easeIn(clamp01((since - 750) / 400f)) : 1f;
        // The halves swing open past wide and back (a spring), then drop.
        float open = split ? (tl.drop().win() ? 1f : 0.45f) * (1f - settle(since / 900f, 2.2f)) : 0f;
        float sag = split ? 14f * easeIn(clamp01((since - 380) / 700f)) : 0f;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < n; i++) {
                float y = COCOON_BASE + (i + 0.5f) * LAYER_H;
                float r = LAYER_R[i];
                float px = (float) Math.sin(sway) * y;
                if (!split) {
                    if (pass == 0) inkOnly(px, y, 0f, 0f, 0.4f, -sway, r, LAYER_H * 0.52f, r, WOOL, TILE, 1f, c);
                    else plain(px, y, 0f, 0f, 0.4f, -sway, r, LAYER_H * 0.52f, r, WOOL, TILE, 0.92f, 0.92f, 0.88f, 1f, c);
                } else {
                    for (int side = -1; side <= 1; side += 2) {
                        float ox = side * (r * 0.5f + 9f * open), oy = y - sag, rot = side * 1.1f * open, oz = -1.5f * open;
                        if (pass == 0) inkOnly(px + ox, oy, oz, 0f, 0.4f, rot, r * 0.5f, LAYER_H * 0.52f, r, WOOL, TILE, alpha, c);
                        else plain(px + ox, oy, oz, 0f, 0.4f, rot, r * 0.5f, LAYER_H * 0.52f, r, WOOL, TILE, 0.92f, 0.92f, 0.88f, alpha, c);
                    }
                }
            }
        }
        if (split) return;
        // Silk wound round it: helices of pale cells over the near side.
        for (int s = 0; s < 3; s++) {
            for (int q = 0; q <= 90; q++) {
                float f = q / 90f;
                float y = COCOON_BASE + 0.4f + f * (LAYER_H * n - 0.8f);
                int li = Math.min(n - 1, Math.max(0, (int) ((y - COCOON_BASE) / LAYER_H)));
                float r = LAYER_R[li] + 0.06f;
                double ang = s * 2.1 + f * Math.PI * 2 * 2.3 + t / 4000.0;
                float x = (float) Math.cos(ang) * r, z = (float) Math.sin(ang) * r;
                // Only the strands on the camera's side.
                if (ChopScene.depth(x, y, z) < ChopScene.depth(0f, y, 0f)) continue;
                ChopScene.project(x + (float) Math.sin(sway) * y, y, z);
                square(c, ChopScene.P[0], ChopScene.P[1], 1, a(0.55f) | 0xFFFFFF);
            }
        }
        // The cuts so far: gashes in the silk, following its curve - a dark split, torn fibres standing off its edges,
        // a loose thread hanging from one end; the pale light in them once the last is in. (Straight lines across the
        // front read as straps tied round it.)
        int cuts = tl.beat(t) + 1;
        float leak = tl.tension(t);
        for (int b = 0; b < cuts; b++) gash(c, tl.seed(), b, sway, leak, t);
    }

    /** One gash, {@code b} the blow that made it. */
    static void gash(ChopScene.Canvas c, long seed, int b, float sway, float leak, long t) {
        float y0 = COCOON_BASE + LAYER_H * (3f + b * 1.9f);
        float slope = (b % 2 == 0 ? 0.55f : -0.55f);
        float span = 2.7f - 0.25f * (b % 2);
        int n = 14;
        float[] xs = new float[n + 1], ys = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float u = -1f + 2f * i / n;
            float x = u * span, y = y0 + slope * u * span + 0.18f * (float) Math.sin(u * 5 + b);
            int li = Math.min(LAYER_R.length - 1, Math.max(0, (int) ((y - COCOON_BASE) / LAYER_H)));
            float r = LAYER_R[li];
            float z = (float) Math.sqrt(Math.max(0.01f, r * r - x * x)) + 0.08f;
            ChopScene.project(x + (float) Math.sin(sway) * y, y, z);
            xs[i] = ChopScene.P[0];
            ys[i] = ChopScene.P[1];
        }
        for (int i = 0; i < n; i++) {
            // Thickest in the middle, a single cell at the ends.
            int thick = (i > 2 && i < n - 3) ? 2 : 1;
            SlayerFx.pline(c, xs[i], ys[i], xs[i + 1], ys[i + 1], thick, a(0.95f) | 0x2B241D, i > 0);
        }
        for (int i = 1; i < n; i++) {
            // Torn fibres: white cells standing off the split, above and below.
            if (rnd(seed, b * 41 + i, 3) < 0.45f) square(c, xs[i], ys[i] - U * 2, 1, a(0.85f) | 0xFFFFFF);
            if (rnd(seed, b * 41 + i, 4) < 0.35f) square(c, xs[i], ys[i] + U * 2, 1, a(0.75f) | 0xF2F2EC);
            if (leak > 0f && i > 1 && i < n - 1) square(c, xs[i], ys[i], 1, a(leak * (0.55f + 0.45f * rnd(seed, i, (int) (t / 90)))) | 0xF0F8E8);
        }
        // A loose thread off one end, a few cells down.
        int end = b % 2 == 0 ? n : 0;
        for (int k = 1; k <= 3; k++) square(c, xs[end] + (b % 2 == 0 ? U : -U) * (k / 2), ys[end] + U * k, 1, a(0.8f) | 0xEDEDE6);
    }

    /**
     * The sword, a cut on each beat: it comes up and is drawn back past where it starts - slowing as it gathers; then
     * the cut, accelerating, fastest as it crosses the cocoon on the beat; then it carries on past, slowing, and fades.
     * Its smear trails only while it is fast.
     */
    static void sword(ChopScene.Canvas c, SlayerTimeline tl, long t, int h) {
        long[] beats = SlayerTimeline.BEATS;
        for (int b = 0; b < beats.length; b++) {
            long d = t - beats[b];
            if (d < -440 || d > 320 || t >= SlayerTimeline.REVEAL) continue;
            boolean down = b % 2 == 0;
            float baseY = COCOON_BASE + LAYER_H * (3f + b * 1.9f);
            float pos = slash(d), alpha = d < -300 ? clamp01((d + 440) / 140f) : d > 80 ? 1f - easeIn((d - 80) / 240f) : 1f;
            if (d > -170 && d < 140) {
                for (int s = 6; s >= 1; s--) {
                    float kk = slash(d - s * 14L);
                    ChopScene.project(-11f + 22f * kk, baseY + (down ? 2.4f : -2.4f) * (1f - 2f * kk), 7.5f);
                    square(c, ChopScene.P[0], ChopScene.P[1], s < 3 ? 2 : 1, a(alpha * (0.75f - s * 0.1f)) | 0xF2F6FF);
                }
            }
            float sx = -11f + 22f * pos, sy = baseY + (down ? 2.4f : -2.4f) * (1f - 2f * pos);
            float roll = (down ? 0.9f : 2.2f) + 0.55f * (pos - 0.5f);
            item(c, SWORD, sx, sy, 7.5f, 4.6f, -0.2f, roll, alpha);
        }
    }

    static final float SLASH_END = -0.16f + 1.36f * (230f / 244f) * (230f / 244f);

    /** The cut's place across the cocoon, 0..1 (0.5 on it), {@code d} ms from the beat. */
    static float slash(long d) {
        if (d < -170) return -0.02f - 0.14f * easeOut(clamp01((d + 440) / 270f));
        // Accelerating (k squared) from the top of the draw-back to 0.5 on the beat, and on past the far side.
        if (d < 60) {
            float k = (d + 170) / 244f;
            return -0.16f + 1.36f * k * k;
        }
        return SLASH_END + 0.16f * easeOut(clamp01((d - 60) / 260f));
    }

    static void cocoonBust(ChopScene.Canvas c, SlayerTimeline tl, long t, long since, int w, int h) {
        ChopScene.project(0f, 7f, 4f);
        float x = ChopScene.P[0], y = ChopScene.P[1];
        // It hangs open, empty; a few flies buzz out and away.
        long seed = tl.seed();
        for (int i = 0; i < 5; i++) {
            long d = since - 120 - i * 90L;
            if (d < 0) continue;
            float s = d / 1000f;
            double ang = -Math.PI / 2 + (rnd(seed, i, 31) - 0.5f) * 2.4;
            float v = h * (0.35f + 0.25f * rnd(seed, i, 32));
            float fx = x + (float) Math.cos(ang) * v * s + (float) Math.sin(d / 40.0 + i) * U * 2;
            float fy = y + (float) Math.sin(ang) * v * s + (float) Math.cos(d / 33.0 + i) * U * 2;
            float al = clamp01(1.4f - s * 1.4f);
            int gx = SlayerFx.g(fx), gy = SlayerFx.g(fy);
            SlayerFx.cells(c, gx, gy, 2, 1, a(al) | 0x1A1A1A);
            boolean flap = ((d / 30) % 2) == 0;
            SlayerFx.cells(c, gx + (flap ? 0 : 1), gy - 1, 1, 1, a(al * 0.7f) | 0xD8E2E8);
        }
        SlayerFx.burst(c, x, y, since, 800, 14, h * 0.3f, seed + 5, new int[]{0xE8E8E0, 0xC8C8C0}, 0.7f, h * 0.4f);
    }

    static void cocoonWin(ChopScene.Canvas c, SlayerTimeline tl, Show show, long t, long since, int w, int h) {
        if (tl.ending() != Ending.PLAIN) {
            CocoonEndings.draw(c, tl, show, t, since, w, h);
            return;
        }
        int rgb = tl.drop().colour;
        ChopScene.project(0f, 7f, 3f);
        float x = ChopScene.P[0], y = ChopScene.P[1];
        ChopScene.glow(c, x, y, h * 0.5f, rgb, 0.45f * (float) Math.exp(-since / 800.0) + 0.1f);
        SlayerFx.burst(c, x, y, since, 1000, 34, h * 0.6f, tl.seed() + 3, new int[]{rgb, 0xFFFFFF, 0xE8E8E0}, 1f, h * 0.8f);
    }

    /** A web of cells: spokes out of the hub, a spiral strung between them, sagging. */
    static void web(ChopScene.Canvas c, float hx, float hy, float radius, int spokes, int rings, long seed, float alpha, long t,
                    float[] state, float tx, float ty) {
        float reel = state[0];
        if (reel >= 1f) return;
        int col = ChopScene.mix(0xDADADA, CocoonEndings.BRICK, state[1]), col2 = ChopScene.mix(0xCFCFCF, CocoonEndings.BRICK, state[1]);
        float al = alpha + (0.55f - alpha) * state[1];
        float[][] px = new float[spokes][rings + 1], py = new float[spokes][rings + 1];
        for (int s = 0; s < spokes; s++) {
            double ang = s * Math.PI * 2 / spokes + 0.13 + 0.01 * Math.sin(t / 1200.0);
            float len = radius * (0.85f + 0.25f * rnd(seed, s, 1));
            for (int r = 0; r <= rings; r++) {
                px[s][r] = rx(hx + (float) Math.cos(ang) * len * r / rings, reel, tx);
                py[s][r] = rx(hy + (float) Math.sin(ang) * len * r / rings, reel, ty);
                if (state[2] > 0f && r > 0) CocoonEndings.WEB_POINTS.add(new float[]{px[s][r], py[s][r]});
            }
            SlayerFx.pline(c, rx(hx, reel, tx), rx(hy, reel, ty), px[s][rings], py[s][rings], 1, a(al) | col);
        }
        for (int r = 1; r <= rings; r++) {
            for (int s = 0; s < spokes; s++) {
                int n = (s + 1) % spokes;
                float mx = (px[s][r] + px[n][r]) / 2f, my = (py[s][r] + py[n][r]) / 2f;
                mx += (rx(hx, reel, tx) - mx) * 0.08f;
                my += (rx(hy, reel, ty) - my) * 0.08f;
                SlayerFx.pline(c, px[s][r], py[s][r], mx, my, 1, a(al * 0.8f) | col2, true);
                SlayerFx.pline(c, mx, my, px[n][r], py[n][r], 1, a(al * 0.8f) | col2, true);
            }
        }
    }

    static void cornerWeb(ChopScene.Canvas c, float cx, float cy, double a0, double a1, float radius, long seed, float alpha, long t,
                          float[] state, float tx, float ty) {
        float reel = state[0];
        if (reel >= 1f) return;
        int col = ChopScene.mix(0xE6E6E6, CocoonEndings.BRICK, state[1]), col2 = ChopScene.mix(0xDCDCDC, CocoonEndings.BRICK, state[1]);
        alpha = alpha + (0.6f - alpha) * state[1];
        int spokes = 6, rings = 6;
        float sway = 0.02f * (float) Math.sin(t / 900.0 + seed % 5);
        float[][] px = new float[spokes][rings + 1], py = new float[spokes][rings + 1];
        for (int s = 0; s < spokes; s++) {
            double ang = a0 + (a1 - a0) * s / (spokes - 1) + sway;
            float len = radius * (0.85f + 0.3f * rnd(seed, s, 1));
            for (int r = 0; r <= rings; r++) {
                px[s][r] = rx(cx + (float) Math.cos(ang) * len * r / rings, reel, tx);
                py[s][r] = rx(cy + (float) Math.sin(ang) * len * r / rings, reel, ty);
                if (state[2] > 0f && r > 0) CocoonEndings.WEB_POINTS.add(new float[]{px[s][r], py[s][r]});
            }
            SlayerFx.pline(c, rx(cx, reel, tx), rx(cy, reel, ty), px[s][rings], py[s][rings], 1, a(alpha) | col);
        }
        float ccx = rx(cx, reel, tx), ccy = rx(cy, reel, ty);
        for (int r = 1; r <= rings; r++) {
            for (int s = 0; s + 1 < spokes; s++) {
                float mx = (px[s][r] + px[s + 1][r]) / 2f, my = (py[s][r] + py[s + 1][r]) / 2f;
                mx += (ccx - mx) * 0.1f;
                my += (ccy - my) * 0.1f;
                SlayerFx.pline(c, px[s][r], py[s][r], mx, my, 1, a(alpha * 0.8f) | col2, true);
                SlayerFx.pline(c, mx, my, px[s + 1][r], py[s + 1][r], 1, a(alpha * 0.8f) | col2, true);
                if (rnd(seed, s * 13 + r, 2) < 0.12f && state[1] < 0.5f) {
                    float tw = 0.6f + 0.4f * (float) Math.sin(t / 240.0 + s * 3 + r);
                    square(c, mx, my, 1, a(alpha * 2f * tw) | VENOM);
                }
            }
        }
    }

    /** A small spider on its own thread, let down from the top of the screen: pixel art, legs twitching. */
    static void dangler(ChopScene.Canvas c, float x, float y, long t, int i) {
        SlayerFx.pline(c, x, 0f, x, y - U * 2, 1, a(0.6f) | 0xE8E8E8);
        int gx = SlayerFx.g(x), gy = SlayerFx.g(y);
        int col = a(1f) | 0x231A16;
        boolean tw = ((t / 140 + i) % 2) == 0;
        SlayerFx.cells(c, gx - 1, gy - 1, 3, 3, col);
        SlayerFx.cells(c, gx, gy + 2, 1, 1, col);
        for (int l = 0; l < 3; l++) {
            int ly = gy - 1 + l + (tw && l == 1 ? -1 : 0);
            SlayerFx.cells(c, gx - 3, ly, 2, 1, col);
            SlayerFx.cells(c, gx + 2, ly, 2, 1, col);
        }
        SlayerFx.cells(c, gx - 1, gy + 1, 1, 1, a(1f) | 0xE0261E);
        SlayerFx.cells(c, gx + 1, gy + 1, 1, 1, a(1f) | 0xE0261E);
    }

    /**
     * The vanilla spider (64 x 32), posed as SpiderModel poses it: head, neck, abdomen and eight legs splayed - each leg
     * 16 x 2 x 2 from the body's side, rolled down 45 degrees front and back and 33 between, fanned round the body.
     */
    static void spider(ChopScene.Canvas c, SlayerTimeline tl, long t, long since) {
        float s = 0.55f;
        int beat = tl.beat(t) + 1;
        float bx = 13f - beat * 0.8f, by = 17f - beat * 0.5f, bz = -12f;
        float rear = since >= 0 && tl.drop().win() ? ease(since / 600f) : 0f;
        float eyes = Math.max(tl.tension(t), since >= 0 ? 1f : 0f);
        // Belly on the web, back to the camera, head down towards the cocoon: so its legs spread across the web as a
        // star, as a spider's do. (Facing sideways, they ran into the screen and hung like a squid's.)
        float[] fwd = {-0.42f, -0.88f, 0.22f - 0.6f * rear};
        normalize(fwd);
        float[] back = {0f, 0.1f, 1f};
        float dot = back[0] * fwd[0] + back[1] * fwd[1] + back[2] * fwd[2];
        back = new float[]{back[0] - fwd[0] * dot, back[1] - fwd[1] * dot, back[2] - fwd[2] * dot};
        normalize(back);
        float[] right = cross(back, fwd), up = back;
        normalize(right);
        java.util.List<Object[]> parts = new java.util.ArrayList<>();
        int[][] leg = uv(18, 0, 16, 2, 2);
        float[] zr = {0.785f, 0.58f, 0.58f, 0.785f};
        float[] yr = {0.785f, 0.39f, -0.39f, -0.785f};
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 4; i++) {
                float tw = 0.1f * (float) Math.sin(t / 110.0 + i * 1.7 + side) * (0.4f + eyes);
                // A leg's direction: out sideways, fanned forward or back, rolled down.
                float fan = yr[i] * (side > 0 ? 1 : -1) * -1f, drop = zr[i] + tw;
                float ox = side * (float) Math.cos(fan) * (float) Math.cos(drop), of = (float) Math.sin(fan) * (float) Math.cos(drop) * side;
                float oy = -(float) Math.sin(drop);
                float[] dir = {right[0] * ox + fwd[0] * of + up[0] * oy, right[1] * ox + fwd[1] * of + up[1] * oy, right[2] * ox + fwd[2] * of + up[2] * oy};
                normalize(dir);
                float px = bx + fwd[0] * (1.5f - i) * s * 1.2f, py = by + fwd[1] * (1.5f - i) * s * 1.2f, pz = bz + fwd[2] * (1.5f - i) * s * 1.2f;
                float half = 8f * s;
                float[] cen = {px + dir[0] * half, py + dir[1] * half, pz + dir[2] * half};
                parts.add(new Object[]{cen, dir, half, 1f * s, 1f * s, leg});
            }
        }
        float[] abd = {bx - fwd[0] * 6f * s, by - fwd[1] * 6f * s + 1f * s, bz - fwd[2] * 6f * s};
        float[] neck = {bx, by, bz};
        float[] head = {bx + fwd[0] * 5f * s, by + fwd[1] * 5f * s + 3f * rear, bz + fwd[2] * 5f * s};
        parts.add(new Object[]{abd, null, 5f * s, 4f * s, 6f * s, uv(0, 12, 10, 8, 12)});
        parts.add(new Object[]{neck, null, 3f * s, 3f * s, 3f * s, uv(0, 0, 6, 6, 6)});
        parts.add(new Object[]{head, null, 4f * s, 4f * s, 4f * s, uv(32, 4, 8, 8, 8)});
        // No depth buffer: every part back to front.
        parts.sort((p, q) -> Float.compare(ChopScene.depth(((float[]) p[0])[0], ((float[]) p[0])[1], ((float[]) p[0])[2]),
                ChopScene.depth(((float[]) q[0])[0], ((float[]) q[0])[1], ((float[]) q[0])[2])));
        float[] bodyX = right, bodyY = up, bodyZ = fwd;
        for (int pass = 0; pass < 2; pass++) {
            for (Object[] p : parts) {
                float[] cen = (float[]) p[0];
                float[] dir = (float[]) p[1];
                float[] X, Y, Z;
                if (dir == null) {
                    X = bodyX;
                    Y = bodyY;
                    Z = bodyZ;
                } else {
                    X = dir;
                    Z = cross(X, up);
                    normalize(Z);
                    Y = cross(Z, X);
                    normalize(Y);
                }
                float hx = (float) p[2], hy = (float) p[3], hz = (float) p[4];
                int[][] rects = (int[][]) p[5];
                if (pass == 0) {
                    ChopScene.inkWidth = ink();
                    ChopScene.boxAxes(cen[0], cen[1], cen[2], X, Y, Z, hx * 1.02f, hy * 1.02f, hz * 1.02f, six(SPIDER), rects, 1f, 1f, 1f, 0f, 1f, c);
                    ChopScene.inkWidth = 0f;
                } else {
                    ChopScene.boxAxes(cen[0], cen[1], cen[2], X, Y, Z, hx, hy, hz, six(SPIDER), rects, 0.82f, 0.82f, 0.82f, 0f, 1f, c);
                }
            }
        }
        if (eyes > 0.02f) {
            ChopScene.boxAxes(head[0], head[1], head[2], bodyX, bodyY, bodyZ, 4.05f * s, 4.05f * s, 4.05f * s, six(SPIDER_EYES),
                    uv(32, 4, 8, 8, 8), 1f, 1f, 1f, 1f, eyes, c);
        }
    }

    /** The six faces of a Minecraft model box at texture offset (u, v), size w x h x d, in ChopScene's face order. */
    static int[][] uv(int u, int v, int w, int h, int d) {
        return new int[][]{
                {u + d + w, v + d, d, h},
                {u, v + d, d, h},
                {u + d, v, w, d},
                {u + d + w, v, w, d},
                {u + d, v + d, w, h},
                {u + d + w + d, v + d, w, h}};
    }

    static float[] cross(float[] p, float[] q) {
        return new float[]{p[1] * q[2] - p[2] * q[1], p[2] * q[0] - p[0] * q[2], p[0] * q[1] - p[1] * q[0]};
    }

    static void normalize(float[] v) {
        float l = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (l < 1e-6f) return;
        v[0] /= l;
        v[1] /= l;
        v[2] /= l;
    }

    // ================================================================== the showcase

    /**
     * The drop up in the middle, as every other cutscene shows one: its light behind it, the item three-quarters on with
     * a slow float and a little sway (no spin - it turned up back first and swung round, the user), the banner stamped
     * over it, the name, odds and price under it.
     */
    static void showcase(ChopScene.Canvas c, SlayerTimeline tl, Show show, long t, float fromX, float fromY, int w, int h, int rgb) {
        float rise = tl.rise(t);
        if (rise <= 0f) return;
        float x = fromX + (w / 2f - fromX) * rise;
        float y = fromY + (h * 0.42f - fromY) * rise;
        float in = tl.intensity();
        // It pops to its size as it arrives, a touch past it and back.
        float pop = backOut(clamp01((t - tl.arrive() + 450) / 650f));
        float size = h * (0.17f + 0.05f * in) * (0.35f + 0.65f * pop);
        float bob = size * 0.05f * (float) Math.sin(t / 430.0) * rise;
        float yaw = 32f + 7f * (float) Math.sin(t / 900.0);
        hype(c, tl, t, x, y, w, h, rgb, tl.arrive() - 150);
        ChopScene.glow(c, x, y, size * (2.1f + 0.6f * in), rgb, (0.35f + 0.2f * in) * rise);
        ChopScene.showItem(c, x, y + bob, size, ChopScene.sceneAlpha * Math.min(1f, rise * 2f), yaw);
        float ts = Math.max(1f, Math.min(1.35f, h * 0.7f / 150f));
        float ba = tl.bannerAlpha(t) * ChopScene.sceneAlpha;
        if (show.banner != null && ba > 0.02f) {
            // Stamped, as Hellion's: in big, down to its size, a flash of its colour as it lands.
            float k = clamp01((t - tl.arrive() + 150) / 260f);
            float scale = ts * (1.3f + 1.2f * (1f - ease(k)));
            float by = y - size * 0.7f - 12f * ts;
            if (k < 1f) ChopScene.glow(c, x, by, 120f * ts, show.bannerColour, 0.5f * (1f - k));
            c.text("§l" + show.banner, x, by, scale, ChopScene.alpha(Math.min(ba, k * 2.5f)) | show.bannerColour);
        }
        float below = y + size * 0.62f;
        float na = tl.nameAlpha(t) * ChopScene.sceneAlpha;
        if (na > 0.02f) c.name(x, below + 8f * ts, 1.6f * ts, ChopScene.alpha(na) | tl.drop().colour, tl.letters(t));
        float la = tl.lineAlpha(t) * ChopScene.sceneAlpha;
        if (la > 0.02f) {
            if (show.line != null) c.text(show.line, x, below + 22f * ts, ts, ChopScene.alpha(la) | 0xC8C8C8);
            long price = tl.price();
            if (price > 0 && t >= tl.countStart()) {
                float k = clamp01((t - tl.countStart()) / (float) SlayerTimeline.COUNT_MS);
                long shown = Math.round(price * (1f - (1f - k) * (1f - k) * (1f - k)));
                c.text(String.format("%,d coins", shown), x, below + 35f * ts, 1.25f * ts, ChopScene.alpha(la) | 0xFFD84A);
                if (tl.priceSource() != null) c.text(tl.priceSource(), x, below + 46f * ts, 0.75f * ts, ChopScene.alpha(la * 0.7f) | 0x9A9A9A);
            }
        }
    }

    /**
     * The showcase's light, as Diana's and the slayers' before it: rays in the drop's own colour, one ring as it lands
     * for the big ones, a few sparkles, motes rising. No rainbow and no confetti - the user found the dyes' too much, and
     * the mod should feel the same throughout.
     */
    static void hype(ChopScene.Canvas c, SlayerTimeline tl, long t, float x, float y, int w, int h, int rgb, long from) {
        long s = t - from;
        if (s < 0) return;
        float i = tl.intensity();
        float on = ease(s / 400f) * tl.fadeOut(t);
        long seed = tl.seed();
        int colA = rgb, colB = ChopScene.mix(rgb, 0xFFFFFF, 0.45f);
        SlayerFx.rays(c, x, y, h, Math.round(t * 0.7f), on * (0.45f + 0.3f * i), colA, colB, 8, 0.26f + 0.08f * i);
        if (i >= 0.55f && s < 900) {
            float k = s / 900f;
            float dd = h * (0.15f + 0.7f * ease(k));
            SlayerFx.ring(c, x, y, dd, dd * 0.8f, a((1f - k) * (1f - k) * 0.5f) | colA);
        }
        // Motes of its colour rising past it.
        for (int k = 0; k < 10 + Math.round(10 * i); k++) {
            float life = 1600f + 900f * rnd(seed, k, 71);
            float ph = ((s + rnd(seed, k, 72) * life) % life) / life;
            float mx = x + (rnd(seed, k, 73) - 0.5f) * h * 0.6f, my = y + h * 0.25f - ph * h * 0.5f;
            square(c, mx, my, 1, a(on * 0.7f * (float) Math.sin(Math.PI * ph)) | (k % 3 == 0 ? 0xFFFFFF : colA));
        }
        int stars = Math.round(3 + 6 * i);
        for (int k = 0; k < stars; k++) {
            double ang = rnd(seed, k, 61) * Math.PI * 2;
            float dist = h * (0.12f + (0.1f + 0.18f * i) * rnd(seed, k, 62));
            float period = 600f + 700f * rnd(seed, k, 63);
            float ph = ((s + rnd(seed, k, 64) * period) % period) / period;
            float al = on * (float) Math.sin(Math.PI * ph);
            if (al < 0.05f) continue;
            float sx = x + (float) Math.cos(ang) * dist, sy = y + (float) Math.sin(ang) * dist * 0.8f;
            int gx = SlayerFx.g(sx), gy = SlayerFx.g(sy);
            SlayerFx.cells(c, gx, gy, 1, 1, a(al) | 0xFFFFFF);
            if (ph > 0.3f && ph < 0.7f) {
                int col = a(al * 0.8f) | (k % 3 == 0 ? 0xFFFFFF : colA);
                SlayerFx.cells(c, gx - 1, gy, 1, 1, col);
                SlayerFx.cells(c, gx + 1, gy, 1, 1, col);
                SlayerFx.cells(c, gx, gy - 1, 1, 1, col);
                SlayerFx.cells(c, gx, gy + 1, 1, 1, col);
            }
        }
    }
}
