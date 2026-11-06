import com.butcher.anim.SlayerTimeline.Boss;
import com.butcher.anim.SlayerTimeline.Drop;
import com.butcher.detect.DecisionLog;
import com.butcher.detect.RevTaraWatcher;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plays a records file's Revenant / Tarantula nametags ({@code slayer_mob}: seen, changed, gone) and slayer chat lines
 * back through the real {@link RevTaraWatcher} at their own times, and prints when each Grave or Cocoon would open and
 * with what - against when the boss's nametag read 0 and when its quest completed. Usage:
 * {@code java RevTaraReplay <butcher-drops.jsonl> [yyyy-mm-dd]}. Needs the game's classpath (the watcher imports it).
 *
 * The user's 2026-10-28 records settled the Tarantula's late death: its 0 comes 2.6 to 7.3 s before it really dies.
 */
public class RevTaraReplay {
    record Ev(long at, String kind, JsonObject o) {
    }

    public static void main(String[] args) throws Exception {
        String day = args.length > 1 ? args[1] : null;
        List<Ev> evs = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            JsonObject o = JsonParser.parseString(line).getAsJsonObject();
            String kind = o.get("kind").getAsString();
            if (!kind.equals("slayer_mob") && !kind.equals("slayer_line")) continue;
            String time = o.get("time").getAsString();
            if (day != null && !time.startsWith(day)) continue;
            evs.add(new Ev(Instant.parse(time).toEpochMilli(), kind, o));
        }
        long[] zeroAt = {-1};
        RevTaraWatcher w = new RevTaraWatcher(new DecisionLog(), new RevTaraWatcher.Listener() {
            long opened;

            @Override
            public void kill(Boss boss) {
                opened = now;
                System.out.println(t(now) + "  OPEN " + boss + " pending" + since(zeroAt[0]));
            }

            @Override
            public void resolve(Boss boss, Drop drop, String line) {
                System.out.println(t(now) + "    resolved " + drop + (line == null ? "" : " (" + line + ")"));
            }

            @Override
            public void killKnown(Boss boss, Drop drop, String line) {
                System.out.println(t(now) + "  OPEN " + boss + " known " + drop + since(zeroAt[0]));
            }
        });
        Map<Integer, RevTaraWatcher.Tag> present = new LinkedHashMap<>();
        for (Ev e : evs) {
            now = e.at;
            w.afterTick(now);
            if (e.kind.equals("slayer_line")) {
                String s = e.o.get("line").getAsString();
                if (s.contains("QUEST COMPLETE")) System.out.println(t(now) + "  -- quest complete");
                w.onChatLine(s, now);
                continue;
            }
            int id = e.o.get("entity").getAsInt();
            String name = e.o.get("name").getAsString(), ev = e.o.get("event").getAsString();
            double d = e.o.get("distance").getAsDouble();
            // A "gone" record carries the last name the stand had: seen once more with it, then gone.
            present.put(id, new RevTaraWatcher.Tag(id, name, d * d));
            if (ev.equals("gone")) {
                w.observe(new ArrayList<>(present.values()), false, now);
                present.remove(id);
            }
            if (name.contains("0/") && (name.contains("Broodfather") || name.contains("Horror")) && name.matches(".*\\s0/.*")) {
                if (zeroAt[0] < 0 || now - zeroAt[0] > 20_000) {
                    zeroAt[0] = now;
                    System.out.println(t(now) + "  -- nametag 0: " + name.replaceAll("[^ -~]", ""));
                }
            }
            w.observe(new ArrayList<>(present.values()), false, now);
        }
    }

    static long now;

    static String t(long ms) {
        // A whole second prints with no fraction ("...T20:13:10Z"): formatted, not cut out of toString.
        return java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(java.time.ZoneOffset.UTC).format(Instant.ofEpochMilli(ms));
    }

    static String since(long zero) {
        return zero < 0 ? "" : String.format(" (%.1f s after the 0)", (now - zero) / 1000.0);
    }
}
