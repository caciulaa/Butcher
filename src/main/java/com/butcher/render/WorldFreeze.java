package com.butcher.render;

import com.butcher.ButcherClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * The world as it was at the Gauntlet's snap, for its dust (the user, 2026-10-28: the world crumbles away, then the
 * gauntlet). Read once, a quarter size, by the game's own screenshot path - {@code Screenshot.takeScreenshot} with a
 * downscale - at the moment the main render target holds the world and nothing else: {@code mixin/GameRendererMixin}
 * calls {@link #capture} just before the GUI is drawn, as it does for the gameplay window. The pixels land in
 * {@link GauntletScene#world} when the read comes back; until then the scene draws the void alone. The frame it runs on
 * is the flash's, so a hitch there is never seen.
 */
public final class WorldFreeze {
    private WorldFreeze() {}

    private static volatile boolean wanted;

    /** Asks for the world on the next frame (the snap). */
    public static void request() {
        wanted = true;
    }

    /** Lets go of the frozen world (a cutscene closing). */
    public static void clear() {
        wanted = false;
        GauntletScene.world = null;
    }

    /** From the mixin, before the GUI: reads the world if it was asked for. */
    public static void capture() {
        if (!wanted) return;
        wanted = false;
        try {
            Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), 4, img -> {
                try {
                    int w = img.getWidth(), h = img.getHeight();
                    int[] abgr = img.getPixelsABGR();
                    int[] argb = new int[w * h];
                    for (int i = 0; i < argb.length && i < abgr.length; i++) {
                        int p = abgr[i];
                        argb[i] = 0xFF000000 | (p & 0xFF) << 16 | (p & 0xFF00) | (p >> 16 & 0xFF);
                    }
                    GauntletScene.worldW = w;
                    GauntletScene.worldH = h;
                    GauntletScene.world = argb;
                } finally {
                    img.close();
                }
            });
        } catch (Throwable t) {
            ButcherClient.LOGGER.warn("[Butcher] could not read the world for the Gauntlet's dust", t);
        }
    }
}
