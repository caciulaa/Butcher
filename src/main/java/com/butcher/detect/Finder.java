package com.butcher.detect;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Drop lines that name who found the drop, and whether that is someone else. Pure (tools/LavaDetectTest).
 *
 * Hypixel tells everyone nearby about the biggest drops: "WOW! [MVP+] Friend_A found a Carmine Dye!" reached the
 * user and a second friend too (2026-10-21), and all three got the Carmine Dye's cutscene and its stamp - 0.3.16
 * read any line naming the dye. {@code ButcherClient.allowChatLine} hands a line that names another player to no
 * drop watcher at all, and the watchers that read such lines check again.
 */
public final class Finder {
    private Finder() {}

    /** A drop prefix ("WOW!", "RARE DROP!", "PRAY TO RNGESUS DROP!"...), a rank or none, the name, then the verb. */
    private static final Pattern FINDER = Pattern.compile(
            "^(?:WOW!|[A-Z][A-Z ]*DROP!)\\s+(?:\\[[^\\]]*\\]\\s*)?([A-Za-z0-9_]{2,16})\\s+(?:has\\s+)?(?:found|obtained|dropped|got|looted)\\b");

    /** Who a drop line says found it, or null when it names nobody (the usual "RARE DROP! <item>") or says "You". */
    public static String finder(String line) {
        if (line == null) return null;
        Matcher m = FINDER.matcher(DianaWatcher.plain(line).trim());
        if (!m.find()) return null;
        String name = m.group(1);
        return name.equalsIgnoreCase("You") ? null : name;
    }

    /** Whether a drop line names a player other than {@code me}. An unknown {@code me} never refuses a line. */
    public static boolean someoneElses(String line, String me) {
        String f = finder(line);
        return f != null && me != null && !me.isEmpty() && !f.equalsIgnoreCase(me);
    }
}
