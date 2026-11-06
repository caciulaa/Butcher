package com.butcher.detect;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Crop Fever's chat lines - pure, so tools can run it over logs. The Ultimate enchantment on a farming tool: 60 seconds in
 * which every crop broken may roll a drop of four tiers (the wiki, 2026-10-24, and SkyHanni's own patterns):
 * <pre>
 *   WOAH! You caught a case of the CROP FEVER for 60 seconds!
 *   UNCOMMON DROP! You dropped 6x Enchanted Pumpkin!
 *   RARE DROP! You dropped 12x Enchanted Pumpkin!
 *   CRAZY RARE DROP! You dropped 110x Enchanted Pumpkin!
 *   PRAY TO RNGESUS DROP! You dropped 4x Polished Pumpkin!
 *   GONE! Your CROP FEVER has been cured!
 * </pre>
 * (The "You dropped" line was taken for a Pumpkin Dicer's in rng.6 - the dicers are gone; it is the fever's.)
 */
public final class CropFever {
    private CropFever() {}

    public enum Tier {
        UNCOMMON("UNCOMMON DROP", 0x55FF55, 0.15f),
        RARE("RARE DROP", 0x5555FF, 0.4f),
        CRAZY_RARE("CRAZY RARE DROP", 0xFF55FF, 0.75f),
        RNGESUS("PRAY TO RNGESUS DROP", 0xAA00AA, 1f);

        /** The chat's own words, without the "!". */
        public final String word;
        public final int colour;
        /** How big its pop-up goes, 0..1. */
        public final float intensity;

        Tier(String word, int colour, float intensity) {
            this.word = word;
            this.colour = colour;
            this.intensity = intensity;
        }

        static Tier of(String word) {
            for (Tier t : values()) if (t.word.equals(word)) return t;
            return null;
        }
    }

    /**
     * One of the fever's items: its NEU id (for its price), the item model the game draws it with and whether it glints
     * (the enchanted ones do), and a vanilla texture for the offline preview.
     */
    public record Item(String name, String id, String model, boolean glint, String icon) {
    }

    private static Item e(String name, String id, String model, String icon) {
        return new Item(name, id, model, true, icon);
    }

    private static Item p(String name, String id, String model, String icon) {
        return new Item(name, id, model, false, icon);
    }

    private static final String M = "hypixel_skyblock:item/";

    /** Every item the fever can give (the wiki's table, 2026-10-24), the model as the NEU repo gives it. */
    public static final List<Item> ITEMS = List.of(
            e("Enchanted Wheat", "ENCHANTED_WHEAT", "minecraft:wheat", "wheat"),
            e("Enchanted Hay Bale", "ENCHANTED_HAY_BLOCK", "minecraft:hay_block", "block:hay_block_side"),
            e("Enchanted Golden Carrot", "ENCHANTED_GOLDEN_CARROT", "minecraft:golden_carrot", "golden_carrot"),
            e("Enchanted Carrot", "ENCHANTED_CARROT", "minecraft:carrot", "carrot"),
            e("Enchanted Baked Potato", "ENCHANTED_BAKED_POTATO", "minecraft:baked_potato", "baked_potato"),
            e("Enchanted Potato", "ENCHANTED_POTATO", "minecraft:potato", "potato"),
            p("Polished Pumpkin", "POLISHED_PUMPKIN", M + "uncategorized/polished_pumpkin", "block:carved_pumpkin"),
            e("Enchanted Pumpkin", "ENCHANTED_PUMPKIN", "minecraft:carved_pumpkin", "block:carved_pumpkin"),
            e("Enchanted Sugar Cane", "ENCHANTED_SUGAR_CANE", "minecraft:sugar_cane", "sugar_cane"),
            e("Enchanted Sugar", "ENCHANTED_SUGAR", "minecraft:sugar", "sugar"),
            e("Enchanted Melon Slice", "ENCHANTED_MELON", "minecraft:melon_slice", "melon_slice"),
            e("Enchanted Melon", "ENCHANTED_MELON_BLOCK", "minecraft:melon", "block:melon_side"),
            e("Enchanted Cactus Green", "ENCHANTED_CACTUS_GREEN", "minecraft:green_dye", "green_dye"),
            e("Enchanted Cactus", "ENCHANTED_CACTUS", "minecraft:cactus", "block:cactus_side"),
            e("Enchanted Cocoa Beans", "ENCHANTED_COCOA", "minecraft:cocoa_beans", "cocoa_beans"),
            e("Enchanted Cookie", "ENCHANTED_COOKIE", "minecraft:cookie", "cookie"),
            e("Enchanted Brown Mushroom Block", "ENCHANTED_HUGE_MUSHROOM_1", "minecraft:brown_mushroom_block", "block:brown_mushroom_block"),
            e("Enchanted Brown Mushroom", "ENCHANTED_BROWN_MUSHROOM", "minecraft:brown_mushroom", "block:brown_mushroom"),
            e("Enchanted Red Mushroom Block", "ENCHANTED_HUGE_MUSHROOM_2", "minecraft:red_mushroom_block", "block:red_mushroom_block"),
            e("Enchanted Red Mushroom", "ENCHANTED_RED_MUSHROOM", "minecraft:red_mushroom", "block:red_mushroom"),
            p("Mutant Nether Wart", "MUTANT_NETHER_STALK", M + "uncategorized/mutant_nether_wart", "nether_wart"),
            e("Enchanted Nether Wart", "ENCHANTED_NETHER_STALK", "minecraft:nether_wart", "nether_wart"),
            p("Compacted Sunflower", "COMPACTED_SUNFLOWER", M + "island_relevant/garden/greenhouse/sunflower/compacted_sunflower",
                    "block:sunflower_front"),
            e("Enchanted Sunflower", "ENCHANTED_SUNFLOWER", "minecraft:sunflower", "block:sunflower_front"),
            p("Compacted Moonflower", "COMPACTED_MOONFLOWER", M + "island_relevant/garden/greenhouse/moonflower/compacted_moonflower",
                    "block:blue_orchid"),
            e("Enchanted Moonflower", "ENCHANTED_MOONFLOWER", "minecraft:blue_orchid", "block:blue_orchid"),
            p("Compacted Wild Rose", "COMPACTED_WILD_ROSE", M + "island_relevant/garden/greenhouse/wild_rose/compacted_wild_rose",
                    "block:rose_bush_top"),
            e("Enchanted Wild Rose", "ENCHANTED_WILD_ROSE", "minecraft:rose_bush", "block:rose_bush_top")
    );

    /** The item called {@code name}, or one standing in for a name not in the table (drawn as paper, still shown). */
    public static Item item(String name) {
        for (Item i : ITEMS) if (i.name().equalsIgnoreCase(name)) return i;
        return new Item(name, null, null, true, "paper");
    }

    // ------------------------------------------------------------------ the lines

    public enum Type { START, END, DROP }

    /** A line of the fever's: its start (with how long it lasts), its end, or one of its drops. */
    public record Event(Type type, int seconds, Tier tier, int count, Item item) {
    }

    private static final Pattern START = Pattern.compile("^WOAH! You caught a case of the CROP FEVER for (\\d+) seconds!$");
    private static final Pattern DROP = Pattern.compile("^(UNCOMMON|RARE|CRAZY RARE|PRAY TO RNGESUS) DROP! You dropped (\\d+)x ([A-Za-z' ]+?)!?$");
    static final String END = "GONE! Your CROP FEVER has been cured!";
    /** A repeat counter at the end of a line - "(2)", "(×2)", "(x2)", "[3]" - as VanguardWatcher strips it. */
    private static final Pattern COLLAPSED = Pattern.compile("\\s*[(\\[](?:[x\\u00d7]\\s*)?[\\d,]+[)\\]]\\s*$");

    public static Event parse(String raw) {
        if (raw == null) return null;
        // A chat-compacting mod adds "(2)" / "(×2)" to a repeated line - and fever drops repeat (6x Enchanted Red
        // Mushroom, again and again): every such drop was missed until 0.3.27 (the user's logs).
        String s = COLLAPSED.matcher(raw.replaceAll("§.", "").trim()).replaceFirst("");
        // Someone else's words in chat are never the fever's.
        if (s.contains(": ") || s.contains(" > ")) return null;
        if (s.equals(END)) return new Event(Type.END, 0, null, 0, null);
        Matcher m = START.matcher(s);
        if (m.matches()) return new Event(Type.START, Integer.parseInt(m.group(1)), null, 0, null);
        m = DROP.matcher(s);
        if (m.matches()) {
            Tier tier = Tier.of(m.group(1) + " DROP");
            int count = Integer.parseInt(m.group(2));
            return new Event(Type.DROP, 0, tier, count, item(m.group(3).trim()));
        }
        return null;
    }

    /** "12x Enchanted Hay Bale". */
    public static String label(Event e) {
        return e.count() + "x " + e.item().name();
    }

    /** "CRAZY RARE DROP" -> "Crazy Rare". */
    public static String pretty(Tier t) {
        String w = t.word.replace(" DROP", "").toLowerCase(Locale.ROOT);
        StringBuilder b = new StringBuilder();
        for (String part : w.split(" ")) {
            if (b.length() > 0) b.append(' ');
            b.append(part.equals("rngesus") ? "RNGesus" : part.equals("to") ? "to" : Character.toUpperCase(part.charAt(0)) + part.substring(1));
        }
        return b.toString();
    }
}
