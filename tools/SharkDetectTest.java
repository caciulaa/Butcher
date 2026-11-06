import com.butcher.detect.SharkDrops;
import com.butcher.detect.SharkWatcher;

/**
 * Fin's detection against the real Fishing Festival lines (the user's records, 2026-10-21). Offline:
 *
 *   javac -d out -cp build/classes/java/main tools/SharkDetectTest.java && java -cp "out;build/classes/java/main;<mc>" SharkDetectTest
 */
public final class SharkDetectTest {
    static int passed, failed;

    static void check(String what, boolean ok) {
        if (ok) passed++;
        else {
            failed++;
            System.out.println("FAIL " + what);
        }
    }

    public static void main(String[] a) {
        // The pet: its rarity is only the name's colour.
        check("an epic Megalodon by colour", SharkDrops.of("PET DROP! Megalodon", SharkDrops.EPIC_COLOUR) == SharkDrops.Drop.MEGALODON_EPIC);
        check("a legendary Megalodon by colour", SharkDrops.of("PET DROP! Megalodon", SharkDrops.LEGENDARY_COLOUR) == SharkDrops.Drop.MEGALODON_LEGENDARY);
        check("a mod's rewrite with the word", SharkDrops.of("§6§lPET DROP! §6§lLEGENDARY §6Megalodon", -1) == SharkDrops.Drop.MEGALODON_LEGENDARY);
        check("no colour, no word: epic", SharkDrops.of("PET DROP! Megalodon", -1) == SharkDrops.Drop.MEGALODON_EPIC);
        check("not a drop", SharkDrops.of("You sold your [Lvl 1] Megalodon to George for 500,000 Coins!", -1) == null);
        // The catch line, and what is not one.
        check("Hypixel's catch line", SharkWatcher.isCatch("Hide no longer, a Great White Shark has tracked your scent and thirsts for your blood!"));
        check("a mod's rewrite", SharkWatcher.isCatch("A Great White Shark has spawned! dau tweak"));
        check("a bazaar sale is not a catch", !SharkWatcher.isCatch("[Bazaar] Sold 2x Great White Shark Tooth for 6,115,239 coins!"));
        check("party chat is not a catch", !SharkWatcher.isCatch("Party > [MVP+] caciula: --> A GREAT WHITE SHARK has spawned <--"));
        check("someone's Feesh line is not a catch", !SharkWatcher.isCatch("[110] [MVP+] Player_4: [Feesh] RARE DROP! Great White Shark Tooth"));
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
