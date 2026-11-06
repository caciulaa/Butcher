package com.butcher.detect;


import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A short, plain-English record of what the detectors decided and why.
 *
 * Neither half of the detection can be tested on demand: a Scatha pet drops
 * roughly once in two thousand kills, and a kill itself can only be had by
 * going and mining one. When something comes out wrong the only evidence is
 * whatever was written down at the time, so both detectors narrate
 * themselves here - every kill they accept, every kill they *refuse* and the
 * reason, and every item they matched or ignored.
 *
 * Read it back in-game with "/butcher log". Lines carry Minecraft colour
 * codes so the shape of a run is readable at a glance: green for something
 * accepted, red for something deliberately thrown away, grey for context.
 *
 * Borrowed wholesale from SkyMaxx, which keeps the same rolling log for the
 * same reason - its detector spends far more code refusing bad kills than
 * accepting good ones, and without a log a refusal is indistinguishable from
 * a bug.
 */
public final class DecisionLog {
    /** The last few hundred: enough for a bug report (/butcher report) to tell the story of a session's end. */
    private static final int KEEP = 300;
    /** How many /butcher log shows - the story of the last kill or two. */
    public static final int SHOWN = 40;
    /**
     * Whether every decision goes to the game log, or only what matters (openings in green, warnings in gold, errors
     * in red). Everything while Drop records is on - that log is what a bug report is read from - and only those
     * otherwise, so a player's log is not a page of nametag narration per fight. All of it stays in /butcher log and
     * /butcher report either way. Set by the client from the config.
     */
    public static volatile boolean verbose = true;

    private final Deque<String> lines = new ArrayDeque<>();
    /** Its own logger, not ButcherClient's, so the offline replays can use a DecisionLog without the game. */
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("butcher");
    /** When set, sees every decision as it is made (tools/HellionReplay prints them). */
    public static java.util.function.Consumer<String> tap;

    /** Records one decision. {@code text} may contain § colour codes. */
    public void note(String text) {
        synchronized (lines) {
            lines.addLast(text);
            while (lines.size() > KEEP) {
                lines.removeFirst();
            }
        }
        if (verbose || important(text)) LOG.info("[Butcher] {}", strip(text));
        else LOG.debug("[Butcher] {}", strip(text));
        if (tap != null) tap.accept(strip(text));
    }

    /** A decision a player's log should keep: it opened something (green), warns (gold) or failed (red). */
    static boolean important(String text) {
        if (text.length() < 2 || text.charAt(0) != '\u00a7') return true;
        char c = Character.toLowerCase(text.charAt(1));
        return c == 'a' || c == '6' || c == 'c';
    }

    /** The last {@code n}, oldest first. */
    public List<String> last(int n) {
        synchronized (lines) {
            List<String> all = new ArrayList<>(lines);
            return new ArrayList<>(all.subList(Math.max(0, all.size() - n), all.size()));
        }
    }

    /** Oldest first. */
    public List<String> recent() {
        synchronized (lines) {
            return new ArrayList<>(lines);
        }
    }

    public void clear() {
        synchronized (lines) {
            lines.clear();
        }
    }

    /** Drops § colour codes, for the copy that goes to the log file. */
    public static String strip(String in) {
        StringBuilder out = new StringBuilder(in.length());
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            if (c == '§' && i + 1 < in.length()) {
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
