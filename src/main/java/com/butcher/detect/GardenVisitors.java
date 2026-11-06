package com.butcher.detect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Garden's visitors in chat - pure, so tools run it over logs. Ported from a viewer's fork (2026-10-26), whose
 * arrival notice the user left out: only the gift plays, for an accepted offer whose reward is worth the
 * gardenGiftMin. The lines, as Hypixel sends them (the fork author's logs, 2026-09-29 on, and the user's own) and as
 * SkyHanni compacts them:
 * <pre>
 *   Wizard has arrived on your Garden!                      (the name in its rarity's colour)
 *   OFFER ACCEPTED with Carpenter (RARE)
 *
 *     REWARDS
 *       +30 Garden Experience
 *       +7.1k Farming XP
 *       +38 Copper
 *       §7§9Greenhouse Blueprint                             (an item: its own colour codes in the text)
 *    §8(§7x§r2§8)                                           (its count, on a line of its own)
 *       §7§aFine Flour §8x3
 *   [NPC] Carpenter: Here you go! ...
 *   §6§lOFFER ACCEPTED §7w/§r§9Carpenter§6§l!§r §230§7, §37.1k§7, §c38§7, §b10§7, §7§9Greenhouse Blueprint   (SkyHanni)
 * </pre>
 * A {@link Watcher} gathers an accepted offer's items for a moment - from the block, or SkyHanni's line, or both - and then
 * hands it on whole.
 */
public final class GardenVisitors {
    private GardenVisitors() {}

    // ------------------------------------------------------------------ the rewards worth knowing

    /**
     * A reward: its NEU id, the ids its price is looked up by (bazaar first, then the lowest BIN), how it is drawn (a
     * Hypixel item model, a head's NEU skin, or a vanilla icon), its colour, and whether it is always worth the animation
     * even when no price can be had (a Copper Dye is never under 5M).
     */
    public record Reward(String name, String id, String[] priceIds, String art, boolean head, String icon, int colour, boolean always,
                         boolean glint) {
    }

    private static final String M = "hypixel_skyblock:item/";

    private static Reward r(String name, String id, String priceId, String art, boolean head, String icon, int colour, boolean always) {
        return new Reward(name, id, new String[]{priceId}, art, head, icon, colour, always, false);
    }

    private static Reward book(String name, String id, String bazaarId, int colour) {
        return new Reward(name, id, new String[]{bazaarId}, "minecraft:enchanted_book", false, "enchanted_book", colour, false, true);
    }

    /** The visitors' rare rewards (SkyHanni's VisitorReward list and the NEU repo, 2026-10-25). */
    public static final List<Reward> RARE = List.of(
            r("Copper Dye", "DYE_COPPER", "DYE_COPPER", "DYE_COPPER", true, "orange_dye", 0xC87533, true),
            r("Wild Strawberry Dye", "DYE_WILD_STRAWBERRY", "DYE_WILD_STRAWBERRY", "DYE_WILD_STRAWBERRY", true, "pink_dye", 0xFF55FF, true),
            r("Space Helmet", "DCTR_SPACE_HELM", "DCTR_SPACE_HELM", "minecraft:red_stained_glass", false, "red_stained_glass", 0xFF5555, true),
            book("Dedication IV", "DEDICATION;4", "ENCHANTMENT_DEDICATION_4", 0x5555FF),
            book("Cultivating I", "CULTIVATING;1", "ENCHANTMENT_CULTIVATING_1", 0x5555FF),
            book("Replenish I", "REPLENISH;1", "ENCHANTMENT_REPLENISH_1", 0x5555FF),
            book("Delicate V", "DELICATE;5", "ENCHANTMENT_DELICATE_5", 0x5555FF),
            r("Music Rune I", "MUSIC_RUNE;1", "MUSIC_RUNE", "MUSIC_RUNE;1", true, "music_disc_cat", 0x55FFFF, false),
            r("Overgrown Grass", "OVERGROWN_GRASS", "OVERGROWN_GRASS", M + "reforge_stones/overgrown_grass", false, "wheat_seeds", 0xAA00AA,
                    false),
            r("Flowering Bouquet", "FLOWERING_BOUQUET", "FLOWERING_BOUQUET", M + "reforge_stones/flowering_bouquet", false, "sweet_berries", 0x5555FF,
                    false),
            r("Green Bandana", "GREEN_BANDANA", "GREEN_BANDANA", "GREEN_BANDANA", true, "lime_dye", 0xAA00AA, false),
            r("Jungle Key", "JUNGLE_KEY", "JUNGLE_KEY", M + "uncategorized/jungle_key", false, "gold_nugget", 0xAA00AA, false),
            r("Fruit Bowl", "FRUIT_BOWL", "FRUIT_BOWL", "FRUIT_BOWL", true, "bowl", 0x5555FF, false),
            r("Hypercharge Chip", "HYPERCHARGE_GARDEN_CHIP", "HYPERCHARGE_GARDEN_CHIP", M + "island_relevant/garden/chips/hypercharge_chip", false,
                    "paper", 0x5555FF, false),
            r("Quickdraw Chip", "QUICKDRAW_GARDEN_CHIP", "QUICKDRAW_GARDEN_CHIP", M + "island_relevant/garden/chips/quickdraw_chip", false,
                    "paper", 0x5555FF, false),
            r("Farming Exp Boost", "PET_ITEM_FARMING_SKILL_BOOST_EPIC", "PET_ITEM_FARMING_SKILL_BOOST_EPIC", "minecraft:iron_hoe", false,
                    "iron_hoe", 0xAA00AA, false),
            r("Harvest Harbinger V", "POTION_HARVEST_HARBINGER;5", "POTION_HARVEST_HARBINGER", "minecraft:potion", false, "potion", 0x5555FF,
                    false),
            r("Velvet Top Hat", "VELVET_TOP_HAT", "VELVET_TOP_HAT", "minecraft:leather_helmet", false, "leather_helmet", 0x5555FF, false),
            r("Cashmere Jacket", "CASHMERE_JACKET", "CASHMERE_JACKET", "minecraft:leather_chestplate", false, "leather_chestplate", 0x5555FF,
                    false),
            r("Satin Trousers", "SATIN_TROUSERS", "SATIN_TROUSERS", "minecraft:leather_leggings", false, "leather_leggings", 0x5555FF, false),
            r("Oxford Shoes", "OXFORD_SHOES", "OXFORD_SHOES", "minecraft:leather_boots", false, "leather_boots", 0x5555FF, false)
    );

    /** The reward called {@code name} ("Delicate V Book" is Delicate V), or one made up from its name for anything else. */
    public static Reward reward(String name) {
        // "◆ Music Rune I": the rune's diamond (a "?" in the log) is not part of its name.
        name = name.replaceAll("^[^A-Za-z0-9]+", "").trim();
        String n = name.endsWith(" Book") ? name.substring(0, name.length() - 5) : name;
        for (Reward r : RARE) if (r.name().equalsIgnoreCase(n)) return r;
        // A book of anything: ENCHANTMENT_NAME_LEVEL on the bazaar.
        Matcher b = ROMAN_BOOK.matcher(name);
        if (b.matches()) {
            int level = roman(b.group(2));
            String key = b.group(1).toUpperCase(Locale.ROOT).replace(' ', '_');
            if (level > 0) return book(b.group(1) + " " + b.group(2), key + ";" + level, "ENCHANTMENT_" + key + "_" + level, 0x5555FF);
        }
        String id = n.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
        return new Reward(n, id, new String[]{id}, null, false, "paper", 0xFFFFFF, false, false);
    }

    private static final Pattern ROMAN_BOOK = Pattern.compile("^([A-Za-z' ]+?) ([IVX]+) Book$");

    static int roman(String s) {
        Map<Character, Integer> v = Map.of('I', 1, 'V', 5, 'X', 10);
        int total = 0, prev = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int x = v.getOrDefault(s.charAt(i), 0);
            total += x < prev ? -x : x;
            prev = Math.max(prev, x);
        }
        return total;
    }

    // ------------------------------------------------------------------ the lines

    public enum Type { ARRIVE, ACCEPT, REWARD, COUNT, COMPACT, OTHER }

    /** One item of an offer's rewards and how many. */
    public record Item(String name, int count) {
    }

    /** A parsed line. {@code colour} the visitor's (RGB), or -1 when the line does not say. */
    public record Line(Type type, String name, int colour, String rarity, Item item, List<Item> items) {
    }

    static final Pattern ARRIVE = Pattern.compile("^([A-Za-z0-9 .'&()-]{2,48}?) has arrived on your Garden!$");
    static final Pattern ACCEPT = Pattern.compile("^OFFER ACCEPTED with ([A-Za-z0-9 .'&()-]{2,48}?) \\((COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|SPECIAL)\\)$");
    static final Pattern COMPACT = Pattern.compile("^OFFER ACCEPTED w/(.{2,48}?)! (.*)$");
    static final Pattern CURRENCY = Pattern.compile("^\\+[\\d,.]+k? (Copper|Farming XP|Farming Experience|Garden Experience|Bits|SkyBlock XP)$");
    static final Pattern PLUS_ITEM = Pattern.compile("^\\+([\\d,]+)x (.+)$");
    static final Pattern COUNT_LINE = Pattern.compile("^\\(x(\\d+)\\)$");
    static final Pattern ITEM_X = Pattern.compile("^(.+?) x(\\d+)$");
    static final Pattern NUMBER = Pattern.compile("^[\\d,.]+k?$");

    /** The rarity a visitor's colour stands for. */
    public static String rarityOf(int rgb) {
        return switch (rgb) {
            case 0xFFFFFF -> "COMMON";
            case 0x55FF55 -> "UNCOMMON";
            case 0x5555FF -> "RARE";
            case 0xFFAA00 -> "LEGENDARY";
            case 0xFF55FF -> "MYTHIC";
            case 0xFF5555 -> "SPECIAL";
            default -> null;
        };
    }

    /** A rarity's colour (RGB), white for one not known. */
    public static int colourOf(String rarity) {
        if (rarity == null) return 0xFFFFFF;
        return switch (rarity.toUpperCase(Locale.ROOT)) {
            case "UNCOMMON" -> 0x55FF55;
            case "RARE" -> 0x5555FF;
            case "EPIC" -> 0xAA00AA;
            case "LEGENDARY" -> 0xFFAA00;
            case "MYTHIC" -> 0xFF55FF;
            case "SPECIAL" -> 0xFF5555;
            default -> 0xFFFFFF;
        };
    }

    /**
     * Reads one chat line. {@code plain} as the game prints it (Component.getString: codes Hypixel put in the text itself
     * stay in), {@code legacy} the same with its styles as § codes, for the visitor's colour - may be null.
     */
    public static Line parse(String plain, String legacy) {
        if (plain == null) return null;
        String raw = plain.replace(' ', ' ');
        String s = strip(raw).trim();
        if (s.isEmpty()) return new Line(Type.OTHER, null, -1, null, null, null);
        Matcher m;
        if (!s.contains(": ") && !s.contains(" > ") && !s.startsWith("[")) {
            m = ARRIVE.matcher(s);
            if (m.matches()) {
                String name = m.group(1).trim();
                return new Line(Type.ARRIVE, name, colourBefore(legacy != null ? legacy : raw, name), null, null, null);
            }
            m = ACCEPT.matcher(s);
            if (m.matches()) return new Line(Type.ACCEPT, m.group(1).trim(), colourOf(m.group(2)), m.group(2), null, null);
            m = COMPACT.matcher(s);
            if (m.matches()) {
                String name = m.group(1).trim();
                List<Item> items = new ArrayList<>();
                for (String part : m.group(2).split(", ")) {
                    String p = part.trim();
                    if (p.isEmpty() || NUMBER.matcher(p).matches()) continue;
                    Item it = item(p);
                    if (it != null) items.add(it);
                }
                return new Line(Type.COMPACT, name, colourBefore(raw, name), null, null, items);
            }
        }
        // A reward line: indented in the block.
        if (raw.startsWith(" ")) {
            m = COUNT_LINE.matcher(s);
            if (m.matches()) return new Line(Type.COUNT, null, -1, null, new Item(null, Integer.parseInt(m.group(1))), null);
            if (s.equals("REWARDS") || CURRENCY.matcher(s).matches()) return new Line(Type.OTHER, null, -1, null, null, null);
            if (s.startsWith("+") && !PLUS_ITEM.matcher(s).matches()) return new Line(Type.OTHER, null, -1, null, null, null);
            Item it = item(s);
            if (it != null) return new Line(Type.REWARD, null, -1, null, it, null);
        }
        return null;
    }

    /** "Fine Flour x3", "+3x Gold Essence", "Greenhouse Blueprint". */
    static Item item(String s) {
        Matcher m = PLUS_ITEM.matcher(s);
        if (m.matches()) return new Item(m.group(2).trim(), parseInt(m.group(1)));
        m = ITEM_X.matcher(s);
        if (m.matches()) return new Item(m.group(1).trim(), parseInt(m.group(2)));
        if (s.length() < 2 || s.length() > 48 || s.contains(": ")) return null;
        return new Item(s, 1);
    }

    private static int parseInt(String s) {
        try {
            return Math.max(1, Integer.parseInt(s.replace(",", "")));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public static String strip(String s) {
        return s.replaceAll("§.", "");
    }

    /** The colour the last § code before {@code name} sets, as RGB; -1 if none. */
    static int colourBefore(String legacy, String name) {
        if (legacy == null) return -1;
        int at = legacy.indexOf(name);
        if (at < 0) {
            // Codes inside the name (rare): look before its first word.
            String first = name.split(" ")[0];
            at = legacy.indexOf(first);
        }
        if (at < 0) return -1;
        for (int i = at - 2; i >= 0; i--) {
            if (legacy.charAt(i) == '§') {
                int rgb = codeColour(Character.toLowerCase(legacy.charAt(i + 1)));
                if (rgb >= 0) return rgb;
            }
        }
        return -1;
    }

    /** A § code's colour (RGB), for tools and tests; -1 for a code that is no colour. */
    public static int codeColourOf(char c) {
        return codeColour(Character.toLowerCase(c));
    }

    static int codeColour(char c) {
        return switch (c) {
            case '0' -> 0x000000;
            case '1' -> 0x0000AA;
            case '2' -> 0x00AA00;
            case '3' -> 0x00AAAA;
            case '4' -> 0xAA0000;
            case '5' -> 0xAA00AA;
            case '6' -> 0xFFAA00;
            case '7' -> 0xAAAAAA;
            case '8' -> 0x555555;
            case '9' -> 0x5555FF;
            case 'a' -> 0x55FF55;
            case 'b' -> 0x55FFFF;
            case 'c' -> 0xFF5555;
            case 'd' -> 0xFF55FF;
            case 'e' -> 0xFFFF55;
            case 'f' -> 0xFFFFFF;
            default -> -1;
        };
    }

    // ------------------------------------------------------------------ gathering an offer

    /** An accepted offer: the visitor, its rarity's colour, and the items it gave (no Copper, XP or Bits). */
    public record Offer(String visitor, int colour, String rarity, List<Item> items) {
    }

    /**
     * Feeds on chat lines; tells its listener at once of an arrival, and of an accepted offer once its rewards are in -
     * {@link #GATHER_MS} after the accept line, or sooner when the block has plainly ended.
     */
    public static final class Watcher {
        public interface Listener {
            void arrived(String name, int colour);

            void accepted(Offer offer);
        }

        static final long GATHER_MS = 1500;
        private final Listener listener;
        private String visitor, rarity;
        private int colour = -1;
        private List<Item> items;
        private long closeAt;
        private boolean inBlock, compactSeen;
        /** The last offer handed on, so SkyHanni's line after it is not a second one. */
        private String lastVisitor;
        private long lastAt;

        public Watcher(Listener listener) {
            this.listener = listener;
        }

        public void onLine(String plain, String legacy, long now) {
            tick(now);
            Line l = parse(plain, legacy);
            if (l == null) {
                // Anything else ends the block's indented run (the [NPC] line after it).
                if (visitor != null && plain != null && !plain.startsWith(" ") && !strip(plain).trim().isEmpty()) inBlock = false;
                return;
            }
            switch (l.type()) {
                case ARRIVE -> listener.arrived(l.name(), l.colour());
                case ACCEPT -> {
                    flush();
                    visitor = l.name();
                    rarity = l.rarity();
                    colour = l.colour();
                    items = new ArrayList<>();
                    closeAt = now + GATHER_MS;
                    inBlock = true;
                    compactSeen = false;
                }
                case REWARD -> {
                    if (visitor != null && inBlock) items.add(l.item());
                }
                case COUNT -> {
                    if (visitor != null && inBlock && !items.isEmpty()) {
                        Item last = items.remove(items.size() - 1);
                        items.add(new Item(last.name(), l.item().count()));
                    }
                }
                case COMPACT -> {
                    if (visitor != null && visitor.equalsIgnoreCase(l.name())) {
                        // The block was seen: SkyHanni's line adds only what the block missed.
                        for (Item it : l.items()) if (items.stream().noneMatch(x -> x.name().equalsIgnoreCase(it.name()))) items.add(it);
                        compactSeen = true;
                        flush();
                    } else if (lastVisitor != null && lastVisitor.equalsIgnoreCase(l.name()) && now - lastAt < 5000) {
                        // Already handed on from the block.
                        return;
                    } else {
                        // Only SkyHanni's line came through (the block hidden): it is the offer.
                        flush();
                        int col = l.colour() >= 0 ? l.colour() : 0xFFFFFF;
                        listener.accepted(new Offer(l.name(), col, rarityOf(col), List.copyOf(l.items())));
                        lastVisitor = l.name();
                        lastAt = now;
                    }
                }
                default -> {
                }
            }
        }

        public void tick(long now) {
            if (visitor != null && now >= closeAt) flush();
        }

        private void flush() {
            if (visitor == null) return;
            Offer o = new Offer(visitor, colour, rarity, List.copyOf(items));
            lastVisitor = visitor;
            lastAt = closeAt - GATHER_MS;
            visitor = null;
            items = null;
            inBlock = false;
            listener.accepted(o);
        }
    }
}
