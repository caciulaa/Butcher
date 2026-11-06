package com.butcher.anim;

/**
 * Divan's Alloy's own cutscene (the Crystal Nucleus's jackpot, designed with the user 2026-10-27): a whole room - the
 * Dwarven Mines' forge - filling the screen, shot by shot with the camera moving through it. The most expensive drop in
 * the game, so the grandest thing the mod plays. Pure: ms from the moment the vault's camera dives into the forge.
 *
 * The shots: the room wakes (a crane down from a high corner - the furnace flares, the braziers catch, the Automaton's
 * eye lights and its arm lifts); the five crystals sweep in and circle over the crucible (low, looking up); they drop in
 * one by one and the metal turns white (close, from above); the crucible tips and pours into the mould (from the side);
 * three hammer blows, each closer (a low hero angle), the last in slow motion; the ingot cools from white to its gold
 * (an orbit round the anvil); and it rises, centred, into the showcase.
 *
 * Given life (the user, 2026-10-27: "a little robotic... more bouncy, less fixated camera movements"): the camera is
 * handheld - it drifts and breathes the whole time - and overshoots into its keys; some shots whip into the next; the
 * crucible, the arm and the ingot move with anticipation, overshoot and rebound rather than straight eases.
 *
 * Two clocks. {@code real} is the screen's; {@link #scene} slows it round the last blow ({@code SLOW_FROM} to
 * {@code SLOW_TO} real ms play at {@code SLOW_RATE}). Every beat below is on the scene clock; the sound's cues and the
 * cutscene's length are turned back to real time with {@link #real}.
 */
public final class ForgeTimeline {

    // ------------------------------------------------------------------ the beats (scene ms)
    public static final long FIRE_FROM = 250, FIRE_TO = 900;
    public static final long BRAZIER_L = 950, BRAZIER_R = 1200;
    public static final long EYE = 1750, ARM_FROM = 1900, ARM_TO = 2700;
    public static final long FLY_FROM = 3350, FLY_TO = 4400, RING_TO = 5500;
    public static final long DROP_FROM = 5650, DROP_EVERY = 320, DROP_MS = 300;
    public static final long WHITE = DROP_FROM + 4 * DROP_EVERY + DROP_MS + 150;
    public static final long TIP_FROM = 7900, TIP_TO = 8450, STREAM_FROM = 8300, STREAM_TO = 9400, BACK_FROM = 9450, BACK_TO = 9900;
    /** The three blows: when the arm starts to lift for each, and when it strikes. */
    public static final long[] RAISE = {10450, 11150, 11850}, STRIKE = {10920, 11620, 12550};
    public static final long COOL_FROM = 12950, COOL_TO = 15300;
    public static final long LIFT_FROM = 15400, LIFT_TO = 16800, WORDS = 16450, TOTAL = 21300;
    /**
     * The crucible's chains come down onto its pins (CHAIN_FROM .. BACK_TO) and hoist it off the cradle, up out of the
     * shot (HOIST_FROM .. HOIST_TO): the hammer's swing passes where it stood (the user: the arm went through it).
     */
    public static final long CHAIN_FROM = 9300, HOIST_FROM = 9950, HOIST_TO = 10400;
    public static final float HOIST = 130f;
    /** How far the crucible tips to pour. */
    public static final float TIP = 0.95f;

    // ------------------------------------------------------------------ the slow motion round the last blow

    /** Real ms where the slow motion starts, how long it lasts in real ms, and how fast the scene runs in it. */
    static final long SLOW_FROM = STRIKE[2] - 180;
    static final long SLOW_REAL = 760;
    static final float SLOW_RATE = 0.32f;
    static final long SLOW_SCENE = Math.round(SLOW_REAL * SLOW_RATE);

    /** The scene's time for a real time: the same, but slowed round the last blow (eased in and out of it). */
    public static long scene(long real) {
        if (real <= SLOW_FROM) return real;
        if (real >= SLOW_FROM + SLOW_REAL) return real - (SLOW_REAL - SLOW_SCENE);
        return SLOW_FROM + Math.round((real - SLOW_FROM) * SLOW_RATE);
    }

    /** The real time a scene time plays at. */
    public static long real(long scene) {
        if (scene <= SLOW_FROM) return scene;
        if (scene >= SLOW_FROM + SLOW_SCENE) return scene + (SLOW_REAL - SLOW_SCENE);
        return SLOW_FROM + Math.round((scene - SLOW_FROM) / SLOW_RATE);
    }

    /** The cutscene's length on the screen's clock. */
    public static long realTotal() {
        return real(TOTAL);
    }

    // ------------------------------------------------------------------ what moves

    /** The crucible's tip (radians about z, negative pours to +x): a lean back first, over with an overshoot, back. */
    public static float tip(long t) {
        if (t < TIP_FROM - 160) return 0f;
        if (t < TIP_FROM) return 0.06f * (float) Math.sin(Math.PI * (t - TIP_FROM + 160) / 160.0);       // the lean back
        if (t < TIP_TO) return -TIP * backOut((t - TIP_FROM) / (float) (TIP_TO - TIP_FROM));
        if (t < BACK_FROM) return -TIP - 0.035f * (float) Math.sin((t - TIP_TO) / 55.0) * (float) Math.exp(-(t - TIP_TO) / 400.0);
        float u = clamp01((t - BACK_FROM) / (float) (BACK_TO - BACK_FROM));
        return -TIP * (1f - backOut(u)) + 0.05f * (float) Math.sin(u * Math.PI * 3) * (1f - u);
    }

    /** How far the crucible has been hoisted (mod pixels up): a jerk as the chains take it, then up and away. */
    public static float hoist(long t) {
        if (t < HOIST_FROM) return 0f;
        float u = clamp01((t - HOIST_FROM) / (float) (HOIST_TO - HOIST_FROM));
        return HOIST * u * u * (1.6f - 0.6f * u);
    }

    /** How far the chains have come down to the pins, 0..1. */
    public static float chains(long t) {
        if (t < CHAIN_FROM) return 0f;
        return clamp01((t - CHAIN_FROM) / (float) (BACK_TO - 150 - CHAIN_FROM));
    }

    /** The furnace's fire, 0.25 smouldering to 1 roaring, flickering; flaring on each blow. */
    public static float fire(long t) {
        float f = 0.25f + 0.75f * ease(clamp01((t - FIRE_FROM) / (float) (FIRE_TO - FIRE_FROM)));
        float flicker = 0.08f * (float) (Math.sin(t / 61.0) + 0.6 * Math.sin(t / 23.0 + 1.3));
        return Math.max(0f, f + flicker * f + 0.3f * flash(t));
    }

    /** A brazier's flame, 0 to 1 as it catches (with a flare as it does), flickering. */
    public static float brazier(long t, boolean left) {
        long at = left ? BRAZIER_L : BRAZIER_R;
        float on = ease(clamp01((t - at) / 300f));
        float flare = (float) Math.exp(-Math.max(0, t - at) / 220.0) * (t >= at ? 0.6f : 0f);
        return on * (1f + 0.1f * (float) Math.sin(t / (left ? 47.0 : 53.0))) + flare;
    }

    /** The Automaton waking: its eye's light, 0..1, a flicker as it comes on, a blink now and then. */
    public static float eye(long t) {
        long d = t - EYE;
        if (d < 0) return 0f;
        if (d < 260) return (d / 40) % 2 == 0 ? 0.7f : 0.15f;
        long blink = (d - 260) % 3100;
        return blink < 120 ? 0.2f : 1f;
    }

    /** How fast the Automaton's gears turn, radians: idle, faster as it works, racing on each blow. */
    public static float gears(long t) {
        if (t < EYE) return 0f;
        float a = (t - EYE) / 600f;
        for (long s : STRIKE) if (t > s - 300) a += Math.min(1f, (t - s + 300) / 300f) * 2.2f;
        return a;
    }

    /**
     * The arm's pose: {shoulder, forearm} radians about z. Slumped at first, lifted ready with an overshoot, then each
     * blow - a small dip of anticipation, drawn up and back past the top and settling there, down fast onto the ingot,
     * rebounding - the last the biggest.
     */
    public static float[] arm(long t) {
        // Ready, the hammer hangs back over the anvil's far end - clear of the crucible, the pour and the hoist (it
        // used to wait up over the crucible, and went through it).
        float[] slump = {-0.08f, 0.45f}, ready = {-0.3f, 0.65f}, up = {-0.42f, -1.45f}, hit = {0f, 0f};
        if (t < ARM_FROM) return slump;
        if (t < ARM_TO) return mix(slump, ready, backOut((t - ARM_FROM) / (float) (ARM_TO - ARM_FROM)));
        float[] pose = ready;
        for (int k = 0; k < 3; k++) {
            if (t < RAISE[k]) return breathe(pose, t);
            float[] top = k == 2 ? new float[]{-0.55f, -1.9f} : up;
            long strike = STRIKE[k], raise = RAISE[k];
            long down = k == 2 ? 150 : 110, dip = 90;
            if (t < raise + dip) {
                float u = (t - raise) / (float) dip;
                return mix(pose, new float[]{pose[0] + 0.05f, pose[1] + 0.12f}, (float) Math.sin(Math.PI * u) * 0.8f);
            }
            if (t < strike - down - 60) return mix(pose, top, backOut(clamp01((t - raise - dip) / (float) (strike - down - 60 - raise - dip))));
            if (t < strike - down) return top;
            if (t < strike) {
                float u = (t - (strike - down)) / (float) down;
                return mix(top, hit, u * u * u);
            }
            // The rebound off the ingot, bouncing twice and settling where it struck.
            float b = clamp01((t - strike) / 320f);
            float bounce = (float) (Math.abs(Math.sin(b * Math.PI * 2.2)) * Math.exp(-b * 3.5));
            pose = mix(hit, new float[]{-0.08f, -0.3f}, bounce * 0.8f + b * 0.25f);
            if (k < 2 && t < RAISE[k + 1]) return pose;
        }
        if (t < COOL_FROM) return pose;
        return mix(pose, ready, backOut(clamp01((t - COOL_FROM) / 900f)));
    }

    /** A pose breathing a little, as a machine idling does. */
    private static float[] breathe(float[] pose, long t) {
        float b = 0.02f * (float) Math.sin(t / 380.0);
        return new float[]{pose[0] + b, pose[1] - b * 1.5f};
    }

    /** The ingot's hop and squash on each blow: {lift, squash 0..1}. */
    public static float[] hop(long t) {
        for (int k = 2; k >= 0; k--) {
            long d = t - STRIKE[k];
            if (d < 0 || d > 420) continue;
            float u = d / 420f;
            float squash = (float) Math.exp(-d / 60.0) * (k == 2 ? 0.45f : 0.3f);
            float lift = (float) (Math.abs(Math.sin(u * Math.PI * 2)) * Math.exp(-u * 4)) * (k == 2 ? 3.5f : 2.2f);
            return new float[]{lift, squash};
        }
        return new float[]{0f, 0f};
    }

    /** How hard the last blow's impact is still shaking things, 0..1 (each blow's own, the last the biggest). */
    public static float impact(long t) {
        float k = 0f;
        for (int i = 0; i < 3; i++) {
            long d = t - STRIKE[i];
            if (d >= 0 && d < 600) k = Math.max(k, (i == 2 ? 1f : 0.65f) * (float) Math.exp(-d / 130.0));
        }
        long w = t - WHITE;
        if (w >= 0 && w < 400) k = Math.max(k, 0.35f * (float) Math.exp(-w / 120.0));
        long a = t - (LIFT_TO - 200);
        if (a >= 0 && a < 500) k = Math.max(k, 0.5f * (float) Math.exp(-a / 140.0));
        return k;
    }

    /** A white flash: the metal turning white, each blow, the Alloy's arrival. */
    public static float flash(long t) {
        float f = pulse(t, WHITE, 260);
        for (long s : STRIKE) f = Math.max(f, pulse(t, s, 160) * (s == STRIKE[2] ? 1f : 0.7f));
        return Math.max(f, pulse(t, LIFT_TO - 200, 380));
    }

    private static float pulse(long t, long at, float ms) {
        long d = t - at;
        return d < 0 ? 0f : (float) Math.exp(-d / ms) * clamp01(1f - d / (ms * 6f));
    }

    /** Crystal {@code k}'s drop into the crucible, 0..1 (-1 before it falls, 2 once it is in). */
    public static float drop(int k, long t) {
        long d = t - (DROP_FROM + k * DROP_EVERY);
        if (d < 0) return -1f;
        if (d > DROP_MS) return 2f;
        return d / (float) DROP_MS;
    }

    /** The crystals' flight in, 0..1 (each a little behind the one before), overshooting into the ring. */
    public static float fly(int k, long t) {
        return backOut(clamp01((t - FLY_FROM - k * 90L) / (float) (FLY_TO - FLY_FROM - 360)));
    }

    public static float whiteHot(long t) {
        return ease(clamp01((t - WHITE + 150) / 300f));
    }

    /** The stream from the spout, 0 (none) to 1 (full), thinning at its end. */
    public static float stream(long t) {
        if (t < STREAM_FROM || t > STREAM_TO) return 0f;
        float a = clamp01((t - STREAM_FROM) / 120f), b = clamp01((STREAM_TO - t) / 260f);
        return Math.min(a, b);
    }

    /** The mould filling, 0..1. */
    public static float fill(long t) {
        return ease(clamp01((t - STREAM_FROM - 100) / (float) (STREAM_TO - STREAM_FROM)));
    }

    /** The ingot's heat: 1 white from the pour, through the blows, cooling to 0 - the Alloy's own gold. */
    public static float heat(long t) {
        if (t < STREAM_FROM) return 0f;
        if (t < COOL_FROM) return 1f;
        float u = clamp01((t - COOL_FROM) / (float) (COOL_TO - COOL_FROM));
        return 1f - u * u * (3f - 2f * u);
    }

    /** The Alloy lifting off the mould to the showcase, 0..1 (-1 before), overshooting into place. */
    public static float lift(long t) {
        if (t < LIFT_FROM) return -1f;
        return backOut(clamp01((t - LIFT_FROM) / (float) (LIFT_TO - LIFT_FROM)));
    }

    public static float words(long t, int line) {
        return clamp01((t - WORDS - line * 220L) / 300f);
    }

    /** In from the black of the dive, out at the end (on the scene clock). */
    public static float master(long t) {
        return Math.min(clamp01(t / 380f), clamp01((TOTAL - t) / 600f));
    }

    // ------------------------------------------------------------------ the camera

    /** A camera: what it looks at (x, y, z mod pixels), its yaw and pitch (degrees), its zoom (1 is the vault's). */
    public record Cam(float x, float y, float z, float yaw, float pitch, float zoom) {
        Cam mix(Cam o, float u) {
            return new Cam(x + (o.x - x) * u, y + (o.y - y) * u, z + (o.z - z) * u, yaw + (o.yaw - yaw) * u,
                    pitch + (o.pitch - pitch) * u, zoom * (float) Math.pow(o.zoom / zoom, u));
        }

        Cam plus(float dx, float dy, float dz, float dyaw, float dpitch, float zoomK) {
            return new Cam(x + dx, y + dy, z + dz, yaw + dyaw, pitch + dpitch, zoom * zoomK);
        }
    }

    /** One shot: from {@code start} to the next shot's start, its keys at ms from its start; {@code whip}: it whips in. */
    record Shot(long start, long[] at, Cam[] keys, boolean whip) {
    }

    static final Shot[] SHOTS = {
            // The room wakes: a crane down from a high corner, turning in towards the furnace.
            new Shot(0, new long[]{0, 1600, 3350}, new Cam[]{new Cam(0, 86, 10, 54, -38, 1.0f), new Cam(-4, 68, 14, 40, -26, 0.98f),
                    new Cam(-8, 54, 16, 28, -17, 1.02f)}, false),
            // The crystals sweep in: low, looking just down at them over the crucible, drifting round.
            new Shot(3350, new long[]{0, 1100, 2300}, new Cam[]{new Cam(-16, 76, 40, -36, -4, 1.05f), new Cam(-18, 78, 34, -22, -3, 1.14f),
                    new Cam(-20, 76, 30, -8, -3, 1.24f)}, true),
            // They drop in: close over the crucible, from above.
            new Shot(5650, new long[]{0, 2250}, new Cam[]{new Cam(-20, 64, 26, 16, -44, 1.8f), new Cam(-18, 62, 26, 32, -52, 2.1f)}, false),
            // The pour: from the side, along the stream, pushing in.
            new Shot(7900, new long[]{0, 2050}, new Cam[]{new Cam(-8, 46, 26, 22, -12, 1.45f), new Cam(-4, 41, 26, 32, -16, 1.7f)}, true),
            // The blows: a low hero angle on the anvil, closer after each.
            new Shot(10450, new long[]{0, 480, 1180, 2110, 2500}, new Cam[]{new Cam(8, 42, 26, -40, -5, 1.4f), new Cam(7, 40, 26, -32, -4, 1.52f),
                    new Cam(6, 38, 26, -22, -4, 1.75f), new Cam(4, 36, 26, -10, -3, 2.1f), new Cam(4, 36, 26, -6, -3, 2.15f)}, false),
            // It cools: an orbit round the anvil.
            new Shot(12950, new long[]{0, 2450}, new Cam[]{new Cam(4, 32, 26, -62, -24, 1.9f), new Cam(4, 32, 26, 32, -20, 1.6f)}, true),
            // The reveal: it rises and the camera rises with it, centring it.
            new Shot(15400, new long[]{0, 1400, 5900}, new Cam[]{new Cam(4, 34, 26, 32, -20, 1.6f), new Cam(4, 66, 40, 22, -8, 1.28f),
                    new Cam(4, 66, 40, 12, -6, 1.34f)}, false),
    };

    /** How long a whip takes either side of its cut. */
    static final long WHIP = 170;

    /** The camera now (scene time; {@code real} for the handheld drift, which never slows): eased between its keys. */
    public static Cam camera(long t, long real) {
        int i = shot(t);
        Shot s = SHOTS[i];
        Cam cam = keyed(s, t - s.start);
        // A whip: the end of the shot before swings away fast, this one comes in from the other side.
        if (i + 1 < SHOTS.length && SHOTS[i + 1].whip && SHOTS[i + 1].start - t < WHIP) {
            float u = 1f - (SHOTS[i + 1].start - t) / (float) WHIP;
            cam = cam.plus(0, 0, 0, 38f * u * u * u, 0, 1f + 0.05f * u);
        }
        if (s.whip && t - s.start < WHIP) {
            float u = 1f - (t - s.start) / (float) WHIP;
            cam = cam.plus(0, 0, 0, -38f * u * u * u, 0, 1f + 0.05f * u);
        }
        // Handheld: never quite still - a slow drift and a breath, a little more on the wide shots.
        float k = i == 0 || i == 6 ? 1.3f : 1f;
        double r = real;
        float dx = k * (float) (1.6 * Math.sin(r / 830.0) + 0.7 * Math.sin(r / 370.0 + 1.1));
        float dy = k * (float) (1.1 * Math.sin(r / 710.0 + 0.4) + 0.5 * Math.sin(r / 290.0 + 2.2));
        float dyaw = k * (float) (0.9 * Math.sin(r / 1130.0 + 0.7) + 0.35 * Math.sin(r / 470.0));
        float dpitch = k * (float) (0.55 * Math.sin(r / 970.0 + 1.9));
        float breath = 1f + 0.012f * (float) Math.sin(r / 1300.0);
        return cam.plus(dx, dy, 0, dyaw, dpitch, breath);
    }

    private static Cam keyed(Shot s, long d) {
        for (int i = 0; i < s.at.length - 1; i++) {
            if (d <= s.at[i + 1]) {
                float u = clamp01((d - s.at[i]) / (float) (s.at[i + 1] - s.at[i]));
                return s.keys[i].mix(s.keys[i + 1], smooth(u, i == 0, i == s.at.length - 2));
            }
        }
        return s.keys[s.keys.length - 1];
    }

    /** Which shot {@code t} is in (a cut is where this changes). */
    public static int shot(long t) {
        int k = 0;
        for (int i = 0; i < SHOTS.length; i++) if (t >= SHOTS[i].start) k = i;
        return k;
    }

    /** From rest at a shot's first key; through the keys between; into its last with a small overshoot and settle. */
    private static float smooth(float u, boolean first, boolean last) {
        if (first && last) return backOutSoft(ease(u) * 0.7f + u * 0.3f);
        if (first) return u * u * (2f - u);
        if (last) return backOutSoft(u);
        return u;
    }

    /** A gentle overshoot: a few percent past and back, for the camera. */
    private static float backOutSoft(float u) {
        float c1 = 0.9f, c3 = c1 + 1f;
        float v = u - 1f;
        return 1f + c3 * v * v * v + c1 * v * v;
    }

    /** Overshoots and settles: for things that move with weight. */
    static float backOut(float u) {
        u = clamp01(u);
        float c1 = 1.70158f, c3 = c1 + 1f;
        float v = u - 1f;
        return 1f + c3 * v * v * v + c1 * v * v;
    }

    static float[] mix(float[] a, float[] b, float u) {
        return new float[]{a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u};
    }

    static float ease(float u) {
        u = clamp01(u);
        return u * u * (3f - 2f * u);
    }

    static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
