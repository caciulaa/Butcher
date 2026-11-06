import com.butcher.anim.ChopTimeline;
import com.butcher.anim.SlayerTimeline;
import com.butcher.anim.SlayerTimeline.Boss;
import com.butcher.anim.SlayerTimeline.Drop;
import com.butcher.anim.SlayerTimeline.Ending;
import com.butcher.audio.HellionSound;
import com.butcher.audio.SlayerSound;
import com.butcher.detect.RevTaraDrops;
import com.butcher.render.ChopScene;
import com.butcher.render.SlayerScene;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Offline checks for the Revenant's Grave and the Tarantula's Cocoon. The one that matters, as for Hellion: nothing
 * drawn or heard before the reveal depends on the drop - every draw call of every frame is hashed, and every cue before
 * it rendered to samples, for every drop of each boss on several seeds, and they must agree. Also the drop lines.
 *
 *   javac -d out -cp slf4j.jar -sourcepath "src/main/java;tools" tools/SlayerTest.java && java -cp "out;slf4j.jar" SlayerTest
 */
public final class SlayerTest {
    static int passed, failed;

    static void check(String what, boolean ok) {
        if (ok) passed++;
        else {
            failed++;
            System.out.println("FAIL " + what);
        }
    }

    /** Hashes every primitive drawn, the rasterised models' pixels included. */
    static final class Hash implements ChopScene.Canvas {
        long h = 1125899906842597L;

        void mix(float... v) {
            for (float f : v) h = 31 * h + Float.floatToIntBits(Math.round(f * 100f) / 100f);
        }

        void mix(String s) { h = 31 * h + (s == null ? 0 : s.hashCode()); }

        public void quad(String tex, float ox, float oy, float ux, float uy, float vx, float vy, int u0, int v0, int uw, int vh, int argb) {
            mix(tex); mix(ox, oy, ux, uy, vx, vy, u0, v0, uw, vh, argb);
        }
        public void sprite(String tex, float cx, float cy, float w, float h2, float rot, int argb) { mix(tex); mix(cx, cy, w, h2, rot, argb); }
        public void line(float x0, float y0, float x1, float y1, float width, int argb) { mix(x0, y0, x1, y1, width, argb); }
        public void fill(float x, float y, float w, float h2, int argb) { mix(x, y, w, h2, argb); }
        public void item(float cx, float cy, float size, float alpha, float yaw) { mix(cx, cy, size, alpha, yaw); mix("item"); }
        public void name(float cx, float cy, float scale, int argb, int letters) { mix(cx, cy, scale, argb, letters); mix("name"); }
        public void text(String text, float cx, float cy, float scale, int argb) { mix(text); mix(cx, cy, scale, argb); }
        public String creatureSkin(ChopTimeline.Creature creature) { return null; }
        public String dropSkin() { return null; }
        @Override public void image(String key, int[] argb, int iw, int ih, float x, float y, float w, float hh) {
            mix(key); mix(iw, ih, x, y, w, hh);
            for (int i = 0; i < iw * ih; i++) h = 31 * h + argb[i];
        }
        @Override public void imageAffine(String key, int[] argb, int iw, int ih, float ox, float oy, float ux, float uy, float vx, float vy) {
            mix(key); mix(iw, ih, ox, oy, ux, uy, vx, vy);
            for (int i = 0; i < iw * ih; i++) h = 31 * h + argb[i];
        }
    }

    static long frames(Boss boss, Drop d, long seed, boolean own, long from, long to) {
        SlayerTimeline tl = new SlayerTimeline(boss, d, seed, own);
        SlayerScene.Show show = new SlayerScene.Show();
        show.line = "1 in 100";
        Hash hash = new Hash();
        for (long t = from; t < to; t += 16) SlayerScene.draw(hash, tl, show, t, 640, 360);
        return hash.h;
    }

    static List<Drop> drops(Boss b) {
        List<Drop> out = new ArrayList<>();
        for (Drop d : Drop.values()) if (d.from(b)) out.add(d);
        return out;
    }

    public static void main(String[] a) {
        long[] seeds = {1, 7, 42, 99_991, 123_456_789};
        for (Boss b : Boss.values()) {
            for (long seed : seeds) {
                long ref = frames(b, Drop.BUST, seed, true, 0, SlayerTimeline.REVEAL);
                for (Drop d : drops(b)) {
                    for (boolean own : new boolean[]{true, false}) {
                        check(b + ": before the reveal, " + d + (own ? "" : " (plain endings)") + " draws as a bust (seed " + seed + ")",
                                frames(b, d, seed, own, 0, SlayerTimeline.REVEAL) == ref);
                    }
                }
            }
            for (Drop d : drops(b)) {
                if (!d.win()) continue;
                check(b + ": " + d + " draws differently from a bust after the reveal",
                        frames(b, d, 7, true, SlayerTimeline.REVEAL, SlayerTimeline.REVEAL + 600)
                                != frames(b, Drop.BUST, 7, true, SlayerTimeline.REVEAL, SlayerTimeline.REVEAL + 600));
            }

            // The sound: everything before the reveal renders to the very same samples.
            for (long seed : new long[]{3, 77}) {
                float[] base = null;
                for (Drop d : drops(b)) {
                    List<HellionSound.Cue> before = new ArrayList<>();
                    for (HellionSound.Cue c : SlayerSound.schedule(new SlayerTimeline(b, d, seed))) if (c.at < SlayerTimeline.REVEAL) before.add(c);
                    float[] pcm = HellionSoundtrack.render(before, SlayerTimeline.REVEAL / 1000f);
                    if (base == null) base = pcm;
                    else check(b + ": before the reveal, " + d + " sounds as a bust (seed " + seed + ")", Arrays.equals(base, pcm));
                }
            }

            for (Drop d : drops(b)) {
                SlayerTimeline tl = new SlayerTimeline(b, d, 5);
                List<HellionSound.Cue> cues = SlayerSound.schedule(tl);
                boolean cut = false, ordered = true, inside = true;
                for (int i = 0; i < cues.size(); i++) {
                    HellionSound.Cue c = cues.get(i);
                    if (c.at == SlayerTimeline.REVEAL && c.cut) cut = true;
                    if (i > 0 && c.at < cues.get(i - 1).at) ordered = false;
                    if (c.at < 0 || c.at > tl.total()) inside = false;
                }
                check(b + " " + d + ": the reveal cuts the build-up dead", cut);
                check(b + " " + d + ": its cues are in order", ordered);
                check(b + " " + d + ": every cue within the cutscene", inside);
                check(b + " " + d + ": fades out by its end", tl.master(tl.total() - 1) < 0.02f);
                check(b + " " + d + ": the drop arrives before the end", !d.win() || tl.arrive() < tl.total() - 1500);
                check(b + " " + d + ": the own ending off is the plain one", new SlayerTimeline(b, d, 5, false).ending()
                        == (d.win() ? Ending.PLAIN : Ending.BUST));
            }
            SlayerTimeline bust = new SlayerTimeline(b, Drop.BUST, 7);
            check(b + ": a bust is over within 1.3 s of the reveal (" + (bust.total() - SlayerTimeline.REVEAL) + ")",
                    bust.total() - SlayerTimeline.REVEAL <= 1300);
        }
        // The user's ask: short build-ups, no colour cycle - under 4 s to the reveal.
        check("the build-up is under 4 s (" + SlayerTimeline.REVEAL + ")", SlayerTimeline.REVEAL < 4000);
        long prev = SlayerTimeline.APPEAR;
        boolean beats = true;
        for (long t : SlayerTimeline.BEATS) {
            beats &= t > prev && t < SlayerTimeline.REVEAL;
            prev = t;
        }
        check("the blows are in order, all before the reveal", beats);
        // The dyes have the grandest endings: the longest of their boss's.
        for (Boss b : Boss.values()) {
            long dye = 0, best = 0;
            for (Drop d : drops(b)) {
                long len = new SlayerTimeline(b, d, 1).arrive();
                if (d.dye()) dye = len;
                else best = Math.max(best, len);
            }
            check(b + ": the dye's ending is the longest", dye >= best);
        }

        // ---- the drop lines (RevTaraDrops)
        check("a Scythe Blade line", RevTaraDrops.of("RARE DROP! (Scythe Blade) (+116% ✯ Magic Find)", Boss.REVENANT) == Drop.SCYTHE_BLADE);
        check("a counted drop", RevTaraDrops.of("VERY RARE DROP! (2x Severed Hand) (+116% ✯ Magic Find)", Boss.REVENANT) == Drop.SEVERED_HAND);
        check("Shard of the Shredded", RevTaraDrops.of("CRAZY RARE DROP! (Shard of the Shredded) (+300% ✯ Magic Find)", Boss.REVENANT)
                == Drop.SHARD_OF_THE_SHREDDED);
        check("the Warden Heart", RevTaraDrops.of("CRAZY RARE DROP! (Warden Heart)", null) == Drop.WARDEN_HEART);
        check("a Revenant drop from a Tarantula's quest is a bust", RevTaraDrops.of("RARE DROP! (Scythe Blade)", Boss.TARANTULA) == Drop.BUST);
        check("any other slayer drop is a bust", RevTaraDrops.of("RARE DROP! (4x Foul Flesh) (+116% ✯ Magic Find)", Boss.REVENANT) == Drop.BUST);
        check("the Paragon Shard is either boss's", RevTaraDrops.of("RARE DROP! (Paragon Shard)", Boss.TARANTULA) == Drop.PARAGON);
        check("the Primordial Eye", RevTaraDrops.of("PRAY TO RNGESUS DROP! (Primordial Eye) (+50% ✯ Magic Find)", Boss.TARANTULA)
                == Drop.PRIMORDIAL_EYE);
        check("the Matcha Dye, WOW! way", RevTaraDrops.of("WOW! You found a Matcha Dye!", Boss.REVENANT) == Drop.MATCHA_DYE);
        check("the Brick Red Dye, bracketed", RevTaraDrops.of("PRAY TO RNGESUS DROP! (Brick Red Dye)", Boss.TARANTULA) == Drop.BRICK_RED_DYE);
        check("Diana's unbracketed RARE DROP! is no slayer line", RevTaraDrops.of("RARE DROP! Antique Remedies (+277 Magic Find)", null) == null);
        check("chat is no drop line", RevTaraDrops.of("Friend_A: Scythe Blade when", Boss.REVENANT) == null);
        check("the quest names zombies", RevTaraDrops.questBoss("» Slay 7,200 Combat XP worth of Zombies.") == Boss.REVENANT);
        check("the quest names spiders", RevTaraDrops.questBoss("  » Slay 2,400 Combat XP worth of Spiders.") == Boss.TARANTULA);
        check("other quests name neither", RevTaraDrops.questBoss("» Slay 2,400 Combat XP worth of Blazes.") == null);
        check("the Atoned Horror is the Revenant", RevTaraDrops.bossOf("☠ Atoned Horror 8.5M❤") == Boss.REVENANT);
        check("the Broodfather is the Tarantula", RevTaraDrops.bossOf("☠ Tarantula Broodfather V 2M❤") == Boss.TARANTULA);
        check("tier V", RevTaraDrops.tierOf("☠ Revenant Horror V 8M❤") == 5);
        check("tier IV", RevTaraDrops.tierOf("☠ Revenant Horror IV 400k❤") == 4);
        check("tier V by its own name", RevTaraDrops.tierOf("☠ Primordial Broodfather 2M❤") == 5);
        check("colour codes ignored", RevTaraDrops.tierOf("§c☠ §bTarantula Broodfather §cIV §a740k§c❤") == 4);

        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
