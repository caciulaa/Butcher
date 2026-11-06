package com.butcher.detect;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which slayer quest is running, from Hypixel's own lines - so a drop more than one slayer gives (the Paragon Shard:
 * Sven, Voidgloom and Demonlord alike) only ever plays the animation of the slayer that dropped it. Two friends' Paragon
 * Shards, one from a Sven and one from a Voidgloom, played Hellion (2026-10-28 and 2026-11-01).
 *
 * Three lines say it, and none is guessed:
 * <ul>
 *   <li>the objective that follows every quest start at once, "» Slay 2,400 Combat XP worth of Blazes." (85 of 85
 *       starts in the user's game logs, 2026-11-01);</li>
 *   <li>the boss's spawn line, "SLAYER BOSS! The Sven Packmaster IV spawned!" (not with auto-slayer);</li>
 *   <li>the level line that follows every quest complete, "Wolf Slayer LVL 7 - Next LVL in ..." or "LVL UP! ➜ Blaze Slayer
 *       LVL 9!" (1,144 of 1,144 completions in a friend's records).</li>
 * </ul>
 * A quest start forgets the last quest's kind, and a failed or cancelled quest ends it. ButcherClient feeds every game
 * line here wherever the player is - an island's watcher only hears its own island's chat, and a quest started anywhere
 * else would otherwise keep the last one's kind. The watchers feed it too (the same line twice changes nothing), so
 * the offline tests and replays need nothing else.
 */
public final class SlayerQuest {

    public enum Type { ZOMBIE, SPIDER, WOLF, ENDERMAN, BLAZE, VAMPIRE }

    /** How long after quest complete its level line still counts: it comes 1-33 ms after. */
    public static final long LEVEL_WAIT_MS = 1_500;

    private Type current;
    private Type completed;
    private long completedAt = -1;

    /** The running quest's kind, or null when none is known (none running, or it started before the mod saw it). */
    public synchronized Type current() {
        return current;
    }

    /** The kind the last completed quest's level line named, if it came in the last {@link #LEVEL_WAIT_MS}. */
    public synchronized Type completed(long now) {
        return completed != null && now - completedAt <= LEVEL_WAIT_MS ? completed : null;
    }

    /** A game chat line, colour codes and all. */
    public synchronized void onChatLine(String text, long now) {
        if (text == null) return;
        String s = text.replaceAll("§.", "").trim();
        if (s.startsWith("SLAYER QUEST STARTED!")) {
            current = null;
            return;
        }
        if (s.startsWith("SLAYER QUEST FAILED!") || s.startsWith("Your Slayer Quest has been cancelled")) {
            current = null;
            return;
        }
        Type t = objectiveOf(s);
        if (t != null) {
            current = t;
            return;
        }
        if (s.startsWith("SLAYER BOSS!") && (t = bossOf(s)) != null) {
            current = t;
            return;
        }
        if ((t = levelOf(s)) != null) {
            completed = t;
            completedAt = now;
        }
    }

    private static final Pattern OBJECTIVE = Pattern.compile("^»\\s*Slay [\\d,.]+ Combat XP worth of (\\w+)");
    private static final Pattern LEVEL = Pattern.compile("\\b(Zombie|Spider|Wolf|Enderman|Blaze|Vampire) Slayer LVL \\d");

    /** "» Slay 2,400 Combat XP worth of Blazes." -> BLAZE. Pure. */
    public static Type objectiveOf(String plain) {
        Matcher m = OBJECTIVE.matcher(plain);
        if (!m.find()) return null;
        return switch (m.group(1).toLowerCase(Locale.ROOT)) {
            case "zombies" -> Type.ZOMBIE;
            case "spiders" -> Type.SPIDER;
            case "wolves" -> Type.WOLF;
            case "endermen" -> Type.ENDERMAN;
            case "blazes" -> Type.BLAZE;
            case "vampires" -> Type.VAMPIRE;
            default -> null;
        };
    }

    /** A boss's name anywhere in the line - "Inferno Demonlord IV", "Atoned Horror"... - its kind. Pure. */
    public static Type bossOf(String plain) {
        if (plain.contains("Revenant Horror") || plain.contains("Atoned Horror")) return Type.ZOMBIE;
        if (plain.contains("Tarantula Broodfather")) return Type.SPIDER;
        if (plain.contains("Sven Packmaster")) return Type.WOLF;
        if (plain.contains("Voidgloom Seraph")) return Type.ENDERMAN;
        if (plain.contains("Inferno Demonlord")) return Type.BLAZE;
        if (plain.contains("Riftstalker Bloodfiend")) return Type.VAMPIRE;
        return null;
    }

    /** "Wolf Slayer LVL 7 - Next LVL in ..." or "LVL UP! ➜ Blaze Slayer LVL 9!" -> its kind. Pure. */
    public static Type levelOf(String plain) {
        Matcher m = LEVEL.matcher(plain);
        return m.find() ? Type.valueOf(m.group(1).toUpperCase(Locale.ROOT)) : null;
    }
}
