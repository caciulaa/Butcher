package com.butcher.audio;

import com.butcher.anim.NucleusTimeline;

import java.util.ArrayList;
import java.util.List;

/**
 * The Vault's build-up as a cue list (built from voices the other cutscenes already use; its own come with the
 * endings): the door's rumble as it rises; a whoosh as each crystal flies in, a note climbing a major pentatonic as it
 * locks and a metal clank as its bolt pulls back; the dial's clicks, quickening as it spins up; a riser under the seam's
 * colour cycle with a tick per colour; cut at the reveal, the landing's own note. The same for every run up to there.
 * Then the ending's own (below).
 */
public final class NucleusSound {
    private NucleusSound() {}

    public static List<HellionSound.Cue> schedule(NucleusTimeline tl) {
        List<HellionSound.Cue> out = new ArrayList<>();
        out.add(new HellionSound.Cue(0, false, Voices.relicDust(0.9f, 71)));
        for (int k = 0; k < 5; k++) {
            long lock = NucleusTimeline.lock(k);
            out.add(new HellionSound.Cue(Math.max(0, lock - 260), false, Voices.stingWhoosh(0.32f, 300 + k)));
            out.add(new HellionSound.Cue(lock, false, new Voice[]{Voices.thawLand(1, k * 2)}));
            out.add(new HellionSound.Cue(lock + 70, false, Voices.midasClank(400 + k)));
        }
        // The dial's clicks while it spins up: one each time it passes a notch (30 degrees), by its own angle.
        double notch = Math.PI / 6;
        float last = NucleusTimeline.dial(NucleusTimeline.SPIN_FROM);
        int step = 0;
        for (long t = NucleusTimeline.SPIN_FROM; t < NucleusTimeline.REVEAL; t += 5) {
            float a = NucleusTimeline.dial(t);
            if (Math.floor(a / notch) != Math.floor(last / notch)) out.add(new HellionSound.Cue(t, false, new Voice[]{Voices.thawCycleTick(Math.min(12, step++ / 3))}));
            last = a;
        }
        out.add(new HellionSound.Cue(NucleusTimeline.CYCLE, false, Voices.thawBuildUp((NucleusTimeline.REVEAL - NucleusTimeline.CYCLE) / 1000f)));
        for (int i = 0; i < NucleusTimeline.stepCount(); i++) {
            out.add(new HellionSound.Cue(NucleusTimeline.step(i), false, new Voice[]{Voices.thawCycleTick(i)}));
        }
        int land = tl.tier() == NucleusTimeline.Tier.PLAIN ? 0 : 1;
        out.add(new HellionSound.Cue(NucleusTimeline.REVEAL, true, new Voice[]{Voices.thawLand(land, tl.tier() == NucleusTimeline.Tier.ALLOY ? 8 : 4)}));
        // The ending. Plain: the latch, the heavy door's creak and its stop, a clink for every other item onto the pile,
        // a chime under the words. A win: the flood rising into the blow, the drop's arrival and glow, the fanfare.
        long open = tl.open();
        if (tl.tier() == NucleusTimeline.Tier.PLAIN) {
            out.add(new HellionSound.Cue(open, false, Voices.midasClank(611)));
            out.add(new HellionSound.Cue(open + 120, false, Voices.stingCreak(0.75f, 95f, 62f, 612)));
            out.add(new HellionSound.Cue(open + NucleusTimeline.SWING_MS, false, Voices.chopLand(613)));
            out.add(new HellionSound.Cue(tl.textAt(), false, Voices.chime(3, 523.25f)));
        } else if (tl.tier() == NucleusTimeline.Tier.JADE) {
            // The Jade Dye: a low rumble and a build under five heartbeats, each with the crack of the door and (after the
            // first) the hiss of a jet; the hush; the burst - a boom, a shatter, the gush - and glugs as it runs; the
            // door's creak open on the fountain's roar; the arrival.
            long r = NucleusTimeline.REVEAL;
            out.add(new HellionSound.Cue(r + 40, false, Voices.eruptionRumble(1.5f, 670)));
            out.add(new HellionSound.Cue(r + 60, false, Voices.eruptionBuild((NucleusTimeline.JADE_HUSH - 60) / 1000f)));
            for (int k = 0; k < NucleusTimeline.JADE_BEATS.length; k++) {
                long at = r + NucleusTimeline.JADE_BEATS[k];
                out.add(new HellionSound.Cue(at, false, Voices.hellionHeart(k)));
                out.add(new HellionSound.Cue(at + 20, false, Voices.seraphCrack(0.22f, 0.35f + 0.12f * k, 671 + k)));
                if (k > 0) out.add(new HellionSound.Cue(at + 70, false, Voices.stingHiss(0.28f + 0.05f * k, 676 + k)));
            }
            out.add(new HellionSound.Cue(r + NucleusTimeline.JADE_HUSH, true, new Voice[0]));
            long burst = r + NucleusTimeline.JADE_BURST;
            out.add(new HellionSound.Cue(burst, false, Voices.eruptionBoom(3, 0.95f, 680)));
            out.add(new HellionSound.Cue(burst + 15, false, Voices.seraphShatter(681)));
            out.add(new HellionSound.Cue(burst + 40, false, Voices.huntGeyser(1.3f, 682)));
            out.add(new HellionSound.Cue(burst + 60, false, Voices.relicBurst(2, 683)));
            for (int i = 0; i < 5; i++) out.add(new HellionSound.Cue(burst + 300 + i * 290L, false, Voices.seraphGlug(i)));
            out.add(new HellionSound.Cue(open, false, Voices.midasClank(641)));
            out.add(new HellionSound.Cue(open + 120, false, Voices.stingCreak(0.8f, 90f, 60f, 642)));
            out.add(new HellionSound.Cue(open + 240, false, Voices.huntGeyser(1.8f, 684)));
            out.add(new HellionSound.Cue(tl.showAt(), false, Voices.seraphArrive(true, 643)));
            out.add(new HellionSound.Cue(tl.showAt() + 40, false, Voices.relicBurst(1, 685)));
            out.add(new HellionSound.Cue(tl.showAt() + 200, false, Voices.seraphGlow(3.2f, true, 644)));
            out.add(new HellionSound.Cue(tl.textAt(), false, Voices.fanfare()));
        } else if (tl.tier() == NucleusTimeline.Tier.CLAW) {
            // The Quick Claw: a swish and a tearing hit for each swipe, louder each time; the door coming apart, its
            // pieces knocking down; the arrival.
            long r = NucleusTimeline.REVEAL;
            for (int k = 0; k < NucleusTimeline.SLASH.length; k++) {
                out.add(new HellionSound.Cue(r + NucleusTimeline.SLASH[k] - 60, false, Voices.chopWhoosh(k, 650 + k)));
                out.add(new HellionSound.Cue(r + NucleusTimeline.SLASH[k] + 40, false, Voices.chopHit(k, 653 + k)));
                out.add(new HellionSound.Cue(r + NucleusTimeline.SLASH[k] + 60, false, Voices.midasClank(656 + k)));
            }
            out.add(new HellionSound.Cue(open, true, Voices.eruptionBoom(2, 0.7f, 660)));
            out.add(new HellionSound.Cue(open + 10, false, Voices.labSlam()));
            for (int k = 0; k < 4; k++) out.add(new HellionSound.Cue(open + k * 70L + NucleusTimeline.FALL_MS, false, Voices.chopLand(661 + k)));
            out.add(new HellionSound.Cue(tl.showAt(), false, Voices.seraphArrive(true, 665)));
            out.add(new HellionSound.Cue(tl.showAt() + 200, false, Voices.seraphGlow(2.6f, true, 666)));
            out.add(new HellionSound.Cue(tl.textAt(), false, Voices.fanfare()));
        } else if (tl.forgeAt() >= 0) {
            // Divan's Alloy: the blow, the rush of the dive, then the forge's own soundtrack.
            out.add(new HellionSound.Cue(NucleusTimeline.REVEAL + 20, false, Voices.reverseSwell(NucleusTimeline.FLOOD_MS / 1000f)));
            out.add(new HellionSound.Cue(open, true, Voices.eruptionBoom(3, 0.8f, 614)));
            out.add(new HellionSound.Cue(open + 10, false, Voices.labSlam()));
            out.add(new HellionSound.Cue(open + 200, false, Voices.stingWhoosh(0.8f, 617)));
            out.add(new HellionSound.Cue(tl.forgeAt(), true, new Voice[0]));
            out.addAll(ForgeSound.schedule(tl.forgeAt()));
        } else {
            out.add(new HellionSound.Cue(NucleusTimeline.REVEAL + 20, false, Voices.reverseSwell(NucleusTimeline.FLOOD_MS / 1000f)));
            out.add(new HellionSound.Cue(open, true, Voices.eruptionBoom(3, 0.8f, 614)));
            out.add(new HellionSound.Cue(open + 10, false, Voices.labSlam()));
            out.add(new HellionSound.Cue(tl.showAt(), false, Voices.seraphArrive(true, 615)));
            out.add(new HellionSound.Cue(tl.showAt() + 200, false, Voices.seraphGlow(2.6f, true, 616)));
            out.add(new HellionSound.Cue(tl.textAt(), false, Voices.fanfare()));
        }
        // The spotlight on the three special drops: time stopping, the arrival, a long glow (the fanfare is at textAt, or
        // the forge's own at its words).
        if (tl.spotAt() >= 0) {
            long sp = tl.spotAt();
            out.add(new HellionSound.Cue(sp - 20, false, Voices.paintFreeze(690)));
            out.add(new HellionSound.Cue(sp, false, Voices.seraphArrive(true, 691)));
            out.add(new HellionSound.Cue(sp + 30, false, Voices.relicBurst(2, 692)));
            out.add(new HellionSound.Cue(sp + 250, false, Voices.seraphGlow(4.4f, true, 693)));
        }
        for (int k = 0; k < tl.pile().size(); k += 2) {
            out.add(new HellionSound.Cue(tl.spillAt(k) + NucleusTimeline.ITEM_FLIGHT, false, Voices.coinLand()));
        }
        out.sort((a, b) -> Long.compare(a.at, b.at));
        return out;
    }
}
