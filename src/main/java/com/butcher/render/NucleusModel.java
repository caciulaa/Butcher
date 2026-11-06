package com.butcher.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The Crystal Nucleus vault door: the boxes the user approved in Blender (2026-10-27, v7), baked to
 * {@code assets/butcher/nucleus_vault.json} by {@code tools/blender/build_vault.py}, in mod pixels (x right, y up, z
 * towards the camera). Each box names a part (Static, Door, Dial, Bolt0-4, Socket0-4) and a kind (a block texture and a
 * tint). Boxes are cut at 16-pixel boundaries on load so every face shows one tile of one texture, a texel a pixel, the
 * way the Blender preview tiled them; all the textures go into one atlas, one {@link ModelRaster} slot. Pure, and safe
 * to rasterise off the render thread with a {@link Cam} of its own.
 */
final class NucleusModel {
    private NucleusModel() {}

    /** {@code alpha}: how much of it shows (the wall's and floor's fading edge, decided per block in Blender). */
    record Box(String part, String kind, float x0, float x1, float y0, float y1, float z0, float z1, float rot, float ox, float oy, float alpha) {
        Box(String part, String kind, float x0, float x1, float y0, float y1, float z0, float z1, float rot, float ox, float oy) {
            this(part, kind, x0, x1, y0, y1, z0, z1, rot, ox, oy, 1f);
        }
    }

    record Part(String name, float[] pivot, String parent, float[] slide) {
    }

    /** {@code mc}: shaded as Minecraft shades a block, its texture from its own block's edge (the wall and floor). */
    record Kind(String texture, int tint, int tile, boolean mc) {
    }

    /**
     * One model in this format - the vault door, or the Gauntlet (2026-10-28) - its parts, kinds, textures and boxes, and
     * the atlas of its textures (one {@link ModelRaster} slot).
     */
    static final class Model {
        final Map<String, Part> parts = new LinkedHashMap<>();
        final Map<String, Kind> kinds = new HashMap<>();
        final List<String> textures = new ArrayList<>();
        final List<Box> boxes = new ArrayList<>();
        /** Where a Minecraft-shaded kind's blocks start in x (the vault's wall is centred on the door). */
        final float gridX;

        Model(float gridX) {
            this.gridX = gridX;
        }

        int atlasW() {
            return COLS * 16;
        }

        int atlasH() {
            return Math.max(1, (textures.size() + COLS - 1) / COLS) * 16;
        }

        /** The atlas: every kind's texture as a 16 x 16 tile, read through {@code pixels} (null: a grey tile). */
        int[] atlas(Function<String, int[]> pixels) {
            int w = atlasW(), h = atlasH();
            int[] out = new int[w * h];
            for (int t = 0; t < textures.size(); t++) {
                int[] src = pixels.apply(textures.get(t));
                int tx = (t % COLS) * 16, ty = (t / COLS) * 16;
                for (int y = 0; y < 16; y++)
                    for (int x = 0; x < 16; x++)
                        out[(ty + y) * w + tx + x] = src == null || src.length < 256 ? 0xFF808080 : src[y * 16 + x];
            }
            return out;
        }
    }

    /** A model in this format from the mod's resources (empty if it is missing). */
    static Model load(String resource, float gridX) {
        Model m = new Model(gridX);
        try (InputStream in = NucleusModel.class.getResourceAsStream(resource)) {
            if (in != null) load(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject(), m);
        } catch (Exception e) {
            // Missing or broken: the scene plays without it.
        }
        return m;
    }

    /** The vault door; its parts, kinds, textures and boxes by their old names. */
    static final Model VAULT = load("/assets/butcher/nucleus_vault.json", 8f);
    static final Map<String, Part> PARTS = VAULT.parts;
    static final Map<String, Kind> KINDS = VAULT.kinds;
    static final List<String> TEXTURES = VAULT.textures;
    static final List<Box> BOXES = VAULT.boxes;

    /** The floor's top, mod pixels, on the block grid: the frame (r 44) stands just above it. */
    static final float FLOOR_Y = -48f;
    /** The wall's and floor's blocks sit on a grid this far off the door's middle in x (blocks centred on x 0). */
    static final float GRID_X = 8f;
    /** How bright a Minecraft-shaded block is in the vault's light. */
    static final float MC_LIGHT = 0.86f;

    private static void load(JsonObject root, Model into) {
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("parts").entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            String parent = o.get("parent").isJsonNull() ? null : o.get("parent").getAsString();
            float[] slide = o.has("slide") ? vec(o.getAsJsonArray("slide")) : null;
            into.parts.put(e.getKey(), new Part(e.getKey(), vec(o.getAsJsonArray("pivot")), parent, slide));
        }
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("kinds").entrySet()) {
            JsonArray a = e.getValue().getAsJsonArray();
            String tex = a.get(0).getAsString();
            int tile = into.textures.indexOf(tex);
            if (tile < 0) {
                tile = into.textures.size();
                into.textures.add(tex);
            }
            int tint = channel(a.get(1).getAsFloat()) << 16 | channel(a.get(2).getAsFloat()) << 8 | channel(a.get(3).getAsFloat());
            into.kinds.put(e.getKey(), new Kind(tex, tint, tile, a.size() > 4 && a.get(4).getAsInt() == 1));
        }
        for (JsonElement e : root.getAsJsonArray("boxes")) {
            JsonArray a = e.getAsJsonArray();
            float cx = a.get(2).getAsFloat(), cy = a.get(3).getAsFloat(), cz = a.get(4).getAsFloat();
            float hx = a.get(5).getAsFloat(), hy = a.get(6).getAsFloat(), hz = a.get(7).getAsFloat();
            float alpha = a.size() > 11 ? a.get(11).getAsFloat() : 1f;
            split(into, a.get(0).getAsString(), a.get(1).getAsString(), cx - hx, cx + hx, cy - hy, cy + hy, cz - hz, cz + hz,
                    a.get(8).getAsFloat(), a.get(9).getAsFloat(), a.get(10).getAsFloat(), alpha);
        }
    }

    /** Cuts a box at every texture tile's edge along each axis (a block's own, for the wall's), so no face spans two. */
    private static void split(Model into, String part, String kind, float x0, float x1, float y0, float y1, float z0, float z1, float rot, float ox, float oy, float alpha) {
        Kind k = into.kinds.get(kind);
        float gx = k != null && k.mc() ? into.gridX : 0f;
        for (float[] xs : cuts(x0 + gx, x1 + gx))
            for (float[] ys : cuts(y0, y1))
                for (float[] zs : cuts(z0, z1))
                    into.boxes.add(new Box(part, kind, xs[0] - gx, xs[1] - gx, ys[0], ys[1], zs[0], zs[1], rot, ox, oy, alpha));
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

    private static int channel(float f) {
        return Math.max(0, Math.min(255, Math.round(f * 255f)));
    }

    // ------------------------------------------------------------------ the atlas

    static final int COLS = 8;

    /** The vault's atlas. */
    static int[] atlas(Function<String, int[]> pixels) {
        return VAULT.atlas(pixels);
    }

    static int atlasW() {
        return VAULT.atlasW();
    }

    static int atlasH() {
        return VAULT.atlasH();
    }

    // ------------------------------------------------------------------ drawing

    /**
     * A part's pose: a 3x4 matrix taking mod pixels to the scene's world (x right, y up, z towards the camera), already
     * scaled and placed - {@code m[0..2]} the x column, {@code m[3..5]} y, {@code m[6..8]} z, {@code m[9..11]} the origin.
     */
    static float[] identity(float scale, float tx, float ty, float tz) {
        return new float[]{scale, 0, 0, 0, scale, 0, 0, 0, scale, tx, ty, tz};
    }

    /** {@code m} turned {@code angle} about a vertical axis through (px, pz) in mod pixels - the door on its hinge. */
    static float[] hinged(float[] m, float px, float pz, float angle) {
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        // x' = px + (x - px) c - (z - pz) s,  z' = pz + (x - px) s + (z - pz) c: positive swings the door towards the camera
        float[] r = {c, 0, s, 0, 1, 0, -s, 0, c};
        float[] t = {px - px * c + pz * s, 0, pz - px * s - pz * c};
        return compose(m, r, t);
    }

    /** {@code m} turned {@code angle} about the door's normal through (px, py) - the dial. */
    static float[] spun(float[] m, float px, float py, float angle) {
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        float[] r = {c, s, 0, -s, c, 0, 0, 0, 1};
        float[] t = {px - px * c + py * s, py - px * s - py * c, 0};
        return compose(m, r, t);
    }

    /** {@code m} moved by (dx, dy, dz) mod pixels - a bolt sliding. */
    static float[] moved(float[] m, float dx, float dy, float dz) {
        return compose(m, new float[]{1, 0, 0, 0, 1, 0, 0, 0, 1}, new float[]{dx, dy, dz});
    }

    /** m applied after the local affine (r columns, t): world = m (r p + t). */
    private static float[] compose(float[] m, float[] r, float[] t) {
        float[] o = new float[12];
        for (int col = 0; col < 3; col++) {
            for (int row = 0; row < 3; row++) {
                o[col * 3 + row] = m[row] * r[col * 3] + m[3 + row] * r[col * 3 + 1] + m[6 + row] * r[col * 3 + 2];
            }
        }
        for (int row = 0; row < 3; row++) o[9 + row] = m[row] * t[0] + m[3 + row] * t[1] + m[6 + row] * t[2] + m[9 + row];
        return o;
    }

    private static final float[] L = {-0.4f, 0.8f, 0.45f};

    /**
     * A camera: ChopScene's projection with its own numbers, so the rock can be rasterised on a worker thread while
     * the game draws with ChopScene's (statics shared by every scene).
     */
    record Cam(float cy, float sy, float cp, float sp, float size, float fx, float fy, float fz, float sx0, float sy0) {
        static Cam current() {
            return new Cam(ChopScene.cy, ChopScene.sy, ChopScene.cp, ChopScene.sp, ChopScene.size, ChopScene.fx, ChopScene.fy,
                    ChopScene.fz, ChopScene.sx0, ChopScene.sy0);
        }

        float px(float x, float y, float z) {
            return sx0 + ((x - fx) * cy + (z - fz) * sy) * size;
        }

        float py(float x, float y, float z) {
            float rz = -(x - fx) * sy + (z - fz) * cy;
            return sy0 - ((y - fy) * cp + rz * sp) * size;
        }

        float depth(float x, float y, float z) {
            float rz = -(x - fx) * sy + (z - fz) * cy;
            return -(y - fy) * sp + rz * cp;
        }
    }

    /**
     * Queues one box's visible faces into {@code r}, posed by {@code m}; {@code add} adds light (RGB, alpha byte how
     * much) - the gems' glow. The camera is ChopScene's.
     */
    static void box(ModelRaster r, Box b, float[] m, int add, Cam cam) {
        box(r, b, m, add, cam, VAULT);
    }

    /** The same, for a box of {@code model} (its kinds and atlas). */
    static void box(ModelRaster r, Box b, float[] m, int add, Cam cam, Model model) {
        Kind k = model.kinds.get(b.kind);
        if (k == null) return;
        float c = (float) Math.cos(b.rot), s = (float) Math.sin(b.rot);
        float x0 = b.x0, x1 = b.x1, y0 = b.y0, y1 = b.y1, z0 = b.z0, z1 = b.z1;
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float gx = k.mc ? model.gridX : 0f;
        float cx = (float) Math.floor((x0 + gx + 1e-3f) / 16f) * 16f - gx, cy = (float) Math.floor((y0 + 1e-3f) / 16f) * 16f,
                cz = (float) Math.floor((z0 + 1e-3f) / 16f) * 16f;
        int tx = (k.tile % COLS) * 16, ty = (k.tile / COLS) * 16;
        float aw = model.atlasW(), ah = model.atlasH();
        int tint = k.tint;
        int alpha = Math.round(b.alpha * 255f);
        if (alpha <= 2) return;
        // A crystal's window not lit yet: the glass dark, as it is with nothing behind it.
        if (GEMS.contains(b.kind) && add == 0) tint = scale(tint, 0.62f);
        r.faceAlpha = alpha;
        // {corner, U edge, V edge, normal, u0, u1, v0, v1} per face, in the box's own frame
        float[][] f = {
                {x0, y1, z1, dx, 0, 0, 0, -dy, 0, 0, 0, 1, x0 - cx, x1 - cx, cy + 16 - y1, cy + 16 - y0},
                {x1, y1, z0, -dx, 0, 0, 0, -dy, 0, 0, 0, -1, cx + 16 - x1, cx + 16 - x0, cy + 16 - y1, cy + 16 - y0},
                {x1, y1, z1, 0, 0, -dz, 0, -dy, 0, 1, 0, 0, cz + 16 - z1, cz + 16 - z0, cy + 16 - y1, cy + 16 - y0},
                {x0, y1, z0, 0, 0, dz, 0, -dy, 0, -1, 0, 0, z0 - cz, z1 - cz, cy + 16 - y1, cy + 16 - y0},
                {x0, y1, z0, dx, 0, 0, 0, 0, dz, 0, 1, 0, x0 - cx, x1 - cx, z0 - cz, z1 - cz},
                {x0, y0, z1, dx, 0, 0, 0, 0, -dz, 0, -1, 0, x0 - cx, x1 - cx, cz + 16 - z1, cz + 16 - z0},
        };
        for (float[] q : f) {
            // the box's turn about z, then the part's pose
            float[] o = turn(q[0], q[1], q[2], c, s, b.ox, b.oy, true), u = turn(q[3], q[4], q[5], c, s, 0, 0, false),
                    v = turn(q[6], q[7], q[8], c, s, 0, 0, false), n = turn(q[9], q[10], q[11], c, s, 0, 0, false);
            float[] wo = apply(m, o, true), wu = apply(m, u, false), wv = apply(m, v, false), wn = apply(m, n, false);
            float ln = (float) Math.sqrt(wn[0] * wn[0] + wn[1] * wn[1] + wn[2] * wn[2]);
            if (ln < 1e-6f) continue;
            float nx = wn[0] / ln, ny = wn[1] / ln, nz = wn[2] / ln;
            float facing = -ny * cam.sp() + (-nx * cam.sy() + nz * cam.cy()) * cam.cp();
            if (facing <= 0.02f) continue;
            float lit = k.mc ? MC_LIGHT * shade(nx, ny, nz) : 0.55f + 0.45f * Math.max(0f, nx * L[0] + ny * L[1] + nz * L[2]);
            float ax = wo[0], ay = wo[1], az = wo[2], bx = ax + wu[0], by = ay + wu[1], bz = az + wu[2];
            float ex = ax + wv[0], ey = ay + wv[1], ez = az + wv[2];
            float px = cam.px(ax, ay, az), py = cam.py(ax, ay, az), d0 = cam.depth(ax, ay, az);
            float qx = cam.px(bx, by, bz) - px, qy = cam.py(bx, by, bz) - py, du = cam.depth(bx, by, bz) - d0;
            float rx = cam.px(ex, ey, ez) - px, ry = cam.py(ex, ey, ez) - py, dv = cam.depth(ex, ey, ez) - d0;
            r.face(px, py, qx, qy, rx, ry, d0, du, dv, Float.NaN, Float.NaN, Float.NaN,
                    (tx + q[12]) / aw, (ty + q[14]) / ah, (tx + q[13]) / aw, (ty + q[15]) / ah, 0, lit, tint, add, false);
        }
        r.faceAlpha = 255;
    }

    /**
     * A box of one colour - the Jade Dye's paint, the claw's torn metal - posed by {@code m}: the 8 x 8 sheen in
     * {@code slot} tinted {@code rgb}, lit by the scene's light (tops brightest, as a liquid catches it), {@code add}
     * adding light (the slits' glow). Faces turned away are skipped, as a box's are.
     */
    static void solid(ModelRaster r, int slot, float[] m, float x0, float x1, float y0, float y1, float z0, float z1,
                      int rgb, int add, Cam cam) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float su = Math.min(1f, dx / 8f), sv = Math.min(1f, dy / 8f), sw = Math.min(1f, dz / 8f);
        // {corner, U edge, V edge, normal, u1, v1}
        float[][] f = {
                {x0, y1, z1, dx, 0, 0, 0, -dy, 0, 0, 0, 1, su, sv},
                {x1, y1, z0, -dx, 0, 0, 0, -dy, 0, 0, 0, -1, su, sv},
                {x1, y1, z1, 0, 0, -dz, 0, -dy, 0, 1, 0, 0, sw, sv},
                {x0, y1, z0, 0, 0, dz, 0, -dy, 0, -1, 0, 0, sw, sv},
                {x0, y1, z0, dx, 0, 0, 0, 0, dz, 0, 1, 0, su, sw},
                {x0, y0, z1, dx, 0, 0, 0, 0, -dz, 0, -1, 0, su, sw},
        };
        for (float[] q : f) {
            float[] wn = apply(m, new float[]{q[9], q[10], q[11]}, false);
            float ln = (float) Math.sqrt(wn[0] * wn[0] + wn[1] * wn[1] + wn[2] * wn[2]);
            if (ln < 1e-6f) continue;
            float nx = wn[0] / ln, ny = wn[1] / ln, nz = wn[2] / ln;
            float facing = -ny * cam.sp() + (-nx * cam.sy() + nz * cam.cy()) * cam.cp();
            if (facing <= 0.02f) continue;
            float lit = 0.5f + 0.42f * Math.max(0f, nx * L[0] + ny * L[1] + nz * L[2]) + (ny > 0.7f ? 0.12f : 0f);
            float[] wo = apply(m, new float[]{q[0], q[1], q[2]}, true), wu = apply(m, new float[]{q[3], q[4], q[5]}, false),
                    wv = apply(m, new float[]{q[6], q[7], q[8]}, false);
            float ax = wo[0], ay = wo[1], az = wo[2];
            float px = cam.px(ax, ay, az), py = cam.py(ax, ay, az), d0 = cam.depth(ax, ay, az);
            float qx = cam.px(ax + wu[0], ay + wu[1], az + wu[2]) - px, qy = cam.py(ax + wu[0], ay + wu[1], az + wu[2]) - py;
            float du = cam.depth(ax + wu[0], ay + wu[1], az + wu[2]) - d0;
            float rx = cam.px(ax + wv[0], ay + wv[1], az + wv[2]) - px, ry = cam.py(ax + wv[0], ay + wv[1], az + wv[2]) - py;
            float dv = cam.depth(ax + wv[0], ay + wv[1], az + wv[2]) - d0;
            r.face(px, py, qx, qy, rx, ry, d0, du, dv, Float.NaN, Float.NaN, Float.NaN, 0f, 0f, q[12], q[13], slot, Math.min(1f, lit), rgb, add, false);
        }
    }

    /** The screen rectangle a {@link #solid} box covers once posed, into {@code acc}. */
    static void bounds(float[] m, float x0, float x1, float y0, float y1, float z0, float z1, float[] acc, Cam cam) {
        for (int i = 0; i < 8; i++) {
            float[] w = apply(m, new float[]{(i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? z0 : z1}, true);
            float sx = cam.px(w[0], w[1], w[2]), sy = cam.py(w[0], w[1], w[2]);
            acc[0] = Math.min(acc[0], sx);
            acc[1] = Math.min(acc[1], sy);
            acc[2] = Math.max(acc[2], sx);
            acc[3] = Math.max(acc[3], sy);
        }
    }

    /** Minecraft's own face shading: the top full, the bottom half, north and south 0.8, east and west 0.6. */
    static float shade(float nx, float ny, float nz) {
        if (Math.abs(ny) > 0.7f) return ny > 0 ? 1f : 0.5f;
        return Math.abs(nz) >= Math.abs(nx) ? 0.8f : 0.6f;
    }

    /** The crystals' windows (and two gem kinds the old rock had). */
    static final java.util.Set<String> GEMS = java.util.Set.of("jade", "amber", "topaz", "sapphire", "amethyst", "ruby", "jasper");

    /**
     * A head - base and hat, a 64 x 64 skin in {@code slot} - its base {@code 2 half} across, centred on the origin and
     * posed by {@code m}. The front (8, 8) is on +z, towards the camera; the head's own left (16, 8) on +x.
     */
    static void head(ModelRaster r, int slot, float[] m, float half, int add, Cam cam) {
        head(r, slot, m, half, add, cam, 0, 0, 64, 64);
    }

    /** The same, its skin at ({@code ax}, {@code ay}) in an atlas {@code aw x ah} texels. */
    static void head(ModelRaster r, int slot, float[] m, float half, int add, Cam cam, int tx0, int ty0, int aw, int ah) {
        for (int layer = 0; layer < 2; layer++) {
            float h = layer == 0 ? half : half * 17f / 16f;
            int uo = layer * 32;
            float x0 = -h, x1 = h, y0 = -h, y1 = h, z0 = -h, z1 = h, d = 2f * h;
            // {corner, U edge, V edge, normal, u0, v0, u1, v1} in skin texels
            float[][] f = {
                    {x0, y1, z1, d, 0, 0, 0, -d, 0, 0, 0, 1, 8, 8, 16, 16},
                    {x1, y1, z0, -d, 0, 0, 0, -d, 0, 0, 0, -1, 24, 8, 32, 16},
                    {x1, y1, z1, 0, 0, -d, 0, -d, 0, 1, 0, 0, 16, 8, 24, 16},
                    {x0, y1, z0, 0, 0, d, 0, -d, 0, -1, 0, 0, 0, 8, 8, 16},
                    {x0, y1, z0, d, 0, 0, 0, 0, d, 0, 1, 0, 8, 0, 16, 8},
                    {x0, y0, z1, d, 0, 0, 0, 0, -d, 0, -1, 0, 16, 0, 24, 8},
            };
            for (float[] q : f) {
                float[] wo = apply(m, new float[]{q[0], q[1], q[2]}, true), wu = apply(m, new float[]{q[3], q[4], q[5]}, false),
                        wv = apply(m, new float[]{q[6], q[7], q[8]}, false), wn = apply(m, new float[]{q[9], q[10], q[11]}, false);
                float ln = (float) Math.sqrt(wn[0] * wn[0] + wn[1] * wn[1] + wn[2] * wn[2]);
                if (ln < 1e-6f) continue;
                float nx = wn[0] / ln, ny = wn[1] / ln, nz = wn[2] / ln;
                float facing = -ny * cam.sp() + (-nx * cam.sy() + nz * cam.cy()) * cam.cp();
                if (facing <= 0.02f) continue;
                float lit = 0.62f + 0.38f * Math.max(0f, nx * L[0] + ny * L[1] + nz * L[2]);
                float ax = wo[0], ay = wo[1], az = wo[2];
                float px = cam.px(ax, ay, az), py = cam.py(ax, ay, az), d0 = cam.depth(ax, ay, az);
                float qx = cam.px(ax + wu[0], ay + wu[1], az + wu[2]) - px, qy = cam.py(ax + wu[0], ay + wu[1], az + wu[2]) - py;
                float du = cam.depth(ax + wu[0], ay + wu[1], az + wu[2]) - d0;
                float rx = cam.px(ax + wv[0], ay + wv[1], az + wv[2]) - px, ry = cam.py(ax + wv[0], ay + wv[1], az + wv[2]) - py;
                float dv = cam.depth(ax + wv[0], ay + wv[1], az + wv[2]) - d0;
                r.face(px, py, qx, qy, rx, ry, d0, du, dv, Float.NaN, Float.NaN, Float.NaN,
                        (tx0 + uo + q[12]) / aw, (ty0 + q[13]) / (float) ah, (tx0 + uo + q[14]) / aw, (ty0 + q[15]) / (float) ah, slot, lit, 0xFFFFFF, add, false);
            }
        }
    }

    /**
     * An item as the game shows one dropped: its 16 x 16 icon a pixel deep - front, back, and a strip on every edge of
     * a texel that borders a hole - {@code size} across, centred on the origin, posed by {@code m}. The icon is at
     * ({@code ax}, {@code ay}) in the atlas; {@code px} its own 256 pixels, to find the holes.
     */
    static void icon(ModelRaster r, int slot, float[] m, float size, int[] px, int add, Cam cam, int ax, int ay, int aw, int ah) {
        float h = size / 2f, t = size / 16f, d = t;
        quad(r, slot, m, add, cam, -h, h, d / 2f, size, 0, 0, 0, -size, 0, 0, 0, 1, ax, ay, ax + 16, ay + 16, aw, ah);
        quad(r, slot, m, add, cam, -h, h, -d / 2f, size, 0, 0, 0, -size, 0, 0, 0, -1, ax, ay, ax + 16, ay + 16, aw, ah);
        for (int j = 0; j < 16; j++) {
            for (int i = 0; i < 16; i++) {
                if ((px[j * 16 + i] >>> 24) == 0) continue;
                float x0 = -h + i * t, yt = h - j * t;
                int u0 = ax + i, v0 = ay + j;
                if (i == 0 || (px[j * 16 + i - 1] >>> 24) == 0)
                    quad(r, slot, m, add, cam, x0, yt, -d / 2f, 0, 0, d, 0, -t, 0, -1, 0, 0, u0, v0, u0 + 1, v0 + 1, aw, ah);
                if (i == 15 || (px[j * 16 + i + 1] >>> 24) == 0)
                    quad(r, slot, m, add, cam, x0 + t, yt, d / 2f, 0, 0, -d, 0, -t, 0, 1, 0, 0, u0, v0, u0 + 1, v0 + 1, aw, ah);
                if (j == 0 || (px[(j - 1) * 16 + i] >>> 24) == 0)
                    quad(r, slot, m, add, cam, x0, yt, -d / 2f, t, 0, 0, 0, 0, d, 0, 1, 0, u0, v0, u0 + 1, v0 + 1, aw, ah);
                if (j == 15 || (px[(j + 1) * 16 + i] >>> 24) == 0)
                    quad(r, slot, m, add, cam, x0, yt - t, d / 2f, t, 0, 0, 0, 0, -d, 0, -1, 0, u0, v0, u0 + 1, v0 + 1, aw, ah);
            }
        }
    }

    /** One quad in a model's own frame - corner, two edges, its normal - posed by {@code m}, culled if turned away. */
    private static void quad(ModelRaster r, int slot, float[] m, int add, Cam cam, float ox, float oy, float oz,
                             float ux, float uy, float uz, float vx, float vy, float vz, float nx0, float ny0, float nz0,
                             int u0, int v0, int u1, int v1, int aw, int ah) {
        float[] wn = apply(m, new float[]{nx0, ny0, nz0}, false);
        float ln = (float) Math.sqrt(wn[0] * wn[0] + wn[1] * wn[1] + wn[2] * wn[2]);
        if (ln < 1e-6f) return;
        float nx = wn[0] / ln, ny = wn[1] / ln, nz = wn[2] / ln;
        float facing = -ny * cam.sp() + (-nx * cam.sy() + nz * cam.cy()) * cam.cp();
        if (facing <= 0.02f) return;
        float lit = 0.62f + 0.38f * Math.max(0f, nx * L[0] + ny * L[1] + nz * L[2]);
        float[] wo = apply(m, new float[]{ox, oy, oz}, true), wu = apply(m, new float[]{ux, uy, uz}, false), wv = apply(m, new float[]{vx, vy, vz}, false);
        float ax = wo[0], ay = wo[1], az = wo[2];
        float px = cam.px(ax, ay, az), py = cam.py(ax, ay, az), d0 = cam.depth(ax, ay, az);
        float qx = cam.px(ax + wu[0], ay + wu[1], az + wu[2]) - px, qy = cam.py(ax + wu[0], ay + wu[1], az + wu[2]) - py;
        float du = cam.depth(ax + wu[0], ay + wu[1], az + wu[2]) - d0;
        float rx = cam.px(ax + wv[0], ay + wv[1], az + wv[2]) - px, ry = cam.py(ax + wv[0], ay + wv[1], az + wv[2]) - py;
        float dv = cam.depth(ax + wv[0], ay + wv[1], az + wv[2]) - d0;
        r.face(px, py, qx, qy, rx, ry, d0, du, dv, Float.NaN, Float.NaN, Float.NaN,
                u0 / (float) aw, v0 / (float) ah, u1 / (float) aw, v1 / (float) ah, slot, lit, 0xFFFFFF, add, false);
    }

    /** {@code m} turned {@code angle} about the x axis through the origin - a head stood on its bottom face. */
    static float[] tilted(float[] m, float angle) {
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        return compose(m, new float[]{1, 0, 0, 0, c, s, 0, -s, c}, new float[]{0, 0, 0});
    }

    /** {@code m} turned {@code angle} about the y axis through the origin. */
    static float[] yawed(float[] m, float angle) {
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        return compose(m, new float[]{c, 0, -s, 0, 1, 0, s, 0, c}, new float[]{0, 0, 0});
    }

    /** The screen rectangle a head covers once posed, into {@code acc}. */
    static void headBounds(float[] m, float half, float[] acc, Cam cam) {
        float h = half * 17f / 16f;
        for (int i = 0; i < 8; i++) {
            float[] w = apply(m, new float[]{(i & 1) == 0 ? -h : h, (i & 2) == 0 ? -h : h, (i & 4) == 0 ? -h : h}, true);
            float sx = cam.px(w[0], w[1], w[2]), sy = cam.py(w[0], w[1], w[2]);
            acc[0] = Math.min(acc[0], sx);
            acc[1] = Math.min(acc[1], sy);
            acc[2] = Math.max(acc[2], sx);
            acc[3] = Math.max(acc[3], sy);
        }
    }

    /** The screen rectangle a box covers once posed (for sizing a raster), into {@code acc} {minX, minY, maxX, maxY}. */
    static void bounds(Box b, float[] m, float[] acc, Cam cam) {
        float c = (float) Math.cos(b.rot), s = (float) Math.sin(b.rot);
        for (int i = 0; i < 8; i++) {
            float[] p = turn((i & 1) == 0 ? b.x0 : b.x1, (i & 2) == 0 ? b.y0 : b.y1, (i & 4) == 0 ? b.z0 : b.z1, c, s, b.ox, b.oy, true);
            float[] w = apply(m, p, true);
            float sx = cam.px(w[0], w[1], w[2]), sy = cam.py(w[0], w[1], w[2]);
            acc[0] = Math.min(acc[0], sx);
            acc[1] = Math.min(acc[1], sy);
            acc[2] = Math.max(acc[2], sx);
            acc[3] = Math.max(acc[3], sy);
        }
    }

    private static int scale(int rgb, float k) {
        int r = Math.min(255, Math.round((rgb >> 16 & 255) * k)), g = Math.min(255, Math.round((rgb >> 8 & 255) * k)),
                bl = Math.min(255, Math.round((rgb & 255) * k));
        return r << 16 | g << 8 | bl;
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
}
