package com.butcher.detect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Which SkyBlock island the player is on, so each animation's nametag scans only run where its creatures live.
 *
 * Read from Hypixel's own tab list ("Area: Crimson Isle"), once a second, and from a {@code /locraw} answer when another
 * mod asks for one ({"server":...,"map":"Crystal Hollows"}). Up to 0.3.20 every scan ran every tick on every island -
 * seven of them in a crowded Hub - and a nametag that happened to read like a lava creature's elsewhere was followed
 * like one.
 *
 * <b>It fails open</b>: an island it cannot read is "anywhere", and every animation runs as before. Missing a real kill
 * costs the player a reveal they earned; an extra scan costs nothing they can see.
 */
public final class Location {
    private Location() {}

    /** The island's name as Hypixel writes it ("Crimson Isle"), or null when it is not known. */
    private static volatile String area;
    private static long readAt;
    private static final long EVERY_MS = 1_000;

    public static final String HUB = "Hub", CRYSTAL_HOLLOWS = "Crystal Hollows", CRIMSON_ISLE = "Crimson Isle",
            THE_END = "The End", DWARVEN_MINES = "Dwarven Mines", GARDEN = "Garden", SPIDERS_DEN = "Spider's Den";

    /** Once a client tick; reads the tab list at most once a second. */
    public static void tick() {
        long now = System.currentTimeMillis();
        if (now - readAt < EVERY_MS) return;
        readAt = now;
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener conn = mc.getConnection();
        if (conn == null || mc.player == null) {
            area = null;
            return;
        }
        String found = null;
        try {
            for (PlayerInfo info : conn.getListedOnlinePlayers()) {
                Component name = info.getTabListDisplayName();
                if (name == null) continue;
                String a = areaOf(name.getString());
                if (a != null) {
                    found = a;
                    break;
                }
            }
        } catch (RuntimeException e) {
            // The tab list changing under us: try again in a second.
            return;
        }
        // Kept if the tab list says nothing this time (it is redrawn in pieces); dropped on leaving the server.
        if (found != null) area = found;
    }

    /** "Area: Crimson Isle" (colour codes already gone) -> "Crimson Isle"; anything else -> null. Pure. */
    public static String areaOf(String line) {
        if (line == null) return null;
        String s = DianaWatcher.plain(line).trim();
        int i = s.indexOf("Area:");
        if (i < 0 || i > 3) return null;
        String a = s.substring(i + 5).trim();
        return a.isEmpty() ? null : a;
    }

    /** A {@code /locraw} answer another mod asked for: {"server":"mini26AE","gametype":"SKYBLOCK",...,"map":"Crystal Hollows"}. */
    public static void onChatLine(String text) {
        if (text == null || !text.startsWith("{\"server\"")) return;
        int m = text.indexOf("\"map\":\"");
        if (m < 0) return;
        int end = text.indexOf('"', m + 7);
        if (end > m + 7) area = text.substring(m + 7, end);
    }

    /** Leaving a server forgets the island: the next one has to be read afresh. */
    public static void reset() {
        area = null;
        readAt = 0;
    }

    /** The island, or null if it has not been read. */
    public static String area() {
        return area;
    }

    /**
     * Whether the player may be on one of these islands: true when on one, and true when the island is not known.
     */
    public static boolean maybeIn(String... islands) {
        String a = area;
        if (a == null) return true;
        String l = a.toLowerCase(Locale.ROOT);
        for (String island : islands) {
            if (l.equals(island.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    /** For /butcher report and /butcher log. */
    public static String describe() {
        String a = area;
        return a == null ? "not read (every animation runs everywhere)" : a;
    }
}
