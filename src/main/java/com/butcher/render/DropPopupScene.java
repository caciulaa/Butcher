package com.butcher.render;

import java.util.Random;

/**
 * The small pop-ups just above the hotbar - drops worth a moment but not a cutscene. Ported from a viewer's fork
 * (2026-10-26) without its fishing catch. Pure: no Minecraft; {@link DropPopup} plays it in the game. Each is about three
 * seconds, nothing dimmed or hidden:
 *
 * <ul>
 *   <li><b>The Automaton</b> - one of its six robot parts: two brass gears grind up out of the Precursor stone, sparking,
 *       venting steam faster and faster, until they fly apart and the part pops out (the fork's arcs of charge are gone -
 *       the user wanted no lightning);</li>
 *   <li><b>The rift</b> - a Summoning Eye from a Special Zealot: a tear in the air opens, ender particles spiral into it,
 *       an Enderman's eyes look out of the dark, and it spits the eye out as it snaps shut;</li>
 *   <li><b>The harvest</b> - a Crop Fever drop: a strip of farmland, a stalk shooting up and ripening, a hoe's sweep, and
 *       the enchanted crop jumps out of the soil glinting - the rarer the drop, the bigger the burst;</li>
 *   <li><b>The gift</b> - a Garden visitor's reward worth the moment: the visitor walks up to a little greenhouse and
 *       holds it up.</li>
 * </ul>
 *
 * Every shape is pixel art in squares of {@link #P} GUI pixels; only the drop is a real item ({@link DropArt}), drawn
 * by the caller.
 */
public final class DropPopupScene {
    private DropPopupScene() {}

    /** GUI pixels per art pixel. */
    public static final int P = 2;
    /** How far up from the screen's bottom it sits: Fin's and Wisp's height, clear of the action bar (h - 72 .. h - 63). */
    static final int LIFT = 82;

    // ---- the Automaton
    public static final long GRIND = 160, STEAM1 = 620, STEAM2 = 860, STEAM3 = 1020, BURST = 1150, POPPED = 1450, BOT_WORDS = 1460, BOT_FADE = 2750,
            BOT_END = 3150;
    // ---- the rift (a Summoning Eye)
    public static final long RIFT_OPEN = 260, STARE1 = 520, STARE2 = 900, EYE_OUT = 1150, EYE_POPPED = 1450, EYE_WORDS = 1470, EYE_FADE = 2850,
            EYE_END = 3250;
    // ---- the harvest (a Crop Fever drop): quicker - a fever can give one every few seconds
    public static final long SPROUT = 120, RIPE = 520, SWING = 600, CUT = 690, CROP_POPPED = 940, CROP_WORDS = 960;
    // ---- the gift (a Garden visitor's reward worth 5M or more): the visitor walks up to the greenhouse and holds it up
    public static final long G_WALK = 120, G_ARRIVE = 1000, G_RAISE = 1120, G_GIFT = 1260, G_POPPED = 1620, G_WORDS = 1640, G_FADE = 3350,
            G_END = 3750;

    /** Which pop-up. */
    public enum Kind { AUTOMATON, EYE, CROP, GARDEN }

    /** What one pop-up shows. */
    public static final class Pop {
        public Kind kind = Kind.AUTOMATON;
        public String name = "";
        public int colour = 0x5555FF;
        /** "2.5% · Tadgang". */
        public String line = "";
        public long seed;
        /** How good the drop is, 0..1: 10% is 0, 0.1% is 1 (log scale) - more splash, sparkle and burst the better. */
        public float intensity = 0.3f;
        /** The gift: the visitor's own colour (its rarity), for the rim of its silhouette; -1 for none. */
        public int accent = -1;

        public static float intensityFor(double chancePercent) {
            if (chancePercent <= 0) return 0.3f;
            return clamp01((float) ((1.0 - Math.log10(chancePercent)) / 2.0));
        }
    }

    /** The drop itself, centred at (cx, cy), {@code size} px, turned {@code yawDeg} about its upright. */
    public interface Icon {
        void draw(float cx, float cy, float size, float alpha, float yawDeg);
    }

    /** One line of text, centred at (cx, cy), outlined in black as Minecraft's drop messages are. */
    public interface Words {
        void line(String text, float cx, float cy, int argb);
    }

    public static long end(Pop p) {
        return switch (p.kind) {
            case AUTOMATON -> BOT_END;
            case EYE -> EYE_END;
            case GARDEN -> G_END;
            case CROP -> fadeAt(p) + 380;
        };
    }

    /** When it starts to fade: a harvest holds longer the rarer its drop. */
    static long fadeAt(Pop p) {
        return switch (p.kind) {
            case AUTOMATON -> BOT_FADE;
            case EYE -> EYE_FADE;
            case GARDEN -> G_FADE;
            case CROP -> 2250 + Math.round(650 * p.intensity);
        };
    }

    /** Draws {@code p} at {@code t} ms on a {@code w x h} GUI screen. */
    public static void draw(ChopScene.Canvas c, Icon icon, Words words, Pop p, long t, int w, int h) {
        if (t < 0 || t >= end(p)) return;
        long fadeAt = fadeAt(p);
        float fade = t < fadeAt ? 1f : 1f - (t - fadeAt) / (float) (end(p) - fadeAt);
        float in = clamp01(t / 180f);
        float alpha = clamp01(Math.min(fade, in));
        if (alpha < 0.02f) return;
        // Above the hotbar, the bars over it and the action bar, in the middle - where Fin and Wisp play.
        int cx = snap(w / 2f), baseY = snap(h - LIFT);
        switch (p.kind) {
            case AUTOMATON -> automaton(c, icon, words, p, t, cx, baseY, alpha);
            case EYE -> rift(c, icon, words, p, t, cx, baseY, alpha);
            case CROP -> harvest(c, icon, words, p, t, cx, baseY, alpha);
            case GARDEN -> gift(c, icon, words, p, t, cx, baseY, alpha);
        }
    }

    /**
     * How the drop is turned: no spin (the user, 2026-10-26: edge-on they looked wrong) - Fin's three-quarter view with a
     * gentle sway, which only a head shows; a flat item always faces the front ({@link DropIcon}).
     */
    static float spin(long t, long from) {
        return 35f + 9f * (float) Math.sin((t - from) / 520.0);
    }

    /** Fin's drop: 26 px, up out of its strip to 36 px over it (FinPopup.item), its words under it. */
    static final float SIZE = 26f, RISE_TO = 30f, BASE_UP = 6f;

    /** Where the name's line is centred under a drop at {@code y} (Fin's: its top 6 px under the item). */
    static float wordsY(float y) {
        return y + SIZE / 2f + 10f;
    }

    // ================================================================== the Automaton

    private static void automaton(ChopScene.Canvas c, Icon icon, Words words, Pop p, long t, int cx, int floorY, float alpha) {
        stone(c, cx, floorY, t, alpha);
        float rise = ease(clamp01((t - GRIND) / 420f));
        // The gears' spin speeds up to the burst.
        float speed = 1f + 5f * clamp01((t - GRIND) / (float) (BURST - GRIND));
        double turn = spinOf(t, speed);
        float lx = cx - 12, ly = floorY - 10 - 6 * rise + (1f - rise) * 14, rx = cx + 11, ry = floorY - 16 - 6 * rise + (1f - rise) * 14;
        if (t < BURST) {
            gear(c, lx, ly, 5, 8, turn, 0xC9A43A, 0x8C6E22, floorY, alpha);
            gear(c, rx, ry, 4, 7, -turn * 8 / 7 + 0.2, 0xB8B8B8, 0x6E6E6E, floorY, alpha);
            float mx = (lx + rx) / 2f, my = Math.min(ly, ry) - 4;
            steam(c, mx, my, t, STEAM1, p.seed, 1f, alpha);
            steam(c, mx, my, t, STEAM2, p.seed + 1, 1.3f, alpha);
            steam(c, mx, my, t, STEAM3, p.seed + 2, 1.7f, alpha);
            sparks(c, cx, floorY - 14, t, p.seed, speed, alpha);
        } else {
            // They fly apart, still turning, and fall away.
            float s = (t - BURST) / 1000f;
            float fa = alpha * clamp01(1f - s / 0.6f);
            gear(c, lx - 80 * s, ly - 60 * s + 220 * s * s, 5, 8, turn + s * 9, 0xC9A43A, 0x8C6E22, 1e9f, fa);
            gear(c, rx + 80 * s, ry - 70 * s + 220 * s * s, 4, 7, -turn - s * 11, 0xB8B8B8, 0x6E6E6E, 1e9f, fa);
            burst(c, cx, floorY - 16, t, p.seed, alpha);
        }
        if (t >= BURST) {
            float k = clamp01((t - BURST) / (float) (POPPED - BURST));
            float x = cx, y = floorY - BASE_UP - RISE_TO * backOut(k);
            if (k >= 1f) y += (float) Math.sin((t - POPPED) / 380.0) * 1.2f;
            float size = SIZE * Math.min(1f, 0.4f + k);
            icon.draw(x, y, size, alpha, spin(t, BURST));
            if (t >= POPPED) twinkle(c, x, y, size, t, p.colour, p.seed, alpha, p.intensity);
            burst(c, x, y, t, POPPED, p, alpha);
            if (t >= BOT_WORDS) label(words, p, x, wordsY(y), t, BOT_WORDS, alpha);
        }
    }

    static double spinOf(long t, float speed) {
        // Integrated so the speed-up never jumps: the angle grows with t times a speed that rises linearly.
        float d = Math.max(0, t - GRIND) / 1000f;
        float ramp = (BURST - GRIND) / 1000f;
        float k = Math.min(d, ramp);
        double a = 2.0 * (k + 2.5 * k * k / ramp);
        if (d > ramp) a += 2.0 * 6 * (d - ramp);
        return a;
    }

    /** Precursor stone: three rows of grey-brown bricks, a seam every few squares; in from the middle. */
    private static void stone(ChopScene.Canvas c, int cx, int y, long t, float alpha) {
        int half = Math.round(24 * ease(clamp01(t / 240f)));
        for (int i = -half; i <= half; i++) {
            float edge = clamp01(1.15f - Math.abs(i) / 25f);
            for (int row = 0; row < 3; row++) {
                boolean seam = Math.floorMod(i + (row == 1 ? 2 : 0), 5) == 0 && row > 0;
                int col = row == 0 ? 0x9A9384 : seam ? 0x3E3B35 : Math.floorMod(i * 7 + row * 3, 5) == 0 ? 0x5E5A50 : 0x6E6A60;
                cell(c, cx + i * P, y + row * P, col, alpha * edge);
            }
        }
    }

    /**
     * A gear in squares: a ring of {@code teeth} teeth round a body of radius {@code r} (in squares), a hole in the
     * middle, turned {@code turn} radians; nothing below {@code clipY} (it comes up out of the floor).
     */
    private static void gear(ChopScene.Canvas c, float cx, float cy, int r, int teeth, double turn, int rgb, int dark, float clipY, float alpha) {
        int ox = snap(cx), oy = snap(cy);
        for (int pass = 0; pass < 2; pass++) {
            for (int j = -r - 2; j <= r + 2; j++) {
                for (int i = -r - 2; i <= r + 2; i++) {
                    double d = Math.sqrt(i * i + j * j);
                    double ang = Math.atan2(j, i) - turn;
                    boolean tooth = Math.floorMod((int) Math.floor(ang / (2 * Math.PI) * teeth * 2), 2) == 0;
                    boolean in = d <= r - 0.3 && d >= 1.2 || (tooth && d > r - 0.3 && d <= r + 1.2);
                    int y = oy + j * P;
                    if (y + P > clipY) continue;
                    if (pass == 0) {
                        // The ink: one square out all round.
                        if (!in && near(i, j, r, teeth, turn)) cell(c, ox + i * P, y, 0x15110C, alpha);
                    } else if (in) {
                        boolean lit = i + j < -1;
                        int col = d < 2.3 ? dark : d > r - 0.3 ? (lit ? brighten(rgb) : rgb) : (Math.floorMod(i - j, 4) == 0 ? dark : rgb);
                        cell(c, ox + i * P, y, col, alpha);
                    }
                }
            }
        }
    }

    private static boolean near(int i, int j, int r, int teeth, double turn) {
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                int x = i + di, y = j + dj;
                double d = Math.sqrt(x * x + y * y);
                double ang = Math.atan2(y, x) - turn;
                boolean tooth = Math.floorMod((int) Math.floor(ang / (2 * Math.PI) * teeth * 2), 2) == 0;
                if (d <= r - 0.3 && d >= 1.2 || (tooth && d > r - 0.3 && d <= r + 1.2)) return true;
            }
        }
        return false;
    }

    /**
     * A puff of steam out of the gears' mesh: squares going up and out, white turning grey, each a little bigger than the
     * last ({@code power}) - the strain building to the burst.
     */
    private static void steam(ChopScene.Canvas c, float x, float y, long t, long at, long seed, float power, float alpha) {
        long d = t - at;
        if (d < 0 || d > 620) return;
        float k = d / 620f;
        Random rng = new Random(seed * 977 + at);
        int n = Math.round(6 * power);
        for (int i = 0; i < n; i++) {
            float vx = (rng.nextFloat() - 0.5f) * 26f * power, vy = -(26f + rng.nextFloat() * 30f) * power;
            float drift = (float) Math.sin(k * 5 + i) * 2f;
            float px = x + vx * k + drift, py = y + vy * k * (1f - 0.35f * k);
            int col = k < 0.35f ? 0xF2F2F2 : k < 0.7f ? 0xC8C8C8 : 0x9A9A9A;
            float a = alpha * (1f - k) * 0.85f;
            cell(c, px, py, col, a);
            // The older the puff, the bigger it swells.
            if (k > 0.3f) cell(c, px + P, py, col, a * 0.7f);
            if (k > 0.55f) cell(c, px, py - P, col, a * 0.55f);
        }
    }

    /** Sparks thrown off the grinding gears - more as they speed up - falling under their own weight. */
    private static void sparks(ChopScene.Canvas c, float x, float y, long t, long seed, float speed, float alpha) {
        Random rng = new Random(seed * 31 + 5);
        for (int i = 0; i < 26; i++) {
            long born = GRIND + 120 + rng.nextInt((int) (BURST - GRIND - 120));
            float dir = rng.nextFloat() < 0.5f ? -1f : 1f;
            float vx = dir * (20f + rng.nextFloat() * 50f), vy = -(30f + rng.nextFloat() * 50f);
            long d = t - born;
            // Fewer early on: a spark only once the gears are going fast enough for it.
            if (d < 0 || d > 480 || i > 6 + speed * 3.4f) continue;
            float s = d / 1000f;
            int col = rng.nextInt(3) == 0 ? 0xFFF4B0 : rng.nextInt(2) == 0 ? 0xFFB238 : 0xFF7A1E;
            cell(c, x + vx * s, y + vy * s + 300f * s * s, col, alpha * (1f - d / 480f));
        }
    }

    /** The burst: a ring of squares going out, sparks and bits of brass flung, a flash of white in the middle. */
    private static void burst(ChopScene.Canvas c, float x, float y, long t, long seed, float alpha) {
        long d = t - BURST;
        if (d < 0 || d > 700) return;
        float k = d / 700f;
        if (d < 110) {
            float fa = alpha * (1f - d / 110f);
            for (int j = -2; j <= 2; j++) for (int i = -2; i <= 2; i++) if (Math.abs(i) + Math.abs(j) <= 2) cell(c, x + i * P, y + j * P, 0xFFFFFF, fa);
        }
        float r = 6 + 34 * ease(clamp01(d / 380f));
        for (int i = 0; i < 20; i++) {
            double ang = i / 20.0 * Math.PI * 2;
            cell(c, x + (float) Math.cos(ang) * r, y + (float) Math.sin(ang) * r * 0.6f, i % 2 == 0 ? 0x9FE6FF : 0xFFE08A, alpha * (1f - clamp01(d / 380f)));
        }
        Random rng = new Random(seed * 53 + 11);
        float s = d / 1000f;
        for (int i = 0; i < 18; i++) {
            float vx = (rng.nextFloat() - 0.5f) * 180f, vy = -(40f + rng.nextFloat() * 110f);
            int col = i < 5 ? 0x8C8C8C : rng.nextInt(2) == 0 ? 0xFFB238 : 0xFFF4B0;
            cell(c, x + vx * s, y + vy * s + 320f * s * s, col, alpha * (1f - k));
        }
    }

    // ================================================================== the rift (a Summoning Eye)

    static final int VOID = 0x0B0414, RIM = 0xC35BFF, RIM_HOT = 0xF0C8FF, ENDER = 0xB24CFF, ENDER_PALE = 0xE0A8FF;

    private static void rift(ChopScene.Canvas c, Icon icon, Words words, Pop p, long t, int cx, int baseY, float alpha) {
        float ry = baseY - 16;
        // Open, hold, and snap shut as the eye comes out.
        float open = ease(clamp01(t / (float) RIFT_OPEN)) * (t < EYE_OUT ? 1f : 1f - ease(clamp01((t - EYE_OUT) / 240f)));
        float breathe = 1f + 0.08f * (float) Math.sin(t / 110.0) * open;
        int hw = Math.round((13 + 3 * p.intensity) * open * breathe), hh = Math.round(8 * open * breathe);
        // The ender particles, pulled in on a spiral - more and faster as the eye comes.
        Random rng = new Random(p.seed * 29 + 7);
        int n = Math.round(14 + 22 * p.intensity);
        for (int i = 0; i < n; i++) {
            long period = 700 + rng.nextInt(500);
            long born = rng.nextInt(1000);
            float ph = ((t + born) % period) / (float) period;
            if (t > EYE_OUT + 200) continue;
            double ang = rng.nextFloat() * Math.PI * 2 + ph * 4.2;
            float r = (46f + 20f * rng.nextFloat()) * (1f - ph);
            float px = cx + (float) Math.cos(ang) * r, py = ry + (float) Math.sin(ang) * r * 0.55f;
            cell(c, px, py, i % 3 == 0 ? ENDER_PALE : ENDER, alpha * (float) Math.sin(Math.PI * ph) * clamp01(t / 300f));
        }
        if (hw >= 1 && hh >= 1) {
            // A glow of rim round it, then the void with its rim, flickering.
            for (int j = -hh - 1; j <= hh + 1; j++) {
                for (int i = -hw - 1; i <= hw + 1; i++) {
                    float d = (i * i) / (float) ((hw + 1) * (hw + 1)) + (j * j) / (float) ((hh + 1) * (hh + 1));
                    float din = (i * i) / (float) (hw * hw) + (j * j) / (float) (hh * hh);
                    if (din <= 1f) {
                        boolean edge = din > 0.62f;
                        int k = Math.floorMod(i * 7 + j * 13 + (int) (t / 70), 9);
                        int col = edge ? (k == 0 ? RIM_HOT : RIM) : (k == 0 && Math.floorMod(i + j, 3) == 0 ? 0x3A1A5A : VOID);
                        cell(c, cx + i * P, ry + j * P, col, alpha * (edge ? 0.95f : 0.92f));
                    } else if (d <= 1f) {
                        cell(c, cx + i * P, ry + j * P, 0x6A1FA6, alpha * 0.45f * open);
                    }
                }
            }
            // Something looks out: an Enderman's eyes, purple, opening twice.
            for (long at : new long[]{STARE1, STARE2}) {
                long d = t - at;
                if (d < 0 || d > 240 || t >= EYE_OUT) continue;
                float ea = alpha * (d < 40 ? d / 40f : d > 190 ? (240 - d) / 50f : 1f);
                for (int s = -1; s <= 1; s += 2) {
                    cell(c, cx + s * 3 * P, ry, 0xE070FF, ea);
                    cell(c, cx + s * 4 * P, ry, 0xE070FF, ea);
                    cell(c, cx + s * 3 * P, ry, 0xFFFFFF, ea * 0.6f);
                }
            }
        }
        // It spits the eye out: a white flash, a ring, the eye up and turning.
        long d = t - EYE_OUT;
        if (d >= 0 && d < 120) {
            float fa = alpha * (1f - d / 120f);
            for (int j = -2; j <= 2; j++) for (int i = -3; i <= 3; i++) if (Math.abs(i) + Math.abs(j) <= 3) cell(c, cx + i * P, ry + j * P, 0xFFFFFF, fa);
        }
        if (t >= EYE_OUT) {
            float k = clamp01(d / (float) (EYE_POPPED - EYE_OUT));
            float x = cx, y = baseY - BASE_UP - RISE_TO * backOut(k);
            if (k >= 1f) y += (float) Math.sin((t - EYE_POPPED) / 380.0) * 1.2f;
            float size = SIZE * Math.min(1f, 0.4f + k);
            ringOut(c, cx, ry, d, 520, 30 + 14 * p.intensity, RIM, alpha);
            icon.draw(x, y, size, alpha, spin(t, EYE_OUT));
            if (t >= EYE_POPPED) twinkle(c, x, y, size, t, p.colour, p.seed, alpha, p.intensity);
            burst(c, x, y, t, EYE_POPPED, p, alpha);
            if (t >= EYE_WORDS) label(words, p, x, wordsY(y), t, EYE_WORDS, alpha);
        }
    }

    /** A ring of squares going out from (x, y) over {@code ms} from {@code d} = 0. */
    private static void ringOut(ChopScene.Canvas c, float x, float y, long d, long ms, float reach, int rgb, float alpha) {
        if (d < 0 || d > ms) return;
        float k = d / (float) ms;
        float r = 4 + reach * ease(k);
        int n = 18;
        for (int q = 0; q < n; q++) {
            double ang = q * Math.PI * 2 / n;
            cell(c, x + (float) Math.cos(ang) * r, y + (float) Math.sin(ang) * r * 0.6f, q % 2 == 0 ? rgb : 0xFFFFFF, alpha * (1f - k));
        }
    }

    // ================================================================== the harvest (a Crop Fever drop)

    static final int[] RAINBOW = {0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x5555FF, 0xFF55FF};

    private static void harvest(ChopScene.Canvas c, Icon icon, Words words, Pop p, long t, int cx, int soilY, float alpha) {
        float i = p.intensity;
        soil(c, cx, soilY, t, alpha);
        // The stalk: up out of the soil, green, ripening to gold; cut at CUT.
        if (t >= SPROUT && t < CUT) {
            float g = ease(clamp01((t - SPROUT) / (float) (RIPE - SPROUT)));
            int cells = Math.max(1, Math.round(12 * g));
            float ripe = clamp01((t - (RIPE - 120)) / 160f);
            float sway = (float) Math.sin(t / 120.0) * 0.6f;
            for (int k = 0; k < cells; k++) {
                float x = cx + sway * k / 4f;
                int col = mix(0x4FA83A, 0xC9A23A, ripe * k / 12f);
                // Two squares wide, inked on the left, so it reads over any ground.
                cell(c, x - 2 * P, soilY - P - k * P, 0x16200C, alpha);
                cell(c, x - P, soilY - P - k * P, col, alpha);
                cell(c, x, soilY - P - k * P, mix(col, 0xFFFFFF, 0.12f), alpha);
                cell(c, x + P, soilY - P - k * P, 0x16200C, alpha);
                // Leaves off it, every other square.
                if (k > 1 && k < cells - 1 && k % 2 == 0) cell(c, x + (k % 4 == 0 ? P : -P), soilY - P - k * P, mix(0x3E8A2C, 0xB08A2A, ripe), alpha);
            }
            if (cells >= 6) {
                // The ear at the top, gold once ripe.
                float tx = cx + sway * cells / 4f, ty = soilY - P - cells * P;
                for (int dy = 0; dy < 3; dy++) for (int dx = -1; dx <= 1; dx++) {
                    if (Math.abs(dx) + dy > 2) continue;
                    cell(c, tx + dx * P, ty - dy * P, mix(0x6CC04A, 0xF0CC4A, ripe), alpha);
                }
                if (ripe > 0.5f) cell(c, tx, ty - 3 * P, 0xFFF0A0, alpha * ripe);
            }
        }
        // The hoe's sweep: an arc of white across the stalk, a fading trail behind it.
        long sd = t - SWING;
        if (sd >= 0 && sd < 240) {
            float k = sd / 160f;
            for (int q = 0; q < 9; q++) {
                float kk = clamp01(k - q * 0.07f);
                if (kk <= 0f) continue;
                double ang = Math.toRadians(210 - 150 * kk);
                float r = 16f;
                float x = cx + (float) Math.cos(ang) * r, y = soilY - 10 + (float) Math.sin(ang) * r * 0.55f;
                cell(c, x, y, q == 0 ? 0xFFFFFF : 0xD8E4EC, alpha * (1f - q / 9f) * (1f - clamp01((sd - 160) / 80f)));
            }
        }
        // Cut: the stalk in pieces, flying and falling; the soil kicked up.
        long cd = t - CUT;
        if (cd >= 0 && cd < 700) {
            Random rng = new Random(p.seed * 61 + 13);
            float s = cd / 1000f;
            int n = 10 + Math.round(10 * i);
            for (int q = 0; q < n; q++) {
                float vx = (rng.nextFloat() - 0.5f) * 120f, vy = -(30f + rng.nextFloat() * 90f);
                int col = q < 4 ? 0x5C3A1C : rng.nextBoolean() ? 0xC9A23A : 0x4FA83A;
                float py = soilY - 8 + vy * s + 320f * s * s;
                if (py > soilY) continue;
                cell(c, cx + vx * s, py, col, alpha * (1f - cd / 700f));
            }
        }
        if (t >= CUT) {
            float k = clamp01(cd / (float) (CROP_POPPED - CUT));
            float x = cx, y = soilY - BASE_UP - RISE_TO * backOut(k);
            if (k >= 1f) y += (float) Math.sin((t - CROP_POPPED) / 380.0) * 1.2f;
            float size = SIZE * Math.min(1f, 0.4f + k);
            // Pray to RNGesus: rainbow rings going out; Crazy Rare: two of its colour.
            if (i >= 0.95f) {
                for (int r = 0; r < 4; r++) ringOut(c, x, y, cd - r * 110L, 620, 34 + 8 * r, RAINBOW[(r * 2 + (int) (t / 90)) % RAINBOW.length], alpha);
            } else if (i >= 0.7f) {
                for (int r = 0; r < 2; r++) ringOut(c, x, y, cd - r * 130L, 560, 30, r == 0 ? p.colour : 0xFFFFFF, alpha);
            }
            icon.draw(x, y, size, alpha, spin(t, CUT));
            // The enchantment's glint round it: purple sparks.
            glint(c, x, y, size, t, p.seed, alpha, i);
            if (t >= CROP_POPPED) twinkle(c, x, y, size, t, i >= 0.95f ? RAINBOW[(int) (t / 120) % RAINBOW.length] : p.colour, p.seed, alpha, i);
            burst(c, x, y, t, CROP_POPPED, p, alpha);
            if (t >= CROP_WORDS) label(words, p, x, wordsY(y), t, CROP_WORDS, alpha);
        }
    }

    /** Farmland: tilled rows, the furrows darker, the wet soil under; in from the middle. */
    // ================================================================== the gift

    /**
     * A Garden visitor's reward worth the moment (5M and up): a strip of the plot with crops growing, a little greenhouse
     * behind, and the visitor - a silhouette rimmed in its rarity's colour - walks in from the left, stops, raises its arms
     * and the reward rises out of its hands; petals and leaves burst off it, and its name and worth come up under it.
     */
    private static void gift(ChopScene.Canvas c, Icon icon, Words words, Pop p, long t, int cx, int soilY, float alpha) {
        float i = p.intensity;
        greenhouse(c, cx + 14, soilY, t, alpha);
        soil(c, cx, soilY, t, alpha);
        // Crops in the plot, swaying.
        int[] at = {-36, -24, 26, 38};
        for (int k = 0; k < at.length; k++) {
            float g = ease(clamp01((t - 80 - k * 70) / 400f));
            int cells = Math.round(4 * g);
            float sway = (float) Math.sin(t / 260.0 + k) * 0.5f;
            for (int q = 0; q < cells; q++) {
                cell(c, cx + at[k] + sway * q, soilY - P - q * P, q == cells - 1 ? 0x8FD460 : 0x4FA83A, alpha);
                if (q == 2) cell(c, cx + at[k] + sway * q + (k % 2 == 0 ? P : -P), soilY - P - q * P, 0x3E8A2C, alpha);
            }
        }
        // The visitor: in from the left, then still.
        float walk = ease(clamp01((t - G_WALK) / (float) (G_ARRIVE - G_WALK)));
        float vx = cx - 70 + 44 * walk;
        boolean stepping = t >= G_WALK && t < G_ARRIVE;
        float raise = ease(clamp01((t - G_RAISE) / 160f));
        float vAlpha = alpha * clamp01((t - G_WALK) / 200f) * (1f - clamp01((t - G_FADE + 400) / 400f) * 0.6f);
        visitor(c, vx, soilY, stepping ? (int) ((t / 130) % 2) : -1, raise, p.accent < 0 ? 0xFFFFFF : p.accent, vAlpha);
        // The reward: out of the raised hands, up over the plot.
        if (t >= G_GIFT) {
            long d = t - G_GIFT;
            float k = clamp01(d / (float) (G_POPPED - G_GIFT));
            float hx = vx + 2, hy = soilY - 30;
            float restY = soilY - BASE_UP - RISE_TO;
            float x = hx + (cx - hx) * ease(k), y = hy + (restY - hy) * ease(k) - 10f * (float) Math.sin(Math.PI * k);
            if (k >= 1f) y += (float) Math.sin((t - G_POPPED) / 380.0) * 1.2f;
            float size = SIZE * Math.min(1f, 0.4f + k);
            for (int r = 0; r < 2; r++) ringOut(c, x, y, t - G_POPPED - r * 130L, 600, 32 + 6 * r, r == 0 ? p.colour : 0xFFFFFF, alpha);
            if (i >= 0.9f) for (int r = 0; r < 3; r++) ringOut(c, x, y, t - G_POPPED - 260 - r * 110L, 620, 40 + 8 * r, RAINBOW[(r * 2 + (int) (t / 90)) % RAINBOW.length], alpha);
            icon.draw(x, y, size, alpha, spin(t, G_GIFT));
            if (t >= G_POPPED) twinkle(c, x, y, size, t, p.colour, p.seed, alpha, i);
            petals(c, x, y, t, G_POPPED, p.seed, alpha, i);
            burst(c, x, y, t, G_POPPED, p, alpha);
            if (t >= G_WORDS) label(words, p, x, wordsY(y), t, G_WORDS, alpha);
        }
    }

    /** A little greenhouse: a glass gable on a frame, panes catching the light. */
    private static void greenhouse(ChopScene.Canvas c, int cx, int soilY, long t, float alpha) {
        float a = alpha * ease(clamp01(t / 300f));
        if (a < 0.03f) return;
        int half = 12, wall = 14, peak = 24;
        int frame = 0xD8EEF2, glass = 0x8FD3E3;
        // The panes: faint, a glint running across them.
        for (int y = 1; y < wall / P * P; y += P) {
            for (int x = -half + P; x < half; x += P) {
                float shine = Math.abs(((x + y + t / 18f) % 40 + 40) % 40 - 20) < 2 ? 0.5f : 0.16f;
                cell(c, cx + x, soilY - y - P, glass, a * shine);
            }
        }
        for (int y = 0; y < wall; y += P) {
            cell(c, cx - half, soilY - y - P, frame, a * 0.85f);
            cell(c, cx + half, soilY - y - P, frame, a * 0.85f);
        }
        cellLine(c, cx - half, soilY - wall - P, cx, soilY - peak, frame, a * 0.85f, false);
        cellLine(c, cx + half, soilY - wall - P, cx, soilY - peak, frame, a * 0.85f, false);
        // The roof's glass.
        for (int y = wall + P; y < peak; y += P) {
            int span = Math.round(half * (1f - (y - wall) / (float) (peak - wall)));
            for (int x = -span + P; x < span; x += P) cell(c, cx + x, soilY - y - P, glass, a * 0.2f);
        }
        cellLine(c, cx, soilY - peak, cx, soilY - P, frame, a * 0.45f, false);
    }

    /** The visitor, 5 x 13 art pixels: a dark figure rimmed in its colour; {@code step} 0/1 walking, -1 still; arms up by {@code raise}. */
    private static void visitor(ChopScene.Canvas c, float x, int soilY, int step, float raise, int rim, float alpha) {
        if (alpha < 0.03f) return;
        int body = 0x1C2420, x0 = Math.round(x / P) * P;
        int top = soilY - 13 * P;
        // Head 3 x 3, a hat brim over it.
        for (int dy = 0; dy < 3; dy++) for (int dx = -1; dx <= 1; dx++) cell(c, x0 + dx * P, top + (dy + 1) * P, body, alpha);
        for (int dx = -2; dx <= 2; dx++) cell(c, x0 + dx * P, top, rim, alpha * 0.9f);
        // Body.
        for (int dy = 4; dy < 9; dy++) for (int dx = -1; dx <= 1; dx++) cell(c, x0 + dx * P, top + dy * P, body, alpha);
        // Rim down its front, in its colour.
        for (int dy = 1; dy < 9; dy++) cell(c, x0 + 2 * P, top + dy * P, rim, alpha * 0.7f);
        // Legs, stepping.
        for (int dy = 9; dy < 13; dy++) {
            int lx = step == 0 ? (dy > 10 ? -1 : 0) : step == 1 ? (dy > 10 ? 1 : 0) : 0;
            cell(c, x0 + (-1 + lx) * P, top + dy * P, body, alpha);
            cell(c, x0 + (1 - lx) * P, top + dy * P, body, alpha);
        }
        // Arms: down at its sides, or up over its head.
        for (int q = 0; q < 3; q++) {
            float down = top + (5 + q) * P, up = top + (3 - q) * P;
            float ay = down + (up - down) * raise;
            cell(c, x0 - 2 * P, ay, body, alpha);
            cell(c, x0 + 2 * P + (raise > 0.5f ? 0 : P), ay, body, alpha);
        }
    }

    /** Petals and leaves off the reward: flung out, then drifting down, swaying. */
    private static void petals(ChopScene.Canvas c, float x, float y, long t, long at, long seed, float alpha, float intensity) {
        long d = t - at;
        if (d < 0 || d > 1700) return;
        Random rng = new Random(seed * 29 + 7);
        int n = 14 + Math.round(18 * intensity);
        float s = d / 1000f;
        int[] cols = {0xFF9EC8, 0xFFFFFF, 0xFFE070, 0x7CCB4A, 0xFF7FB0};
        for (int q = 0; q < n; q++) {
            double ang = rng.nextFloat() * Math.PI * 2;
            float v = 30f + 50f * rng.nextFloat();
            float drift = (float) Math.sin(s * 5 + q) * 4f;
            float px = x + (float) Math.cos(ang) * v * Math.min(s, 0.45f) * 1.6f + drift;
            float py = y + (float) Math.sin(ang) * v * Math.min(s, 0.45f) * 1.2f + 22f * Math.max(0f, s - 0.3f);
            cell(c, px, py, cols[q % cols.length], alpha * (1f - d / 1700f));
        }
    }

    private static void soil(ChopScene.Canvas c, int cx, int y, long t, float alpha) {
        int half = Math.round(24 * ease(clamp01(t / 220f)));
        for (int i = -half; i <= half; i++) {
            float edge = clamp01(1.15f - Math.abs(i) / 25f);
            // A dark line over and under it: farmland over farmland still reads.
            cell(c, cx + i * P, y - P, 0x1E1209, alpha * edge * 0.85f);
            cell(c, cx + i * P, y + 3 * P, 0x1E1209, alpha * edge * 0.85f);
            for (int row = 0; row < 3; row++) {
                boolean furrow = row == 0 && Math.floorMod(i, 3) == 0;
                int col = row == 0 ? (furrow ? 0x5A3618 : 0x8B5A2B) : row == 1 ? (Math.floorMod(i * 5, 7) == 0 ? 0x4A2C14 : 0x5C3A1C) : 0x3E2410;
                cell(c, cx + i * P, y + row * P, col, alpha * edge);
            }
        }
    }

    /** An enchanted item's glint as squares: purple and white sparks winking round it. */
    private static void glint(ChopScene.Canvas c, float x, float y, float size, long t, long seed, float alpha, float intensity) {
        Random rng = new Random(seed * 23 + 5);
        int n = 5 + Math.round(6 * intensity);
        for (int q = 0; q < n; q++) {
            double ang = rng.nextFloat() * Math.PI * 2;
            float d = size * (0.2f + 0.45f * rng.nextFloat());
            long period = 420 + rng.nextInt(380), off = rng.nextInt(400);
            float k = ((t + off) % period) / (float) period;
            float a = alpha * (float) Math.sin(Math.PI * k) * 0.85f;
            if (a < 0.05f) continue;
            cell(c, x + (float) Math.cos(ang) * d, y + (float) Math.sin(ang) * d, q % 3 == 0 ? 0xFFFFFF : 0xC890FF, a);
        }
    }

    private static int mix(int a, int b, float k) {
        k = clamp01(k);
        int r = Math.round((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * k);
        int g = Math.round((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * k);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * k);
        return r << 16 | g << 8 | bl;
    }

    // ================================================================== shared

    /** Four-point stars twinkling round the drop once it has landed, in its rarity's colour. */
    private static void twinkle(ChopScene.Canvas c, float x, float y, float size, long t, int rgb, long seed, float alpha, float intensity) {
        Random rng = new Random(seed * 17 + 9);
        int n = Math.round(4 + 10 * intensity);
        for (int i = 0; i < n; i++) {
            double ang = rng.nextFloat() * Math.PI * 2;
            float d = size * 0.55f + rng.nextFloat() * (6f + 10f * intensity);
            long period = 700 + rng.nextInt(500), off = rng.nextInt(700);
            float k = ((t + off) % period) / (float) period;
            float a = alpha * (float) Math.sin(Math.PI * k);
            if (a < 0.05f) continue;
            float sx = x + (float) Math.cos(ang) * d, sy = y + (float) Math.sin(ang) * d * 0.8f;
            int col = i % 2 == 0 ? 0xFFFFFF : brighten(rgb);
            cell(c, sx, sy, col, a);
            if (k > 0.3f && k < 0.7f) {
                cell(c, sx - P, sy, rgb, a * 0.8f);
                cell(c, sx + P, sy, rgb, a * 0.8f);
                cell(c, sx, sy - P, rgb, a * 0.8f);
                cell(c, sx, sy + P, rgb, a * 0.8f);
            }
        }
    }

    /**
     * The landing, for a drop worth it (a third of the way up): a ring of squares going out, two for the rarest, and a
     * ring of sparks flung off.
     */
    private static void burst(ChopScene.Canvas c, float x, float y, long t, long at, Pop p, float alpha) {
        float i = p.intensity;
        if (i < 0.33f) return;
        long d = t - at;
        if (d < 0 || d > 700) return;
        int rings = i >= 0.75f ? 2 : 1;
        for (int j = 0; j < rings; j++) {
            long dj = d - j * 120L;
            if (dj < 0 || dj > 520) continue;
            float k = dj / 520f;
            float r = 8 + (16 + 18 * i) * ease(k);
            int n = 16 + Math.round(10 * i);
            for (int q = 0; q < n; q++) {
                double ang = q * Math.PI * 2 / n;
                cell(c, x + (float) Math.cos(ang) * r, y + (float) Math.sin(ang) * r * 0.7f, j == 0 ? brighten(p.colour) : 0xFFFFFF,
                        alpha * (1f - k) * (0.5f + 0.5f * i));
            }
        }
        Random rng = new Random(p.seed * 41 + 3);
        float s = d / 1000f;
        int sparks = Math.round(6 + 16 * i);
        for (int q = 0; q < sparks; q++) {
            double ang = rng.nextFloat() * Math.PI * 2;
            float v = 40f + 60f * rng.nextFloat() * (0.6f + 0.6f * i);
            cell(c, x + (float) Math.cos(ang) * v * s, y + (float) Math.sin(ang) * v * s * 0.8f + 90f * s * s,
                    q % 2 == 0 ? 0xFFFFFF : brighten(p.colour), alpha * (1f - d / 700f));
        }
    }

    /** The name in its rarity's colour, and under it the chance and where it came from. */
    private static void label(Words words, Pop p, float x, float y, long t, long at, float alpha) {
        float a1 = alpha * clamp01((t - at) / 200f);
        float a2 = alpha * clamp01((t - at - 140) / 200f);
        // Bold, with the line 11 px under it - as Fin and Wisp (the original pop-ups) set theirs.
        if (a1 > 0.03f) words.line("\u00a7l" + p.name, x, y, a(a1) | p.colour);
        if (a2 > 0.03f && !p.line.isEmpty()) words.line(p.line, x, y + 11, a(a2) | 0xC8B27A);
    }

    private static void cellLine(ChopScene.Canvas c, float x0, float y0, float x1, float y1, int rgb, float alpha, boolean ink) {
        int n = steps(x0, y0, x1, y1);
        for (int i = 0; i <= n; i++) {
            float k = n == 0 ? 0 : i / (float) n;
            float x = x0 + (x1 - x0) * k, y = y0 + (y1 - y0) * k;
            if (ink) {
                cell(c, x - P, y, rgb, alpha);
                cell(c, x + P, y, rgb, alpha);
                cell(c, x, y - P, rgb, alpha);
                cell(c, x, y + P, rgb, alpha);
            } else {
                cell(c, x, y, rgb, alpha);
            }
        }
    }

    private static int steps(float x0, float y0, float x1, float y1) {
        return Math.max(1, Math.round(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) / P));
    }

    /** One square of the art, on the grid. */
    private static void cell(ChopScene.Canvas c, float x, float y, int rgb, float alpha) {
        if (alpha < 0.03f) return;
        c.fill(snap(x), snap(y), P, P, a(alpha) | (rgb & 0xFFFFFF));
    }

    private static int snap(float v) {
        return Math.round(v / P) * P;
    }

    /** 0..1 and back over {@code ms} after {@code at}: a nibble's tug. */
    private static float dip(long t, long at, long ms) {
        long d = t - at;
        if (d < 0 || d > ms) return 0f;
        return (float) Math.sin(Math.PI * d / ms);
    }

    private static int brighten(int rgb) {
        int r = Math.min(255, (rgb >> 16 & 255) + 70), g = Math.min(255, (rgb >> 8 & 255) + 70), b = Math.min(255, (rgb & 255) + 70);
        return r << 16 | g << 8 | b;
    }

    private static int a(float alpha) {
        return Math.round(255 * clamp01(alpha)) << 24;
    }

    static float clamp01(float x) {
        return x < 0f ? 0f : (x > 1f ? 1f : x);
    }

    static float ease(float x) {
        x = clamp01(x);
        return x * x * (3f - 2f * x);
    }

    static float backOut(float x) {
        x = clamp01(x);
        float c1 = 1.70158f, c3 = c1 + 1f, y = x - 1f;
        return 1f + c3 * y * y * y + c1 * y * y;
    }
}
