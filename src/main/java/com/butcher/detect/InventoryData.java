package com.butcher.detect;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * The SkyBlock data (CUSTOM_DATA: "id", "petInfo"...) of each inventory slot, read once per stack.
 *
 * Reading it means copying the item's whole tag, and the inventory watchers did that for every slot on every tick -
 * well over a thousand copies a second between them. A slot's stack object only changes when the server changes
 * what is in it, so the copy is kept until a different object turns up there; the count is still read fresh each tick
 * by the caller.
 */
final class InventoryData {
    private static final int SLOTS = 64;
    private final ItemStack[] stacks = new ItemStack[SLOTS];
    private final CompoundTag[] data = new CompoundTag[SLOTS];

    /** The slot's SkyBlock data, or null for an item without any. */
    CompoundTag of(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOTS) return read(stack);
        if (stacks[slot] != stack) {
            stacks[slot] = stack;
            data[slot] = read(stack);
        }
        return data[slot];
    }

    void clear() {
        java.util.Arrays.fill(stacks, null);
        java.util.Arrays.fill(data, null);
    }

    private static CompoundTag read(ItemStack stack) {
        CustomData custom = stack.getComponents().get(DataComponents.CUSTOM_DATA);
        return custom == null ? null : custom.copyTag();
    }
}
