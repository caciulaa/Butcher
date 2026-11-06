package com.butcher.audio;

import com.butcher.anim.GauntletTimeline;
import com.butcher.anim.GauntletTimeline.Ending;

import java.util.ArrayList;
import java.util.List;

/**
 * The Gauntlet's soundtrack as a cue list, from voices the other cutscenes already use plus the snap's own: the rise; a
 * whoosh as each crystal flies in, its landing note climbing and a clank as it seats; the metal's creak as it turns; a
 * riser under the centre's cycle with a tick per step; cut at the landing, the landing's note. The same for every run
 * up to there. Then: a plain haul's cracks and the chime; a win's clench, boom and swell, its cracks, the arrival and
 * the fanfare; the snap's creak, charge, the click and boom, ringing ears (Voices.tinnitus), the dust on the wind, the
 * arrival.
 */
public final class GauntletSound {
    private GauntletSound() {}

    public static List<HellionSound.Cue> schedule(GauntletTimeline tl) {
        List<HellionSound.Cue> out = new ArrayList<>();
        out.add(new HellionSound.Cue(0, false, Voices.relicDust(0.9f, 91)));
        for (int k = 0; k < 5; k++) {
            long lock = GauntletTimeline.lock(k);
            out.add(new HellionSound.Cue(Math.max(0, lock - 280), false, Voices.stingWhoosh(0.32f, 920 + k)));
            out.add(new HellionSound.Cue(lock, false, new Voice[]{Voices.thawLand(1, k * 2)}));
            out.add(new HellionSound.Cue(lock + 60, false, Voices.midasClank(930 + k)));
        }
        out.add(new HellionSound.Cue(GauntletTimeline.TURN_FROM, false, Voices.stingCreak(0.65f, 85f, 58f, 940)));
        out.add(new HellionSound.Cue(GauntletTimeline.CYCLE_FROM, false,
                Voices.thawBuildUp((GauntletTimeline.REVEAL - GauntletTimeline.CYCLE_FROM) / 1000f)));
        for (int i = 0; i < GauntletTimeline.CYCLE.length; i++) {
            out.add(new HellionSound.Cue(GauntletTimeline.step(i), false, new Voice[]{Voices.thawCycleTick(i)}));
        }
        long r = GauntletTimeline.REVEAL;
        Ending e = tl.ending();
        out.add(new HellionSound.Cue(r, true, new Voice[]{Voices.thawLand(e == Ending.FALL ? 0 : 1, e == Ending.SNAP ? 8 : 4)}));
        switch (e) {
            case FALL -> {
                for (int k = 0; k < 5; k++) out.add(new HellionSound.Cue(tl.crackAt(k), false, Voices.seraphCrack(0.12f, 0.3f, 950 + k)));
                out.add(new HellionSound.Cue(tl.crackAt(4) + 400, false, Voices.coinLand()));
                out.add(new HellionSound.Cue(tl.textAt(), false, Voices.chime(3, 523.25f)));
            }
            case FIST -> {
                out.add(new HellionSound.Cue(r + 120, false, Voices.midasClank(960)));
                out.add(new HellionSound.Cue(r + 470, false, Voices.eruptionBoom(2, 0.7f, 961)));
                out.add(new HellionSound.Cue(r + 480, false, Voices.relicBurst(1, 962)));
                out.add(new HellionSound.Cue(r + 520, false, Voices.eruptionSwell(1.0f, 963)));
                out.add(new HellionSound.Cue(r + 1600, false, Voices.stingCreak(0.35f, 110f, 80f, 964)));
                for (int k = 0; k < 5; k++) out.add(new HellionSound.Cue(tl.crackAt(k), false, Voices.seraphCrack(0.14f, 0.35f, 965 + k)));
                out.add(new HellionSound.Cue(tl.popAt(), false, Voices.seraphArrive(true, 970)));
                out.add(new HellionSound.Cue(tl.popAt() + 200, false, Voices.seraphGlow(2.6f, true, 971)));
                out.add(new HellionSound.Cue(tl.textAt(), false, Voices.fanfare()));
            }
            case SNAP -> {
                long snap = tl.snapAt();
                out.add(new HellionSound.Cue(r + 150, false, Voices.stingCreak(0.5f, 100f, 70f, 980)));
                out.add(new HellionSound.Cue(r + GauntletTimeline.SNAP_POSED, false,
                        Voices.reverseSwell((snap - GauntletTimeline.HUSH - r - GauntletTimeline.SNAP_POSED) / 1000f)));
                if (tl.storm()) {
                    // The storm: zaps coming quicker as it charges.
                    long z = r + GauntletTimeline.SNAP_POSED + 100;
                    for (int i = 0; z < snap - GauntletTimeline.HUSH; i++) {
                        out.add(new HellionSound.Cue(z, false, Voices.eruptionZap(990 + i)));
                        z += Math.max(70, 260 - i * 22);
                    }
                } else {
                    // The Alloy: a note per beam, climbing.
                    for (int k = 0; k < 5; k++) {
                        out.add(new HellionSound.Cue(r + GauntletTimeline.BEAM_FROM + k * GauntletTimeline.BEAM_EVERY, false,
                                new Voice[]{Voices.thawLand(1, 4 + 2 * k)}));
                    }
                }
                // The hush: everything cut, one heartbeat.
                out.add(new HellionSound.Cue(snap - GauntletTimeline.HUSH, true, Voices.heartbeat(981)));
                out.add(new HellionSound.Cue(snap, true, Voices.gauntletSnap(982)));
                out.add(new HellionSound.Cue(snap + 10, false, Voices.eruptionBoom(3, 0.95f, 983)));
                out.add(new HellionSound.Cue(snap + 30, false, Voices.tinnitus(4.6f)));
                if (tl.storm()) {
                    out.add(new HellionSound.Cue(snap + 20, false, Voices.eruptionThunder(0.8f, 984)));
                    out.add(new HellionSound.Cue(snap + 120, false, Voices.seraphFreeze(985)));
                    out.add(new HellionSound.Cue(snap + GauntletTimeline.SHATTER_FROM, false, Voices.seraphShatter(986)));
                    out.add(new HellionSound.Cue(snap + GauntletTimeline.SHATTER_FROM + 60, false, Voices.dustWind(2.2f, 987)));
                    out.add(new HellionSound.Cue(snap + GauntletTimeline.STORM_DUST_FROM, false, Voices.dustWind(2.0f, 988)));
                } else {
                    out.add(new HellionSound.Cue(snap + 60, false, Voices.eruptionGroundCrack(984)));
                    out.add(new HellionSound.Cue(snap + GauntletTimeline.WORLD_FROM, false, Voices.dustWind(2.8f, 985)));
                    out.add(new HellionSound.Cue(snap + GauntletTimeline.DUST_FROM, false, Voices.dustWind(2.2f, 986)));
                }
                out.add(new HellionSound.Cue(tl.popAt(), false, Voices.seraphArrive(true, 990)));
                out.add(new HellionSound.Cue(tl.popAt() + 40, false, Voices.relicBurst(1, 991)));
                out.add(new HellionSound.Cue(tl.popAt() + 200, false, Voices.seraphGlow(3.2f, true, 992)));
                out.add(new HellionSound.Cue(tl.textAt(), false, Voices.fanfare()));
            }
        }
        out.sort((a, b) -> Long.compare(a.at, b.at));
        return out;
    }
}
