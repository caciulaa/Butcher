package com.butcher.anim;

import com.butcher.render.DropArt;

/**
 * The clock of the Revenant Horror's cutscene (The Grave) and the Tarantula Broodfather's (The Cocoon) - pure, so
 * tools/SlayerTest and the offline preview run the same frames. Built on a viewer's fork (2026-10-26), rebuilt the
 * Hellion way: it opens on every kill of the player's own tier V boss with the drop still unknown, and nothing before
 * {@link #REVEAL} depends on it (SlayerTest hashes every draw call for every drop on several seeds).
 *
 * <pre>
 *   0        the scene fades in
 *   BEATS    four digs / four cuts, each a little sooner
 *   REVEAL   the lid / the cocoon: dark for a bust, the drop's own light for a win
 *   arrive   the drop up in the middle: name, odds, price
 * </pre>
 *
 * The user's calls (2026-10-26): busts short, no colour cycle before the reveal (it took too long), and every drop worth
 * 8M or more its own ending - the dyes the grandest. Tier IV plays only for its dye, which arrives known.
 */
public final class SlayerTimeline {

    public enum Boss {
        REVENANT("Revenant Horror", "revenant"), TARANTULA("Tarantula Broodfather", "tarantula");

        public final String title;
        /** Its key in the stats, the bust setting and the menu. */
        public final String key;

        Boss(String title, String key) {
            this.title = title;
            this.key = key;
        }
    }

    /** How a win ends. PLAIN is the shared one; the rest are a drop's own. */
    public enum Ending { BUST, PLAIN, DEEP_DARK, WAKE, GRAB, REAPER, SHREDDED, THOUSAND_EYES, RED_THREAD, DIGESTED, HUSK }

    /** Every outcome. A boss's drops are in worth order, cheapest first; {@link #BUST} is any other drop or none. */
    public enum Drop {
        BUST(null, "", null, Ending.BUST, null, 0, 0),
        // ---- the Revenant (Atoned Horror's table)
        BEHEADED_HORROR(Boss.REVENANT, "Beheaded Horror", "BEHEADED_HORROR", Ending.PLAIN, DropArt.head("revtara", "BEHEADED_HORROR"), 0xAA00AA, 0.14),
        FESTERING_MAGGOT(Boss.REVENANT, "Festering Maggot", "FESTERING_MAGGOT", Ending.PLAIN, DropArt.head("revtara", "FESTERING_MAGGOT"), 0xAA00AA, 0.14),
        SMITE_VII(Boss.REVENANT, "Smite VII", "ENCHANTMENT_SMITE_7", Ending.PLAIN, DropArt.glinting("minecraft:enchanted_book"), 0xAA00AA, 0.035),
        SEVERED_HAND(Boss.REVENANT, "Severed Hand", "SEVERED_HAND", Ending.GRAB, DropArt.head("revtara", "SEVERED_HAND"), 0xFFAA00, 0.049),
        SCYTHE_BLADE(Boss.REVENANT, "Scythe Blade", "SCYTHE_BLADE", Ending.REAPER, DropArt.glinting("minecraft:diamond"), 0xFFAA00, 0.105),
        SHARD_OF_THE_SHREDDED(Boss.REVENANT, "Shard of the Shredded", "SHARD_OF_THE_SHREDDED", Ending.SHREDDED, DropArt.head("revtara", "SHARD_OF_THE_SHREDDED"), 0xFFAA00, 0.056),
        WARDEN_HEART(Boss.REVENANT, "Warden Heart", "WARDEN_HEART", Ending.DEEP_DARK, DropArt.head("revtara", "WARDEN_HEART"), 0xFFAA00, 0.014),
        MATCHA_DYE(Boss.REVENANT, "Matcha Dye", "DYE_MATCHA", Ending.WAKE, DropArt.head("revtara", "DYE_MATCHA"), 0x00AA00, 0.0004),
        // ---- the Tarantula (Primordial Broodfather's table)
        TARANTULA_TALISMAN(Boss.TARANTULA, "Tarantula Talisman", "TARANTULA_TALISMAN", Ending.PLAIN, DropArt.head("revtara", "TARANTULA_TALISMAN"), 0xAA00AA, 0.21),
        VIAL_OF_VENOM(Boss.TARANTULA, "Vial of Venom", "VIAL_OF_VENOM", Ending.PLAIN, DropArt.head("revtara", "VIAL_OF_VENOM"), 0xAA00AA, 0.14),
        FLY_SWATTER(Boss.TARANTULA, "Fly Swatter", "FLY_SWATTER", Ending.PLAIN, DropArt.glinting("hypixel_skyblock:item/slayer/spider/fly_swatter"), 0xAA00AA, 0.21),
        ENSNARED_SNAIL(Boss.TARANTULA, "Ensnared Snail", "ENSNARED_SNAIL", Ending.PLAIN, DropArt.head("revtara", "ENSNARED_SNAIL"), 0xFFAA00, 0.042),
        SHRIVELED_WASP(Boss.TARANTULA, "Shriveled Wasp", "SHRIVELED_WASP", Ending.HUSK, DropArt.head("revtara", "SHRIVELED_WASP"), 0xFFAA00, 0.14),
        DIGESTED_MOSQUITO(Boss.TARANTULA, "Digested Mosquito", "DIGESTED_MOSQUITO", Ending.DIGESTED, DropArt.model("minecraft:rotten_flesh"), 0xFFAA00, 0.071),
        PRIMORDIAL_EYE(Boss.TARANTULA, "Primordial Eye", "PRIMORDIAL_EYE", Ending.THOUSAND_EYES, DropArt.head("revtara", "PRIMORDIAL_EYE"), 0xFFAA00, 0.014),
        BRICK_RED_DYE(Boss.TARANTULA, "Brick Red Dye", "DYE_BRICK_RED", Ending.RED_THREAD, DropArt.head("revtara", "DYE_BRICK_RED"), 0xFF5555, 0.0004),
        // ---- both: whose it is, the boss says
        PARAGON(null, "Paragon Shard", "SHARD_PARAGON", Ending.PLAIN, DropArt.head("slayer", "Paragon Shard"), 0xFF55FF, 0.5);

        /** Null for a bust; null too for the Paragon Shard, which both bosses drop. */
        public final Boss boss;
        public final String title;
        /** The id its price is looked up by (bazaar first, then the lowest BIN). */
        public final String priceId;
        public final Ending ending;
        public final DropArt art;
        public final int colour;
        /** Its chance at tier V, in percent (the wiki, 2026-10-23), 0 for none. */
        public final double chance;

        Drop(Boss boss, String title, String priceId, Ending ending, DropArt art, int colour, double chance) {
            this.boss = boss;
            this.title = title;
            this.priceId = priceId;
            this.ending = ending;
            this.art = art;
            this.colour = colour;
            this.chance = chance;
        }

        public boolean win() {
            return this != BUST;
        }

        public boolean from(Boss b) {
            return this == BUST || boss == null || boss == b;
        }

        public boolean dye() {
            return this == MATCHA_DYE || this == BRICK_RED_DYE;
        }

        /** "1 in 7,143": its chance as odds, or null. */
        public String odds() {
            if (chance <= 0) return null;
            return "1 in " + String.format(java.util.Locale.ROOT, "%,d", Math.round(100.0 / chance));
        }
    }

    // ------------------------------------------------------------------ the beats

    public static final long APPEAR = 600;
    public static final long[] BEATS = {1000, 1720, 2330, 2840};
    public static final long REVEAL = 3700;
    /** A bust is over this long after the reveal. */
    public static final long BUST_LENGTH = 1150;
    public static final long FADE = 600;
    public static final long COUNT_MS = 900;

    private final Boss boss;
    private final Drop drop;
    private final long seed;
    private long price;
    private String priceSource;

    /** Each drop's own ending, or (the menu's switch off) the plain one for every win. */
    private final boolean own;

    public SlayerTimeline(Boss boss, Drop drop, long seed) {
        this(boss, drop, seed, true);
    }

    public SlayerTimeline(Boss boss, Drop drop, long seed, boolean ownEndings) {
        this.boss = boss;
        this.drop = drop == null ? Drop.BUST : drop;
        this.seed = seed;
        this.own = ownEndings;
    }

    public Boss boss() { return boss; }
    public Drop drop() { return drop; }
    public Ending ending() { return own || !drop.win() ? drop.ending : Ending.PLAIN; }
    public long seed() { return seed; }

    /** The beat last struck by {@code t} (-1 before the first). */
    public int beat(long t) {
        int b = -1;
        for (int i = 0; i < BEATS.length; i++) if (t >= BEATS[i]) b = i;
        return b;
    }

    /** 0..1 over the last stretch, from the last beat to the reveal: the same for every outcome. */
    public float tension(long t) {
        long last = BEATS[BEATS.length - 1];
        if (t < last || t >= REVEAL) return 0f;
        return clamp01((t - last) / (float) (REVEAL - last));
    }

    // ------------------------------------------------------------------ the ending's beats

    /**
     * When the drop is up in the middle. The plain ending brings it straight out; each special one plays its own
     * moment first - the mid drops a few seconds, the four big ones longer, the dyes longest.
     */
    public long arrive() {
        return REVEAL + switch (ending()) {
            case BUST, PLAIN -> 800;
            case GRAB, SHREDDED, DIGESTED, HUSK -> 2200;
            // The scythe: dropped in, drawn back, the swing, and the stone's top tipping off and landing.
            case REAPER -> 2500;
            case DEEP_DARK, THOUSAND_EYES -> 4200;
            case WAKE, RED_THREAD -> 5400;
        };
    }

    public long total() {
        if (!drop.win()) return REVEAL + BUST_LENGTH;
        long hold = switch (ending()) {
            case PLAIN -> 3000;
            case GRAB, REAPER, SHREDDED, DIGESTED, HUSK -> 3400;
            default -> 4200;
        };
        return arrive() + hold;
    }

    /** Words: the name stamped out a letter at a time from {@link #arrive}, then odds, then the price counted up. */
    public float nameAlpha(long t) { return clamp01((t - arrive()) / 200f) * fadeOut(t); }

    public int letters(long t) {
        if (t < arrive()) return 0;
        return ending() == Ending.PLAIN ? 99 : (int) ((t - arrive()) / 45) + 1;
    }

    public float lineAlpha(long t) { return clamp01((t - arrive() - 250) / 300f) * fadeOut(t); }

    public float bannerAlpha(long t) {
        if (!drop.win() || ending() == Ending.PLAIN) return 0f;
        return clamp01((t - arrive() + 150) / 250f) * fadeOut(t);
    }

    public long countStart() { return arrive() + 500; }

    /** The drop rising to its place, 0..1, from a little before {@link #arrive}. */
    public float rise(long t) {
        if (!drop.win()) return 0f;
        return ease(clamp01((t - arrive() + 450) / 600f));
    }

    public float fadeOut(long t) {
        return clamp01((total() - t) / (float) FADE);
    }

    /** The whole scene's alpha: in over {@link #APPEAR}, out over the last {@link #FADE}. */
    public float master(long t) {
        return Math.min(clamp01(t / (float) APPEAR), fadeOut(t));
    }

    public void price(long coins, String source) {
        price = coins;
        priceSource = source;
    }

    public long price() { return price; }
    public String priceSource() { return priceSource; }

    /** How hard the showcase goes, 0..1: by the drop's worth band, the dyes at the top. */
    public float intensity() {
        if (!drop.win()) return 0f;
        if (drop.dye()) return 1f;
        return switch (drop.ending) {
            case DEEP_DARK, THOUSAND_EYES -> 0.85f;
            case GRAB, REAPER, SHREDDED, DIGESTED, HUSK -> 0.6f;
            default -> drop == Drop.ENSNARED_SNAIL || drop == Drop.PARAGON ? 0.4f : 0.25f;
        };
    }

    // ------------------------------------------------------------------ the shake (a slider per animation)

    /**
     * A small kick, decaying, on each beat and a bigger one at the reveal, in GUI px before the player's own slider
     * ({@code shake} 0..1.5). Moderate at 100%: the fork's went up to 3.7% of the screen's height at the reveal.
     */
    public float[] shake(long t, int h, float shake) {
        if (shake <= 0f) return new float[]{0f, 0f};
        float amp = 0f;
        for (long b : BEATS) {
            long d = t - b;
            if (d >= 0 && d < 220) amp = Math.max(amp, 0.0045f * (1f - d / 220f));
        }
        amp = Math.max(amp, 0.0018f * tension(t));
        long r = t - REVEAL;
        float big = drop.win() ? 0.006f + 0.008f * intensity() : 0.004f;
        if (r >= 0 && r < 420) amp = Math.max(amp, big * (1f - r / 420f));
        float a = amp * h * shake;
        return new float[]{a * (float) Math.sin(t * 0.11 + seed % 7), a * (float) Math.cos(t * 0.137 + seed % 5)};
    }

    // ------------------------------------------------------------------ helpers

    public static float clamp01(float x) {
        return x < 0f ? 0f : (x > 1f ? 1f : x);
    }

    public static float ease(float x) {
        x = clamp01(x);
        return x * x * (3f - 2f * x);
    }

    public static float backOut(float x) {
        if (x <= 0f) return 0f;
        x = clamp01(x);
        float c1 = 1.70158f, c3 = c1 + 1f, y = x - 1f;
        return 1f + c3 * y * y * y + c1 * y * y;
    }

    // The motion curves (the user, 2026-10-26: "make the movements feel a little more natural"): nothing moves at an even
    // speed - a swing gathers and strikes, a thrown thing slows at the top, a dropped one lands and settles.

    /** Fast then slowing: a lift, a throw's rise. */
    public static float easeOut(float x) {
        x = clamp01(x);
        float y = 1f - x;
        return 1f - y * y * y;
    }

    /** Slow then fast: a strike gathering speed, a fall. */
    public static float easeIn(float x) {
        x = clamp01(x);
        return x * x * x;
    }

    /** A spring's settle: 1 at x = 0, swinging through 0 and dying away over x 0..1 - a wobble, a recoil. */
    public static float settle(float x, float swings) {
        if (x <= 0f) return 1f;
        if (x >= 1f) return 0f;
        return (float) (Math.cos(x * swings * Math.PI) * Math.pow(1f - x, 2.2));
    }

    /** A dropped thing's height over x 0..1: falls (easeIn), then bounces twice, smaller each time, and lies still. */
    public static float bounce(float x) {
        x = clamp01(x);
        if (x < 0.55f) return 1f - easeIn(x / 0.55f);
        if (x < 0.8f) {
            float k = (x - 0.55f) / 0.25f;
            return 0.16f * 4f * k * (1f - k);
        }
        float k = (x - 0.8f) / 0.2f;
        return 0.05f * 4f * k * (1f - k);
    }
}
