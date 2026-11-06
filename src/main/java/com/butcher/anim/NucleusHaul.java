package com.butcher.anim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a Crystal Nucleus run gave: the lines of its "CRYSTAL NUCLEUS LOOT BUNDLE" under REWARDS, as items. Pure.
 *
 * The shape, from the user's 195 logged bundles (to 2026-10-19): every line indented, gems with a symbol in front
 * (" Fine Amber Gemstone", the glyph from Hypixel's pack, or "⸕" without it), a count after (" x60"), a
 * chat-compacting mod's repeat counter at the end ("(x7,317)", "(2)"), and powders and HOTM exp that are no item at all.
 * Every bundle has the seven Fine gems; most have Flawed ones; the rare lines are the Divan Fragment, Jaderald,
 * Gemstone Mixture, Flawless gems, Precious Pearl, Treasurite and Divan's Alloy.
 */
public final class NucleusHaul {

    /** One kind of item in the bundle. {@code id} is its NEU id - its head's key, or null for powders and exp. */
    public record Item(String id, String name, int count, int colour, long worth) {
        public boolean shown() {
            return id != null;
        }
    }

    /** What is known of an item: its NEU id, rarity colour and a rough worth for ordering (prices are looked up live). */
    record Def(String id, int colour, long worth) {
    }

    static final Map<String, Def> DEFS = defs();

    /** One of an item the haul knows by name ("Jaderald"), or null: the Gauntlet's cycle shows these. */
    public static Item item(String name) {
        Def d = DEFS.get(name);
        return d == null ? null : new Item(d.id(), name, 1, d.colour(), d.worth());
    }

    private static Map<String, Def> defs() {
        Map<String, Def> m = new java.util.HashMap<>();
        String[] gems = {"Amber", "Amethyst", "Jade", "Jasper", "Ruby", "Sapphire", "Topaz"};
        for (String g : gems) {
            String up = g.toUpperCase();
            m.put("Fine " + g + " Gemstone", new Def("FINE_" + up + "_GEM", 0x5555FF, 60_000));
            m.put("Flawed " + g + " Gemstone", new Def("FLAWED_" + up + "_GEM", 0x55FF55, 1_200));
            m.put("Flawless " + g + " Gemstone", new Def("FLAWLESS_" + up + "_GEM", 0xAA00AA, 2_500_000));
        }
        m.put("Divan's Alloy", new Def("DIVAN_ALLOY", 0xFFAA00, 1_100_000_000L));
        m.put("Jade Dye", new Def("DYE_JADE", 0x00AA00, 300_000_000L));
        m.put("Quick Claw", new Def("PET_ITEM_QUICK_CLAW", 0xFFAA00, 8_000_000L));
        m.put("Jaderald", new Def("JADERALD", 0x5555FF, 2_800_000));
        m.put("Gemstone Mixture", new Def("GEMSTONE_MIXTURE", 0x5555FF, 1_600_000));
        m.put("Divan Fragment", new Def("DIVAN_FRAGMENT", 0xAA00AA, 1_000_000));
        m.put("Precious Pearl", new Def("PRECIOUS_PEARL", 0xAA00AA, 900_000));
        m.put("Treasurite", new Def("TREASURITE", 0xAA00AA, 250_000));
        m.put("Recall Potion", new Def("RECALL_POTION", 0xAA00AA, 300_000));
        m.put("Dwarven O's Gemstone Grahams", new Def("DWARVEN_OS_GEMSTONE_GRAHAMS", 0xAA00AA, 200_000));
        m.put("Dwarven O's Metallic Minis", new Def("DWARVEN_OS_METALLIC_MINIS", 0xFFAA00, 200_000));
        m.put("Helix Fossil", new Def("HELIX", 0xFFAA00, 150_000));
        m.put("Claw Fossil", new Def("CLAW_FOSSIL", 0xFFAA00, 150_000));
        m.put("Pickonimbus 2000", new Def("PICKONIMBUS", 0xAA00AA, 60_000));
        m.put("Prehistoric Egg", new Def("PREHISTORIC_EGG", 0xFFFFFF, 40_000));
        m.put("Oil Barrel", new Def("OIL_BARREL", 0x55FF55, 20_000));
        m.put("Wishing Compass", new Def("WISHING_COMPASS", 0x55FF55, 3_000));
        m.put("Bob-omb", new Def("BOB_OMB", 0x5555FF, 1_000));
        m.put("Diamond Essence", new Def("ESSENCE_DIAMOND", 0xFF55FF, 1_000));
        m.put("Gold Essence", new Def("ESSENCE_GOLD", 0xFF55FF, 1_000));
        m.put("Ruby Crystal", new Def("RUBY_CRYSTAL", 0xAA00AA, 60_000));
        m.put("Jasper Crystal", new Def("JASPER_CRYSTAL", 0xAA00AA, 60_000));
        return m;
    }

    // ------------------------------------------------------------------ the odds

    /** Rolls a bundle averages (the wiki's column for no Nucleus perks). */
    static final double ROLLS = 16.5;
    /** The Jade Dye replaces a roll 1 time in 500,000 - outside the table. */
    static final double DYE_ROLL = 500_000;

    /**
     * The loot table, from the wiki's "Crystal Nucleus Drop Chances" (the user's screenshots, 2026-10-27): a name, the
     * count that line gives, and its weight. A bundle's chance of a line is ROLLS x weight / the total, as the wiki works
     * it out (it goes past 100% for the commonest).
     */
    static final Object[][] TABLE = table();

    private static Object[][] table() {
        List<Object[]> t = new ArrayList<>();
        String[] six = {"Ruby", "Amethyst", "Jade", "Amber", "Sapphire", "Topaz"};
        t.add(new Object[]{"Flawless Jasper Gemstone", 1, 5});
        for (String g : six) t.add(new Object[]{"Flawless " + g + " Gemstone", 1, 15});
        t.add(new Object[]{"Divan's Alloy", 1, 17});
        t.add(new Object[]{"Quick Claw", 1, 50});
        t.add(new Object[]{"Fine Jasper Gemstone", 1, 200});
        t.add(new Object[]{"Treasurite", 10, 200});
        t.add(new Object[]{"Recall Potion", 1, 250});
        t.add(new Object[]{"Wishing Compass", 3, 500});
        t.add(new Object[]{"Precious Pearl", 1, 500});
        t.add(new Object[]{"Claw Fossil", 1, 500});
        t.add(new Object[]{"Flawed Jasper Gemstone", 32, 700});
        t.add(new Object[]{"Gemstone Mixture", 1, 700});
        t.add(new Object[]{"Fortune IV", 1, 700});
        t.add(new Object[]{"Gold Essence", 10, 750});
        t.add(new Object[]{"Diamond Essence", 10, 750});
        for (String g : six) t.add(new Object[]{"Fine " + g + " Gemstone", 1, 900});
        t.add(new Object[]{"Jaderald", 1, 1450});
        t.add(new Object[]{"Dwarven O's Gemstone Grahams", 1, 1450});
        for (String g : six) t.add(new Object[]{"Flawed " + g + " Gemstone", 40, 2000});
        t.add(new Object[]{"Flawed Jasper Gemstone", 24, 2000});
        t.add(new Object[]{"Treasurite", 5, 2000});
        t.add(new Object[]{"Ruby Crystal", 1, 2500});
        t.add(new Object[]{"Jasper Crystal", 1, 2500});
        t.add(new Object[]{"Helix Fossil", 1, 4000});
        t.add(new Object[]{"Lapidary I", 1, 4000});
        for (String g : six) t.add(new Object[]{"Flawed " + g + " Gemstone", 24, 5000});
        t.add(new Object[]{"Flawed Jasper Gemstone", 12, 5000});
        t.add(new Object[]{"Prehistoric Egg", 1, 5000});
        t.add(new Object[]{"Divan Fragment", 1, 6000});
        t.add(new Object[]{"Oil Barrel", 1, 8000});
        for (String g : six) t.add(new Object[]{"Flawed " + g + " Gemstone", 12, 10000});
        t.add(new Object[]{"Flawed Jasper Gemstone", 6, 10000});
        t.add(new Object[]{"Pickonimbus 2000", 1, 10000});
        t.add(new Object[]{"Bob-omb", 20, 10000});
        for (String g : six) t.add(new Object[]{"Flawed " + g + " Gemstone", 6, 20000});
        return t.toArray(new Object[0][]);
    }

    static final int TOTAL_WEIGHT = total();

    private static int total() {
        int w = 0;
        for (Object[] r : TABLE) w += (Integer) r[2];
        return w;
    }

    /**
     * How many bundles, on average, give this line: its own row when the count matches one, else every row of that item
     * together (a stack two rolls landed on); 0 when the table does not have it.
     */
    public static double bundlesPer(Item it) {
        if (it == null) return 0;
        if ("DYE_JADE".equals(it.id())) return DYE_ROLL / ROLLS;
        int exact = 0, any = 0;
        for (Object[] r : TABLE) {
            if (!r[0].equals(it.name())) continue;
            any += (Integer) r[2];
            if ((Integer) r[1] == it.count()) exact += (Integer) r[2];
        }
        int w = exact > 0 ? exact : any;
        return w == 0 ? 0 : TOTAL_WEIGHT / (ROLLS * w);
    }

    /** "1 in 1,095 bundles", or null when the table does not know the line (or it comes in most bundles). */
    public static String odds(Item it) {
        double n = bundlesPer(it);
        if (n < 1.5) return null;
        String num = n >= 100 ? String.format(java.util.Locale.ROOT, "%,d", Math.round(n))
                : n >= 10 ? String.format(java.util.Locale.ROOT, "%.0f", n) : String.format(java.util.Locale.ROOT, "%.1f", n);
        return "1 in " + num + " bundles";
    }

    /** The drops that make a run a win (1.5M and up - the user, 2026-10-27); Divan's Alloy is the jackpot. */
    static boolean win(String id) {
        return id != null && (id.startsWith("FLAWLESS_") || id.equals("JADERALD") || id.equals("GEMSTONE_MIXTURE") || id.equals("DIVAN_ALLOY"));
    }

    private static final Pattern COUNT = Pattern.compile("^(.*?)\\s+x([\\d,]+)$");
    private static final Pattern COLLAPSED = Pattern.compile("\\s*[(\\[](?:[x\\u00d7]\\s*)?[\\d,]+[)\\]]\\s*$");

    public final List<Item> items;

    private NucleusHaul(List<Item> items) {
        List<Item> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingLong((Item i) -> i.worth() * Math.max(1, i.count())).reversed());
        this.items = List.copyOf(sorted);
    }

    /** For the log: the drawn items, most valuable first, with their counts; the rest as "+N more". */
    public String describe() {
        StringBuilder b = new StringBuilder();
        int shown = 0, more = 0;
        for (Item i : items) {
            if (!i.shown()) continue;
            if (shown == 6) {
                more++;
                continue;
            }
            if (shown++ > 0) b.append(", ");
            b.append(i.name()).append(i.count() > 1 ? " x" + i.count() : "");
        }
        if (more > 0) b.append(" +").append(more).append(" more");
        return b.append(" (").append(tier()).append(")").toString();
    }

    /** The bundle's lines under REWARDS (stripped of colour codes or not), in any order. */
    public static NucleusHaul parse(List<String> lines) {
        List<Item> out = new ArrayList<>();
        for (String raw : lines) {
            Item i = line(raw);
            if (i != null) out.add(i);
        }
        return new NucleusHaul(out);
    }

    static Item line(String raw) {
        if (raw == null) return null;
        String s = raw.replaceAll("§.", "").strip();
        if (s.isEmpty() || s.equals("REWARDS") || s.contains("Profit for")) return null;
        s = COLLAPSED.matcher(s).replaceAll("").strip();
        int count = 1;
        Matcher m = COUNT.matcher(s);
        if (m.matches()) {
            s = m.group(1).strip();
            try {
                count = Integer.parseInt(m.group(2).replace(",", ""));
            } catch (NumberFormatException ignored) {
            }
        }
        // A symbol in front: the gems' glyph (Hypixel's pack) or its plain stand-in.
        s = s.replaceFirst("^[^A-Za-z+]+", "").strip();
        if (s.startsWith("Enchanted Book")) {
            String book = s.replaceFirst("^Enchanted Book\\s*", "").replaceAll("[()]", "").strip();
            return new Item("ENCHANTED_BOOK", book.isEmpty() ? "Enchanted Book" : book, count, 0x5555FF, book.startsWith("Fortune") ? 900_000 : 40_000);
        }
        Def d = DEFS.get(s);
        if (d != null) return new Item(d.id(), s, count, d.colour(), d.worth());
        // Powders, HOTM exp and anything not known: listed, never drawn.
        return new Item(null, s, count, 0xAAAAAA, 0);
    }

    /** The drops with an ending of their own, grandest first (the user, 2026-10-27): the Alloy, the Jade Dye, the Quick Claw. */
    static final String[] SPECIAL = {"DIVAN_ALLOY", "DYE_JADE", "PET_ITEM_QUICK_CLAW"};

    private Item find(String id) {
        for (Item i : items) if (id.equals(i.id())) return i;
        return null;
    }

    /** The showcase: an item with its own ending if the haul has one, else the best item drawn. */
    public Item best() {
        for (String id : SPECIAL) {
            Item i = find(id);
            if (i != null) return i;
        }
        // A win's drop before anything worth more only as a stack: three Divan Fragments outweigh a Jaderald, but the
        // Jaderald is the one that makes the run (the replay of the user's logs: a win reported as plain).
        Item win = null;
        for (Item i : items) if (i.shown() && win(i.id()) && (win == null || i.worth() > win.worth())) win = i;
        if (win != null) return win;
        for (Item i : items) if (i.shown()) return i;
        return null;
    }

    public NucleusTimeline.Tier tier() {
        Item b = best();
        if (b == null) return NucleusTimeline.Tier.PLAIN;
        switch (b.id()) {
            case "DIVAN_ALLOY": return NucleusTimeline.Tier.ALLOY;
            case "DYE_JADE": return NucleusTimeline.Tier.JADE;
            case "PET_ITEM_QUICK_CLAW": return NucleusTimeline.Tier.CLAW;
            default: return win(b.id()) ? NucleusTimeline.Tier.WIN : NucleusTimeline.Tier.PLAIN;
        }
    }

    /** The items drawn on the pile, the best last (it lands on top): at most {@code max}; the rest are "+N more". */
    public List<Item> pile(int max) {
        List<Item> shown = new ArrayList<>();
        Item best = best();
        if (best != null) shown.add(best);
        for (Item i : items) if (i.shown() && i != best) shown.add(i);
        if (shown.size() > max) shown = new ArrayList<>(shown.subList(0, max));
        java.util.Collections.reverse(shown);
        return shown;
    }

    /** How many lines are not on the pile. */
    public int more(int max) {
        int shown = 0;
        for (Item i : items) if (i.shown()) shown++;
        return items.size() - Math.min(max, shown);
    }

    // ------------------------------------------------------------------ test hauls (real bundles from the user's logs)

    private static final String[] FINE = {" Fine Amber Gemstone", " Fine Amethyst Gemstone", " Fine Jade Gemstone",
            " Fine Jasper Gemstone", " Fine Ruby Gemstone", " Fine Sapphire Gemstone", " Fine Topaz Gemstone"};

    /** A plain run (2026-10-19), a Jaderald run, or the one Divan's Alloy. */
    public static NucleusHaul test(NucleusTimeline.Tier tier) {
        List<String> l = new ArrayList<>(List.of(FINE));
        switch (tier) {
            case PLAIN -> l.addAll(List.of("Divan Fragment x3", "Pickonimbus 2000", "Bob-omb x40", " Flawed Amber Gemstone x60",
                    " Flawed Amethyst Gemstone x12", " Flawed Jasper Gemstone x6", " Flawed Ruby Gemstone x6",
                    " Flawed Sapphire Gemstone x36", " Flawed Topaz Gemstone x60", "Gemstone Powder x5,834 (x7,317)"));
            case WIN -> l.addAll(List.of("Jaderald", "Treasurite", "Prehistoric Egg", " Flawed Jade Gemstone x24",
                    " Flawed Ruby Gemstone x48", " Flawed Sapphire Gemstone x30", "Mithril Powder x3,380"));
            case ALLOY -> l.addAll(List.of("Divan's Alloy", "Helix Fossil", "Oil Barrel", " Flawed Topaz Gemstone x42",
                    " Flawed Amber Gemstone x18", "Gemstone Powder x4,927"));
            case JADE -> l.addAll(List.of("Jade Dye", "Treasurite", "Bob-omb x40", " Flawed Jade Gemstone x36",
                    " Flawed Sapphire Gemstone x24", "Mithril Powder x2,940"));
            case CLAW -> l.addAll(List.of("Quick Claw", "Divan Fragment", "Prehistoric Egg", " Flawed Ruby Gemstone x30",
                    " Flawed Amethyst Gemstone x18", "Gemstone Powder x5,120"));
        }
        return parse(l);
    }
}
