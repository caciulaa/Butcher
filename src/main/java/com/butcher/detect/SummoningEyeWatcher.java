package com.butcher.detect;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * A Summoning Eye from a Special Zealot, for the small pop-up (ported from a viewer's fork,
 * 2026-10-26).
 *
 * The eye is the Special Zealot's common loot - always dropped, so Hypixel says nothing in chat when it comes (the wiki,
 * Special Zealot). What it does say is when one spawns for you: "A special Zealot has spawned nearby!". So that line arms
 * this, and while armed the inventory is watched: the moment it holds one more Summoning Eye than it did (with no menu
 * open, so it is not one taken out of a chest), that is the eye. A Voidgloom's eye is The Seraph's to show, not this.
 */
public final class SummoningEyeWatcher {

    public interface Listener {
        /** An eye came in; {@code awakened} for the 4% Awakened Summoning Eye. */
        void eye(boolean awakened);
    }

    private final DecisionLog log;
    private final Listener listener;
    /** How long after the spawn line an eye still counts as the Special Zealot's. */
    static final long ARMED_MS = 120_000;
    public static final String SPAWN_LINE = "A special Zealot has spawned nearby!";

    private long armedUntil;
    private int lastEyes = -1, lastAwakened = -1;

    public SummoningEyeWatcher(DecisionLog log, Listener listener) {
        this.log = log;
        this.listener = listener;
    }

    public void onChatLine(String raw, long now) {
        if (raw == null) return;
        String s = raw.replaceAll("§.", "").trim();
        if (s.equals(SPAWN_LINE)) {
            armedUntil = now + ARMED_MS;
            if (log != null) log.note("§7Summoning Eye: a Special Zealot of yours - watching for its eye");
        }
    }

    public boolean armed(long now) {
        return now < armedUntil;
    }

    /** Arms it as the spawn line would, for the test. */
    public void arm(long now) {
        armedUntil = now + ARMED_MS;
    }

    /** Every client tick: counts the eyes while armed. {@code menuOpen}: a screen is up, so a change is the player's own. */
    public void tick(LocalPlayer player, boolean menuOpen, long now) {
        if (player == null || now >= armedUntil) {
            lastEyes = lastAwakened = -1;
            return;
        }
        int eyes = 0, awakened = 0;
        NonNullList<ItemStack> items = player.getInventory().getNonEquipmentItems();
        for (ItemStack stack : items) {
            if (stack == null || stack.isEmpty()) continue;
            CustomData data = stack.getComponents().get(DataComponents.CUSTOM_DATA);
            if (data == null) continue;
            CompoundTag tag = data.copyTag();
            String id = tag.getString("id").orElse(null);
            if (id == null || !id.contains("SUMMONING_EYE")) continue;
            if (id.contains("AWAKENED")) awakened += stack.getCount();
            else eyes += stack.getCount();
        }
        boolean more = lastEyes >= 0 && (eyes > lastEyes || awakened > lastAwakened);
        if (more && !menuOpen) {
            boolean aw = awakened > lastAwakened;
            armedUntil = 0;
            if (log != null) log.note("§aSummoning Eye: " + (aw ? "an Awakened one" : "one") + " came in - the small pop-up");
            listener.eye(aw);
        }
        lastEyes = eyes;
        lastAwakened = awakened;
    }
}
