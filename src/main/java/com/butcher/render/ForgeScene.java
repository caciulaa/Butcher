package com.butcher.render;

import com.butcher.anim.ForgeTimeline;
import com.butcher.anim.NucleusTimeline.Crystal;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draws a frame of Divan's Alloy's cutscene: the whole forge room, full screen, from a camera that moves (ForgeTimeline).
 *
 * The vault caches its rock as one picture because its camera never turns; this camera turns, so the room is drawn as
 * itself every frame - each block face a textured quad, sorted back to front once a frame. That stays cheap because it
 * is a room of whole blocks: the faces between two blocks are thrown away once (nothing can see them), a face turned away
 * from the camera is skipped, and the wall or ceiling between the camera and the room is cut away (it fades as the camera
 * turns past it, so nothing pops). Blocks are shaded as Minecraft shades them, under a light map: the furnace, the
 * braziers, the molten metal and the ingot - each face's share of each light is worked out once, only the lights'
 * strengths change. Model: {@code assets/butcher/nucleus_forge.json}, from tools/blender/build_forge.py.
 */
public final class ForgeScene {
    private ForgeScene() {}

    // ------------------------------------------------------------------ the model

    record Part(String name, float[] pivot, String parent, float[] inward) {
    }

    record Kind(String tex, int tint, boolean mc) {
    }

    record Box(String part, String kind, float x0, float x1, float y0, float y1, float z0, float z1, float rot, float ox, float oy) {
    }

    static final Map<String, Part> PARTS = new LinkedHashMap<>();
    static final Map<String, Kind> KINDS = new HashMap<>();
    static final List<Box> BOXES = new ArrayList<>();
    /** Every block texture the room uses, by its name ("stone_bricks"). */
    public static final Set<String> TEXTURES = new HashSet<>();
    static final float GX = 8f;
    // Declared before the loader below runs: static fields are made in the order they are written.
    static final List<String> PART_NAMES = new ArrayList<>();
    static final List<Face> STILL = new ArrayList<>();

    /** The lights: the furnace's mouth, the two braziers, the crucible, the ingot (its strength and colour per frame). */
    static final float[][] LIGHTS = {{0, 18, -44, 260}, {-96, 44, 40, 150}, {96, 44, 40, 150}, {-20, 62, 26, 150}, {4, 28, 26, 150}};

    static {
        try (InputStream in = ForgeScene.class.getResourceAsStream("/assets/butcher/nucleus_forge.json")) {
            if (in != null) load(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (Exception e) {
            // No forge: the Alloy plays the win's ending.
        }
    }

    public static boolean ready() {
        return !BOXES.isEmpty();
    }

    private static void load(JsonObject root) {
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("parts").entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            String parent = o.get("parent").isJsonNull() ? null : o.get("parent").getAsString();
            PARTS.put(e.getKey(), new Part(e.getKey(), vec(o.getAsJsonArray("pivot")), parent, o.has("inward") ? vec(o.getAsJsonArray("inward")) : null));
        }
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("kinds").entrySet()) {
            JsonArray a = e.getValue().getAsJsonArray();
            int tint = ch(a.get(1).getAsFloat()) << 16 | ch(a.get(2).getAsFloat()) << 8 | ch(a.get(3).getAsFloat());
            KINDS.put(e.getKey(), new Kind(a.get(0).getAsString(), tint, a.size() > 4 && a.get(4).getAsInt() == 1));
            TEXTURES.add(a.get(0).getAsString());
        }
        for (JsonElement e : root.getAsJsonArray("boxes")) {
            JsonArray a = e.getAsJsonArray();
            float cx = a.get(2).getAsFloat(), cy = a.get(3).getAsFloat(), cz = a.get(4).getAsFloat();
            float hx = a.get(5).getAsFloat(), hy = a.get(6).getAsFloat(), hz = a.get(7).getAsFloat();
            String part = a.get(0).getAsString(), kind = a.get(1).getAsString();
            // Cut at every texture tile's edge (a block's own, for a Minecraft block), so no face runs off its texture.
            Kind k = KINDS.get(kind);
            float gx = k != null && k.mc ? GX : 0f;
            for (float[] xs : cuts(cx - hx + gx, cx + hx + gx))
                for (float[] ys : cuts(cy - hy, cy + hy))
                    for (float[] zs : cuts(cz - hz, cz + hz))
                        BOXES.add(new Box(part, kind, xs[0] - gx, xs[1] - gx, ys[0], ys[1], zs[0], zs[1],
                                a.get(8).getAsFloat(), a.get(9).getAsFloat(), a.get(10).getAsFloat()));
        }
        build();
    }

    private static List<float[]> cuts(float a0, float a1) {
        List<float[]> out = new ArrayList<>();
        float a = a0;
        while (a < a1 - 1e-4f) {
            float next = Math.min(a1, (float) (Math.floor(a / 16f + 1e-4f) + 1) * 16f);
            out.add(new float[]{a, next});
            a = next;
        }
        return out;
    }

    private static float[] vec(JsonArray a) {
        float[] v = new float[a.size()];
        for (int i = 0; i < v.length; i++) v[i] = a.get(i).getAsFloat();
        return v;
    }

    private static int ch(float f) {
        return Math.max(0, Math.min(255, Math.round(f * 255f)));
    }

    /** The parts that never move: the room itself. */
    static boolean still(String part) {
        return part.equals("Static") || PARTS.get(part) != null && PARTS.get(part).inward() != null;
    }

    // ------------------------------------------------------------------ faces

    /**
     * One face: corner, the texture's right and down edges, its normal (world, mod pixels), its texture and texel rect,
     * the part it belongs to, its middle, and its share of each light (worked out once for the still ones).
     */
    static final class Face {
        float ox, oy, oz, ux, uy, uz, vx, vy, vz, nx, ny, nz, mx, my, mz;
        String tex;
        int[] rect;
        int part;           // index into PART_NAMES
        float shade, tintR, tintG, tintB;
        float[] share;      // per light
        boolean mc;
    }


    private static void build() {
        PART_NAMES.addAll(PARTS.keySet());
        // Whole blocks of the room, by cell: a face against another one is never seen.
        Set<Long> solid = new HashSet<>();
        for (Box b : BOXES) {
            if (!still(b.part) || !KINDS.get(b.kind).mc || !whole(b)) continue;
            solid.add(cell(b.x0 + GX, b.y0, b.z0));
        }
        for (Box b : BOXES) {
            if (!still(b.part)) continue;
            // The anvil is vanilla's own model with its own UVs (below): the boxes in the file only stand in for it.
            if (b.kind.equals("anvil") || b.kind.equals("anvil_top")) continue;
            Kind k = KINDS.get(b.kind);
            boolean blk = k.mc && whole(b);
            int ci = Math.round((b.x0 + GX) / 16f), cj = Math.round(b.y0 / 16f), ck = Math.round(b.z0 / 16f);
            for (Face f : faces(b, null)) {
                if (blk && solid.contains(key(ci + Math.round(f.nx), cj + Math.round(f.ny), ck + Math.round(f.nz)))) continue;
                // The room's outside: a wall's or the ceiling's face turned out of the room, the back of the back wall, the
                // floor's underside. Nothing sees them - the wall between the camera and the room is always cut away.
                Part p = PARTS.get(b.part);
                if (p.inward() != null && f.nx * p.inward()[0] + f.ny * p.inward()[1] + f.nz * p.inward()[2] < -0.5f) continue;
                if (b.part.equals("Static") && ((f.nz < -0.5f && b.z0 <= -79f) || (f.ny < -0.5f && b.y0 <= -15f))) continue;
                light(f);
                STILL.add(f);
            }
        }
        anvil();
    }

    // ------------------------------------------------------------------ the anvil

    /** Where the anvil stands and how big (build_forge.py's ANVIL_X, ANVIL_Z, ANVIL_K): vanilla's half as big again. */
    static final float ANVIL_X = 4f, ANVIL_Z = 26f, ANVIL_K = 1.5f;
    private static final int DOWN = 0, UP = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;
    /**
     * Vanilla's anvil, face by face with its own UVs and rotations (the user: it did not look right as boxes tiled with
     * the texture - its top was a thin slab and the faces were cut up at every 16 px). Turned a quarter about y so its
     * length runs across the camera, scaled by ANVIL_K, and shaded as Minecraft shades a block.
     */
    private static void anvil() {
        // (Local: build() runs from the static block above, before any array declared below it is made.)
        // models/block/template_anvil.json (26.2): its four elements, from and to.
        int[][] elements = {{2, 0, 2, 14, 4, 14}, {4, 4, 3, 12, 5, 13}, {6, 5, 4, 10, 10, 12}, {3, 10, 0, 13, 16, 16}};
        // Each element's faces: direction, uv (u0, v0, u1, v1), rotation, 1 for the top texture.
        int[][][] faceList = {
                {{DOWN, 2, 2, 14, 14, 180, 0}, {UP, 2, 2, 14, 14, 180, 0}, {NORTH, 2, 12, 14, 16, 0, 0}, {SOUTH, 2, 12, 14, 16, 0, 0},
                        {WEST, 0, 2, 4, 14, 90, 0}, {EAST, 4, 2, 0, 14, 270, 0}},
                {{UP, 4, 3, 12, 13, 180, 0}, {NORTH, 4, 11, 12, 12, 0, 0}, {SOUTH, 4, 11, 12, 12, 0, 0}, {WEST, 4, 3, 5, 13, 90, 0},
                        {EAST, 5, 3, 4, 13, 270, 0}},
                {{NORTH, 6, 6, 10, 11, 0, 0}, {SOUTH, 6, 6, 10, 11, 0, 0}, {WEST, 5, 4, 10, 12, 90, 0}, {EAST, 10, 4, 5, 12, 270, 0}},
                {{DOWN, 3, 0, 13, 16, 180, 0}, {UP, 3, 0, 13, 16, 180, 1}, {NORTH, 3, 0, 13, 6, 0, 0}, {SOUTH, 3, 0, 13, 6, 0, 0},
                        {WEST, 10, 0, 16, 16, 90, 0}, {EAST, 16, 0, 10, 16, 270, 0}},
        };
        int part = PART_NAMES.indexOf("Static");
        for (int e = 0; e < elements.length; e++) {
            int[] el = elements[e];
            for (int[] fd : faceList[e]) {
                float[][] corner = corners(fd[0], el[0], el[1], el[2], el[3], el[4], el[5]);
                float[][] uv = {{fd[1], fd[2]}, {fd[3], fd[2]}, {fd[3], fd[4]}, {fd[1], fd[4]}};
                int r = fd[5] / 90;
                float umin = Math.min(fd[1], fd[3]), vmin = Math.min(fd[2], fd[4]);
                float umax = Math.max(fd[1], fd[3]), vmax = Math.max(fd[2], fd[4]);
                float[] o = null, ou = null, ov = null;
                for (int i = 0; i < 4; i++) {
                    float[] at = uv[(i - r + 4) % 4];
                    float[] w = anvilWorld(corner[i]);
                    if (at[0] == umin && at[1] == vmin) o = w;
                    else if (at[0] == umax && at[1] == vmin) ou = w;
                    else if (at[0] == umin && at[1] == vmax) ov = w;
                }
                if (o == null || ou == null || ov == null) continue;
                Face f = new Face();
                f.ox = o[0]; f.oy = o[1]; f.oz = o[2];
                f.ux = ou[0] - o[0]; f.uy = ou[1] - o[1]; f.uz = ou[2] - o[2];
                f.vx = ov[0] - o[0]; f.vy = ov[1] - o[1]; f.vz = ov[2] - o[2];
                float[] n = {fd[0] == WEST ? -1 : fd[0] == EAST ? 1 : 0, fd[0] == DOWN ? -1 : fd[0] == UP ? 1 : 0,
                        fd[0] == NORTH ? -1 : fd[0] == SOUTH ? 1 : 0};
                // the quarter turn: model (x, z) -> world (z, -x)
                f.nx = n[2]; f.ny = n[1]; f.nz = -n[0];
                f.mx = f.ox + (f.ux + f.vx) / 2f; f.my = f.oy + (f.uy + f.vy) / 2f; f.mz = f.oz + (f.uz + f.vz) / 2f;
                f.tex = fd[6] == 1 ? "block:anvil_top" : "block:anvil";
                f.rect = new int[]{Math.round(umin), Math.round(vmin), Math.max(1, Math.round(umax - umin)), Math.max(1, Math.round(vmax - vmin))};
                f.part = part;
                f.mc = true;
                f.shade = NucleusModel.shade(f.nx, f.ny, f.nz);
                f.tintR = f.tintG = f.tintB = 1f;
                light(f);
                STILL.add(f);
            }
        }
    }

    /** A face's corners in Minecraft's order - top left, top right, bottom right, bottom left, seen from outside. */
    private static float[][] corners(int dir, float x0, float y0, float z0, float x1, float y1, float z1) {
        return switch (dir) {
            case DOWN -> new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}};
            case UP -> new float[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
            case NORTH -> new float[][]{{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}};
            case SOUTH -> new float[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}};
            case WEST -> new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}};
            default -> new float[][]{{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}};
        };
    }

    /** A point of the anvil's model (0..16) in the forge: turned a quarter, scaled, set on its spot. */
    private static float[] anvilWorld(float[] m) {
        return new float[]{ANVIL_X + (m[2] - 8f) * ANVIL_K, m[1] * ANVIL_K, ANVIL_Z - (m[0] - 8f) * ANVIL_K};
    }

    private static boolean whole(Box b) {
        return Math.abs(b.x1 - b.x0 - 16f) < 0.01f && Math.abs(b.y1 - b.y0 - 16f) < 0.01f && Math.abs(b.z1 - b.z0 - 16f) < 0.01f
                && b.rot == 0f && Math.abs((b.x0 + GX) % 16f) < 0.01f && Math.abs(b.y0 % 16f) < 0.01f && Math.abs(b.z0 % 16f) < 0.01f;
    }

    private static long cell(float x, float y, float z) {
        return key(Math.round(x / 16f), Math.round(y / 16f), Math.round(z / 16f));
    }

    private static long key(int i, int j, int k) {
        return ((long) (i + 512) << 22) | ((long) (j + 512) << 11) | (k + 512);
    }

    private static void light(Face f) {
        f.share = new float[LIGHTS.length];
        for (int i = 0; i < LIGHTS.length; i++) {
            float[] l = LIGHTS[i];
            float dx = f.mx - l[0], dy = f.my - l[1], dz = f.mz - l[2];
            float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float u = Math.max(0f, 1f - d / l[3]);
            // A face turned towards the light takes more of it; one turned away, only a little (light bounces).
            float facing = d < 1e-3f ? 1f : -(dx * f.nx + dy * f.ny + dz * f.nz) / d;
            f.share[i] = u * u * (0.35f + 0.65f * Math.max(0f, facing));
        }
    }

    /**
     * A box's six faces in world space, posed by {@code m} (null: as it is). A Minecraft block's texture starts at its
     * own block's edge (the grid is GX off x 0); a prop's at the world's 16 px grid, as the Blender preview tiled it.
     */
    static List<Face> faces(Box b, float[] m) {
        Kind k = KINDS.get(b.kind);
        List<Face> out = new ArrayList<>(6);
        if (k == null) return out;
        float x0 = b.x0, x1 = b.x1, y0 = b.y0, y1 = b.y1, z0 = b.z0, z1 = b.z1;
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float gx = k.mc ? GX : 0f;
        float cx = (float) Math.floor((x0 + gx + 1e-3f) / 16f) * 16f - gx, cy = (float) Math.floor((y0 + 1e-3f) / 16f) * 16f,
                cz = (float) Math.floor((z0 + 1e-3f) / 16f) * 16f;
        float[][] q = {
                {x0, y1, z1, dx, 0, 0, 0, -dy, 0, 0, 0, 1, x0 - cx, cy + 16 - y1, dx, dy},
                {x1, y1, z0, -dx, 0, 0, 0, -dy, 0, 0, 0, -1, cx + 16 - x1, cy + 16 - y1, dx, dy},
                {x1, y1, z1, 0, 0, -dz, 0, -dy, 0, 1, 0, 0, cz + 16 - z1, cy + 16 - y1, dz, dy},
                {x0, y1, z0, 0, 0, dz, 0, -dy, 0, -1, 0, 0, z0 - cz, cy + 16 - y1, dz, dy},
                {x0, y1, z0, dx, 0, 0, 0, 0, dz, 0, 1, 0, x0 - cx, z0 - cz, dx, dz},
                {x0, y0, z1, dx, 0, 0, 0, 0, -dz, 0, -1, 0, x0 - cx, cz + 16 - z1, dx, dz},
        };
        float c = (float) Math.cos(b.rot), s = (float) Math.sin(b.rot);
        int part = PART_NAMES.indexOf(b.part);
        for (float[] f : q) {
            float[] o = turn(f[0], f[1], f[2], c, s, b.ox, b.oy, true), u = turn(f[3], f[4], f[5], c, s, 0, 0, false),
                    v = turn(f[6], f[7], f[8], c, s, 0, 0, false), n = turn(f[9], f[10], f[11], c, s, 0, 0, false);
            if (m != null) {
                o = apply(m, o, true);
                u = apply(m, u, false);
                v = apply(m, v, false);
                n = apply(m, n, false);
            }
            Face face = new Face();
            face.ox = o[0]; face.oy = o[1]; face.oz = o[2];
            face.ux = u[0]; face.uy = u[1]; face.uz = u[2];
            face.vx = v[0]; face.vy = v[1]; face.vz = v[2];
            float ln = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
            face.nx = n[0] / ln; face.ny = n[1] / ln; face.nz = n[2] / ln;
            face.mx = o[0] + (u[0] + v[0]) / 2f; face.my = o[1] + (u[1] + v[1]) / 2f; face.mz = o[2] + (u[2] + v[2]) / 2f;
            face.tex = "block:" + k.tex;
            face.rect = new int[]{Math.round(f[12]), Math.round(f[13]), Math.max(1, Math.round(f[14])), Math.max(1, Math.round(f[15]))};
            face.part = part;
            face.mc = k.mc;
            face.shade = k.mc ? NucleusModel.shade(face.nx, face.ny, face.nz)
                    : 0.62f + 0.38f * Math.max(0f, face.nx * -0.4f + face.ny * 0.8f + face.nz * 0.45f);
            face.tintR = (k.tint >> 16 & 255) / 255f;
            face.tintG = (k.tint >> 8 & 255) / 255f;
            face.tintB = (k.tint & 255) / 255f;
            out.add(face);
        }
        return out;
    }

    private static float[] turn(float x, float y, float z, float c, float s, float ox, float oy, boolean point) {
        if (point) {
            float dx = x - ox, dy = y - oy;
            return new float[]{ox + dx * c - dy * s, oy + dx * s + dy * c, z};
        }
        return new float[]{x * c - y * s, x * s + y * c, z};
    }

    private static float[] apply(float[] m, float[] p, boolean point) {
        float x = m[0] * p[0] + m[3] * p[1] + m[6] * p[2], y = m[1] * p[0] + m[4] * p[1] + m[7] * p[2],
                z = m[2] * p[0] + m[5] * p[1] + m[8] * p[2];
        if (point) {
            x += m[9];
            y += m[10];
            z += m[11];
        }
        return new float[]{x, y, z};
    }

    // ------------------------------------------------------------------ the frame

    /** Skins for the crystals' heads and the Alloy's, by NEU id: a texture key (null until there). */
    public static java.util.function.Function<String, String> skin = id -> null;

    private static final List<Face> frame = new ArrayList<>(4096);
    private static float[] depth = new float[4096];
    private static Integer[] order = new Integer[0];

    /** Behind the Vault's spotlight: the Alloy's own showcase is not drawn (the spotlight has it), and all of it fades by fadeK. */
    public static boolean spotlight;
    public static float fadeK = 1f;

    public static void draw(ChopScene.Canvas c, ForgeTimeline ignored, long real, int w, int h, Show show) {
        // The scene's clock: the screen's, slowed round the last blow. The handheld drift runs on the screen's.
        long t = ForgeTimeline.scene(real);
        float master = ForgeTimeline.master(t) * fadeK;
        // Full screen: the room covers everything, the dark round it too.
        c.fill(0, 0, w, h, ChopScene.alpha(fadeK) | 0x050407);
        if (BOXES.isEmpty()) return;
        ChopScene.sceneAlpha = master;
        SlayerFx.U = SlayerFx.gridFor(h);

        ForgeTimeline.Cam cam = ForgeTimeline.camera(t, real);
        float shake = ChopScene.calm ? 0f : ForgeTimeline.impact(t) * 3.6f * ChopScene.shakeK;
        float kick = 1f + 0.07f * ForgeTimeline.impact(t);
        camera(w, h, cam, shake * (float) Math.sin(t / 13.0), shake * 0.7f * (float) Math.sin(t / 17.0 + 1.3), kick);

        // The lights' strengths and colours now.
        float fire = ForgeTimeline.fire(t);
        float[][] lights = {
                {1.25f * fire, 1.0f, 0.62f, 0.3f},
                {0.9f * ForgeTimeline.brazier(t, true), 1.0f, 0.55f, 0.25f},
                {0.9f * ForgeTimeline.brazier(t, false), 1.0f, 0.55f, 0.25f},
                molten(t),
                ingotLight(t),
        };
        float flash = ForgeTimeline.flash(t);
        // The room's own light: dim, flaring with each flash, warm and pulsing once the Alloy is out.
        float out = Math.max(0f, ForgeTimeline.lift(t)) * (0.12f + 0.05f * (float) Math.sin(t / 260.0));
        float ambR = 0.2f + 0.2f * flash + out, ambG = 0.19f + 0.18f * flash + out * 0.8f, ambB = 0.24f + 0.12f * flash + out * 0.35f;

        // Each wall's cutaway: drawn when its inside faces the camera, fading out as the camera passes it.
        float tcx = -ChopScene.sy * ChopScene.cp, tcy = -ChopScene.sp, tcz = ChopScene.cy * ChopScene.cp;
        float[] partAlpha = new float[PART_NAMES.size()];
        for (int i = 0; i < partAlpha.length; i++) {
            Part p = PARTS.get(PART_NAMES.get(i));
            if (p.inward() == null) partAlpha[i] = 1f;
            else {
                float dot = p.inward()[0] * tcx + p.inward()[1] * tcy + p.inward()[2] * tcz;
                partAlpha[i] = clamp01((dot - 0.04f) / 0.16f);
            }
        }

        frame.clear();
        for (Face f : STILL) frame.add(f);
        moving(t, frame);
        chains(t, frame);
        int n = frame.size();
        if (depth.length < n) depth = new float[n * 2];
        if (order.length < n) order = new Integer[n * 2];
        int kept = 0;
        for (int i = 0; i < n; i++) {
            Face f = frame.get(i);
            if (partAlpha[f.part] <= 0.01f) continue;
            float facing = -f.ny * ChopScene.sp + (-f.nx * ChopScene.sy + f.nz * ChopScene.cy) * ChopScene.cp;
            if (facing <= 0.002f) continue;
            if (offScreen(f, w, h)) continue;
            depth[i] = depthOf(f.mx, f.my, f.mz);
            order[kept++] = i;
        }
        java.util.Arrays.sort(order, 0, kept, (a, b) -> Float.compare(depth[a], depth[b]));
        for (int k = 0; k < kept; k++) {
            Face f = frame.get(order[k]);
            float r = ambR, g = ambG, bl = ambB;
            float[] share = f.share != null ? f.share : shareOf(f);
            for (int i = 0; i < lights.length; i++) {
                float s = share[i] * lights[i][0];
                r += s * lights[i][1];
                g += s * lights[i][2];
                bl += s * lights[i][3];
            }
            // Light in Minecraft's steps: sixteen levels.
            r = steps(r) * f.shade * f.tintR;
            g = steps(g) * f.shade * f.tintG;
            bl = steps(bl) * f.shade * f.tintB;
            int tint = Math.round(255f * clamp01(partAlpha[f.part] * master)) << 24 | ChopScene.channel(r) << 16 | ChopScene.channel(g) << 8 | ChopScene.channel(bl);
            emit(c, f, tint);
        }
        effects(c, t, w, h, fire, show);
        ChopScene.sceneAlpha = 1f;
    }

    /** Whether a face lies wholly outside the screen (its four corners all past one edge). */
    private static boolean offScreen(Face f, int w, int h) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int k = 0; k < 4; k++) {
            float x = f.ox + ((k & 1) != 0 ? f.ux : 0) + ((k & 2) != 0 ? f.vx : 0);
            float y = f.oy + ((k & 1) != 0 ? f.uy : 0) + ((k & 2) != 0 ? f.vy : 0);
            float z = f.oz + ((k & 1) != 0 ? f.uz : 0) + ((k & 2) != 0 ? f.vz : 0);
            ChopScene.project(x, y, z);
            minX = Math.min(minX, ChopScene.P[0]); maxX = Math.max(maxX, ChopScene.P[0]);
            minY = Math.min(minY, ChopScene.P[1]); maxY = Math.max(maxY, ChopScene.P[1]);
        }
        return maxX < 0 || maxY < 0 || minX > w || minY > h;
    }

    private static float steps(float v) {
        return Math.round(clamp01(v) * 15f) / 15f;
    }

    /** A moving face's share of each light, worked out as it is drawn. */
    private static float[] shareOf(Face f) {
        light(f);
        return f.share;
    }

    private static void emit(ChopScene.Canvas c, Face f, int tint) {
        ChopScene.project(f.ox, f.oy, f.oz);
        float px = ChopScene.P[0], py = ChopScene.P[1];
        ChopScene.project(f.ox + f.ux, f.oy + f.uy, f.oz + f.uz);
        float qx = ChopScene.P[0] - px, qy = ChopScene.P[1] - py;
        ChopScene.project(f.ox + f.vx, f.oy + f.vy, f.oz + f.vz);
        float rx = ChopScene.P[0] - px, ry = ChopScene.P[1] - py;
        ChopScene.emit(c, f.tex, px, py, qx, qy, rx, ry, f.rect, tint);
    }

    private static float depthOf(float x, float y, float z) {
        float rz = -(x - ChopScene.fx) * ChopScene.sy + (z - ChopScene.fz) * ChopScene.cy;
        return -(y - ChopScene.fy) * ChopScene.sp + rz * ChopScene.cp;
    }

    static void camera(int w, int h, ForgeTimeline.Cam cam, float dx, float dy, float kick) {
        double yaw = Math.toRadians(cam.yaw()), pitch = Math.toRadians(cam.pitch());
        ChopScene.cy = (float) Math.cos(yaw);
        ChopScene.sy = (float) Math.sin(yaw);
        ChopScene.cp = (float) Math.cos(pitch);
        ChopScene.sp = (float) Math.sin(pitch);
        ChopScene.size = h / 195f * cam.zoom() * kick;
        ChopScene.fx = cam.x();
        ChopScene.fy = cam.y();
        ChopScene.fz = cam.z();
        ChopScene.sx0 = w / 2f + dx;
        ChopScene.sy0 = h * 0.5f + dy;
        ChopScene.yShift = 0f;
        ChopScene.faceAlpha = 1f;
    }

    // ------------------------------------------------------------------ what moves

    /** The crucible's metal: the crystals' colours mixing as they go in, then white; its light the same. */
    static int moltenColour(long t) {
        Crystal[] all = Crystal.values();
        float r = 1f, g = 0.55f, b = 0.2f, n = 1f;
        for (int k = 0; k < 5; k++) {
            if (ForgeTimeline.drop(k, t) < 1f) continue;
            int c = all[k].colour;
            r += (c >> 16 & 255) / 255f;
            g += (c >> 8 & 255) / 255f;
            b += (c & 255) / 255f;
            n += 1f;
        }
        r /= n; g /= n; b /= n;
        float w = ForgeTimeline.whiteHot(t);
        r += (1f - r) * w; g += (0.97f - g) * w; b += (0.88f - b) * w;
        return ChopScene.channel(r) << 16 | ChopScene.channel(g) << 8 | ChopScene.channel(b);
    }

    private static float[] molten(long t) {
        int c = moltenColour(t);
        float strength = t < ForgeTimeline.DROP_FROM ? 0.35f : 0.6f + 0.6f * ForgeTimeline.whiteHot(t);
        if (t > ForgeTimeline.BACK_TO) strength *= 0.3f;
        return new float[]{strength, (c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f};
    }

    /** The Alloy's own gold (its skin's front) and its top's yellow. */
    static final int ALLOY = 0xE7A42D, ALLOY_TOP = 0xF8EC4E;

    /** The ingot's glow: white from the pour, cooling through orange to the Alloy's own gold. */
    static int ingotColour(long t) {
        float heat = ForgeTimeline.heat(t);
        int cool = ALLOY, warm = 0xFF7A2A, white = 0xFFF6E0;
        return heat > 0.5f ? ChopScene.mix(warm, white, (heat - 0.5f) * 2f) : ChopScene.mix(cool, warm, heat * 2f);
    }

    private static float[] ingotLight(long t) {
        int c = ingotColour(t);
        float s = ForgeTimeline.fill(t) * (0.5f + 0.9f * ForgeTimeline.heat(t)) + (ForgeTimeline.lift(t) >= 0 ? 0.6f : 0f);
        return new float[]{s, (c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f};
    }

    /** The crucible tipping, the arm's pose, the ingot filling: their faces this frame. */
    private static void moving(long t, List<Face> out) {
        float[] id = {1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0};
        Part cru = PARTS.get("Crucible"), sh = PARTS.get("Shoulder"), fa = PARTS.get("Forearm");
        float[] crucible = cru == null ? id : spun(id, cru.pivot()[0], cru.pivot()[1], ForgeTimeline.tip(t));
        // Hoisted off its cradle once it has poured, so the hammer's swing is clear.
        crucible = crucible.clone();
        crucible[10] += ForgeTimeline.hoist(t);
        float[] arm = ForgeTimeline.arm(t);
        float[] shoulder = sh == null ? id : spun(id, sh.pivot()[0], sh.pivot()[1], arm[0]);
        float[] forearm = fa == null ? shoulder : spun(shoulder, fa.pivot()[0], fa.pivot()[1], arm[1]);
        // The Automaton's gears turning - the column's one way, the shoulder's and elbow's the other.
        float g = ForgeTimeline.gears(t);
        float[] gear0 = gear("Gear0", id, g), gear1 = gear("Gear1", shoulder, -g * 1.4f), gear2 = gear("Gear2", forearm, g * 1.8f);
        // The ingot hopping and squashing on each blow, about its base.
        float[] hp = ForgeTimeline.hop(t);
        Part ig = PARTS.get("Ingot");
        float[] ingot = id;
        if (ig != null) {
            float bx = ig.pivot()[0], by = ig.pivot()[1], bz = ig.pivot()[2], sq = hp[1];
            ingot = new float[]{1 + sq * 0.6f, 0, 0, 0, 1 - sq, 0, 0, 0, 1 + sq * 0.6f,
                    bx - bx * (1 + sq * 0.6f), by - by * (1 - sq) + hp[0], bz - bz * (1 + sq * 0.6f)};
        }
        for (Box b : BOXES) {
            if (still(b.part) || b.part.startsWith("Crystal")) continue;
            float[] m = switch (b.part) {
                case "Crucible", "Molten" -> crucible;
                case "Shoulder", "UpperArm" -> shoulder;
                case "Forearm", "Hammer" -> forearm;
                case "Gear0" -> gear0;
                case "Gear1" -> gear1;
                case "Gear2" -> gear2;
                case "Ingot" -> ingot;
                default -> id;
            };
            if (b.part.equals("Ingot") && ForgeTimeline.fill(t) <= 0.02f) continue;
            if (b.part.equals("Ingot") && ForgeTimeline.lift(t) >= 0f) continue;
            for (Face f : faces(b, m)) {
                if (b.part.equals("Molten")) {
                    int mc = moltenColour(t);
                    f.tintR = (mc >> 16 & 255) / 255f * 1.4f; f.tintG = (mc >> 8 & 255) / 255f * 1.4f; f.tintB = (mc & 255) / 255f * 1.4f;
                    f.shade = 1f;
                }
                if (b.part.equals("Ingot")) {
                    int ic = ingotColour(t);
                    f.tintR = (ic >> 16 & 255) / 255f * 1.3f; f.tintG = (ic >> 8 & 255) / 255f * 1.3f; f.tintB = (ic & 255) / 255f * 1.3f;
                    f.shade = 1f;
                }
                if (b.part.equals("Shoulder") && b.kind.equals("eye")) f.shade = 0.3f + 1.2f * ForgeTimeline.eye(t);
                out.add(f);
            }
        }
    }

    /**
     * The two chains that lift the crucible off its cradle: links of dark iron, turned alternately, from its pins up out of
     * the shot - let down onto the pins after the pour, then hoisting with it.
     */
    private static void chains(long t, List<Face> out) {
        float down = ForgeTimeline.chains(t);
        Part cru = PARTS.get("Crucible");
        if (down <= 0f || cru == null) return;
        float cx = cru.pivot()[0], cy = cru.pivot()[1], cz = cru.pivot()[2];
        float bottom = cy - 2f + (1f - down * down) * 140f + ForgeTimeline.hoist(t);
        for (int side = -1; side <= 1; side += 2) {
            float z = cz + side * 11.5f;
            for (int i = 0; i < 44; i++) {
                float y = bottom + i * 3.2f;
                boolean across = (i & 1) == 0;
                Box b = across ? new Box("Static", "iron_dark", cx - 1.6f, cx + 1.6f, y, y + 4f, z - 0.4f, z + 0.4f, 0f, 0f, 0f)
                        : new Box("Static", "iron_dark", cx - 0.4f, cx + 0.4f, y, y + 4f, z - 1.6f, z + 1.6f, 0f, 0f, 0f);
                for (Face f : faces(b, null)) {
                    // a link straddles the 16 px grid now and then: sample a patch well inside the tile
                    f.rect = new int[]{4, 4, Math.min(8, f.rect[2]), Math.min(8, f.rect[3])};
                    out.add(f);
                }
            }
        }
    }

    private static float[] gear(String name, float[] parent, float angle) {
        Part p = PARTS.get(name);
        return p == null ? parent : spun(parent, p.pivot()[0], p.pivot()[1], angle);
    }

    /** {@code m} turned {@code angle} about z through (px, py). */
    static float[] spun(float[] m, float px, float py, float angle) {
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        float[] r = {c, s, 0, -s, c, 0, 0, 0, 1};
        float[] tr = {px - px * c + py * s, py - px * s - py * c, 0};
        float[] o = new float[12];
        for (int col = 0; col < 3; col++)
            for (int row = 0; row < 3; row++)
                o[col * 3 + row] = m[row] * r[col * 3] + m[3 + row] * r[col * 3 + 1] + m[6 + row] * r[col * 3 + 2];
        for (int row = 0; row < 3; row++) o[9 + row] = m[row] * tr[0] + m[3 + row] * tr[1] + m[6 + row] * tr[2] + m[9 + row];
        return o;
    }

    // ------------------------------------------------------------------ the effects, the crystals, the showcase

    /** What the screen fills in: the price, the odds, how many more the haul had. */
    public static final class Show {
        public long price = -1;
        public String odds, nth;
        public int more;
    }

    static final float[] CRUCIBLE_TOP = {-20, 61, 26};
    /** The spout's lip relative to the crucible's pivot (it turns with the tip). */
    static final float[] SPOUT_REL = {18, 3.5f};
    static final float[] MOULD = {4, 27.5f, 26};
    public static final float[] SHOW_AT = {4, 66, 40};
    static final int GOLD = 0xF2C94C;

    private static float[] at(float x, float y, float z) {
        ChopScene.project(x, y, z);
        return new float[]{ChopScene.P[0], ChopScene.P[1]};
    }

    private static void effects(ChopScene.Canvas c, long t, int w, int h, float fire, Show show) {
        float U = SlayerFx.U;
        // Light shafts from the furnace's mouth, faint, with dust turning in them.
        float[] mouth = at(0, 20, -32);
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * (0.18 + 0.13 * i) + 0.03 * Math.sin(t / 900.0 + i);
            float len = h * (0.35f + 0.1f * (i % 3));
            int steps = Math.round(len / U);
            for (int s = 0; s < steps; s += 2) {
                float f = 1f - s / (float) steps;
                SlayerFx.square(c, mouth[0] + (float) Math.cos(a) * s * U, mouth[1] + (float) Math.sin(a) * s * U * 0.5f,
                        1, SlayerFx.a(0.10f * Math.min(1f, fire) * f) | 0xFFC070);
            }
        }
        // Embers off the furnace, always, more as it roars.
        int embers = 26 + Math.round(20 * Math.min(1f, fire));
        for (int i = 0; i < embers; i++) {
            float life = 1600f + 900f * SlayerFx.rnd(901, i, 1);
            float u = ((t + SlayerFx.rnd(901, i, 2) * life) % life) / life;
            float[] p = at(-14 + 28 * SlayerFx.rnd(901, i, 3) + 10 * (float) Math.sin(u * 6 + i), 10 + u * 150, -30 + 20 * SlayerFx.rnd(901, i, 4));
            SlayerFx.square(c, p[0], p[1], SlayerFx.rnd(901, i, 5) < 0.3f ? 2 : 1, SlayerFx.a(Math.min(1f, fire) * (1f - u) * 0.85f) | (i % 3 == 0 ? 0xFFE08A : 0xFF8A2A));
        }
        // The braziers' flames: squares licking up off the magma, a burst of sparks as each catches.
        for (int side = -1; side <= 1; side += 2) {
            float on = Math.min(1f, ForgeTimeline.brazier(t, side < 0));
            if (on <= 0.01f) continue;
            for (int i = 0; i < 16; i++) {
                float u = ((t / 600f + SlayerFx.rnd(910 + side, i, 1)) % 1f);
                float[] p = at(side * 96 - 6 + 12 * SlayerFx.rnd(910 + side, i, 2), 42 + u * 30, 34 + 12 * SlayerFx.rnd(910 + side, i, 3));
                SlayerFx.square(c, p[0], p[1], u < 0.4f ? 2 : 1, SlayerFx.a(on * (1f - u)) | (u < 0.3f ? 0xFFE08A : 0xFF7A20));
            }
            long d = t - (side < 0 ? ForgeTimeline.BRAZIER_L : ForgeTimeline.BRAZIER_R);
            float[] b = at(side * 96, 42, 40);
            SlayerFx.burst(c, b[0], b[1], d, 600, 18, ChopScene.size * 40f, 915L + side, new int[]{0xFFE08A, 0xFF7A20, 0xFFFFFF}, 1f, ChopScene.size * 80f);
            if (d >= 0 && d < 300) NucleusScene.glow(c, b[0], b[1], ChopScene.size * 16f, 0xFF9A3C, 0.8f * (1f - d / 300f));
        }
        // The Automaton waking: a glow off its eye, sparks off its joints as it lifts.
        float eye = ForgeTimeline.eye(t);
        Part sh = PARTS.get("Shoulder");
        if (eye > 0f && sh != null) {
            float[] e = at(sh.pivot()[0] + 4.5f, sh.pivot()[1] + 7f, sh.pivot()[2] + 10f);
            NucleusScene.glow(c, e[0], e[1], ChopScene.size * 7f, 0xB8FFF0, 0.55f * eye);
            long d = t - ForgeTimeline.ARM_FROM;
            float[] j = at(sh.pivot()[0], sh.pivot()[1], sh.pivot()[2] + 10f);
            SlayerFx.burst(c, j[0], j[1], d, 500, 14, ChopScene.size * 34f, 918, new int[]{0xFFE08A, 0xFFFFFF}, 1f, ChopScene.size * 90f);
        }
        crystals(c, t, w, h);
        bloom(c, t, w, h);
        pour(c, t);
        blows(c, t, w, h);
        steam(c, t);
        showcase(c, t, w, h, show);
    }

    /**
     * The five crystals: in from the front of the room as comets, circling the crucible joined by beams of light into a
     * star, then spiralling down into it one by one, each a splash and a ring of its colour.
     */
    private static void crystals(ChopScene.Canvas c, long t, int w, int h) {
        if (t < ForgeTimeline.FLY_FROM || t > ForgeTimeline.DROP_FROM + 5 * ForgeTimeline.DROP_EVERY + 700) return;
        Crystal[] all = Crystal.values();
        float[][] pos = new float[5][];
        for (int k = 0; k < 5; k++) {
            float drop = ForgeTimeline.drop(k, t);
            int col = all[k].colour;
            if (drop >= 2f) {
                long d = t - (ForgeTimeline.DROP_FROM + k * ForgeTimeline.DROP_EVERY + ForgeTimeline.DROP_MS);
                float[] p = at(CRUCIBLE_TOP[0], CRUCIBLE_TOP[1], CRUCIBLE_TOP[2]);
                SlayerFx.burst(c, p[0], p[1], d, 620, 22, ChopScene.size * 40f, 920L + k, new int[]{col, 0xFFFFFF, ChopScene.mix(col, 0xFFFFFF, 0.5f)}, 1f, ChopScene.size * 100f);
                if (d < 240) NucleusScene.glow(c, p[0], p[1], ChopScene.size * 16f, col, 1f - d / 240f);
                if (d < 420) ringFlat(c, CRUCIBLE_TOP[0], CRUCIBLE_TOP[1] + 1, CRUCIBLE_TOP[2], 6f + 26f * (d / 420f), SlayerFx.a(0.8f * (1f - d / 420f)) | col);
                continue;
            }
            float fly = ForgeTimeline.fly(k, t);
            double a = k * Math.PI * 2 / 5 + t / 520.0;
            float rr = 26f;
            float ringX = CRUCIBLE_TOP[0] + rr * (float) Math.cos(a), ringZ = CRUCIBLE_TOP[2] + rr * (float) Math.sin(a);
            float ringY = 80f + 4f * (float) Math.sin(t / 260.0 + k * 1.3);
            float sx = 40f - 40f * k, sy = 130f, sz = 210f;
            float x = sx + (ringX - sx) * fly, y = sy + (ringY - sy) * fly, z = sz + (ringZ - sz) * fly;
            if (drop >= 0f) {
                // A spiral down into the crucible, faster as it goes.
                float e = drop * drop;
                double sa = a + e * Math.PI * 2;
                float r = rr * (1f - e);
                x = CRUCIBLE_TOP[0] + r * (float) Math.cos(sa);
                z = CRUCIBLE_TOP[2] + r * (float) Math.sin(sa);
                y = ringY + (CRUCIBLE_TOP[1] - ringY) * e;
            }
            pos[k] = new float[]{x, y, z};
            // A long tail behind it.
            for (int i = 1; i <= 16; i++) {
                long back = t - i * 22L;
                float bf = ForgeTimeline.fly(k, back);
                double ba = k * Math.PI * 2 / 5 + back / 520.0;
                float bx = sx + (CRUCIBLE_TOP[0] + rr * (float) Math.cos(ba) - sx) * bf, by = sy + (80f - sy) * bf,
                        bz = sz + (CRUCIBLE_TOP[2] + rr * (float) Math.sin(ba) - sz) * bf;
                if (drop >= 0f) {
                    bx = x;
                    by = y + i * 2.2f;
                    bz = z;
                }
                float[] q = at(bx, by, bz);
                SlayerFx.square(c, q[0], q[1], i < 4 ? 2 : 1, SlayerFx.a(0.85f * (1f - i / 17f)) | ChopScene.mix(col, 0xFFFFFF, i < 4 ? 0.4f : 0f));
            }
            float[] p = at(x, y, z);
            NucleusScene.glow(c, p[0], p[1], ChopScene.size * 10f, col, 0.75f);
            float bob = 1f + 0.08f * (float) Math.sin(t / 140.0 + k * 2.1);
            head(c, all[k].name() + "_CRYSTAL", x, y, z, 3.4f * bob, (float) (t / 260.0 + k), col);
        }
        // The star: beams between the ringing crystals, each a line of squares in the two colours.
        if (t > ForgeTimeline.FLY_TO - 200 && t < ForgeTimeline.DROP_FROM + 100) {
            float on = clamp01((t - ForgeTimeline.FLY_TO + 200) / 400f);
            for (int k = 0; k < 5; k++) {
                float[] a0 = pos[k], a1 = pos[(k + 2) % 5];
                if (a0 == null || a1 == null) continue;
                float[] p0 = at(a0[0], a0[1], a0[2]), p1 = at(a1[0], a1[1], a1[2]);
                int n = 18;
                for (int i = 1; i < n; i++) {
                    float u = i / (float) n;
                    float flick = 0.6f + 0.4f * (float) Math.sin(t / 50.0 + i + k);
                    SlayerFx.square(c, p0[0] + (p1[0] - p0[0]) * u, p0[1] + (p1[1] - p0[1]) * u, 1,
                            SlayerFx.a(on * flick * 0.8f) | ChopScene.mix(all[k].colour, all[(k + 2) % 5].colour, u));
                }
            }
        }
    }

    /** A ring of squares flat on a horizontal plane at height {@code y}. */
    private static void ringFlat(ChopScene.Canvas c, float cx, float y, float cz, float r, int argb) {
        // Dense enough to stay a line on screen even seen nearly edge on, from a low camera.
        int n = Math.max(24, Math.round(r * 8f));
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            float[] q = at(cx + r * (float) Math.cos(a), y, cz + r * (float) Math.sin(a));
            SlayerFx.square(c, q[0], q[1], Math.sin(a) > 0 ? 2 : 1, argb);
        }
    }

    /** The metal turning white: embers bursting out of the crucible, a ring of light rising off it, the room flaring. */
    private static void bloom(ChopScene.Canvas c, long t, int w, int h) {
        long d = t - ForgeTimeline.WHITE;
        if (d < 0 || d > 1200) return;
        float[] p = at(CRUCIBLE_TOP[0], CRUCIBLE_TOP[1], CRUCIBLE_TOP[2]);
        SlayerFx.burst(c, p[0], p[1], d, 1200, 60, ChopScene.size * 70f, 930, new int[]{0xFFFFFF, 0xFFF0B0, 0xFFB050}, 1f, ChopScene.size * 60f);
        if (d < 300) NucleusScene.glow(c, p[0], p[1], ChopScene.size * 26f, 0xFFF0C0, 1f - d / 300f);
        for (int r = 0; r < 3; r++) {
            long e = d - r * 140L;
            if (e < 0 || e > 700) continue;
            float u = e / 700f;
            ringFlat(c, CRUCIBLE_TOP[0], CRUCIBLE_TOP[1] + 4 + 40 * u, CRUCIBLE_TOP[2], 10 + 16 * u, SlayerFx.a(0.8f * (1f - u)) | 0xFFF0C0);
        }
        if (d < 500) SlayerFx.edgeBands(c, w, h, 0xFFB050, 0.16f * (1f - d / 500f), 4);
    }

    /** A head as quads: base and hat, its skin by NEU id, turned {@code yaw} about its upright. */
    private static void head(ChopScene.Canvas c, String id, float x, float y, float z, float half, float yaw, int fallback) {
        String tex = skin.apply(id);
        float[] p = at(x, y, z);
        if (tex == null) {
            SlayerFx.square(c, p[0], p[1], Math.max(2, Math.round(half * 2 * ChopScene.size / SlayerFx.U)), SlayerFx.a(1f) | fallback);
            return;
        }
        float cyw = (float) Math.cos(yaw), syw = (float) Math.sin(yaw);
        List<Face> faces = new ArrayList<>(12);
        for (int layer = 0; layer < 2; layer++) {
            float hh = layer == 0 ? half : half * 17f / 16f;
            int uo = layer * 32;
            float d = 2f * hh;
            float[][] f = {
                    {-hh, hh, hh, d, 0, 0, 0, -d, 0, 0, 0, 1, 8, 8},
                    {hh, hh, -hh, -d, 0, 0, 0, -d, 0, 0, 0, -1, 24, 8},
                    {hh, hh, hh, 0, 0, -d, 0, -d, 0, 1, 0, 0, 16, 8},
                    {-hh, hh, -hh, 0, 0, d, 0, -d, 0, -1, 0, 0, 0, 8},
                    {-hh, hh, -hh, d, 0, 0, 0, 0, d, 0, 1, 0, 8, 0},
            };
            for (float[] q : f) {
                Face face = new Face();
                float[] o = yawed(q[0], q[1], q[2], cyw, syw), u = yawed(q[3], q[4], q[5], cyw, syw), v = yawed(q[6], q[7], q[8], cyw, syw),
                        n = yawed(q[9], q[10], q[11], cyw, syw);
                face.ox = x + o[0]; face.oy = y + o[1]; face.oz = z + o[2];
                face.ux = u[0]; face.uy = u[1]; face.uz = u[2];
                face.vx = v[0]; face.vy = v[1]; face.vz = v[2];
                face.nx = n[0]; face.ny = n[1]; face.nz = n[2];
                face.mx = face.ox + (u[0] + v[0]) / 2f; face.my = face.oy + (u[1] + v[1]) / 2f; face.mz = face.oz + (u[2] + v[2]) / 2f;
                face.tex = tex;
                face.rect = new int[]{Math.round(q[12]) + uo, Math.round(q[13]), 8, 8};
                faces.add(face);
            }
        }
        faces.sort((a, b) -> Float.compare(depthOf(a.mx, a.my, a.mz), depthOf(b.mx, b.my, b.mz)));
        for (Face f : faces) {
            float facing = -f.ny * ChopScene.sp + (-f.nx * ChopScene.sy + f.nz * ChopScene.cy) * ChopScene.cp;
            if (facing <= 0.002f) continue;
            float lit = 0.72f + 0.28f * Math.max(0f, f.ny);
            emit(c, f, SlayerFx.a(1f) | ChopScene.channel(lit) << 16 | ChopScene.channel(lit) << 8 | ChopScene.channel(lit));
        }
    }

    private static float[] yawed(float x, float y, float z, float c, float s) {
        return new float[]{x * c + z * s, y, -x * s + z * c};
    }

    /** Where the spout's lip is now, turned with the crucible: {x, y, direction it points (radians)}. */
    private static float[] spout(long t) {
        Part cru = PARTS.get("Crucible");
        float px = cru == null ? -20 : cru.pivot()[0], py = cru == null ? 54 : cru.pivot()[1];
        float tip = ForgeTimeline.tip(t), cs = (float) Math.cos(tip), sn = (float) Math.sin(tip);
        return new float[]{px + SPOUT_REL[0] * cs - SPOUT_REL[1] * sn, py + SPOUT_REL[0] * sn + SPOUT_REL[1] * cs, tip};
    }

    /**
     * The pour: molten metal over the spout's lip, leaving it nearly level and falling into the mould - a parabola from
     * the lip to the mould's near half. Drawn as a tube: five cells across, shaded like a cylinder lit from the furnace
     * (a dark skin, orange, a white-hot core, its highlight, orange), wobbling; drops breaking off it; a splash and spatter
     * where it lands, and smoke rising off it.
     */
    private static void pour(ChopScene.Canvas c, long t) {
        float on = ForgeTimeline.stream(t);
        if (on <= 0.01f) return;
        float[] sp = spout(t);
        int col = moltenColour(t), skin = 0xFF7A2A, dark = 0xB8380E;
        float x0 = sp[0], y0 = sp[1], xt = MOULD[0] - 3f + 1.5f * (float) Math.sin(t / 210.0), yt = MOULD[1];
        float m = (float) Math.tan(sp[2] * 0.45f);          // it leaves the lip much flatter than the spout points
        float dx = Math.max(1f, xt - x0);
        float k = Math.max(0.02f, (y0 - yt + m * dx) / (dx * dx));
        int[] across = {dark, skin, ChopScene.mix(col, skin, 0.25f), 0xFFFFF0, skin};
        float[] across_a = {0.7f, 0.95f, 1f, 1f, 0.9f};
        int n = 40;
        float[] prev = at(x0, y0, MOULD[2]);
        for (int i = 1; i <= n; i++) {
            float u = i / (float) n;
            float x = x0 + dx * u, y = y0 + m * dx * u - k * dx * dx * u * u;
            float[] p = at(x, y, MOULD[2]);
            // The stream's screen direction, and the perpendicular its cross-section lies along.
            float ddx = p[0] - prev[0], ddy = p[1] - prev[1], len = (float) Math.max(1e-3, Math.hypot(ddx, ddy));
            float nx = -ddy / len, ny = ddx / len;
            float wob = (float) Math.sin(t / 37.0 + i * 0.7) * 0.6f * SlayerFx.U;
            float thick = on * (0.95f - 0.25f * u);
            float half = 2f * thick;                         // cells either side of the middle
            for (int j = -2; j <= 2; j++) {
                if (Math.abs(j) > half + 0.5f) continue;
                float o = j * SlayerFx.U * Math.max(0.6f, thick);
                SlayerFx.square(c, p[0] + nx * (o + wob), p[1] + ny * (o + wob), 1, SlayerFx.a(across_a[j + 2] * Math.min(1f, on * 1.4f)) | across[j + 2]);
            }
            prev = p;
        }
        // Drops breaking off and falling beside it.
        for (int i = 0; i < 7; i++) {
            long life = 300;
            long d = (t - ForgeTimeline.STREAM_FROM + i * 43L) % life;
            float u = SlayerFx.rnd(931, i, 1) * 0.8f + 0.1f;
            float x = x0 + dx * u, y = y0 + m * dx * u - k * dx * dx * u * u - 0.5f * 300f * (d / 1000f) * (d / 1000f);
            if (y < MOULD[1]) continue;
            float[] p = at(x + (SlayerFx.rnd(931, i, 3) - 0.5f) * 2f, y, MOULD[2] + (SlayerFx.rnd(931, i, 2) - 0.5f) * 4f);
            SlayerFx.square(c, p[0], p[1], 1, SlayerFx.a(on) | ChopScene.mix(col, 0xFFFFFF, 0.3f));
        }
        float landX = xt;
        float[] mm = at(landX, MOULD[1], MOULD[2]);
        long phase = (t - ForgeTimeline.STREAM_FROM) % 260;
        SlayerFx.burst(c, mm[0], mm[1], phase, 260, 12, ChopScene.size * 30f, 932L + (t - ForgeTimeline.STREAM_FROM) / 260, new int[]{0xFFF0B0, 0xFFFFFF, skin}, on, ChopScene.size * 140f);
        NucleusScene.glow(c, mm[0], mm[1], ChopScene.size * 9f, ChopScene.mix(col, skin, 0.4f), 0.4f * on);
        float[] lip = at(x0, y0, MOULD[2]);
        NucleusScene.glow(c, lip[0], lip[1], ChopScene.size * 6f, skin, 0.35f * on);
        // Smoke off the hot metal.
        for (int i = 0; i < 12; i++) {
            float life = 900f;
            float u = ((t + SlayerFx.rnd(933, i, 1) * life) % life) / life;
            float[] p = at(landX - 5 + 10 * SlayerFx.rnd(933, i, 2) + 6 * u * (float) Math.sin(i), MOULD[1] + 2 + u * 34, MOULD[2]);
            SlayerFx.square(c, p[0], p[1], u > 0.5f ? 3 : 2, SlayerFx.a(0.3f * on * (1f - u)) | 0x6A6460);
        }
    }

    /**
     * Each blow: sparks thrown up and out off the anvil, arcing down and bouncing on the floor; dust kicked up round the
     * anvil's foot; rings across the floor (the last blow three); a warm glow where the hammer lands; after the last, dust
     * shaken down off the ceiling.
     */
    private static void blows(ChopScene.Canvas c, long t, int w, int h) {
        for (int k = 0; k < 3; k++) {
            long d = t - ForgeTimeline.STRIKE[k];
            if (d < 0 || d > 1100) continue;
            boolean last = k == 2;
            float big = last ? 1.6f : 1f;
            int col = ingotColour(t);
            float[] p = at(MOULD[0], MOULD[1] + 2, MOULD[2]);
            // Sparks, ballistic: up and out, falling, a bounce off the floor.
            int n = last ? 70 : 40;
            float s = d / 1000f;
            for (int i = 0; i < n; i++) {
                double a = SlayerFx.rnd(940 + k, i, 1) * Math.PI * 2;
                float sp = (40f + 90f * SlayerFx.rnd(940 + k, i, 2)) * big;
                float vy = (50f + 110f * SlayerFx.rnd(940 + k, i, 3)) * big;
                float x = MOULD[0] + (float) Math.cos(a) * sp * s, z = MOULD[2] + (float) Math.sin(a) * sp * s * 0.8f;
                float y = MOULD[1] + vy * s - 0.5f * 420f * s * s;
                if (y < 0f) y = -y * 0.35f;
                float life = 0.5f + 0.5f * SlayerFx.rnd(940 + k, i, 4);
                float f = 1f - s / life;
                if (f <= 0f) continue;
                float[] q = at(x, y, z);
                SlayerFx.square(c, q[0], q[1], f > 0.6f ? 2 : 1, SlayerFx.a(f) | (i % 4 == 0 ? 0xFFFFFF : (i % 4 == 1 ? 0xFFE08A : (i % 4 == 2 ? 0xFFB050 : col))));
            }
            if (d < 200) NucleusScene.glow(c, p[0], p[1], ChopScene.size * 8f * big, 0xFFA040, 0.7f * (1f - d / 200f));
            // Dust off the floor round the anvil's foot.
            for (int i = 0; i < 28; i++) {
                double a = i * Math.PI * 2 / 28 + SlayerFx.rnd(945 + k, i, 1);
                float u = clamp01(d / 700f), r = 14f + 40f * big * (1f - (1f - u) * (1f - u));
                float[] q = at(MOULD[0] + r * (float) Math.cos(a), 1f + 8f * u * SlayerFx.rnd(945 + k, i, 2), MOULD[2] + r * (float) Math.sin(a));
                SlayerFx.square(c, q[0], q[1], u < 0.5f ? 3 : 2, SlayerFx.a(0.45f * (1f - u)) | 0x9A948A);
            }
            for (int wv = 0; wv < (last ? 3 : 2); wv++) {
                long e = d - wv * 110L;
                if (e < 0 || e > 560) continue;
                float u = e / 560f;
                ringFlat(c, MOULD[0], 0.5f, MOULD[2], 10f + 70f * big * (1f - (1f - u) * (1f - u)), SlayerFx.a((1f - u) * (wv == 0 ? 0.75f : 0.4f)) | (wv == 0 ? 0xFFD890 : 0xC8B8A0));
            }
            if (last && d < 520) SlayerFx.edgeBands(c, w, h, 0xFFD080, 0.3f * (1f - d / 520f), 5);
        }
        // Dust shaken down off the ceiling after the last blow.
        long d = t - ForgeTimeline.STRIKE[2];
        if (d > 60 && d < 1800) {
            for (int i = 0; i < 46; i++) {
                float start = SlayerFx.rnd(948, i, 1) * 600f;
                float s = (d - start) / 1000f;
                if (s < 0f) continue;
                float x = SlayerFx.rnd(948, i, 2) * w, y = -10f + 0.5f * 600f * s * s * (h / 360f);
                if (y > h) continue;
                SlayerFx.square(c, x + 4f * (float) Math.sin(s * 6 + i), y, SlayerFx.rnd(948, i, 3) < 0.3f ? 2 : 1, SlayerFx.a(0.55f) | 0xB0A898);
            }
        }
    }

    /** The ingot cooling: steam off it, puffing up and drifting; the heat rippling the air over it. */
    private static void steam(ChopScene.Canvas c, long t) {
        if (t < ForgeTimeline.COOL_FROM - 200 || t > ForgeTimeline.LIFT_FROM + 400) return;
        float k = 1f - ForgeTimeline.heat(t) * 0.5f;
        for (int i = 0; i < 36; i++) {
            float life = 1300f;
            float u = ((t + SlayerFx.rnd(950, i, 1) * life) % life) / life;
            float[] p = at(MOULD[0] - 8 + 16 * SlayerFx.rnd(950, i, 2) + 8 * u * (float) Math.sin(i), MOULD[1] + 2 + u * 56, MOULD[2] - 3 + 6 * SlayerFx.rnd(950, i, 3));
            SlayerFx.square(c, p[0], p[1], u > 0.5f ? 3 : 2, SlayerFx.a(0.38f * k * (1f - u)) | 0xD8D8DC);
        }
    }

    /**
     * The Alloy: off the mould, rising to the middle of the screen; a pillar of gold light behind it from the floor up,
     * rings out over the floor, a fountain of gold sparks off the anvil, rays of gold and white, the five crystals'
     * colours circling it, motes across the screen; its name, price and odds under it.
     */
    private static void showcase(ChopScene.Canvas c, long t, int w, int h, Show show) {
        float u = ForgeTimeline.lift(t);
        if (u < 0f || spotlight) return;
        float k = clamp01(u);
        float x = MOULD[0] + (SHOW_AT[0] - MOULD[0]) * u, y = MOULD[1] + (SHOW_AT[1] - MOULD[1]) * u, z = MOULD[2] + (SHOW_AT[2] - MOULD[2]) * u;
        y += 1.6f * (float) Math.sin(t / 480.0) * k;
        float half = 4f + 12f * u;
        float[] p = at(x, y, z);
        long d = t - ForgeTimeline.LIFT_FROM;
        // The pillar: a column of gold light, floor to past the top of the screen, flickering at its edges.
        float[] base = at(SHOW_AT[0], 0f, SHOW_AT[2] - 8f);
        float pw = ChopScene.size * 10f * k;
        for (float yy = base[1]; yy > -SlayerFx.U * 2; yy -= SlayerFx.U) {
            float flick = 0.7f + 0.3f * (float) Math.sin(t / 70.0 + yy * 0.13);
            for (float xx = -pw; xx <= pw; xx += SlayerFx.U) {
                float edge = 1f - Math.abs(xx) / Math.max(1f, pw);
                SlayerFx.square(c, base[0] + xx, yy, 1, SlayerFx.a(0.22f * k * edge * flick) | ChopScene.mix(GOLD, 0xFFFFFF, 0.5f * edge));
            }
        }
        // Rings out over the floor as it arrives.
        for (int r = 0; r < 3; r++) {
            long e = t - (ForgeTimeline.LIFT_TO - 200) - r * 160L;
            if (e < 0 || e > 800) continue;
            float v = e / 800f;
            ringFlat(c, SHOW_AT[0], 0.5f, SHOW_AT[2] - 8f, 12f + 110f * (1f - (1f - v) * (1f - v)), SlayerFx.a(0.8f * (1f - v)) | (r == 1 ? 0xFFF4C0 : GOLD));
        }
        // A fountain of gold sparks off the anvil while it rises.
        if (d < 1600) {
            for (int i = 0; i < 40; i++) {
                float born = SlayerFx.rnd(955, i, 1) * 1200f;
                float s = (d - born) / 1000f;
                if (s < 0f || s > 0.9f) continue;
                double a = SlayerFx.rnd(955, i, 2) * Math.PI * 2;
                float sx = MOULD[0] + (float) Math.cos(a) * 30f * s, sz = MOULD[2] + (float) Math.sin(a) * 30f * s;
                float sy = MOULD[1] + 140f * s - 0.5f * 260f * s * s;
                float[] q = at(sx, sy, sz);
                SlayerFx.square(c, q[0], q[1], s < 0.3f ? 2 : 1, SlayerFx.a(1f - s / 0.9f) | (i % 3 == 0 ? 0xFFFFFF : GOLD));
            }
        }
        // Rays: wedges turning slowly, gold and white in turn.
        int rays = 16;
        for (int i = 0; i < rays; i++) {
            double a = i * Math.PI * 2 / rays + t / 2600.0;
            float len = ChopScene.size * half * (3.4f + 0.9f * (float) Math.sin(t / 340.0 + i)) * k;
            int steps = Math.max(1, Math.round(len / SlayerFx.U));
            for (int s = 0; s < steps; s++) {
                float r = ChopScene.size * half * 1.1f + s * SlayerFx.U, f = 1f - s / (float) steps;
                SlayerFx.square(c, p[0] + r * (float) Math.cos(a), p[1] + r * (float) Math.sin(a), 1 + Math.round(3f * s / steps),
                        SlayerFx.a(0.42f * f * k) | ChopScene.mix(i % 2 == 0 ? GOLD : ALLOY_TOP, 0xFFFFFF, 0.35f * f));
            }
        }
        NucleusScene.glow(c, p[0], p[1], ChopScene.size * half * 3.2f, GOLD, 0.8f * k);
        // The five crystals' colours, circling it.
        Crystal[] all = Crystal.values();
        for (int j = 0; j < 5; j++) {
            double a = j * Math.PI * 2 / 5 + t / 700.0;
            float r = half * 2.1f;
            float[] q = at(x + r * (float) Math.cos(a), y + 0.4f * r * (float) Math.sin(a * 2), z + r * (float) Math.sin(a));
            NucleusScene.glow(c, q[0], q[1], ChopScene.size * 5f, all[j].colour, 0.9f * k);
            SlayerFx.square(c, q[0], q[1], 2, SlayerFx.a(k) | ChopScene.mix(all[j].colour, 0xFFFFFF, 0.4f));
        }
        // Gold motes rising across the whole screen.
        for (int i = 0; i < 56; i++) {
            float sp = 14f + 22f * SlayerFx.rnd(960, i, 1);
            float mx = SlayerFx.rnd(960, i, 2) * w, my = h + 10f - ((SlayerFx.rnd(960, i, 3) * (h + 20f) + t * sp / 1000f * (h / 360f)) % (h + 20f));
            float tw = 0.5f + 0.5f * (float) Math.sin(t / 200.0 + i * 1.3);
            SlayerFx.square(c, mx, my, tw > 0.8f ? 2 : 1, SlayerFx.a(k * (0.3f + 0.5f * tw)) | (i % 3 == 0 ? 0xFFFFFF : GOLD));
        }
        head(c, "DIVAN_ALLOY", x, y, z, half, 0.6f + (float) Math.sin(t / 1100.0) * 0.35f + (1f - k) * 6f, GOLD);
        // The words.
        float sc = Math.max(1f, Math.min(1.5f, h / 280f));
        float ty = p[1] + half * ChopScene.size * 1.5f + 8f * sc;
        float a0 = ForgeTimeline.words(t, 0);
        if (a0 > 0f) c.text("Divan's Alloy", p[0], ty, sc * 1.5f, SlayerFx.a(a0) | 0xFFAA00);
        float a1 = ForgeTimeline.words(t, 1);
        if (a1 > 0f && show != null && show.price >= 0) {
            float q = clamp01((t - ForgeTimeline.WORDS - 220) / 1100f);
            long shown = Math.round(show.price * (1.0 - Math.pow(1.0 - q, 3)));
            c.text(String.format(java.util.Locale.ROOT, "%,d coins", shown), p[0], ty + 16f * sc, sc * 1.1f, SlayerFx.a(a1) | 0xFFAA00);
        }
        float a2 = ForgeTimeline.words(t, 2);
        if (a2 > 0f && show != null) {
            String line = show.odds == null ? null : show.odds + (show.nth == null ? "" : " · " + show.nth);
            if (line != null) c.text(line, p[0], ty + 29f * sc, sc * 0.85f, SlayerFx.a(a2 * 0.9f) | 0xCCCCCC);
            if (show.more > 0) c.text("+" + show.more + " more", p[0], ty + 40f * sc, sc * 0.8f, SlayerFx.a(a2 * 0.8f) | 0xAAAAAA);
        }
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
