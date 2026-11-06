package com.butcher.anim;

import com.butcher.anim.NucleusTimeline.Crystal;
import com.butcher.anim.NucleusTimeline.Tier;

/**
 * "The Gauntlet", the Crystal Nucleus's second style (the user's design, 2026-10-28; chosen in the menu beside the Vault).
 * Pure: every beat is a function of time, the haul and the order the crystals were placed in.
 *
 * <b>The build-up, the same every run:</b> a blocky gold gauntlet rises in, seen from its thumb's side; the first
 * crystal flies into the thumb's socket; it turns to show its back; the other four go into the index, middle, ring and
 * pinky in turn. Then the big socket in the middle of the hand cycles through the Nucleus's drops - the same fixed
 * sequence every run, each step shorter, like the Thaw's last strike - and lands on the haul's best ({@link #REVEAL}).
 * Nothing before it depends on the haul.
 *
 * <b>The endings</b> (the user's picks):
 * <ul>
 *   <li>{@link Ending#SNAP} - Divan's Alloy and the Jade Dye: the hand takes the snap's pose and charges for a long
 *       slow beat; a flashbang, ringing ears. The Alloy's (the user: the snap, bigger): each stone fires a beam into the
 *       centre in turn, everything holds its breath, the snap cracks the screen, the world crumbles to dust, then the
 *       gauntlet. The Jade Dye's is the <i>Jade Storm</i> ({@link #storm()}): jade lightning arcs between the stones,
 *       the snap sends a jade shockwave out that turns the world to jade crystal, which then shatters outward from the
 *       fist, then the gauntlet goes. Only the drop is left.</li>
 *   <li>{@link Ending#FIST} - every other drop worth 1.5M and up (the Quick Claw too): the hand clenches into a fist, the
 *       centre stone flares in the drop's colour, it opens, the crystals crack and fall out, the drop rises.</li>
 *   <li>{@link Ending#FALL} - a plain haul: the crystals crack and fall out, the drop rises.</li>
 * </ul>
 */
public final class GauntletTimeline implements NucleusRun {

    public enum Ending { FALL, FIST, SNAP }

    // ------------------------------------------------------------------ the build-up (ms), the same every run
    /** It rises into frame by RISEN. */
    public static final long RISEN = 700;
    /** The thumb's crystal sets off; every flight takes FLIGHT. */
    public static final long THUMB_LAUNCH = 820, FLIGHT = 440;
    /** It turns from the thumb's side to its back. */
    public static final long TURN_FROM = 1400, TURN_TO = 2100;
    /** The four fingers' crystals: index, middle, ring, pinky, one every FINGER_EVERY. */
    public static final long FINGERS_FROM = 2150, FINGER_EVERY = 400;
    /** The yaw it starts at, showing the thumb's side (radians: -pi/2 would be square on to it). */
    public static final float THUMB_YAW = -1.0f;
    /**
     * Where it rests once turned, and how it is held the whole time (the user, 2026-10-28: "a little tilted and turned,
     * not just upright"): a quarter turn short of square on, so the thumb's side and its stone still show, leaning over
     * ({@code LEAN}, about the screen's axis) and tipped back a little ({@code TIP}) so the knuckles' stones face up.
     */
    public static final float REST_YAW = -0.42f, LEAN = 0.17f, TIP = -0.14f;

    public static long launch(int k) {
        return k == 0 ? THUMB_LAUNCH : FINGERS_FROM + (k - 1) * FINGER_EVERY;
    }

    public static long lock(int k) {
        return launch(k) + FLIGHT;
    }

    /** What cycles in the centre socket, in this order every run: the Nucleus's drops, cheap and dear mixed. */
    public static final String[] CYCLE = {"Jaderald", "Flawless Ruby Gemstone", "Gemstone Mixture", "Divan Fragment",
            "Quick Claw", "Flawless Amethyst Gemstone", "Precious Pearl", "Jade Dye", "Flawless Sapphire Gemstone",
            "Divan's Alloy", "Treasurite", "Flawless Jade Gemstone"};
    public static final long CYCLE_FROM = lock(4) + 300;
    private static final long[] STEPS = steps();
    /** The landing: the centre shows the haul's best. */
    public static final long REVEAL = STEPS[STEPS.length - 1] + 300;

    private static long[] steps() {
        long[] s = new long[CYCLE.length + 1];
        long t = CYCLE_FROM;
        float step = 330f;
        for (int i = 0; i < s.length; i++) {
            s[i] = t;
            t += Math.round(step);
            step *= 0.86f;
        }
        return s;
    }

    /** When cycle step {@code i} starts. */
    public static long step(int i) {
        return STEPS[i];
    }

    /** Which cycle item the centre shows (an index into {@link #CYCLE}), -1 before the cycle or once it has landed. */
    public static int cycleIndex(long t) {
        if (t < CYCLE_FROM || t >= REVEAL) return -1;
        int i = 0;
        while (i + 1 < CYCLE.length && t >= STEPS[i + 1]) i++;
        return i;
    }

    // ------------------------------------------------------------------ the run

    private final NucleusHaul haul;
    private final NucleusHaul.Item show;
    private final Tier tier;
    private final Ending ending;
    /** The crystal in each socket: 0 the thumb, then index, middle, ring, pinky. */
    private final Crystal[] order;
    private final boolean storm;
    private long price = -1;
    private String priceSource;

    /**
     * {@code placed}: the crystals in the order the player put them in (null or short: the enum's order), the first to
     * the thumb. {@code ownEndings} off: the Alloy and the Dye get the fist. {@code plain}: a win under the price floor.
     */
    public GauntletTimeline(NucleusHaul haul, Crystal[] placed, boolean ownEndings, boolean plain) {
        this.haul = haul;
        this.show = haul.best();
        Tier own = haul.tier();
        this.tier = plain ? Tier.PLAIN : own;
        this.ending = tier == Tier.PLAIN ? Ending.FALL
                : ownEndings && (own == Tier.ALLOY || own == Tier.JADE) ? Ending.SNAP : Ending.FIST;
        this.storm = ending == Ending.SNAP && own == Tier.JADE;
        Crystal[] o = Crystal.values().clone();
        if (placed != null && placed.length == 5) {
            boolean all = true;
            for (Crystal c : Crystal.values()) all &= java.util.Arrays.asList(placed).contains(c);
            if (all) o = placed.clone();
        }
        this.order = o;
    }

    public Ending ending() {
        return ending;
    }

    public Crystal crystal(int k) {
        return order[k];
    }

    @Override
    public Tier tier() {
        return tier;
    }

    @Override
    public NucleusHaul.Item show() {
        return show;
    }

    @Override
    public int more() {
        return Math.max(0, haul.items.size() - 1);
    }

    @Override
    public long price() {
        return price;
    }

    @Override
    public void price(long coins, String source) {
        price = coins;
        priceSource = source;
    }

    public String priceSource() {
        return priceSource;
    }

    @Override
    public long shortTo() {
        return REVEAL - 450;
    }

    // ------------------------------------------------------------------ the endings (ms after REVEAL)
    /** FALL: the crystals crack out from CRACK, one every CRACK_EVERY; the drop rises from POP. */
    static final long FALL_CRACK = 250, FALL_POP = 700;
    /** FIST: clench, flare, open, crack, rise. */
    static final long FIST_CLENCH = 120, FIST_SHUT = 470, FIST_OPEN = 1600, FIST_OPENED = 1950, FIST_CRACK = 2050, FIST_POP = 2500;
    /** SNAP: the pose, the long charge, the snap; the flash. */
    public static final long SNAP_POSE = 150, SNAP_POSED = 750, SNAP_AT = 2350, FLASH_HOLD = 220, FLASH_OUT = 1700;
    /** The Alloy's charge: stone k fires its beam into the centre at BEAM_FROM + k * BEAM_EVERY; the last HUSH ms hold still. */
    public static final long BEAM_FROM = 950, BEAM_EVERY = 240, HUSH = 260;
    /** After the snap (ms): the Alloy's world dust, the gauntlet's, the drop. */
    public static final long WORLD_FROM = 550, WORLD_TO = 3150, DUST_FROM = 2550, DUST_TO = 4650, SNAP_POP = 4650;
    /** After the snap (ms), the Jade Storm: the shockwave out, the crystal world holding, its shattering, the gauntlet's
     *  dust, the drop. */
    public static final long SHOCK_MS = 700, SHATTER_FROM = 1700, SHATTER_TO = 3700, STORM_DUST_FROM = 3300, STORM_DUST_TO = 5300,
            STORM_POP = 5200;
    public static final long CRACK_EVERY = 90, RISE_MS = 700;

    /** When the drop starts moving to the showcase. */
    public long popAt() {
        return REVEAL + switch (ending) {
            case FALL -> FALL_POP;
            case FIST -> FIST_POP;
            case SNAP -> SNAP_AT + (storm ? STORM_POP : SNAP_POP);
        };
    }

    /** The Jade Dye's snap: the Jade Storm. */
    public boolean storm() {
        return storm;
    }

    /** The Alloy's beam from stone {@code k} into the centre: 0 before it fires, 1 lit; gone at the snap. */
    public float beam(int k, long t) {
        if (ending != Ending.SNAP || storm) return 0f;
        long d = t - REVEAL - BEAM_FROM - k * BEAM_EVERY;
        if (d < 0 || t >= snapAt()) return 0f;
        return clamp01(d / 120f);
    }

    /** The last moment before the snap, everything holding its breath: 0..1. */
    public float hush(long t) {
        if (ending != Ending.SNAP) return 0f;
        long d = t - (snapAt() - HUSH);
        if (d < 0 || t >= snapAt()) return 0f;
        return clamp01(d / (float) HUSH);
    }

    /** The camera's punch at the snap: 1 at it, settling away. */
    public float punch(long t) {
        long d = t - snapAt();
        if (d < 0) return 0f;
        return (float) Math.exp(-d / 260.0);
    }

    /** The Jade Storm's shockwave: how far out it has gone, 0..1 of the screen's diagonal (-1 before the snap). */
    public float shock(long t) {
        if (!storm) return -1f;
        long d = t - snapAt();
        if (d < 0) return -1f;
        float u = clamp01(d / (float) SHOCK_MS);
        return 1f - (1f - u) * (1f - u);
    }

    @Override
    public long textAt() {
        return popAt() + RISE_MS - 100;
    }

    @Override
    public long total() {
        return textAt() + (ending == Ending.SNAP ? 3800 : ending == Ending.FIST ? 3400 : 2800);
    }

    /** When stone {@code k} cracks out of its socket (never for the snap: it goes to dust with the gauntlet). */
    public long crackAt(int k) {
        if (ending == Ending.SNAP) return Long.MAX_VALUE;
        return REVEAL + (ending == Ending.FIST ? FIST_CRACK : FALL_CRACK) + k * CRACK_EVERY;
    }

    /** The snap's moment (Long.MAX_VALUE for the others). */
    public long snapAt() {
        return ending == Ending.SNAP ? REVEAL + SNAP_AT : Long.MAX_VALUE;
    }

    /** The flashbang: 1 white for FLASH_HOLD from the snap, fading out over FLASH_OUT. */
    public float flash(long t) {
        long d = t - snapAt();
        if (d < 0) return 0f;
        if (d < FLASH_HOLD) return 1f;
        float u = clamp01((d - FLASH_HOLD) / (float) FLASH_OUT);
        return (1f - u) * (1f - u);
    }

    /**
     * The world crumbling to dust after the snap: 0 at its start, 1 when the last of it goes, and on past 1 while the
     * last motes drift off (-1 before the snap; the frozen world shows from it).
     */
    public float worldDust(long t) {
        long d = t - snapAt();
        if (d < 0) return -1f;
        if (storm) return Math.max(0f, (d - SHATTER_FROM) / (float) (SHATTER_TO - SHATTER_FROM));
        return Math.max(0f, (d - WORLD_FROM) / (float) (WORLD_TO - WORLD_FROM));
    }

    /** The gauntlet crumbling to dust, fingertips first: as {@link #worldDust}, past 1 while its motes drift (-1 before). */
    public float gauntletDust(long t) {
        if (ending != Ending.SNAP) return -1f;
        long d = t - snapAt() - (storm ? STORM_DUST_FROM : DUST_FROM);
        if (d < 0) return -1f;
        return d / (float) (storm ? STORM_DUST_TO - STORM_DUST_FROM : DUST_TO - DUST_FROM);
    }

    /** The drop moving from the centre socket to the showcase, 0..1 (-1 before). */
    public float rise(long t) {
        long d = t - popAt();
        if (d < 0) return -1f;
        return clamp01(d / (float) RISE_MS);
    }

    /** The gauntlet sinking out of frame as the drop rises (the fist and the fall; the snap's turns to dust). */
    public float sink(long t) {
        if (ending == Ending.SNAP) return 0f;
        float u = clamp01((t - popAt() - 150) / 900f);
        return u * u;
    }

    /** How far a stone is from its launch to its socket, 0..1 (-1 before it sets off). */
    public static float flight(int k, long t) {
        long d = t - launch(k);
        if (d < 0) return -1f;
        float u = clamp01(d / (float) FLIGHT);
        return 1f - (1f - u) * (1f - u) * (1f - u);
    }

    /** The gauntlet's turn about its upright: the thumb's side, then round towards its back with a small overshoot, to
     *  REST_YAW; the snap turns it a touch further towards the thumb. */
    public float yaw(long t) {
        float y;
        if (t < TURN_FROM) y = THUMB_YAW;
        else if (t < TURN_TO) y = THUMB_YAW + (REST_YAW - THUMB_YAW) * backOut((t - TURN_FROM) / (float) (TURN_TO - TURN_FROM));
        else y = REST_YAW;
        // a slow sway once it faces the camera, stilled for the cycle's last steps; held dead still from the hush on
        long st = ending == Ending.SNAP && t >= snapAt() - HUSH ? snapAt() - HUSH : t;
        float sway = st < TURN_TO ? 0f : 0.05f * (float) Math.sin((st - TURN_TO) / 900.0) * (1f - 0.7f * hang(st));
        if (ending == Ending.SNAP) {
            long d = t - REVEAL - SNAP_POSE;
            if (d > 0) sway += -0.08f * ease(clamp01(d / (float) (SNAP_POSED - SNAP_POSE)));
        }
        return y + sway;
    }

    /** The cycle's last stretch, 0..1: everything holds its breath. */
    public static float hang(long t) {
        return clamp01((t - step(CYCLE.length - 3)) / (float) (REVEAL - step(CYCLE.length - 3)));
    }

    /** How far each finger is curled, 0 straight to 1 a fist: {thumb, index, middle, ring, pinky}. */
    public float[] curl(long t) {
        float[] c = new float[5];
        long d = t - REVEAL;
        if (d < 0) return c;
        switch (ending) {
            case FIST -> {
                float shut = backOut(clamp01((d - FIST_CLENCH) / (float) (FIST_SHUT - FIST_CLENCH)));
                float open = ease(clamp01((d - FIST_OPEN) / (float) (FIST_OPENED - FIST_OPEN)));
                float k = shut * (1f - open);
                // the flare's pulse squeezes it a little tighter
                float pulse = d > FIST_SHUT && d < FIST_OPEN ? 0.05f * (float) Math.sin((d - FIST_SHUT) / 70.0) : 0f;
                for (int i = 0; i < 5; i++) c[i] = Math.max(0f, k + pulse * k);
                c[0] *= 0.85f;
            }
            case SNAP -> {
                // the pose: the middle finger pressed to the thumb, the index half up, the ring and pinky folded
                float p = backOut(clamp01((d - SNAP_POSE) / (float) (SNAP_POSED - SNAP_POSE)));
                long s = d - SNAP_AT;
                // the charge: pressed harder as it builds
                float press = s < 0 ? 0.06f * clamp01((d - SNAP_POSED) / (float) (SNAP_AT - SNAP_POSED)) : 0f;
                c[0] = p * (0.62f + press);
                c[1] = p * 0.3f;
                c[2] = p * (0.5f + press);
                c[3] = p * 0.95f;
                c[4] = p * 1.0f;
                if (s >= 0) {
                    // the snap: the middle finger flicks off the thumb into the palm, the thumb springs up and across
                    float f = clamp01(s / 70f);
                    c[2] = c[2] + (1.08f - c[2]) * f;
                    c[0] = c[0] - 0.35f * f + 0.05f * (float) Math.exp(-s / 90.0) * (float) Math.sin(s / 25.0);
                }
            }
            default -> {
            }
        }
        return c;
    }

    /** The snap's charge, 0..1: the stones blaze, the hand trembles. */
    public float charge(long t) {
        if (ending != Ending.SNAP) return 0f;
        long d = t - REVEAL - SNAP_POSED;
        if (d < 0) return 0f;
        float u = clamp01(d / (float) (SNAP_AT - SNAP_POSED));
        return t >= snapAt() ? 0f : u * u;
    }

    /** The fist's flare in the drop's colour, 0..1. */
    public float flare(long t) {
        if (ending != Ending.FIST) return 0f;
        long d = t - REVEAL;
        if (d < FIST_SHUT - 60) return 0f;
        float in = clamp01((d - FIST_SHUT + 60) / 160f), out = 1f - clamp01((d - FIST_OPEN) / 400f);
        return in * out;
    }

    /** Once the fist shuts: ms since (for its shockwave), else -1. */
    public long shutSince(long t) {
        if (ending != Ending.FIST) return -1;
        long d = t - REVEAL - FIST_SHUT;
        return d < 0 ? -1 : d;
    }

    /** The whole thing's alpha: in quickly at the start, out at the end. */
    public float master(long t) {
        float in = clamp01(t / 250f), out = clamp01((total() - t) / 450f);
        return Math.min(in, out);
    }

    // ------------------------------------------------------------------ curves

    static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    public static float clamp(float v) {
        return clamp01(v);
    }

    static float ease(float u) {
        return u * u * (3f - 2f * u);
    }

    /** Over and back: 0 exactly at 0, 1 exactly at 1. */
    static float backOut(float u) {
        if (u <= 0f) return 0f;
        if (u >= 1f) return 1f;
        float c = 1.70158f, v = u - 1f;
        return 1f + (c + 1f) * v * v * v + c * v * v;
    }
}
