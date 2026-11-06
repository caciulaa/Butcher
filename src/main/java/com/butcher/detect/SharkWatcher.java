package com.butcher.detect;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * The player's own Great White Sharks: what each one dropped, for Fin.
 *
 * Settled against a real Fishing Festival (the user's records and log, 2026-10-21):
 * <ul>
 *   <li><b>Ours</b>: a Great White Shark nametag first seen within {@link #CATCH_REACH} blocks from
 *       {@link #CATCH_BEFORE_MS} before to {@link #CATCH_AFTER_MS} after the catch line, "Hide no longer, a Great
 *       White Shark has tracked your scent and thirsts for your blood!". Only that line (or a mod's "... Great White
 *       Shark has spawned!"): matched loosely, "[Bazaar] Sold 2x Great White Shark Tooth" counted as a catch. Right
 *       after "It's a Double Hook!" one line can bring two, so it claims two.
 *   <li><b>Dead</b>: its health reads 0, or the nametag goes while at half health or less, close by, seen a few
 *       ticks, and the player did not teleport (SkyMaxx's 0.5).
 *   <li><b>The Megalodon</b> has its own line ("PET DROP! Megalodon") and plays from it (SharkDrops).
 *   <li><b>The tooth has no line at all.</b> It lands in the inventory (mostly - the user, 2026-10-22) and it
 *       cannot go into a sack (the user, 2026-10-27) - so a {@code GREAT_WHITE_SHARK_TOOTH} count rising is the drop
 *       ({@link #tick}). It is shown the tick it lands, while one of the player's sharks is alive or a kill waits:
 *       it often comes the tick before the kill is noticed, and waiting for the kill made it late. That kill then
 *       finds the tooth already shown and plays no bust.
 *   <li><b>A bust</b>: no tooth in {@link #TOOTH_WAIT_MS} after the kill.
 * </ul>
 */
public final class SharkWatcher {

    public interface Listener {
        /** One of the player's own Great White Sharks dropped nothing worth announcing. */
        void bust();

        /** One of the player's own Great White Sharks dropped a Great White Shark Tooth. */
        void tooth();
    }

    private final DecisionLog log;
    private final Listener listener;

    public SharkWatcher(DecisionLog log, Listener listener) {
        this.log = log;
        this.listener = listener;
    }

    static final String NAME = "Great White Shark";
    private static final double SCAN_RADIUS = 48, CATCH_REACH = 16, VANISH_RADIUS = 32, TELEPORT = 8;
    private static final long CATCH_BEFORE_MS = 2_000, CATCH_AFTER_MS = 4_000, DOUBLE_HOOK_MS = 2_000;
    private static final long DROP_BEFORE_MS = 1_500;
    /** How long a kill waits for its tooth; how long a tooth already shown before the kill still answers for it. */
    static final long TOOTH_WAIT_MS = 2_500, TOOTH_EARLY_MS = 3_000;
    private static final double VANISH_HP_FRACTION = 0.5;
    private static final int MIN_TICKS = 6;
    private static final String TOOTH_ID = "GREAT_WHITE_SHARK_TOOTH";

    private static final class Shark {
        int id;
        long firstSeen;
        boolean owned, dead;
        int ticksSeen;
        double hp = -1, maxHp = -1, distSq;
        String name = "";
    }

    private final Map<Integer, Shark> sharks = new HashMap<>();
    /** Catch lines not yet matched to a nametag (a double hook leaves two). */
    private final java.util.ArrayDeque<Long> catches = new java.util.ArrayDeque<>();
    private long lastDropAt = -1, doubleHookAt = -1;
    /** Deaths waiting for their drop: when each died. */
    private final java.util.ArrayDeque<Long> pending = new java.util.ArrayDeque<>();
    /** Teeth shown before their kill was noticed: when each came. The kill takes one instead of waiting. */
    private final java.util.ArrayDeque<Long> shownEarly = new java.util.ArrayDeque<>();
    private Vec3 lastPos;
    private int teethHeld = -1;

    /** Every game chat line. */
    public void onChatLine(String text) {
        onChatLine(text, System.currentTimeMillis());
    }

    public void onChatLine(String text, long now) {
        if (text == null) return;
        String s = DianaWatcher.plain(text).strip();
        if (SharkDrops.of(s) != null) {
            lastDropAt = now;
            // A pet line answers for the kill just before it.
            if (!pending.isEmpty() && now - pending.peekLast() <= 3_000) {
                pending.pollLast();
                log.note("§7Fishing: the drop came - no bust");
            }
            return;
        }
        if (s.startsWith("It's a Double Hook")) {
            doubleHookAt = now;
            return;
        }
        if (isCatch(s)) {
            boolean twice = doubleHookAt >= 0 && now - doubleHookAt <= DOUBLE_HOOK_MS;
            catches.addLast(now);
            if (twice) catches.addLast(now);
            while (catches.size() > 4) catches.pollFirst();
            log.note("§7Fishing: caught a Great White Shark" + (twice ? " (a double hook: two may come)" : "") + " - \"" + s + "\"");
            // A nametag that showed up just before the line is claimed now.
            for (Shark sh : sharks.values()) claim(sh);
        }
    }

    /** Hypixel's catch line, or a mod's rewrite of it. Never a trade, a sale or anyone's chat. */
    public static boolean isCatch(String s) {
        if (s.startsWith("Hide no longer, a Great White Shark")) return true;
        return s.contains(NAME) && s.contains("has spawned") && !s.contains(":") && !s.startsWith("[")
                && !s.contains("Sold") && !s.contains("Bought");
    }

    public void tick(LocalPlayer player) {
        long now = System.currentTimeMillis();
        scan(player, now);
        // A tooth in the inventory: shown now if one of our sharks is alive or a kill waits for it.
        int held = player == null ? -1 : teethIn(player);
        if (held >= 0 && teethHeld >= 0 && held > teethHeld && Minecraft.getInstance().gui.screen() == null) {
            for (int k = held - teethHeld; k > 0; k--) gained(now);
        }
        teethHeld = held;
        while (!shownEarly.isEmpty() && now - shownEarly.peekFirst() > TOOTH_EARLY_MS) shownEarly.pollFirst();
        // No tooth in time: a bust.
        while (!pending.isEmpty() && now - pending.peekFirst() > TOOTH_WAIT_MS) {
            pending.pollFirst();
            log.note("§aFishing: your Great White Shark dropped nothing - bust");
            listener.bust();
        }
        while (!catches.isEmpty() && now - catches.peekFirst() > CATCH_AFTER_MS + 1000) catches.pollFirst();
    }

    private void gained(long now) {
        if (!pending.isEmpty()) {
            pending.pollFirst();
            log.note("§aFishing: your Great White Shark dropped a tooth");
            listener.tooth();
            return;
        }
        boolean fighting = false;
        for (Shark sh : sharks.values()) fighting |= sh.owned && !sh.dead;
        if (!fighting) {
            log.note("§8Fishing: a Great White Shark Tooth came in - no shark of yours alive or just killed");
            return;
        }
        // Before the kill shows: the tooth is the drop. Its kill takes this instead of waiting.
        shownEarly.addLast(now);
        log.note("§aFishing: your Great White Shark dropped a tooth (before its kill showed)");
        listener.tooth();
    }

    /** Each slot's SkyBlock data, read once per stack object rather than copied every tick. */
    private final InventoryData slots = new InventoryData();

    private int teethIn(LocalPlayer player) {
        int n = 0;
        var items = player.getInventory().getNonEquipmentItems();
        for (int slot = 0; slot < items.size(); slot++) {
            net.minecraft.world.item.ItemStack stack = items.get(slot);
            if (stack == null || stack.isEmpty()) continue;
            net.minecraft.nbt.CompoundTag data = slots.of(slot, stack);
            if (data != null && TOOTH_ID.equals(data.getString("id").orElse(null))) n += stack.getCount();
        }
        return n;
    }

    private void scan(LocalPlayer player, long now) {
        ClientLevel level = Minecraft.getInstance().level;
        if (player == null || level == null) {
            sharks.clear();
            lastPos = null;
            return;
        }
        Vec3 pos = player.position();
        boolean teleported = lastPos != null && pos.distanceToSqr(lastPos) > TELEPORT * TELEPORT;
        lastPos = pos;

        Set<Integer> seen = new HashSet<>();
        AABB box = AABB.ofSize(pos, SCAN_RADIUS * 2, SCAN_RADIUS * 2, SCAN_RADIUS * 2);
        for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, box)) {
            Component custom = stand.getCustomName();
            if (custom == null) continue;
            String name = custom.getString();
            if (!name.contains(NAME)) continue;
            seen.add(stand.getId());
            Shark sh = sharks.get(stand.getId());
            if (sh == null) {
                sh = new Shark();
                sh.id = stand.getId();
                sh.firstSeen = now;
                sh.distSq = stand.distanceToSqr(player);
                sh.name = name;
                sharks.put(sh.id, sh);
                claim(sh);
                record("seen", sh);
            }
            sh.distSq = stand.distanceToSqr(player);
            sh.name = name;
            sh.ticksSeen++;
            Double hp = DianaWatcher.hpOf(name);
            if (hp != null) {
                sh.hp = hp;
                sh.maxHp = Math.max(sh.maxHp, hp);
            }
            if (!sh.dead && hp != null && hp <= 0) {
                sh.dead = true;
                died(sh, now, "its health reads 0");
            }
        }
        for (Iterator<Shark> it = sharks.values().iterator(); it.hasNext(); ) {
            Shark sh = it.next();
            if (seen.contains(sh.id)) continue;
            it.remove();
            if (sh.dead || !sh.owned) continue;
            String why = null;
            if (teleported) why = "you teleported";
            else if (sh.ticksSeen < MIN_TICKS) why = "it was only seen " + sh.ticksSeen + " ticks";
            else if (sh.hp < 0 || sh.maxHp <= 0) why = "its health was never read";
            else if (sh.hp > sh.maxHp * VANISH_HP_FRACTION) why = "it was still at " + Math.round(100 * sh.hp / sh.maxHp) + "%";
            else if (sh.distSq > VANISH_RADIUS * VANISH_RADIUS) why = "it was " + Math.round(Math.sqrt(sh.distSq)) + " blocks away";
            if (why != null) {
                log.note("§8Fishing: your Great White Shark's nametag went - not a kill: " + why);
                continue;
            }
            sh.dead = true;
            died(sh, now, "its nametag went at " + Math.round(100 * sh.hp / sh.maxHp) + "% health");
        }
    }

    /** Ours if it first appeared close by around a catch line. */
    private void claim(Shark sh) {
        if (sh.owned || sh.dead) return;
        for (Iterator<Long> it = catches.iterator(); it.hasNext(); ) {
            long c = it.next();
            if (sh.firstSeen < c - CATCH_BEFORE_MS || sh.firstSeen > c + CATCH_AFTER_MS) continue;
            if (sh.distSq > CATCH_REACH * CATCH_REACH) continue;
            it.remove();
            sh.owned = true;
            log.note("§7Fishing: claimed your Great White Shark (\"" + sh.name + "\")");
            return;
        }
    }

    private void died(Shark sh, long now, String how) {
        record("died", sh);
        if (!sh.owned) {
            log.note("§8Fishing: a Great White Shark died, not yours - " + how);
            return;
        }
        if (lastDropAt >= 0 && now - lastDropAt <= DROP_BEFORE_MS) {
            log.note("§7Fishing: your Great White Shark died - " + how + "; its drop already showed");
            return;
        }
        if (!shownEarly.isEmpty()) {
            shownEarly.pollFirst();
            log.note("§7Fishing: your Great White Shark died - " + how + "; its tooth already showed");
            return;
        }
        log.note("§7Fishing: your Great White Shark died - " + how + "; waiting for its drop");
        pending.addLast(now);
    }

    private static void record(String event, Shark sh) {
        JsonObject o = new JsonObject();
        o.addProperty("event", event);
        o.addProperty("owned", sh.owned);
        o.addProperty("name", sh.name);
        o.addProperty("distance", Math.round(Math.sqrt(sh.distSq)));
        DropRecorder.record("fishing_mob", o);
    }
}
