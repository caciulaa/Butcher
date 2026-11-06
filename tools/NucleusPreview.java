import com.butcher.anim.NucleusTimeline;
import com.butcher.render.NucleusScene;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;

/**
 * Renders frames of "The Vault" (the Crystal Nucleus) offline, through the real NucleusScene on ChopPreview's
 * game-accurate canvas. The block textures come from a folder of the game's PNGs (tools/blender/vault_textures.py
 * extracts them from the local client jar - preview only, never committed).
 *
 *   java -Dblocks=<dir of block PNGs> -Dskins=<dir of NEU skins> -Dbg=<screenshot> -Dat=0,800,1300 -cp ... NucleusPreview <outdir> PLAIN|WIN|ALLOY
 */
public final class NucleusPreview {

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        NucleusTimeline.Tier tier = args.length > 1 ? NucleusTimeline.Tier.valueOf(args[1]) : NucleusTimeline.Tier.PLAIN;
        File blocks = new File(System.getProperty("blocks"));
        // -Dstyle=gauntlet plays the Gauntlet instead of the Vault (its textures are in the same block folder).
        boolean gauntlet = "gauntlet".equals(System.getProperty("style"));
        java.util.function.Function<String, int[]> blockPixels = name -> {
            try {
                BufferedImage img = ImageIO.read(new File(blocks, name + ".png"));
                int[] px = new int[256];
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) px[y * 16 + x] = img.getRGB(x * img.getWidth() / 16, y * img.getWidth() / 16);
                return px;
            } catch (Exception e) {
                return null;
            }
        };
        com.butcher.render.GauntletScene.textures(blockPixels);
        NucleusScene.textures(name -> {
            try {
                BufferedImage img = ImageIO.read(new File(blocks, name + ".png"));
                int[] px = new int[256];
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) px[y * 16 + x] = img.getRGB(x * img.getWidth() / 16, y * img.getWidth() / 16);
                return px;
            } catch (Exception e) {
                return null;
            }
        });
        // The crystals' heads: -Dskins= a folder of <NEU id>_skin.png (preview only).
        String skins = System.getProperty("skins");
        if (skins != null) NucleusScene.skins = id -> {
            try {
                return ImageIO.read(new File(skins, id + "_skin.png"));
            } catch (Exception e) {
                return null;
            }
        };
        // The two vanilla items' icons: -Dicons= a folder of their PNGs (preview only).
        String icons = System.getProperty("icons");
        if (icons != null) NucleusScene.icons = name -> {
            try {
                BufferedImage img = ImageIO.read(new File(icons, name + ".png"));
                int[] px = new int[256];
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) px[y * 16 + x] = img.getRGB(x, y);
                return px;
            } catch (Exception e) {
                return null;
            }
        };
        // The forge's block textures and the heads' skins, for the Alloy: -Dforge= a folder of block PNGs.
        java.util.Map<String, BufferedImage> forgeTex = new HashMap<>();
        String forge = System.getProperty("forge");
        if (forge != null) {
            for (String name : com.butcher.render.ForgeScene.TEXTURES) {
                File f = new File(forge, name + ".png");
                if (f.isFile()) forgeTex.put("block:" + name, ImageIO.read(f));
            }
        }
        if (skins != null) {
            File[] files = new File(skins).listFiles((d, n) -> n.endsWith("_skin.png"));
            if (files != null) for (File f : files) forgeTex.put("skin:" + f.getName().replace("_skin.png", ""), ImageIO.read(f));
        }
        com.butcher.render.ForgeScene.skin = id -> forgeTex.containsKey("skin:" + id) ? "skin:" + id : null;
        NucleusScene.forgeShow.price = 1_100_000_000L;
        NucleusScene.forgeShow.nth = "your 1st";
        NucleusScene.forgeShow.more = 5;
        NucleusScene.rasterScale = ChopPreview.S;
        long b0 = System.nanoTime();
        NucleusScene.bakeNow(ChopPreview.W, ChopPreview.H);
        System.out.printf("bake (in game on a worker thread): %.0f ms%n", (System.nanoTime() - b0) / 1e6);
        NucleusTimeline tl = new NucleusTimeline(com.butcher.anim.NucleusHaul.test(tier));
        tl.price(tl.show() == null ? 0 : tl.show().worth() * Math.max(1, tl.show().count()), "preview");
        com.butcher.anim.GauntletTimeline gtl = new com.butcher.anim.GauntletTimeline(com.butcher.anim.NucleusHaul.test(tier), null, true, false);
        gtl.price(tl.price(), "preview");
        BufferedImage bg = System.getProperty("bg") == null ? null : ImageIO.read(new File(System.getProperty("bg")));
        if (gauntlet && bg != null) {
            // The world the snap turns to dust: the backdrop itself, at a quarter size as the game reads it.
            int ww = bg.getWidth() / 4, wh = bg.getHeight() / 4;
            int[] wp = new int[ww * wh];
            for (int y = 0; y < wh; y++) for (int x = 0; x < ww; x++) wp[y * ww + x] = bg.getRGB(x * 4, y * 4);
            com.butcher.render.GauntletScene.world = wp;
            com.butcher.render.GauntletScene.worldW = ww;
            com.butcher.render.GauntletScene.worldH = wh;
        }
        java.util.function.BiConsumer<com.butcher.render.ChopScene.Canvas, Long> scene = gauntlet
                ? (cv, tt) -> com.butcher.render.GauntletScene.draw(cv, gtl, tt, ChopPreview.W, ChopPreview.H)
                : (cv, tt) -> NucleusScene.draw(cv, tl, tt, ChopPreview.W, ChopPreview.H);
        long total = gauntlet ? gtl.total() : tl.total();
        int W = ChopPreview.W, H = ChopPreview.H, S = ChopPreview.S;
        File dir = new File(out, (gauntlet ? "GAUNTLET_" : "NUCLEUS_") + tier);
        dir.mkdirs();
        String[] at = System.getProperty("at", "0,400,800,1200,1600,2000,2400,2800,3200,3600,4000,4400,4800,5200,5600").split(",");
        if (gauntlet) System.out.printf("total %d ms, reveal %d, pop %d, text %d, ending %s%n", gtl.total(), com.butcher.anim.GauntletTimeline.REVEAL, gtl.popAt(), gtl.textAt(), gtl.ending());
        else System.out.printf("total %d ms, open %d, show %d, text %d%n", tl.total(), tl.open(), tl.showAt(), tl.textAt());
        long worst = 0, sum = 0;
        int counted = 0;
        boolean first = true;
        for (String a : at) {
            long t = Long.parseLong(a.trim());
            BufferedImage img = new BufferedImage(W * S, H * S, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.scale(S, S);
            ChopPreview.background(g, bg);
            ChopPreview.Java2D canvas = new ChopPreview.Java2D(g, forgeTex, tl.show() == null ? "Haul" : tl.show().name(), null);
            long t0 = System.nanoTime();
            scene.accept(canvas, t);
            long ns = System.nanoTime() - t0;
            if (!first) { worst = Math.max(worst, ns); sum += ns; counted++; }
            first = false;
            g.dispose();
            ImageIO.write(img, "PNG", new File(dir, String.format("t_%05d.png", t)));
        }
        System.out.printf("%d frames to %s; after the first (the bake): mean %.1f ms, worst %.1f ms%n", at.length, dir,
                counted == 0 ? 0 : sum / 1e6 / counted, worst / 1e6);
        // The scene's own cost, without the preview's slow image copies: a canvas that only counts.
        final int[] calls = new int[1];
        com.butcher.render.ChopScene.Canvas count = new com.butcher.render.ChopScene.Canvas() {
            public void quad(String tex, float ox, float oy, float ux, float uy, float vx, float vy, int u0, int v0, int uw, int vh, int argb) { calls[0]++; }
            public void sprite(String tex, float cx, float cy, float w, float h, float rot, int argb) { calls[0]++; }
            public void line(float x0, float y0, float x1, float y1, float width, int argb) { calls[0]++; }
            public void fill(float x, float y, float w, float h, int argb) { calls[0]++; }
            public void item(float cx, float cy, float size, float alpha, float yaw) { calls[0]++; }
            public void name(float cx, float cy, float scale, int argb, int letters) { calls[0]++; }
            public void text(String text, float cx, float cy, float scale, int argb) { calls[0]++; }
            public String creatureSkin(com.butcher.anim.ChopTimeline.Creature creature) { return null; }
            public String dropSkin() { return null; }
            @Override public void image(String key, int[] argb, int iw, int ih, float x, float y, float w, float h) { calls[0]++; }
            @Override public void imageCached(String key, int[] argb, int iw, int ih, int version, float x, float y, float w, float h, float alpha) { calls[0]++; }
        };
        long cw = 0, cs = 0, worstAt = 0;
        int most = 0, n2 = 0;
        for (long t = 0; t < total; t += 16) {
            calls[0] = 0;
            long t0 = System.nanoTime();
            scene.accept(count, t);
            long ns = System.nanoTime() - t0;
            if (ns > cw) worstAt = t;
            cw = Math.max(cw, ns); cs += ns; n2++;
            most = Math.max(most, calls[0]);
        }
        System.out.printf("scene alone, every 16 ms: mean %.2f ms, worst %.2f ms at %d, most draw calls %d (squares before batching)%n", cs / 1e6 / n2, cw / 1e6, worstAt, most);
    }
}
