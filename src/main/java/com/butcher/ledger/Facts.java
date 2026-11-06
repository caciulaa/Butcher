package com.butcher.ledger;

import java.util.HashMap;
import java.util.Map;

/**
 * What the stats say, in one place the Ledger can read without knowing where it came from: ButcherStats fills one
 * (ButcherStats.facts), and the offline test fills one by hand. Plain fields, no logic.
 */
public final class Facts {
    // The Scatha.
    public long scathaKills, petsRare, petsEpic, petsLegendary, scathaWorstDry, scathaMisses, dwarvenOs;
    // Vanguard: and every item by its name, as the corpse message names it.
    public long corpses, corpseLegendaries, corpseDyes, lockets, corpseWorstDry, corpseRolls;
    public Map<String, Integer> corpseItems = new HashMap<>();
    // Diana: kills of every creature together and by creature (its enum name, "INQUISITOR"), drops by name.
    public long dianaKills;
    public Map<String, Integer> dianaKillsBy = new HashMap<>();
    public Map<String, Integer> dianaDrops = new HashMap<>();
    // The later sources ("blaze", "lava", "shark", "ghost", "seraph"): kills, worst dry run, drops by name.
    public Map<String, Long> kills = new HashMap<>();
    public Map<String, Long> worstDry = new HashMap<>();
    public Map<String, Map<String, Integer>> drops = new HashMap<>();

    public long pets() {
        return petsRare + petsEpic + petsLegendary;
    }

    public long kills(String source) {
        return kills.getOrDefault(source, 0L);
    }

    public long worstDry(String source) {
        return worstDry.getOrDefault(source, 0L);
    }

    public long drops(String source, String title) {
        Map<String, Integer> m = drops.get(source);
        return m == null ? 0 : m.getOrDefault(title, 0);
    }

    public long drops(String source) {
        Map<String, Integer> m = drops.get(source);
        long n = 0;
        if (m != null) for (int v : m.values()) n += v;
        return n;
    }

    public long item(String name) {
        return corpseItems.getOrDefault(name, 0);
    }

    /** The pop-ups whose drops come far more often than a cutscene's: kept out of the House's totals. */
    static final java.util.Set<String> TALLIES = java.util.Set.of("automaton", "eye", "garden");

    /** Every rare drop counted anywhere: pets, legendary and dye rolls, Diana's, and the later sources' (no pop-ups). */
    public long allDrops() {
        long n = pets() + corpseLegendaries + corpseDyes;
        for (int v : dianaDrops.values()) n += v;
        for (String k : drops.keySet()) if (!TALLIES.contains(k)) n += drops(k);
        return n;
    }

    /** How many animations have given at least one rare drop. The Automaton and the Eye count; the Garden's crops do not. */
    public long dropSources() {
        long n = (pets() > 0 ? 1 : 0) + (corpseLegendaries + corpseDyes > 0 ? 1 : 0);
        long diana = 0;
        for (int v : dianaDrops.values()) diana += v;
        if (diana > 0) n++;
        for (String k : drops.keySet()) if (!"garden".equals(k) && drops(k) > 0) n++;
        return n;
    }

    public long dianaDrop(String name) {
        return dianaDrops.getOrDefault(name, 0);
    }
}
