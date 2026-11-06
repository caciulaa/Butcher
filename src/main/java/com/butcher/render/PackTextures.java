package com.butcher.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Textures read out of whatever resource packs are loaded - Hypixel's own SkyBlock pack above all - adopted into the
 * TextureBank to draw, never bundled. An animated one (Divan's Alloy: frames stacked down a strip, its .mcmeta saying
 * which in what order and how long each holds) is played as the game plays it, {@link #frameNow} giving the frame.
 */
public final class PackTextures {
    private PackTextures() {}

    /** {width, height} by bank key; a path no pack has is not remembered (the pack may come with the server). */
    private static final Map<String, int[]> SIZES = new HashMap<>();
    /** An animated texture's frames (in play order) and ticks per frame, by bank key; absent for a still one. */
    private static final Map<String, Anim> ANIMS = new HashMap<>();

    public record Anim(int[] frames, int[] ticks, boolean interpolate, int count) {}

    /** The bank key of {@code path} ("hypixel_skyblock:textures/item/....png") out of the loaded packs, or null. */
    public static String get(String path) {
        Identifier id = Identifier.tryParse(path);
        if (id == null) return null;
        var res = Minecraft.getInstance().getResourceManager().getResource(id);
        if (res.isEmpty()) return null;
        String key = "rngpack:" + path;
        int[] size = SIZES.get(key);
        if (size == null) {
            try (InputStream in = res.get().open()) {
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(in);
                if (img == null) return null;
                size = new int[]{img.getWidth(), img.getHeight()};
                SIZES.put(key, size);
            } catch (Exception e) {
                return null;
            }
            readAnim(key, id, size);
        }
        TextureBank.adopt(key, id, size[0], size[1]);
        return key;
    }

    private static void readAnim(String key, Identifier id, int[] size) {
        if (size[1] <= size[0] || size[1] % size[0] != 0) return;
        int count = size[1] / size[0];
        Identifier meta = Identifier.tryParse(id + ".mcmeta");
        if (meta == null) return;
        var res = Minecraft.getInstance().getResourceManager().getResource(meta);
        if (res.isEmpty()) return;
        try (InputStream in = res.get().open()) {
            Anim a = parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject(), count);
            if (a != null) ANIMS.put(key, a);
        } catch (Exception e) {
            // A broken .mcmeta shows the first frame.
        }
    }

    /** An .mcmeta's animation: frames as numbers or {index, time}; the default order is top to bottom. */
    public static Anim parse(JsonObject root, int count) {
        if (!root.has("animation")) return null;
        JsonObject an = root.getAsJsonObject("animation");
        int frametime = an.has("frametime") ? Math.max(1, an.get("frametime").getAsInt()) : 1;
        boolean interp = an.has("interpolate") && an.get("interpolate").getAsBoolean();
        int[] frames, ticks;
        if (an.has("frames")) {
            JsonArray arr = an.getAsJsonArray("frames");
            frames = new int[arr.size()];
            ticks = new int[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                JsonElement e = arr.get(i);
                if (e.isJsonObject()) {
                    frames[i] = e.getAsJsonObject().get("index").getAsInt();
                    ticks[i] = e.getAsJsonObject().has("time") ? Math.max(1, e.getAsJsonObject().get("time").getAsInt()) : frametime;
                } else {
                    frames[i] = e.getAsInt();
                    ticks[i] = frametime;
                }
                frames[i] = Math.floorMod(frames[i], count);
            }
        } else {
            frames = new int[count];
            ticks = new int[count];
            for (int i = 0; i < count; i++) {
                frames[i] = i;
                ticks[i] = frametime;
            }
        }
        if (frames.length == 0) return null;
        return new Anim(frames, ticks, interp, count);
    }

    public static int width(String key) {
        int[] s = SIZES.get(key);
        return s == null ? 16 : s[0];
    }

    /** {this frame, the next, how far to it 0..1 (0 unless it interpolates)} now; null for a still texture. */
    public static float[] frameNow(String key) {
        Anim a = ANIMS.get(key);
        return a == null ? null : frameAt(a, System.currentTimeMillis());
    }

    /** The frame of {@code a} at {@code ms} - a game tick is 50 ms. */
    public static float[] frameAt(Anim a, long ms) {
        int total = 0;
        for (int t : a.ticks()) total += t;
        float tick = (ms % (total * 50L)) / 50f;
        int i = 0;
        while (i < a.frames().length - 1 && tick >= a.ticks()[i]) {
            tick -= a.ticks()[i];
            i++;
        }
        int next = a.frames()[(i + 1) % a.frames().length];
        float k = a.interpolate() ? Math.min(1f, tick / a.ticks()[i]) : 0f;
        return new float[]{a.frames()[i], next, k};
    }
}
