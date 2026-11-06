import com.butcher.detect.AutomatonDrops;
import com.butcher.detect.CropFever;

/**
 * The pop-ups' line readers against real lines from the user's game logs (2026-10-27): Crop Fever's start, drops and
 * cure - with the repeat counter a chat-compacting mod adds ("(2)", "(×2)"), which hid every repeated drop until
 * 0.3.27 - and the Automaton's part drops. Pure, no game:  java -cp build/classes/java/main;... PopupDetectTest
 */
public final class PopupDetectTest {
    static int passed, failed;

    static void check(boolean ok, String what) {
        if (ok) passed++;
        else {
            failed++;
            System.out.println("FAIL " + what);
        }
    }

    static String fever(String line) {
        CropFever.Event e = CropFever.parse(line);
        if (e == null) return null;
        return e.type() + (e.item() == null ? "" : " " + e.count() + "x " + e.item().name() + " " + CropFever.pretty(e.tier()));
    }

    public static void main(String[] a) {
        check("START".equals(fever("WOAH! You caught a case of the CROP FEVER for 60 seconds!")), "fever start");
        check("END".equals(fever("GONE! Your CROP FEVER has been cured!")), "fever cured");
        check("DROP 6x Enchanted Red Mushroom Uncommon".equals(fever("UNCOMMON DROP! You dropped 6x Enchanted Red Mushroom!")), "uncommon drop");
        check("DROP 12x Enchanted Red Mushroom Rare".equals(fever("RARE DROP! You dropped 12x Enchanted Red Mushroom!")), "rare drop");
        check("DROP 120x Enchanted Red Mushroom Crazy Rare".equals(fever("CRAZY RARE DROP! You dropped 120x Enchanted Red Mushroom!")), "crazy rare drop");
        check("DROP 6x Enchanted Red Mushroom Uncommon".equals(fever("UNCOMMON DROP! You dropped 6x Enchanted Red Mushroom! (2)")), "a repeat counter (2)");
        check("DROP 6x Enchanted Red Mushroom Uncommon".equals(fever("UNCOMMON DROP! You dropped 6x Enchanted Red Mushroom! (5)")), "a repeat counter (5)");
        check("DROP 12x Enchanted Red Mushroom Rare".equals(fever("RARE DROP! You dropped 12x Enchanted Red Mushroom! (×2)")), "a repeat counter (x2)");
        check(fever("[MVP+] Someone: RARE DROP! You dropped 12x Enchanted Red Mushroom!") == null, "a player's chat is not a drop");
        String[] parts = {"Electron Transmitter", "Robotron Reflector", "Control Switch", "FTX 3070", "Synthetic Heart", "Superlite Motor"};
        for (String p : parts) {
            AutomatonDrops.Part got = AutomatonDrops.of("RARE DROP! " + p + " (+178 ✯ Magic Find)");
            check(got != null && got.name().equals(p), "automaton part " + p);
            AutomatonDrops.Part again = AutomatonDrops.of("RARE DROP! " + p + " (+178 ✯ Magic Find) (2)");
            check(again != null && again.name().equals(p), "automaton part " + p + " with a repeat counter");
        }
        check(AutomatonDrops.of("[Bazaar] Sold 1x Control Switch for 52,000 coins!") == null, "a bazaar sale is not a drop");
        check(AutomatonDrops.of("[MVP+] Someone: RARE DROP! Control Switch") == null, "a player's chat is not a drop");
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
