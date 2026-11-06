package com.butcher.detect;

import com.butcher.anim.NucleusHaul;
import com.butcher.anim.NucleusTimeline;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Notices a Crystal Nucleus run ending, and reads its loot bundle.
 *
 * The message has the Vanguard corpse's shape exactly (243 of them in the user's logs, 2025-10 .. 2026-10): the moment
 * the fifth crystal is placed, one tick brings a rule, "  CRYSTAL NUCLEUS LOOT BUNDLE " (a trailing space), a blank,
 * "  REWARDS", the items indented four (gems with a glyph or symbol in front, " xN" counts, a chat-compacting mod's
 * "(x6,178)" on a repeated line), SkyHanni's "Profit for Crystal Nucleus Run" flush against the margin inside it, the
 * closing rule, then "Pick it up near the Nucleus Vault!". So the outcome is known before anything is drawn, and none
 * of the Scatha's claim-window machinery applies - this is {@code VanguardWatcher}'s state machine with the Nucleus's
 * header, and it hands the lines to {@link NucleusHaul#parse}, which knows the items.
 *
 * Its lines are not hidden: while it reads ({@link #parsing}) chat counts as covered, so they are held at the chat box
 * with everything else and put back once the cutscene that opens on the closing rule is over (ButcherClient.chatHeld).
 * The same three ways out as the corpse's - the rule, a line cap, a tick timeout - and every one narrated.
 */
public final class NucleusWatcher {

    private static final Pattern START = Pattern.compile("^\\s*CRYSTAL NUCLEUS LOOT BUNDLE\\s*$");
    private static final Pattern END = Pattern.compile("^\\s*[\\u25AC]{10,}\\s*$");
    /** A compacting mod's count on a folded line: " (2)", " (×2)", "(x6,178)", square brackets. */
    private static final Pattern COLLAPSED = Pattern.compile("\\s*[(\\[](?:[x\\u00d7]\\s*)?[\\d,]+[)\\]]\\s*$");

    /** "✦ You placed the Jade Crystal!" - one per crystal as it goes in, the fifth just before the bundle. */
    private static final Pattern PLACED = Pattern.compile("^\\W*You placed the (Jade|Amber|Topaz|Sapphire|Amethyst) Crystal!\\s*$");
    /** A placement older than this is from a run before (the five go in within a minute or two, carried round). */
    static final long PLACED_MS = 30 * 60_000L;

    /** More lines than any bundle has: the seven Fine gems, up to seven Flawed, a dozen others, the furniture. */
    static final int MAX_LINES = 40;
    /** The whole message arrives in one tick. */
    static final int TIMEOUT_TICKS = 40;

    private final DecisionLog log;
    private final Consumer<NucleusHaul> onHaul;

    private boolean parsing;
    private int lines, ticksOpen, afterHeading;
    private final List<String> collected = new ArrayList<>();
    /** The crystals placed this run, in order, and when the last went in; the Gauntlet seats them in that order. */
    private final List<NucleusTimeline.Crystal> placed = new ArrayList<>();
    private long placedAt;

    public NucleusWatcher(DecisionLog log, Consumer<NucleusHaul> onHaul) {
        this.log = log;
        this.onHaul = onHaul;
    }

    /**
     * The five crystals in the order they went in this run, or null if not all five were seen (the Gauntlet then uses
     * its own order). Cleared once read.
     */
    public NucleusTimeline.Crystal[] takePlaced() {
        NucleusTimeline.Crystal[] out = placed.size() == 5 ? placed.toArray(new NucleusTimeline.Crystal[0]) : null;
        placed.clear();
        return out;
    }

    /** True while a loot bundle is being read: its lines wait with the rest of chat. */
    public boolean parsing() {
        return parsing;
    }

    /** Offer one chat line. Returns true if it belongs to a loot bundle. */
    public boolean onChatLine(String raw) {
        return onChatLine(raw, System.currentTimeMillis());
    }

    public boolean onChatLine(String raw, long now) {
        String text = COLLAPSED.matcher(DecisionLog.strip(raw)).replaceFirst("");
        java.util.regex.Matcher pm = PLACED.matcher(text);
        if (!parsing && pm.matches()) {
            NucleusTimeline.Crystal c = NucleusTimeline.Crystal.valueOf(pm.group(1).toUpperCase(java.util.Locale.ROOT));
            if (now - placedAt > PLACED_MS) placed.clear();
            placed.remove(c);
            placed.add(c);
            placedAt = now;
            return false;
        }
        if (START.matcher(text).matches()) {
            if (parsing) log.note("§eA second Nucleus bundle arrived before the first closed §8- dropping "
                    + collected.size() + " line(s) and starting again");
            parsing = true;
            lines = 0;
            ticksOpen = 0;
            afterHeading = 0;
            collected.clear();
            return true;
        }
        if (!parsing) return false;
        ticksOpen = 0;
        if (END.matcher(text).matches()) {
            finish("the message closed normally");
            return true;
        }
        if (++lines > MAX_LINES) {
            abandon("more than " + MAX_LINES + " lines arrived without a closing rule");
            return false;
        }
        if (text.isBlank()) return true;
        // "REWARDS": everything above it is preamble, not loot (the corpse has "+1 bonus drop!" there).
        if (heading(text)) {
            afterHeading = collected.size();
            return true;
        }
        // Another mod's line inside the message (SkyHanni's profit, flush against the margin): not ours, not loot.
        if (!Character.isWhitespace(text.charAt(0))) {
            log.note("§8Ignoring a line inside the Nucleus bundle: \"" + trim(text) + "\"");
            return false;
        }
        collected.add(text);
        return true;
    }

    /** Every client tick, so an unclosed bundle cannot stay open. */
    public void tick() {
        if (!parsing) return;
        if (++ticksOpen > TIMEOUT_TICKS) {
            if (collected.isEmpty()) abandon("nothing arrived within " + TIMEOUT_TICKS + " ticks of the header");
            else finish("no closing rule arrived within " + TIMEOUT_TICKS + " ticks");
        }
    }

    private void finish(String why) {
        List<String> loot = new ArrayList<>(collected.subList(Math.min(afterHeading, collected.size()), collected.size()));
        for (String s : collected.subList(0, Math.min(afterHeading, collected.size()))) {
            log.note("§8Not loot, it was above the heading: \"" + trim(s.strip()) + "\"");
        }
        parsing = false;
        collected.clear();
        NucleusHaul haul = NucleusHaul.parse(loot);
        if (haul.items.isEmpty()) {
            log.note("§cA Nucleus bundle came but nothing could be read from it §8(" + why + ")");
            return;
        }
        for (NucleusHaul.Item it : haul.items) {
            if (it.id() == null && it.worth() == 0 && !powderOrExp(it.name())) {
                log.note("§6Unknown Nucleus item \"" + it.name() + "\" §8- listed, not drawn. Add it to NucleusHaul.DEFS.");
            }
        }
        log.note("§aCrystal Nucleus bundle §8- " + haul.describe() + " §8(" + why + ")");
        onHaul.accept(haul);
    }

    private void abandon(String why) {
        log.note("§cGave up reading a Nucleus bundle §8- " + why
                + (collected.isEmpty() ? "" : " §8(" + collected.size() + " line(s) discarded)"));
        parsing = false;
        collected.clear();
    }

    /** Lines that are no item at all, and expected: powders and HOTM exp. */
    private static boolean powderOrExp(String name) {
        return name.contains("Powder") || name.contains("HOTM Exp");
    }

    /** A sub-heading ("REWARDS"): letters, none lower case. No item is printed that way. */
    private static boolean heading(String text) {
        boolean letter = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLowerCase(c)) return false;
            letter |= Character.isLetter(c);
        }
        return letter;
    }

    private static String trim(String s) {
        return s.length() <= 48 ? s : s.substring(0, 48) + "...";
    }
}
