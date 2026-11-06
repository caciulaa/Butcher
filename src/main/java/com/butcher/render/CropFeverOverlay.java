package com.butcher.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * Plays {@link CropFeverScene} in the game while a Crop Fever lasts - drawn in the HUD pass by ButcherClient.drawOwnHud,
 * under the small pop-ups (the fever's own drops show over it). Started by the fever's "WOAH!" line, cured by its "GONE!"
 * line or by running out; a world change ends it.
 */
public final class CropFeverOverlay implements ChopScene.Canvas {
    private static final CropFeverOverlay CANVAS = new CropFeverOverlay();

    private static CropFeverScene.Fever fever;
    private static long startedAt;

    private GuiGraphicsExtractor g;

    private CropFeverOverlay() {}

    /** A fever of {@code seconds} has begun. */
    public static void start(int seconds) {
        CropFeverScene.Fever f = new CropFeverScene.Fever();
        f.duration = Math.max(5, seconds) * 1000L;
        f.seed = System.nanoTime();
        fever = f;
        startedAt = System.currentTimeMillis();
    }

    /** It has been cured (or the world changed): the glow cools and goes. */
    public static void cure() {
        if (fever == null || fever.curedAt >= 0) return;
        fever.curedAt = Math.min(System.currentTimeMillis() - startedAt, fever.duration);
    }

    public static void stop() {
        fever = null;
    }

    public static boolean active() {
        return fever != null && System.currentTimeMillis() - startedAt < CropFeverScene.end(fever);
    }

    /** Whether a fever is on now, for the drops' pop-ups ("Crop Fever" under their name). */
    public static boolean feverOn() {
        return fever != null && fever.curedAt < 0 && System.currentTimeMillis() - startedAt < fever.duration + 2000;
    }

    public static void draw(GuiGraphicsExtractor g) {
        if (!active()) {
            fever = null;
            return;
        }
        CANVAS.g = g;
        try {
            CropFeverScene.draw(CANVAS, fever, System.currentTimeMillis() - startedAt, g.guiWidth(), g.guiHeight());
        } finally {
            CANVAS.g = null;
        }
    }

    // ================================================================== the canvas

    @Override
    public void fill(float x, float y, float w, float h, int argb) {
        if ((argb >>> 24) < 3) return;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(w, h);
        g.fill(0, 0, 1, 1, argb);
        pose.popMatrix();
    }

    @Override
    public void sprite(String tex, float cx, float cy, float w, float h, float rot, int argb) {
        if ((argb >>> 24) < 2 || w < 0.5f || h < 0.5f) return;
        Identifier id = TextureBank.id(tex);
        if (id == null) return;
        int tw = TextureBank.width(tex), th = TextureBank.height(tex);
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        if (rot != 0f) pose.rotate((float) Math.toRadians(rot));
        pose.scale(w / tw, h / th);
        pose.translate(-tw / 2f, -th / 2f);
        g.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0, 0, tw, th, tw, th, argb);
        pose.popMatrix();
    }

    @Override
    public void text(String s, float cx, float cy, float scale, int argb) {
        int alpha = argb >>> 24;
        if (alpha < 6) return;
        Font font = Minecraft.getInstance().font;
        Component text = Component.literal(s);
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.scale(scale, scale);
        int x = Math.round(-font.width(text) / 2f), y = -4;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                if (ox != 0 || oy != 0) g.text(font, text, x + ox, y + oy, alpha << 24, false);
            }
        }
        g.text(font, text, x, y, argb, false);
        pose.popMatrix();
    }

    @Override public void quad(String tex, float ox, float oy, float ux, float uy, float vx, float vy, int u0, int v0, int uw, int vh, int argb) { }
    @Override public void line(float x0, float y0, float x1, float y1, float width, int argb) { }
    @Override public void item(float cx, float cy, float size, float alpha, float yaw) { }
    @Override public void name(float cx, float cy, float scale, int argb, int letters) { }
    @Override public String creatureSkin(com.butcher.anim.ChopTimeline.Creature creature) { return null; }
    @Override public String dropSkin() { return null; }
}
