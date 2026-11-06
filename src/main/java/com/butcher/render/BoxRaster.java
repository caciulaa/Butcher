package com.butcher.render;

/**
 * Model boxes (the Warden, the Wake's zombies) through {@link ModelRaster}: a real depth buffer, so a model's parts
 * never draw through each other, and {@code cutBelow} so a body climbing out of the earth is cut off exactly at the
 * ground. Faces follow ChopScene.boxAxes's texture layout, so a Minecraft model's UV rects map the same way; lit the
 * same way. Pure: the preview and the game draw the same pixels. One instance per scene, render thread only.
 */
final class BoxRaster {
    final ModelRaster raster = new ModelRaster();
    private final java.util.List<float[]> faces = new java.util.ArrayList<>();
    private final java.util.List<int[]> meta = new java.util.ArrayList<>();
    private final int[] texW = new int[8], texH = new int[8];

    void texture(int slot, int[] argb, int w, int h) {
        raster.texture(slot, argb, w, h);
        texW[slot] = w;
        texH[slot] = h;
    }

    boolean has(int slot) {
        return raster.has(slot);
    }

    void begin() {
        faces.clear();
        meta.clear();
    }

    /**
     * A box: centre, its own axes (unit vectors), half sizes, a texel rect per face {+X, -X, +Y, -Y, +Z, -Z} on texture
     * {@code slot} (null skips a face), a tint (RGB multiply), and the ground under which nothing of it is drawn.
     */
    void box(float cx, float cy, float cz, float[] X, float[] Y, float[] Z, float hx, float hy, float hz, int[][] rects, int slot,
             int tint, int add, float cutBelow) {
        float[][] axes = {X, Y, Z};
        float[] half = {hx, hy, hz};
        for (int axis = 0; axis < 3; axis++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                int fi = axis * 2 + (sign > 0 ? 0 : 1);
                if (rects[fi] == null) continue;
                float[] n = axes[axis];
                float nx = n[0] * sign, ny = n[1] * sign, nz = n[2] * sign;
                float facing = -ny * ChopScene.sp + (-nx * ChopScene.sy + nz * ChopScene.cy) * ChopScene.cp;
                if (facing <= 0.02f) continue;
                int ua = axis == 1 ? 0 : (axis == 0 ? 2 : 0), va = axis == 1 ? 2 : 1;
                float[] U = axes[ua], V = axes[va];
                float uh = half[ua], vh = half[va];
                float us = (axis == 0 && sign > 0) || (axis == 2 && sign < 0) ? -1f : 1f;
                float vsgn = axis == 1 ? 1f : -1f;
                float fx0 = cx + nx * half[axis], fy0 = cy + ny * half[axis], fz0 = cz + nz * half[axis];
                float ox = fx0 - U[0] * uh * us - V[0] * vh * vsgn, oy = fy0 - U[1] * uh * us - V[1] * vh * vsgn,
                        oz = fz0 - U[2] * uh * us - V[2] * vh * vsgn;
                float ux = U[0] * 2 * uh * us, uy = U[1] * 2 * uh * us, uz = U[2] * 2 * uh * us;
                float vx = V[0] * 2 * vh * vsgn, vy = V[1] * 2 * vh * vsgn, vz = V[2] * 2 * vh * vsgn;
                float lambert = Math.max(0f, nx * -0.4f + ny * 0.8f + nz * 0.45f);
                float lit = 0.55f + 0.45f * lambert;
                int[] r = rects[fi];
                faces.add(new float[]{ox, oy, oz, ux, uy, uz, vx, vy, vz, lit, cutBelow,
                        r[0] / (float) texW[slot], r[1] / (float) texH[slot], (r[0] + r[2]) / (float) texW[slot], (r[1] + r[3]) / (float) texH[slot]});
                meta.add(new int[]{slot, tint, add});
            }
        }
    }

    /** Rasterises every box queued and draws the one image, outlined, at {@code alpha}. */
    void draw(ChopScene.Canvas c, String key, int w, int h, float scale, float alpha) {
        draw(c, key, w, h, scale, alpha, true);
    }

    /**
     * {@link #draw}, outlined or not (a smear has none). Each image drawn in a frame needs its own {@code key}: the game
     * uploads each key's texture as it is drawn, and draws them all later.
     */
    void draw(ChopScene.Canvas c, String key, int w, int h, float scale, float alpha, boolean outline) {
        if (faces.isEmpty() || alpha <= 0.02f) return;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float[][] scr = new float[faces.size()][];
        for (int i = 0; i < faces.size(); i++) {
            float[] f = faces.get(i);
            float[] o = new float[12];
            float[][] pts = {{f[0], f[1], f[2]}, {f[0] + f[3], f[1] + f[4], f[2] + f[5]}, {f[0] + f[6], f[1] + f[7], f[2] + f[8]}};
            for (int k = 0; k < 3; k++) {
                ChopScene.project(pts[k][0], pts[k][1], pts[k][2]);
                o[k * 2] = ChopScene.P[0];
                o[k * 2 + 1] = ChopScene.P[1];
                o[6 + k] = ChopScene.depth(pts[k][0], pts[k][1], pts[k][2]);
                o[9 + k] = pts[k][1];
            }
            float fx = o[2] + o[4] - o[0], fy = o[3] + o[5] - o[1];
            minX = Math.min(minX, Math.min(Math.min(o[0], o[2]), Math.min(o[4], fx)));
            maxX = Math.max(maxX, Math.max(Math.max(o[0], o[2]), Math.max(o[4], fx)));
            minY = Math.min(minY, Math.min(Math.min(o[1], o[3]), Math.min(o[5], fy)));
            maxY = Math.max(maxY, Math.max(Math.max(o[1], o[3]), Math.max(o[5], fy)));
            scr[i] = o;
        }
        float ink = Math.max(1.2f, ChopScene.size / 3.2f);
        float pad = ink + 2f;
        // A wide image (the Wake's whole yard of zombies) at one pixel a GUI pixel: at two it was half a million a
        // frame to rasterise and upload.
        if ((maxX - minX + 2 * pad) * (maxY - minY + 2 * pad) * scale * scale > 160_000f) scale = 1f;
        if (!raster.begin(Math.max(0f, minX - pad), Math.max(0f, minY - pad), Math.min(w, maxX + pad), Math.min(h, maxY + pad), scale)) return;
        for (int i = 0; i < faces.size(); i++) {
            float[] f = faces.get(i), o = scr[i];
            int[] m = meta.get(i);
            raster.cutBelow = f[10];
            raster.face(o[0], o[1], o[2] - o[0], o[3] - o[1], o[4] - o[0], o[5] - o[1],
                    o[6], o[7] - o[6], o[8] - o[6], o[9], o[10] - o[9], o[11] - o[9],
                    f[11], f[12], f[13], f[14], m[0], f[9], m[1], m[2], false);
        }
        raster.cutBelow = Float.NaN;
        if (outline) raster.ink(Math.max(1, Math.round(ink * raster.scale)));
        raster.fade(alpha * ChopScene.sceneAlpha);
        c.image(key, raster.px, raster.w, raster.h, raster.x, raster.y, raster.w / raster.scale, raster.h / raster.scale);
    }

    /** Box axes for a part turned {@code ay} about Y then tipped {@code ax} about its own X (an arm raised, a body leaning). */
    static float[][] axes(float ax, float ay, float az) {
        float ca = (float) Math.cos(ax), sa = (float) Math.sin(ax);
        float cb = (float) Math.cos(ay), sb = (float) Math.sin(ay);
        float cc = (float) Math.cos(az), sc = (float) Math.sin(az);
        float[] X = {cb * cc, cb * sc, -sb};
        float[] Y = {sa * sb * cc - ca * sc, sa * sb * sc + ca * cc, sa * cb};
        float[] Z = {ca * sb * cc + sa * sc, ca * sb * sc - sa * cc, ca * cb};
        return new float[][]{X, Y, Z};
    }
}
