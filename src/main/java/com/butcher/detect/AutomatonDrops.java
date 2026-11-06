package com.butcher.detect;

import com.butcher.render.DropArt;

import java.util.List;
import java.util.Locale;

/**
 * The Automaton's six robot parts, for the small pop-up. Pure. The line, from the user's own logs (163 of them by
 * 2026-10-26): {@code RARE DROP! Control Switch (+174 ✯ Magic Find)} - each part a 1% drop (the NEU repo), all six blue,
 * all six Hypixel pack items ({@code hypixel_skyblock:item/uncategorized/...}, paper without the pack).
 */
public final class AutomatonDrops {
    private AutomatonDrops() {}

    public record Part(String name, String id, DropArt art) {
    }

    public static final int COLOUR = 0x5555FF;
    /** Each part's chance, in percent. */
    public static final double CHANCE = 1.0;

    private static Part p(String name, String id, String model) {
        return new Part(name, id, DropArt.model("hypixel_skyblock:item/uncategorized/" + model));
    }

    public static final List<Part> PARTS = List.of(
            p("Synthetic Heart", "SYNTHETIC_HEART", "synthetic_heart"),
            p("Control Switch", "CONTROL_SWITCH", "control_switch"),
            p("Superlite Motor", "SUPERLITE_MOTOR", "superlite_motor"),
            p("Robotron Reflector", "ROBOTRON_REFLECTOR", "robotron_reflector"),
            p("FTX 3070", "FTX_3070", "ftx_3070"),
            p("Electron Transmitter", "ELECTRON_TRANSMITTER", "electron_transmitter"));

    /** The part a game line drops, or null: "RARE DROP! Control Switch", any "... DROP!" prefix, never player chat. */
    public static Part of(String raw) {
        if (raw == null) return null;
        String s = raw.replaceAll("§.", "").trim();
        if (s.contains(": ")) return null;
        int at = s.indexOf("DROP! ");
        if (at < 0 || at > 24) return null;
        String low = s.substring(at + 6).toLowerCase(Locale.ROOT);
        for (Part part : PARTS) if (low.startsWith(part.name.toLowerCase(Locale.ROOT))) return part;
        return null;
    }
}
