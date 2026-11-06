package com.butcher.audio;

import com.butcher.anim.SlayerTimeline;
import com.butcher.anim.SlayerTimeline.Boss;
import com.butcher.anim.SlayerTimeline.Ending;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Grave's and the Cocoon's soundtrack as one list of timed cues, worked out once from the timeline - the screen
 * plays what is due. Pure. Every cue before {@link SlayerTimeline#REVEAL} is the same for every drop (SlayerTest);
 * at the reveal everything is cut and the outcome speaks. The special endings add their own ({@link #ending}).
 */
public final class SlayerSound {
    private SlayerSound() {}

    public static List<HellionSound.Cue> schedule(SlayerTimeline tl) {
        long seed = tl.seed();
        long R = SlayerTimeline.REVEAL;
        long[] beats = SlayerTimeline.BEATS;
        long last = beats[beats.length - 1];
        boolean grave = tl.boss() == Boss.REVENANT;
        List<HellionSound.Cue> out = new ArrayList<>();

        // ---- before the reveal: identical for every drop
        out.add(new HellionSound.Cue(0, false, Voices.labRise(seed)));
        for (int i = 0; i < beats.length; i++) {
            if (grave) {
                out.add(new HellionSound.Cue(beats[i] - 180, false, Voices.chopWhoosh(Math.min(2, i), seed + i)));
                out.add(new HellionSound.Cue(beats[i], false, i == beats.length - 1 ? Voices.labKnock(seed + i) : Voices.chopHit(Math.min(2, i), seed + i)));
            } else {
                out.add(new HellionSound.Cue(beats[i] - 140, false, Voices.stingWhoosh(0.22f, seed + i)));
                out.add(new HellionSound.Cue(beats[i] + 10, false, Voices.chopHit(Math.min(2, i), seed + 40 + i)));
            }
        }
        out.add(new HellionSound.Cue(last + 120, false, Voices.thawBuildUp((R - last - 120) / 1000f)));
        out.add(new HellionSound.Cue(last, false, Voices.heartRumble((R - last) / 1000f, seed)));

        // ---- the reveal
        if (!tl.drop().win()) {
            out.add(new HellionSound.Cue(R, true, grave ? Voices.labEmpty() : Voices.chopSplit()));
            out.add(new HellionSound.Cue(R + 380, false, grave ? Voices.chopCrumble(seed) : Voices.labEmpty()));
        } else {
            out.add(new HellionSound.Cue(R, true, grave ? Voices.thawExplode(seed) : Voices.chopSplit()));
            if (!grave) out.add(new HellionSound.Cue(R + 60, false, Voices.stingDrip(seed)));
            if (tl.ending() == Ending.PLAIN) {
                out.add(new HellionSound.Cue(tl.arrive(), false, tl.intensity() >= 0.4f ? Voices.fanfare() : Voices.labArrive()));
            } else {
                ending(out, tl);
                out.add(new HellionSound.Cue(tl.arrive(), false, Voices.fanfare()));
                out.add(new HellionSound.Cue(tl.arrive() + 120, false, Voices.midasCrowned()));
            }
        }
        out.sort(Comparator.comparingLong(c -> c.at));
        return out;
    }

    /** A special ending's own cues, between the reveal and the drop's arrival. */
    static void ending(List<HellionSound.Cue> out, SlayerTimeline tl) {
        // Filled in with the endings themselves (SlayerEndingSound).
        SlayerEndingSound.add(out, tl);
    }
}
