package com.butcher;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * "Report a bug or suggest something" (the user, 2026-11-01): the menu's button, {@code /butcher report} and
 * {@code /butcher feedback}. A bug report is only useful with the mod's own record of what it decided, so it always
 * saves the report file first, copies the report to the clipboard, and sends the player to the GitHub issue forms
 * (the repo's {@code .github/ISSUE_TEMPLATE}: a bug form with a box to paste the report into, and a suggestion form).
 * The repo is private until the user makes it public; until then only they can open the forms.
 */
public final class Feedback {
    private Feedback() {}

    /** Where bug reports and suggestions go: the repo's issue forms. */
    public static final String URL = "https://github.com/caciulaa/butcher/issues/new/choose";
    public static final String BUG_URL = "https://github.com/caciulaa/butcher/issues/new?template=bug_report.yml";
    public static final String IDEA_URL = "https://github.com/caciulaa/butcher/issues/new?template=suggestion.yml";
    /** GitHub takes an issue body of 65,536 characters; a longer report goes in as the file. */
    private static final int PASTE_MAX = 60_000;

    /** From the menu's button: the report, the clipboard, and the forms in the browser (no chat needed - it may be the title screen). */
    public static void fromMenu() {
        Path file = ButcherClient.writeReport();
        boolean copied = copy(file);
        try {
            net.minecraft.util.Util.getPlatform().openUri(URL);
        } catch (Throwable t) {
            ButcherClient.LOGGER.warn("[Butcher] could not open the feedback page", t);
        }
        say(file, copied);
    }

    /** {@code /butcher report} and {@code /butcher feedback}: the report, the clipboard, and clickable lines in chat. */
    public static void tell(Path file) {
        say(file, copy(file));
    }

    /** Puts the report on the clipboard to paste into the bug form; false if it is missing or too long to paste. */
    static boolean copy(Path file) {
        if (file == null) return false;
        try {
            String text = Files.readString(file);
            if (text.length() > PASTE_MAX) return false;
            Minecraft.getInstance().keyboardHandler.setClipboard(text);
            return true;
        } catch (Exception e) {
            ButcherClient.LOGGER.warn("[Butcher] could not copy the report", e);
            return false;
        }
    }

    private static void say(Path file, boolean copied) {
        if (Minecraft.getInstance().player == null) return;
        if (file == null) {
            say(ButcherClient.tagged("§cThe report could not be written - see the game log."));
            return;
        }
        say(ButcherClient.tagged("§7Saved a report of what Butcher has been doing: ").append(Component.literal(
                "§f§n" + file.getFileName()).withStyle(st -> st.withClickEvent(new ClickEvent.OpenFile(file.getParent())))));
        say(Component.literal(copied ? "§a  Copied to your clipboard §7- paste it into the bug form."
                : "§7  Too long to paste - drag the file into the bug form instead (click its name for the folder)."));
        say(Component.literal("  ").append(link("§c§n[Report a bug]", BUG_URL)).append(Component.literal("   "))
                .append(link("§b§n[Suggest something]", IDEA_URL)));
    }

    private static Component link(String text, String url) {
        return Component.literal(text).withStyle(st -> st.withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(url))));
    }

    private static void say(Component c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(c);
    }
}
