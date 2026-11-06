package com.butcher.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Settings for the "/butcher" menu, stored as JSON at
 * "config/butcher.json". Edit this file and run "/butcher reload"
 * in-game to pick up changes without restarting Minecraft (or use "/butcher"
 * to open the in-game menu for the same settings).
 *
 * Drop detection itself is NOT chat-based (an earlier version of this mod
 * tried regex-matching chat lines, but there is no chat broadcast for a
 * Scatha kill or its drop at all - confirmed by decompiling Scatha-Pro's own
 * jar). See {@link com.butcher.detect.ScathaWormTracker} and
 * {@link com.butcher.detect.HotbarDropWatcher} for how it actually
 * works: nearby armor-stand nametag scanning for the kill, then inventory
 * item NBT diffing for the drop.
 */
public class PatternConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("butcher.json");
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Butcher");

    /** Total length of the guillotine cutscene, in milliseconds. */
    public int animationDurationMs = 2600;

    /**
     * Length of a FULL five-cut cleaver cutscene, in milliseconds. Every phase of
     * that animation - the slide-in, each wind-up, fall and impact, and the reveal -
     * is scaled by the same factor, so moving this only ever changes the pace, never
     * the shape. Runs that end in fewer cuts are proportionally shorter.
     */
    public int scopeDurationMs = 7600;

    /** Which drop animation (if any) plays - only one can be active at a time. */
    public enum AnimationMode { NONE, GUILLOTINE, SCOPE }

    /** Which drop animation is active, toggled from the /butcher menu. Turning one on turns the other off. */
    public AnimationMode animationMode = AnimationMode.SCOPE;

    /**
     * The style to come back to when Scatha Gambling is switched off and on again.
     * The menu presents "on/off" and "which cutscene" as two separate rows, but the
     * setting behind them is one enum, so the chosen style has to be parked
     * somewhere while the mode itself sits at NONE.
     */
    public AnimationMode lastStyle = AnimationMode.SCOPE;

    /**
     * Cover the hotbar, held item and chat once a nearby Scatha is at or below this
     * many hearts, so a drop can't be spotted before the cutscene reveals it. A
     * Scatha's nametag counts down from 10, so this is 1-10.
     */
    public int hideBelowHp = 3;

    /** Set off a real firework, coloured by the pet's rarity, as a pet-drop cutscene closes. */
    public boolean petDropFirework = true;

    /** Play the Scatha-head popup - vanilla's totem-of-undying animation, 2 seconds - as a
     *  pet-drop cutscene closes. Its length is fixed by the game, so there's no timing knob. */
    public boolean petHeadPopup = true;

    /**
     * Refuse the game's own sounds while a cutscene - or the pre-kill hide - owns the
     * screen.
     *
     * On by default because a sound cue spoils the reveal the same way a chat line does:
     * Hypixel's drop chime can land while the cleaver is still working up the body, and
     * then the number of chops no longer hides anything. A handful of mining noises are
     * dropped rather than queued for the few seconds this lasts.
     */
    public boolean silenceGameAudio = true;

    /**
     * Play the cutscene's own synthesised sound - the swing, the impacts, the reveal
     * chime.
     *
     * Generated at runtime rather than shipped as files, and written to its own audio
     * line, so it is unaffected by {@link #silenceGameAudio} above. It follows
     * Minecraft's master volume slider, and turning that to zero silences it too.
     */
    public boolean cutsceneAudio = true;

    /**
     * Play the Thaw when a Vanguard Frozen Corpse is opened.
     *
     * Separate from {@code animationMode}, which is the Scatha's. They are different
     * sources with different detection and different cutscenes, and someone who wants one
     * has no particular reason to want the other.
     */
    public boolean vanguardAnimation = true;

    /**
     * Keep the corpse's loot message out of chat while the Thaw plays it back.
     *
     * The message IS the reveal - ten lines of it, arriving before the animation has
     * drawn a single frame. Leaving it in chat hands the player the answer and turns the
     * cutscene into a replay of something they have already read.
     */
    public boolean vanguardHideChat = true;

    /**
     * How fast the Thaw plays, in percent. 100 is the reference pace; 200 is twice as fast.
     *
     * Stretches every part of the run evenly - wind-ups, strikes, endings - so it changes
     * how long the Thaw takes and never its shape. A losing corpse still ends quicker than
     * a winning one at any speed.
     */
    public int vanguardSpeed = 100;

    /**
     * Whether the ice flares a colour before each strike.
     *
     * The flare is a tell - honest about real wins, lying about everything else - and some
     * players will find that exciting and others will find it noise. Off, every strike
     * winds up plain and the payoff is a surprise every time.
     */
    public boolean vanguardFlare = true;

    /** How big a drop's name is drawn in the Thaw, in percent of the default. */
    public int vanguardNameSize = 100;

    /**
     * Volume of the Thaw's two big endings - the legendary detonation and the Dye's flood -
     * in percent. Separate from the Scatha's pet sting, because they are different moments
     * and a player may want one loud and the other not.
     */
    public int vanguardFinaleVolume = 100;

    /**
     * Fetch live prices when a corpse is opened: the bazaar from Hypixel's public API, and
     * the lowest BIN for the Dye, the Locket and the Wisp from Coflnet. Only item IDs are
     * sent. Needed by the profit window and the Locket's price counter; off, the window is
     * not shown and the Locket shows its NPC price.
     */
    public boolean livePrices = true;

    /** Show the corpse's profit or loss, drop by drop, when the Thaw ends. */
    public boolean vanguardProfit = true;

    /**
     * Write every real drop event to config/butcher-drops.jsonl - the chat around it and the
     * detector's reasoning - so a rare drop that comes out wrong can be replayed. Local only.
     *
     * Off by default: it keeps chat lines (other players' too) in a file that is never pruned, which
     * is for someone helping with a bug report to switch on, not for everyone.
     */
    public boolean recordDrops = false;

    /**
     * The game itself in a small window in a corner of every cutscene (SkyMaxx's gameplay view):
     * 0 off, 1 top right, 2 top left, 3 bottom right, 4 bottom left.
     */
    public int gameplayCorner = 1;
    /** How big the gameplay window is, as a percentage of its default (a quarter of the screen's width). */
    public int gameplaySize = 100;
    /** How dark a cutscene makes the game behind it, as a percentage of each animation's own dim; 0 none. */
    public int backgroundDim = 100;
    /** How Butcher's own chat lines are tagged: 0 the "[Butcher]" text, 1 the mod's badge as a glyph. */
    public int chatStyle = 1;
    /**
     * With "Silence the game" on: true turns the game down to {@code QUIETER} instead of muting it, so a boss's or an
     * Inquisitor's sounds still reach the player through a cutscene (the review, 2026-10-25). Off by default: muted is
     * what keeps a drop chime from giving the reveal away.
     */
    public boolean gameAudioQuieter = false;
    /** Saves a screenshot when a big drop's showcase is on screen (Screenshots folder). Off by default. */
    public boolean autoScreenshot = false;
    /** The one-time welcome in chat has been shown. */
    public boolean welcomed = false;
    /** How many cutscenes have shown the "hold Backspace to skip" hint: the first three do. */
    public int skipHints = 0;

    /** Diana's cutscene, "The Chopping Block": Sphinx, Inquisitor, Manticore and King Minos kills, a Minos Relic, a Mythological Dye. */
    public boolean dianaAnimation = true;

    /** The Chopping Block's win sounds, 0-150%. */
    public int dianaVolume = 100;
    /** How fast the Chopping Block plays, in percent (50-200): the whole of it, picture and cues together. */
    public int dianaSpeed = 100;

    /** Which creatures' kills play it (the Champion's only when it drops a Relic), and the Dye from anything. */
    public boolean dianaSphinx = true, dianaInquisitor = true, dianaManticore = true, dianaKingMinos = true,
            dianaChampion = true, dianaDye = true;

    /** Every drop's own ending; off gives every drop the plain one, the drop rising out of the split. */
    public boolean dianaFullEndings = true;

    /** The "RARE DROP!" banner over the expensive drops' names. */
    public boolean dianaBanner = true;

    /** Each expensive ending's own music, building to the reveal. */
    public boolean dianaMusic = true;

    /** The drop's price, counting up under it. */
    public boolean dianaPrice = true;

    /** How big the Chopping Block is drawn, 60-140%. */
    public int dianaSize = 130;

    /**
     * The HUD and chat fade out once a creature that may be ours is at or below this many million
     * HP (DianaWatcher.armed - SkyMaxx's way). 0 turns it off: they go when the cutscene opens.
     */
    public int dianaHideBelowM = 3;
    /**
     * The pre-kill hide as a percentage of the creature's health (0.2.85; the review, A9): millions were wrong across
     * tiers and levels. Diana's, the Demonlord's and the Seraph's; lava's was a percentage already. The old million
     * fields are read once, by {@link #migrateHides}, and kept so an older jar still finds them.
     */
    public int dianaHidePct = 5, blazeHidePct = 7, seraphHidePct = 7;
    public boolean hidesInPercent = false;
    /** Once: the Chopping Block's default size went from 100% to 130% (the review, D1); an untouched 100 follows it. */
    public boolean dianaSizeRaised = false;

    /** Busts, per source: 0 the whole cutscene, 1 short (straight to the bust's own ending), 2 none (wins only). */
    public int dianaBusts = 0, blazeBusts = 0, seraphBusts = 0, lavaBusts = 0;
    /** Only play drops worth at least this ({@link #MIN_WORTH} index; 0 any): one under it plays as a bust. */
    public int minWorth = 0;
    public static final long[] MIN_WORTH = {0, 500_000, 1_000_000, 2_000_000, 5_000_000, 10_000_000, 25_000_000, 50_000_000, 100_000_000};
    /** A line in chat once each cutscene ends: what dropped, its price, which one of yours. */
    public boolean chatSummary = true;
    /** No screen shake, softer flashes - every animation. */
    public boolean calmEffects = false;
    /** "Quick Thaw": a corpse with nothing above common skips to its last strike. */
    public boolean vanguardQuick = false;
    /** "Wisp": a pop-up when a Ghost in the Dwarven Mines drops Sorrow or Plasma. */
    public boolean ghostAnnounce = true;
    public boolean ghostPrice = true;
    public int ghostVolume = 100;
    /** The Ledger: a stamp pops up over the game when one is earned (after any cutscene), and its sound's volume. */
    public boolean ledgerPopups = true;
    public int ledgerVolume = 100;
    /** How fast Wisp plays, in percent (50-200): the whole of it, picture and cues together. */
    public int ghostSpeed = 100;

    /** "Hellion": the cutscene on every kill of the player's own tier IV Inferno Demonlord. */
    public boolean blazeAnimation = true;
    /** The HUD and chat fade once your Demonlord is at or below this many million HP (0 off). Max 150M. */
    public int blazeHideBelowM = 10;
    /** Hellion's "PRAY TO RNGESUS DROP!" banner on the dice and the dye. */
    public boolean blazeBanner = true;
    /** Hellion's price under the drop. */
    public boolean blazePrice = true;
    /** Hellion's sounds, 0-150%. */
    public int blazeVolume = 100;
    /** How fast Hellion plays, in percent (50-200): the whole of it, picture and cues together. */
    public int blazeSpeed = 100;

    /** "The Seraph's Glyph": the Voidgloom Seraph's cutscene (tier IV; detection still waits on real records). */
    public boolean seraphAnimation = true;
    /** The HUD and chat fade once your Seraph is at or below this many million HP (0 off). Max 210M. */
    public int seraphHideBelowM = 15;
    public boolean seraphBanner = true;
    public boolean seraphPrice = true;
    /** The Seraph's sounds, 0-150%. */
    public int seraphVolume = 100;
    /** How fast the Seraph's Glyph plays, in percent (50-200): the whole of it, picture and cues together. */
    public int seraphSpeed = 100;

    /** "The Grave": every kill of the player's own tier V Revenant Horror (tier IV only for the Matcha Dye). */
    public boolean revAnimation = true;
    /** "The Cocoon": every kill of the player's own tier V Tarantula Broodfather (tier IV only for the Brick Red Dye). */
    public boolean taraAnimation = true;
    /** The HUD and chat fade once your boss is at or below this share of its health, % (0 off). */
    public int revHidePct = 5, taraHidePct = 5;
    public boolean revBanner = true, taraBanner = true, revPrice = true, taraPrice = true;
    /** Each one's own drop ending (off: every win gets the plain one). */
    public boolean revEndings = true, taraEndings = true;
    public int revVolume = 100, taraVolume = 100;
    public int revSpeed = 100, taraSpeed = 100;
    /** 0 the whole cutscene, 1 short, 2 none - as every slayer's. */
    public int revBusts = 0, taraBusts = 0;
    /** Their screen shake, % of the moderate default (0 off). */
    public int revShake = 100, taraShake = 100;
    /** The other animations' screen shake, % of what they were built with (0 off). The Scatha's is shakeStrength. */
    public int vanguardShake = 100, dianaShake = 100, blazeShake = 100, seraphShake = 100, lavaShake = 100;

    /** "The Vault": every Crystal Nucleus loot bundle (detect/NucleusWatcher). */
    public boolean nucleusAnimation = true;
    /** The Alloy's forge and the Jade Dye's and the Quick Claw's endings (off: they get the win's). */
    public boolean nucleusEndings = true;
    public boolean nucleusPrice = true;
    public int nucleusVolume = 100, nucleusSpeed = 100, nucleusShake = 100;
    /** Plain hauls (no drop worth 1.5M+): 0 the whole cutscene, 1 short, 2 none - the busts setting, by its name here. */
    public int nucleusPlain = 0;
    /** The Crystal Nucleus's style: 0 the Vault, 1 the Gauntlet (2026-10-28). */
    public int nucleusStyle = 0;

    /** "Eruption": lava fishing's cutscene on the rare Crimson Isle creatures (Jawbus, Thunder, Ragnarok, the Fiery Scuttler, Plhlegblast). */
    public boolean lavaAnimation = true;
    /** Per creature: whether its kills play (Plhlegblast only ever plays for the Carmine Dye). */
    public boolean lavaJawbus = true, lavaThunder = true, lavaRagnarok = true, lavaScuttler = true;
    /** Kills you only assisted (loot share) play too. */
    public boolean lavaLootShare = true;
    /** The HUD and chat fade once your creature is at or below this share of its health, % (0 off). */
    public int lavaHidePct = 4;
    public boolean lavaBanner = true;
    public boolean lavaPrice = true;
    /** Eruption's sounds, 0-150%. */
    public int lavaVolume = 100;
    /** How fast Eruption plays, in percent (50-200): the whole of it, picture and cues together. */
    public int lavaSpeed = 100;

    /** "Fin": the short pop-up when a Great White Shark drops a Megalodon pet or its tooth. */
    public boolean fishingAnnounce = true;
    /** Fin's bust: the fin ducks the cleaver when your Great White Shark dies with nothing. */
    public boolean fishingBust = true;
    /** Fin's price under the drop (George's for the pets). */
    public boolean fishingPrice = true;

    /** Fin's sounds, 0-150%. */
    public int fishingVolume = 100;
    /** How fast Fin plays, in percent (50-200): the whole of it, picture and cues together. */
    public int fishingSpeed = 100;

    /** The Pop-ups group: the Automaton's robot parts and the Special Zealot's Summoning Eye, each its own switch and volume. */
    public boolean automatonPopup = true, eyePopup = true;
    public int automatonVolume = 100, eyeVolume = 100;
    /** How fast the Automaton's, the Eye's and the Garden's pop-ups play, in percent (50-200). */
    public int popupSpeed = 100;

    /** The Garden: Crop Fever's glow round the screen while it lasts, and a pop-up for each of its drops. */
    public boolean gardenFever = true, gardenCropDrops = true;
    /** Leave Crop Fever's Uncommon drops out of the pop-ups. */
    public boolean gardenCropRareUp = false;
    /** The Garden: the gift pop-up when an accepted visitor offer's reward is worth {@link #gardenGiftMin} coins or more. */
    public boolean gardenGift = true;
    public long gardenGiftMin = 5_000_000L;
    /** The Garden's sounds, 0-150%. */
    public int gardenVolume = 100;

    /**
     * Take the vanilla HUD off the screen during the pre-kill hide and the cutscene.
     *
     * The hotbar is the leak this closes. A pet lands in it before the cutscene even
     * opens, and the cutscene's own backdrop is a dim rather than a blackout, so
     * without this the player can read the answer through it and the reveal is spoiled
     * before the cleaver has moved.
     *
     * Before the kill the HUD simply goes, the moment the hide starts - Diana's way. The dark
     * veil that used to grow over the screen first is gone (the user asked).
     *
     * ON by default. {@code mixin/HudFadeMixin} fades the whole HUD pass out - every mod's
     * elements, not only vanilla's - and skips it once it has gone, as the game does for F1.
     */
    public boolean hideHud = true;
    /** While the HUD is away, the hotbar and the health, hunger, armour and XP bars stay (drawn over the cutscene). */
    public boolean keepVitals = true;

    /**
     * Write every generated texture to {@code config/butcher/textures/} so it can
     * be edited, and prefer an edited file over the generated one.
     *
     * The art is drawn in Java, which is fine for whoever is writing the mod and no use
     * at all to whoever is playing it. This puts the PNGs somewhere reachable. A manifest
     * of hashes is what stops the mod overwriting your edits on the next launch - see
     * {@link com.butcher.render.ArtOverrides}. Off by default (0.4.0): most players never edit the art, and the first
     * cutscene would otherwise fill config/butcher/textures/ with PNGs nobody asked for. A config that has it on keeps it.
     */
    public boolean writeGeneratedArt = false;


    /**
     * Percent chance that a non-pet run ends with the Scatha ducking the last swing and
     * bolting, instead of the blade stopping short.
     *
     * Never applies to a pet: Hypixel has already decided the drop and the cutscene is
     * not allowed to contradict it. This only changes how a run you were never going to
     * win ends - and it means a long run is no longer a promise that the pet is coming,
     * which is the whole point of the chop count being hidden.
     *
     * 0 turns it off entirely; 100 makes every non-pet run a miss.
     */
    public int escapeChance = 45;

    /**
     * Cutscene volume, as a percentage, on top of Minecraft's master slider.
     *
     * Separate from the game's own volume rather than instead of it: the synth writes to
     * its own audio line, so without this there would be no way to have the cutscene
     * quieter or louder than everything else.
     */
    public int cutsceneVolume = 75;

    /** Vanilla's advancement sting when a pet drops. */
    public boolean petFanfare = true;

    /**
     * How loud that sting is, as a percentage.
     *
     * Its own slider rather than riding the cutscene volume, because it is the only
     * sound here that does not come from our synth - it goes through Minecraft's mixer
     * - and because it is the one moment anyone might want louder than everything else.
     */
    public int petFanfareVolume = 100;

    /**
     * How strongly the run builds, each as a percentage of the built-in amount.
     *
     * Three separate knobs rather than one "intensity", because they fail differently:
     * the vignette is pure decoration, the zoom changes how much of the scene you can
     * see, and the shake is the one people actually get motion sick from. Anyone who
     * wants the effect without the shake should not have to give up the other two.
     */
    public int vignetteStrength = 100;
    public int zoomStrength = 100;
    public int shakeStrength = 100;

    public static PatternConfig loadOrCreate() {
        try {
            if (Files.exists(PATH)) {
                String json = Files.readString(PATH);
                PatternConfig loaded = GSON.fromJson(json, PatternConfig.class);
                if (loaded != null) {
                    loaded.sanitize();
                    loaded.migrateHides();
                    return loaded;
                }
            }
        } catch (IOException | RuntimeException e) {
            // Kept aside rather than overwritten: a typo in a hand edit should not cost every setting.
            LOGGER.warn("[Butcher] could not read {}, starting on the defaults (the old file is kept as butcher.json.broken)", PATH, e);
            try {
                Files.move(PATH, PATH.resolveSibling("butcher.json.broken"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
        }
        return fresh();
    }

    /** The defaults, saved. A new file has nothing to migrate, so the one-off migrations are marked done. */
    public static PatternConfig fresh() {
        PatternConfig fresh = new PatternConfig();
        fresh.hidesInPercent = true;
        fresh.dianaSizeRaised = true;
        fresh.save();
        return fresh;
    }

    /**
     * Puts every value back inside what the menu can set. A hand-edited file can hold anything - a
     * misspelt mode reads as null, a volume of -40 - and the cutscenes would crash or misbehave on it.
     */
    void sanitize() {
        // The guillotine's art left the repo (2026-11-02): a config that still asks for it gets the cleaver.
        if (animationMode == null || animationMode == AnimationMode.GUILLOTINE) animationMode = AnimationMode.SCOPE;
        if (lastStyle == null || lastStyle == AnimationMode.NONE || lastStyle == AnimationMode.GUILLOTINE) {
            lastStyle = AnimationMode.SCOPE;
        }
        animationDurationMs = clamp(animationDurationMs, 800, 5000);
        scopeDurationMs = clamp(scopeDurationMs, 3000, 16000);
        hideBelowHp = clamp(hideBelowHp, 1, 10);
        escapeChance = clamp(escapeChance, 0, 100);
        vignetteStrength = clamp(vignetteStrength, 0, 100);
        zoomStrength = clamp(zoomStrength, 0, 200);
        shakeStrength = clamp(shakeStrength, 0, 200);
        cutsceneVolume = clamp(cutsceneVolume, 0, 150);
        petFanfareVolume = clamp(petFanfareVolume, 0, 150);
        vanguardSpeed = clamp(vanguardSpeed, 50, 200);
        vanguardNameSize = clamp(vanguardNameSize, 60, 160);
        vanguardFinaleVolume = clamp(vanguardFinaleVolume, 0, 150);
        dianaVolume = clamp(dianaVolume, 0, 150);
        dianaSpeed = clamp(dianaSpeed, 50, 200);
        dianaSize = clamp(dianaSize, 60, 140);
        blazeVolume = clamp(blazeVolume, 0, 150);
        blazeSpeed = clamp(blazeSpeed, 50, 200);
        seraphVolume = clamp(seraphVolume, 0, 150);
        seraphSpeed = clamp(seraphSpeed, 50, 200);
        lavaVolume = clamp(lavaVolume, 0, 150);
        lavaSpeed = clamp(lavaSpeed, 50, 200);
        fishingVolume = clamp(fishingVolume, 0, 150);
        fishingSpeed = clamp(fishingSpeed, 50, 200);
        ghostVolume = clamp(ghostVolume, 0, 150);
        ledgerVolume = clamp(ledgerVolume, 0, 150);
        ghostSpeed = clamp(ghostSpeed, 50, 200);
        automatonVolume = clamp(automatonVolume, 0, 150);
        eyeVolume = clamp(eyeVolume, 0, 150);
        gardenVolume = clamp(gardenVolume, 0, 150);
        popupSpeed = clamp(popupSpeed, 50, 200);
        gardenGiftMin = Math.max(1_000_000L, Math.min(100_000_000L, gardenGiftMin));
        dianaHidePct = clamp(dianaHidePct, 0, 20);
        blazeHidePct = clamp(blazeHidePct, 0, 30);
        seraphHidePct = clamp(seraphHidePct, 0, 30);
        lavaHidePct = clamp(lavaHidePct, 0, 20);
        dianaBusts = clamp(dianaBusts, 0, 2);
        blazeBusts = clamp(blazeBusts, 0, 2);
        seraphBusts = clamp(seraphBusts, 0, 2);
        lavaBusts = clamp(lavaBusts, 0, 2);
        revBusts = clamp(revBusts, 0, 2);
        taraBusts = clamp(taraBusts, 0, 2);
        revHidePct = clamp(revHidePct, 0, 30);
        taraHidePct = clamp(taraHidePct, 0, 30);
        revVolume = clamp(revVolume, 0, 150);
        taraVolume = clamp(taraVolume, 0, 150);
        revSpeed = clamp(revSpeed, 50, 200);
        taraSpeed = clamp(taraSpeed, 50, 200);
        revShake = clamp(revShake, 0, 200);
        taraShake = clamp(taraShake, 0, 200);
        vanguardShake = clamp(vanguardShake, 0, 200);
        dianaShake = clamp(dianaShake, 0, 200);
        blazeShake = clamp(blazeShake, 0, 200);
        seraphShake = clamp(seraphShake, 0, 200);
        lavaShake = clamp(lavaShake, 0, 200);
        nucleusVolume = clamp(nucleusVolume, 0, 150);
        nucleusSpeed = clamp(nucleusSpeed, 50, 200);
        nucleusShake = clamp(nucleusShake, 0, 200);
        nucleusPlain = clamp(nucleusPlain, 0, 2);
        nucleusStyle = clamp(nucleusStyle, 0, 1);
        minWorth = clamp(minWorth, 0, MIN_WORTH.length - 1);
        gameplayCorner = clamp(gameplayCorner, 0, 4);
        gameplaySize = clamp(gameplaySize, 50, 200);
        backgroundDim = clamp(backgroundDim, 0, 100);
        chatStyle = clamp(chatStyle, 0, 1);
        skipHints = clamp(skipHints, 0, 1000);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Once: the old million thresholds become percentages of each boss's health (150M Demonlord, 210M Seraph). */
    void migrateHides() {
        if (!dianaSizeRaised) {
            dianaSizeRaised = true;
            if (dianaSize == 100) dianaSize = 130;
            save();
        }
        if (hidesInPercent) return;
        hidesInPercent = true;
        blazeHidePct = blazeHideBelowM <= 0 ? 0 : Math.max(1, Math.round(blazeHideBelowM / 150f * 100f));
        seraphHidePct = seraphHideBelowM <= 0 ? 0 : Math.max(1, Math.round(seraphHideBelowM / 210f * 100f));
        dianaHidePct = dianaHideBelowM <= 0 ? 0 : 5;
        save();
    }

    /**
     * Writes {@code text} to a temporary file beside {@code path} and moves it into place, so a crash
     * mid-write leaves the old file whole instead of a half-written one that can no longer be read.
     */
    public static void writeAtomically(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, text);
        try {
            Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void save() {
        try {
            writeAtomically(PATH, GSON.toJson(this));
        } catch (IOException e) {
            LOGGER.warn("[Butcher] could not save {}", PATH, e);
        }
    }
}
