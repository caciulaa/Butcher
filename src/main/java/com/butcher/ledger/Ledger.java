package com.butcher.ledger;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Ledger's rules: what each stamp counts, which grade that reaches, and the luck stamps that look back over the
 * last drops. Pure - no Minecraft, no files - so tools/LedgerTest runs it offline. ButcherStats owns the data and
 * calls in here whenever a number moves; the book and the pop-up only read.
 *
 * Two kinds of stamp: a count (kills, drops, a worst dry run...) read from {@link Facts} every time, so the stats
 * already on disk earn their stamps the first time it runs; and an event (a hot streak, a drop at 3 AM...) that has
 * to be seen happening, kept in {@link LedgerData#flags} by {@link #award}.
 */
public final class Ledger {
    private Ledger() {}

    public static final List<Stamp> STAMPS = StampTable.ALL;
    /** How many rare drops the luck stamps can look back over. */
    public static final int RECENT = 60;
    /** "Hot Streak": two rare drops this close together. */
    public static final long HOT_STREAK_MS = 10 * 60_000L;
    /** The Ghosts' windows (the user's numbers): Double Haunting 30 s; Poltergeist and Matched Pair a minute. */
    public static final long HAUNT_MS = 30_000L, GHOST_RUN_MS = 60_000L;
    /** The secret runs of luck: Two-Timer and Hot Potato within 5 minutes; a Bigger Boat and No Ghost within 10. */
    public static final long TWO_TIMER_MS = 5 * 60_000L, FRENZY_MS = 10 * 60_000L;
    /** The pop-ups' windows: Spare Parts and Wink within a minute, Double Vision 5 minutes, Fever Dream 10. */
    public static final long SPARE_MS = 60_000L, DOUBLE_VISION_MS = 5 * 60_000L, FEVER_DREAM_MS = 10 * 60_000L;

    private static final Map<String, Stamp> BY_ID = new HashMap<>();
    private static final Map<Chapter, List<Stamp>> BY_CHAPTER = new EnumMap<>(Chapter.class);

    static {
        for (Chapter c : Chapter.values()) BY_CHAPTER.put(c, new ArrayList<>());
        for (Stamp s : STAMPS) {
            BY_ID.put(s.id(), s);
            BY_CHAPTER.get(s.chapter()).add(s);
        }
    }

    public static Stamp byId(String id) {
        return BY_ID.get(id);
    }

    public static List<Stamp> chapter(Chapter c) {
        return BY_CHAPTER.get(c);
    }

    /** Every grade there is to earn: a single stamp is one, a tiered one each of its tiers. */
    public static int totalGrades() {
        int n = 0;
        for (Stamp s : STAMPS) n += s.grades();
        return n;
    }

    public static int earnedGrades(LedgerData d) {
        int n = 0;
        for (Stamp s : STAMPS) n += grade(s, d);
        return n;
    }

    /** The grade earned so far (0 = not yet). */
    public static int grade(Stamp s, LedgerData d) {
        LedgerData.Entry e = d.stamps.get(s.id());
        return e == null ? 0 : Math.min(e.grade, s.grades());
    }

    // ------------------------------------------------------------------ what each stamp counts

    /** The number a stamp is measured by. For a stamp about one thing having happened: how many times (0 = never). */
    public static long value(Stamp s, Facts f, LedgerData d) {
        return switch (s.id()) {
            case "house_open" -> d.cutscenes;
            case "house_regular" -> d.cutscenes;
            case "house_full_menu" -> f.scathaKills > 0 && f.corpses > 0 && f.dianaKills > 0 && f.kills("blaze") > 0
                    && f.kills("lava") > 0 && f.kills("shark") > 0 && f.drops("ghost") > 0
                    && f.kills("revenant") > 0 && f.kills("tarantula") > 0 ? 1 : 0;
            case "house_always_wins" -> d.worstBustRun;
            case "house_tycoon" -> d.coins;
            case "house_places_to_be" -> d.skips;
            case "house_well_done" -> f.allDrops();
            case "house_collector" -> f.dropSources();

            case "scatha_worm_food" -> f.scathaKills;
            case "scatha_rare_cut" -> f.petsRare;
            case "scatha_choice_cut" -> f.petsEpic;
            case "scatha_prime_cut" -> f.petsLegendary;
            case "scatha_full_set" -> Math.min(f.petsRare, Math.min(f.petsEpic, f.petsLegendary));
            case "scatha_dry_aged", "scatha_dry_aged_45" -> f.scathaWorstDry;
            case "scatha_slippery" -> f.scathaMisses;
            case "scatha_dozen" -> f.pets();
            case "scatha_cereal" -> f.dwarvenOs;

            case "vg_thawed_out" -> f.corpses;
            case "vg_cold_hands" -> f.corpseLegendaries;
            case "vg_heart_of_glass" -> f.lockets;
            case "vg_frostbitten" -> f.corpseDyes;
            case "vg_ice_cold" -> f.corpseWorstDry;
            case "vg_skeleton_crew" -> f.corpseRolls;
            case "vg_cold_storage" -> f.corpseLegendaries;
            case "vg_will_o_wisp" -> f.item("Caged Wisp");
            case "vg_tough_skin" -> f.item("Frozen Scute");
            case "vg_master_key" -> f.item("Skeleton Key");

            case "diana_heads" -> f.dianaKills;
            case "diana_inquisition" -> f.dianaKillsBy.getOrDefault("INQUISITOR", 0);
            case "diana_brain_food" -> f.dianaDrop("Brain Food");
            case "diana_chimera" -> f.dianaDrop("Chimera I");
            case "diana_stinger" -> f.dianaDrop("Fateful Stinger");
            case "diana_manticore" -> f.dianaDrop("Manti-core");
            case "diana_crown" -> f.dianaDrop("Crown of Greed");
            case "diana_wool" -> f.dianaDrop("Shimmering Wool");
            case "diana_relic" -> f.dianaDrop("Minos Relic");
            case "diana_dye" -> f.dianaDrop("Mythological Dye");
            case "diana_pantheon" -> {
                long min = Long.MAX_VALUE;
                for (String n : DIANA_DROPS) min = Math.min(min, f.dianaDrop(n));
                yield min;
            }

            case "hellion_fire_watch" -> f.kills("blaze");
            case "hellion_double_down" -> f.drops("blaze", "Duplex I");
            case "hellion_roll_dice" -> f.drops("blaze", "Archfiend Dice");
            case "hellion_high_roller" -> f.drops("blaze", "High Class Archfiend Dice");
            case "hellion_playing_fire" -> f.drops("blaze", "Flame Dye");
            case "hellion_ashes" -> f.worstDry("blaze");
            case "hellion_power_trip" -> f.drops("blaze", "Scorched Power Crystal");
            case "hellion_cold_feet" -> f.drops("blaze", "Subzero Inverter");
            case "hellion_blueprints" -> f.drops("blaze", "Wilson's Engineering Plans");
            case "hellion_paragon" -> f.drops("blaze", "Paragon Shard");
            case "hellion_hellfire_club" -> f.drops("blaze");
            case "hellion_seventh_circle" -> {
                long min = Long.MAX_VALUE;
                for (String n : HELLION_DROPS) min = Math.min(min, f.drops("blaze", n));
                yield min;
            }

            case "lava_magma_maker" -> f.kills("lava");
            case "lava_flash" -> f.drops("lava", "Flash I");
            case "lava_meltdown" -> f.drops("lava", "Radioactive Vial");
            case "lava_end_times" -> f.drops("lava", "Burnt Texts");
            case "lava_kuudra" -> f.drops("lava", "Carmine Dye");
            case "lava_thermal_runaway" -> f.worstDry("lava");
            case "lava_chained" -> f.drops("lava", "Chain of the End Times");
            case "lava_pinch_me" -> f.drops("lava", "Severed Pincer");
            case "lava_forged" -> f.drops("lava", "Brimstone Handle");
            case "lava_shell_shocked" -> f.drops("lava", "Scuttler Shell");
            case "lava_hot_catch" -> f.drops("lava");

            case "fin_shark_week" -> f.kills("shark");
            case "fin_big_fish" -> f.drops("shark", "Megalodon");
            case "fin_tooth_fairy" -> f.drops("shark", "Great White Shark Tooth");
            case "fin_off_the_hook" -> Math.max(0, f.kills("shark") - f.drops("shark"));
            case "fin_dentist" -> f.drops("shark", "Great White Shark Tooth");
            case "fin_school" -> f.drops("shark", "Megalodon");
            case "fin_dry_dock" -> f.worstDry("shark");
            case "fin_both_ends" -> Math.min(f.drops("shark", "Megalodon"), f.drops("shark", "Great White Shark Tooth"));

            case "wisp_sorrowful" -> f.drops("ghost", "Sorrow");
            case "wisp_plasma" -> f.drops("ghost", "Plasma");
            case "wisp_spirit_level" -> f.drops("ghost");
            case "wisp_drowning" -> f.drops("ghost", "Sorrow");
            case "wisp_high_voltage" -> f.drops("ghost", "Plasma");
            case "wisp_bittersweet" -> Math.min(f.drops("ghost", "Sorrow"), f.drops("ghost", "Plasma"));

            case "rev_six_feet" -> f.kills("revenant");
            case "rev_heads_roll" -> f.drops("revenant", "Beheaded Horror");
            case "rev_maggot" -> f.drops("revenant", "Festering Maggot");
            case "rev_smite" -> f.drops("revenant", "Smite VII");
            case "rev_hand" -> f.drops("revenant", "Severed Hand");
            case "rev_reaper" -> f.drops("revenant", "Scythe Blade");
            case "rev_shredded" -> f.drops("revenant", "Shard of the Shredded");
            case "rev_deep_dark" -> f.drops("revenant", "Warden Heart");
            case "rev_wake" -> f.drops("revenant", "Matcha Dye");
            case "rev_graveyard_shift" -> f.worstDry("revenant");
            case "rev_undertaker" -> f.drops("revenant");
            case "rev_rest_in_pieces" -> every(f, "revenant", REVENANT_DROPS);

            case "tara_exterminator" -> f.kills("tarantula");
            case "tara_talisman" -> f.drops("tarantula", "Tarantula Talisman");
            case "tara_venom" -> f.drops("tarantula", "Vial of Venom");
            case "tara_swatter" -> f.drops("tarantula", "Fly Swatter");
            case "tara_snail" -> f.drops("tarantula", "Ensnared Snail");
            case "tara_husk" -> f.drops("tarantula", "Shriveled Wasp");
            case "tara_digested" -> f.drops("tarantula", "Digested Mosquito");
            case "tara_thousand_eyes" -> f.drops("tarantula", "Primordial Eye");
            case "tara_red_thread" -> f.drops("tarantula", "Brick Red Dye");
            case "tara_tangled" -> f.worstDry("tarantula");
            case "tara_silk_road" -> f.drops("tarantula");
            case "tara_whole_brood" -> every(f, "tarantula", TARANTULA_DROPS);

            case "garden_feverish" -> {
                long n = 0;
                for (String t : FEVER_TIERS) n += f.drops("garden", "Crop Fever " + t);
                yield n;
            }
            case "garden_green_thumb" -> f.drops("garden", "Crop Fever Rare");
            case "garden_crazy" -> f.drops("garden", "Crop Fever Crazy Rare");
            case "garden_answered" -> f.drops("garden", RNGESUS_CROP);
            case "garden_copper" -> f.drops("garden", "Copper Dye");
            case "garden_berry" -> f.drops("garden", "Wild Strawberry Dye");
            case "garden_small_step" -> f.drops("garden", "Space Helmet");
            case "garden_painted" -> Math.min(f.drops("garden", "Copper Dye"), f.drops("garden", "Wild Strawberry Dye"));
            case "garden_guests" -> {
                long n = f.drops("garden");
                for (String t : FEVER_TIERS) n -= f.drops("garden", "Crop Fever " + t);
                yield Math.max(0, n);
            }

            case "auto_scrap" -> f.drops("automaton");
            case "auto_ticker" -> f.drops("automaton", "Synthetic Heart");
            case "auto_switch" -> f.drops("automaton", "Control Switch");
            case "auto_motor" -> f.drops("automaton", "Superlite Motor");
            case "auto_mirror" -> f.drops("automaton", "Robotron Reflector");
            case "auto_crysis" -> f.drops("automaton", "FTX 3070");
            case "auto_signal" -> f.drops("automaton", "Electron Transmitter");
            case "auto_assembly" -> every(f, "automaton", AUTOMATON_PARTS);

            case "eye_spy" -> f.drops("eye");
            case "eye_wide_awake", "eye_insomniac" -> f.drops("eye", AWAKENED_EYE);

            // The Crystal Nucleus: a run is a "kill", a run's best 1.5M+ drop its drop (the stats keep only the best).
            case "nuc_five_alive" -> f.kills("nucleus");
            case "nuc_safecracker" -> f.drops("nucleus");
            case "nuc_divine" -> f.drops("nucleus", "Divan's Alloy");
            case "nuc_jaded" -> f.drops("nucleus", "Jade Dye");
            case "nuc_quick_draw" -> f.drops("nucleus", "Quick Claw");
            case "nuc_green_with_envy" -> f.drops("nucleus", "Jaderald");
            case "nuc_mixologist" -> f.drops("nucleus", "Gemstone Mixture");
            case "nuc_flawless_victory" -> flawless(f);
            case "nuc_full_spectrum" -> every(f, "nucleus", FLAWLESS);
            case "nuc_locked_out" -> f.worstDry("nucleus");
            case "nuc_heist" -> every(f, "nucleus", CROWN_JEWELS);

            // Everything else is an event: it happened (award) or it did not. Happened, it reads as its own number
            // (Back to Back's 50, In the Black's 30M), so a numbered event stamps too - read as 1, it never did.
            default -> d.flags.containsKey(s.id()) ? s.need(s.grades()) : 0;
        };
    }

    /** Diana's eight drops, as the stats name them. */
    /** Every Demonlord drop with its own ending, as the stats name them (Seventh Circle). */
    static final String[] HELLION_DROPS = {"Duplex I", "Scorched Power Crystal", "Subzero Inverter",
            "Wilson's Engineering Plans", "Paragon Shard", "Archfiend Dice", "High Class Archfiend Dice", "Flame Dye"};
    static final String[] DIANA_DROPS = {"Brain Food", "Chimera I", "Fateful Stinger", "Manti-core", "Crown of Greed",
            "Shimmering Wool", "Minos Relic", "Mythological Dye"};
    /** The tier V slayers' own drops (Paragon Shard left out: it is the Demonlord's too), as SlayerTimeline names them. */
    static final String[] REVENANT_DROPS = {"Beheaded Horror", "Festering Maggot", "Smite VII", "Severed Hand", "Scythe Blade",
            "Shard of the Shredded", "Warden Heart", "Matcha Dye"};
    static final String[] TARANTULA_DROPS = {"Tarantula Talisman", "Vial of Venom", "Fly Swatter", "Ensnared Snail",
            "Shriveled Wasp", "Digested Mosquito", "Primordial Eye", "Brick Red Dye"};
    /** The Automaton's six parts, as AutomatonDrops names them. */
    static final String[] AUTOMATON_PARTS = {"Synthetic Heart", "Control Switch", "Superlite Motor", "Robotron Reflector",
            "FTX 3070", "Electron Transmitter"};
    /** Crop Fever's tiers as the Garden's stats name them ("Crop Fever " + CropFever.pretty). */
    static final String[] FEVER_TIERS = {"Uncommon", "Rare", "Crazy Rare", "Pray to RNGesus"};
    static final String RNGESUS_CROP = "Crop Fever Pray to RNGesus", AWAKENED_EYE = "Awakened Summoning Eye";

    /** The Nucleus's seven Flawless gemstones, as the stats name them; its two crown jewels. */
    static final String[] FLAWLESS = {"Flawless Amber Gemstone", "Flawless Amethyst Gemstone", "Flawless Jade Gemstone",
            "Flawless Jasper Gemstone", "Flawless Ruby Gemstone", "Flawless Sapphire Gemstone", "Flawless Topaz Gemstone"};
    static final String[] CROWN_JEWELS = {"Divan's Alloy", "Jade Dye"};

    /** Every Flawless gemstone the Nucleus has given, of any kind. */
    private static long flawless(Facts f) {
        long n = 0;
        for (String g : FLAWLESS) n += f.drops("nucleus", g);
        return n;
    }

    /** How many whole sets of {@code names} a source has: the fewest of any one of them. */
    private static long every(Facts f, String source, String[] names) {
        long min = Long.MAX_VALUE;
        for (String n : names) min = Math.min(min, f.drops(source, n));
        return min;
    }

    /** The grade a value reaches: how many of the stamp's numbers it has met. */
    public static int gradeFor(Stamp s, long value) {
        int g = 0;
        for (int i = 1; i <= s.grades(); i++) if (value >= s.need(i)) g = i;
        return g;
    }

    // ------------------------------------------------------------------ earning

    /**
     * Reads every stamp again and earns what the numbers now reach. {@code pre}: backdating from stats that existed
     * before the Ledger - stamped with no date, and not announced.
     *
     * @return the stamps earned or raised a grade just now, for the pop-up
     */
    public static List<Stamp> refresh(Facts f, LedgerData d, long now, boolean pre) {
        List<Stamp> fresh = new ArrayList<>();
        for (Stamp s : STAMPS) {
            if (s.chapter() == Chapter.SERAPH) continue;
            long v = value(s, f, d);
            int g = gradeFor(s, v);
            LedgerData.Entry e = d.stamps.get(s.id());
            if (g <= (e == null ? 0 : e.grade)) continue;
            if (e == null) {
                e = new LedgerData.Entry();
                d.stamps.put(s.id(), e);
            }
            e.grade = g;
            e.at = pre ? 0 : now;
            Long kill = d.flags.get(s.id());
            e.value = kill != null ? kill : v;
            d.seen.remove(s.id());
            if (!pre) fresh.add(s);
        }
        return fresh;
    }

    /** An event stamp happened: kept for good (the first time's {@code kill} is the one remembered). */
    public static void award(LedgerData d, String id, long kill) {
        d.flags.putIfAbsent(id, kill);
    }

    /** One real run of any animation, a win or not: the House's cutscene count and its bust run. */
    public static void run(LedgerData d, boolean win) {
        d.cutscenes++;
        if (win) {
            d.bustRun = 0;
        } else {
            d.bustRun++;
            d.worstBustRun = Math.max(d.worstBustRun, d.bustRun);
        }
    }

    /**
     * A rare drop: the luck stamps look at it against the last ones, then it joins them.
     *
     * @param kill that source's kill count with this kill (0 for a source with no kills)
     */
    public static void drop(LedgerData d, String source, String title, long kill, long now, ZoneId zone) {
        // The computer's clock went back (a new time zone, a fixed clock, a dead BIOS battery): nothing that needs
        // the time is trusted until it is past the last drop again - no hot streak against "future" drops, no 3 AM.
        boolean clockOk = d.recent.isEmpty() || now >= d.recent.get(d.recent.size() - 1).at;
        if (clockOk) timed(d, source, title, kill, now, zone);
        Stamp jackpot = byId("house_jackpot_777");
        if (kill > 0 && kill == (jackpot == null ? 777 : jackpot.number(777))) award(d, "house_jackpot_777", kill);
        // Natural Talent: a drop on the day's first kill of that animation (the user: it resets every day).
        if ("scatha".equals(source) ? d.scathaDay == day(now, zone) && d.scathaDayKills == 1 : killsToday(d, source, now, zone) == 1) {
            award(d, "house_natural", kill);
        }
        if ("blaze".equals(source)) {
            Stamp beast = byId("hellion_number_beast");
            if (kill > 0 && kill == (beast == null ? 666 : beast.number(666))) award(d, "hellion_number_beast", kill);
            LedgerData.Drop last = lastOf(d, source);
            if (last != null && title.contains("Archfiend Dice") && last.title != null && last.title.contains("Archfiend Dice")) {
                award(d, "hellion_snake_eyes", kill);
            }
        }
        if ("revenant".equals(source)) {
            Stamp casket = byId("rev_open_casket");
            long within = casket == null ? 10 : casket.number(10);
            LedgerData.Drop last = lastOf(d, source);
            if (last != null && last.kill > 0 && kill > last.kill && kill - last.kill <= within) award(d, "rev_open_casket", kill);
        }
        if ("nucleus".equals(source)) {
            // Double Dip: a 1.5M+ drop from the very next run after another (the stamp's number: how many runs apart).
            Stamp dip = byId("nuc_double_dip");
            long apart = dip == null ? 1 : dip.number(1);
            LedgerData.Drop last = lastOf(d, source);
            if (last != null && last.kill > 0 && kill > last.kill && kill - last.kill <= apart) award(d, "nuc_double_dip", kill);
        }
        if ("tarantula".equals(source)) {
            Stamp legs = byId("tara_eight_legs");
            if (kill > 0 && kill == (legs == null ? 888 : legs.number(888))) award(d, "tara_eight_legs", kill);
        }
        if ("scatha".equals(source)) {
            // Is That It?: the gap back to the last pet - from the recent drops, or the whole count for the first pet.
            LedgerData.Drop last = lastOf(d, source);
            long gap = last != null && last.kill > 0 && kill > last.kill ? kill - last.kill
                    : last == null && d.firstPetKill <= 0 ? kill : -1;
            Stamp meh = byId("scatha_is_that_it");
            if ("rare".equals(title) && gap >= (meh == null ? 300 : meh.number(300))) award(d, "scatha_is_that_it", kill);
            if (kill > 0 && kill % 100 == 0) award(d, "scatha_round_number", kill);
            if (d.firstPetKill <= 0) d.firstPetKill = kill;
            // A pet within the day's first N kills (scathaKill counts them; a new day starts again).
            Stamp lucky = byId("scatha_beginners_luck");
            long today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay();
            if (d.scathaDay == today && d.scathaDayKills > 0 && d.scathaDayKills <= (lucky == null ? 10 : lucky.number(10))) {
                award(d, "scatha_beginners_luck", kill);
            }
            Stamp b2b = byId("scatha_back_to_back");
            long within = b2b == null ? 50 : b2b.number(50);
            for (LedgerData.Drop prev : d.recent) {
                if ("scatha".equals(prev.source) && prev.kill > 0 && kill > prev.kill && kill - prev.kill <= within) {
                    award(d, "scatha_back_to_back", kill);
                    break;
                }
            }
        }
        LedgerData.Drop dr = new LedgerData.Drop();
        dr.source = source;
        dr.title = title;
        dr.kill = kill;
        dr.at = now;
        d.recent.add(dr);
        while (d.recent.size() > RECENT) d.recent.remove(0);
    }

    /** The newest of this source's recent drops, or null. */
    private static LedgerData.Drop lastOf(LedgerData d, String source) {
        for (int i = d.recent.size() - 1; i >= 0; i--) {
            LedgerData.Drop prev = d.recent.get(i);
            if (source.equals(prev.source)) return prev;
        }
        return null;
    }

    /** A real Scatha kill's outcome, for Cereal Killer: Dwarven O's busts in a row. A pet or gemstones end the run. */
    public static void scathaResult(LedgerData d, String rarity, boolean dwarvenOs, long kill) {
        if (rarity == null && dwarvenOs) {
            d.cerealRun++;
            Stamp cereal = byId("scatha_cereal_killer");
            if (d.cerealRun >= (cereal == null ? 3 : cereal.number(3))) award(d, "scatha_cereal_killer", kill);
        } else {
            d.cerealRun = 0;
        }
    }

    private static long day(long now, ZoneId zone) {
        return Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay();
    }

    /**
     * A real kill of {@code key} - an animation's source key, or "diana:" + the creature - for today's counts. A new
     * day (local time) starts every count again. The Scatha's own day count is {@link #scathaKill}.
     */
    public static void kill(LedgerData d, String key, long now, ZoneId zone) {
        long today = day(now, zone);
        if (today != d.killsDay) {
            d.killsDay = today;
            d.dayKills.clear();
        }
        d.dayKills.merge(key, 1L, Long::sum);
    }

    /** Today's kills of {@code key} so far (0 when the counts are for another day). */
    public static long killsToday(LedgerData d, String key, long now, ZoneId zone) {
        return d.killsDay == day(now, zone) ? d.dayKills.getOrDefault(key, 0L) : 0;
    }

    /** A real Scatha kill: today's count goes up, or starts again on a new day (local time). */
    public static void scathaKill(LedgerData d, long now, ZoneId zone) {
        long today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay();
        if (today != d.scathaDay) {
            d.scathaDay = today;
            d.scathaDayKills = 0;
        }
        d.scathaDayKills++;
    }

    /** The awards that read the clock: the hot streak, the night shift, the dates, and each source's runs of luck. */
    private static void timed(LedgerData d, String source, String title, long kill, long now, ZoneId zone) {
        for (LedgerData.Drop prev : d.recent) {
            if (now - prev.at <= HOT_STREAK_MS && now >= prev.at) {
                award(d, "house_hot_streak", kill);
                break;
            }
        }
        java.time.ZonedDateTime when = Instant.ofEpochMilli(now).atZone(zone);
        int hour = when.getHour();
        if (hour >= 3 && hour < 5) award(d, "house_night_shift", kill);
        if (when.getDayOfMonth() == 13 && when.getDayOfWeek() == java.time.DayOfWeek.FRIDAY) award(d, "house_unlucky_13", kill);
        if (when.getMonthValue() == 2 && when.getDayOfMonth() == 29) award(d, "house_leap_of_faith", kill);
        if (when.getMonthValue() == 12 && when.getDayOfMonth() == 25) award(d, "house_christmas_ham", kill);
        for (LedgerData.Drop prev : d.recent) {
            if (prev.source != null && !source.equals(prev.source) && now >= prev.at && now - prev.at <= TWO_TIMER_MS) {
                award(d, "house_two_timer", kill);
                break;
            }
        }
        // A source's own runs of luck, against its earlier drops (this one is not in the list yet).
        int inRun = 0, inFrenzy = 0;
        boolean sameTen = false, haunt = false, otherKind = false, sameFive = false;
        for (LedgerData.Drop prev : d.recent) {
            if (!source.equals(prev.source) || prev.at > now) continue;
            long ago = now - prev.at;
            if (ago <= HOT_STREAK_MS) sameTen = true;
            if (ago <= TWO_TIMER_MS) sameFive = true;
            if (ago <= FRENZY_MS) inFrenzy++;
            if (ago <= HAUNT_MS) haunt = true;
            if (ago <= GHOST_RUN_MS) {
                inRun++;
                if (!title.equals(prev.title)) otherKind = true;
            }
        }
        if ("shark".equals(source) && sameTen) award(d, "fin_feeding_frenzy", kill);
        if ("shark".equals(source) && inFrenzy >= 2) award(d, "fin_bigger_boat", kill);
        if ("lava".equals(source) && sameFive) award(d, "lava_hot_potato", kill);
        if ("ghost".equals(source) && inFrenzy >= 4) award(d, "wisp_aint_afraid", kill);
        if ("ghost".equals(source)) {
            if (haunt) award(d, "wisp_double_haunting", kill);
            if (inRun >= 2) award(d, "wisp_poltergeist", kill);
            if (otherKind) award(d, "wisp_matched_pair", kill);
            if (hour == 0) award(d, "wisp_witching_hour", kill);
        }
    }

    /**
     * A pop-up's drop (an Automaton part, a Summoning Eye, a Garden gift or Crop Fever drop): its chapter's runs of luck
     * and the day's counts. Kept apart from {@link #drop}: they come far too often for the House's luck stamps.
     */
    public static void tally(LedgerData d, String source, String title, long now, ZoneId zone) {
        // A clock that went back: today's counts still go up, nothing timed is trusted (as for the rare drops).
        boolean clockOk = d.tallies.isEmpty() || now >= d.tallies.get(d.tallies.size() - 1).at;
        kill(d, "tally:" + source, now, zone);
        kill(d, "tally:" + source + ":" + title, now, zone);
        long today = killsToday(d, "tally:" + source, now, zone);
        int hour = Instant.ofEpochMilli(now).atZone(zone).getHour();
        LedgerData.Drop sameKind = null, anyOther = null;
        for (int i = d.tallies.size() - 1; i >= 0; i--) {
            LedgerData.Drop prev = d.tallies.get(i);
            if (!source.equals(prev.source) || prev.at > now) continue;
            if (sameKind == null && title.equals(prev.title)) sameKind = prev;
            if (anyOther == null) anyOther = prev;
        }
        switch (source) {
            case "automaton" -> {
                if (clockOk && sameKind != null && now - sameKind.at <= SPARE_MS) award(d, "auto_spare", 0);
                boolean all = true;
                for (String p : AUTOMATON_PARTS) all &= killsToday(d, "tally:automaton:" + p, now, zone) > 0;
                if (all) award(d, "auto_six_pack", 0);
            }
            case "eye" -> {
                boolean awake = AWAKENED_EYE.equals(title);
                Stamp altar = byId("eye_full_altar"), marathon = byId("eye_marathon");
                if (today >= (altar == null ? 8 : altar.number(8))) award(d, "eye_full_altar", 0);
                if (today >= (marathon == null ? 25 : marathon.number(25))) award(d, "eye_marathon", 0);
                if (awake && killsToday(d, "tally:eye:" + AWAKENED_EYE, now, zone) >= 2) award(d, "eye_rude_awakening", 0);
                if (awake && today == 1) award(d, "eye_first_light", 0);
                if (clockOk) {
                    if (anyOther != null && now - anyOther.at <= DOUBLE_VISION_MS) award(d, "eye_double_vision", 0);
                    if (awake && anyOther != null && !AWAKENED_EYE.equals(anyOther.title) && now - anyOther.at <= SPARE_MS) {
                        award(d, "eye_wink", 0);
                    }
                    if (hour < 5) award(d, "eye_red_eye", 0);
                }
            }
            case "garden" -> {
                if (clockOk && RNGESUS_CROP.equals(title)) {
                    if (sameKind != null && now - sameKind.at <= FEVER_DREAM_MS) award(d, "garden_fever_dream", 0);
                    if (hour >= 5 && hour < 7) award(d, "garden_early_bird", 0);
                }
            }
            default -> { }
        }
        LedgerData.Drop dr = new LedgerData.Drop();
        dr.source = source;
        dr.title = title;
        dr.at = now;
        d.tallies.add(dr);
        while (d.tallies.size() > RECENT) d.tallies.remove(0);
    }

    /** A Vanguard corpse's worth, once every price is in: In the Black and Negative Equity. */
    public static void corpseWorth(LedgerData d, long net, long items, long corpse) {
        Stamp black = byId("vg_in_the_black");
        if (net >= (black == null ? 10_000_000L : black.number(10_000_000L))) award(d, "vg_in_the_black", corpse);
        if (net < 0) award(d, "vg_negative_equity", corpse);
        d.coins += Math.max(0, items);
    }
}
