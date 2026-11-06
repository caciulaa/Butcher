package com.butcher.anim;

/**
 * What the Nucleus's screen needs of a run, whichever style plays it: the Vault ({@link NucleusTimeline}) or the
 * Gauntlet ({@link GauntletTimeline}). Pure.
 */
public interface NucleusRun {
    /** How the haul plays (after the price floor and the menu's own-endings switch). */
    NucleusTimeline.Tier tier();

    /** The showcase: the haul's best item. */
    NucleusHaul.Item show();

    /** How many more items the haul had than the one shown. */
    int more();

    long total();

    /** When the showcase's words come up (the price is asked for a little before). */
    long textAt();

    long price();

    void price(long coins, String source);

    /** Where a plain haul set to Short jumps to: just before the reveal. */
    long shortTo();
}
