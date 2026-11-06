package com.butcher.anim;

/**
 * The Crystal Nucleus's cutscene, "The Vault" (designed with the user 2026-10-27): the Nucleus Vault's door rises into
 * frame set in the Hollows' rock; the five crystals fly in from their zones' sides one at a time, each locking into its
 * socket - a bolt pulls back, the dial turns a notch faster, the veins of its colour light; then the dial spins up and
 * the light leaking from the door's seam cycles the five colours, the same on every run, and lands on the haul's at
 * {@link #REVEAL}. Pure: the build-up depends on nothing - every run is the same until the reveal.
 *
 * After it, the ending (2026-10-27, first version): a plain haul's door swings open on its hinge and the bundle tumbles
 * out onto a pile on the ledge, the best item lifting off the top with its name and price; a win (1.5M and up) floods
 * the seam with the drop's colour, blows the door wide and floats the drop out, the rest of the haul tipping out behind
 * it. Divan's Alloy plays the win's ending until its forge is built.
 */
public final class NucleusTimeline implements NucleusRun {

    /** The five crystals, in the order they arrive, with the side of the screen each comes from (its zone's side). */
    public enum Crystal {
        JADE(0x63E25B, 18, "jade"),        // the Mithril Deposits, north-east
        AMBER(0xF09A2E, 234, "amber"),     // the Goblin Holdout, south-west
        TOPAZ(0xF7E04A, 90, "topaz"),      // the Magma Fields, below: it comes up round the door to the top socket
        SAPPHIRE(0x5CB8F2, 306, "sapphire"), // the Precursor Remnants, south-east
        AMETHYST(0xB565E8, 162, "amethyst"); // the Jungle, north-west

        public final int colour;
        /** Its socket's angle on the door (degrees; 90 is the top) - the sockets sit on a pentagon. */
        public final int socket;
        /** The gem kind its veins are in the model. */
        public final String gem;

        Crystal(int colour, int socket, String gem) {
            this.colour = colour;
            this.socket = socket;
            this.gem = gem;
        }
    }

    /** How the haul lands: a plain haul (the best drop under 1.5M), a win, or Divan's Alloy. */
    /** JADE and CLAW: the Jade Dye's and the Quick Claw's own endings - less grand than the Alloy's (the user). */
    public enum Tier { PLAIN, WIN, ALLOY, JADE, CLAW }

    // ------------------------------------------------------------------ the build-up (ms)
    /** The door rises into frame. */
    public static final long RISE = 0, RISEN = 760;
    /** When each crystal sets off, and how long its flight takes; it locks at the end of the flight. */
    public static final long FIRST = 880, EVERY = 560, FLIGHT = 430;
    /** A bolt pulls back over this long after its crystal locks. */
    public static final long BOLT_MS = 280;
    /** On a lock, the power runs out from the socket to its colour's veins over this long; they light as it gets there. */
    public static final long RUN_FROM = 40, RUN_MS = 300;
    /** After the fifth lock the dial spins up; the seam's light cycles, then hangs. */
    public static final long SPIN_FROM = lock(4) + 120, CYCLE = lock(4) + 260, REVEAL = lock(4) + 1900;

    public static long launch(int k) {
        return FIRST + EVERY * k;
    }

    public static long lock(int k) {
        return launch(k) + FLIGHT;
    }

    /** The seam's colour cycle: the five crystals' colours round twice, the steps shortening, then the hang. */
    static final long[] STEPS = steps();

    private static long[] steps() {
        long[] s = new long[10];
        long t = CYCLE;
        float step = 210f;
        for (int i = 0; i < s.length; i++) {
            s[i] = t;
            t += Math.round(step);
            step *= 0.88f;
        }
        return s;
    }

    /** How many colours the seam cycles through, and when the {@code i}th starts. */
    public static int stepCount() {
        return STEPS.length;
    }

    public static long step(int i) {
        return STEPS[i];
    }

    private final Tier tier;
    private final int tellColour;
    private final NucleusHaul haul;
    /** The items that land on the pile, in the order they come out (a plain haul's best last, on top). */
    private final java.util.List<NucleusHaul.Item> pile;
    /** The showcase: the best item - off the top of the pile, or a win's drop out of the door. */
    private final NucleusHaul.Item show;
    private final long open, spill, showAt, textAt, total, forgeAt;
    /**
     * The spotlight for the three special drops (the user, 2026-10-28: the Alloy, the Dye and the Claw "should be the main
     * focus ... dim the background and add more special effects, like the locket and frostbitten dye"): from here the
     * scene behind stops and dims and the drop takes the middle of the screen; -1 for every other haul.
     */
    private final long spotAt;
    /** From the spotlight coming in: when its words start, and how long it holds. */
    public static final long SPOT_TEXT = 650, SPOT_LEN = 5800, SPOT_LEN_ALLOY = 6400;
    private long price = -1;
    private String priceSource;

    public NucleusTimeline(NucleusHaul haul) {
        this(haul, true, false);
    }

    /**
     * {@code ownEndings} off (the menu): the Alloy, the Jade Dye and the Quick Claw get the win's ending. {@code plain}:
     * a win under the price floor ("Only play drops worth at least") plays as a plain haul, its drop on top of the pile.
     */
    public NucleusTimeline(NucleusHaul haul, boolean ownEndings, boolean plain) {
        this.haul = haul;
        Tier own = haul.tier();
        this.tier = plain ? Tier.PLAIN : !ownEndings && (own == Tier.ALLOY || own == Tier.JADE || own == Tier.CLAW) ? Tier.WIN : own;
        this.show = haul.best();
        this.tellColour = tell(tier, show);
        java.util.List<NucleusHaul.Item> p = haul.pile(PILE_MAX + (tier == Tier.PLAIN ? 0 : 1));
        if (tier != Tier.PLAIN && !p.isEmpty()) p = new java.util.ArrayList<>(p.subList(0, p.size() - 1));
        this.pile = java.util.List.copyOf(p);
        long forge = -1, spot = -1;
        if (tier == Tier.ALLOY) {
            // Divan's Alloy: the door is blown as a win's is, then the camera dives through the doorway into the forge,
            // its own whole cutscene (ForgeTimeline) - the grandest the mod plays.
            open = REVEAL + FLOOD_MS;
            forge = open + DIVE_MS;
            showAt = forge + ForgeTimeline.real(ForgeTimeline.LIFT_FROM);
            spot = showAt + 600;
            textAt = spot + SPOT_TEXT;
            spill = Long.MAX_VALUE / 4;
            total = spot + SPOT_LEN_ALLOY;
        } else if (tier == Tier.JADE || tier == Tier.CLAW) {
            // The door stays shut: the dye bleeds out of the seam and runs down it all, then it swings open, dripping;
            // or the claw rakes it three times from inside, and it falls apart.
            open = REVEAL + (tier == Tier.JADE ? JADE_OPEN : CLAW_BREAK);
            showAt = open + (tier == Tier.JADE ? 640 : 420);
            spot = showAt + 300;
            textAt = spot + SPOT_TEXT;
            spill = open + (tier == Tier.JADE ? 1200 : 900);
            total = spot + SPOT_LEN;
        } else if (tier == Tier.PLAIN) {
            open = REVEAL + 300;
            spill = open + 380;
            long lastLand = spill + Math.max(0, pile.size() - 1) * SPILL_EVERY + ITEM_FLIGHT;
            showAt = lastLand + 160;
            textAt = showAt + 380;
            total = textAt + 2700;
        } else {
            open = REVEAL + FLOOD_MS;
            showAt = open + 120;
            textAt = showAt + 760;
            spill = open + 650;
            long lastLand = spill + Math.max(0, pile.size() - 1) * SPILL_EVERY + ITEM_FLIGHT;
            total = Math.max(textAt + 3400, lastLand + 1600);
        }
        forgeAt = forge;
        spotAt = spot;
    }

    /** When the spotlight comes in (-1: this haul has none). */
    public long spotAt() {
        return spotAt;
    }

    /** How long the camera dives through the doorway before the cut into the forge. */
    public static final long DIVE_MS = 1050;

    /** When Divan's Alloy's forge starts (-1: this run has none). */
    public long forgeAt() {
        return forgeAt;
    }

    /** The dive into the doorway, 0..1 (the Alloy only). */
    public float dive(long t) {
        if (forgeAt < 0) return 0f;
        float u = clamp01((t - (open + 200)) / (float) (DIVE_MS - 200));
        return u * u * u;
    }

    public Tier tier() {
        return tier;
    }

    public NucleusHaul haul() {
        return haul;
    }

    public NucleusHaul.Item show() {
        return show;
    }

    public java.util.List<NucleusHaul.Item> pile() {
        return pile;
    }

    /** Lines of the haul not on the pile. */
    public int more() {
        return haul.items.size() - pile.size() - (show == null ? 0 : 1);
    }

    /** The showcase's price, once the screen has looked it up (-1 until then), and where from. */
    public void price(long coins, String source) {
        price = coins;
        priceSource = source;
    }

    public long price() {
        return price;
    }

    public String priceSource() {
        return priceSource;
    }

    /** The seam's last colour: pale stone for a plain haul, the drop's own for a win. */
    static int tell(Tier tier, NucleusHaul.Item best) {
        if (tier == Tier.PLAIN || best == null) return 0xD8D2C4;
        String id = best.id();
        if (id.equals("DIVAN_ALLOY")) return 0xF5D547;
        if (id.equals("DYE_JADE")) return JADE_COLOUR;
        if (id.equals("PET_ITEM_QUICK_CLAW")) return CLAW_COLOUR;
        if (id.equals("JADERALD")) return 0x4FD97A;
        if (id.equals("GEMSTONE_MIXTURE")) return 0xC7A6FF;
        if (id.startsWith("FLAWLESS_")) {
            String gem = id.substring(9, id.length() - 4);
            for (Crystal c : Crystal.values()) if (c.name().equals(gem)) return c.colour;
            return gem.equals("RUBY") ? 0xE8475A : 0xE05CC8;
        }
        return 0x55E27A;
    }

    // ------------------------------------------------------------------ the Jade Dye and the Quick Claw
    public static final int JADE_COLOUR = 0x00A86B, CLAW_COLOUR = 0xFFB43C;
    /**
     * The Jade Dye (rebuilt 0.3.33 - the user: "no build up, it just happens", make it far grander): the seam lands on
     * jade and the door strains - five heartbeats, quickening and harder each, cracking its face further and squirting
     * dye out of the seam - then a hush, and it bursts: the dye blasts out all round, runs down everything for RUN_MS and
     * pools on the floor; the door swings open at JADE_OPEN on a fountain of it that carries the dye up. From REVEAL.
     */
    public static final long[] JADE_BEATS = {200, 640, 990, 1250, 1440};
    public static final long JADE_HUSH = 1530, JADE_BURST = 1680, JADE_RUN_MS = 1700, JADE_OPEN = 3650;
    /** The Quick Claw: three swipes from inside (each SLASH_MS to cross the door), the door falling apart at CLAW_BREAK. */
    public static final long[] SLASH = {320, 820, 1340};
    public static final long SLASH_MS = 110, CLAW_BREAK = 1900, FALL_MS = 620;

    /** How far the Jade Dye's paint has run, 0..1 (eased: fast out of the seam, slowing as it spreads). */
    public float paint(long t) {
        if (tier != Tier.JADE) return 0f;
        float u = clamp01((t - REVEAL - JADE_BURST - 60) / (float) JADE_RUN_MS);
        return 1f - (1f - u) * (1f - u);
    }

    /** How hard the dye presses on the door before it bursts, 0..1 (0 after). */
    public float pressure(long t) {
        long d = t - REVEAL;
        if (tier != Tier.JADE || d < 0 || d >= JADE_BURST) return 0f;
        float u = d / (float) JADE_BURST;
        return u * u;
    }

    /** The heartbeat's thump now, 0..1 - each beat harder than the last. */
    public float jadeBeat(long t) {
        if (tier != Tier.JADE) return 0f;
        float k = 0f;
        for (int i = 0; i < JADE_BEATS.length; i++) {
            long d = t - REVEAL - JADE_BEATS[i];
            if (d >= 0 && d < 600) k = Math.max(k, (0.55f + 0.45f * i / (JADE_BEATS.length - 1f)) * (float) Math.exp(-d / 110.0));
        }
        return k;
    }

    /** How far the cracks across the door have spread, 0..1: a step out on each beat. */
    public float cracks(long t) {
        if (tier != Tier.JADE) return 0f;
        float k = 0f;
        for (int i = 0; i < JADE_BEATS.length; i++) {
            long d = t - REVEAL - JADE_BEATS[i];
            if (d >= 0) k = i / (float) JADE_BEATS.length + clamp01(d / 120f) / JADE_BEATS.length;
        }
        return k;
    }

    /** The hush before the burst: 0, rising to 1 (all held) just before it, 0 again after. */
    public float hush(long t) {
        long d = t - REVEAL;
        if (tier != Tier.JADE || d < JADE_HUSH || d >= JADE_BURST) return 0f;
        return clamp01((d - JADE_HUSH) / 60f);
    }

    /** ms since the burst (negative before it). */
    public long sinceBurst(long t) {
        return t - REVEAL - JADE_BURST;
    }

    /** Swipe {@code k} of the claw: 0..1 across the door (-1 before; 1 once done). */
    public float slash(int k, long t) {
        if (tier != Tier.CLAW) return -1f;
        long d = t - REVEAL - SLASH[k];
        if (d < 0) return -1f;
        return clamp01(d / (float) SLASH_MS);
    }

    /** The cut door's fall, 0..1 for a piece starting {@code delay} ms after the break. */
    public float fall(long t, long delay) {
        if (tier != Tier.CLAW) return 0f;
        return clamp01((t - open - delay) / (float) FALL_MS);
    }

    // ------------------------------------------------------------------ the ending (ms)
    /** At most this many items on the pile; the rest are "+N more" (the user: keep the plain ending about 5 s). */
    public static final int PILE_MAX = 12;
    /** One item out of the door every this long; each flies this long to the pile. */
    public static final long SPILL_EVERY = 60, ITEM_FLIGHT = 560;
    /** A win's seam floods this long before the door blows. */
    public static final long FLOOD_MS = 650;
    /** How long the plain door takes to swing open; how far it goes (radians, towards the camera). */
    public static final long SWING_MS = 860, BLOW_MS = 260;
    public static final float OPEN_ANGLE = 1.95f, BLOWN_ANGLE = 2.2f;

    /** When the door starts to move. */
    public long open() {
        return open;
    }

    /** When item {@code k} of the pile leaves the door. */
    public long spillAt(int k) {
        return spill + k * SPILL_EVERY;
    }

    public long showAt() {
        return showAt;
    }

    public long textAt() {
        return textAt;
    }

    /** The door's swing (radians about its hinge, towards the camera): a heavy door pulled open, or blown wide. */
    public float door(long t) {
        long d = t - open;
        if (d < 0 || tier == Tier.CLAW) return 0f;
        if (tier == Tier.PLAIN || tier == Tier.JADE) {
            // A small jerk as it unlatches, then the weight of it, slowing into its stop and knocking back off it.
            if (d < 120) return 0.035f * (float) Math.sin(Math.PI * d / 120.0);
            float u = clamp01((d - 120) / (float) (SWING_MS - 120));
            float a = OPEN_ANGLE * (u * u * (3f - 2f * u) * 0.35f + (1f - (1f - u) * (1f - u) * (1f - u)) * 0.65f);
            if (d > SWING_MS) a -= 0.07f * (float) Math.exp(-(d - SWING_MS) / 160.0) * (float) Math.abs(Math.sin((d - SWING_MS) / 55.0));
            return a;
        }
        float u = clamp01(d / (float) BLOW_MS);
        float a = BLOWN_ANGLE * (1f - (1f - u) * (1f - u));
        if (d > BLOW_MS) a -= 0.14f * (float) Math.exp(-(d - BLOW_MS) / 180.0) * (float) Math.abs(Math.sin((d - BLOW_MS) / 60.0));
        return a;
    }

    /** A win's flood: 0 to 1 over the moment before the door blows, then held. */
    public float flood(long t) {
        // The Jade Dye and the Quick Claw have their own build to the door (the paint, the claw): no flood of rays.
        if (tier == Tier.PLAIN || tier == Tier.JADE || tier == Tier.CLAW) return 0f;
        return clamp01((t - REVEAL) / (float) FLOOD_MS);
    }

    /** Item {@code k}'s flight to the pile, 0..1 (1 landed), -1 before it leaves. */
    public float itemFlight(int k, long t) {
        long d = t - spillAt(k);
        if (d < 0) return -1f;
        return clamp01(d / (float) ITEM_FLIGHT);
    }

    /** The showcase rising to its place, 0..1 - off the top of the pile, or out of the door. */
    public float showRise(long t) {
        long d = t - showAt;
        if (d < 0) return -1f;
        return clamp01(d / (tier == Tier.PLAIN ? 520f : 900f));
    }

    /** The words fading in (name, then price, then the rest), 0..1 each by index. */
    public float words(long t, int line) {
        return clamp01((t - textAt - line * 180L) / 260f);
    }

    /** How the door stands: 0 below the frame, 1 risen - with a settle. */
    public static float rise(long t) {
        float k = clamp01((t - RISE) / (float) (RISEN - RISE));
        float y = 1f - (1f - k) * (1f - k) * (1f - k);
        return y + 0.035f * (float) (Math.sin(k * Math.PI * 2.6) * (1f - k));
    }

    /** Where crystal {@code k} is along its flight, 0..1 (1 locked in), easing in to the socket. */
    public static float flight(int k, long t) {
        float u = clamp01((t - launch(k)) / (float) FLIGHT);
        return u * u * (3f - 2f * u);
    }

    public static boolean locked(int k, long t) {
        return t >= lock(k);
    }

    /** How far bolt {@code k} has pulled back, 0..1 - fast, then a little bounce at the end of its travel. */
    public static float bolt(int k, long t) {
        long d = t - lock(k) - 60;
        if (d <= 0) return 0f;
        float u = clamp01(d / (float) BOLT_MS);
        float e = u * u * (3f - 2f * u);
        return e - 0.06f * (float) Math.sin(Math.PI * clamp01((d - BOLT_MS) / 160f));
    }

    /**
     * The dial's angle (radians): a notch with each lock, then spinning up after the fifth - like a safe being cracked -
     * and slowing to a stop at the reveal.
     */
    public static float dial(long t) {
        float a = 0f;
        for (int k = 0; k < 5; k++) {
            float u = clamp01((t - lock(k)) / 160f);
            a += (float) (Math.PI / 6) * u * u * (3f - 2f * u) * (k % 2 == 0 ? 1f : -1f);
        }
        if (t > SPIN_FROM) {
            float s = Math.min(t, REVEAL) - SPIN_FROM, span = REVEAL - SPIN_FROM;
            // speed rises to 16 rad/s and falls away over the last third: the integral of a smooth bump
            float u = clamp01(s / span);
            a += 16f * span / 1000f * (u * u * u * (1f - 0.75f * u));
        }
        return a;
    }

    /** How far the power from crystal {@code k}'s lock has run towards its veins, 0..1 (eased: fast out of the socket). */
    public static float run(int k, long t) {
        float u = clamp01((t - lock(k) - RUN_FROM) / (float) RUN_MS);
        return 1f - (1f - u) * (1f - u);
    }

    /** When crystal {@code k}'s veins are reached. */
    public static long lit(int k) {
        return lock(k) + RUN_FROM + RUN_MS;
    }

    /** How strongly the veins of crystal {@code k}'s colour glow: on as the power reaches them, a flash, then held. */
    public static float vein(int k, long t) {
        long d = t - lit(k) + 40;
        if (d < 0) return 0f;
        float on = clamp01(d / 120f);
        return on * (0.72f + 0.28f * (float) Math.exp(-d / 260.0));
    }

    /** A glint where crystal {@code k} will come from, 0..1 over the moment before it sets off. */
    public static float charge(int k, long t) {
        long d = launch(k) - t;
        if (d < -60 || d > 300) return 0f;
        return d < 0 ? 1f + d / 60f : 1f - d / 300f;
    }

    /** The camera's jolt in on each lock (and the door's landing), decaying. */
    public static float kick(long t) {
        float k = 0f;
        long r = t - RISEN;
        if (r >= 0) k += 0.02f * (float) Math.exp(-r / 110.0);
        for (int i = 0; i < 5; i++) {
            long d = t - lock(i);
            if (d >= 0) k += 0.038f * (float) Math.exp(-d / 120.0);
        }
        return k;
    }

    /** The hang after the cycle: 0 until its last colour, rising to 1 at the reveal - everything tightening. */
    public static float hang(long t) {
        long from = STEPS[STEPS.length - 1];
        return clamp01((t - from) / (float) (REVEAL - from));
    }

    /** A pulse on each of the cycle's colours: 1 as it changes, dying away. */
    public static float pulse(long t) {
        for (int i = STEPS.length - 1; i >= 0; i--) {
            if (t >= STEPS[i]) return (float) Math.exp(-(t - STEPS[i]) / 140.0);
        }
        return 0f;
    }

    /** Which crystal's colour the cycle is on (-1 before it starts). */
    public static int cycleStep(long t) {
        if (t >= REVEAL) return -1;
        int step = -1;
        for (int i = 0; i < STEPS.length; i++) if (t >= STEPS[i]) step = i;
        return step < 0 ? -1 : step % Crystal.values().length;
    }

    /** The seam's light: 0 dark, rising after the fifth lock. */
    public static float seam(long t) {
        if (t < lock(4)) return 0f;
        return clamp01((t - lock(4)) / 700f) * (t >= REVEAL ? 1.2f : 1f);
    }

    /** The seam's colour now: the five crystals' colours in turn (identical every run), then the truth at the reveal. */
    public int seamColour(long t) {
        if (t >= REVEAL) return tellColour;
        Crystal[] c = Crystal.values();
        int step = -1;
        for (int i = 0; i < STEPS.length; i++) if (t >= STEPS[i]) step = i;
        if (step < 0) return c[0].colour;
        return c[step % c.length].colour;
    }

    /** The camera's push in through the build-up (1 at the start), creeping closer through the hang. */
    public static float push(long t) {
        float u = clamp01(t / (float) REVEAL);
        float h = hang(t);
        return 1f + 0.08f * u * u * (3f - 2f * u) + 0.035f * h * h;
    }

    /** The push at the reference camera the still layers are baked at: the largest the build-up reaches. */
    public static float pushMax() {
        return push(REVEAL) + 0.06f;
    }

    @Override
    public long shortTo() {
        return REVEAL - 450;
    }

    public long total() {
        return total;
    }

    public float master(long t) {
        return clamp01(t / 150f) * clamp01((total() - t) / 400f);
    }

    static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
