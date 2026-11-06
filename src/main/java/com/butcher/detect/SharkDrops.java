package com.butcher.detect;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The two Fishing Festival drops worth an announcement: the Megalodon pet and the Great White Shark
 * Tooth, both from a Great White Shark. Read from the drop's chat line; pure, so it can be checked
 * offline.
 *
 * Checked against a real Fishing Festival (2026-10-21): the pet is "PET DROP! Megalodon", its rarity only the name's
 * colour (see {@link #of(String, int)}). The tooth has no drop line at all - it lands in the inventory and cannot go
 * into a sack, so SharkWatcher counts it there. Written before then (2026-10-02):
 * The shapes are Hypixel's usual drop lines, which SkyHanni's own pattern confirms for pets:
 * "PET DROP! EPIC Slug (+1300☘)" - the rarity is in the text. The tooth is an item, so "RARE DROP!"
 * or one of its louder cousins. Any "... DROP!" prefix is accepted, and DropRecorder keeps every
 * fishing line, so the first real one can settle it.
 */
public final class SharkDrops {
    private SharkDrops() {}

    public enum Drop {
        MEGALODON_EPIC("Megalodon", 0xAA00AA, "PET_MEGALODON"),
        MEGALODON_LEGENDARY("Megalodon", 0xFFAA00, "PET_MEGALODON"),
        TOOTH("Great White Shark Tooth", 0xFFAA00, "GREAT_WHITE_SHARK_TOOTH");

        public final String title;
        /** Its rarity colour, as Hypixel shows it. */
        public final int colour;
        /** The ID its price is looked up by - the bazaar for the tooth, Coflnet for the pet. */
        public final String priceId;

        Drop(String title, int colour, String priceId) {
            this.title = title;
            this.colour = colour;
            this.priceId = priceId;
        }

        public boolean pet() {
            return this != TOOTH;
        }
    }

    /** "PET DROP! ...", "RARE DROP! ...", "VERY RARE DROP! ..." - the drop itself after it. */
    private static final Pattern DROP = Pattern.compile("^[A-Z][A-Z ]*DROP! (.+)$");

    /** GOLD and DARK_PURPLE: the colours Hypixel gives a legendary and an epic pet's name. */
    public static final int LEGENDARY_COLOUR = 0xFFAA00, EPIC_COLOUR = 0xAA00AA;

    /**
     * What a chat line says dropped, with the colour the pet's name was drawn in (-1 unknown). The real line
     * (Fishing Festival, 2026-10-21) is {@code PET DROP! Megalodon} - the rarity is only the name's colour, gold or
     * purple, so read from the text alone every Megalodon was an epic, a friend's legendary too.
     */
    public static Drop of(String line, int nameColour) {
        Drop d = of(line);
        if (d == null || !d.pet()) return d;
        if (nameColour == LEGENDARY_COLOUR) return Drop.MEGALODON_LEGENDARY;
        if (nameColour == EPIC_COLOUR) return Drop.MEGALODON_EPIC;
        return d;
    }

    /** What a chat line says dropped, or null. Colour codes are stripped first. */
    public static Drop of(String line) {
        if (line == null) return null;
        String s = line.replaceAll("§.", "").strip();
        Matcher m = DROP.matcher(s);
        if (!m.find()) return null;
        String what = m.group(1);
        if (what.startsWith("You dug out") || what.startsWith("You caught")) return null;
        if (what.contains("Megalodon")) {
            return what.toUpperCase(Locale.ROOT).contains("LEGENDARY") ? Drop.MEGALODON_LEGENDARY : Drop.MEGALODON_EPIC;
        }
        if (what.contains("Great White Shark Tooth")) return Drop.TOOTH;
        return null;
    }
}
