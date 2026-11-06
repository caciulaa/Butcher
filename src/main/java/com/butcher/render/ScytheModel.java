package com.butcher.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The Reaper's scythe: a blocky 3D model built in Blender (2026-10-26 - the user asked for a 3D scythe in place of the
 * pixel one), baked to {@code assets/butcher/scythe.json} as boxes in model pixels: the haft up +Y from the grip at the
 * origin, the blade out along -X from the collar at the top. Drawn through a {@link BoxRaster} in flat colours. Pure.
 */
final class ScytheModel {
    private ScytheModel() {}

    /** One box; {@code blade} for the steel, its edge and spine (what the smear draws). */
    record Part(float[] c, float[] h, float[] x, float[] y, float[] z, int rgb, boolean blade) {
    }

    static final List<Part> PARTS = load();
    static final BoxRaster RASTER = new BoxRaster();
    static final String TEX_IMAGE = "slayer_scythe_raster";

    static {
        int[] white = new int[16];
        java.util.Arrays.fill(white, 0xFFFFFFFF);
        RASTER.texture(0, white, 4, 4);
    }

    private static int colour(String kind) {
        return switch (kind) {
            case "haft" -> 0x5A3A1E;
            case "grip" -> 0x2E1E10;
            case "collar" -> 0x5A6068;
            case "spine" -> 0x6E7C8A;
            case "edge" -> 0xF2F8FF;
            default -> 0xB4C4D2;
        };
    }

    private static List<Part> load() {
        List<Part> out = new ArrayList<>();
        try (InputStream in = ScytheModel.class.getResourceAsStream("/assets/butcher/scythe.json")) {
            if (in == null) return out;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("boxes")) {
                JsonObject o = e.getAsJsonObject();
                String kind = o.get("kind").getAsString();
                out.add(new Part(vec(o.getAsJsonArray("c")), vec(o.getAsJsonArray("h")), vec(o.getAsJsonArray("X")), vec(o.getAsJsonArray("Y")),
                        vec(o.getAsJsonArray("Z")), colour(kind), kind.equals("steel") || kind.equals("edge") || kind.equals("spine")));
            }
        } catch (Exception e) {
            // No scythe: the Reaper still cuts, unseen.
        }
        return out;
    }

    private static float[] vec(JsonArray a) {
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    private static final int[][] FACES = {{0, 0, 4, 4}, {0, 0, 4, 4}, {0, 0, 4, 4}, {0, 0, 4, 4}, {0, 0, 4, 4}, {0, 0, 4, 4}};

    /**
     * Queues the scythe with its grip at {@code pivot}, its haft along {@code up} (world), the blade leading along
     * {@code lead}, {@code s} world units a model pixel - only the blade, for a smear. Model X goes to -lead, Y to up,
     * Z to their cross.
     */
    static void pose(BoxRaster r, float[] pivot, float[] up, float[] lead, float s, boolean bladeOnly) {
        float[] mx = {-lead[0], -lead[1], -lead[2]};
        float[] mz = SlayerScene.cross(mx, up);
        SlayerScene.normalize(mz);
        for (Part p : PARTS) {
            if (bladeOnly && !p.blade) continue;
            float[] c = map(p.c, mx, up, mz);
            float[] X = map(p.x, mx, up, mz), Y = map(p.y, mx, up, mz), Z = map(p.z, mx, up, mz);
            r.box(pivot[0] + c[0] * s, pivot[1] + c[1] * s, pivot[2] + c[2] * s, X, Y, Z, p.h[0] * s, p.h[1] * s, p.h[2] * s, FACES, 0,
                    p.rgb, 0, Float.NaN);
        }
    }

    /** Where the blade's point is, in the world, for the same pose (the smear follows it). */
    static float[] point(float[] pivot, float[] up, float[] lead, float s) {
        float[] mx = {-lead[0], -lead[1], -lead[2]};
        float[] mz = SlayerScene.cross(mx, up);
        SlayerScene.normalize(mz);
        float[] tip = map(new float[]{-31.5f, 34.8f, 0f}, mx, up, mz);
        return new float[]{pivot[0] + tip[0] * s, pivot[1] + tip[1] * s, pivot[2] + tip[2] * s};
    }

    private static float[] map(float[] v, float[] mx, float[] my, float[] mz) {
        return new float[]{v[0] * mx[0] + v[1] * my[0] + v[2] * mz[0], v[0] * mx[1] + v[1] * my[1] + v[2] * mz[1],
                v[0] * mx[2] + v[1] * my[2] + v[2] * mz[2]};
    }
}
