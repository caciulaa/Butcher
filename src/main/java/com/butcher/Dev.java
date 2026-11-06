package com.butcher;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;

/**
 * Who may use the developer commands: {@code /butcher test} and {@code /butcher debug}.
 *
 * Everyone else gets the game's own "Unknown or incomplete command" for them, and they are left out
 * of tab completion, as if they did not exist. Players preview the cutscenes with the menu's "Try it"
 * rows instead, which play the same thing.
 *
 * This is a tidy-up, not security: it is a client mod, and the commands only affect the game they run in.
 * A development run ({@code gradlew runClient}) is always allowed; otherwise an empty file named
 * {@code config/butcher/dev} on that PC turns them on. A file rather than a list of accounts, so the
 * source and the jar name nobody.
 */
public final class Dev {
    private Dev() {}

    /** Whether this game is allowed the developer commands. */
    public static boolean allowed() {
        FabricLoader loader = FabricLoader.getInstance();
        return loader.isDevelopmentEnvironment()
                || Files.exists(loader.getConfigDir().resolve("butcher").resolve("dev"));
    }
}
