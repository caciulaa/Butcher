package com.butcher;

import com.butcher.config.ButcherStats;
import com.butcher.config.PatternConfig;
import com.butcher.detect.AutomatonDrops;
import com.butcher.detect.CropFever;
import com.butcher.detect.GardenVisitors;
import com.butcher.detect.Location;
import com.butcher.detect.SummoningEyeWatcher;
import com.butcher.market.CorpseValue;
import com.butcher.render.CropFeverOverlay;
import com.butcher.render.DropArt;
import com.butcher.render.DropPopup;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * The small pop-ups that are not cutscenes - the Automaton's robot parts, a Special Zealot's Summoning Eye - and the
 * Garden (Crop Fever's glow and drops, a visitor's 5M+ gift), ported from a viewer's fork (2026-10-26). ButcherClient
 * hands this its game chat lines and its tick; {@link DropPopup} and {@link CropFeverOverlay} draw what it starts.
 * Every call from ButcherClient goes through {@code Safety.run}, keyed "automaton", "eye" or "garden".
 */
public final class Popups {
    private Popups() {}

    private static PatternConfig config() {
        return ButcherClient.config;
    }

    // ================================================================== chat

    /** A game line (never player chat, never someone else's drop). */
    public static void onChatLine(Component message, String text) {
        PatternConfig c = config();
        if (c == null) return;
        if (c.automatonPopup && Safety.on("automaton")) Safety.run("automaton", () -> automaton(text));
        if (c.eyePopup && Safety.on("eye")) Safety.run("eye", () -> eyeWatcher.onChatLine(text, System.currentTimeMillis()));
        if ((c.gardenFever || c.gardenCropDrops || c.gardenGift) && Safety.on("garden")) Safety.run("garden", () -> garden(message, text));
    }

    private static void automaton(String text) {
        AutomatonDrops.Part part = AutomatonDrops.of(text);
        if (part == null) return;
        ButcherClient.decisions.note("§aAutomaton: " + part.name() + " - the small pop-up");
        ButcherStats st = ButcherStats.get();
        st.onSourceTally("automaton", part.name());
        String nth = "your " + com.butcher.gui.ChopScreen.ordinal(st.sourceDropCount("automaton", part.name()));
        DropPopup.showAutomaton(part.name(), AutomatonDrops.COLOUR, "1% · Automaton · " + nth, 0.5f, part.art());
    }

    private static void garden(Component message, String text) {
        PatternConfig c = config();
        CropFever.Event fe = CropFever.parse(text);
        if (fe != null) {
            switch (fe.type()) {
                case START -> {
                    ButcherClient.decisions.note("§aCrop Fever: " + fe.seconds() + " seconds");
                    if (c.gardenFever) CropFeverOverlay.start(fe.seconds());
                }
                case END -> CropFeverOverlay.cure();
                case DROP -> {
                    boolean show = c.gardenCropDrops && (!c.gardenCropRareUp || fe.tier().ordinal() >= CropFever.Tier.RARE.ordinal());
                    ButcherClient.decisions.note("§7Crop Fever: " + fe.tier().word + " - " + CropFever.label(fe)
                            + (show ? " - the small pop-up" : " - not shown"));
                    ButcherStats.get().onSourceTally("garden", "Crop Fever " + CropFever.pretty(fe.tier()));
                    if (show) showCrop(fe, false);
                }
            }
            return;
        }
        if (!c.gardenGift) return;
        long now = System.currentTimeMillis();
        String[] plain = text.split("\n");
        String[] legacy = ButcherClient.legacy(message).split("\n");
        for (int i = 0; i < plain.length; i++) gardenWatcher.onLine(plain[i], i < legacy.length ? legacy[i] : null, now);
    }

    // ================================================================== tick

    public static void tick(Minecraft client, boolean live) {
        long now = System.currentTimeMillis();
        PatternConfig c = config();
        if (client.level == null) {
            CropFeverOverlay.stop();
            DropPopup.stop();
        }
        if (c == null) return;
        if (live && c.eyePopup && Location.maybeIn(Location.THE_END)) Safety.run("eye", () ->
                eyeWatcher.tick(client.player, client.gui.screen() != null, now));
        // Off the Garden a fever cannot be on: a warp away ends its glow.
        if (live && CropFeverOverlay.feverOn() && !Location.maybeIn(Location.GARDEN)) CropFeverOverlay.cure();
        Safety.run("garden", () -> {
            gardenWatcher.tick(now);
            giftTick(now);
        });
    }

    /** Whether one of these is on screen (or a fever's glow is up): drawOwnHud draws then. */
    public static boolean showing() {
        return DropPopup.busy() || CropFeverOverlay.active();
    }

    public static void draw(net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
        if (CropFeverOverlay.active()) Safety.run("garden", () -> CropFeverOverlay.draw(graphics));
        if (DropPopup.busy()) Safety.run("popups", () -> DropPopup.draw(graphics));
    }

    public static void stop() {
        DropPopup.stop();
        CropFeverOverlay.stop();
        giftPending.clear();
    }

    // ================================================================== the Summoning Eye

    /** The Special Zealot's Summoning Eye (Awakened: the 4% one): the rift. */
    private static final SummoningEyeWatcher eyeWatcher = new SummoningEyeWatcher(ButcherClient.decisions,
            awakened -> Minecraft.getInstance().execute(() -> showEye(awakened, false)));

    public static void showEye(boolean awakened, boolean test) {
        String title = awakened ? "Awakened Summoning Eye" : "Summoning Eye";
        String nth;
        if (!test) {
            ButcherStats st = ButcherStats.get();
            st.onSourceTally("eye", title);
            nth = " · your " + com.butcher.gui.ChopScreen.ordinal(st.sourceDropCount("eye", title));
        } else {
            nth = " (test)";
        }
        // A Special Zealot spawns from 1 in 420 Zealot kills; the Awakened eye is 4% of those.
        String line = (awakened ? "1 in 10,500" : "1 in 420") + " · Special Zealot" + nth;
        DropPopup.showEye(title, awakened ? 0xFFAA00 : 0xAA00AA, line, awakened ? 1f : 0.81f,
                DropArt.model("hypixel_skyblock:item/uncategorized/summoning_eye"));
    }

    // ================================================================== Crop Fever's drops

    /** A Crop Fever drop's pop-up: its count and name in its tier's colour, the tier and its worth (bazaar) under it. */
    public static void showCrop(CropFever.Event e, boolean test) {
        CropFever.Item item = e.item();
        long price = 0;
        PatternConfig c = config();
        if (item.id() != null && (c == null || c.livePrices)) {
            com.butcher.market.Bazaar.request();
            Double sell = com.butcher.market.Bazaar.sell(item.id());
            if (sell != null && sell > 0) price = Math.round(sell * e.count());
        }
        String line = CropFever.pretty(e.tier()) + " · " + (price > 0 ? CorpseValue.coins(price) + " coins" : "Crop Fever")
                + (test ? " (test)" : "");
        DropArt art = item.model() == null ? DropArt.model("minecraft:paper")
                : item.glint() ? DropArt.glinting(item.model()) : DropArt.model(item.model());
        DropPopup.showCrop(CropFever.label(e), e.tier().colour, line, e.tier().intensity, art);
    }

    /** A sample Crop Fever drop of {@code tier} (random if null), from the wiki's table. */
    public static void testCrop(CropFever.Tier tier) {
        java.util.Random rng = new java.util.Random();
        if (tier == null) tier = CropFever.Tier.values()[rng.nextInt(4)];
        String[][] rows = {
                {"Enchanted Wheat", "6", "Enchanted Hay Bale", "1"},
                {"Enchanted Carrot", "12", "Enchanted Golden Carrot", "2"},
                {"Enchanted Pumpkin", "110", "Polished Pumpkin", "1"},
                {"Mutant Nether Wart", "2", "Compacted Wild Rose", "1"}};
        String[] row = rows[tier.ordinal()];
        int k = rng.nextInt(2);
        CropFever.Event e = new CropFever.Event(CropFever.Type.DROP, 0, tier, Integer.parseInt(row[k * 2 + 1]), CropFever.item(row[k * 2]));
        ButcherClient.decisions.note("§7Test: Crop Fever - " + tier.word + " " + CropFever.label(e));
        showCrop(e, true);
    }

    // ================================================================== the Garden's visitors: the gift

    private static final GardenVisitors.Watcher gardenWatcher = new GardenVisitors.Watcher(new GardenVisitors.Watcher.Listener() {
        @Override
        public void arrived(String name, int colour) {
            // The arrival notice of the fork the gift came from is left out (the user, 2026-10-26).
        }

        @Override
        public void accepted(GardenVisitors.Offer offer) {
            PatternConfig c = config();
            if (c == null || !c.gardenGift) return;
            offerAccepted(offer, false);
        }
    });

    /** An accepted offer waiting on its rewards' prices (never longer than {@link #GIFT_WAIT_MS}). */
    private record GiftPending(GardenVisitors.Offer offer, long deadline, boolean test, Set<String> asked) {
    }

    private static final long GIFT_WAIT_MS = 5000;
    private static final List<GiftPending> giftPending = new ArrayList<>();

    private static void offerAccepted(GardenVisitors.Offer o, boolean test) {
        if (o.items().isEmpty()) {
            ButcherClient.decisions.note("§7Garden: offer accepted with " + o.visitor() + " - no item in its rewards");
            return;
        }
        PatternConfig c = config();
        if (c == null || c.livePrices) com.butcher.market.Bazaar.request();
        giftPending.add(new GiftPending(o, System.currentTimeMillis() + GIFT_WAIT_MS, test, new HashSet<>()));
        giftTick(System.currentTimeMillis());
    }

    /** Each pending offer: its best reward's worth, once known - the gift if it is enough. */
    private static void giftTick(long now) {
        if (giftPending.isEmpty()) return;
        PatternConfig c = config();
        boolean live = c == null || c.livePrices;
        long min = c == null ? 5_000_000L : Math.max(0L, c.gardenGiftMin);
        for (Iterator<GiftPending> it = giftPending.iterator(); it.hasNext(); ) {
            GiftPending gp = it.next();
            boolean unresolved = false;
            long bestWorth = 0;
            GardenVisitors.Item best = null, always = null;
            for (GardenVisitors.Item item : gp.offer().items()) {
                GardenVisitors.Reward r = GardenVisitors.reward(item.name());
                if (r.always() && always == null) always = item;
                long unit = 0;
                for (String pid : r.priceIds()) {
                    Double sell = live ? com.butcher.market.Bazaar.sell(pid) : null;
                    if (sell != null && sell > 0) {
                        unit = Math.round(sell);
                        break;
                    }
                    if (!live) continue;
                    if (com.butcher.market.Bazaar.pending()) {
                        unresolved = true;
                        continue;
                    }
                    Long bin = com.butcher.market.LivePrice.cached(pid);
                    if (bin != null && bin > 0) {
                        unit = bin;
                        break;
                    }
                    if (gp.asked().add(pid)) com.butcher.market.LivePrice.request(pid);
                    if (com.butcher.market.LivePrice.pending(pid)) unresolved = true;
                }
                long worth = unit * Math.max(1, item.count());
                if (worth > bestWorth) {
                    bestWorth = worth;
                    best = item;
                }
            }
            if (best != null && (bestWorth >= min || gp.test())) {
                it.remove();
                showGift(gp.offer(), best, bestWorth, gp.test());
            } else if (!unresolved || now >= gp.deadline()) {
                it.remove();
                if (always != null && (best == null || gp.test())) showGift(gp.offer(), always, 0, gp.test());
                else if (gp.test() && !gp.offer().items().isEmpty()) showGift(gp.offer(), gp.offer().items().get(0), 0, true);
                else ButcherClient.decisions.note("§7Garden: " + gp.offer().visitor() + "'s rewards"
                        + (best == null ? " have no price" : " - best " + best.name() + " " + CorpseValue.coins(bestWorth))
                        + " - under " + CorpseValue.coins(min) + ", no gift");
            }
        }
    }

    private static void showGift(GardenVisitors.Offer o, GardenVisitors.Item item, long worth, boolean test) {
        GardenVisitors.Reward r = GardenVisitors.reward(item.name());
        String name = (item.count() > 1 ? item.count() + "x " : "") + r.name();
        String line = (worth > 0 ? CorpseValue.coins(worth) + " coins" : "Garden visitor") + " · from " + o.visitor()
                + (test ? " (test)" : "");
        // 5M is the start, 200M and up all of it.
        float intensity = worth <= 0 ? 0.9f
                : Math.max(0.55f, Math.min(1f, 0.55f + 0.45f * (float) ((Math.log10(worth) - Math.log10(5e6)) / (Math.log10(2e8) - Math.log10(5e6)))));
        ButcherClient.decisions.note("§aGarden: " + name + " from " + o.visitor() + (worth > 0 ? " - " + CorpseValue.coins(worth) : "")
                + " - the gift");
        if (!test) {
            ButcherStats.get().onSourceTally("garden", r.name());
            if (worth > 0) ButcherStats.get().onCoins(worth);
        }
        DropPopup.showGarden(name, r.colour(), o.colour() < 0 ? 0xFFFFFF : o.colour(), line, intensity, art(r));
    }

    /** A reward's look: its NEU head, its pack or vanilla model, a book's glint. */
    static DropArt art(GardenVisitors.Reward r) {
        if (r.head() && r.art() != null) return DropArt.head("garden", r.art());
        if (r.art() == null) return DropArt.model("minecraft:paper");
        return r.glint() ? DropArt.glinting(r.art()) : DropArt.model(r.art());
    }

    private static final String[][] TEST_VISITORS = {{"Jacob", "a"}, {"Wizard", "a"}, {"Madame Eleanor Q. Goldsworth III", "9"},
            {"Jerry", "6"}, {"Spaceman", "c"}, {"Sirius", "d"}};

    /** The gift, for a test: {@code item} or a Copper Dye, from a random visitor. */
    public static void testGift(String item) {
        String[] pick = TEST_VISITORS[new java.util.Random().nextInt(TEST_VISITORS.length)];
        String n = item == null || item.isBlank() ? "Copper Dye" : item.trim();
        int colour = GardenVisitors.codeColourOf(pick[1].charAt(0));
        var o = new GardenVisitors.Offer(pick[0], colour, GardenVisitors.rarityOf(colour), List.of(new GardenVisitors.Item(n, 1)));
        ButcherClient.decisions.note("§7Test: the gift - " + n + " from " + pick[0]);
        offerAccepted(o, true);
    }

    /** An Automaton part, for a test: {@code name} or a random one. */
    public static void testAutomaton(String name) {
        AutomatonDrops.Part part = null;
        if (name != null) for (AutomatonDrops.Part p : AutomatonDrops.PARTS) if (p.id().equalsIgnoreCase(name)) part = p;
        if (part == null) part = AutomatonDrops.PARTS.get(new java.util.Random().nextInt(AutomatonDrops.PARTS.size()));
        DropPopup.showAutomaton(part.name(), AutomatonDrops.COLOUR, "1% · Automaton (test)", 0.5f, part.art());
    }
}
