package com.butcher.render;

import com.butcher.audio.ButcherAudio;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

import java.util.ArrayDeque;

/**
 * Plays {@link DropPopupScene} in the game: the small pop-up over the hotbar for an Automaton's robot part, a Summoning
 * Eye, a Crop Fever drop or a Garden visitor's gift. Not a cutscene - nothing is dimmed or hidden; drawn after the HUD by
 * {@code ButcherClient.drawOwnHud}, as Fin and Wisp are. The drop is the real item ({@link DropArt}, {@link DropIcon}).
 *
 * A Crop Fever can give drops seconds apart, so a harvest waits its turn behind the one showing (up to four); any other
 * pop-up starts over the top, and a harvest it cuts short goes back to the front of the line.
 */
public final class DropPopup implements ChopScene.Canvas {
    private static final DropPopup CANVAS = new DropPopup();

    private record Entry(DropPopupScene.Pop pop, DropArt art) {
    }

    private static Entry current;
    private static long startedAt = -1;
    private static boolean first, second, revealed;
    private static final ArrayDeque<Entry> queue = new ArrayDeque<>();

    private GuiGraphicsExtractor g;
    private final Matrix3x2f matrix = new Matrix3x2f();
    private final DropIcon icons = new DropIcon();

    private DropPopup() {}

    private static DropPopupScene.Pop pop(DropPopupScene.Kind kind, String name, int colour, String line, float intensity) {
        DropPopupScene.Pop p = new DropPopupScene.Pop();
        p.kind = kind;
        p.name = name;
        p.colour = colour;
        p.line = line == null ? "" : line;
        p.seed = System.currentTimeMillis() * 31 + name.hashCode();
        p.intensity = intensity;
        return p;
    }

    /** The Automaton's robot part. */
    public static void showAutomaton(String name, int colour, String line, float intensity, DropArt art) {
        start(new Entry(pop(DropPopupScene.Kind.AUTOMATON, name, colour, line, intensity), art));
    }

    /** The rift: a Summoning Eye. */
    public static void showEye(String name, int colour, String line, float intensity, DropArt art) {
        start(new Entry(pop(DropPopupScene.Kind.EYE, name, colour, line, intensity), art));
    }

    /** The harvest: a Crop Fever drop - queued behind one already showing. */
    public static void showCrop(String name, int colour, String line, float intensity, DropArt art) {
        Entry e = new Entry(pop(DropPopupScene.Kind.CROP, name, colour, line, intensity), art);
        if (active() && (current.pop.kind == DropPopupScene.Kind.CROP || !queue.isEmpty())) {
            // Four waiting is plenty: past that the commonest gives way.
            if (queue.size() >= 4) {
                Entry least = null;
                for (Entry q : queue) if (least == null || q.pop.intensity < least.pop.intensity) least = q;
                if (least != null && least.pop.intensity <= intensity) queue.remove(least);
                else return;
            }
            queue.add(e);
            return;
        }
        start(e);
    }

    /** The gift: a Garden visitor's reward. {@code accent} is the visitor's rarity colour, for its rim. */
    public static void showGarden(String name, int colour, int accent, String line, float intensity, DropArt art) {
        DropPopupScene.Pop p = pop(DropPopupScene.Kind.GARDEN, name, colour, line, intensity);
        p.accent = accent;
        if (active() && current.pop.kind == DropPopupScene.Kind.CROP) queue.addFirst(current);
        start(new Entry(p, art));
    }

    private static void start(Entry e) {
        current = e;
        startedAt = System.currentTimeMillis();
        first = second = revealed = false;
    }

    public static boolean active() {
        return current != null && elapsed() < DropPopupScene.end(current.pop);
    }

    /** Also true while drops wait their turn: the HUD keeps drawing. */
    public static boolean busy() {
        return active() || !queue.isEmpty();
    }

    /** Leaving the world: nothing carries over. */
    public static void stop() {
        current = null;
        queue.clear();
    }

    /** Milliseconds into the pop-up, at the player's pop-up speed. */
    private static long elapsed() {
        int pct = com.butcher.ButcherClient.config == null ? 100 : com.butcher.ButcherClient.config.popupSpeed;
        return (long) ((System.currentTimeMillis() - startedAt) * Math.max(0.5f, Math.min(2f, pct / 100f)));
    }

    public static void draw(GuiGraphicsExtractor g) {
        if (!active()) {
            current = null;
            Entry next = queue.poll();
            if (next == null) return;
            start(next);
        }
        long t = elapsed();
        sounds(t);
        CANVAS.g = g;
        Font font = Minecraft.getInstance().font;
        // Its squares as a few images, not hundreds of rectangles (BatchCanvas) - a fever's harvests queue up while farming.
        int w = g.guiWidth(), h = g.guiHeight();
        LAYERS.begin(g, w, h, DropPopupScene.P);
        BATCH.begin(CANVAS, LAYERS, w, h, DropPopupScene.P);
        try {
            DropPopupScene.draw(BATCH, (cx, cy, size, alpha, yaw) -> {
                BATCH.beforeItem(cx, cy, size);
                CANVAS.icons.draw(g, CANVAS, current.art, cx, cy, size, alpha, yaw);
            }, (text, cx, cy, argb) -> {
                float hw = font.width(text) / 2f + 2f;
                BATCH.before(cx - hw, cy - 6f, cx + hw, cy + 6f);
                outlined(g, font, text, cx, cy, argb);
            }, current.pop, t, w, h);
        } finally {
            BATCH.end();
            LAYERS.end();
            CANVAS.g = null;
        }
    }

    private static final BatchCanvas BATCH = new BatchCanvas();
    private static final PixelBatch LAYERS = new PixelBatch("drop_popup");

    private static void outlined(GuiGraphicsExtractor g, Font font, String s, float cx, float cy, int argb) {
        int alpha = argb >>> 24;
        if (alpha < 6) return;
        Component text = Component.literal(s);
        int x = Math.round(cx - font.width(text) / 2f), y = Math.round(cy - 4);
        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                if (ox != 0 || oy != 0) g.text(font, text, x + ox, y + oy, alpha << 24, false);
            }
        }
        g.text(font, text, x, y, argb, false);
    }

    // ================================================================== sound

    /** Each kind on its own volume (the menu's Pop-ups group). */
    private static float gain(DropPopupScene.Kind kind) {
        com.butcher.config.PatternConfig c = com.butcher.ButcherClient.config;
        int pct = c == null ? 100 : switch (kind) {
            case AUTOMATON -> c.automatonVolume;
            case EYE -> c.eyeVolume;
            case CROP, GARDEN -> c.gardenVolume;
        };
        return Math.max(0f, Math.min(1.5f, pct / 100f));
    }

    private static void sounds(long t) {
        DropPopupScene.Pop p = current.pop;
        float gain = gain(p.kind);
        switch (p.kind) {
            case AUTOMATON -> {
                if (!first && t >= DropPopupScene.GRIND) {
                    first = true;
                    ButcherAudio.onPopupGrind(p.seed, gain);
                }
                if (!second && t >= DropPopupScene.BURST) {
                    second = true;
                    ButcherAudio.onPopupBurst(p.seed, gain);
                }
                if (!revealed && t >= DropPopupScene.POPPED) {
                    revealed = true;
                    ButcherAudio.onPopupChime(3, 261.63f, gain);
                }
            }
            case EYE -> {
                if (!first) {
                    first = true;
                    ButcherAudio.onPopupRift(DropPopupScene.EYE_OUT, p.seed, gain);
                }
                if (!second && t >= DropPopupScene.EYE_OUT) {
                    second = true;
                    ButcherAudio.onPopupEnderPop(p.seed, gain);
                }
                if (!revealed && t >= DropPopupScene.EYE_POPPED) {
                    revealed = true;
                    ButcherAudio.onPopupChime(p.intensity >= 0.9f ? 5 : 4, 311.13f, gain);
                }
            }
            case GARDEN -> {
                if (!first && t >= DropPopupScene.G_GIFT) {
                    first = true;
                    ButcherAudio.onPopupSwish(p.seed, gain);
                }
                if (!revealed && t >= DropPopupScene.G_POPPED) {
                    revealed = true;
                    ButcherAudio.onPopupChime(p.intensity >= 0.9f ? 5 : 4, 349.23f, gain);
                }
            }
            case CROP -> {
                if (!first && t >= DropPopupScene.SWING) {
                    first = true;
                    ButcherAudio.onPopupSwish(p.seed, gain);
                }
                if (!revealed && t >= DropPopupScene.CROP_POPPED) {
                    revealed = true;
                    // Two notes for an Uncommon, five for Pray to RNGesus, each tier a step up.
                    int notes = 2 + Math.round(3 * p.intensity);
                    ButcherAudio.onPopupChime(notes, 261.63f * (float) Math.pow(2, Math.round(4 * p.intensity) / 12.0), gain);
                }
            }
        }
    }

    // ================================================================== the canvas

    @Override
    public void quad(String tex, float ox, float oy, float ux, float uy, float vx, float vy, int u0, int v0, int uw, int vh, int argb) {
        Identifier id = TextureBank.id(tex);
        if (id == null || uw <= 0 || vh <= 0) return;
        matrix.set(ux / uw, uy / uw, vx / vh, vy / vh, ox, oy);
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.mul(matrix);
        g.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, u0, v0, uw, vh, TextureBank.width(tex), TextureBank.height(tex), argb);
        pose.popMatrix();
    }

    @Override
    public void sprite(String tex, float cx, float cy, float w, float h, float rot, int argb) {
    }

    @Override
    public void line(float x0, float y0, float x1, float y1, float width, int argb) {
    }

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

    @Override public void item(float cx, float cy, float size, float alpha, float yaw) { }
    @Override public void name(float cx, float cy, float scale, int argb, int letters) { }
    @Override public void text(String text, float cx, float cy, float scale, int argb) { }
    @Override public String creatureSkin(com.butcher.anim.ChopTimeline.Creature creature) { return null; }
    @Override public String dropSkin() { return null; }
    @Override public String packTexture(String path) { return PackTextures.get(path); }
    @Override public int textureSize(String key) { return PackTextures.width(key); }
    @Override public float[] frameNow(String key) { return PackTextures.frameNow(key); }
}
