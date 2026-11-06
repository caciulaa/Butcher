package com.butcher.render;

import com.butcher.anim.NucleusHaul;
import com.butcher.anim.NucleusTimeline;
import com.butcher.anim.NucleusTimeline.Crystal;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a frame of "The Vault" (the Crystal Nucleus) onto a Canvas - the game's screen and the offline preview alike.
 *
 * Built not to drop frames (the user, 2026-10-27): of the door's 1,656 boxes, 1,439 never move. They are rasterised
 * <b>once</b> into one image at a reference camera, and every frame only places that image: the camera's yaw and pitch
 * stay fixed, so its push-in, its kicks and the door's rise are a scale and a move of the picture. Each crystal colour's
 * veins are a second image made once, with a pixel halo, drawn at the strength they glow. The patch of rock fades out
 * block by block towards its edge, darker away from the door (the user: like the Revenant's plot, no hard edge). Per
 * frame only the small moving set is rasterised - the bolts with their housings and keepers, the dial's spokes, the
 * locked crystals (the real crystal heads, NEU's skins) - plus each flying crystal on its own. Everything else is grid
 * cells (SlayerFx), batched: the motes, the comets, the power running out to the veins, the shockwaves, the seam's rays.
 */
public final class NucleusScene {
    private NucleusScene() {}

    /** The camera: one angle for the whole build-up (turning it would mean rasterising the rock again). */
    static final float YAW = 20f, PITCH = -12f;
    /** GUI pixels a model pixel, at a screen this many GUI pixels high: the frame ring about 44% of the height. */
    static final float PX_PER_H = 1f / 195f;
    /** How far a bolt pulls back, model pixels - its tip clears the frame's lip. */
    static final float BOLT_TRAVEL = 11f;
    static final float SOCKET_R = 20f;
    static final float DOOR_FACE = 7f;
    /** A crystal head's half size: 6 across, its socket's hole, which its white base goes into. */
    static final float HEAD_HALF = 3f;
    /** Where a locked crystal's middle sits: its bottom face at the hole's floor (z 7.5), the rest standing out. */
    static final float HEAD_Z = 7.5f + HEAD_HALF;
    /** The door's hinge axis (vertical), mod pixels - Blender's `Door` pivot. */
    static final float HINGE_X = -47f, HINGE_Z = 7f;
    /** A haul item's half size on the pile, and the showcase's - a plain haul's best, and a win's drop. */
    static final float ITEM_HALF = 4f, SHOW_HALF = 9.5f, WIN_HALF = 11f;
    /** Where the showcase rests, mod pixels: in front of the open doorway, above the pile. */
    static final float[] SHOW_AT = {0f, 21f, 44f};
    /** The seam between the door and the frame, model pixels from the middle. */
    static final float SEAM_R = 33f;

    /** Image pixels a GUI pixel (the screen sets it from the GUI scale). */
    public static int rasterScale = 2;

    /** The crystals' skins (64 x 64), by NEU id ("JADE_CRYSTAL"): null until there; the prisms stand in meanwhile. */
    public static java.util.function.Function<String, java.awt.image.BufferedImage> skins = n -> null;

    private static final ModelRaster ROCK = new ModelRaster(), MOVING = new ModelRaster(), FLYING = new ModelRaster(),
            DOOR = new ModelRaster(), ITEMS = new ModelRaster(), SHOW = new ModelRaster();
    /** The haul's skins and icons, gathered into one texture as they come (64 cells of 64 x 64). */
    static final int AW = 512, AH = 512;
    static final int[] ATLAS = new int[AW * AH];
    static final java.util.Map<String, int[]> CELLS = new java.util.HashMap<>();
    private static final java.util.Map<String, int[]> ICON_PX = new java.util.HashMap<>();
    private static int nextCell;

    /** Vanilla item icons (16 x 16), by texture name ("diamond_pickaxe"): the two haul items that are not heads. */
    public static java.util.function.Function<String, int[]> icons = n -> null;
    private static final ModelRaster[] GLOW = new ModelRaster[Crystal.values().length];
    private static final boolean[] skinned = new boolean[Crystal.values().length];
    private static boolean textured;

    static {
        for (int i = 0; i < GLOW.length; i++) GLOW[i] = new ModelRaster();
        ITEMS.texture(1, ATLAS, AW, AH);
        SHOW.texture(1, ATLAS, AW, AH);
    }

    /** The block textures, by name (the game's, read through its resources; the preview's from files). */
    public static void textures(java.util.function.Function<String, int[]> pixels) {
        int[] atlas = NucleusModel.atlas(pixels);
        int w = NucleusModel.atlasW(), h = NucleusModel.atlasH();
        ROCK.texture(0, atlas, w, h);
        MOVING.texture(0, atlas, w, h);
        FLYING.texture(0, atlas, w, h);
        DOOR.texture(0, atlas, w, h);
        for (ModelRaster g : GLOW) g.texture(0, atlas, w, h);
        textured = true;
        baked = null;
    }

    public static boolean hasTextures() {
        return textured;
    }

    /** The texture names the model asks for, so the screen knows what to read. */
    public static List<String> textureNames() {
        return new ArrayList<>(NucleusModel.TEXTURES);
    }

    /** Crystal {@code k}'s skin in its raster slot, once it is here. */
    private static boolean skin(int k) {
        if (skinned[k]) return true;
        java.awt.image.BufferedImage img = skins.apply(Crystal.values()[k].name() + "_CRYSTAL");
        if (img == null) return false;
        MOVING.texture(1 + k, img);
        FLYING.texture(1 + k, img);
        DOOR.texture(1 + k, img);
        skinned[k] = true;
        return true;
    }

    // ------------------------------------------------------------------ the cached layers

    /** A layer rasterised once: its pixels, their size, and where it sat on the screen at the reference camera. */
    private static final class Layer {
        int[] px;
        int iw, ih;
        float x, y, w, h;
    }

    /** The baked layers, published whole when a bake finishes (read on the render thread, written on the worker). */
    private record Baked(int w, int h, float refSize, float refX, float refY, Layer rock, Layer[] glow, int version) {
    }

    private static volatile Baked baked;
    /** The rock and frame without the door, for once it opens - baked after the closed layers, on the same worker. */
    private static volatile Layer openRock;
    private static volatile boolean baking;
    private static int versions;

    /** The size of a model pixel on a screen {@code h} high, before the push-in. */
    static float baseSize(int h) {
        return h * PX_PER_H;
    }

    /** Whether the still layers for a {@code w x h} screen are ready; if not, starts baking them on a worker thread. */
    public static boolean ready(int w, int h) {
        Baked b = baked;
        if (b != null && b.w == w && b.h == h) return true;
        if (textured && !baking) {
            baking = true;
            Thread th = new Thread(() -> {
                try {
                    openRock = null;
                    baked = bake(w, h);
                    openRock = bakeOpen(w, h);
                } finally {
                    baking = false;
                }
            }, "butcher-nucleus-bake");
            th.setDaemon(true);
            th.start();
        }
        return false;
    }

    /** The same, on this thread (the preview). */
    public static void bakeNow(int w, int h) {
        baked = bake(w, h);
        openRock = bakeOpen(w, h);
    }

    /** The rock, frame, tunnel and ledge alone, at the same reference camera as {@link #bake}. */
    private static Layer bakeOpen(int w, int h) {
        NucleusModel.Cam cam = cam(w, h, NucleusTimeline.pushMax(), 0f, 0f);
        List<NucleusModel.Box> rock = new ArrayList<>();
        for (NucleusModel.Box b : NucleusModel.BOXES) if (stillOpen(b)) rock.add(b);
        return rasterise(ROCK, rock, NucleusModel.identity(1f, 0f, 0f, 0f), 0, cam);
    }

    /** What stays where it is once the door has gone: the rock, the frame and its tunnel, the ledge. */
    private static boolean stillOpen(NucleusModel.Box b) {
        return b.part().equals("Floor") || (b.part().equals("Static") && !b.kind().startsWith("keeper"));
    }

    /**
     * Rasterises the still layers at the reference camera (the closest the build-up comes), with a camera of its own, so
     * it may run on a worker thread - never on a frame (the user: no dropped frames).
     */
    private static Baked bake(int w, int h) {
        NucleusModel.Cam cam = cam(w, h, NucleusTimeline.pushMax(), 0f, 0f);
        float[] id = NucleusModel.identity(1f, 0f, 0f, 0f);
        List<NucleusModel.Box> still = new ArrayList<>();
        for (NucleusModel.Box b : NucleusModel.BOXES) if (still(b)) still.add(b);
        // No outline round the rock: its edge fades out instead.
        Layer rock = rasterise(ROCK, still, id, 0, cam);
        Layer[] glow = new Layer[Crystal.values().length];
        for (Crystal c : Crystal.values()) {
            List<NucleusModel.Box> gems = new ArrayList<>();
            for (NucleusModel.Box b : NucleusModel.BOXES) if (b.kind().equals(c.gem) && b.part().equals("Static")) gems.add(b);
            Layer l = rasterise(GLOW[c.ordinal()], gems, id, 0xD0 << 24 | c.colour, cam);
            if (l != null) halo(l, ChopScene.mix(c.colour, 0xFFFFFF, 0.2f), Math.round(cam.size() * GLOW[c.ordinal()].scale));
            glow[c.ordinal()] = l;
        }
        return new Baked(w, h, cam.size(), cam.sx0(), cam.sy0(), rock, glow, ++versions);
    }

    /** Whether a box never moves: the rock, the frame, the door's own body, the sockets, the dial's disc. */
    private static boolean still(NucleusModel.Box b) {
        switch (b.part()) {
            case "Static":
                return !b.kind().startsWith("keeper");          // the keepers go over the bolts, every frame
            case "Floor":
                return true;
            case "Door":
                return b.rot() == 0f;                            // the turned ones are the bolts' housings
            case "Dial":
                return b.kind().equals("dial");                  // the disc looks the same at any angle
            default:
                return b.part().startsWith("Socket");
        }
    }

    private static Layer rasterise(ModelRaster r, List<NucleusModel.Box> boxes, float[] m, int add, NucleusModel.Cam cam) {
        if (boxes.isEmpty()) return null;
        float[] acc = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (NucleusModel.Box b : boxes) NucleusModel.bounds(b, m, acc, cam);
        // Room round it for the veins' halo.
        float pad = add != 0 ? cam.size() * 3f : 2f;
        if (!r.begin(acc[0] - pad, acc[1] - pad, acc[2] + pad, acc[3] + pad, rasterScale)) return null;
        for (NucleusModel.Box b : boxes) NucleusModel.box(r, b, m, add, cam);
        Layer l = new Layer();
        l.iw = r.w;
        l.ih = r.h;
        l.px = java.util.Arrays.copyOf(r.px, r.w * r.h);
        l.x = r.x;
        l.y = r.y;
        l.w = r.w / r.scale;
        l.h = r.h / r.scale;
        return l;
    }

    /**
     * A lit vein's light on the rock round it, pixel art: two square steps out from the gem, a model pixel and two -
     * each the strength of the gem it comes from, so a vein fading out at the patch's edge glows less.
     */
    private static void halo(Layer l, int rgb, int modelPx) {
        int w = l.iw, h = l.ih, n = w * h;
        int r1 = Math.max(1, modelPx), r2 = Math.max(2, Math.round(modelPx * 2.2f));
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = l.px[i] >>> 24;
        int[] near = maxFilter(a, w, h, r1), far = maxFilter(a, w, h, r2);
        for (int i = 0; i < n; i++) {
            if (a[i] != 0) continue;
            int v = Math.max(near[i] * 120 / 255, far[i] * 50 / 255);
            if (v > 2) l.px[i] = v << 24 | (rgb & 0xFFFFFF);
        }
    }

    /** Each pixel's largest value within {@code r} along a row and down a column: a square of side 2r + 1. */
    private static int[] maxFilter(int[] src, int w, int h, int r) {
        int[] rows = new int[w * h], out = new int[w * h];
        for (int y = 0; y < h; y++) {
            int o = y * w;
            for (int x = 0; x < w; x++) {
                int m = 0;
                for (int k = Math.max(0, x - r), e = Math.min(w - 1, x + r); k <= e; k++) m = Math.max(m, src[o + k]);
                rows[o + x] = m;
            }
        }
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int m = 0;
                for (int k = Math.max(0, y - r), e = Math.min(h - 1, y + r); k <= e; k++) m = Math.max(m, rows[k * w + x]);
                out[y * w + x] = m;
            }
        }
        return out;
    }

    /** Draws a cached layer where it sits under the camera now: the reference picture, scaled about its centre and moved. */
    private static void place(ChopScene.Canvas c, String key, Baked b, Layer l, float alpha) {
        if (l == null || alpha <= 0.004f) return;
        float k = ChopScene.size / b.refSize;
        float x = ChopScene.sx0 + (l.x - b.refX) * k, y = ChopScene.sy0 + (l.y - b.refY) * k;
        c.imageCached(key, l.px, l.iw, l.ih, b.version, x, y, l.w * k, l.h * k, Math.min(1f, alpha));
    }

    /** The camera's numbers for a screen, a push-in and an offset - without touching ChopScene's. */
    static NucleusModel.Cam cam(int w, int h, float push, float dx, float dy) {
        double yaw = Math.toRadians(YAW), pitch = Math.toRadians(PITCH);
        return new NucleusModel.Cam((float) Math.cos(yaw), (float) Math.sin(yaw), (float) Math.cos(pitch), (float) Math.sin(pitch),
                baseSize(h) * push, 0f, 0f, 0f, w / 2f + dx, h * 0.5f + dy);
    }

    // ------------------------------------------------------------------ the frame

    static void camera(int w, int h, float push, float dx, float dy) {
        double yaw = Math.toRadians(YAW), pitch = Math.toRadians(PITCH);
        ChopScene.cy = (float) Math.cos(yaw);
        ChopScene.sy = (float) Math.sin(yaw);
        ChopScene.cp = (float) Math.cos(pitch);
        ChopScene.sp = (float) Math.sin(pitch);
        ChopScene.size = baseSize(h) * push;
        ChopScene.fx = 0f;
        ChopScene.fy = 0f;
        ChopScene.fz = 0f;
        ChopScene.sx0 = w / 2f + dx;
        ChopScene.sy0 = h * 0.5f + dy;
    }

    /** The door's rise as a screen offset: below the frame, coming up. */
    private static float riseOffset(long t, int h) {
        return (1f - NucleusTimeline.rise(t)) * h * 0.85f;
    }

    /** What Divan's Alloy's forge shows under it: the screen fills in the price (the preview, a stand-in). */
    public static ForgeScene.Show forgeShow = new ForgeScene.Show();

    public static void draw(ChopScene.Canvas c, NucleusTimeline tl, long t, int w, int h) {
        long spot = tl.spotAt();
        if (spot >= 0 && t >= spot) {
            // The spotlight: the scene behind stopped where it was, its own showcase gone, the drop in the middle.
            float out = tl.master(t);
            if (tl.forgeAt() >= 0) {
                ForgeScene.spotlight = true;
                ForgeScene.fadeK = out;
                try {
                    ForgeScene.draw(c, null, spot - tl.forgeAt(), w, h, forgeShow);
                } finally {
                    ForgeScene.spotlight = false;
                    ForgeScene.fadeK = 1f;
                }
            } else {
                scene(c, tl, spot, w, h, true, out);
            }
            NucleusSpotlight.draw(c, tl, t, w, h);
            return;
        }
        if (tl.forgeAt() >= 0 && t >= tl.forgeAt()) {
            ForgeScene.draw(c, null, t - tl.forgeAt(), w, h, forgeShow);
            return;
        }
        scene(c, tl, t, w, h, false, 1f);
    }

    /** The vault at {@code t}; {@code frozen}: behind the spotlight, without its showcase, faded by {@code outK}. */
    private static void scene(ChopScene.Canvas c, NucleusTimeline tl, long t, int w, int h, boolean frozen, float outK) {
        float master = tl.master(t) * outK;
        c.fill(0, 0, w, h, ChopScene.alpha(0.8f * master * ChopScene.dimScale) | 0x07060A);
        if (master <= 0.01f || !textured) return;
        Baked bk = baked;
        if (bk == null || bk.w != w || bk.h != h) {
            ready(w, h);
            return;
        }
        ChopScene.sceneAlpha = master;
        SlayerFx.U = SlayerFx.gridFor(h);
        for (int k = 0; k < skinned.length; k++) skin(k);
        warm(tl);

        // Shake: the door landing, each lock, and the hang tightening before the reveal.
        float shakeX = 0f, shakeY = 0f;
        long land = t - NucleusTimeline.RISEN;
        if (land >= 0 && land < 300) shakeY += (1f - land / 300f) * 2.4f * (float) Math.sin(land / 21.0);
        for (int k = 0; k < 5; k++) {
            long d = t - NucleusTimeline.lock(k);
            if (d >= 0 && d < 240) shakeX += (1f - d / 240f) * 1.9f * (float) Math.sin(d / 17.0);
        }
        float hang = NucleusTimeline.hang(t);
        if (hang > 0f && t < NucleusTimeline.REVEAL) {
            shakeX += hang * hang * 1.3f * (float) Math.sin(t / 19.0);
            shakeY += hang * hang * 0.9f * (float) Math.sin(t / 23.0 + 1.1);
        }
        if (ChopScene.calm) shakeX = shakeY = 0f;
        // A small lean towards each socket as its crystal comes in.
        float panX = 0f, panY = 0f, size0 = baseSize(h);
        for (int k = 0; k < 5; k++) {
            float u = (t - NucleusTimeline.launch(k)) / (float) (NucleusTimeline.FLIGHT + 520);
            if (u <= 0f || u >= 1f) continue;
            float f = 0.06f * (float) Math.sin(Math.PI * u);
            float[] s = socket(Crystal.values()[k]);
            panX -= s[0] * size0 * f;
            panY += s[1] * size0 * f;
        }
        // The ending: a win's flood trembles the door; the blow and each landing kick; the camera eases back and up
        // so the doorway, the pile and the words all fit.
        float flood = tl.flood(t);
        if (flood > 0f && t < tl.open()) {
            shakeX += flood * flood * 1.6f * (float) Math.sin(t / 15.0);
            shakeY += flood * flood * 1.1f * (float) Math.sin(t / 18.0 + 0.7);
        }
        for (int k = 0; k < NucleusTimeline.SLASH.length; k++) {
            long d = t - NucleusTimeline.REVEAL - NucleusTimeline.SLASH[k] - NucleusTimeline.SLASH_MS;
            if (tl.tier() == NucleusTimeline.Tier.CLAW && d >= 0 && d < 260) shakeX += (1f - d / 260f) * 2.6f * (float) Math.sin(d / 15.0);
        }
        // The Jade Dye's build: the door straining harder to the burst, thumping on each beat; the burst's jolt.
        float jp = tl.pressure(t), jb = tl.jadeBeat(t);
        if (jp > 0f || jb > 0f) {
            shakeX += (jp * jp * 1.4f + jb * 2.4f) * (float) Math.sin(t / 16.0);
            shakeY += jb * 2.8f * (float) Math.sin(t / 12.0 + 0.4);
        }
        long jsb = tl.sinceBurst(t);
        if (tl.tier() == NucleusTimeline.Tier.JADE && jsb >= 0 && jsb < 520) {
            shakeX += (1f - jsb / 520f) * 4.4f * (float) Math.sin(jsb / 13.0);
            shakeY += (1f - jsb / 520f) * 3.2f * (float) Math.sin(jsb / 17.0);
        }
        long blown = t - tl.open();
        if (tl.tier() != NucleusTimeline.Tier.PLAIN && blown >= 0 && blown < 360) shakeX += (1f - blown / 360f) * 3.2f * (float) Math.sin(blown / 14.0);
        if (ChopScene.calm) shakeX = shakeY = 0f;
        // The Alloy dives into the doorway instead of easing back; everything else eases back so the pile fits.
        float end = tl.forgeAt() >= 0 ? 0f : ease(clamp01((t - tl.open()) / 900f));
        float push = (NucleusTimeline.push(t) + NucleusTimeline.kick(t) + endKick(tl, t) + 0.07f * jp + 0.025f * jb) * (1f - end) + 1f * end;
        float dive = tl.dive(t);
        push *= 1f + 5f * dive;
        camera(w, h, push, shakeX * ChopScene.shakeK + panX, riseOffset(t, h) + shakeY * ChopScene.shakeK + panY - end * h * 0.09f);
        // The showcase comes to the middle of the screen (the user): the camera follows it in as it rises.
        float centre = centring(tl, t);
        if (centre > 0f) {
            float[] o = at(SHOW_AT[0], SHOW_AT[1], SHOW_AT[2]);
            ChopScene.sx0 += (w / 2f - o[0]) * centre;
            ChopScene.sy0 += (h * 0.42f - o[1]) * centre;
        }
        boolean opened = t >= tl.open() && openRock != null;

        Crystal[] all = Crystal.values();
        motes(c, t, w, h, false);
        if (opened) place(c, "nucleus_rock_open", bk, openRock, master);
        else place(c, "nucleus_rock", bk, bk.rock, master);
        for (Crystal cr : all) place(c, "nucleus_glow_" + cr.ordinal(), bk, bk.glow[cr.ordinal()], master * NucleusTimeline.vein(cr.ordinal(), t));
        if (opened) interior(c, tl, t);
        seam(c, tl, t);
        paint(c, tl, t, false, opened, w, h, master);
        if (opened) {
            keepers(c, tl, t, w, h, master);
            door(c, tl, t, w, h, master);
        } else {
            sockets(c, t);
            moving(c, t, w, h, master);
        }
        paint(c, tl, t, true, opened, w, h, master);
        clawMarks(c, tl, t, w, h, master);
        dial(c, t);
        for (int k = 0; k < all.length; k++) runners(c, all[k], t);
        for (int k = 0; k < all.length; k++) {
            if (t >= NucleusTimeline.launch(k) && !NucleusTimeline.locked(k, t)) flying(c, k, t, w, h, master);
            long d = t - NucleusTimeline.lock(k);
            if (d >= 0 && d < 560) lockFlash(c, all[k], d);
            bolt(c, all[k], t);
            charge(c, k, t, w, h);
        }
        dust(c, t, h);
        if (opened) {
            blow(c, tl, t);
            if (tl.forgeAt() < 0) {
                items(c, tl, t, w, h, master);
                if (!frozen) showcase(c, tl, t, w, h, master);
            }
        }
        motes(c, t, w, h, true);
        float seamOn = NucleusTimeline.seam(t);
        if (seamOn > 0f) {
            float strength = 0.14f * seamOn * (0.55f + 0.45f * NucleusTimeline.pulse(t)) + 0.1f * hang;
            SlayerFx.edgeBands(c, w, h, tl.seamColour(t), strength * (1f - 0.6f * end), 4);
        }
        if (opened && tl.forgeAt() < 0 && !frozen) words(c, tl, t, h);
        // Into the dark of the doorway, to black, and the cut.
        if (dive > 0f) c.fill(0, 0, w, h, ChopScene.alpha(clamp01(dive * 1.3f)) | 0x050407);
        ChopScene.sceneAlpha = 1f;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static float ease(float u) {
        return u * u * (3f - 2f * u);
    }

    /** How far the camera has come to centre the showcase, 0..1: over its rise. */
    private static float centring(NucleusTimeline tl, long t) {
        if (tl.show() == null) return 0f;
        long d = t - tl.showAt();
        if (d <= 0) return 0f;
        return ease(clamp01(d / (tl.tier() == NucleusTimeline.Tier.PLAIN ? 700f : 1000f)));
    }

    /** A win's blow and the showcase's arrival kick the camera in. */
    private static float endKick(NucleusTimeline tl, long t) {
        if (tl.tier() == NucleusTimeline.Tier.PLAIN) return 0f;
        long d = t - tl.open();
        return d < 0 ? 0f : 0.07f * (float) Math.exp(-d / 150.0);
    }

    // ------------------------------------------------------------------ the pixel effects

    /** A disc of cells round (x, y), {@code r} GUI pixels. */
    static void disc(ChopScene.Canvas c, float x, float y, float r, int argb) {
        int gr = Math.max(0, Math.round(r / SlayerFx.U)), gx = SlayerFx.g(x), gy = SlayerFx.g(y);
        for (int dy = -gr; dy <= gr; dy++) {
            int half = (int) Math.floor(Math.sqrt(gr * gr - dy * dy + 0.5));
            SlayerFx.cells(c, gx - half, gy + dy, 2 * half + 1, 1, argb);
        }
    }

    /** A stepped glow, pixel art: three discs of cells, each smaller and stronger. */
    static void glow(ChopScene.Canvas c, float x, float y, float r, int rgb, float alpha) {
        if (alpha <= 0.01f || r < SlayerFx.U) return;
        for (int s = 0; s < 3; s++) {
            disc(c, x, y, r * (1f - s / 3f), SlayerFx.a(alpha * (0.14f + 0.12f * s)) | ChopScene.mix(rgb, 0xFFFFFF, 0.18f * s));
        }
    }

    /** Where a point in the door's plane lands on the screen now. */
    static float[] at(float x, float y, float z) {
        ChopScene.project(x, y, z);
        return new float[]{ChopScene.P[0], ChopScene.P[1]};
    }

    /**
     * Motes in the air: grey dust from the start, then a drift of each crystal's colour once it is in - so the air
     * fills as the vault charges. In the hang they are drawn in towards the door. Half behind the rock, half in front.
     */
    private static void motes(ChopScene.Canvas c, long t, int w, int h, boolean front) {
        float hang = NucleusTimeline.hang(t);
        float[] mid = at(0f, 0f, DOOR_FACE);
        for (int k = -1; k < 5; k++) {
            float on;
            int rgb, n;
            if (k < 0) {
                on = 0.5f;
                rgb = 0x9A948A;
                n = 16;
            } else {
                long d = t - NucleusTimeline.lit(k);
                if (d < 0) continue;
                on = Math.min(1f, d / 600f);
                rgb = Crystal.values()[k].colour;
                n = 12;
            }
            for (int i = 0; i < n; i++) {
                if (((i & 1) == 1) != front) continue;
                long seed = 9100L + k * 131L;
                float speed = 9f + 16f * SlayerFx.rnd(seed, i, 1);
                float x = SlayerFx.rnd(seed, i, 2) * w + 7f * (float) Math.sin(t / 900.0 + i * 1.7);
                float y = h + 10f - ((SlayerFx.rnd(seed, i, 3) * (h + 20f) + t * speed / 1000f * (h / 360f)) % (h + 20f));
                if (hang > 0f) {
                    float pull = hang * hang * 0.55f;
                    x += (mid[0] - x) * pull;
                    y += (mid[1] - y) * pull;
                }
                float tw = 0.5f + 0.5f * (float) Math.sin(t / (170.0 + 230 * SlayerFx.rnd(seed, i, 4)) + i * 2.1);
                float a = on * (0.25f + 0.5f * tw) * (k < 0 ? 0.6f : 1f);
                SlayerFx.square(c, x, y, front && SlayerFx.rnd(seed, i, 5) < 0.4f ? 2 : 1, SlayerFx.a(a) | ChopScene.mix(rgb, 0xFFFFFF, 0.25f * tw));
            }
        }
    }

    /** Grit shaken off the rock as it rises, and a puff of dust along the bottom as it lands. */
    private static void dust(ChopScene.Canvas c, long t, int h) {
        if (t > NucleusTimeline.RISEN + 900) return;
        float now = riseOffset(t, h);
        for (int i = 0; i < 30; i++) {
            long born = i * 24L;
            long age = t - born;
            if (age < 0 || age > 700) continue;
            float mx = -150f + 300f * SlayerFx.rnd(41, i, 1), my = -30f - 40f * SlayerFx.rnd(41, i, 2);
            float[] p = at(mx, my, 2f);
            float y0 = p[1] + (riseOffset(born, h) - now);
            float s = age / 1000f;
            float y = y0 + 0.9f * h * s * s + 8f * s;
            float a = 0.8f * (1f - age / 700f);
            SlayerFx.square(c, p[0], y, SlayerFx.rnd(41, i, 3) < 0.35f ? 2 : 1, SlayerFx.a(a) | (i % 3 == 0 ? 0x6E6A62 : 0x8C877D));
        }
        long d = t - NucleusTimeline.RISEN;
        if (d < 0) return;
        float k = d / 900f;
        for (int i = 0; i < 26; i++) {
            float side = i % 2 == 0 ? -1f : 1f;
            float mx = side * (18f + 36f * SlayerFx.rnd(43, i, 1));
            float[] p = at(mx, -50f, DOOR_FACE);
            float v = (30f + 70f * SlayerFx.rnd(43, i, 2)) * ChopScene.size / 3f;
            float x = p[0] + side * v * (1f - (1f - k) * (1f - k)), y = p[1] - 14f * ChopScene.size / 3f * k * SlayerFx.rnd(43, i, 3);
            SlayerFx.square(c, x, y, i % 4 == 0 ? 3 : 2, SlayerFx.a(0.55f * (1f - k)) | 0x8A8478);
        }
    }

    /**
     * The light leaking from the seam between the door and the frame: the cycle's colour, flickering, round the door;
     * rays out across the frame and the rock; a ring out on each change of colour.
     */
    static void seam(ChopScene.Canvas c, NucleusTimeline tl, long t) {
        float flood = tl.flood(t);
        float on = NucleusTimeline.seam(t) * (1f + 1.6f * flood) * (1f - clamp01(tl.door(t) / 0.45f));
        on *= (1f + 1.3f * tl.jadeBeat(t)) * (1f - 0.88f * tl.hush(t)) * (tl.sinceBurst(t) >= 0 && tl.tier() == NucleusTimeline.Tier.JADE ? 0.55f : 1f);
        if (on <= 0.01f) return;
        int rgb = tl.seamColour(t);
        float pulse = NucleusTimeline.pulse(t), hang = NucleusTimeline.hang(t);
        int n = 120;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            float flick = 0.75f + 0.25f * (float) Math.sin(t / (55.0 - 25 * hang) + i * 1.7);
            float[] p = at(SEAM_R * (float) Math.cos(a), SEAM_R * (float) Math.sin(a), 6.2f);
            SlayerFx.square(c, p[0], p[1], 2, SlayerFx.a(on * 0.35f * flick) | rgb);
            SlayerFx.square(c, p[0], p[1], 1, SlayerFx.a(on * flick) | ChopScene.mix(rgb, 0xFFFFFF, 0.45f));
        }
        // The rays: out from the seam over the frame and the rock, longer on each pulse and through the hang.
        int rays = 24;
        for (int i = 0; i < rays; i++) {
            double a = (i + 0.5) * Math.PI * 2 / rays + 0.08 * Math.sin(i * 2.3);
            float flick = 0.6f + 0.4f * (float) Math.sin(t / (90.0 - 40 * hang) + i * 2.9);
            float len = (10f + 16f * SlayerFx.rnd(77, i, 1)) * Math.min(1f, on) * (0.7f + 0.6f * pulse + 0.9f * hang + 2.4f * flood);
            int steps = Math.max(1, Math.round(len / 1.6f));
            for (int s = 0; s < steps; s++) {
                float r = SEAM_R + 1.5f + s * 1.6f, f = 1f - s / (float) steps;
                float[] p = at(r * (float) Math.cos(a), r * (float) Math.sin(a), 6.4f);
                SlayerFx.square(c, p[0], p[1], 1, SlayerFx.a(on * flick * 0.55f * f * f) | ChopScene.mix(rgb, 0xFFFFFF, 0.3f * f));
            }
        }
        // A ring out across the rock on each new colour.
        for (int i = 0; i < NucleusTimeline.stepCount(); i++) {
            long d = t - NucleusTimeline.step(i);
            if (d < 0 || d > 360) continue;
            float k = d / 360f, r = SEAM_R + 4f + 38f * (1f - (1f - k) * (1f - k));
            ring(c, r, 6.5f, SlayerFx.a(on * 0.6f * (1f - k)) | ChopScene.mix(rgb, 0xFFFFFF, 0.25f));
        }
    }

    /** A ring of cells on the door's plane, about its middle (or about (cx, cy)), {@code r} model pixels. */
    private static void ring(ChopScene.Canvas c, float r, float z, int argb) {
        ringAt(c, 0f, 0f, r, z, argb);
    }

    static void ringAt(ChopScene.Canvas c, float cx, float cy, float r, float z, int argb) {
        if ((argb >>> 24) < 3) return;
        int n = Math.max(16, (int) (Math.PI * 2 * r * ChopScene.size / SlayerFx.U * 1.2f));
        int lx = Integer.MIN_VALUE, ly = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            float[] p = at(cx + r * (float) Math.cos(a), cy + r * (float) Math.sin(a), z);
            int gx = SlayerFx.g(p[0]), gy = SlayerFx.g(p[1]);
            if (gx == lx && gy == ly) continue;
            lx = gx;
            ly = gy;
            SlayerFx.cells(c, gx, gy, 1, 1, argb);
        }
    }

    /**
     * Each locked socket glows its crystal's colour, breathing; through the cycle the socket whose colour the seam shows
     * flares - the same on every run.
     */
    private static void sockets(ChopScene.Canvas c, long t) {
        int step = NucleusTimeline.cycleStep(t);
        float pulse = NucleusTimeline.pulse(t);
        for (Crystal cr : Crystal.values()) {
            int k = cr.ordinal();
            if (!NucleusTimeline.locked(k, t)) continue;
            float[] s = socket(cr);
            float[] p = at(s[0], s[1], 10f);
            float a = 0.42f + 0.18f * (float) Math.sin(t / 320.0 + k * 1.3);
            float r = ChopScene.size * 7.5f;
            if (step == k) {
                a += 0.5f * (0.4f + 0.6f * pulse);
                r *= 1.25f + 0.35f * pulse;
            }
            glow(c, p[0], p[1], r, cr.colour, a);
        }
    }

    /**
     * The power running out of a socket the moment its crystal locks: three comets of its colour over the door and the
     * frame, out to the farthest of its veins, each arching a little; the vein flashes as one gets there.
     */
    private static void runners(ChopScene.Canvas c, Crystal cr, long t) {
        int k = cr.ordinal();
        float p = NucleusTimeline.run(k, t);
        long after = t - NucleusTimeline.lit(k);
        if (p <= 0f || after > 520) return;
        float[] s = socket(cr);
        float[][] to = targets(cr);
        for (int j = 0; j < to.length; j++) {
            float[] e = to[j];
            float dx = e[0] - s[0], dy = e[1] - s[1], len = (float) Math.hypot(dx, dy);
            float side = j % 2 == 0 ? 1f : -1f;
            float mx = (s[0] + e[0]) / 2f - dy / len * len * 0.18f * side, my = (s[1] + e[1]) / 2f + dx / len * len * 0.18f * side;
            if (after < 0) {
                // The comet: a bright head and a tail behind it.
                int n = 14;
                for (int i = 0; i < n; i++) {
                    float u = p - 0.22f * i / n;
                    if (u < 0f) break;
                    float[] q = curve(s[0], s[1], 11f, mx, my, 15f, e[0], e[1], e[2] + 0.5f, u);
                    float f = 1f - i / (float) n;
                    SlayerFx.square(c, q[0], q[1], i < 2 ? 2 : 1, SlayerFx.a(0.95f * f) | ChopScene.mix(cr.colour, 0xFFFFFF, i < 2 ? 0.7f : 0.25f * f));
                }
            }
            // The path it took, fading: every other cell.
            float trace = after < 0 ? 0.3f : 0.3f * (1f - after / 520f);
            int m = 26;
            for (int i = 0; i < m; i += 2) {
                float u = i / (float) m;
                if (u > p) break;
                float[] q = curve(s[0], s[1], 11f, mx, my, 15f, e[0], e[1], e[2] + 0.5f, u);
                SlayerFx.square(c, q[0], q[1], 1, SlayerFx.a(trace) | cr.colour);
            }
            if (after >= 0) {
                float[] q = at(e[0], e[1], e[2]);
                SlayerFx.burst(c, q[0], q[1], after, 420, 9, ChopScene.size * 26f, 700L + k * 17L + j, new int[]{cr.colour, 0xFFFFFF}, 1f, ChopScene.size * 40f);
                if (after < 200) glow(c, q[0], q[1], ChopScene.size * 6f, cr.colour, 0.9f * (1f - after / 200f));
            }
        }
    }

    /** A point on the quadratic curve a - b - c at {@code u}, projected. */
    private static float[] curve(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float u) {
        float v = 1f - u;
        return at(v * v * ax + 2 * v * u * bx + u * u * cx, v * v * ay + 2 * v * u * by + u * u * cy, v * v * az + 2 * v * u * bz + u * u * cz);
    }

    private static final float[][][] TARGETS = new float[Crystal.values().length][][];

    /** Where each crystal's power runs to: three of its veins, as far apart as they go, inside the patch's solid part. */
    static float[][] targets(Crystal cr) {
        float[][] got = TARGETS[cr.ordinal()];
        if (got != null) return got;
        List<float[]> pts = new ArrayList<>();
        for (NucleusModel.Box b : NucleusModel.BOXES) {
            if (!b.part().equals("Static") || !b.kind().equals(cr.gem)) continue;
            if (b.alpha() < 0.99f) continue;
            pts.add(new float[]{(b.x0() + b.x1()) / 2f, (b.y0() + b.y1()) / 2f, b.z1()});
        }
        List<float[]> chosen = new ArrayList<>();
        float[] s = socket(cr);
        for (int n = 0; n < 3 && !pts.isEmpty(); n++) {
            float[] best = null;
            float bestD = -1f;
            for (float[] q : pts) {
                float d = (float) Math.hypot(q[0] - s[0], q[1] - s[1]);
                for (float[] o : chosen) d = Math.min(d, (float) Math.hypot(q[0] - o[0], q[1] - o[1]));
                if (d > bestD) {
                    bestD = d;
                    best = q;
                }
            }
            chosen.add(best);
            pts.remove(best);
        }
        got = chosen.toArray(new float[0][]);
        TARGETS[cr.ordinal()] = got;
        return got;
    }

    /** Sparks flung off the dial's rim as it spins up, more the faster it goes, and its glow. */
    private static void dial(ChopScene.Canvas c, long t) {
        if (t < NucleusTimeline.SPIN_FROM || t > NucleusTimeline.REVEAL + 300) return;
        float speed = speedAt(t);
        float[] mid = at(0f, 0f, 11f);
        glow(c, mid[0], mid[1], ChopScene.size * 14f, 0xF2C04A, Math.min(0.7f, speed / 16f * 0.7f));
        for (long b = t / 30 - 9; b <= t / 30; b++) {
            long born = b * 30;
            long age = t - born;
            if (age < 0 || age > 260 || born < NucleusTimeline.SPIN_FROM) continue;
            float sp = speedAt(born);
            int n = Math.min(4, Math.round(sp / 4.5f));
            for (int j = 0; j < n; j++) {
                double a = NucleusTimeline.dial(born) + j * Math.PI * 2 / Math.max(1, n) + SlayerFx.rnd(b, j, 1) * 1.2;
                float r0 = 10f, v = 0.03f + 0.004f * sp;
                float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                // tangential (the way it turns) and a little outward
                float x = r0 * ca + (-sa * 0.8f + ca * 0.45f) * v * age, y = r0 * sa + (ca * 0.8f + sa * 0.45f) * v * age;
                float[] p = at(x, y, 11.5f);
                float f = 1f - age / 260f;
                SlayerFx.square(c, p[0], p[1], f > 0.6f ? 2 : 1, SlayerFx.a(f) | (j % 2 == 0 ? 0xFFE59A : 0xFFFFFF));
            }
        }
    }

    /** The dial's turning speed, radians a second. */
    private static float speedAt(long t) {
        return Math.abs(NucleusTimeline.dial(t + 8) - NucleusTimeline.dial(t - 8)) * 1000f / 16f;
    }

    /** A crystal's bolt pulling back: sparks off its tip as it unlatches. */
    private static void bolt(ChopScene.Canvas c, Crystal cr, long t) {
        long d = t - NucleusTimeline.lock(cr.ordinal()) - 60;
        if (d < 0 || d > 360) return;
        float[] tip = boltTip(boltOf(cr));
        if (tip == null) return;
        float[] p = at(tip[0], tip[1], 9f);
        SlayerFx.burst(c, p[0], p[1], d, 360, 8, ChopScene.size * 22f, 900L + cr.ordinal(), new int[]{0xFFE07A, 0xFFFFFF, 0xC88A2A}, 1f, ChopScene.size * 60f);
    }

    private static final java.util.Map<String, float[]> TIPS = new java.util.HashMap<>();

    /** The end of a bolt that goes into the frame, where it is at rest. */
    private static float[] boltTip(String part) {
        return TIPS.computeIfAbsent(part, name -> {
            NucleusModel.Part p = NucleusModel.PARTS.get(name);
            if (p == null || p.slide() == null) return null;
            float sx = p.slide()[0], sy = p.slide()[1], best = -1f;
            for (NucleusModel.Box b : NucleusModel.BOXES) {
                if (!b.part().equals(name)) continue;
                for (int i = 0; i < 4; i++) {
                    float x = (i & 1) == 0 ? b.x0() : b.x1(), y = (i & 2) == 0 ? b.y0() : b.y1();
                    best = Math.max(best, x * sx + y * sy);
                }
            }
            return best < 0f ? null : new float[]{sx * best, sy * best};
        });
    }

    /** A glint at the edge of the screen where a crystal is about to come from. */
    private static void charge(ChopScene.Canvas c, int k, long t, int w, int h) {
        float ch = NucleusTimeline.charge(k, t);
        if (ch <= 0.01f) return;
        Crystal cr = Crystal.values()[k];
        float[] st = start(cr);
        float[] p = at(st[0], st[1], st[2]);
        float x = Math.max(w * 0.07f, Math.min(w * 0.93f, p[0])), y = Math.max(h * 0.1f, Math.min(h * 0.9f, p[1]));
        glow(c, x, y, ChopScene.size * 9f * ch, cr.colour, 0.8f * ch);
        int arm = Math.round(1 + 4 * ch);
        int gx = SlayerFx.g(x), gy = SlayerFx.g(y), col = SlayerFx.a(ch) | ChopScene.mix(cr.colour, 0xFFFFFF, 0.6f);
        SlayerFx.cells(c, gx - arm, gy, 2 * arm + 1, 1, col);
        SlayerFx.cells(c, gx, gy - arm, 1, 2 * arm + 1, col);
    }

    /** Where crystal {@code k}'s socket sits, model pixels. */
    static float[] socket(Crystal cr) {
        double a = Math.toRadians(cr.socket);
        return new float[]{Math.round(SOCKET_R * Math.cos(a) / 2) * 2f, Math.round(SOCKET_R * Math.sin(a) / 2) * 2f};
    }

    /** The bolt a crystal pulls back: the one just counter-clockwise of its socket. */
    static String boltOf(Crystal cr) {
        int deg = Math.floorMod(cr.socket + 36 - 126, 360);
        return "Bolt" + (deg / 72);
    }

    /** The parts that move, and the locked crystals: rasterised each frame, small. */
    private static void moving(ChopScene.Canvas c, long t, int w, int h, float master) {
        float[] id = NucleusModel.identity(1f, 0f, 0f, 0f);
        float spin = NucleusTimeline.dial(t);
        float[] dial = NucleusModel.spun(id, 0f, 0f, spin);
        List<NucleusModel.Box> boxes = new ArrayList<>();
        List<float[]> poses = new ArrayList<>();
        Crystal[] all = Crystal.values();
        for (NucleusModel.Box b : NucleusModel.BOXES) {
            float[] m = null;
            if (b.part().equals("Static") && b.kind().startsWith("keeper")) m = id;
            else if (b.part().equals("Door") && b.rot() != 0f) m = id;
            else if (b.part().equals("Dial") && !b.kind().equals("dial")) m = dial;
            else if (b.part().startsWith("Bolt")) {
                float pull = 0f;
                for (Crystal cr : all) if (boltOf(cr).equals(b.part())) pull = NucleusTimeline.bolt(cr.ordinal(), t);
                NucleusModel.Part p = NucleusModel.PARTS.get(b.part());
                float[] s = p == null || p.slide() == null ? new float[]{0, 0} : p.slide();
                m = NucleusModel.moved(id, -s[0] * BOLT_TRAVEL * pull, -s[1] * BOLT_TRAVEL * pull, 0f);
            }
            if (m != null) {
                boxes.add(b);
                poses.add(m);
            }
        }
        List<Crystal> heads = new ArrayList<>();
        for (Crystal cr : all) {
            if (!NucleusTimeline.locked(cr.ordinal(), t)) continue;
            if (skinned[cr.ordinal()]) {
                heads.add(cr);
                continue;
            }
            float[] s = socket(cr);
            float[] m = NucleusModel.moved(id, s[0], s[1], DOOR_FACE - 0.5f);
            for (NucleusModel.Box b : crystal(cr)) {
                boxes.add(b);
                poses.add(m);
            }
        }
        if (boxes.isEmpty() && heads.isEmpty()) return;
        float[] acc = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        NucleusModel.Cam cam = NucleusModel.Cam.current();
        for (int i = 0; i < boxes.size(); i++) NucleusModel.bounds(boxes.get(i), poses.get(i), acc, cam);
        for (Crystal cr : heads) NucleusModel.headBounds(lockedPose(cr), HEAD_HALF, acc, cam);
        float pad = 2f;
        if (!MOVING.begin(Math.max(0f, acc[0] - pad), Math.max(0f, acc[1] - pad), Math.min(w, acc[2] + pad), Math.min(h, acc[3] + pad), rasterScale)) return;
        for (int i = 0; i < boxes.size(); i++) {
            NucleusModel.Box b = boxes.get(i);
            int add = 0;
            Crystal gem = gemOf(b.kind());
            if (gem != null) add = 0x60 << 24 | gem.colour;
            NucleusModel.box(MOVING, b, poses.get(i), add, cam);
        }
        for (Crystal cr : heads) {
            long d = t - NucleusTimeline.lock(cr.ordinal());
            // White hot as it lands, then its own colour breathing in it.
            float hot = (float) Math.exp(-d / 130.0);
            int add = hot > 0.05f ? Math.round(0xC0 * hot) << 24 | 0xFFFFFF : Math.round(0x28 + 0x18 * (float) Math.sin(t / 320.0 + cr.ordinal())) << 24 | cr.colour;
            NucleusModel.head(MOVING, 1 + cr.ordinal(), lockedPose(cr), HEAD_HALF, add, cam);
        }
        // No outline here: the rock and the door have none inside their silhouette, and inked keepers read as staples.
        MOVING.fade(master);
        c.image("nucleus_moving", MOVING.px, MOVING.w, MOVING.h, MOVING.x, MOVING.y, MOVING.w / MOVING.scale, MOVING.h / MOVING.scale);
    }

    /**
     * A locked crystal's head, on the door's pose {@code door}: stood upright in its socket - its bottom (the white
     * base of the skin) into the hole and its top, the crystal's point, towards the camera (the user, 2026-10-27).
     */
    private static float[] lockedPose(Crystal cr, float[] door) {
        float[] s = socket(cr);
        return NucleusModel.tilted(NucleusModel.moved(door, s[0], s[1], HEAD_Z), (float) (Math.PI / 2));
    }

    private static float[] lockedPose(Crystal cr) {
        return lockedPose(cr, NucleusModel.identity(1f, 0f, 0f, 0f));
    }

    private static Crystal gemOf(String kind) {
        for (Crystal cr : Crystal.values()) if (cr.gem.equals(kind)) return cr;
        return null;
    }

    /**
     * A crystal before its skin is here: a six-sided prism of the gem standing out of its socket (along +z), a point on
     * it, two small shards at its foot - in model pixels about the socket's centre.
     */
    static List<NucleusModel.Box> crystal(Crystal cr) {
        String g = cr.gem;
        List<NucleusModel.Box> out = new ArrayList<>();
        out.add(new NucleusModel.Box("Crystal", g, -1.5f, 1.5f, -2f, 2f, 0f, 7f, 0f, 0f, 0f));
        out.add(new NucleusModel.Box("Crystal", g, -2f, 2f, -1.5f, 1.5f, 0f, 6.5f, 0f, 0f, 0f));
        out.add(new NucleusModel.Box("Crystal", g, -1f, 1f, -1f, 1f, 7f, 9f, 0f, 0f, 0f));
        out.add(new NucleusModel.Box("Crystal", g, 1.5f, 2.75f, 0.5f, 1.75f, 0f, 3.5f, 0.4f, 0f, 0f));
        out.add(new NucleusModel.Box("Crystal", g, -2.75f, -1.5f, -1.75f, -0.5f, 0f, 2.5f, 0.4f, 0f, 0f));
        return out;
    }

    /** Where a crystal's flight starts, model pixels: far out along its socket's direction (topaz: from below). */
    private static float[] start(Crystal cr) {
        double from = Math.toRadians(cr == Crystal.TOPAZ ? 270 : cr.socket);
        return new float[]{170f * (float) Math.cos(from), 120f * (float) Math.sin(from), 40f};
    }

    /** Crystal {@code k}'s place along its flight at {@code u} (0..1): {x, y, z}, model pixels. */
    private static float[] path(Crystal cr, float u) {
        float[] s = socket(cr), st = start(cr);
        double from = Math.toRadians(cr == Crystal.TOPAZ ? 270 : cr.socket);
        float ex = s[0], ey = s[1], ez = HEAD_Z + 12f;
        float bend = cr == Crystal.TOPAZ ? 60f : 24f;
        float mx = (st[0] + ex) / 2f + bend * (float) Math.cos(from + Math.PI / 2), my = (st[1] + ey) / 2f + bend * (float) Math.sin(from + Math.PI / 2);
        float q = Math.min(1f, u / 0.82f);
        float x = (1 - q) * (1 - q) * st[0] + 2 * (1 - q) * q * mx + q * q * ex;
        float y = (1 - q) * (1 - q) * st[1] + 2 * (1 - q) * q * my + q * q * ey;
        float z = st[2] + (ez - st[2]) * q;
        if (u > 0.82f) z = ez + (HEAD_Z - ez) * ((u - 0.82f) / 0.18f);
        return new float[]{x, y, z, q};
    }

    /**
     * A crystal on its way, a comet: from beyond the screen's edge on its zone's side, curving in, tumbling, slowing into
     * the socket - the last stretch straight in along the door's normal - with a glow, a tail and sparks shed behind.
     */
    private static void flying(ChopScene.Canvas c, int k, long t, int w, int h, float master) {
        Crystal cr = Crystal.values()[k];
        float u = NucleusTimeline.flight(k, t);
        float[] pos = path(cr, u);
        float q = pos[3];
        // The tail: back along where it has been, thick and white near it.
        int n = 18;
        for (int i = n; i >= 1; i--) {
            float[] pp = path(cr, NucleusTimeline.flight(k, t - i * 14L));
            float[] p = at(pp[0], pp[1], pp[2]);
            float f = 1f - i / (float) (n + 1);
            SlayerFx.square(c, p[0], p[1], i < 4 ? 3 : (i < 10 ? 2 : 1), SlayerFx.a(0.85f * f) | ChopScene.mix(cr.colour, 0xFFFFFF, i < 4 ? 0.45f : 0.1f));
        }
        // Sparks shed along the way, drifting and twinkling out.
        for (int i = 0; i < 12; i++) {
            float ui = 0.05f + 0.75f * SlayerFx.rnd(300 + k, i, 1);
            long born = NucleusTimeline.launch(k) + (long) (ui * NucleusTimeline.FLIGHT);
            long age = t - born;
            if (age < 0 || age > 340) continue;
            float[] pp = path(cr, NucleusTimeline.flight(k, born));
            float[] p = at(pp[0], pp[1], pp[2]);
            double dir = SlayerFx.rnd(300 + k, i, 2) * Math.PI * 2;
            float v = ChopScene.size * (0.02f + 0.03f * SlayerFx.rnd(300 + k, i, 3));
            float f = 1f - age / 340f, tw = 0.5f + 0.5f * (float) Math.sin(age / 40.0 + i);
            SlayerFx.square(c, p[0] + (float) Math.cos(dir) * v * age, p[1] + (float) Math.sin(dir) * v * age + 0.00004f * age * age * h,
                    1, SlayerFx.a(f * (0.5f + 0.5f * tw)) | ChopScene.mix(cr.colour, 0xFFFFFF, 0.5f));
        }
        float[] head = at(pos[0], pos[1], pos[2]);
        glow(c, head[0], head[1], ChopScene.size * (8f + 6f * (1f - q)), cr.colour, 0.7f);

        // Coming from near the camera, it is bigger at first and settles to its size in the socket.
        float half = HEAD_HALF * (1f + 0.9f * (1f - q));
        float tumble = (1f - q) * 9f, roll = (1f - q) * 6f;
        float[] m = NucleusModel.identity(1f, 0f, 0f, 0f);
        m = NucleusModel.moved(m, pos[0], pos[1], pos[2]);
        m = NucleusModel.hinged(m, 0f, 0f, tumble);
        m = NucleusModel.spun(m, 0f, 0f, roll);
        // Landing as it sits locked: upright, the base first into the hole.
        m = NucleusModel.tilted(m, (float) (Math.PI / 2));
        NucleusModel.Cam cam = NucleusModel.Cam.current();
        float[] acc = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        boolean skin = skinned[k];
        List<NucleusModel.Box> boxes = skin ? null : crystal(cr);
        if (skin) NucleusModel.headBounds(m, half, acc, cam);
        else for (NucleusModel.Box b : boxes) NucleusModel.bounds(b, m, acc, cam);
        float inkW = Math.max(1.2f, ChopScene.size / 1.6f), pad = inkW + 2f;
        if (!FLYING.begin(Math.max(0f, acc[0] - pad), Math.max(0f, acc[1] - pad), Math.min(w, acc[2] + pad), Math.min(h, acc[3] + pad), rasterScale)) return;
        if (skin) NucleusModel.head(FLYING, 1 + k, m, half, 0x30 << 24 | cr.colour, cam);
        else for (NucleusModel.Box b : boxes) NucleusModel.box(FLYING, b, m, 0x80 << 24 | cr.colour, cam);
        FLYING.ink(Math.max(1, Math.round(inkW * FLYING.scale)));
        FLYING.fade(master);
        c.image("nucleus_crystal_" + k, FLYING.px, FLYING.w, FLYING.h, FLYING.x, FLYING.y, FLYING.w / FLYING.scale, FLYING.h / FLYING.scale);
    }

    /** A crystal locking in: a flash, two shockwaves out across the door, and a burst of its colour. */
    private static void lockFlash(ChopScene.Canvas c, Crystal cr, long d) {
        float[] s = socket(cr);
        float[] p = at(s[0], s[1], 12f);
        int h = Math.round(ChopScene.size * 195f);
        SlayerFx.burst(c, p[0], p[1], d, 520, 18, h * 0.38f, cr.ordinal() * 31L + 5, new int[]{cr.colour, 0xFFFFFF, ChopScene.mix(cr.colour, 0xFFFFFF, 0.5f)}, 1f, h * 0.6f);
        if (d < 170) glow(c, p[0], p[1], ChopScene.size * 11f, ChopScene.mix(cr.colour, 0xFFFFFF, 0.4f), 1f - d / 170f);
        for (int wv = 0; wv < 2; wv++) {
            long e = d - wv * 90L;
            if (e < 0 || e > 430) continue;
            float k = e / 430f, r = 5f + 42f * (1f - (1f - k) * (1f - k));
            ringAt(c, s[0], s[1], r, 10f, SlayerFx.a((1f - k) * (wv == 0 ? 0.9f : 0.5f)) | ChopScene.mix(cr.colour, 0xFFFFFF, wv == 0 ? 0.35f : 0f));
        }
    }

    // ------------------------------------------------------------------ the Jade Dye and the Quick Claw

    /** The Jade Dye's paint: this many runs out of the seam. */
    static final int RUNS = 30;

    /** Run {@code i}'s start on the seam, its length when it has all run, and its wiggle's seed. */
    private static float[] run(int i) {
        double a = (i + 0.5) * Math.PI * 2 / RUNS + 0.12 * Math.sin(i * 2.7);
        float x = SEAM_R * (float) Math.cos(a), y = SEAM_R * (float) Math.sin(a);
        // From the top the paint runs down over the door's face; from below, down the frame and the wall to the floor.
        float len = (y > 0 ? 30f + y * 1.4f : 18f + 40f * SlayerFx.rnd(71, i, 1)) * (0.75f + 0.5f * SlayerFx.rnd(71, i, 2));
        return new float[]{x, y, len};
    }

    /** Where a point of paint lies: on the door's face, the frame's, or the wall's. */
    private static float surface(float x, float y) {
        float r = (float) Math.hypot(x, y);
        return r < 32.5f ? 7.4f : (r < 44f ? 6.3f : 0.6f);   // the door (r 32), the frame (to 44), the wall
    }

    private static float[] point(float[] m, float x, float y, float z) {
        return new float[]{m[0] * x + m[3] * y + m[6] * z + m[9], m[1] * x + m[4] * y + m[7] * z + m[10], m[2] * x + m[5] * y + m[8] * z + m[11]};
    }

    static final int JADE = NucleusTimeline.JADE_COLOUR, JADE_LIGHT = 0x46D9A0;
    /**
     * The paint's (and the claw's torn metal's) texture slot in the rasters: an 8 x 8 wet sheen, tinted per box. The paint
     * is boxes rasterised as the door is - raised runs, beads, pools with depth, blobs that tumble - since flat squares
     * read as 2D in the 3D scene (the user, 2026-10-27).
     */
    static final int PAINT_SLOT = 6;
    private static final ModelRaster PAINT = new ModelRaster(), SPLASH = new ModelRaster();
    private static final Frozen PAINT_STILL = new Frozen();
    private static int paintStillKey;

    static {
        int[] sheen = new int[64];
        for (int i = 0; i < 64; i++) {
            int v = 214 + (int) (30 * SlayerFx.rnd(91, i, 1));
            sheen[i] = 0xFF000000 | v << 16 | v << 8 | v;
        }
        sheen[9] = sheen[10] = sheen[17] = 0xFFFFFFFF;
        PAINT.texture(PAINT_SLOT, sheen, 8, 8);
        SPLASH.texture(PAINT_SLOT, sheen, 8, 8);
        DOOR.texture(PAINT_SLOT, sheen, 8, 8);
    }

    /**
     * The paint as boxes {x0, x1, y0, y1, z0, z1, rgb}: the seam brimming with it, every run a raised line of it - thick
     * near the seam, thinning - with a bead at its head, and a pool with depth where one reaches the floor. {@code door}:
     * only what lies on the door's face (it swings with the door), else only the rest.
     */
    static List<float[]> paintBoxes(float p, boolean door) {
        List<float[]> out = new ArrayList<>();
        if (p <= 0f) return out;
        if (!door) {
            float well = clamp01(p * 3f);
            for (int i = 0; i < 44; i++) {
                double a = (i + 0.5) * Math.PI * 2 / 44;
                float x = SEAM_R * (float) Math.cos(a), y = SEAM_R * (float) Math.sin(a), sz = 1.6f + 1.0f * well;
                out.add(new float[]{x - sz, x + sz, y - sz, y + sz, 6.3f, 7.6f + 0.9f * well, i % 5 == 0 ? JADE_LIGHT : JADE});
            }
        }
        final float seg = 2f;
        for (int i = 0; i < RUNS; i++) {
            float[] r = run(i);
            float len = r[2] * p;
            float lx = r[0], ly = r[1];
            boolean floor = false;
            int k = 0;
            // Runs differ: some thick, some a thread; and each swells here and there where more of it has gathered.
            float wk = 0.6f + 0.8f * SlayerFx.rnd(71, i, 3);
            for (float s = 0; s <= len; s += seg, k++) {
                float px = r[0] + 1.2f * (float) Math.sin(s * 0.21f + i * 1.7f), py = r[1] - s;
                if (py < NucleusModel.FLOOR_Y + 0.6f) {
                    floor = true;
                    break;
                }
                lx = px;
                ly = py;
                boolean onDoor = Math.hypot(px, py) < 32.5f && r[1] > 0;
                if (onDoor != door) continue;
                float z = surface(px, py), f = s / Math.max(1f, r[2]);
                boolean swell = (k + i) % 5 == 0 && k > 0;
                float wd = (2.8f - 1.3f * f) * wk * (swell ? 1.45f : 1f), th = (1.15f - 0.45f * f) * (swell ? 1.5f : 1f);
                out.add(new float[]{px - wd / 2f, px + wd / 2f, py - seg - 0.3f, py + 0.3f, z, z + th, k % 7 == 3 ? JADE_LIGHT : JADE});
            }
            // The bead at its head, heavy, about to drop.
            if (len > 2f && !floor && (Math.hypot(lx, ly) < 32.5f && r[1] > 0) == door) {
                float z = surface(lx, ly);
                out.add(new float[]{lx - 1.8f, lx + 1.8f, ly - 3.4f, ly + 0.4f, z, z + 2f, JADE_LIGHT});
            }
            // A pool where it reached the floor, spreading out towards the camera, a little higher than the floor.
            if (!door && floor) {
                float reach = clamp01((len - (r[1] - NucleusModel.FLOOR_Y)) / 26f);
                float rad = 3f + 10f * reach;
                float y0 = NucleusModel.FLOOR_Y, cz = 0.6f + rad * 0.9f;
                for (float zz = -rad; zz < rad; zz += 2f) {
                    float mid = zz + 1f;
                    float hw = rad * (float) Math.sqrt(Math.max(0f, 1f - mid * mid / (rad * rad))) * (1f + 0.15f * (float) Math.sin(i * 3.1f + zz));
                    if (hw < 0.7f) continue;
                    out.add(new float[]{lx - hw, lx + hw, y0, y0 + 0.9f, Math.max(0.6f, cz + zz), cz + zz + 2f, ((int) zz & 3) == 0 ? JADE_LIGHT : JADE});
                }
            }
        }
        return out;
    }

    /**
     * The Jade Dye's paint, rasterised as the door is: before the door opens all of it, after the door's face; once it
     * is open only what is off the door, under it (the door's own goes into the door's raster and swings with it). Then,
     * after the door, everything flying - and the build-up's light.
     */
    private static void paint(ChopScene.Canvas c, NucleusTimeline tl, long t, boolean afterDoor, boolean opened, int w, int h, float master) {
        if (tl.tier() != NucleusTimeline.Tier.JADE) return;
        if (afterDoor != opened) paintStill(c, tl, t, !opened, w, h, master);
        if (afterDoor) {
            splash(c, tl, t, w, h, master);
            jadeLight(c, tl, t, w, h);
        }
    }

    private static void paintStill(ChopScene.Canvas c, NucleusTimeline tl, long t, boolean withDoor, int w, int h, float master) {
        float p = tl.paint(t);
        if (p <= 0f) return;
        boolean still = p >= 1f;
        int key = withDoor ? 1 : 2;
        if (still && paintStillKey == key && PAINT_STILL.holds(tl, w, h)) {
            PAINT_STILL.draw(c, "nucleus_paint_still", PAINT, master);
            return;
        }
        List<float[]> boxes = paintBoxes(p, false);
        if (withDoor) boxes.addAll(paintBoxes(p, true));
        float[] id = NucleusModel.identity(1f, 0f, 0f, 0f);
        NucleusModel.Cam cam = NucleusModel.Cam.current();
        float[] acc = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (float[] b : boxes) NucleusModel.bounds(id, b[0], b[1], b[2], b[3], b[4], b[5], acc, cam);
        if (!PAINT.begin(Math.max(0f, acc[0] - 2f), Math.max(0f, acc[1] - 2f), Math.min(w, acc[2] + 2f), Math.min(h, acc[3] + 2f), still ? rasterScale : 1)) return;
        for (float[] b : boxes) NucleusModel.solid(PAINT, PAINT_SLOT, id, b[0], b[1], b[2], b[3], b[4], b[5], (int) b[6], 0, cam);
        if (still) {
            PAINT_STILL.take(tl, w, h);
            paintStillKey = key;
            PAINT_STILL.draw(c, "nucleus_paint_still", PAINT, master);
            return;
        }
        PAINT.fade(master);
        c.image("nucleus_paint", PAINT.px, PAINT.w, PAINT.h, PAINT.x, PAINT.y, PAINT.w / PAINT.scale, PAINT.h / PAINT.scale);
    }

    /** One piece of flying paint, {@code s} seconds after it left: {x, y, z, half, rgb, spin, tilt, splat (1)}. */
    private static void flung(List<float[]> out, float x0, float y0, float z0, float vx, float vy, float vz, float half, int rgb, float spin, float s) {
        float g = 260f, floor = NucleusModel.FLOOR_Y + half;
        float y = y0 + vy * s - 0.5f * g * s * s;
        if (y > floor) {
            out.add(new float[]{x0 + vx * s, y, z0 + vz * s, half, rgb, spin * s, spin * 0.7f * s, 0f});
            return;
        }
        // Down: a splat where it came down (unless that was past the front of the floor - it flew past the camera).
        float a = -0.5f * g, b = vy, c = y0 - floor;
        float at = (-b - (float) Math.sqrt(Math.max(0f, b * b - 4f * a * c))) / (2f * a);
        float zl = z0 + vz * at;
        if (zl < 64f) out.add(new float[]{x0 + vx * at, NucleusModel.FLOOR_Y, zl, half * 1.5f, rgb, 0f, 0f, 1f});
    }

    static final int BLOBS = 64;

    /** Everything flying: the jets on the beats, the burst's blobs, the drips off the opening door, the fountain. */
    private static List<float[]> jadeFlying(NucleusTimeline tl, long t) {
        List<float[]> out = new ArrayList<>();
        long r0 = NucleusTimeline.REVEAL;
        // Jets out of the seam on every beat after the first: a squirt of drops, two places a beat.
        for (int k = 1; k < NucleusTimeline.JADE_BEATS.length; k++) {
            long b = r0 + NucleusTimeline.JADE_BEATS[k];
            for (int j = 0; j < 2; j++) {
                double a = SlayerFx.rnd(83, k * 2 + j, 1) * Math.PI * 2;
                float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                for (int d = 0; d < 9; d++) {
                    float s = (t - b - d * 22L) / 1000f;
                    if (s < 0f || s > 1.6f) continue;
                    int seed = k * 20 + j * 9 + d;
                    float sp = 40f + 35f * SlayerFx.rnd(84, seed, 1);
                    flung(out, SEAM_R * ca, SEAM_R * sa, 8f, ca * sp, sa * sp + 25f, 18f + 30f * SlayerFx.rnd(84, seed, 2),
                            0.55f + 0.45f * SlayerFx.rnd(84, seed, 3), d % 3 == 0 ? JADE_LIGHT : JADE, 9f, s);
                }
            }
        }
        // The burst: blobs blasted out all round and at the camera, tumbling; splats where they come down.
        long sb = tl.sinceBurst(t);
        if (sb >= 0) {
            float s = sb / 1000f;
            for (int i = 0; i < BLOBS; i++) {
                double a = (i + 0.5) * Math.PI * 2 / BLOBS + 0.3 * (SlayerFx.rnd(81, i, 1) - 0.5);
                float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                float sp = 45f + 95f * SlayerFx.rnd(81, i, 2), at = SlayerFx.rnd(81, i, 3);
                flung(out, SEAM_R * ca, SEAM_R * sa, 8f, ca * sp, sa * sp + 45f, 15f + 170f * at * at,
                        0.6f + 1.3f * SlayerFx.rnd(81, i, 4) * SlayerFx.rnd(81, i, 4) + 0.4f * SlayerFx.rnd(81, i, 6), i % 4 == 0 ? JADE_LIGHT : JADE, 4f + 8f * SlayerFx.rnd(81, i, 5), s);
            }
        }
        // Drops off the swinging door's bottom edge.
        if (tl.door(t) > 0.01f) {
            for (int i = 0; i < 8; i++) {
                for (int rep = 0; rep < 3; rep++) {
                    long born = tl.open() + 140L * i + rep * 460L;
                    float s = (t - born) / 1000f;
                    if (s < 0f || s > 0.9f) continue;
                    float bx = -22f + 6.3f * i, by = -(float) Math.sqrt(Math.max(0, 32 * 32 - bx * bx));
                    float[] wp = point(doorPose(tl, born), bx, by, 7.8f);
                    flung(out, wp[0], wp[1], wp[2], 0f, -10f, 0f, 0.7f, JADE_LIGHT, 0f, s);
                }
            }
        }
        // The fountain the dye rides up on: a column of it out of the floor in front of the door, falling back into
        // a pool at its foot.
        long f0 = tl.open() + 200, stop = tl.showAt() + 1700;
        if (t > f0) {
            float env = clamp01((t - f0) / 260f) * (1f - clamp01((t - stop) / 700f));
            float rad = 6f + 8f * env;
            for (float zz = -rad; zz < rad; zz += 2f) {
                float mid = zz + 1f, hw = rad * (float) Math.sqrt(Math.max(0f, 1f - mid * mid / (rad * rad)));
                if (hw < 0.7f) continue;
                out.add(new float[]{0f, NucleusModel.FLOOR_Y, FOUNT_Z + mid, hw, ((int) zz & 3) == 0 ? JADE_LIGHT : JADE, 0f, 0f, 2f, 1f});
            }
            for (int i = 0; i < 72; i++) {
                float life = 0.95f + 0.3f * SlayerFx.rnd(86, i, 1);
                float age = ((t - f0) / 1000f + SlayerFx.rnd(86, i, 2) * life) % life;
                long born = t - (long) (age * 1000f);
                if (born < f0 || born > stop) continue;
                double a = SlayerFx.rnd(86, i, 3) * Math.PI * 2;
                float spread = 4f + 9f * SlayerFx.rnd(86, i, 4);
                flung(out, (float) Math.cos(a) * 1.5f, NucleusModel.FLOOR_Y + 1f, FOUNT_Z + (float) Math.sin(a) * 1.5f,
                        (float) Math.cos(a) * spread, 150f + 42f * SlayerFx.rnd(86, i, 5), (float) Math.sin(a) * spread * 0.6f,
                        0.7f + 1.0f * SlayerFx.rnd(86, i, 6), i % 4 == 0 ? JADE_LIGHT : JADE, 6f, age);
            }
        }
        return out;
    }

    /** The fountain's foot, in front of the door (the dye rises from there to the showcase). */
    static final float FOUNT_Z = 40f;

    private static void splash(ChopScene.Canvas c, NucleusTimeline tl, long t, int w, int h, float master) {
        List<float[]> parts = jadeFlying(tl, t);
        if (parts.isEmpty()) return;
        NucleusModel.Cam cam = NucleusModel.Cam.current();
        float[] acc = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        List<float[]> mats = new ArrayList<>(parts.size());
        List<float[]> drawn = new ArrayList<>(parts.size());
        for (float[] q : parts) {
            float[] m;
            float x0, x1, y0, y1, z0, z1;
            if (q[7] >= 2f) {
                // a row of the fountain's pool: {x, y, z, half-width, rgb}
                m = NucleusModel.identity(1f, 0f, 0f, 0f);
                x0 = q[0] - q[3]; x1 = q[0] + q[3]; y0 = q[1]; y1 = q[1] + 1f; z0 = q[2] - 1f; z1 = q[2] + 1f;
            } else if (q[7] >= 1f) {
                m = NucleusModel.identity(1f, 0f, 0f, 0f);
                x0 = q[0] - q[3]; x1 = q[0] + q[3]; y0 = q[1]; y1 = q[1] + 0.5f; z0 = q[2] - q[3]; z1 = q[2] + q[3];
            } else {
                // Coming at the camera it grows, as the crystals do flying in.
                float k = 1f + Math.max(0f, q[2] - 10f) / 80f, hh = q[3] * k;
                m = NucleusModel.moved(NucleusModel.identity(1f, 0f, 0f, 0f), q[0], q[1], q[2]);
                m = NucleusModel.spun(m, 0f, 0f, q[5]);
                m = NucleusModel.hinged(m, 0f, 0f, q[6]);
                x0 = y0 = z0 = -hh;
                x1 = y1 = z1 = hh;
            }
            float[] one = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            NucleusModel.bounds(m, x0, x1, y0, y1, z0, z1, one, cam);
            if (one[2] < 0f || one[0] > w || one[3] < 0f || one[1] > h) continue;
            for (int i = 0; i < 2; i++) acc[i] = Math.min(acc[i], one[i]);
            for (int i = 2; i < 4; i++) acc[i] = Math.max(acc[i], one[i]);
            mats.add(m);
            drawn.add(new float[]{x0, x1, y0, y1, z0, z1, q[4]});
        }
        if (drawn.isEmpty()) return;
        if (!SPLASH.begin(Math.max(0f, acc[0] - 2f), Math.max(0f, acc[1] - 2f), Math.min(w, acc[2] + 2f), Math.min(h, acc[3] + 2f), 1)) return;
        for (int i = 0; i < drawn.size(); i++) {
            float[] b = drawn.get(i);
            NucleusModel.solid(SPLASH, PAINT_SLOT, mats.get(i), b[0], b[1], b[2], b[3], b[4], b[5], (int) b[6], 0, cam);
        }
        SPLASH.fade(master);
        c.image("nucleus_splash", SPLASH.px, SPLASH.w, SPLASH.h, SPLASH.x, SPLASH.y, SPLASH.w / SPLASH.scale, SPLASH.h / SPLASH.scale);
    }

    /** The cracks of jade light across the door, out of the seam towards its middle: polylines, a step out each beat. */
    static final int CRACKS = 9;
    private static final float[][][] CRACK = crackLines();

    private static float[][][] crackLines() {
        float[][][] out = new float[CRACKS][][];
        for (int k = 0; k < CRACKS; k++) {
            double a = (k + 0.3) * Math.PI * 2 / CRACKS;
            float x = 31.5f * (float) Math.cos(a), y = 31.5f * (float) Math.sin(a);
            float[][] p = new float[7][];
            for (int j = 0; j < p.length; j++) {
                p[j] = new float[]{x, y};
                double dir = Math.atan2(-y, -x) + (SlayerFx.rnd(89, k * 10 + j, 1) - 0.5) * 1.4;
                float step = 2.6f + 1.8f * SlayerFx.rnd(89, k * 10 + j, 2);
                x += (float) Math.cos(dir) * step;
                y += (float) Math.sin(dir) * step;
            }
            out[k] = p;
        }
        return out;
    }

    /**
     * The build-up's light: on every beat the cracks spread a step further over the door's face, the seam flares and
     * a ring runs out over the rock, a glow thumps at the middle and the screen's edges pulse jade. Then the burst's: a
     * flash where it happens, rings out, a spray of light.
     */
    private static void jadeLight(ChopScene.Canvas c, NucleusTimeline tl, long t, int w, int h) {
        float cracks = tl.cracks(t), beat = tl.jadeBeat(t);
        long sb = tl.sinceBurst(t);
        float fade = sb < 0 ? 1f : 1f - clamp01(sb / 450f);
        if (cracks > 0f && fade > 0f && t < tl.open()) {
            int core = ChopScene.mix(JADE_LIGHT, 0xFFFFFF, 0.25f + 0.5f * beat);
            for (int k = 0; k < CRACKS; k++) {
                float[][] pts = CRACK[k];
                float upto = (pts.length - 1) * clamp01(cracks * (0.7f + 0.3f * SlayerFx.rnd(90, k, 1)) * 1.15f);
                for (int j = 0; j < pts.length - 1 && j < upto; j++) {
                    float[] a = pts[j], b = pts[j + 1];
                    float segLen = (float) Math.hypot(b[0] - a[0], b[1] - a[1]), part = Math.min(1f, upto - j);
                    int n = Math.max(1, Math.round(segLen * part / 1.1f));
                    for (int i = 0; i <= n; i++) {
                        float u = i / (float) n * part;
                        float[] q = at(a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u, 7.6f);
                        SlayerFx.square(c, q[0], q[1], 2, SlayerFx.a(fade * (0.22f + 0.25f * beat)) | JADE);
                        SlayerFx.square(c, q[0], q[1], 1, SlayerFx.a(fade * (0.8f + 0.2f * beat)) | core);
                    }
                }
            }
        }
        for (int i = 0; i < NucleusTimeline.JADE_BEATS.length; i++) {
            long d = t - NucleusTimeline.REVEAL - NucleusTimeline.JADE_BEATS[i];
            if (d < 0 || d > 440) continue;
            float u = d / 440f;
            ringAt(c, 0f, 0f, SEAM_R + 3f + (40f + 14f * i) * (float) Math.sqrt(u), 7f, SlayerFx.a((1f - u) * 0.75f) | JADE_LIGHT);
            if (d < 220) {
                float[] o = at(0f, 0f, 8f);
                glow(c, o[0], o[1], ChopScene.size * (16f + 4f * i), JADE, 0.45f * (1f - d / 220f));
            }
        }
        if (beat > 0.02f) SlayerFx.edgeBands(c, w, h, JADE, 0.2f * beat, 4);
        if (sb >= 0 && sb < 900) {
            float[] o = at(0f, 0f, 9f);
            if (sb < 320) glow(c, o[0], o[1], ChopScene.size * 44f, JADE_LIGHT, 1f - sb / 320f);
            for (int r = 0; r < 3; r++) {
                long e = sb - r * 110L;
                if (e < 0 || e > 620) continue;
                float u = e / 620f;
                ringAt(c, 0f, 0f, SEAM_R + 4f + 120f * (1f - (1f - u) * (1f - u)), 8f, SlayerFx.a(0.9f * (1f - u)) | (r == 1 ? 0xFFFFFF : JADE_LIGHT));
            }
            SlayerFx.burst(c, o[0], o[1], sb, 900, 54, ChopScene.size * 120f, 87, new int[]{JADE_LIGHT, 0xFFFFFF, JADE}, 1f, ChopScene.size * 90f);
            if (sb < 600) SlayerFx.edgeBands(c, w, h, JADE_LIGHT, 0.38f * (1f - sb / 600f), 5);
        }
    }

    /** The claw's three swipes, from inside: each three parallel marks, {from x, y, to x, y}, on the door's face. */
    static final float[][] SWIPES = {{-24, 24, 22, -20}, {24, 22, -20, -24}, {-28, 2, 28, -4}};
    static final float CLAW_SPACING = 6f;
    static final int TORN = 0x4A4640;

    /**
     * The Quick Claw raking the door from inside (the user's pick), in 3D as the door is: each swipe tears three gouges
     * across its face - the metal torn up either side in raised, ragged lips, a slit of gold light between them - and
     * throws shards of the door out at the camera; sparks off the claws' points as they go; the light through the slits
     * breathing until the door falls apart.
     */
    private static void clawMarks(ChopScene.Canvas c, NucleusTimeline tl, long t, int w, int h, float master) {
        if (tl.tier() != NucleusTimeline.Tier.CLAW || t >= tl.open() + 60) return;
        int gold = NucleusTimeline.CLAW_COLOUR, hot = ChopScene.mix(gold, 0xFFFFFF, 0.6f);
        float breath = 0.75f + 0.25f * (float) Math.sin(t / 90.0);
        int slitAdd = Math.round(0xE0 * breath) << 24 | hot, edgeAdd = Math.round(0x90 * breath) << 24 | gold,
                lipAdd = Math.round(0x38 * breath) << 24 | gold;
        List<float[]> boxes = new ArrayList<>();         // {x0, x1, y0, y1, z0, z1, rgb, 0 the core / 2 the gouge / 1 a lip}
        List<float[]> shards = new ArrayList<>();        // {x, y, z, half, spin, tilt}
        for (int k = 0; k < SWIPES.length; k++) {
            float u = tl.slash(k, t);
            if (u < 0f) continue;
            float[] sw = SWIPES[k];
            float dx = sw[2] - sw[0], dy = sw[3] - sw[1], len = (float) Math.hypot(dx, dy);
            float nx = -dy / len, ny = dx / len;
            for (int m = -1; m <= 1; m++) {
                float ox = nx * CLAW_SPACING * m, oy = ny * CLAW_SPACING * m;
                int n = Math.round(len * u / 1.2f);
                for (int i = 0; i <= n; i++) {
                    float f = i / (len / 1.2f);
                    float taper = (float) Math.sin(Math.PI * Math.min(1f, f));
                    float x = sw[0] + dx * f + ox, y = sw[1] + dy * f + oy;
                    if (Math.hypot(x, y) > 31.5f) continue;
                    // The gouge: gold light at its edges, white-hot down its middle, a little below the face.
                    float hw = 0.6f + 0.75f * taper;
                    boxes.add(new float[]{x - hw, x + hw, y - hw, y + hw, 7.15f, 7.45f, gold, 2f});
                    float core = hw * 0.45f;
                    boxes.add(new float[]{x - core, x + core, y - core, y + core, 7.15f, 7.5f, hot, 0f});
                    // Its torn lips, raised and ragged either side: the one towards the light catching it, the other in
                    // shadow - the bevel that reads as a cut into the metal even seen face on.
                    for (int side = -1; side <= 1; side += 2) {
                        float lx = x + nx * (hw + 0.6f) * side, ly = y + ny * (hw + 0.6f) * side;
                        float lh = 0.5f + 1.5f * taper * (0.4f + 0.6f * SlayerFx.rnd(97, k * 997 + m * 131 + i * 2 + side, 1));
                        boolean lit = (nx * side * -0.4f + ny * side * 0.8f) > 0f;
                        boxes.add(new float[]{lx - 0.7f, lx + 0.7f, ly - 0.7f, ly + 0.7f, 7.4f, 7.4f + lh, lit ? 0xA8A296 : 0x24201C, 1f});
                    }
                }
                // Sparks off this claw's point while it tears.
                if (u < 1f) {
                    float[] q = at(sw[0] + dx * u + ox, sw[1] + dy * u + oy, 8f);
                    long d = t - NucleusTimeline.REVEAL - NucleusTimeline.SLASH[k];
                    SlayerFx.burst(c, q[0], q[1], d % 160, 160, 5, ChopScene.size * 26f, 960L + k * 7 + m, new int[]{hot, 0xFFFFFF, gold}, 1f, ChopScene.size * 60f);
                }
            }
            // Shards of the door, torn off along the cut as it goes, flung out at the camera and falling.
            for (int j = 0; j < 12; j++) {
                float f = (j + 0.5f) / 12f;
                long born = NucleusTimeline.REVEAL + NucleusTimeline.SLASH[k] + Math.round(f * NucleusTimeline.SLASH_MS);
                float s = (t - born) / 1000f;
                if (s < 0f || s > 1.3f) continue;
                int seed = k * 40 + j;
                float x0 = sw[0] + dx * f + nx * CLAW_SPACING * (j % 3 - 1), y0 = sw[1] + dy * f + ny * CLAW_SPACING * (j % 3 - 1);
                float vx = (SlayerFx.rnd(98, seed, 1) - 0.5f) * 70f, vy = 20f + 40f * SlayerFx.rnd(98, seed, 2), vz = 50f + 80f * SlayerFx.rnd(98, seed, 3);
                float y = y0 + vy * s - 150f * s * s;
                if (y < NucleusModel.FLOOR_Y) continue;
                shards.add(new float[]{x0 + vx * s, y, 8f + vz * s, 0.5f + 0.6f * SlayerFx.rnd(98, seed, 4), 9f * s, 6f * s});
            }
            // The light through the slits.
            float[] mid = at(sw[0] + dx * 0.5f * u, sw[1] + dy * 0.5f * u, 8f);
            glow(c, mid[0], mid[1], ChopScene.size * (8f + 6f * u), gold, 0.5f * breath);
        }
        if (boxes.isEmpty()) return;
        NucleusModel.Cam cam = NucleusModel.Cam.current();
        float[] id = NucleusModel.identity(1f, 0f, 0f, 0f);
        float[] acc = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (float[] b : boxes) NucleusModel.bounds(id, b[0], b[1], b[2], b[3], b[4], b[5], acc, cam);
        List<float[]> mats = new ArrayList<>();
        for (float[] q : shards) {
            float k = 1f + Math.max(0f, q[2] - 10f) / 55f, hh = q[3] * k;
            float[] m = NucleusModel.moved(id, q[0], q[1], q[2]);
            m = NucleusModel.hinged(NucleusModel.spun(m, 0f, 0f, q[4]), 0f, 0f, q[5]);
            float[] one = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            NucleusModel.bounds(m, -hh, hh, -hh * 0.4f, hh * 0.4f, -hh, hh, one, cam);
            if (one[2] < 0f || one[0] > w || one[3] < 0f || one[1] > h) continue;
            for (int i = 0; i < 2; i++) acc[i] = Math.min(acc[i], one[i]);
            for (int i = 2; i < 4; i++) acc[i] = Math.max(acc[i], one[i]);
            mats.add(new float[]{hh});
            mats.add(m);
        }
        if (!SPLASH.begin(Math.max(0f, acc[0] - 2f), Math.max(0f, acc[1] - 2f), Math.min(w, acc[2] + 2f), Math.min(h, acc[3] + 2f), 1)) return;
        for (float[] b : boxes) NucleusModel.solid(SPLASH, PAINT_SLOT, id, b[0], b[1], b[2], b[3], b[4], b[5], (int) b[6], b[7] == 0f ? slitAdd : b[7] == 2f ? edgeAdd : lipAdd, cam);
        for (int i = 0; i < mats.size(); i += 2) {
            float hh = mats.get(i)[0];
            NucleusModel.solid(SPLASH, PAINT_SLOT, mats.get(i + 1), -hh, hh, -hh * 0.4f, hh * 0.4f, -hh, hh, 0x5A5650, 0x40 << 24 | gold, cam);
        }
        SPLASH.fade(master);
        c.image("nucleus_claw", SPLASH.px, SPLASH.w, SPLASH.h, SPLASH.x, SPLASH.y, SPLASH.w / SPLASH.scale, SPLASH.h / SPLASH.scale);
    }

    /** Which of the four pieces the claw's first two swipes cut the door into a point is in. */
    static int piece(float x, float y) {
        int a = side(SWIPES[0], x, y), b = side(SWIPES[1], x, y);
        return a * 2 + b;
    }

    private static int side(float[] sw, float x, float y) {
        float cross = (sw[2] - sw[0]) * (y - sw[1]) - (sw[3] - sw[1]) * (x - sw[0]);
        return cross >= 0f ? 1 : 0;
    }

    static final long PIECE_GAP = 70;
    private static float[][] pieceLows;

    /** Each piece's lowest point and its middle's x: what it tips over on, and which way it slides. */
    private static float[][] pieceLows() {
        if (pieceLows != null) return pieceLows;
        float[][] out = new float[4][];
        for (int p = 0; p < 4; p++) out[p] = new float[]{Float.MAX_VALUE, 0f, 0f};
        for (NucleusModel.Box b : doorBoxes()) {
            float cx = (b.x0() + b.x1()) / 2f, cy = (b.y0() + b.y1()) / 2f;
            int p = piece(cx, cy);
            out[p][0] = Math.min(out[p][0], b.y0());
            out[p][1] += cx;
            out[p][2] += 1f;
        }
        for (float[] o : out) {
            if (o[2] > 0) o[1] /= o[2];
            if (o[0] == Float.MAX_VALUE) o[0] = 0f;
        }
        pieceLows = out;
        return out;
    }

    /**
     * The cut door falling apart: each piece tips forward over its lowest edge and drops to the floor, sliding a little
     * outward, one after another (the highest first), with a knock as it lands.
     */
    private static float[][] piecePoses(NucleusTimeline tl, long t) {
        float[][] lows = pieceLows();
        float[][] out = new float[4][];
        Integer[] order = {0, 1, 2, 3};
        java.util.Arrays.sort(order, (a, b) -> Float.compare(lows[b][0], lows[a][0]));
        for (int k = 0; k < 4; k++) {
            int p = order[k];
            float u = tl.fall(t, k * PIECE_GAP);
            float e = u * u;
            float yl = lows[p][0];
            float drop = (NucleusModel.FLOOR_Y + 1.5f - yl) * e;
            float angle = (float) (Math.PI / 2) * e;
            if (u >= 1f) {
                long d = t - tl.open() - k * PIECE_GAP - NucleusTimeline.FALL_MS;
                angle -= 0.07f * (float) Math.exp(-d / 120.0) * (float) Math.abs(Math.sin(d / 45.0));
            }
            float slide = Math.signum(lows[p][1]) * 7f * u;
            float[] m = NucleusModel.moved(NucleusModel.identity(1f, 0f, 0f, 0f), slide, yl + drop, 7f + 6f * u);
            m = NucleusModel.tilted(m, angle);
            out[p] = NucleusModel.moved(m, 0f, -yl, -7f);
        }
        return out;
    }

    // ------------------------------------------------------------------ the ending

    /**
     * A per-frame raster that has stopped changing - the door once it has settled, the pile once the last item lies
     * still - kept and drawn as a cached image from then on: the camera has stopped by then too, so it is the same
     * picture. One per raster; it belongs to one run and one screen size.
     */
    private static final class Frozen {
        NucleusTimeline tl;
        int w, h, version;
        /** The camera it was taken at: it is drawn moved and scaled with the camera since, as the cached rock is. */
        float refSize, refX, refY;

        boolean holds(NucleusTimeline tl, int w, int h) {
            return this.tl == tl && this.w == w && this.h == h;
        }

        void take(NucleusTimeline tl, int w, int h) {
            this.tl = tl;
            this.w = w;
            this.h = h;
            refSize = ChopScene.size;
            refX = ChopScene.sx0;
            refY = ChopScene.sy0;
            version++;
        }

        void draw(ChopScene.Canvas c, String key, ModelRaster r, float master) {
            float k = ChopScene.size / refSize;
            c.imageCached(key, r.px, r.w, r.h, version, ChopScene.sx0 + (r.x - refX) * k, ChopScene.sy0 + (r.y - refY) * k,
                    r.w / r.scale * k, r.h / r.scale * k, master);
        }
    }

    private static final Frozen DOOR_STILL = new Frozen(), ITEMS_STILL = new Frozen(), KEEPERS_STILL = new Frozen();

    /** When the camera and the door have both stopped: the camera's ease ends 900 ms after the door starts. */
    private static long doorStill(NucleusTimeline tl) {
        NucleusTimeline.Tier tier = tl.tier();
        long settle = tier == NucleusTimeline.Tier.PLAIN || tier == NucleusTimeline.Tier.JADE ? NucleusTimeline.SWING_MS + 700
                : tier == NucleusTimeline.Tier.CLAW ? 3 * PIECE_GAP + NucleusTimeline.FALL_MS + 500 : NucleusTimeline.BLOW_MS + 900;
        return tl.open() + Math.max(950, settle);
    }

    /** When the pile has stopped: the last item landed and done bouncing, and a plain haul's best gone off the top. */
    private static long itemsStill(NucleusTimeline tl) {
        int n = tl.pile().size();
        long landed = n == 0 ? tl.open() : tl.spillAt(n - 1) + NucleusTimeline.ITEM_FLIGHT + 450;
        return Math.max(Math.max(landed, tl.showAt() + 1), tl.open() + 950);
    }

    private static List<NucleusModel.Box> doorBoxes;

    /** Everything that swings with the door: its body and housings, the sockets, the dial and the bolts. */
    private static List<NucleusModel.Box> doorBoxes() {
        if (doorBoxes == null) {
            List<NucleusModel.Box> out = new ArrayList<>();
            for (NucleusModel.Box b : NucleusModel.BOXES) {
                String p = b.part();
                if (p.equals("Door") || p.equals("Dial") || p.startsWith("Socket") || p.startsWith("Bolt")) out.add(b);
            }
            doorBoxes = out;
        }
        return doorBoxes;
    }

    /** The door's pose now: turned about its hinge, towards the camera. */
    private static float[] doorPose(NucleusTimeline tl, long t) {
        return NucleusModel.hinged(NucleusModel.identity(1f, 0f, 0f, 0f), HINGE_X, HINGE_Z, tl.door(t));
    }

    /** The keepers on the frame, which stay when the door goes. */
    private static void keepers(ChopScene.Canvas c, NucleusTimeline tl, long t, int w, int h, float master) {
        boolean still = t >= tl.open() + 950;
        if (still && KEEPERS_STILL.holds(tl, w, h)) {
            KEEPERS_STILL.draw(c, "nucleus_keepers_still", MOVING, master);
            return;
        }
        float[] id = NucleusModel.identity(1f, 0f, 0f, 0f);
        NucleusModel.Cam cam = NucleusModel.Cam.current();
        List<NucleusModel.Box> boxes = new ArrayList<>();
        float[] acc = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (NucleusModel.Box b : NucleusModel.BOXES) {
            if (!b.part().equals("Static") || !b.kind().startsWith("keeper")) continue;
            boxes.add(b);
            NucleusModel.bounds(b, id, acc, cam);
        }
        if (boxes.isEmpty()) return;
        if (!MOVING.begin(Math.max(0f, acc[0] - 2f), Math.max(0f, acc[1] - 2f), Math.min(w, acc[2] + 2f), Math.min(h, acc[3] + 2f), rasterScale)) return;
        for (NucleusModel.Box b : boxes) NucleusModel.box(MOVING, b, id, 0, cam);
        if (still) {
            KEEPERS_STILL.take(tl, w, h);
            KEEPERS_STILL.draw(c, "nucleus_keepers_still", MOVING, master);
            return;
        }
        MOVING.fade(master);
        c.image("nucleus_moving", MOVING.px, MOVING.w, MOVING.h, MOVING.x, MOVING.y, MOVING.w / MOVING.scale, MOVING.h / MOVING.scale);
    }

    /**
     * The open door, every frame it is open: all of it on its hinge - the bolts pulled, the dial where it stopped,
     * the crystals still in their sockets - outlined, since it now stands out in front of the rock.
     */
    private static void door(ChopScene.Canvas c, NucleusTimeline tl, long t, int w, int h, float master) {
        boolean still = t >= doorStill(tl);
        if (still && DOOR_STILL.holds(tl, w, h)) {
            DOOR_STILL.draw(c, "nucleus_door_still", DOOR, master);
            return;
        }
        float[] hinge = doorPose(tl, t);
        boolean claw = tl.tier() == NucleusTimeline.Tier.CLAW;
        float[][] pieces = claw ? piecePoses(tl, t) : null;
        NucleusModel.Cam cam = NucleusModel.Cam.current();
        Crystal[] all = Crystal.values();
        List<NucleusModel.Box> boxes = doorBoxes();
        List<float[]> poses = new ArrayList<>(boxes.size());
        float[] acc = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (NucleusModel.Box b : boxes) {
            float[] base = claw ? pieces[piece((b.x0() + b.x1()) / 2f, (b.y0() + b.y1()) / 2f)] : hinge;
            float[] m = base;
            if (b.part().equals("Dial") && !b.kind().equals("dial")) m = NucleusModel.spun(base, 0f, 0f, NucleusTimeline.dial(t));
            else if (b.part().startsWith("Bolt")) {
                float pull = 0f;
                for (Crystal cr : all) if (boltOf(cr).equals(b.part())) pull = NucleusTimeline.bolt(cr.ordinal(), t);
                NucleusModel.Part p = NucleusModel.PARTS.get(b.part());
                float[] s = p == null || p.slide() == null ? new float[]{0, 0} : p.slide();
                m = NucleusModel.moved(base, -s[0] * BOLT_TRAVEL * pull, -s[1] * BOLT_TRAVEL * pull, 0f);
            }
            poses.add(m);
            NucleusModel.bounds(b, m, acc, cam);
        }
        float[][] holds = new float[all.length][];
        for (Crystal cr : all) {
            float[] s = socket(cr);
            holds[cr.ordinal()] = claw ? pieces[piece(s[0], s[1])] : hinge;
            if (skinned[cr.ordinal()]) NucleusModel.headBounds(lockedPose(cr, holds[cr.ordinal()]), HEAD_HALF, acc, cam);
        }
        float inkW = Math.max(1.2f, ChopScene.size / 1.6f), pad = inkW + 2f;
        // Moving, at one image pixel a GUI pixel (a texel is still three across): a quarter of the work. The still
        // frame that is kept is drawn at full size.
        if (!DOOR.begin(Math.max(0f, acc[0] - pad), Math.max(0f, acc[1] - pad), Math.min(w, acc[2] + pad), Math.min(h, acc[3] + pad), still ? rasterScale : 1)) return;
        for (int i = 0; i < boxes.size(); i++) NucleusModel.box(DOOR, boxes.get(i), poses.get(i), 0, cam);
        if (tl.tier() == NucleusTimeline.Tier.JADE) {
            for (float[] b : paintBoxes(tl.paint(t), true)) NucleusModel.solid(DOOR, PAINT_SLOT, hinge, b[0], b[1], b[2], b[3], b[4], b[5], (int) b[6], 0, cam);
        }
        for (Crystal cr : all) {
            int add = Math.round(0x28 + 0x18 * (float) Math.sin(t / 320.0 + cr.ordinal())) << 24 | cr.colour;
            if (skinned[cr.ordinal()]) {
                NucleusModel.head(DOOR, 1 + cr.ordinal(), lockedPose(cr, holds[cr.ordinal()]), HEAD_HALF, add, cam);
            } else {
                float[] s = socket(cr);
                float[] m = NucleusModel.moved(holds[cr.ordinal()], s[0], s[1], DOOR_FACE - 0.5f);
                for (NucleusModel.Box b : crystal(cr)) NucleusModel.box(DOOR, b, m, 0x60 << 24 | cr.colour, cam);
            }
        }
        DOOR.ink(Math.max(1, Math.round(inkW * DOOR.scale)));
        if (still) {
            DOOR_STILL.take(tl, w, h);
            DOOR_STILL.draw(c, "nucleus_door_still", DOOR, master);
            return;
        }
        DOOR.fade(master);
        c.image("nucleus_door", DOOR.px, DOOR.w, DOOR.h, DOOR.x, DOOR.y, DOOR.w / DOOR.scale, DOOR.h / DOOR.scale);
    }

    /** The light inside the open vault: warm for a plain haul, the drop's colour for a win, with rays out of it. */
    private static void interior(ChopScene.Canvas c, NucleusTimeline tl, long t) {
        float open = clamp01(tl.door(t) / 1.2f);
        if (open <= 0.01f) return;
        boolean win = tl.tier() != NucleusTimeline.Tier.PLAIN;
        int rgb = win ? tl.seamColour(t) : 0xFFE3A8;
        float[] p = at(0f, 0f, 1f);
        glow(c, p[0], p[1], ChopScene.size * 30f, rgb, (win ? 0.75f : 0.5f) * open);
        if (!win) return;
        // A win's light: stepped rays out of the doorway, widening, over the rock.
        int rays = 16;
        for (int i = 0; i < rays; i++) {
            double a = (i + 0.5) * Math.PI * 2 / rays + 0.1 * Math.sin(t / 900.0 + i);
            float flick = 0.7f + 0.3f * (float) Math.sin(t / 130.0 + i * 1.9);
            float len = (40f + 30f * SlayerFx.rnd(81, i, 1)) * open;
            int steps = Math.round(len / 2f);
            for (int s = 0; s < steps; s++) {
                float r = SEAM_R + s * 2f, f = 1f - s / (float) steps;
                float[] q = at(r * (float) Math.cos(a), r * (float) Math.sin(a), 6f);
                SlayerFx.square(c, q[0], q[1], 1 + Math.round(2f * s / steps), SlayerFx.a(0.32f * flick * f) | ChopScene.mix(rgb, 0xFFFFFF, 0.25f * f));
            }
        }
    }

    /**
     * The door going: a plain door kicks dust off its hinge; a win's is blown - a flash in the doorway, two shockwaves
     * out over the rock, stone knocked loose off the frame.
     */
    private static void blow(ChopScene.Canvas c, NucleusTimeline tl, long t) {
        long d = t - tl.open();
        if (d < 0 || d > 1000) return;
        if (tl.tier() == NucleusTimeline.Tier.PLAIN || tl.tier() == NucleusTimeline.Tier.JADE) {
            for (int i = 0; i < 14; i++) {
                long born = 120 + i * 30L;
                long age = d - born;
                if (age < 0 || age > 600) continue;
                float[] p = at(HINGE_X + 2f, -28f + 56f * SlayerFx.rnd(61, i, 1), 9f);
                float k = age / 600f;
                SlayerFx.square(c, p[0] - 10f * ChopScene.size * k * SlayerFx.rnd(61, i, 2), p[1] + 6f * ChopScene.size * k * k,
                        i % 3 == 0 ? 2 : 1, SlayerFx.a(0.6f * (1f - k)) | 0x8A8478);
            }
            return;
        }
        int rgb = tl.seamColour(t);
        float[] p = at(0f, 0f, 8f);
        if (d < 240) glow(c, p[0], p[1], ChopScene.size * 46f, ChopScene.mix(rgb, 0xFFFFFF, 0.35f), 1f - d / 240f);
        for (int wv = 0; wv < 2; wv++) {
            long e = d - wv * 110L;
            if (e < 0 || e > 560) continue;
            float k = e / 560f, r = SEAM_R + 2f + 80f * (1f - (1f - k) * (1f - k));
            ringAt(c, 0f, 0f, r, 6.5f, SlayerFx.a((1f - k) * (wv == 0 ? 0.85f : 0.45f)) | ChopScene.mix(rgb, 0xFFFFFF, wv == 0 ? 0.3f : 0f));
        }
        // Stone off the frame: out from the seam, falling.
        for (int i = 0; i < 34; i++) {
            double a = SlayerFx.rnd(67, i, 1) * Math.PI * 2;
            float v = 0.05f + 0.07f * SlayerFx.rnd(67, i, 2);
            float r = SEAM_R + 3f + v * d;
            float x = r * (float) Math.cos(a), y = r * (float) Math.sin(a) - 0.00006f * d * d;
            float[] q = at(x, y, 8f + 0.02f * d);
            float k = d / 1000f;
            SlayerFx.square(c, q[0], q[1], SlayerFx.rnd(67, i, 3) < 0.3f ? 2 : 1, SlayerFx.a(1f - k) | (i % 3 == 0 ? 0x6E6A62 : 0x8C877D));
        }
    }

    /**
     * Where pile item {@code k} of {@code n} rests: rows on the ledge, the middle of each first, each row a little
     * further back; on a plain haul the last (the best) on top of them all. {x, y, z, yaw}.
     */
    static float[] slot(int k, int n, boolean bestOnTop) {
        int[] rowSize = {5, 4, 2, 1};
        float[][] xs = {{0, -10, 10, -20, 20}, {-5, 5, -15, 15}, {-5, 5}, {0}};
        int others = bestOnTop ? n - 1 : n;
        int r, i;
        if (bestOnTop && k == n - 1) {
            // On top: one row above the highest the others reached.
            int used = 0, row = 0;
            while (row < 3 && used + rowSize[row] < others) used += rowSize[row++];
            r = others == 0 ? 0 : Math.min(3, row + 1);
            i = 0;
        } else {
            r = 0;
            i = k;
            while (r < 3 && i >= rowSize[r]) i -= rowSize[r++];
            i = Math.min(i, rowSize[r] - 1);
        }
        float x = (r == 3 || (bestOnTop && k == n - 1) ? 0f : xs[r][i]) + (SlayerFx.rnd(51, k, 1) - 0.5f) * 3f;
        float y = NucleusModel.FLOOR_Y + ITEM_HALF + r * (2f * ITEM_HALF - 0.6f);
        float z = 34f - r * 2.5f + (SlayerFx.rnd(51, k, 2) - 0.5f) * 4f;
        return new float[]{x, y, z, (SlayerFx.rnd(51, k, 3) - 0.5f) * 1.3f};
    }

    /** Item {@code k}'s pose on its way out of the door to the pile, or at rest there: {x, y, z, yaw, tumble, scale}. */
    private static float[] itemPose(NucleusTimeline tl, int k, int n, long t) {
        float u = tl.itemFlight(k, t);
        float[] to = slot(k, n, tl.tier() == NucleusTimeline.Tier.PLAIN);
        float x0 = -9f + 18f * SlayerFx.rnd(53, k, 1), y0 = -8f + 16f * SlayerFx.rnd(53, k, 2), z0 = 4f;
        float arc = 14f + 9f * SlayerFx.rnd(53, k, 3);
        float x = x0 + (to[0] - x0) * u, z = z0 + (to[2] - z0) * u;
        float y = y0 + (to[1] - y0) * u * u + arc * 4f * u * (1f - u);
        if (u >= 1f) {
            long d = t - tl.spillAt(k) - NucleusTimeline.ITEM_FLIGHT;
            y += 2.4f * (float) Math.exp(-d / 90.0) * (float) Math.abs(Math.sin(d / 38.0));
        }
        float tumble = (1f - u) * (5f + 4f * SlayerFx.rnd(53, k, 4));
        float scale = 0.45f + 0.55f * Math.min(1f, u / 0.3f);
        return new float[]{x, y, z, to[3] + tumble * 0.7f, tumble, scale};
    }

    /** The haul: out of the door one by one, tumbling, onto the pile on the ledge, a puff of dust under each. */
    private static void items(ChopScene.Canvas c, NucleusTimeline tl, long t, int w, int h, float master) {
        boolean still = t >= itemsStill(tl);
        if (still && ITEMS_STILL.holds(tl, w, h)) {
            ITEMS_STILL.draw(c, "nucleus_items_still", ITEMS, master);
            return;
        }
        List<NucleusHaul.Item> pile = tl.pile();
        int n = pile.size();
        boolean plain = tl.tier() == NucleusTimeline.Tier.PLAIN;
        NucleusModel.Cam cam = NucleusModel.Cam.current();
        List<float[]> poses = new ArrayList<>();
        List<int[]> cells = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        float[] acc = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int k = 0; k < n; k++) {
            if (tl.itemFlight(k, t) < 0f) continue;
            // A plain haul's best leaves the pile once it rises to the showcase.
            if (plain && k == n - 1 && tl.showRise(t) >= 0f) continue;
            NucleusHaul.Item it = pile.get(k);
            int[] cell = cellOf(it.id());
            if (cell == null) continue;
            float[] p = itemPose(tl, k, n, t);
            float[] m = NucleusModel.moved(NucleusModel.identity(1f, 0f, 0f, 0f), p[0], p[1], p[2]);
            m = NucleusModel.yawed(m, p[3]);
            m = NucleusModel.tilted(m, p[4]);
            float half = ITEM_HALF * p[5];
            poses.add(new float[]{half});
            poses.add(m);
            cells.add(cell);
            ids.add(it.id());
            NucleusModel.headBounds(m, half * 1.3f, acc, cam);
            // Dust where it lands.
            long landed = t - tl.spillAt(k) - NucleusTimeline.ITEM_FLIGHT;
            if (landed >= 0 && landed < 380) {
                float[] q = at(p[0], NucleusModel.FLOOR_Y + 1f, p[2] + 2f);
                float f = landed / 380f;
                for (int j = 0; j < 4; j++) {
                    float side = j % 2 == 0 ? -1f : 1f;
                    SlayerFx.square(c, q[0] + side * ChopScene.size * (3f + 6f * f) * (0.6f + 0.4f * j / 3f), q[1] - ChopScene.size * 3f * f * (j / 3f),
                            1, SlayerFx.a(0.55f * (1f - f)) | 0x8A8478);
                }
            }
        }
        if (cells.isEmpty()) return;
        float inkW = Math.max(1.2f, ChopScene.size / 1.6f), pad = inkW + 2f;
        if (!ITEMS.begin(Math.max(0f, acc[0] - pad), Math.max(0f, acc[1] - pad), Math.min(w, acc[2] + pad), Math.min(h, acc[3] + pad), still ? rasterScale : 1)) return;
        for (int i = 0; i < cells.size(); i++) drawItem(ITEMS, ids.get(i), cells.get(i), poses.get(2 * i + 1), poses.get(2 * i)[0], 0, cam);
        ITEMS.ink(Math.max(1, Math.round(inkW * ITEMS.scale)));
        if (still) {
            ITEMS_STILL.take(tl, w, h);
            ITEMS_STILL.draw(c, "nucleus_items_still", ITEMS, master);
            return;
        }
        ITEMS.fade(master);
        c.image("nucleus_items", ITEMS.px, ITEMS.w, ITEMS.h, ITEMS.x, ITEMS.y, ITEMS.w / ITEMS.scale, ITEMS.h / ITEMS.scale);
    }

    static void drawItem(ModelRaster r, String id, int[] cell, float[] m, float half, int add, NucleusModel.Cam cam) {
        if (cell[2] == 1) NucleusModel.icon(r, 1, m, half * 2.3f, ICON_PX.get(id), add, cam, cell[0], cell[1], AW, AH);
        else NucleusModel.head(r, 1, m, half, add, cam, cell[0], cell[1], AW, AH);
    }

    /**
     * The showcase: a plain haul's best lifts off the top of the pile; a win's drop floats out of the doorway. It turns
     * slowly in front of the dark opening, in a glow of its colour (a win: rays too), and the words come in under it.
     */
    private static void showcase(ChopScene.Canvas c, NucleusTimeline tl, long t, int w, int h, float master) {
        NucleusHaul.Item it = tl.show();
        float u = tl.showRise(t);
        if (it == null || u < 0f) return;
        int[] cell = cellOf(it.id());
        if (cell == null) return;
        boolean plain = tl.tier() == NucleusTimeline.Tier.PLAIN;
        int n = tl.pile().size();
        float[] from = plain ? slot(n - 1, n, true)
                : tl.tier() == NucleusTimeline.Tier.JADE ? new float[]{0f, NucleusModel.FLOOR_Y + 10f, FOUNT_Z} : new float[]{0f, 0f, 6f};
        float e = 1f - (1f - u) * (1f - u) * (1f - u);
        float bob = 1.2f * (float) Math.sin(t / 520.0) * e;
        float x = from[0] + (SHOW_AT[0] - from[0]) * e, y = from[1] + (SHOW_AT[1] - from[1]) * e + bob, z = from[2] + (SHOW_AT[2] - from[2]) * e;
        float target = plain ? SHOW_HALF : WIN_HALF;
        float half = (plain ? ITEM_HALF : 3f) + (target - (plain ? ITEM_HALF : 3f)) * e;
        int rgb = plain ? it.colour() : tl.seamColour(t);
        float[] p = at(x, y, z);
        glow(c, p[0], p[1], ChopScene.size * half * 2.6f, rgb, (plain ? 0.45f : 0.7f) * e);
        if (!plain) {
            // Rays behind it: wedges of cells, turning slowly.
            int rays = 10;
            for (int i = 0; i < rays; i++) {
                double a = i * Math.PI * 2 / rays + t / 2400.0;
                float len = ChopScene.size * half * (2.6f + 0.6f * (float) Math.sin(t / 300.0 + i)) * e;
                int steps = Math.max(1, Math.round(len / SlayerFx.U));
                for (int s = 0; s < steps; s++) {
                    float r = ChopScene.size * half * 0.9f + s * SlayerFx.U, f = 1f - s / (float) steps;
                    SlayerFx.square(c, p[0] + r * (float) Math.cos(a), p[1] + r * (float) Math.sin(a), 1 + Math.round(2f * s / steps),
                            SlayerFx.a(0.4f * f * e) | ChopScene.mix(rgb, 0xFFFFFF, 0.3f * f));
                }
            }
        }
        if (tl.tier() == NucleusTimeline.Tier.CLAW) clawFlourish(c, tl, t, p, half);
        float[] m = NucleusModel.moved(NucleusModel.identity(1f, 0f, 0f, 0f), x, y, z);
        m = NucleusModel.yawed(m, 0.5f + 0.28f * (float) Math.sin(t / 900.0) + (1f - e) * 5f);
        m = NucleusModel.tilted(m, -0.18f);
        NucleusModel.Cam cam = NucleusModel.Cam.current();
        float[] acc = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        NucleusModel.headBounds(m, half * 1.3f, acc, cam);
        float inkW = Math.max(1.2f, ChopScene.size / 1.4f), pad = inkW + 2f;
        if (!SHOW.begin(Math.max(0f, acc[0] - pad), Math.max(0f, acc[1] - pad), Math.min(w, acc[2] + pad), Math.min(h, acc[3] + pad), rasterScale)) return;
        drawItem(SHOW, it.id(), cell, m, half, plain ? 0 : Math.round(0x30 * e) << 24 | rgb, cam);
        SHOW.ink(Math.max(1, Math.round(inkW * SHOW.scale)));
        SHOW.fade(master);
        c.image("nucleus_show", SHOW.px, SHOW.w, SHOW.h, SHOW.x, SHOW.y, SHOW.w / SHOW.scale, SHOW.h / SHOW.scale);
    }

    /** The Quick Claw arriving: three gold slashes across it, one after another, each a fading arc of squares. */
    private static void clawFlourish(ChopScene.Canvas c, NucleusTimeline tl, long t, float[] p, float half) {
        int gold = NucleusTimeline.CLAW_COLOUR;
        for (int k = 0; k < 3; k++) {
            long d = t - tl.showAt() - 500 - k * 140L;
            if (d < 0 || d > 520) continue;
            float u = clamp01(d / 160f), fade = 1f - clamp01((d - 160) / 360f);
            float r = ChopScene.size * half * 2.2f;
            float off = (k - 1) * ChopScene.size * half * 0.7f;
            int n = 26;
            for (int i = 0; i < n * u; i++) {
                float f = i / (float) n;
                double a = Math.PI * (0.15 + 0.7 * f);
                float x = p[0] + off + r * (float) Math.cos(a) - r * 0.5f, y = p[1] - r * 0.9f + r * 1.4f * f;
                SlayerFx.square(c, x, y, Math.sin(Math.PI * f) > 0.5 ? 2 : 1, SlayerFx.a(fade) | ChopScene.mix(gold, 0xFFFFFF, 0.5f * (1f - f)));
            }
        }
    }

    /** Under the showcase: its name in its rarity's colour, its price counting up, and how much more the haul had. */
    private static void words(ChopScene.Canvas c, NucleusTimeline tl, long t, int h) {
        NucleusHaul.Item it = tl.show();
        if (it == null) return;
        boolean plain = tl.tier() == NucleusTimeline.Tier.PLAIN;
        float[] p = at(SHOW_AT[0], SHOW_AT[1], SHOW_AT[2]);
        float sc = Math.max(1f, Math.min(1.35f, h / 300f));
        float y = p[1] + (plain ? SHOW_HALF : WIN_HALF) * ChopScene.size * 1.45f + 6f * sc;
        float a0 = tl.words(t, 0);
        if (a0 > 0f) c.text(it.name() + (it.count() > 1 ? " x" + it.count() : ""), p[0], y, sc * 1.25f, SlayerFx.a(a0) | it.colour());
        float a1 = tl.words(t, 1);
        if (a1 > 0f && tl.price() >= 0) {
            float k = clamp01((t - tl.textAt() - 180) / 700f);
            long shown = Math.round(tl.price() * (1.0 - Math.pow(1.0 - k, 3)));
            c.text(String.format(java.util.Locale.ROOT, "%,d coins", shown), p[0], y + 13f * sc, sc, SlayerFx.a(a1) | 0xFFAA00);
        }
        int more = tl.more();
        float a2 = tl.words(t, 2);
        String odds = NucleusHaul.odds(it);
        float ly = y + 23f * sc;
        if (a2 > 0f && odds != null) {
            String nth = forgeShow == null ? null : forgeShow.nth;
            c.text(odds + (nth == null ? "" : " \u00b7 " + nth), p[0], ly, sc * 0.85f, SlayerFx.a(a2 * 0.9f) | 0xCCCCCC);
            ly += 10f * sc;
        }
        if (a2 > 0f && more > 0) c.text("+" + more + " more", p[0], ly, sc * 0.8f, SlayerFx.a(a2 * 0.85f) | 0xAAAAAA);
    }

    /** Copies one more of the haul's skins into the atlas, a frame at a time, so none is copied on the frame it shows. */
    private static void warm(NucleusTimeline tl) {
        if (tl.show() != null && !CELLS.containsKey(tl.show().id())) {
            cellOf(tl.show().id());
            return;
        }
        for (NucleusHaul.Item it : tl.pile()) {
            if (!CELLS.containsKey(it.id())) {
                cellOf(it.id());
                return;
            }
        }
    }

    /** The two haul items that are not heads: drawn as the game draws a dropped item, from these vanilla icons. */
    private static String iconOf(String id) {
        return switch (id) {
            case "PICKONIMBUS" -> "diamond_pickaxe";
            case "ENCHANTED_BOOK" -> "enchanted_book";
            default -> null;
        };
    }

    /** An item's place in the atlas {x, y, 1 if an icon}, copied in the first time it is here; null until then. */
    static int[] cellOf(String id) {
        int[] got = CELLS.get(id);
        if (got != null) return got;
        int per = AW / 64;
        if (nextCell >= per * (AH / 64)) return null;
        int ax = (nextCell % per) * 64, ay = (nextCell / per) * 64;
        String icon = iconOf(id);
        if (icon != null) {
            int[] px = icons.apply(icon);
            if (px == null || px.length < 256) return null;
            for (int y = 0; y < 16; y++) System.arraycopy(px, y * 16, ATLAS, (ay + y) * AW + ax, 16);
            ICON_PX.put(id, px);
            got = new int[]{ax, ay, 1};
        } else {
            java.awt.image.BufferedImage img = skins.apply(id);
            if (img == null) return null;
            int iw = Math.min(64, img.getWidth()), ih = Math.min(64, img.getHeight());
            for (int y = 0; y < ih; y++) for (int x = 0; x < iw; x++) ATLAS[(ay + y) * AW + ax + x] = img.getRGB(x, y);
            got = new int[]{ax, ay, 0};
        }
        nextCell++;
        CELLS.put(id, got);
        return got;
    }
}
