package com.butcher.render;

import com.butcher.anim.GauntletTimeline;
import com.butcher.anim.GauntletTimeline.Ending;
import com.butcher.anim.NucleusHaul;
import com.butcher.anim.NucleusTimeline.Crystal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws "The Gauntlet" ({@link GauntletTimeline}) - the same code in game (gui/NucleusScreen) and offline
 * (tools/NucleusPreview with {@code -Dstyle=gauntlet}). The gauntlet is {@code assets/butcher/nucleus_gauntlet.json} (built
 * by tools/blender/build_gauntlet.py), rasterised every frame with its stones and anything flying ({@link ModelRaster}:
 * its fingers move, so there is nothing to cache); the centre socket's item is its own raster over it. Effects are grid
 * cells (batched by the screen). After the snap the world is the frozen frame ({@link #world}) drawn as cells, which
 * blow away as dust, and then the gauntlet's own pixels go the same way.
 */
public final class GauntletScene {
    private GauntletScene() {}

    static final NucleusModel.Model MODEL = NucleusModel.load("/assets/butcher/nucleus_gauntlet.json", 0f);
    private static final ModelRaster HAND = new ModelRaster(), CENTRE = new ModelRaster();
    private static boolean textured;
    private static final boolean[] skinned = new boolean[Crystal.values().length];
    /** The slot a stone without its skin yet is drawn from: a plain white texture, tinted. */
    private static final int WHITE_SLOT = 6;

    /** The world as it was at the snap (ARGB, row by row, {@link #worldW} x {@link #worldH}); null until captured. */
    public static volatile int[] world;
    public static volatile int worldW, worldH;

    static {
        CENTRE.texture(1, NucleusScene.ATLAS, NucleusScene.AW, NucleusScene.AH);
        HAND.texture(WHITE_SLOT, new int[]{-1, -1, -1, -1}, 2, 2);
    }

    /** The gauntlet's block textures, by name (the game's; the preview's from files). */
    public static void textures(java.util.function.Function<String, int[]> pixels) {
        HAND.texture(0, MODEL.atlas(pixels), MODEL.atlasW(), MODEL.atlasH());
        textured = true;
    }

    public static boolean hasTextures() {
        return textured;
    }

    /** The texture names the model asks for, so the screen knows what to read. */
    public static List<String> textureNames() {
        return new java.util.ArrayList<>(MODEL.textures);
    }

    private static void skins() {
        Crystal[] all = Crystal.values();
        for (int k = 0; k < all.length; k++) {
            if (skinned[k]) continue;
            java.awt.image.BufferedImage img = NucleusScene.skins.apply(all[k].name() + "_CRYSTAL");
            if (img == null) continue;
            HAND.texture(1 + k, img);
            skinned[k] = true;
        }
    }

    // ------------------------------------------------------------------ the camera and the pose

    /** The stones' sockets in order: the thumb, then index, middle, ring, pinky. */
    static final String[] SOCKETS = {"SockThumb", "SockIndex", "SockMiddle", "SockRing", "SockPinky"};
    static final String[] FINGERS = {"Thumb", "Index", "Middle", "Ring", "Pinky"};
    /** How far each joint of a finger turns in a full fist (radians): the knuckle, the middle, the tip. */
    static final float[] JOINT = {1.5f, 1.6f, 1.3f};
    static final float STONE = 2.6f, CENTRE_HALF = 6.2f, SHOW_HALF = 11f;
    /** The model's y that sits at the screen's middle; the camera's pitch. */
    static final float MODEL_MID = 7f, PITCH = -7f;
    /** Where the drop comes to rest in front of it all (model pixels). */
    static final float[] SHOW_AT = {0f, MODEL_MID + 8f, 34f};

    static void camera(int w, int h, float zoom, float dx, float dy) {
        double pitch = Math.toRadians(PITCH);
        ChopScene.cy = 1f;
        ChopScene.sy = 0f;
        ChopScene.cp = (float) Math.cos(pitch);
        ChopScene.sp = (float) Math.sin(pitch);
        ChopScene.size = h / 132f * zoom;
        ChopScene.fx = 0f;
        ChopScene.fy = MODEL_MID;
        ChopScene.fz = 0f;
        ChopScene.sx0 = w / 2f + dx;
        ChopScene.sy0 = h * 0.5f + dy;
    }

    /**
     * Every part's pose: the whole hand's (risen, sunk, leant over and tipped back about its middle, then turned about
     * its own long axis) times its joints' curls.
     */
    private static Map<String, float[]> poses(GauntletTimeline tl, long t, float lift) {
        float[] root = NucleusModel.moved(NucleusModel.identity(1f, 0f, 0f, 0f), 0f, lift + MODEL_MID, 0f);
        root = NucleusModel.spun(root, 0f, 0f, GauntletTimeline.LEAN);
        root = NucleusModel.tilted(root, GauntletTimeline.TIP);
        root = NucleusModel.moved(root, 0f, -MODEL_MID, 0f);
        root = NucleusModel.yawed(root, tl.yaw(t));
        float[] curl = tl.curl(t);
        Map<String, float[]> out = new HashMap<>();
        for (NucleusModel.Part p : MODEL.parts.values()) pose(p, out, root, curl);
        return out;
    }

    private static float[] pose(NucleusModel.Part p, Map<String, float[]> out, float[] root, float[] curl) {
        float[] done = out.get(p.name());
        if (done != null) return done;
        NucleusModel.Part parent = p.parent() == null ? null : MODEL.parts.get(p.parent());
        float[] m = parent == null ? root : pose(parent, out, root, curl);
        String n = p.name();
        float[] pv = p.pivot();
        int finger = -1;
        for (int i = 0; i < FINGERS.length; i++) if (n.startsWith(FINGERS[i])) finger = i;
        int seg = finger >= 0 && Character.isDigit(n.charAt(n.length() - 1)) ? n.charAt(n.length() - 1) - '0' : -1;
        if (finger >= 1 && seg >= 0) {
            // a finger's joint: about x through its knuckle, towards the palm (-z)
            m = NucleusModel.moved(m, pv[0], pv[1], pv[2]);
            m = NucleusModel.tilted(m, -curl[finger] * JOINT[Math.min(seg, 2)]);
            m = NucleusModel.moved(m, -pv[0], -pv[1], -pv[2]);
        } else if (finger == 0 && seg == 0) {
            // the thumb: straightens up and swings across towards the palm
            m = NucleusModel.moved(m, pv[0], pv[1], pv[2]);
            m = NucleusModel.spun(m, 0f, 0f, 0.8f * curl[0]);
            m = NucleusModel.yawed(m, 0.9f * curl[0]);
            m = NucleusModel.moved(m, -pv[0], -pv[1], -pv[2]);
        } else if (finger == 0 && seg == 1) {
            m = NucleusModel.moved(m, pv[0], pv[1], pv[2]);
            m = NucleusModel.spun(m, 0f, 0f, 0.7f * curl[0]);
            m = NucleusModel.moved(m, -pv[0], -pv[1], -pv[2]);
        }
        out.put(n, m);
        return m;
    }

    /** A stone in socket {@code name}: at its middle, its point (the skin's top) along the way the socket faces. */
    private static float[] stoneMatrix(Map<String, float[]> poses, String name) {
        NucleusModel.Part s = MODEL.parts.get(name);
        if (s == null) return NucleusModel.identity(1f, 0f, 0f, 0f);
        float[] parent = poses.get(s.parent());
        float[] pv = s.pivot(), f = s.slide();
        float[] m = NucleusModel.moved(parent, pv[0], pv[1], pv[2]);
        if (f != null && Math.abs(f[2]) < 0.5f) return NucleusModel.spun(m, 0f, 0f, (float) Math.atan2(-f[0], f[1]));
        return NucleusModel.tilted(m, (float) (Math.PI / 2));
    }

    private static float[] origin(float[] m) {
        return new float[]{m[9], m[10], m[11]};
    }

    /** Where a crystal flies in from, off the screen's edge (model pixels): each from its own side. */
    private static float[] start(int k) {
        double a = Math.toRadians(new int[]{-30, 150, 60, 120, 30}[k]);
        return new float[]{150f * (float) Math.cos(a), MODEL_MID + 95f * (float) Math.sin(a), 60f};
    }

    // ------------------------------------------------------------------ the frame

    public static void draw(ChopScene.Canvas c, GauntletTimeline tl, long t, int w, int h) {
        float master = tl.master(t);
        if (master <= 0.01f || !textured) return;
        ChopScene.sceneAlpha = master;
        SlayerFx.U = SlayerFx.gridFor(h);
        skins();
        warm(tl);
        long snap = tl.snapAt();
        boolean snapped = t >= snap;
        // The dim - or, once it has snapped, the void the world crumbles into.
        if (!snapped) c.fill(0, 0, w, h, ChopScene.alpha(0.82f * master * ChopScene.dimScale) | 0x07060A);
        else c.fill(0, 0, w, h, ChopScene.alpha(master) | 0x040307);

        // The camera: in close for the build, a little closer through the charge, a kick on each beat; shake.
        float zoom = 1f + 0.05f * GauntletTimeline.hang(t) + 0.14f * tl.charge(t);
        float shakeX = 0f, shakeY = 0f;
        for (int k = 0; k < 5; k++) {
            long d = t - GauntletTimeline.lock(k);
            if (d >= 0 && d < 260) {
                shakeX += (1f - d / 260f) * 2f * (float) Math.sin(d / 17.0);
                zoom += 0.025f * (1f - d / 260f);
            }
        }
        long land = t - GauntletTimeline.REVEAL;
        if (land >= 0 && land < 420) {
            shakeY += (1f - land / 420f) * 3f * (float) Math.sin(land / 19.0);
            zoom += 0.05f * (1f - land / 420f);
        }
        float ch = tl.charge(t), still = 1f - tl.hush(t);
        shakeX += ch * ch * 3.2f * still * (float) Math.sin(t / 13.0);
        shakeY += ch * ch * 2.4f * still * (float) Math.sin(t / 17.0 + 0.5);
        // The snap punches the camera in, and it settles back.
        zoom += 0.13f * tl.punch(t) + 0.04f * tl.hush(t);
        long ss = tl.shutSince(t);
        if (ss >= 0 && ss < 420) shakeX += (1f - ss / 420f) * 4f * (float) Math.sin(ss / 14.0);
        long sd = t - snap;
        if (sd >= 0 && sd < 600) {
            shakeX += (1f - sd / 600f) * 6f * (float) Math.sin(sd / 11.0);
            shakeY += (1f - sd / 600f) * 4f * (float) Math.sin(sd / 15.0);
        }
        if (ChopScene.calm) shakeX = shakeY = 0f;
        float rise = 1f - GauntletTimeline.clamp(t / (float) GauntletTimeline.RISEN);
        float lift = -rise * rise * 120f - tl.sink(t) * 150f + 1.2f * (float) Math.sin(t / 700.0);
        camera(w, h, zoom, shakeX * ChopScene.shakeK, shakeY * ChopScene.shakeK);

        Map<String, float[]> poses = poses(tl, t, lift);
        float[] fist = at(origin(stoneMatrix(poses, "SockCentre")));
        if (snapped) {
            worldDust(c, tl, t, w, h, fist);
            if (tl.storm()) shockwave(c, tl, t, w, h, fist);
            else cracks(c, tl, t, w, h, fist);
        }
        backGlow(c, tl, t, poses);
        hand(c, tl, t, w, h, poses, master);
        stoneGlows(c, tl, t, poses);
        // The drop's light goes behind it (squares drawn after an image land over it).
        showLight(c, tl, t, poses);
        centre(c, tl, t, w, h, poses, master);
        front(c, tl, t, w, h, poses);
        // The flashbang - for the Jade Storm it fades out through jade.
        float fl = tl.flash(t);
        if (fl > 0.003f) c.fill(0, 0, w, h, ChopScene.alpha(fl) | (tl.storm() ? ChopScene.mix(0xFFFFFF, 0x9CFFD0, 1f - fl) : 0xFFFFFF));
        words(c, tl, t, h);
        ChopScene.sceneAlpha = 1f;
    }

    /** Copies one more needed item's skin into the item atlas each frame: the cycle's, then the drop's. */
    private static void warm(GauntletTimeline tl) {
        for (String name : GauntletTimeline.CYCLE) {
            NucleusHaul.Item it = NucleusHaul.item(name);
            if (it != null && !NucleusScene.CELLS.containsKey(it.id())) {
                NucleusScene.cellOf(it.id());
                return;
            }
        }
        if (tl.show() != null && tl.show().id() != null && !NucleusScene.CELLS.containsKey(tl.show().id())) NucleusScene.cellOf(tl.show().id());
    }

    // ------------------------------------------------------------------ the gauntlet and its stones

    private static void hand(ChopScene.Canvas c, GauntletTimeline tl, long t, int w, int h, Map<String, float[]> poses, float master) {
        NucleusModel.Cam cam = NucleusModel.Cam.current();
        float[] acc = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (NucleusModel.Box b : MODEL.boxes) NucleusModel.bounds(b, poses.get(b.part()), acc, cam);
        // The stones: set, flying in, or cracked out and falling.
        Crystal[] order = new Crystal[5];
        float[][] mats = new float[5][];
        float[] halves = new float[5];
        for (int k = 0; k < 5; k++) {
            order[k] = tl.crystal(k);
            float u = GauntletTimeline.flight(k, t);
            if (u < 0f) continue;
            float[] seat = stoneMatrix(poses, SOCKETS[k]);
            long crack = tl.crackAt(k);
            if (t >= crack) {
                float s = (t - crack) / 1000f;
                float[] o = origin(seat), n = facing(poses, SOCKETS[k]);
                float vx = n[0] * 45f + (SlayerFx.rnd(41, k, 1) - 0.5f) * 50f, vy = 55f + 30f * SlayerFx.rnd(41, k, 2), vz = n[2] * 45f + 25f;
                float y = o[1] + vy * s - 0.5f * 340f * s * s;
                if (y < MODEL_MID - 160f) continue;
                float[] m = NucleusModel.moved(NucleusModel.identity(1f, 0f, 0f, 0f), o[0] + vx * s, y, o[2] + vz * s);
                m = NucleusModel.spun(m, 0f, 0f, s * 9f * (k % 2 == 0 ? 1 : -1));
                m = NucleusModel.hinged(m, 0f, 0f, s * 6f);
                mats[k] = m;
                halves[k] = STONE;
            } else if (u >= 1f) {
                mats[k] = seat;
                halves[k] = STONE;
            } else {
                float[] a = start(k), b = origin(seat);
                float arc = 30f * (float) Math.sin(Math.PI * u);
                float[] m = seat.clone();
                m[9] = a[0] + (b[0] - a[0]) * u;
                m[10] = a[1] + (b[1] - a[1]) * u + arc;
                m[11] = a[2] + (b[2] - a[2]) * u;
                m = NucleusModel.spun(m, 0f, 0f, (1f - u) * 7f);
                m = NucleusModel.hinged(m, 0f, 0f, (1f - u) * 5f);
                mats[k] = m;
                halves[k] = STONE * (1f + 1.6f * (1f - u));
            }
            NucleusModel.headBounds(mats[k], halves[k] * 1.2f, acc, cam);
        }
        float inkW = Math.max(1.2f, ChopScene.size / 1.6f), pad = inkW + 2f;
        if (!HAND.begin(Math.max(0f, acc[0] - pad), Math.max(0f, acc[1] - pad), Math.min(w, acc[2] + pad), Math.min(h, acc[3] + pad), 1)) return;
        // The Jade Storm turns the gauntlet to jade crystal with the world, a glint running over it.
        int jade = 0;
        if (tl.storm() && t >= tl.snapAt()) {
            float glint = 0.5f + 0.5f * (float) Math.sin((t - tl.snapAt()) / 140.0);
            jade = Math.round(0xC0 + 0x28 * glint) << 24 | 0x008C58;
        }
        for (NucleusModel.Box b : MODEL.boxes) NucleusModel.box(HAND, b, poses.get(b.part()), jade, cam, MODEL);
        float ch = tl.charge(t);
        for (int k = 0; k < 5; k++) {
            if (mats[k] == null) continue;
            Crystal cr = order[k];
            float breathe = 0.5f + 0.5f * (float) Math.sin(t / 300.0 + k);
            int add = Math.round(Math.min(255f, 0x30 + 0x20 * breathe + 0xA0 * ch)) << 24 | cr.colour;
            if (skinned[cr.ordinal()]) NucleusModel.head(HAND, 1 + cr.ordinal(), mats[k], halves[k], add, cam);
            else NucleusModel.solid(HAND, WHITE_SLOT, mats[k], -halves[k], halves[k], -halves[k], halves[k], -halves[k], halves[k], cr.colour, add, cam);
        }
        HAND.ink(Math.max(1, Math.round(inkW * HAND.scale)));
        float dust = tl.gauntletDust(t);
        if (dust > 1.4f) return;
        if (dust >= 0f) dissolve(c, dust, t, tl.storm());
        HAND.fade(master);
        c.image("gauntlet_hand", HAND.px, HAND.w, HAND.h, HAND.x, HAND.y, HAND.w / HAND.scale, HAND.h / HAND.scale);
    }

    /** The way a socket's stone faces, in the world (the hand turned). */
    private static float[] facing(Map<String, float[]> poses, String name) {
        NucleusModel.Part s = MODEL.parts.get(name);
        float[] m = poses.get(s.parent());
        float[] f = s.slide() == null ? new float[]{0f, 0f, 1f} : s.slide();
        return new float[]{m[0] * f[0] + m[3] * f[1] + m[6] * f[2], m[1] * f[0] + m[4] * f[1] + m[7] * f[2], m[2] * f[0] + m[5] * f[1] + m[8] * f[2]};
    }

    /**
     * The gauntlet going to dust, fingertips first: a block of its pixels at a time, each gone at its turn (a front coming
     * down the image, ragged), drifting off up and to the right as a grid cell of its own colour, fading.
     */
    private static void dissolve(ChopScene.Canvas c, float dust, long t, boolean jade) {
        int[] px = HAND.px;
        float sc = HAND.scale;
        int bs = Math.max(1, Math.round(SlayerFx.U * sc));
        int bw = (HAND.w + bs - 1) / bs, bh = (HAND.h + bs - 1) / bs;
        for (int by = 0; by < bh; by++) {
            for (int bx = 0; bx < bw; bx++) {
                float when = (by / (float) Math.max(1, bh - 1)) * 0.72f + SlayerFx.rnd(77, by * 977 + bx, 1) * 0.28f;
                float p = (dust - when) / 0.28f;
                if (p < 0f) continue;
                int cx = Math.min(HAND.w - 1, bx * bs + bs / 2), cy = Math.min(HAND.h - 1, by * bs + bs / 2);
                int col = px[cy * HAND.w + cx];
                for (int y = by * bs; y < Math.min(HAND.h, by * bs + bs); y++) {
                    java.util.Arrays.fill(px, y * HAND.w + bx * bs, Math.min((y + 1) * HAND.w, y * HAND.w + bx * bs + bs), 0);
                }
                if ((col >>> 24) < 40 || p >= 1f) continue;
                float gx = HAND.x + (bx * bs + bs / 2f) / sc, gy = HAND.y + (by * bs + bs / 2f) / sc;
                float r = SlayerFx.rnd(78, by * 977 + bx, 1);
                gx += p * (30f + 60f * r) * ChopScene.size / 4f + 6f * (float) Math.sin(p * 6 + bx);
                gy -= p * (18f + 30f * SlayerFx.rnd(78, by * 977 + bx, 2)) * ChopScene.size / 4f;
                int mote = jade ? ChopScene.mix(col & 0xFFFFFF, 0x9CFFD0, 0.35f + 0.4f * p) : ChopScene.mix(col & 0xFFFFFF, 0xFFD25A, 0.3f * p);
                SlayerFx.square(c, gx, gy, 1, SlayerFx.a((1f - p) * (col >>> 24) / 255f) | mote);
            }
        }
    }

    /**
     * The world as it was, in grid cells. The Alloy's: blowing away left to right in drifting dust until only the void is
     * left. The Jade Storm's: turned to jade crystal behind the shockwave - its brightness mapped onto jade, cut into
     * facets with pale edges, a glint sweeping over it - then shattered outward from the fist a facet at a time.
     */
    private static void worldDust(ChopScene.Canvas c, GauntletTimeline tl, long t, int w, int h, float[] fist) {
        int[] img = world;
        int iw = worldW, ih = worldH;
        if (img == null || iw <= 0 || ih <= 0) return;
        float u = Math.max(0f, tl.worldDust(t));
        if (u > 1.4f) return;
        float cell = SlayerFx.U;
        int cols = (int) Math.ceil(w / cell), rows = (int) Math.ceil(h / cell);
        // What still stands is one image, a texel a cell (25,000 squares a frame otherwise); the motes are squares.
        if (WORLD.length < cols * rows) WORLD = new int[cols * rows];
        int[] still = WORLD;
        int alpha = Math.round(255f * ChopScene.sceneAlpha) << 24;
        boolean storm = tl.storm();
        float shock = tl.shock(t), diag = (float) Math.hypot(w, h);
        if (storm) facets(cols, rows, fist[0] / cell, fist[1] / cell);
        long since = t - tl.snapAt();
        float sweep = (since - 600) / 900f;   // the glint's band, across the screen corner to corner
        for (int gy = 0; gy < rows; gy++) {
            int sy = Math.min(ih - 1, (int) ((gy + 0.5f) * cell * ih / h));
            for (int gx = 0; gx < cols; gx++) {
                int i = gy * cols + gx;
                int sx = Math.min(iw - 1, (int) ((gx + 0.5f) * cell * iw / w));
                int col = img[sy * iw + sx] & 0xFFFFFF;
                float when, p;
                if (storm) {
                    // Crystal once the wave has passed it.
                    float dx = (gx + 0.5f) * cell - fist[0], dy = (gy + 0.5f) * cell - fist[1];
                    float r = (float) Math.sqrt(dx * dx + dy * dy) / diag;
                    if (shock >= r) col = jadeOf(col, FACET_SHADE[FACET[i]], FACET_EDGE[i],
                            Math.abs((gx + gy) / (float) (cols + rows) - sweep) < 0.018f);
                    else if (shock >= r - 0.03f) col = 0xEFFFF6;
                    when = FACET_WHEN[FACET[i]];
                    p = (u - when) / 0.35f;
                } else {
                    when = (gx / (float) Math.max(1, cols - 1)) * 0.7f + SlayerFx.rnd(91, gy * 1031 + gx, 1) * 0.3f;
                    p = (u - when) / 0.3f;
                }
                still[i] = p < 0f ? alpha | col : 0;
                if (p < 0f || p >= 1f) continue;
                // A shard moves whole (every cell of its facet, so it stays solid); dust is every other cell.
                if (!storm && ((gx + gy) & 1) != 0) continue;
                float r = SlayerFx.rnd(92, gy * 1031 + gx, 1);
                float x, y;
                int mote;
                if (storm) {
                    // Its facet, flung out from the fist as one shard and falling, a glint running over it.
                    int f = FACET[i];
                    float dx = FACET_CX[f] * cell - fist[0], dy = FACET_CY[f] * cell - fist[1];
                    float d = Math.max(1f, (float) Math.sqrt(dx * dx + dy * dy));
                    float fly = p * (70f + 120f * SlayerFx.rnd(503, f, 1)) * ChopScene.size / 4f;
                    x = (gx + 0.5f) * cell + dx / d * fly;
                    y = (gy + 0.5f) * cell + dy / d * fly + p * p * 110f * ChopScene.size / 4f;
                    mote = FACET_EDGE[i] || (((int) (t / 90) + f) % 7 == 0) ? ChopScene.mix(col, 0xEFFFF6, 0.6f) : col;
                    SlayerFx.square(c, x, y, 1, SlayerFx.a(Math.min(1f, (1f - p) * 2.5f)) | mote);
                    continue;
                } else {
                    // Gone: a mote of it, drifting up and away to the right, turning to ash as it fades.
                    x = (gx + 0.5f) * cell + p * (40f + 90f * r) + 8f * (float) Math.sin(p * 5 + gy);
                    y = (gy + 0.5f) * cell - p * (25f + 45f * SlayerFx.rnd(92, gy * 1031 + gx, 2));
                    mote = ChopScene.mix(col, 0x6A5E52, p);
                }
                SlayerFx.square(c, x, y, 1, SlayerFx.a(1f - p) | mote);
            }
        }
        c.image("gauntlet_world", still, cols, rows, 0f, 0f, cols * cell, rows * cell);
    }

    private static int[] WORLD = new int[0];

    /** The jade crystal: the cell's brightness on a jade ramp, its facet's shade, pale along a facet's edge or the glint. */
    private static int jadeOf(int rgb, float shade, boolean edge, boolean glint) {
        float lum = ((rgb >> 16 & 255) * 0.3f + (rgb >> 8 & 255) * 0.59f + (rgb & 255) * 0.11f) / 255f;
        float v = Math.max(0f, Math.min(1f, (0.25f + lum * 1.1f) * shade));
        int c = v < 0.6f ? ChopScene.mix(0x06281C, 0x00A86B, v / 0.6f) : ChopScene.mix(0x00A86B, 0x9CFFD0, (v - 0.6f) / 0.4f);
        if (edge) c = ChopScene.mix(c, 0xD8FFEE, 0.55f);
        if (glint) c = ChopScene.mix(c, 0xF4FFF9, 0.7f);
        return c;
    }

    /**
     * The crystal's facets, worked out once per screen size: each cell's nearest of a scatter of points (a Voronoi
     * cut), whether it sits on a facet's edge, each facet's shade, and when it shatters - nearest the fist first.
     */
    private static int[] FACET = new int[0];
    private static boolean[] FACET_EDGE = new boolean[0];
    private static final int FACETS = 70;
    private static final float[] FACET_SHADE = new float[FACETS], FACET_WHEN = new float[FACETS];
    private static int facetCols, facetRows;

    private static void facets(int cols, int rows, float fx, float fy) {
        if (cols != facetCols || rows != facetRows) {
            facetCols = cols;
            facetRows = rows;
            FACET = new int[cols * rows];
            FACET_EDGE = new boolean[cols * rows];
            float[] px = new float[FACETS], py = new float[FACETS];
            for (int k = 0; k < FACETS; k++) {
                px[k] = SlayerFx.rnd(501, k, 1) * cols;
                py[k] = SlayerFx.rnd(501, k, 2) * rows;
                FACET_SHADE[k] = 0.82f + 0.36f * SlayerFx.rnd(501, k, 3);
            }
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    int best = 0;
                    float bd = Float.MAX_VALUE;
                    for (int k = 0; k < FACETS; k++) {
                        float d = (x - px[k]) * (x - px[k]) + (y - py[k]) * (y - py[k]);
                        if (d < bd) { bd = d; best = k; }
                    }
                    FACET[y * cols + x] = best;
                }
            }
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    int f = FACET[y * cols + x];
                    FACET_EDGE[y * cols + x] = (x + 1 < cols && FACET[y * cols + x + 1] != f) || (y + 1 < rows && FACET[(y + 1) * cols + x] != f);
                }
            }
            for (int k = 0; k < FACETS; k++) {
                FACET_CX[k] = px[k];
                FACET_CY[k] = py[k];
            }
        }
        // Nearest the fist shatter first, with a little scatter.
        float far = (float) Math.hypot(cols, rows);
        for (int k = 0; k < FACETS; k++) {
            float d = (float) Math.hypot(FACET_CX[k] - fx, FACET_CY[k] - fy) / far;
            FACET_WHEN[k] = Math.min(0.62f, d * 0.6f + SlayerFx.rnd(502, k, 1) * 0.12f);
        }
    }

    private static final float[] FACET_CX = new float[FACETS], FACET_CY = new float[FACETS];

    /** The Jade Storm's shockwave: two rings of cells running out from the fist, the front white, the trail jade. */
    private static void shockwave(ChopScene.Canvas c, GauntletTimeline tl, long t, int w, int h, float[] fist) {
        float sh = tl.shock(t);
        if (sh < 0f || sh >= 1f) return;
        float diag = (float) Math.hypot(w, h);
        ring(c, fist[0], fist[1], sh * diag, SlayerFx.a(0.95f * (1f - sh * 0.6f)) | 0xEFFFF6);
        ring(c, fist[0], fist[1], Math.max(1f, sh * diag - SlayerFx.U * 2), SlayerFx.a(0.8f * (1f - sh * 0.6f)) | 0x3CE8A0);
        ring(c, fist[0], fist[1], Math.max(1f, sh * diag * 0.82f), SlayerFx.a(0.45f * (1f - sh)) | 0x00A86B);
    }

    /**
     * The Alloy's snap cracks the screen: crooked rays out from the fist with a branch or two, white-hot as they split
     * (under the flash), cooling to glowing gold seams in the world, gone with the world's dust.
     */
    private static void cracks(ChopScene.Canvas c, GauntletTimeline tl, long t, int w, int h, float[] fist) {
        long d = t - tl.snapAt();
        if (d < 0) return;
        float u = Math.max(0f, tl.worldDust(t));
        if (u >= 1f) return;
        float grow = Math.min(1f, d / 260f), cool = Math.min(1f, d / 1600f);
        float diag = (float) Math.hypot(w, h);
        int core = ChopScene.mix(0xFFF6D0, 0xFFB43C, cool), edge = ChopScene.mix(0xFFD25A, 0x5A3A08, cool);
        float fade = 1f - u;
        for (int k = 0; k < 9; k++) {
            double a = k * Math.PI * 2 / 9 + (SlayerFx.rnd(611, k, 1) - 0.5f) * 0.5f;
            float x = fist[0], y = fist[1], len = diag * (0.45f + 0.25f * SlayerFx.rnd(611, k, 2)) * grow;
            float seg = len / 6f;
            for (int sgm = 0; sgm < 6; sgm++) {
                a += (SlayerFx.rnd(612, k * 13 + sgm, 1) - 0.5f) * 0.7f;
                float nx = x + (float) Math.cos(a) * seg, ny = y + (float) Math.sin(a) * seg;
                int th = sgm < 2 ? 2 : 1;
                cellLine(c, x, y, nx, ny, th + 1, SlayerFx.a(0.7f * fade) | edge);
                cellLine(c, x, y, nx, ny, th, SlayerFx.a(0.95f * fade) | core);
                if (sgm == 2 || (sgm == 4 && SlayerFx.rnd(613, k, 1) < 0.5f)) {
                    double ba = a + (SlayerFx.rnd(613, k * 7 + sgm, 2) < 0.5f ? -0.8 : 0.8);
                    float bl = seg * 1.4f;
                    cellLine(c, nx, ny, nx + (float) Math.cos(ba) * bl, ny + (float) Math.sin(ba) * bl, 1, SlayerFx.a(0.8f * fade) | core);
                }
                x = nx;
                y = ny;
            }
        }
        // The shockwave: a gold ring and a white one racing out.
        float sw = Math.min(1f, d / 900f);
        if (sw < 1f) {
            ring(c, fist[0], fist[1], diag * 0.7f * (1f - (1f - sw) * (1f - sw)), SlayerFx.a(0.9f * (1f - sw)) | 0xFFD25A);
            float sw2 = Math.max(0f, sw - 0.12f);
            if (sw2 > 0f) ring(c, fist[0], fist[1], diag * 0.7f * (1f - (1f - sw2) * (1f - sw2)), SlayerFx.a(0.7f * (1f - sw)) | 0xFFFFFF);
        }
        // Gold embers rising off the fist while the world goes.
        for (int i = 0; i < 26 && d > 300; i++) {
            float life = ((d - 300) / 1000f + SlayerFx.rnd(614, i, 1)) % 1f;
            float ex = fist[0] + (SlayerFx.rnd(614, i, 2) - 0.5f) * ChopScene.size * 70f + 6f * (float) Math.sin(life * 7 + i);
            float ey = fist[1] + ChopScene.size * 20f - life * ChopScene.size * 90f;
            SlayerFx.square(c, ex, ey, 1, SlayerFx.a((1f - life) * 0.9f * fade) | (i % 3 == 0 ? 0xFFF6D0 : 0xFFB43C));
        }
    }

    /** A line of grid cells between two screen points, {@code th} cells thick. */
    private static void cellLine(ChopScene.Canvas c, float x0, float y0, float x1, float y1, int th, int argb) {
        float len = (float) Math.hypot(x1 - x0, y1 - y0);
        int n = Math.max(1, Math.round(len / SlayerFx.U));
        for (int i = 0; i <= n; i++) {
            float f = i / (float) n;
            SlayerFx.square(c, x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, th, argb);
        }
    }

    // ------------------------------------------------------------------ light and effects

    /** Behind the gauntlet: a glow off each set stone; the centre's in the cycling item's colour. */
    private static void backGlow(ChopScene.Canvas c, GauntletTimeline tl, long t, Map<String, float[]> poses) {
        if (t >= tl.snapAt()) return;
        int ci = GauntletTimeline.cycleIndex(t);
        NucleusHaul.Item it = ci >= 0 ? NucleusHaul.item(GauntletTimeline.CYCLE[ci]) : t >= GauntletTimeline.REVEAL ? tl.show() : null;
        if (it != null && tl.rise(t) < 0f) {
            float[] p = at(origin(stoneMatrix(poses, "SockCentre")));
            float pulse = ci >= 0 ? 1f - Math.min(1f, (t - GauntletTimeline.step(ci)) / 200f) : 0f;
            NucleusScene.glow(c, p[0], p[1], ChopScene.size * (16f + 10f * pulse), colourOf(it), 0.45f + 0.35f * pulse);
        }
    }

    private static void stoneGlows(ChopScene.Canvas c, GauntletTimeline tl, long t, Map<String, float[]> poses) {
        float ch = tl.charge(t);
        for (int k = 0; k < 5; k++) {
            long lock = GauntletTimeline.lock(k);
            if (t < lock || t >= tl.crackAt(k) || t - tl.snapAt() >= 200) continue;
            Crystal cr = tl.crystal(k);
            float[] p = at(origin(stoneMatrix(poses, SOCKETS[k])));
            float breathe = 0.5f + 0.5f * (float) Math.sin(t / 300.0 + k);
            NucleusScene.glow(c, p[0], p[1], ChopScene.size * (5f + 3f * breathe + 9f * ch), cr.colour, 0.35f + 0.2f * breathe + 0.45f * ch);
            long d = t - lock;
            if (d < 420) {
                // the lock: a flash, two rings out across the hand, sparks
                float u = d / 420f;
                if (d < 160) NucleusScene.glow(c, p[0], p[1], ChopScene.size * 12f, 0xFFFFFF, 1f - d / 160f);
                ring(c, p[0], p[1], ChopScene.size * (3f + 22f * u), SlayerFx.a(0.85f * (1f - u)) | ChopScene.mix(cr.colour, 0xFFFFFF, 0.4f));
                ring(c, p[0], p[1], ChopScene.size * (2f + 12f * u), SlayerFx.a(0.6f * (1f - u)) | cr.colour);
                SlayerFx.burst(c, p[0], p[1], d, 420, 12, ChopScene.size * 30f, 600L + k, new int[]{cr.colour, 0xFFFFFF}, 1f, ChopScene.size * 60f);
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

    /** In front: the flying stones' tails, the cycle's flashes, the landing, the fist's flare, the charge, cracks. */
    private static void front(ChopScene.Canvas c, GauntletTimeline tl, long t, int w, int h, Map<String, float[]> poses) {
        // Tails behind the flying stones.
        for (int k = 0; k < 5; k++) {
            float u = GauntletTimeline.flight(k, t);
            if (u < 0f || u >= 1f) continue;
            float[] a = start(k), b = origin(stoneMatrix(poses, SOCKETS[k]));
            int col = tl.crystal(k).colour;
            for (int i = 1; i <= 14; i++) {
                float uu = GauntletTimeline.flight(k, t - i * 14L);
                if (uu < 0f) break;
                float arc = 30f * (float) Math.sin(Math.PI * uu);
                float[] p = at(new float[]{a[0] + (b[0] - a[0]) * uu, a[1] + (b[1] - a[1]) * uu + arc, a[2] + (b[2] - a[2]) * uu});
                float f = 1f - i / 15f;
                SlayerFx.square(c, p[0], p[1], i < 4 ? 3 : (i < 9 ? 2 : 1), SlayerFx.a(0.85f * f) | ChopScene.mix(col, 0xFFFFFF, i < 4 ? 0.45f : 0.1f));
            }
            float arc = 30f * (float) Math.sin(Math.PI * u);
            float[] p = at(new float[]{a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u + arc, a[2] + (b[2] - a[2]) * u});
            NucleusScene.glow(c, p[0], p[1], ChopScene.size * 8f, col, 0.7f);
        }
        float[] centre = at(origin(stoneMatrix(poses, "SockCentre")));
        // Each cycle step: a ring off the centre in that item's colour.
        int ci = GauntletTimeline.cycleIndex(t);
        if (ci >= 0) {
            long d = t - GauntletTimeline.step(ci);
            NucleusHaul.Item it = NucleusHaul.item(GauntletTimeline.CYCLE[ci]);
            if (it != null && d < 260) {
                float u = d / 260f;
                ring(c, centre[0], centre[1], ChopScene.size * (8f + 14f * u), SlayerFx.a(0.8f * (1f - u)) | colourOf(it));
            }
            float hang = GauntletTimeline.hang(t);
            if (hang > 0f) SlayerFx.edgeBands(c, w, h, it == null ? 0xFFFFFF : colourOf(it), 0.16f * hang, 4);
        }
        // The landing: a flash, rings, and - for a win - rays in its colour.
        long land = t - GauntletTimeline.REVEAL;
        NucleusHaul.Item show = tl.show();
        int col = show == null ? 0xFFFFFF : colourOf(show);
        boolean plain = tl.ending() == Ending.FALL;
        if (land >= 0 && land < 700 && tl.rise(t) < 0f) {
            float u = land / 700f;
            if (land < 220) NucleusScene.glow(c, centre[0], centre[1], ChopScene.size * (plain ? 16f : 26f), plain ? 0xFFF4D0 : col, 1f - land / 220f);
            for (int r = 0; r < (plain ? 1 : 3); r++) {
                float v = Math.max(0f, u - r * 0.15f);
                if (v > 0f) ring(c, centre[0], centre[1], ChopScene.size * (8f + 40f * v), SlayerFx.a(0.8f * (1f - v)) | (r == 1 ? 0xFFFFFF : col));
            }
        }
        // The fist: rays of the drop's colour off the centre stone, a shockwave as it shuts, crackle round the knuckles.
        float fl = tl.flare(t);
        if (fl > 0f) {
            rays(c, centre[0], centre[1], col, fl, t);
            NucleusScene.glow(c, centre[0], centre[1], ChopScene.size * 22f * (0.8f + 0.2f * (float) Math.sin(t / 60.0)), col, 0.75f * fl);
            long ss = tl.shutSince(t);
            for (int r = 0; r < 2 && ss >= 0; r++) {
                long e = ss - r * 120L;
                if (e < 0 || e > 600) continue;
                float u = e / 600f;
                ring(c, centre[0], centre[1], ChopScene.size * (10f + 70f * u), SlayerFx.a(0.85f * (1f - u)) | (r == 0 ? 0xFFFFFF : col));
            }
            SlayerFx.edgeBands(c, w, h, col, 0.22f * fl, 4);
        }
        // The Alloy's charge: each stone fires a beam into the centre in turn, the centre swelling with every one.
        int lit = 0;
        for (int k = 0; k < 5; k++) {
            float bm = tl.beam(k, t);
            if (bm <= 0f) continue;
            lit++;
            float[] p = at(origin(stoneMatrix(poses, SOCKETS[k])));
            int cc = tl.crystal(k).colour;
            float ex = p[0] + (centre[0] - p[0]) * bm, ey = p[1] + (centre[1] - p[1]) * bm;
            boolean flick = ((t / 40) + k) % 3 == 0;
            cellLine(c, p[0], p[1], ex, ey, 2, SlayerFx.a(flick ? 0.55f : 0.75f) | cc);
            cellLine(c, p[0], p[1], ex, ey, 1, SlayerFx.a(0.95f) | ChopScene.mix(cc, 0xFFFFFF, 0.6f));
            long fired = t - (GauntletTimeline.REVEAL + GauntletTimeline.BEAM_FROM + k * GauntletTimeline.BEAM_EVERY);
            if (fired < 260) {
                float u = fired / 260f;
                NucleusScene.glow(c, p[0], p[1], ChopScene.size * 10f, 0xFFFFFF, 1f - u);
                ring(c, centre[0], centre[1], ChopScene.size * (6f + 16f * u), SlayerFx.a(0.85f * (1f - u)) | cc);
            }
        }
        if (lit > 0) NucleusScene.glow(c, centre[0], centre[1], ChopScene.size * (12f + 4f * lit), 0xFFE68A, 0.25f + 0.12f * lit);
        // The Jade Storm's charge: jade lightning between the stones and into the centre, more of it as it builds; it
        // freezes, brightest, for the hush.
        if (tl.storm()) {
            float sc = tl.charge(t);
            if (sc > 0f) {
                long frame = tl.hush(t) > 0f ? (tl.snapAt() - GauntletTimeline.HUSH) / 45 : t / 45;
                float[][] pts = new float[6][];
                for (int k = 0; k < 5; k++) pts[k] = at(origin(stoneMatrix(poses, SOCKETS[k])));
                pts[5] = centre;
                int[][] pairs = {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {0, 5}, {2, 5}, {4, 5}, {1, 5}, {3, 5}, {0, 4}};
                for (int i = 0; i < pairs.length; i++) {
                    if (i / (float) pairs.length > sc * 1.25f) break;
                    if (SlayerFx.rnd(720, (int) frame * 17 + i, 1) < 0.25f && tl.hush(t) <= 0f) continue;
                    bolt(c, pts[pairs[i][0]], pts[pairs[i][1]], frame * 31 + i, 0.6f + 0.4f * sc);
                }
                SlayerFx.edgeBands(c, w, h, 0x00A86B, 0.3f * sc, 5);
            }
        }
        // The snap's charge: every stone's colour drawn into crackles round the hand, the edges closing in.
        float ch = tl.storm() ? 0f : tl.charge(t);
        if (tl.storm() && tl.charge(t) > 0f) SlayerFx.edgeBands(c, w, h, 0x000000, 0.4f * tl.charge(t), 5);
        if (ch > 0f) {
            for (int k = 0; k < 5; k++) {
                float[] p = at(origin(stoneMatrix(poses, SOCKETS[k])));
                int cc = tl.crystal(k).colour;
                for (int i = 0; i < 6; i++) {
                    double a = SlayerFx.rnd(700 + k, i + (int) (t / 60), 1) * Math.PI * 2;
                    float len = ChopScene.size * (4f + 10f * ch * SlayerFx.rnd(700 + k, i + (int) (t / 60), 2));
                    for (float s = 0; s < len; s += SlayerFx.U) {
                        float jit = (SlayerFx.rnd(710 + k, i * 31 + (int) s, 3) - 0.5f) * SlayerFx.U * 1.5f;
                        SlayerFx.square(c, p[0] + (float) Math.cos(a) * s + jit, p[1] + (float) Math.sin(a) * s + jit, 1,
                                SlayerFx.a(ch * (1f - s / len)) | ChopScene.mix(cc, 0xFFFFFF, 0.4f));
                    }
                }
            }
            NucleusScene.glow(c, centre[0], centre[1], ChopScene.size * 30f * ch, col, 0.6f * ch);
            SlayerFx.edgeBands(c, w, h, 0x000000, 0.4f * ch, 5);
        }
        // The hush: the edges close right in, the centre burns white.
        float hs = tl.hush(t);
        if (hs > 0f) {
            SlayerFx.edgeBands(c, w, h, 0x000000, 0.35f * hs, 9);
            NucleusScene.glow(c, centre[0], centre[1], ChopScene.size * (10f + 8f * hs), 0xFFFFFF, 0.5f * hs);
        }
        // Cracking out: a white crack and shards off each socket as its stone pops.
        for (int k = 0; k < 5; k++) {
            long d = t - tl.crackAt(k);
            if (d < 0 || d > 500) continue;
            float[] p = at(origin(stoneMatrix(poses, SOCKETS[k])));
            if (d < 120) NucleusScene.glow(c, p[0], p[1], ChopScene.size * 7f, 0xFFFFFF, 1f - d / 120f);
            SlayerFx.burst(c, p[0], p[1], d, 500, 10, ChopScene.size * 28f, 800L + k, new int[]{tl.crystal(k).colour, 0xFFFFFF, 0x8A7A55}, 1f, ChopScene.size * 120f);
        }
    }

    /** The rising drop's light: a glow and, for a win, turning rays - behind the drop, which is drawn after. */
    private static void showLight(ChopScene.Canvas c, GauntletTimeline tl, long t, Map<String, float[]> poses) {
        float rise = tl.rise(t);
        NucleusHaul.Item show = tl.show();
        if (rise < 0f || show == null) return;
        int col = colourOf(show);
        float[] s = at(showPos(tl, t, poses));
        NucleusScene.glow(c, s[0], s[1], ChopScene.size * SHOW_HALF * 2.1f, ChopScene.mix(col, 0xFFFFFF, 0.3f), 0.55f * Math.min(1f, rise * 2f));
        if (tl.ending() != Ending.FALL) rays(c, s[0], s[1], col, Math.min(1f, rise * 1.5f), t);
        // The snap's showcase: a burst as it comes out of the dust, and a ring.
        if (tl.ending() == Ending.SNAP) {
            long d = t - tl.popAt();
            if (d >= 0 && d < 700) {
                float u = d / 700f;
                ring(c, s[0], s[1], ChopScene.size * (10f + 60f * u), SlayerFx.a(0.9f * (1f - u)) | 0xFFFFFF);
                ring(c, s[0], s[1], ChopScene.size * (8f + 44f * u), SlayerFx.a(0.8f * (1f - u)) | col);
                SlayerFx.burst(c, s[0], s[1], d, 700, 24, ChopScene.size * 60f, 990L, new int[]{col, 0xFFFFFF, ChopScene.mix(col, 0xFFFFFF, 0.5f)}, 1f, ChopScene.size * 40f);
            }
        }
    }

    /** A jade lightning bolt between two screen points: a crooked line, jade round a white core, re-struck each frame step. */
    private static void bolt(ChopScene.Canvas c, float[] a, float[] b, long seed, float k) {
        int n = 7;
        float dx = b[0] - a[0], dy = b[1] - a[1], len = (float) Math.hypot(dx, dy);
        if (len < 1f) return;
        float nx = -dy / len, ny = dx / len;
        float px = a[0], py = a[1];
        for (int i = 1; i <= n; i++) {
            float f = i / (float) n;
            float off = i == n ? 0f : (SlayerFx.rnd(730, (int) (seed % 100000) * 11 + i, 1) - 0.5f) * len * 0.35f * (float) Math.sin(Math.PI * f);
            float qx = a[0] + dx * f + nx * off, qy = a[1] + dy * f + ny * off;
            cellLine(c, px, py, qx, qy, 2, SlayerFx.a(0.7f * k) | 0x00A86B);
            cellLine(c, px, py, qx, qy, 1, SlayerFx.a(0.95f * k) | 0xEFFFF6);
            px = qx;
            py = qy;
        }
    }

    /** Turning wedges of cells round a point: a win's light. */
    private static void rays(ChopScene.Canvas c, float x, float y, int col, float k, long t) {
        int n = 12;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n + t / 2400.0;
            float len = ChopScene.size * (24f + 8f * (float) Math.sin(t / 300.0 + i)) * k;
            int steps = Math.max(1, Math.round(len / SlayerFx.U));
            for (int s = 0; s < steps; s++) {
                float r = ChopScene.size * 8f + s * SlayerFx.U, f = 1f - s / (float) steps;
                SlayerFx.square(c, x + r * (float) Math.cos(a), y + r * (float) Math.sin(a), 1 + Math.round(2f * s / steps),
                        SlayerFx.a(0.42f * f * k) | ChopScene.mix(col, 0xFFFFFF, 0.3f * f));
            }
        }
    }

    // ------------------------------------------------------------------ the centre and the showcase

    /** Where the drop is now: in the centre socket, or on its way to (or at) the showcase. */
    private static float[] showPos(GauntletTimeline tl, long t, Map<String, float[]> poses) {
        float[] a = origin(stoneMatrix(poses, "SockCentre"));
        float u = tl.rise(t);
        if (u <= 0f) return a;
        float e = 1f - (1f - u) * (1f - u) * (1f - u);
        float bob = 1.3f * (float) Math.sin(t / 520.0) * e;
        return new float[]{a[0] + (SHOW_AT[0] - a[0]) * e, a[1] + (SHOW_AT[1] - a[1]) * e + bob, a[2] + (SHOW_AT[2] - a[2]) * e};
    }

    /** The centre socket's item: each of the cycle's in turn, then the drop; it rises out of it to the showcase. */
    private static void centre(ChopScene.Canvas c, GauntletTimeline tl, long t, int w, int h, Map<String, float[]> poses, float master) {
        int ci = GauntletTimeline.cycleIndex(t);
        NucleusHaul.Item it = ci >= 0 ? NucleusHaul.item(GauntletTimeline.CYCLE[ci]) : t >= GauntletTimeline.REVEAL ? tl.show() : null;
        if (it == null || it.id() == null) return;
        int[] cell = NucleusScene.cellOf(it.id());
        if (cell == null) return;
        float u = Math.max(0f, tl.rise(t));
        float e = 1f - (1f - u) * (1f - u) * (1f - u);
        float half = CENTRE_HALF + (SHOW_HALF - CENTRE_HALF) * e;
        // A step's item pops in, a little big, and settles.
        if (ci >= 0) {
            long d = t - GauntletTimeline.step(ci);
            half *= 1f + 0.18f * (float) Math.exp(-d / 60.0);
        }
        float[] p = showPos(tl, t, poses);
        float[] m = NucleusModel.moved(NucleusModel.identity(1f, 0f, 0f, 0f), p[0], p[1], p[2]);
        // In the socket it is held as the hand is (leant, tipped back) and turned three-quarters to it, so it reads as a
        // block; rising, it lets go of the hand's lean and turns slowly on its own.
        m = NucleusModel.spun(m, 0f, 0f, GauntletTimeline.LEAN * (1f - e));
        m = NucleusModel.tilted(m, GauntletTimeline.TIP * (1f - e) - 0.15f);
        m = NucleusModel.yawed(m, (tl.yaw(t) + 0.85f) * (1f - e) + (0.45f + 0.28f * (float) Math.sin(t / 900.0)) * e
                + (u > 0f ? (1f - e) * 4f : 0f));
        NucleusModel.Cam cam = NucleusModel.Cam.current();
        float[] acc = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        NucleusModel.headBounds(m, half * 1.3f, acc, cam);
        float inkW = Math.max(1.2f, ChopScene.size / 1.4f), pad = inkW + 2f;
        if (!CENTRE.begin(Math.max(0f, acc[0] - pad), Math.max(0f, acc[1] - pad), Math.min(w, acc[2] + pad), Math.min(h, acc[3] + pad), 1)) return;
        int add = t >= GauntletTimeline.REVEAL ? Math.round(0x30 * Math.max(0f, 1f - e)) << 24 | colourOf(it) : 0x24 << 24 | colourOf(it);
        NucleusScene.drawItem(CENTRE, it.id(), cell, m, half, add, cam);
        CENTRE.ink(Math.max(1, Math.round(inkW * CENTRE.scale)));
        CENTRE.fade(master);
        c.image("gauntlet_centre", CENTRE.px, CENTRE.w, CENTRE.h, CENTRE.x, CENTRE.y, CENTRE.w / CENTRE.scale, CENTRE.h / CENTRE.scale);
    }

    /** Under the showcase: the name in its rarity's colour, the price counting up, the odds and "your Nth", "+N more". */
    private static void words(ChopScene.Canvas c, GauntletTimeline tl, long t, int h) {
        NucleusHaul.Item it = tl.show();
        if (it == null || t < tl.textAt()) return;
        float[] p = at(SHOW_AT);
        float sc = Math.max(1f, Math.min(1.35f, h / 300f));
        float y = p[1] + SHOW_HALF * ChopScene.size * 1.5f + 6f * sc;
        float a0 = GauntletTimeline.clamp((t - tl.textAt()) / 260f);
        c.text(it.name() + (it.count() > 1 ? " x" + it.count() : ""), p[0], y, sc * 1.25f, SlayerFx.a(a0) | it.colour());
        float a1 = GauntletTimeline.clamp((t - tl.textAt() - 180) / 260f);
        if (a1 > 0f && tl.price() >= 0) {
            float k = GauntletTimeline.clamp((t - tl.textAt() - 180) / 900f);
            long shown = Math.round(tl.price() * (1.0 - Math.pow(1.0 - k, 3)));
            c.text(String.format(java.util.Locale.ROOT, "%,d coins", shown), p[0], y + 13f * sc, sc, SlayerFx.a(a1) | 0xFFAA00);
        }
        float a2 = GauntletTimeline.clamp((t - tl.textAt() - 420) / 260f);
        if (a2 <= 0f) return;
        float ly = y + 23f * sc;
        String odds = NucleusHaul.odds(it);
        String nth = NucleusScene.forgeShow == null ? null : NucleusScene.forgeShow.nth;
        if (odds != null) {
            c.text(odds + (nth == null ? "" : " · " + nth), p[0], ly, sc * 0.85f, SlayerFx.a(a2 * 0.9f) | 0xCCCCCC);
            ly += 10f * sc;
        }
        if (tl.more() > 0) c.text("+" + tl.more() + " more", p[0], ly, sc * 0.8f, SlayerFx.a(a2 * 0.85f) | 0xAAAAAA);
    }

    private static int colourOf(NucleusHaul.Item it) {
        String id = it.id();
        if (id == null) return 0xFFFFFF;
        if (id.equals("DIVAN_ALLOY")) return 0xF5D547;
        if (id.equals("DYE_JADE")) return 0x00A86B;
        if (id.equals("PET_ITEM_QUICK_CLAW")) return 0xFFB43C;
        if (id.equals("JADERALD")) return 0x4FD97A;
        if (id.equals("GEMSTONE_MIXTURE")) return 0xC7A6FF;
        for (Crystal cr : Crystal.values()) if (id.startsWith("FLAWLESS_" + cr.name())) return cr.colour;
        if (id.startsWith("FLAWLESS_RUBY")) return 0xE8475A;
        if (id.startsWith("FLAWLESS_JASPER")) return 0xE05CC8;
        return ChopScene.mix(it.colour(), 0xFFFFFF, 0.2f);
    }

    private static float[] at(float[] p) {
        return NucleusScene.at(p[0], p[1], p[2]);
    }
}
