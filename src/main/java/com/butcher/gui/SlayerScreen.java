package com.butcher.gui;

import com.butcher.ButcherClient;
import com.butcher.anim.SceneClock;
import com.butcher.anim.SlayerTimeline;
import com.butcher.anim.SlayerTimeline.Boss;
import com.butcher.anim.SlayerTimeline.Drop;
import com.butcher.audio.ButcherAudio;
import com.butcher.audio.HellionSound;
import com.butcher.audio.SlayerSound;
import com.butcher.render.ChopScene;
import com.butcher.render.DropIcon;
import com.butcher.render.SlayerScene;
import com.butcher.render.TextureBank;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

/**
 * The Grave (the Revenant Horror) and The Cocoon (the Tarantula Broodfather), in the HUD like the other cutscenes.
 * Thin, as HellionScreen is: {@link SlayerScene} draws every frame (the same code as tools/SlayerPreview); this supplies
 * the GUI primitives, the clock, the sounds and the price. Opened on the kill with the drop pending ({@link #resolve}).
 */
public class SlayerScreen extends Screen implements ChopScene.Canvas, Cutscene {

    private final Boss boss;
    private SlayerTimeline tl;
    private Drop drop;
    private final long seed;
    private boolean pending;
    private final boolean test;
    private final boolean followsBusts = ButcherClient.tryingIt;
    private final SceneClock clock;
    private boolean active = true;

    private GuiGraphicsExtractor g;
    private final Matrix3x2f matrix = new Matrix3x2f();
    private final DropIcon icons = new DropIcon();
    private final SlayerScene.Show show = new SlayerScene.Show();

    private static final com.butcher.render.BatchCanvas BATCH = new com.butcher.render.BatchCanvas();
    private static final com.butcher.render.PixelBatch LAYERS = new com.butcher.render.PixelBatch("slayer_cells");

    private java.util.List<HellionSound.Cue> cues;
    private int cueAt;
    private long lastT = -1;
    private boolean priced, landed, shot;
    private int coinTicks;

    /** {@code drop} null: a real kill whose drop is still to come - it plays as a bust until resolved. */
    public SlayerScreen(Boss boss, Drop drop, boolean test) {
        super(Component.literal("Butcher"));
        this.boss = boss;
        this.pending = drop == null;
        this.drop = drop == null ? Drop.BUST : drop;
        this.test = test;
        this.clock = new SceneClock(ButcherClient.config == null ? 100 : boss == Boss.REVENANT ? ButcherClient.config.revSpeed : ButcherClient.config.taraSpeed);
        this.seed = System.nanoTime() ^ clock.openedAt();
        rebuild();
        count();
        shortBust();
    }

    private boolean ownEndings() {
        return ButcherClient.config == null || (boss == Boss.REVENANT ? ButcherClient.config.revEndings : ButcherClient.config.taraEndings);
    }

    private void rebuild() {
        tl = new SlayerTimeline(boss, drop, seed, ownEndings());
        cues = SlayerSound.schedule(tl);
        // Hypixel's own word for it, from the drop line just read (as Hellion's banner), else the tier it usually is.
        String tier = !test && drop.win() ? com.butcher.detect.HellionDrops.freshTier() : null;
        String word = tier != null ? tier : drop.dye() ? "PRAY TO RNGESUS DROP!" : "CRAZY RARE DROP!";
        show.banner = drop.win() && tl.ending() != SlayerTimeline.Ending.PLAIN && banner() ? word : null;
        show.bannerColour = com.butcher.detect.HellionDrops.tierColour(word);
        requestPrice();
    }

    private boolean banner() {
        return ButcherClient.config == null || (boss == Boss.REVENANT ? ButcherClient.config.revBanner : ButcherClient.config.taraBanner);
    }

    private String nth;
    private boolean counted;

    private void count() {
        if (counted || test || pending) return;
        counted = true;
        com.butcher.config.ButcherStats st = com.butcher.config.ButcherStats.get();
        st.onSource(boss.key, drop.win() ? drop.title : null);
        if (drop.win()) nth = "your " + ChopScreen.ordinal(st.sourceDropCount(boss.key, drop.title));
        settleLine();
    }

    private void settleLine() {
        String odds = drop.odds();
        String tail = test ? "(test)" : nth;
        show.line = odds == null ? tail : tail == null ? odds : odds + " · " + tail;
    }

    public Boss boss() { return boss; }
    public boolean isActive() { return active; }
    public boolean isTest() { return test; }
    public boolean pending() { return pending; }
    public boolean counted() { return counted; }

    /** The kill's drop, now known: the timeline is rebuilt with the same seed - nothing before the reveal changes. */
    public boolean resolve(Drop d) {
        if (!pending) return d == null || d == drop;
        pending = false;
        long t = clock.t();
        if (t >= SlayerTimeline.REVEAL - 250 || d == null || d == drop) {
            count();
            shortBust();
            return d == null || d == drop;
        }
        drop = d;
        rebuild();
        cueAt = 0;
        while (cueAt < cues.size() && cues.get(cueAt).at <= lastT) cueAt++;
        count();
        shortBust();
        return true;
    }

    private void requestPrice() {
        if (drop.win() && drop.priceId != null && (ButcherClient.config == null || ButcherClient.config.livePrices)) {
            com.butcher.market.Bazaar.request();
            com.butcher.market.LivePrice.request(drop.priceId);
        }
    }

    @Override protected void init() { }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) { }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        long t = clock.t();
        if (t >= tl.total()) {
            summary();
            close();
            return;
        }
        if (pending && t >= SlayerTimeline.REVEAL - 250) {
            pending = false;
            count();
            shortBust();
        }
        sounds(t);
        for (int i = 0; i < SlayerScene.ADOPT.length; i++) {
            TextureBank.adopt(SlayerScene.ADOPT[i], Identifier.withDefaultNamespace(SlayerScene.ADOPT_PATH[i]), SlayerScene.ADOPT_SIZE[i][0],
                    SlayerScene.ADOPT_SIZE[i][1]);
        }
        // The Warden's and the zombies' textures, read once through the game's resources (a pack's are used).
        if (!SlayerScene.hasModelTextures()) {
            java.awt.image.BufferedImage warden = ButcherClient.readGameTexture("textures/entity/warden/warden.png");
            java.awt.image.BufferedImage zombie = ButcherClient.readGameTexture("textures/entity/zombie/zombie.png");
            SlayerScene.modelTextures(pixels(warden), warden == null ? 0 : warden.getWidth(), warden == null ? 0 : warden.getHeight(),
                    pixels(zombie), zombie == null ? 0 : zombie.getWidth(), zombie == null ? 0 : zombie.getHeight());
        }
        SlayerScene.rasterScale(Math.max(1, Math.min(2, net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale())));
        ChopScene.calm = (ButcherClient.config != null && ButcherClient.config.calmEffects) || com.butcher.Safety.slow();
        int shake = ButcherClient.config == null ? 100 : boss == Boss.REVENANT ? ButcherClient.config.revShake : ButcherClient.config.taraShake;
        SlayerScene.shake = shake / 100f;
        this.g = g;
        // The scene's pixel squares (webs, rain, rings, rays, dust) go to the GUI as a few images, not thousands of
        // rectangles: one each was what dropped the frames (the user, 2026-10-26) - and slow frames also turned the
        // gameplay window off (Safety.slow).
        int unit = Math.max(2, Math.round(height / 120f));
        LAYERS.begin(g, width, height, unit);
        BATCH.begin(this, LAYERS, width, height, unit);
        try {
            SlayerScene.draw(BATCH, tl, show, t, width, height);
        } finally {
            BATCH.end();
            LAYERS.end();
            this.g = null;
        }
        com.butcher.render.GameplayView.draw(g, Math.min(1f, (System.currentTimeMillis() - clock.openedAt()) / 400f),
                boss == Boss.REVENANT ? 0xFF55FF55 : 0xFF9BE83A);
    }

    // ================================================================== sound

    private static final long STALE_CUE_MS = 1_000;

    private void sounds(long t) {
        while (cueAt < cues.size() && t >= cues.get(cueAt).at) {
            HellionSound.Cue cue = cues.get(cueAt++);
            if (cue.cut) ButcherAudio.silence();
            if (t - cue.at > STALE_CUE_MS) continue;
            ButcherAudio.playRevTara(cue.voices, boss == Boss.REVENANT);
        }
        lastT = t;
        if (drop.win()) {
            if (!priced && t >= tl.countStart()) {
                priced = true;
                fixPrice();
            }
            if (tl.price() > 0 && t >= tl.countStart() && t < tl.countStart() + SlayerTimeline.COUNT_MS) {
                int due = (int) ((t - tl.countStart()) / 70);
                while (coinTicks <= due) ButcherAudio.onThawCoin(coinTicks++);
            }
            if (!landed && tl.price() > 0 && t >= tl.countStart() + SlayerTimeline.COUNT_MS) {
                landed = true;
                ButcherAudio.onThawCoinLand();
            }
            if (!shot && t >= Math.max(tl.arrive(), tl.countStart() + SlayerTimeline.COUNT_MS) + 350) {
                shot = true;
                if (!test) ButcherClient.showcaseShot(drop.title);
            }
        }
    }

    private void fixPrice() {
        if (drop.priceId == null) return;
        com.butcher.config.PatternConfig c = ButcherClient.config;
        if (c != null && (!c.livePrices || !(boss == Boss.REVENANT ? c.revPrice : c.taraPrice))) return;
        Double sell = com.butcher.market.Bazaar.sell(drop.priceId);
        if (sell != null && sell > 0) {
            tl.price(Math.round(sell), "bazaar instant sell");
            return;
        }
        Long bin = com.butcher.market.LivePrice.cached(drop.priceId);
        if (bin != null && bin > 0) tl.price(bin, "lowest BIN on the auction house");
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
    public void line(float x0, float y0, float x1, float y1, float width, int argb) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.05f || (argb >>> 24) < 3) return;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x0, y0);
        pose.rotate((float) Math.atan2(dy, dx));
        pose.translate(-width / 2f, -width / 2f);
        pose.scale(len + width, width);
        g.fill(0, 0, 1, 1, argb);
        pose.popMatrix();
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

    private static int[] pixels(java.awt.image.BufferedImage img) {
        if (img == null) return null;
        return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
    }

    /** Each rasterised model its own image: the GUI draws them all at the frame's end. */
    private static final java.util.Map<String, com.butcher.render.RasterImage> IMAGES = new java.util.HashMap<>();

    @Override
    public void image(String key, int[] argb, int iw, int ih, float x, float y, float w, float h) {
        IMAGES.computeIfAbsent(key, com.butcher.render.RasterImage::new).draw(g, argb, iw, ih, x, y, w, h);
    }

    @Override public String creatureSkin(com.butcher.anim.ChopTimeline.Creature creature) { return null; }

    @Override
    public String dropSkin() {
        if (drop.art == null || !drop.art.isHead()) return null;
        com.butcher.render.HeadSkins set = com.butcher.render.HeadSkins.named(drop.art.headSet());
        return set == null ? null : set.texture(drop.art.head());
    }

    @Override public String packTexture(String path) { return com.butcher.render.PackTextures.get(path); }
    @Override public int textureSize(String key) { return com.butcher.render.PackTextures.width(key); }
    @Override public float[] frameNow(String key) { return com.butcher.render.PackTextures.frameNow(key); }

    /** The drop as SkyBlock shows it: its head, its pack model, or the vanilla item it really is. */
    @Override
    public void item(float cx, float cy, float size, float alpha, float yaw) {
        icons.draw(g, this, drop.art, cx, cy, size, alpha, yaw);
    }

    @Override
    public void name(float cx, float cy, float scale, int argb, int letters) {
        String full = drop.title;
        int n = Math.min(letters, full.length());
        if (n <= 0) return;
        plate(cx, cy, scale, font.width(full), argb);
        if (n >= full.length()) {
            outlined(Component.literal(full), cx, cy, scale, argb);
            return;
        }
        Component part = Component.literal(full.substring(0, n));
        float shift = (font.width(part) - font.width(full)) / 2f * scale;
        outlined(part, cx + shift, cy, scale, argb);
    }

    @Override
    public void text(String text, float cx, float cy, float scale, int argb) {
        plate(cx, cy, scale, font.width(text), argb);
        outlined(Component.literal(text), cx, cy, scale, argb);
    }

    private void plate(float cx, float cy, float scale, int textW, int argb) {
        int a = Math.round((argb >>> 24) * 0.42f);
        if (a < 4) return;
        float w = (textW + 8) * scale, h = 12f * scale;
        fill(Math.round(cx - w / 2f), Math.round(cy - h / 2f), Math.round(w), Math.round(h), a << 24 | 0x0B0709);
    }

    private void outlined(Component text, float cx, float cy, float scale, int argb) {
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

    // ================================================================== busts, the summary, skipping

    private boolean shortened;

    private void shortBust() {
        if (shortened || (test && !followsBusts) || pending || drop.win() || ButcherClient.bustStyle(boss.key) != 1) return;
        shortened = true;
        long t = clock.t(), target = SlayerTimeline.REVEAL - 450;
        if (t >= target) return;
        clock.jump(target);
        ButcherAudio.silence();
        ButcherAudio.muted(() -> sounds(target));
    }

    private boolean summarised;

    private void summary() {
        if (summarised || pending) return;
        summarised = true;
        if (!test && drop.win() && tl.price() > 0) com.butcher.config.ButcherStats.get().onCoins(tl.price());
        String what = drop.win() ? drop.title : "no rare drop";
        String price = drop.win() && tl.price() > 0 ? " - " + String.format("%,d", tl.price()) + " coins" : "";
        String extra = test ? " (test)" : nth != null ? " (" + nth + ")" : "";
        ButcherClient.summary(boss.title + ": " + what + price + extra);
    }

    @Override
    public boolean showsWin() {
        return active && !pending && drop.win();
    }

    @Override
    public void skip() {
        if (!active) return;
        long t = clock.t();
        long target = Math.max(tl.arrive(), tl.countStart() + SlayerTimeline.COUNT_MS);
        if (pending || !drop.win() || t >= target || target >= tl.total()) {
            summary();
            interrupt();
            return;
        }
        clock.jump(target);
        ButcherAudio.silence();
        ButcherAudio.muted(() -> sounds(target));
        ButcherAudio.onSkip();
        summary();
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
