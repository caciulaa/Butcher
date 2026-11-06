package com.butcher.render;

/**
 * How a drop is drawn, read off the NEU repo's item: a head wearing its own skin (bundled in a {@link HeadSkins} set,
 * keyed by NEU ID), or the item model the game draws it with - Hypixel's pack's ("hypixel_skyblock:item/...") or a vanilla
 * one ("minecraft:diamond" for the Scythe Blade, which is what it really is) - and whether it glints. Pure, so the
 * timelines and the offline tools can carry it.
 *
 * There are no stand-ins: until a head's skin has downloaded it is still a head (the game's own player head item, which
 * fetches the skin itself), and a pack item is still that item (paper wearing its model, which is what Hypixel sends).
 */
public record DropArt(String headSet, String head, String model, boolean glint) {

    /** A head of {@code set} ("revtara", "garden", "slayer"...), keyed {@code key}. */
    public static DropArt head(String set, String key) {
        return new DropArt(set, key, null, false);
    }

    /** An item model, as the NEU repo gives it. */
    public static DropArt model(String model) {
        return new DropArt(null, null, model, false);
    }

    /** An item model with the enchantment glint (an enchanted crop, a book, a glinting reforge stone). */
    public static DropArt glinting(String model) {
        return new DropArt(null, null, model, true);
    }

    public boolean isHead() {
        return head != null;
    }

    /** Hypixel's pack's item, whose flat texture can be drawn by the scene itself, spinning a texel thick. */
    public boolean packItem() {
        return model != null && model.startsWith(PACK_ITEM);
    }

    /** "hypixel_skyblock:textures/item/....png" for a pack item, else null. */
    public String packTexture() {
        return packItem() ? "hypixel_skyblock:textures/item/" + model.substring(PACK_ITEM.length()) + ".png" : null;
    }

    public static final String PACK_ITEM = "hypixel_skyblock:item/";
}
