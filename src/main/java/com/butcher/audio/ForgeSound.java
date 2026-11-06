package com.butcher.audio;

import com.butcher.anim.ForgeTimeline;

import java.util.ArrayList;
import java.util.List;

/**
 * Divan's Alloy's forge as a cue list, from voices the other cutscenes already use, at ms from the cut into the forge:
 * the room waking (a boom, the furnace's roar, the braziers catching, the Automaton's clanks as it powers up), the
 * crystals' arrival and each one's glug into the metal, the choir as it turns white, the cradle's creak and the pour's
 * hiss, three anvil blows climbing (the last with a heartbeat boom), the quench as it cools, and the arrival - the
 * jackpot, the glow and the fanfare.
 */
public final class ForgeSound {
    private ForgeSound() {}

    public static List<HellionSound.Cue> schedule(long offset) {
        List<HellionSound.Cue> out = new ArrayList<>();
        add(out, offset, 0, Voices.eruptionBoom(2, 0.7f, 801));
        add(out, offset, ForgeTimeline.FIRE_FROM, Voices.hellionFire(3.4f, 802));
        add(out, offset, ForgeTimeline.BRAZIER_L, Voices.hellionIgnite(803));
        add(out, offset, ForgeTimeline.BRAZIER_R, Voices.hellionIgnite(804));
        add(out, offset, ForgeTimeline.EYE, Voices.relicIgnite());
        add(out, offset, ForgeTimeline.ARM_FROM, Voices.midasClank(805));
        add(out, offset, ForgeTimeline.ARM_TO - 200, Voices.midasClank(806));
        add(out, offset, ForgeTimeline.FLY_FROM, Voices.reverseSwell((ForgeTimeline.FLY_TO - ForgeTimeline.FLY_FROM) / 1000f));
        add(out, offset, ForgeTimeline.FLY_TO, Voices.chime(5, 392f));
        for (int k = 0; k < 5; k++) {
            long at = ForgeTimeline.DROP_FROM + k * ForgeTimeline.DROP_EVERY + ForgeTimeline.DROP_MS;
            add(out, offset, at, Voices.seraphGlug(k));
            out.add(new HellionSound.Cue(offset + ForgeTimeline.real(at + 10), false, new Voice[]{Voices.thawLand(1, k * 2)}));
        }
        add(out, offset, ForgeTimeline.WHITE, Voices.relicBurst(1, 807));
        add(out, offset, ForgeTimeline.WHITE + 80, Voices.seraphJudgement(2.6f, 808));
        add(out, offset, ForgeTimeline.TIP_FROM, Voices.stingCreak(0.6f, 80f, 55f, 809));
        add(out, offset, ForgeTimeline.STREAM_FROM, Voices.eruptionVent(810));
        add(out, offset, ForgeTimeline.STREAM_FROM + 40, Voices.stingHiss(1.0f, 811));
        for (int k = 0; k < 3; k++) {
            add(out, offset, ForgeTimeline.RAISE[k], Voices.midasClank(812 + k));
            add(out, offset, ForgeTimeline.STRIKE[k], Voices.eruptionAnvil(k, 820 + k));
            add(out, offset, ForgeTimeline.STRIKE[k] + 5, Voices.eruptionBoom(k == 2 ? 3 : 2, k == 2 ? 0.9f : 0.5f, 823 + k));
        }
        add(out, offset, ForgeTimeline.STRIKE[2] + 30, Voices.seraphHeartboom(826));
        add(out, offset, ForgeTimeline.COOL_FROM, Voices.eruptionQuench(827));
        add(out, offset, ForgeTimeline.COOL_FROM + 100, Voices.stingHiss(2.0f, 828));
        add(out, offset, ForgeTimeline.LIFT_FROM, Voices.eruptionSwell((ForgeTimeline.LIFT_TO - ForgeTimeline.LIFT_FROM) / 1000f, 829));
        add(out, offset, ForgeTimeline.LIFT_TO - 200, Voices.hellionJackpot(830));
        add(out, offset, ForgeTimeline.LIFT_TO - 190, Voices.eruptionArrive(true, 831));
        add(out, offset, ForgeTimeline.WORDS, Voices.fanfare());
        add(out, offset, ForgeTimeline.WORDS + 100, Voices.eruptionGlow(4.0f, 832));
        return out;
    }

    /** {@code at} is on the forge's scene clock: turned back to the screen's, which the slow motion stretches. */
    private static void add(List<HellionSound.Cue> out, long offset, long at, Voice[] voices) {
        out.add(new HellionSound.Cue(offset + ForgeTimeline.real(at), false, voices));
    }
}
