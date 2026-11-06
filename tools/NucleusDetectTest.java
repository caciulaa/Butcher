import com.butcher.anim.NucleusHaul;
import com.butcher.anim.NucleusTimeline;
import com.butcher.detect.DecisionLog;
import com.butcher.detect.NucleusWatcher;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/**
 * The Crystal Nucleus's detection against real loot bundles, copied verbatim from the user's game logs (2025-10 ..
 * 2026-10): the old plain symbols, Hypixel's pack glyphs (U+E0xx), a colour-coded powder line with a compacting mod's
 * second count, SkyHanni's profit line inside the message, preamble above REWARDS, a folded header, a missing rule.
 * {@code -Dlogs=<the game's logs folder>} also replays every bundle in every log (.log and .log.gz) through the real
 * watcher and prints what each tier came to. Pure: needs only slf4j on the classpath.
 */
public class NucleusDetectTest {
    static int passed, failed;

    static void check(boolean ok, String what) {
        if (ok) passed++;
        else {
            failed++;
            System.out.println("FAIL " + what);
        }
    }

    static final String RULE = "▬".repeat(64);

    /** The 2025-10-03 run: plain symbols, a Lapidary book, HOTM exp. */
    static final String[] OLD = {
            RULE, "  CRYSTAL NUCLEUS LOOT BUNDLE ", "", "  REWARDS",
            "    Helix Fossil", "    Bob-omb x20", "    ⸕ Fine Amber Gemstone", "    ❈ Fine Amethyst Gemstone",
            "    ☘ Fine Jade Gemstone", "    ❁ Fine Jasper Gemstone", "    ❤ Fine Ruby Gemstone",
            "    ✎ Fine Sapphire Gemstone", "    ✧ Fine Topaz Gemstone x3", "    ⸕ Flawed Amber Gemstone x66",
            "    ❈ Flawed Amethyst Gemstone x46", "    ☘ Flawed Jade Gemstone x30", "    ❤ Flawed Ruby Gemstone x24",
            "    ✎ Flawed Sapphire Gemstone x6", "    ✧ Flawed Topaz Gemstone x6", "    +800 HOTM Exp",
            "    Enchanted Book (Lapidary I)", RULE, "Pick it up near the Nucleus Vault!"};

    /** The 2026-10-19 run: the pack's glyphs, the powder line in colour with "(x6,178)", SkyHanni inside. */
    static final String[] NEW = {
            RULE, "  CRYSTAL NUCLEUS LOOT BUNDLE ", "", "  REWARDS",
            "    Bob-omb x80", "     Fine Amber Gemstone", "     Fine Amethyst Gemstone", "     Fine Jade Gemstone",
            "     Fine Jasper Gemstone", "     Fine Ruby Gemstone", "     Fine Sapphire Gemstone",
            "     Fine Topaz Gemstone x2", "     Flawed Amber Gemstone x24", "     Flawed Amethyst Gemstone x6",
            "     Flawed Jade Gemstone x88", "     Flawed Jasper Gemstone x6", "     Flawed Ruby Gemstone x52",
            "     Flawed Sapphire Gemstone x30", "    Prehistoric Egg", "    §r§dGemstone Powder §r§8x4,926 §7(x6,178)",
            "[SkyHanni] Profit for Crystal Nucleus Run§e: §c-677k", RULE, "Pick it up near the Nucleus Vault!"};

    /** A win: a Flawless gem and a Jaderald (the shape of the 2025-10-11 run's sell-off), folded by a compacting mod. */
    static final String[] WIN = {
            RULE, "  CRYSTAL NUCLEUS LOOT BUNDLE  (2)", "", "  +1 bonus drop!", "  REWARDS",
            "    ❈ Flawless Amethyst Gemstone", "    Jaderald", "    Divan Fragment x5", "    ⸕ Fine Amber Gemstone (×2)",
            "    Gemstone Mixture", RULE};

    static List<String> fed;

    static NucleusHaul feed(String[] lines, List<Boolean> took) {
        NucleusHaul[] got = new NucleusHaul[1];
        NucleusWatcher w = new NucleusWatcher(new DecisionLog(), h -> got[0] = h);
        for (String l : lines) {
            boolean b = w.onChatLine(l);
            if (took != null) took.add(b);
            check(!(w.parsing() && got[0] != null), "the watcher is closed once it has a haul");
        }
        return got[0];
    }

    public static void main(String[] args) throws Exception {
        List<Boolean> took = new ArrayList<>();
        NucleusHaul old = feed(OLD, took);
        check(old != null, "the old bundle is read");
        check(old != null && old.tier() == NucleusTimeline.Tier.PLAIN, "the old bundle is plain");
        check(old != null && old.best() != null && old.best().id() != null, "it has a showcase");
        check(!took.get(0), "the rule before the header is not taken");
        check(took.get(1), "the header is taken");
        check(took.get(took.size() - 2), "the closing rule is taken");
        check(!took.get(took.size() - 1), "\"Pick it up\" is not taken");
        check(old != null && has(old, "Fine Topaz Gemstone", 3), "Fine Topaz x3");
        check(old != null && has(old, "Flawed Amber Gemstone", 66), "Flawed Amber x66");
        check(old != null && has(old, "Lapidary I", 1), "the Lapidary book");
        check(old != null && old.items.size() == 17, "17 lines, got " + (old == null ? 0 : old.items.size()));

        took.clear();
        NucleusHaul nw = feed(NEW, took);
        check(nw != null && nw.tier() == NucleusTimeline.Tier.PLAIN, "the glyph bundle is plain");
        check(nw != null && has(nw, "Fine Jade Gemstone", 1), "a glyph in front is read off");
        check(nw != null && has(nw, "Flawed Jade Gemstone", 88), "Flawed Jade x88");
        check(nw != null && has(nw, "Gemstone Powder", 4926), "the powder, its second count dropped");
        check(!took.get(NEW.length - 3), "SkyHanni's profit line inside is not taken");
        check(nw != null && nw.items.stream().noneMatch(i -> i.name().contains("Profit")), "nor read as an item");

        NucleusHaul win = feed(WIN, null);
        check(win != null, "a folded header still opens it");
        check(win != null && win.tier() == NucleusTimeline.Tier.WIN, "a win item makes it a win, whatever a stack is worth");
        // The Jaderald (~2.8M) over the Flawless gem (~2.5M), and either over five Divan Fragments, worth more only as a stack.
        check(win != null && win.best() != null && "Jaderald".equals(win.best().name()), "the Jaderald is the showcase, got "
                + (win == null || win.best() == null ? null : win.best().name()));
        check(win != null && win.items.stream().noneMatch(i -> i.name().contains("bonus")), "the preamble is not loot");
        check(win != null && has(win, "Fine Amber Gemstone", 1), "a folded line's (x2) is dropped");

        // No closing rule: it closes on the timeout with what it has.
        NucleusHaul[] got = new NucleusHaul[1];
        NucleusWatcher w = new NucleusWatcher(new DecisionLog(), h -> got[0] = h);
        for (int i = 0; i < 10; i++) w.onChatLine(OLD[i]);
        for (int i = 0; i < 45; i++) w.tick();
        check(got[0] != null && !w.parsing(), "a bundle with no rule closes on the timeout");

        // The order the crystals went in, for the Gauntlet: real lines, a folded repeat, one placed twice keeps its last place.
        NucleusWatcher pw = new NucleusWatcher(new DecisionLog(), h -> { });
        String[] order = {"Topaz", "Jade", "Amber", "Jade", "Sapphire", "Amethyst"};
        for (int i = 0; i < order.length; i++) {
            check(!pw.onChatLine("✦ You placed the " + order[i] + " Crystal!" + (i == 2 ? " (2)" : ""), 1_000L * i),
                    "a placement line is not part of a bundle");
        }
        NucleusTimeline.Crystal[] placed = pw.takePlaced();
        check(placed != null && java.util.Arrays.equals(placed, new NucleusTimeline.Crystal[]{NucleusTimeline.Crystal.TOPAZ,
                NucleusTimeline.Crystal.AMBER, NucleusTimeline.Crystal.JADE, NucleusTimeline.Crystal.SAPPHIRE,
                NucleusTimeline.Crystal.AMETHYST}), "the five in the order placed, got " + java.util.Arrays.toString(placed));
        check(pw.takePlaced() == null, "read once");
        pw.onChatLine("✦ You placed the Jade Crystal!", 0);
        pw.onChatLine("✦ You placed the Amber Crystal!", 1_000);
        check(pw.takePlaced() == null, "fewer than five: no order");
        pw.onChatLine("✦ You placed the Jade Crystal!", 0);
        for (String c : new String[]{"Amber", "Topaz", "Sapphire", "Amethyst"}) pw.onChatLine("✦ You placed the " + c + " Crystal!", 3_600_000L);
        check(pw.takePlaced() == null, "a placement from an hour before is a run before");
        check(!pw.onChatLine("[MVP+] Someone: You placed the Jade Crystal!", 0), "player chat is no placement");

        // Odds on every showcase that has them.
        check("1 in 1,095 bundles".equals(NucleusHaul.odds(NucleusHaul.test(NucleusTimeline.Tier.ALLOY).best())), "the Alloy's odds");

        String logs = System.getProperty("logs");
        if (logs != null) replay(Path.of(logs));
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    static boolean has(NucleusHaul h, String name, int count) {
        for (NucleusHaul.Item i : h.items) if (i.name().equals(name) && i.count() == count) return true;
        return false;
    }

    /** Every bundle in every log through the real watcher: how many, what tier, what was shown. */
    static void replay(Path dir) throws Exception {
        Map<String, Integer> tiers = new TreeMap<>(), shows = new TreeMap<>();
        int[] n = {0};
        List<Path> files = new ArrayList<>();
        try (var s = Files.list(dir)) {
            s.filter(p -> p.toString().endsWith(".log") || p.toString().endsWith(".log.gz")).sorted().forEach(files::add);
        }
        NucleusWatcher w = new NucleusWatcher(new DecisionLog(), h -> {
            n[0]++;
            tiers.merge(h.tier().name(), 1, Integer::sum);
            if (h.tier() != NucleusTimeline.Tier.PLAIN && h.best() != null) shows.merge(h.best().name(), 1, Integer::sum);
        });
        int headers = 0;
        for (Path f : files) {
            var in = Files.newInputStream(f);
            var r = new BufferedReader(new InputStreamReader(f.toString().endsWith(".gz") ? new GZIPInputStream(in) : in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                int c = line.indexOf("[CHAT] ");
                if (c < 0) {
                    w.tick();
                    continue;
                }
                String text = line.substring(c + 7);
                if (text.contains("CRYSTAL NUCLEUS LOOT BUNDLE")) headers++;
                w.onChatLine(text);
            }
            r.close();
        }
        System.out.println("replay: " + headers + " headers, " + n[0] + " bundles read; tiers " + tiers + "; wins " + shows);
        check(n[0] == headers, "every bundle in the logs is read (" + n[0] + " of " + headers + ")");
    }
}
