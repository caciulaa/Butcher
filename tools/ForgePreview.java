import com.butcher.anim.ForgeTimeline;
import com.butcher.render.ForgeScene;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders frames of Divan's Alloy's forge cutscene offline, through the real ForgeScene on ChopPreview's game-accurate
 * canvas. Block textures from a folder of the game's PNGs (tools/blender/forge_textures.py - preview only, never
 * committed), the heads' skins from a folder of <NEU id>_skin.png.
 *
 *   java -Dblocks=<dir> -Dskins=<dir> -Dat=0,1000,2000 -cp ... ForgePreview <outdir>
 */
public final class ForgePreview {

    public static void main(String[] args) throws Exception {
        File out = new File(args[0], "FORGE");
        out.mkdirs();
        Map<String, BufferedImage> tex = new HashMap<>();
        File blocks = new File(System.getProperty("blocks"));
        for (String name : ForgeScene.TEXTURES) {
            File f = new File(blocks, name + ".png");
            if (f.isFile()) tex.put("block:" + name, ImageIO.read(f));
        }
        String skins = System.getProperty("skins");
        if (skins != null) {
            File[] files = new File(skins).listFiles((d, n) -> n.endsWith("_skin.png"));
            if (files != null) for (File f : files) tex.put("skin:" + f.getName().replace("_skin.png", ""), ImageIO.read(f));
        }
        ForgeScene.skin = id -> tex.containsKey("skin:" + id) ? "skin:" + id : null;
        ForgeScene.Show show = new ForgeScene.Show();
        show.price = 1_100_000_000L;
        show.odds = com.butcher.anim.NucleusHaul.odds(com.butcher.anim.NucleusHaul.test(com.butcher.anim.NucleusTimeline.Tier.ALLOY).best());
        show.nth = "your 1st";
        show.more = 9;
        int W = ChopPreview.W, H = ChopPreview.H, S = ChopPreview.S;
        String[] at = System.getProperty("at", "0,1500,3000,4500,6000,7500,9000,10500,12000,13500,15000,16500,18000").split(",");
        long worst = 0, sum = 0;
        for (String a : at) {
            long t = Long.parseLong(a.trim());
            BufferedImage img = new BufferedImage(W * S, H * S, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.scale(S, S);
            ChopPreview.Java2D canvas = new ChopPreview.Java2D(g, tex, "Divan's Alloy", null);
            long t0 = System.nanoTime();
            ForgeScene.draw(canvas, null, t, W, H, show);
            long ns = System.nanoTime() - t0;
            worst = Math.max(worst, ns);
            sum += ns;
            g.dispose();
            ImageIO.write(img, "PNG", new File(out, String.format("t_%05d.png", t)));
        }
        System.out.printf("%d frames; preview drawing mean %.1f ms, worst %.1f ms%n", at.length, sum / 1e6 / at.length, worst / 1e6);
        // The scene's own cost and its draw calls, on a canvas that only counts.
        final int[] calls = new int[2];
        com.butcher.render.ChopScene.Canvas count = new com.butcher.render.ChopScene.Canvas() {
            public void quad(String t, float ox, float oy, float ux, float uy, float vx, float vy, int u0, int v0, int uw, int vh, int argb) { calls[0]++; }
            public void sprite(String t, float cx, float cy, float w, float h, float rot, int argb) { calls[0]++; }
            public void line(float x0, float y0, float x1, float y1, float width, int argb) { calls[0]++; }
            public void fill(float x, float y, float w, float h, int argb) { calls[1]++; }
            public void item(float cx, float cy, float size, float alpha, float yaw) { }
            public void name(float cx, float cy, float scale, int argb, int letters) { }
            public void text(String text, float cx, float cy, float scale, int argb) { }
            public String creatureSkin(com.butcher.anim.ChopTimeline.Creature creature) { return null; }
            public String dropSkin() { return null; }
        };
        long cw = 0, cs = 0, worstAt = 0;
        int mostQuads = 0, mostCells = 0, n = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (long t = 0; t < ForgeTimeline.realTotal(); t += 16) {
                calls[0] = calls[1] = 0;
                long t0 = System.nanoTime();
                ForgeScene.draw(count, null, t, W, H, show);
                long ns = System.nanoTime() - t0;
                if (pass == 1) {
                    if (ns > cw) worstAt = t;
                    cw = Math.max(cw, ns);
                    cs += ns;
                    n++;
                    mostQuads = Math.max(mostQuads, calls[0]);
                    mostCells = Math.max(mostCells, calls[1]);
                }
            }
        }
        System.out.printf("scene alone, every 16 ms: mean %.2f ms, worst %.2f ms at %d; most quads %d, most cells %d (batched in game)%n",
                cs / 1e6 / n, cw / 1e6, worstAt, mostQuads, mostCells);
    }
}
