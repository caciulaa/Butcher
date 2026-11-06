package com.butcher.detect;

import com.butcher.anim.SlayerTimeline.Boss;
import com.butcher.anim.SlayerTimeline.Drop;

import java.util.Locale;

/**
 * Reads the Revenant Horror's and the Tarantula Broodfather's drop lines (pure, tools/RevTaraDetectTest). The slayers'
 * own format, from the user's logs (2026-10-26): {@code RARE DROP! (4x Foul Flesh) (+116% ✯ Magic Find)} - the item in
 * the first brackets after the tier ({@link HellionDrops#item}). A dye may come the Diana way ("WOW! ... found a Matcha
 * Dye"); someone else's never reaches here (ButcherClient's Finder gate).
 */
public final class RevTaraDrops {
    private RevTaraDrops() {}

    /**
     * The animation's drop for a line: a win of {@code boss}'s (null: either's), {@link Drop#BUST} for any other slayer
     * drop, or null if the line is no drop line.
     */
    public static Drop of(String plain, Boss boss) {
        if (plain == null) return null;
        String s = plain.trim();
        String item = HellionDrops.item(s);
        if (item == null) {
            if (s.startsWith("WOW!") && s.contains("Matcha Dye")) return pick(Drop.MATCHA_DYE, boss);
            if (s.startsWith("WOW!") && s.contains("Brick Red Dye")) return pick(Drop.BRICK_RED_DYE, boss);
            return null;
        }
        String low = item.toLowerCase(Locale.ROOT);
        if (low.contains("paragon shard")) return Drop.PARAGON;
        for (Drop d : Drop.values()) {
            if (!d.win() || d == Drop.PARAGON) continue;
            if (low.contains(d.title.toLowerCase(Locale.ROOT))) return pick(d, boss);
        }
        return Drop.BUST;
    }

    private static Drop pick(Drop d, Boss boss) {
        return boss == null || d.from(boss) ? d : Drop.BUST;
    }

    /** Which slayer a quest is from its second line: "» Slay 7,200 Combat XP worth of Zombies." - or null. */
    public static Boss questBoss(String plain) {
        if (plain == null) return null;
        String s = plain.trim();
        if (!s.contains("Combat XP worth of")) return null;
        if (s.contains("Zombies")) return Boss.REVENANT;
        if (s.contains("Spiders")) return Boss.TARANTULA;
        return null;
    }

    /** Which boss a nametag names, or null: "☠ Atoned Horror 8.5M❤", "Revenant Horror V", "Tarantula Broodfather IV"... */
    public static Boss bossOf(String name) {
        if (name == null) return null;
        if (name.contains("Revenant Horror") || name.contains("Atoned Horror")) return Boss.REVENANT;
        if (name.contains("Tarantula Broodfather") || name.contains("Primordial Broodfather")) return Boss.TARANTULA;
        return null;
    }

    /**
     * The tier a boss nametag shows: the numeral after its name ("Revenant Horror IV" 4, "... V" 5), the tier V bosses'
     * own names (Atoned Horror, Primordial Broodfather) 5, else 0 - not known, taken as the tier V the user plays.
     */
    public static int tierOf(String name) {
        if (name == null) return 0;
        String s = name.replaceAll("§.", "");
        for (String base : new String[]{"Revenant Horror", "Atoned Horror", "Tarantula Broodfather", "Primordial Broodfather"}) {
            int at = s.indexOf(base);
            if (at < 0) continue;
            String rest = s.substring(at + base.length()).trim();
            if (rest.startsWith("V ") || rest.equals("V") || rest.startsWith("V ")) return 5;
            if (rest.startsWith("IV")) return 4;
            if (rest.startsWith("III")) return 3;
            if (rest.startsWith("II")) return 2;
            if (rest.startsWith("I ") || rest.equals("I")) return 1;
            if (base.startsWith("Atoned") || base.startsWith("Primordial")) return 5;
            return 0;
        }
        return 0;
    }
}
