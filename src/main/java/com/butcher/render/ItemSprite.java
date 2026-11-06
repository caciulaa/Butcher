package com.butcher.render;

/**
 * A flat item texture turned about its upright on the screen, as a dropped item turns: it narrows as it goes edge-on and
 * shows a texel of thickness, never mirrored (a flipped quad may be culled). Pure - drawn through a Canvas - so a scene
 * and its offline preview show the same thing. An animated texture (Divan's Alloy) plays its .mcmeta frames.
 */
public final class ItemSprite {
    private ItemSprite() {}

    public static void spin(ChopScene.Canvas c, String key, float cx, float cy, float size, float yawDeg, float alpha) {
        if (alpha <= 0.02f || size < 2f) return;
        double yaw = Math.toRadians(yawDeg);
        float cs = (float) Math.cos(yaw), sn = (float) Math.sin(yaw);
        float wdt = size * Math.max(0.08f, Math.abs(cs));
        int ts = c.textureSize(key);
        float[] fr = c.frameNow(key);
        int v0 = fr == null ? 0 : Math.round(fr[0]) * ts;
        float texel = size / ts;
        int layers = 4;
        int a8 = Math.round(255 * Math.max(0f, Math.min(1f, alpha * ChopScene.sceneAlpha)));
        float ink = Math.max(1.2f, size / 18f);
        float edge = sn * texel * 0.5f * (cs >= 0 ? 1f : -1f);
        float fx = cx - wdt / 2f + edge * (layers - 1) / 2f, fy = cy - size / 2f;
        for (float[] o : ChopScene.INK) c.quad(key, fx + o[0] * ink, fy + o[1] * ink, wdt, 0f, 0f, size, 0, v0, ts, ts, a8 << 24);
        for (int l = 0; l < layers; l++) {
            // The back layers darker: the edge reads as thickness.
            float k = l == layers - 1 ? 1f : 0.5f + 0.1f * l;
            int v = ChopScene.channel(k);
            float ox = cx - wdt / 2f + edge * (l - (layers - 1) / 2f) + edge * (layers - 1) / 2f;
            c.quad(key, ox, fy, wdt, 0f, 0f, size, 0, v0, ts, ts, a8 << 24 | v << 16 | v << 8 | v);
            if (l == layers - 1 && fr != null && fr[2] > 0.02f) {
                int na = Math.round(a8 * fr[2]);
                c.quad(key, ox, fy, wdt, 0f, 0f, size, 0, Math.round(fr[1]) * ts, ts, ts, na << 24 | 0xFFFFFF);
            }
        }
    }
}
