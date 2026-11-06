package com.butcher.audio;

import com.butcher.anim.SlayerTimeline;

import java.util.List;

import static com.butcher.anim.SlayerTimeline.REVEAL;

/**
 * The special endings' cues (The Grave's and the Cocoon's), each in step with its own beats in GraveEndings and
 * CocoonEndings (the times here are theirs, after the reveal). Pure.
 */
final class SlayerEndingSound {
    private SlayerEndingSound() {}

    static void add(List<HellionSound.Cue> out, SlayerTimeline tl) {
        long s = tl.seed();
        switch (tl.ending()) {
            case GRAB -> {
                cue(out, 0, Voices.heartMuffled());
                cue(out, 200, Voices.chopSplit());
                cue(out, 210, Voices.huntThud(0, false, s));
                cue(out, 620, Voices.huntWrench(s));
                cue(out, 700, Voices.chopWhoosh(1, s));
                cue(out, 1000, Voices.huntThud(1, false, s + 1));
                cue(out, 1050, Voices.labRising(0.45f));
                cue(out, 1450, Voices.chopSplit());
            }
            case REAPER -> {
                // In step with GraveEndings: settled at R_SET 380, swung from R_DRAWN 600, through the stone at R_CUT 770,
                // the top lands TOP_LANDS 900 after it.
                cue(out, 0, Voices.relicDust(0.9f, s));
                cue(out, 360, Voices.seraphStill(0.9f));
                cue(out, 640, Voices.stingWhoosh(0.7f, s));
                cue(out, 770, Voices.chopHit(2, s));
                cue(out, 780, Voices.thawCrash(s));
                cue(out, 1100, Voices.stingCreak(0.4f, 200f, 120f, s));
                cue(out, 1670, Voices.huntThud(2, false, s));
                cue(out, 1680, Voices.chopCrumble(s));
            }
            case SHREDDED -> {
                cue(out, 150, Voices.stingWhoosh(0.12f, s));
                cue(out, 160, Voices.chopHit(0, s));
                cue(out, 480, Voices.stingWhoosh(0.12f, s + 1));
                cue(out, 490, Voices.chopHit(1, s + 1));
                cue(out, 810, Voices.stingWhoosh(0.12f, s + 2));
                cue(out, 820, Voices.chopHit(2, s + 2));
                cue(out, 1150, Voices.thawExplode(s));
            }
            case DEEP_DARK -> {
                cue(out, 0, Voices.seraphRumble(2.2f, s));
                for (long at : new long[]{450, 1100, 1750, 2400, 3050, 3700}) cue(out, at, Voices.heartbeat(s + at));
                cue(out, 1250, Voices.eruptionGolem(0.9f, s));
                cue(out, 2450, Voices.seraphRoar(0.8f, true, 0.9f, s));
                cue(out, 2600, Voices.seraphShock(s));
                cue(out, 3000, Voices.eruptionSink(s));
            }
            case WAKE -> {
                cue(out, 200, Voices.paintFreeze(s));
                cue(out, 200, Voices.paintDrone(3.6f));
                for (int i = 0; i < 6; i++) {
                    cue(out, 420 + i * 170L, Voices.huntThud(i % 3, false, s + i));
                    cue(out, 520 + i * 170L, Voices.huntGroan(0.8f, s + 10 + i));
                }
                cue(out, 1500, Voices.paintWave(2.1f, s));
                cue(out, 3700, Voices.seraphJudgement(1.6f, s));
                cue(out, 3950, Voices.eruptionSwell(1.4f, s));
                cue(out, 4150, Voices.paintResume());
            }
            case THOUSAND_EYES -> {
                cue(out, 0, Voices.seraphStill(1.9f));
                for (int i = 0; i < 9; i++) cue(out, 150 + (long) (1750 * Math.pow(i / 9.0, 1.8)), Voices.seraphGlance(s + i));
                cue(out, 1350, Voices.seraphEyeOpen(s));
                cue(out, 2150, Voices.seraphImplode(0.75f));
                cue(out, 2950, Voices.seraphCoreHum(1.2f));
                cue(out, 3450, Voices.seraphHeartboom(s));
            }
            case RED_THREAD -> {
                cue(out, 160, Voices.hellionFreeze(s));
                cue(out, 160, Voices.hellionStill(4.4f));
                for (int i = 0; i < 5; i++) cue(out, 350 + i * 430L, Voices.huntPluck(i, i == 4, s + i));
                cue(out, 2650, Voices.huntWhirl(1.9f, s));
                cue(out, 3250, Voices.labRising(1.3f));
                cue(out, 4550, Voices.hellionSupernova(s));
            }
            case DIGESTED -> {
                cue(out, 0, Voices.paintBlup());
                for (int i = 0; i < 5; i++) cue(out, 200 + i * 230L, Voices.seraphGlug(i));
                cue(out, 1250, Voices.stingCreak(0.3f, 180f, 120f, s));
                cue(out, 1550, Voices.paintSplash(s));
                cue(out, 1560, Voices.stingImpact(s));
            }
            case HUSK -> {
                cue(out, 0, Voices.stingCreak(1.2f, 320f, 160f, s));
                cue(out, 400, Voices.relicDust(0.9f, s));
                cue(out, 1300, Voices.chopCrumble(s));
                cue(out, 1320, Voices.relicDust(0.7f, s + 1));
            }
            default -> { }
        }
    }

    private static void cue(List<HellionSound.Cue> out, long since, Voice[] voices) {
        out.add(new HellionSound.Cue(REVEAL + since, false, voices));
    }
}
