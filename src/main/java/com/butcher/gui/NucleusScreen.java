package com.butcher.gui;

import com.butcher.ButcherClient;
import com.butcher.anim.GauntletTimeline;
import com.butcher.anim.NucleusRun;
import com.butcher.anim.NucleusTimeline;
import com.butcher.audio.ButcherAudio;
import com.butcher.audio.GauntletSound;
import com.butcher.audio.HellionSound;
import com.butcher.audio.NucleusSound;
import com.butcher.render.ChopScene;
import com.butcher.render.GauntletScene;
import com.butcher.render.NucleusScene;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

/**
 * "The Vault", the Crystal Nucleus's cutscene, in the HUD like the others: opened by {@code detect/NucleusWatcher} on
 * every loot bundle (counted before it opens - ButcherClient.onNucleusHaul), or by "Try it" and
 * {@code /butcher test nucleus} with a real bundle from the user's logs. Thin: {@link NucleusScene} draws every frame
 * (the same code as tools/NucleusPreview). The still layers bake on a worker thread first; the clock starts once they
 * are ready, so nothing is drawn half made and no frame waits. The clock is a {@link com.butcher.anim.SceneClock} at
 * the menu's Speed; a plain haul set to Short jumps to just before the reveal.
 *
 * <p>The menu's Style picks the Vault or "The Gauntlet" ({@link GauntletScene}, {@link GauntletTimeline}): one run, two
 * timelines behind {@link NucleusRun}. The Gauntlet needs nothing baked; its snap asks for the world
 * ({@link com.butcher.render.WorldFreeze}) a moment before, for the dust.
 */
public class NucleusScreen extends Screen implements ChopScene.Canvas, Cutscene {

    /** Whichever style plays: {@code vault} or {@code gauntlet} is set, the other null. */
    private final NucleusRun tl;
    private final NucleusTimeline vault;
    private final GauntletTimeline gauntlet;
    private boolean worldAsked;
    private final boolean test;
    /** "Try it" follows the plain-hauls setting, as a real run does; /butcher test plays everything. */
    private final boolean followsBusts = ButcherClient.tryingIt;
    private boolean active = true;
    /** Null until the still layers are baked: the scene's time starts then. */
    private com.butcher.anim.SceneClock clock;
    private final long openedAt = System.currentTimeMillis();
    private boolean shortened, summarised, shot;

    private GuiGraphicsExtractor g;
    private java.util.List<HellionSound.Cue> cues;
    private int cueAt;

    private static final com.butcher.render.BatchCanvas BATCH = new com.butcher.render.BatchCanvas();
    private static final com.butcher.render.PixelBatch LAYERS = new com.butcher.render.PixelBatch("nucleus_cells");

    private final net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;

    public NucleusScreen(com.butcher.anim.NucleusHaul haul, boolean test) {
        this(haul, test, null, false, null);
    }

    /**
     * {@code nth}: "your 3rd", counted by the caller; {@code plain}: a win under the price floor, played as a plain haul;
     * {@code placed}: the crystals in the order the player put them in (null: unknown), for the Gauntlet.
     */
    public NucleusScreen(com.butcher.anim.NucleusHaul haul, boolean test, String nth, boolean plain, NucleusTimeline.Crystal[] placed) {
        super(Component.literal("Butcher"));
        this.test = test;
        boolean own = ButcherClient.config == null || ButcherClient.config.nucleusEndings;
        if (ButcherClient.config != null && ButcherClient.config.nucleusStyle == 1) {
            this.gauntlet = new GauntletTimeline(haul, placed, own, plain);
            this.vault = null;
            this.tl = gauntlet;
            this.cues = GauntletSound.schedule(gauntlet);
        } else {
            this.vault = new NucleusTimeline(haul, own, plain);
            this.gauntlet = null;
            this.tl = vault;
            this.cues = NucleusSound.schedule(vault);
        }
        com.butcher.market.Bazaar.request();
        NucleusScene.forgeShow = new com.butcher.render.ForgeScene.Show();
        NucleusScene.forgeShow.more = Math.max(0, haul.items.size() - 1);
        NucleusScene.forgeShow.nth = test ? "a test" : nth;
        NucleusScene.forgeShow.odds = com.butcher.anim.NucleusHaul.odds(haul.best());
        forgeTextures();
    }

    /** The forge's block textures, adopted from the game's own (a pack's are used) at their real size - lava and magma
     *  are strips, and a face only ever shows their first frame. */
    private static boolean forgeAdopted;

    private static void forgeTextures() {
        if (forgeAdopted) return;
        forgeAdopted = true;
        for (String name : com.butcher.render.ForgeScene.TEXTURES) {
            String path = "textures/block/" + name + ".png";
            java.awt.image.BufferedImage img = ButcherClient.readGameTexture(path);
            int w = img == null ? 16 : img.getWidth(), h = img == null ? 16 : img.getHeight();
            com.butcher.render.TextureBank.adopt("block:" + name, net.minecraft.resources.Identifier.withDefaultNamespace(path), 16, 16 * h / Math.max(1, w));
        }
        com.butcher.render.ForgeScene.skin = com.butcher.render.HeadSkins.NUCLEUS::texture;
    }

    /** The showcase's price: the bazaar's instant sell, else the lowest BIN, else the haul's own rough worth. */
    private void price() {
        if (ButcherClient.config != null && !ButcherClient.config.nucleusPrice) return;
        if (tl.price() >= 0 || tl.show() == null) {
            NucleusScene.forgeShow.price = tl.price();
            return;
        }
        String id = tl.show().id();
        int count = Math.max(1, tl.show().count());
        Double sell = com.butcher.market.Bazaar.sell(id);
        if (sell != null && sell > 0) {
            tl.price(Math.round(sell * count), "bazaar");
            return;
        }
        Long bin = com.butcher.market.LivePrice.cached(id);
        if (bin != null && bin > 0) {
            tl.price(bin * count, "lowest BIN");
            return;
        }
        com.butcher.market.LivePrice.request(id);
    }

    public boolean isActive() { return active; }
    public boolean isTest() { return test; }
    @Override public boolean pending() { return false; }
    @Override public boolean showsWin() { return false; }

    @Override protected void init() { }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) { }

    /** The block textures, read once through the game's resources (a pack's are used), each scaled to 16 x 16. */
    private static void textures() {
        if (NucleusScene.hasTextures()) return;
        // The crystals and the haul are their real heads (NEU's skins, fetched once and cached); the two that are
        // vanilla items are drawn from the game's own icons (a pack's are used).
        NucleusScene.skins = com.butcher.render.HeadSkins.NUCLEUS::pixels;
        com.butcher.render.HeadSkins.NUCLEUS.prewarmPixels();
        NucleusScene.icons = name -> {
            java.awt.image.BufferedImage img = ButcherClient.readGameTexture("textures/item/" + name + ".png");
            if (img == null) return null;
            int w = img.getWidth();
            int[] px = new int[256];
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) px[y * 16 + x] = img.getRGB(x * w / 16, y * w / 16);
            return px;
        };
        NucleusScene.textures(NucleusScreen::block);
    }

    /** The Gauntlet's gold, raw gold and blackstone, read the same way. */
    private static void gauntletTextures() {
        if (!GauntletScene.hasTextures()) GauntletScene.textures(NucleusScreen::block);
    }

    private static int[] block(String name) {
        java.awt.image.BufferedImage img = ButcherClient.readGameTexture("textures/block/" + name + ".png");
        if (img == null) return null;
        int w = img.getWidth();
        int[] px = new int[256];
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) px[y * 16 + x] = img.getRGB(x * w / 16, y * w / 16);
        return px;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        textures();
        if (gauntlet != null) gauntletTextures();
        NucleusScene.rasterScale = Math.max(1, Math.min(2, net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale()));
        long now = System.currentTimeMillis();
        if (clock == null) {
            if (vault != null && !NucleusScene.ready(width, height)) {
                // The rock is still baking (~0.1 s, off this thread); give up if it never comes.
                if (now - openedAt > 5_000) close();
                return;
            }
            clock = new com.butcher.anim.SceneClock(ButcherClient.config == null ? 100 : ButcherClient.config.nucleusSpeed);
            shortPlain();
        }
        long t = clock.t();
        if (t >= tl.total()) {
            summary();
            close();
            return;
        }
        sounds(t);
        // The snap's dust needs the world as it is: asked for a moment before, read before the GUI on a later frame.
        if (gauntlet != null && !worldAsked && gauntlet.ending() == GauntletTimeline.Ending.SNAP && t >= gauntlet.snapAt() - 250) {
            worldAsked = true;
            com.butcher.render.WorldFreeze.request();
        }
        if (t >= tl.textAt() - 600) price();
        // The screenshot once the showcase is complete: its name up and its price counted.
        if (!shot && !test && tl.tier() != NucleusTimeline.Tier.PLAIN && t >= tl.textAt() + 1500) {
            shot = true;
            ButcherClient.showcaseShot(tl.show() == null ? "nucleus" : tl.show().name());
        }
        ChopScene.calm = (ButcherClient.config != null && ButcherClient.config.calmEffects) || com.butcher.Safety.slow();
        ChopScene.shakeK = ButcherClient.config == null ? 1f : ButcherClient.config.nucleusShake / 100f;
        this.g = g;
        int unit = Math.max(2, Math.round(height / 120f));
        LAYERS.begin(g, width, height, unit);
        BATCH.begin(this, LAYERS, width, height, unit);
        try {
            if (gauntlet != null) GauntletScene.draw(BATCH, gauntlet, t, width, height);
            else NucleusScene.draw(BATCH, vault, t, width, height);
        } finally {
            BATCH.end();
            LAYERS.end();
            this.g = null;
        }
        com.butcher.render.GameplayView.draw(g, Math.min(1f, t / 400f), 0xFFB565E8);
    }

    /** "Plain hauls: Short": a plain haul jumps, silently, to just before the reveal. */
    private void shortPlain() {
        if (shortened || (test && !followsBusts) || tl.tier() != NucleusTimeline.Tier.PLAIN || ButcherClient.bustStyle("nucleus") != 1) return;
        shortened = true;
        long target = tl.shortTo();
        clock.jump(target);
        while (cueAt < cues.size() && cues.get(cueAt).at < target) cueAt++;
    }

    /** "Chat summary": one line once it is over - a win's drop and price (the bundle itself comes back to chat). */
    private void summary() {
        if (summarised || test) return;
        summarised = true;
        if (tl.tier() == NucleusTimeline.Tier.PLAIN || tl.show() == null) return;
        String price = tl.price() > 0 ? " §7- §6" + String.format(java.util.Locale.ROOT, "%,d", tl.price()) + " coins" : "";
        String nth = NucleusScene.forgeShow == null || NucleusScene.forgeShow.nth == null ? "" : " §8(" + NucleusScene.forgeShow.nth + ")";
        ButcherClient.summary("Crystal Nucleus: §f" + tl.show().name() + price + nth);
    }

    private static final long STALE_CUE_MS = 1_000;

    private void sounds(long t) {
        while (cueAt < cues.size() && t >= cues.get(cueAt).at) {
            HellionSound.Cue cue = cues.get(cueAt++);
            if (cue.cut) ButcherAudio.silence();
            if (t - cue.at > STALE_CUE_MS) continue;
            ButcherAudio.playNucleus(cue.voices);
        }
    }

    // ================================================================== the canvas

    private final org.joml.Matrix3x2f matrix = new org.joml.Matrix3x2f();

    /** The forge's block faces (and the heads' skins): one textured quad each, as every other scene draws them. */
    @Override
    public void quad(String tex, float ox, float oy, float ux, float uy, float vx, float vy, int u0, int v0, int uw, int vh, int argb) {
        net.minecraft.resources.Identifier id = com.butcher.render.TextureBank.id(tex);
        if (id == null || uw <= 0 || vh <= 0 || (argb >>> 24) < 2) return;
        matrix.set(ux / uw, uy / uw, vx / vh, vy / vh, ox, oy);
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.mul(matrix);
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, id, 0, 0, u0, v0, uw, vh,
                com.butcher.render.TextureBank.width(tex), com.butcher.render.TextureBank.height(tex), argb);
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

    /** Each image its own texture: the GUI draws them all at the frame's end. */
    private static final java.util.Map<String, com.butcher.render.RasterImage> IMAGES = new java.util.HashMap<>();

    @Override
    public void image(String key, int[] argb, int iw, int ih, float x, float y, float w, float h) {
        IMAGES.computeIfAbsent(key, com.butcher.render.RasterImage::new).draw(g, argb, iw, ih, x, y, w, h);
    }

    @Override
    public void imageCached(String key, int[] argb, int iw, int ih, int version, float x, float y, float w, float h, float alpha) {
        IMAGES.computeIfAbsent(key, com.butcher.render.RasterImage::new).drawCached(g, argb, iw, ih, version, x, y, w, h, alpha);
    }

    @Override public void item(float cx, float cy, float size, float alpha, float yaw) { }
    @Override public void name(float cx, float cy, float scale, int argb, int letters) { }

    @Override
    public void text(String text, float cx, float cy, float scale, int argb) {
        int a = Math.round((argb >>> 24) * 0.42f);
        if (a >= 4) {
            float w = (font.width(text) + 8) * scale, h = 12f * scale;
            fill(Math.round(cx - w / 2f), Math.round(cy - h / 2f), Math.round(w), Math.round(h), a << 24 | 0x0B0709);
        }
        if ((argb >>> 24) < 6) return;
        int alpha = argb >>> 24;
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
    @Override public float textWidth(String text, float scale) { return font.width(text) * scale; }
    @Override public String creatureSkin(com.butcher.anim.ChopTimeline.Creature creature) { return null; }
    @Override public String dropSkin() { return null; }

    // ================================================================== skipping

    /** A plain haul's skip ends it; a win's jumps to its showcase, name and price already up, the jumped cues unheard. */
    @Override
    public void skip() {
        if (clock == null || tl.tier() == NucleusTimeline.Tier.PLAIN) {
            summary();
            interrupt();
            return;
        }
        long target = tl.textAt() + 1400;
        if (clock.t() >= target) {
            summary();
            interrupt();
            return;
        }
        clock.jump(target);
        ButcherAudio.silence();
        ButcherAudio.onSkip();
        while (cueAt < cues.size() && cues.get(cueAt).at < target) cueAt++;
        price();
    }

    @Override
    public void interrupt() {
        if (!active) return;
        ButcherAudio.silence();
        ButcherAudio.onSkip();
        close();
    }

    private void close() {
        com.butcher.render.GameplayView.release();
        if (gauntlet != null) com.butcher.render.WorldFreeze.clear();
        if (!active) return;
        active = false;
        ButcherAudio.letRing();
        com.butcher.render.CutsceneHud.hide(this);
    }

    @Override
    public void onClose() {
        close();
    }
}
