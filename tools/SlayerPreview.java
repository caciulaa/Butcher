import com.butcher.anim.SlayerTimeline;
import com.butcher.anim.SlayerTimeline.Boss;
import com.butcher.anim.SlayerTimeline.Drop;
import com.butcher.render.SeraphScene;
import com.butcher.render.SlayerScene;
import com.butcher.render.art.ChopArt;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders frames of The Grave and The Cocoon offline, through the real SlayerScene on ChopPreview's game-accurate
 * canvas (640 x 360 GUI px rasterised at -Dscale, small textures nearest, a dimmed screenshot behind with -Dbg).
 *
 *   java -Dvanilla=<dir of the game's textures> -Dbg=<screenshot> -Dat=0,1000,3700 -cp ... SlayerPreview <outdir> <seed> BOSS:DROP...
 *
 * The game's textures are read from the client jar for the preview only - the mod adopts them at runtime.
 */
public final class SlayerPreview {

    public static void main(String[] args) throws Exception {
        String out = args[0];
        long seed = Long.parseLong(args[1]);
        int fps = Integer.getInteger("fps", 20);
        String van = System.getProperty("vanilla");
        Map<String, BufferedImage> tex = new HashMap<>();
        String[] files = {"dirt", "coarse_dirt", "podzol_top", "mossy_stone_bricks", "spruce_planks", "dark_oak_planks", "iron_shovel",
                "white_wool", "spider", "spider_eyes", "iron_sword", "full_moon", "zombie", "mossy_cobblestone", "sculk"};
        for (int i = 0; i < SlayerScene.ADOPT.length; i++) tex.put(SlayerScene.ADOPT[i], argb(ImageIO.read(new File(van, files[i] + ".png"))));
        BufferedImage white = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) white.setRGB(x, y, 0xFFFFFFFF);
        tex.put(SeraphScene.TEX_WHITE, white);
        tex.put(ChopArt.TEX_GLOW, ChopArt.glow());
        tex.put(ChopArt.TEX_SHEET, ChopArt.sheet());
        BufferedImage warden = argb(ImageIO.read(new File(van, "warden.png"))), zombie = argb(ImageIO.read(new File(van, "zombie.png")));
        SlayerScene.modelTextures(warden.getRGB(0, 0, 128, 128, null, 0, 128), 128, 128, zombie.getRGB(0, 0, 64, 64, null, 0, 64), 64, 64);
        SlayerScene.rasterScale(ChopPreview.S);
        BufferedImage icon = argb(ImageIO.read(new File(van, "diamond.png")));
        BufferedImage bg = System.getProperty("bg") == null ? null : ImageIO.read(new File(System.getProperty("bg")));
        int W = ChopPreview.W, H = ChopPreview.H, S = ChopPreview.S;
        for (int i = 2; i < args.length; i++) {
            String[] bd = args[i].split(":");
            Boss boss = Boss.valueOf(bd[0]);
            Drop d = Drop.valueOf(bd[1]);
            SlayerTimeline tl = new SlayerTimeline(boss, d, seed);
            if (d.win()) tl.price(12_345_678L, "bazaar instant sell");
            SlayerScene.Show show = new SlayerScene.Show();
            show.line = d.odds() == null ? null : d.odds() + " · your 1st";
            show.banner = d.win() && d.ending != SlayerTimeline.Ending.PLAIN ? "CRAZY RARE DROP!" : null;
            File dir = new File(out, boss + "_" + d.name() + "_" + seed);
            dir.mkdirs();
            long[] times;
            String only = System.getProperty("at");
            if (only != null) {
                String[] q = only.split(",");
                times = new long[q.length];
                for (int k = 0; k < q.length; k++) times[k] = Long.parseLong(q[k].trim());
            } else {
                int n = (int) (tl.total() * fps / 1000) + 1;
                times = new long[n];
                for (int k = 0; k < n; k++) times[k] = k * 1000L / fps;
            }
            int n = 0;
            for (long t : times) {
                BufferedImage img = new BufferedImage(W * S, H * S, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = img.createGraphics();
                g.scale(S, S);
                ChopPreview.background(g, bg);
                ChopPreview.Java2D canvas = new ChopPreview.Java2D(g, tex, d.title, d.win() ? new Object[]{"flat", icon} : null);
                SlayerScene.draw(canvas, tl, show, t, W, H);
                g.dispose();
                ImageIO.write(img, "PNG", new File(dir, only != null ? String.format("t_%05d.png", t) : String.format("f_%04d.png", n)));
                n++;
            }
            System.out.println(args[i] + ": " + n + " frames");
        }
    }

    static BufferedImage argb(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }
}
