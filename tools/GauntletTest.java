import com.butcher.anim.ChopTimeline;
import com.butcher.anim.GauntletTimeline;
import com.butcher.anim.GauntletTimeline.Ending;
import com.butcher.anim.NucleusHaul;
import com.butcher.anim.NucleusTimeline.Crystal;
import com.butcher.anim.NucleusTimeline.Tier;
import com.butcher.audio.GauntletSound;
import com.butcher.audio.HellionSound;
import com.butcher.render.ChopScene;
import com.butcher.render.GauntletScene;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Offline checks for "The Gauntlet", the Crystal Nucleus's second style. The one that matters: nothing before the
 * landing depends on the haul - every draw call of every frame (the rasters by their pixels) and every sample of sound
 * up to REVEAL is the same for every tier, own endings on or off. Then the endings' beats in order, the placing order,
 * the cycle and the draw-call budget. Needs slf4j on the classpath (the sound) and src/main/resources (the model).
 */
public final class GauntletTest {
    static int passed, failed;

    static void check(String what, boolean ok) {
        if (ok) passed++;
        else {
            failed++;
            System.out.println("FAIL " + what);
        }
    }

    /** Hashes every primitive drawn, and counts them. */
    static final class Hash implements ChopScene.Canvas {
        long h = 1125899906842597L;
        int calls, fills;

        void mix(float... v) {
            for (float f : v) h = 31 * h + Float.floatToIntBits(Math.round(f * 100f) / 100f);
        }

        void mix(String s) { h = 31 * h + (s == null ? 0 : s.hashCode()); }

        public void quad(String tex, float ox, float oy, float ux, float uy, float vx, float vy, int u0, int v0, int uw, int vh, int argb) {
            calls++; mix(tex); mix(ox, oy, ux, uy, vx, vy, u0, v0, uw, vh, argb);
        }
        public void sprite(String tex, float cx, float cy, float w, float h2, float rot, int argb) { calls++; mix(tex); mix(cx, cy, w, h2, rot, argb); }
        public void line(float x0, float y0, float x1, float y1, float width, int argb) { calls++; mix(x0, y0, x1, y1, width, argb); }
        public void fill(float x, float y, float w, float h2, int argb) { fills++; mix(x, y, w, h2, argb); }
        public void item(float cx, float cy, float size, float alpha, float yaw) { calls++; mix(cx, cy, size, alpha, yaw); mix("item"); }
        public void name(float cx, float cy, float scale, int argb, int letters) { calls++; mix(cx, cy, scale, argb, letters); mix("name"); }
        public void text(String text, float cx, float cy, float scale, int argb) { calls++; mix(text); mix(cx, cy, scale, argb); }
        public void image(String key, int[] argb, int iw, int ih, float x, float y, float w, float hh) {
            calls++; mix(key); mix(x, y, w, hh, iw, ih);
            for (int i = 0; i < iw * ih && i < argb.length; i++) h = 31 * h + argb[i];
        }
        public void imageCached(String key, int[] argb, int iw, int ih, int version, float x, float y, float w, float hh, float alpha) {
            calls++; mix(key); mix(x, y, w, hh, iw, ih, alpha);
            for (int i = 0; i < iw * ih && i < argb.length; i++) h = 31 * h + argb[i];
        }
        public String creatureSkin(ChopTimeline.Creature creature) { return null; }
        public String dropSkin() { return null; }
    }

    static final Tier[] TIERS = Tier.values();
    static final Crystal[] SHUFFLED = {Crystal.TOPAZ, Crystal.AMETHYST, Crystal.JADE, Crystal.SAPPHIRE, Crystal.AMBER};

    /** {@code worst}: {the most draws a frame that are not grid squares, the most grid squares a frame}. */
    static long frames(GauntletTimeline tl, long from, long to, int[] worst) {
        Hash hash = new Hash();
        for (long t = from; t < to; t += 16) {
            int before = hash.calls, squares = hash.fills;
            GauntletScene.draw(hash, tl, t, 640, 360);
            if (worst != null) {
                worst[0] = Math.max(worst[0], hash.calls - before);
                worst[1] = Math.max(worst[1], hash.fills - squares);
            }
        }
        return hash.h;
    }

    public static void main(String[] a) {
        // Plain grey blocks; the stones without their skins draw white, tinted - the same for every haul.
        GauntletScene.textures(name -> {
            int[] px = new int[256];
            Arrays.fill(px, 0xFF808080 | (name.hashCode() & 0x3F3F3F));
            return px;
        });
        com.butcher.render.NucleusScene.skins = id -> null;
        com.butcher.render.NucleusScene.icons = name -> null;

        // Warm every static the scene keeps (the item atlas) on every haul first, so the order the hauls run in
        // cannot show in what is drawn.
        for (Tier tier : TIERS) {
            GauntletTimeline tl = new GauntletTimeline(NucleusHaul.test(tier), null, true, false);
            frames(tl, 0, tl.total(), null);
        }

        // Honesty: everything up to the landing is the same for every haul.
        for (Crystal[] placed : new Crystal[][]{null, SHUFFLED}) {
            for (boolean own : new boolean[]{true, false}) {
                long ref = frames(new GauntletTimeline(NucleusHaul.test(Tier.PLAIN), placed, own, false), 0, GauntletTimeline.REVEAL, null);
                for (Tier tier : TIERS) {
                    long h = frames(new GauntletTimeline(NucleusHaul.test(tier), placed, own, false), 0, GauntletTimeline.REVEAL, null);
                    check("before the landing, " + tier + " draws as a plain haul does (own " + own + ", placed "
                            + (placed != null) + ")", h == ref);
                }
                long floored = frames(new GauntletTimeline(NucleusHaul.test(Tier.ALLOY), placed, own, true), 0, GauntletTimeline.REVEAL, null);
                check("a floored win draws as a plain haul does", floored == ref);
            }
        }
        float[] base = null;
        for (Tier tier : TIERS) {
            List<HellionSound.Cue> before = new ArrayList<>();
            for (HellionSound.Cue c : GauntletSound.schedule(new GauntletTimeline(NucleusHaul.test(tier), null, true, false))) {
                if (c.at < GauntletTimeline.REVEAL) before.add(c);
            }
            float[] pcm = HellionSoundtrack.render(before, GauntletTimeline.REVEAL / 1000f);
            if (base == null) base = pcm;
            else check("before the landing, " + tier + " sounds as a plain haul does", Arrays.equals(base, pcm));
        }

        // The endings.
        check("a plain haul falls", new GauntletTimeline(NucleusHaul.test(Tier.PLAIN), null, true, false).ending() == Ending.FALL);
        check("a 1.5M+ drop gets the fist", new GauntletTimeline(NucleusHaul.test(Tier.WIN), null, true, false).ending() == Ending.FIST);
        check("the Quick Claw gets the fist", new GauntletTimeline(NucleusHaul.test(Tier.CLAW), null, true, false).ending() == Ending.FIST);
        check("the Alloy snaps", new GauntletTimeline(NucleusHaul.test(Tier.ALLOY), null, true, false).ending() == Ending.SNAP);
        check("the Jade Dye snaps", new GauntletTimeline(NucleusHaul.test(Tier.JADE), null, true, false).ending() == Ending.SNAP);
        check("own endings off: the Alloy gets the fist", new GauntletTimeline(NucleusHaul.test(Tier.ALLOY), null, false, false).ending() == Ending.FIST);
        check("under the floor: the Alloy falls", new GauntletTimeline(NucleusHaul.test(Tier.ALLOY), null, true, true).ending() == Ending.FALL);

        // The Alloy's snap and the Jade Storm.
        GauntletTimeline alloy = new GauntletTimeline(NucleusHaul.test(Tier.ALLOY), null, true, false);
        GauntletTimeline jade = new GauntletTimeline(NucleusHaul.test(Tier.JADE), null, true, false);
        check("only the Jade Dye storms", jade.storm() && !alloy.storm());
        long beamAt = GauntletTimeline.REVEAL + GauntletTimeline.BEAM_FROM + 4 * GauntletTimeline.BEAM_EVERY + 130;
        check("the Alloy's five beams are lit before the hush", alloy.beam(4, beamAt) == 1f && beamAt < alloy.snapAt() - GauntletTimeline.HUSH);
        check("no beams in the storm", jade.beam(0, beamAt) == 0f);
        check("the hush comes right before the snap", alloy.hush(alloy.snapAt() - 10) > 0.9f && alloy.hush(alloy.snapAt() - 400) == 0f);
        check("the shockwave runs out from the snap", jade.shock(jade.snapAt() - 1) < 0f && jade.shock(jade.snapAt() + 350) > 0.5f
                && jade.shock(jade.snapAt() + GauntletTimeline.SHOCK_MS) == 1f && alloy.shock(alloy.snapAt() + 100) < 0f);
        check("the crystal world holds before it shatters", jade.worldDust(jade.snapAt() + GauntletTimeline.SHATTER_FROM - 10) == 0f);

        // The stones go in the order they were placed, the first on the thumb; anything but all five: the enum's order.
        GauntletTimeline o = new GauntletTimeline(NucleusHaul.test(Tier.WIN), SHUFFLED, true, false);
        boolean same = true;
        for (int k = 0; k < 5; k++) same &= o.crystal(k) == SHUFFLED[k];
        check("the placed order", same);
        GauntletTimeline bad = new GauntletTimeline(NucleusHaul.test(Tier.WIN),
                new Crystal[]{Crystal.JADE, Crystal.JADE, Crystal.AMBER, Crystal.TOPAZ, Crystal.SAPPHIRE}, true, false);
        check("a repeat falls back to the enum's order", bad.crystal(0) == Crystal.values()[0] && bad.crystal(4) == Crystal.values()[4]);

        // The build: the thumb, the turn, then four fingers; the cycle speeds up and lands at REVEAL.
        check("the thumb's stone seats before the turn", GauntletTimeline.lock(0) < GauntletTimeline.TURN_FROM);
        check("the turn ends before the index's stone flies", GauntletTimeline.TURN_TO <= GauntletTimeline.launch(1));
        boolean up = true;
        for (int k = 1; k < 5; k++) up &= GauntletTimeline.lock(k) > GauntletTimeline.lock(k - 1);
        check("the stones seat one after another", up);
        boolean faster = true;
        for (int i = 2; i < GauntletTimeline.CYCLE.length; i++) {
            long d1 = GauntletTimeline.step(i - 1) - GauntletTimeline.step(i - 2), d2 = GauntletTimeline.step(i) - GauntletTimeline.step(i - 1);
            faster &= d2 <= d1 && d2 > 0;
        }
        check("the cycle's steps shorten", faster);
        check("the cycle starts after the pinky", GauntletTimeline.CYCLE_FROM > GauntletTimeline.lock(4));
        check("the landing comes after the last step", GauntletTimeline.REVEAL > GauntletTimeline.step(GauntletTimeline.CYCLE.length - 1));
        check("no cycle before it starts", GauntletTimeline.cycleIndex(GauntletTimeline.CYCLE_FROM - 1) == -1);
        check("no cycle after the landing", GauntletTimeline.cycleIndex(GauntletTimeline.REVEAL) == -1);

        // Each ending's beats in order, and every one fades out.
        for (Tier tier : TIERS) {
            GauntletTimeline tl = new GauntletTimeline(NucleusHaul.test(tier), null, true, false);
            check(tier + ": the drop comes after the landing", tl.popAt() > GauntletTimeline.REVEAL);
            check(tier + ": the words after the drop", tl.textAt() > tl.popAt());
            check(tier + ": it ends after the words", tl.total() > tl.textAt() + 2000);
            check(tier + ": it fades out", tl.master(tl.total()) < 0.05f && tl.master(tl.total() - 2500) > 0.9f);
            List<HellionSound.Cue> cues = GauntletSound.schedule(tl);
            boolean inOrder = true, cutAtReveal = false;
            for (int i = 0; i < cues.size(); i++) {
                if (cues.get(i).at == GauntletTimeline.REVEAL && cues.get(i).cut) cutAtReveal = true;
                if (i > 0 && cues.get(i).at < cues.get(i - 1).at) inOrder = false;
            }
            check(tier + ": cues in order", inOrder);
            check(tier + ": the riser is cut at the landing", cutAtReveal);
            if (tl.ending() == Ending.SNAP) {
                long s = tl.snapAt();
                check(tier + ": no flash before the snap", tl.flash(s - 1) == 0f);
                check(tier + ": white at the snap", tl.flash(s + 10) == 1f);
                check(tier + ": the flash is gone before the world goes", tl.flash(s + 2000) < 0.05f);
                check(tier + ": the world goes after the snap", tl.worldDust(s - 1) < 0f && tl.worldDust(s + 300) == 0f);
                check(tier + ": the gauntlet goes after the world has started", tl.gauntletDust(s + 300) <= 0f);
                check(tier + ": the drop comes once the dust is gone", tl.popAt() >= s + 4000);
                boolean cutAtSnap = false;
                for (HellionSound.Cue c : cues) cutAtSnap |= c.at == s && c.cut;
                check(tier + ": the sound cuts at the snap", cutAtSnap);
            } else {
                check(tier + ": no snap", tl.snapAt() == Long.MAX_VALUE && tl.flash(tl.total()) == 0f);
                boolean cracks = true;
                for (int k = 1; k < 5; k++) cracks &= tl.crackAt(k) > tl.crackAt(k - 1);
                check(tier + ": the stones crack out one by one", cracks && tl.crackAt(0) > GauntletTimeline.REVEAL);
            }
        }

        // The budget, every frame of every ending: the grid squares go into one PixelLayer in game (BatchCanvas), so they
        // cost CPU, not draws - their own, larger budget; everything else is a draw of its own.
        for (Tier tier : TIERS) {
            GauntletTimeline tl = new GauntletTimeline(NucleusHaul.test(tier), null, true, false);
            int[] worst = {0, 0};
            frames(tl, 0, tl.total(), worst);
            check(tier + ": at most 200 draws a frame besides squares, worst " + worst[0], worst[0] <= 200);
            check(tier + ": at most 30,000 squares a frame, worst " + worst[1], worst[1] <= 30_000);
        }

        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
