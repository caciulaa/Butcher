package com.butcher.detect;

import com.butcher.anim.SlayerTimeline.Boss;
import com.butcher.anim.SlayerTimeline.Drop;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The player's own Revenant Horror and Tarantula Broodfather: a tier V kill opens The Grave or The Cocoon with the drop
 * pending, its drop line fills the outcome in; tier IV plays only for its dye (the user, 2026-10-26).
 *
 * What the user's own logs settle (29 tier V Revenant kills, 2026-10): no "SLAYER BOSS!" line comes with auto-slayer;
 * the drop line ({@code RARE DROP! (4x Foul Flesh) ...}) lands a second before {@code SLAYER QUEST COMPLETE!}, which is
 * followed by "Zombie Slayer LVL 7 - ..." and the next quest's "» Slay 7,200 Combat XP worth of Zombies.". What they
 * do not hold is a boss nametag - so this is built the Seraph's way and must be checked against the first records
 * (SlayerRecorder now records these bosses):
 * <ul>
 *   <li><b>Ownership</b>: the boss's own "Spawned by: &lt;player&gt;" armour stand a few entity ids from its nametag;
 *       failing that (none seen a second after the boss), the first boss of the quest's kind within 12 blocks after the
 *       quest started.</li>
 *   <li><b>The kill</b>: its health reads 0, or its nametag goes at 5% or less, close, not on a teleport. <b>Not the
 *       Tarantula's</b>: its nametag reads 0 and goes 2.6 to 7.3 s before it really dies (the user's records, 2026-10-28:
 *       five tier V kills - the kill credit, the drop line and QUEST COMPLETE all came after the 0; a friend saw the Cocoon
 *       play over a boss still there). Its 0 only marks it {@link #dyingSince dying}; it is killed by the first real
 *       sign - a drop line, "YOU COCOONED YOUR SLAYER BOSS", "Your Slayer Kill gave you ...", or QUEST COMPLETE - and the
 *       HUD stays hidden in between. The Revenant's 0 is its death (its drop line came a second after).</li>
 *   <li><b>The tier</b>: the numeral on its nametag, or tier V's own names; unknown is taken as tier V.</li>
 * </ul>
 * Every decision is narrated.
 */
public final class RevTaraWatcher {

    public interface Listener {
        /** A tier V boss of ours died: open its cutscene, the outcome pending. */
        void kill(Boss boss);

        /** The pending kill's outcome: a win, or {@link Drop#BUST}. */
        void resolve(Boss boss, Drop drop, String line);

        /** A kill whose outcome is already known (the nametag missed it, or a tier IV dye). */
        void killKnown(Boss boss, Drop drop, String line);
    }

    private final DecisionLog log;
    private final Listener listener;
    public String self = "";
    /** Which bosses play (the menu). */
    public boolean revenant = true, tarantula = true;

    /** Which slayer quest is running (SlayerQuest): a kill not seen to die plays only once its level line agrees. */
    private final SlayerQuest quest;

    public RevTaraWatcher(DecisionLog log, Listener listener) {
        this(log, listener, new SlayerQuest());
    }

    /** @param quest the tracker ButcherClient feeds from every island; this watcher feeds it too */
    public RevTaraWatcher(DecisionLog log, Listener listener, SlayerQuest quest) {
        this.log = log;
        this.listener = listener;
        this.quest = quest;
    }

    /** The slayer kind a boss belongs to. */
    static SlayerQuest.Type typeOf(Boss b) {
        return b == Boss.REVENANT ? SlayerQuest.Type.ZOMBIE : SlayerQuest.Type.SPIDER;
    }

    /**
     * A kill not seen to die, waiting at quest complete for the level line ("Zombie Slayer LVL ...") to say which slayer
     * the quest was: a Paragon Shard drops from other slayers too.
     */
    private Boss awaitingBoss;
    private Drop awaiting;
    private String awaitingLine, awaitingWhy;
    private long awaitingAt = -1;

    private void await(Boss boss, Drop drop, String line, String why, long now) {
        awaitingBoss = boss;
        awaiting = drop;
        awaitingLine = line;
        awaitingWhy = why;
        awaitingAt = now;
        log.note("§7" + name(boss) + ": quest complete with " + (drop.win() ? drop.title : "no win") + " (" + why
                + ") - checking which slayer the quest was");
    }

    private static final double SCAN_RADIUS = 48, TELEPORT = 12, GONE_REACH = 24, ARM_RADIUS = 24, FALLBACK_REACH = 12;
    private static final long DROP_WAIT_MS = 3_000, ARM_MS = 2_500, MIN_SEEN_MS = 300, OWNER_WAIT_MS = 1_000;
    /** A Tarantula whose nametag read 0 and whose real death has not come in this long is given up on. */
    static final long DYING_MAX_MS = 15_000;

    private static final class Mob {
        int id;
        long firstSeen;
        boolean owned, dead, ownerLooked;
        double hp = -1, maxHp = -1, distSq;
        Boss boss;
        int tier;
        String owner;
    }

    private final Map<Integer, Mob> mobs = new HashMap<>();
    private Boss questBoss;
    private long questAt = -1;
    private boolean oursThisQuest, killedThisQuest;
    private Boss oursBoss;
    private int oursTier;
    private long pendingSince = -1;
    private Boss pendingBoss;
    private Drop bestDrop;
    private String bestLine;
    private long bestAt = -1;
    private Vec3 lastPos;
    /** When our Tarantula's nametag read 0 (it is still to die - see the class note); -1 when none is dying. */
    private long dyingSince = -1;
    private Boss dyingBoss;
    private int dyingTier;

    private boolean on(Boss b) {
        return b == Boss.REVENANT ? revenant : tarantula;
    }

    // ------------------------------------------------------------------ chat

    public void onChatLine(String text) {
        onChatLine(text, System.currentTimeMillis());
    }

    public void onChatLine(String text, long now) {
        if (text == null) return;
        String s = text.replaceAll("§.", "").trim();
        quest.onChatLine(s, now);
        SlayerQuest.Type level = SlayerQuest.levelOf(s);
        if (level != null && awaiting != null) {
            String title = awaiting.win() ? awaiting.title : "a bust";
            if (level == typeOf(awaitingBoss)) {
                log.note("§a" + name(awaitingBoss) + ": quest complete, a " + level.name().toLowerCase(Locale.ROOT)
                        + " quest - " + awaitingWhy + "; playing " + title);
                listener.killKnown(awaitingBoss, awaiting, awaitingLine);
            } else {
                log.note("§8" + name(awaitingBoss) + ": " + title + " came from a " + level.name().toLowerCase(Locale.ROOT)
                        + " slayer quest - not played");
            }
            awaiting = null;
            awaitingBoss = null;
            return;
        }
        Boss objective = RevTaraDrops.questBoss(s);
        if (objective != null) {
            questBoss = objective;
            questAt = now;
            return;
        }
        // "SLAYER BOSS! The Tarantula Broodfather V spawned!": the quest's boss, if its objective line was not seen.
        if (s.startsWith("SLAYER BOSS!") && RevTaraDrops.bossOf(s) != null) {
            questBoss = RevTaraDrops.bossOf(s);
            if (questAt < 0) questAt = now;
            return;
        }
        // The Tarantula's real death, after its 0: the slayer kill (a cocoon, or the kill's own heal line).
        if (dyingSince >= 0 && (s.startsWith("YOU COCOONED YOUR SLAYER BOSS") || s.startsWith("Your Slayer Kill gave you"))) {
            died("it really died (\"" + s + "\")", now);
            return;
        }
        if (s.startsWith("SLAYER QUEST STARTED!")) {
            forget();
            // A new quest's kind is only what its own objective line says next ("worth of Zombies"). Kept from the last
            // quest, a Revenant quest made a later Sven quest's Paragon Shard play the Grave.
            questBoss = null;
            questAt = now;
            return;
        }
        if (s.startsWith("SLAYER QUEST FAILED!") || s.startsWith("Your Slayer Quest has been cancelled")) {
            if (oursThisQuest) log.note("§8" + name(oursBoss) + ": the quest ended without a kill (" + s + ")");
            forget();
            questBoss = null;
            return;
        }
        Boss context = oursBoss != null ? oursBoss : questBoss;
        Drop d = context == null ? null : RevTaraDrops.of(s, context);
        if (d != null) {
            boolean better = bestDrop == null || bestDrop == Drop.BUST || (d != Drop.BUST && d.ordinal() > bestDrop.ordinal());
            if (better || now - bestAt > DROP_WAIT_MS) {
                bestDrop = d;
                bestLine = s;
                bestAt = now;
            }
            log.note("§7" + name(context) + ": drop line - " + (d == Drop.BUST ? "not one the animation plays" : d.title) + " (\"" + s + "\")");
            // A dying Tarantula's win: it has died, and its outcome is known.
            if (dyingSince >= 0 && d.win()) {
                died("its drop came", now);
                return;
            }
            if (pendingSince >= 0 && d.win()) {
                pendingSince = -1;
                listener.resolve(pendingBoss, d, s);
            }
            return;
        }
        if (s.startsWith("SLAYER QUEST COMPLETE!")) {
            if (pendingSince >= 0) {
                pendingSince = -1;
                log.note("§7" + name(pendingBoss) + ": quest complete with no drop the animation plays - bust");
                listener.resolve(pendingBoss, Drop.BUST, s);
            } else if (!killedThisQuest && context != null && on(context)) {
                Drop known = bestDrop != null && now - bestAt <= DROP_WAIT_MS ? bestDrop : Drop.BUST;
                int tier = oursThisQuest ? oursTier : 0;
                if (tier == 4) {
                    if (known.dye()) await(context, known, bestLine, "a tier IV " + known.title, now);
                } else if (oursThisQuest) {
                    await(context, known, bestLine, "yours died out of sight", now);
                } else if (known.win()) {
                    // Never claimed, but a bracketed slayer drop line is only ever the player's own: the win plays -
                    // once the level line says the quest was this boss's slayer.
                    await(context, known, bestLine, "your boss was never claimed", now);
                }
            }
            forget();
        }
    }

    // ------------------------------------------------------------------ the world

    public void tick(LocalPlayer player, double hidePct) {
        long now = System.currentTimeMillis();
        if (player != null) self = player.getGameProfile().name();
        scan(player, now);
        arm(hidePct, now);
        afterTick(now);
    }

    public void afterTick(long now) {
        if (awaiting != null && now - awaitingAt > SlayerQuest.LEVEL_WAIT_MS) {
            log.note("§8" + name(awaitingBoss) + ": no slayer level line after quest complete - not played");
            awaiting = null;
            awaitingBoss = null;
        }
        if (dyingSince >= 0 && now - dyingSince > DYING_MAX_MS) {
            log.note("§8" + name(dyingBoss) + ": its nametag read 0 " + DYING_MAX_MS / 1000 + " s ago and it never died - given up");
            dyingSince = -1;
        }
        if (pendingSince >= 0 && now - pendingSince > DROP_WAIT_MS) {
            pendingSince = -1;
            log.note("§7" + name(pendingBoss) + ": no win in " + DROP_WAIT_MS / 1000 + " s after the kill - bust");
            listener.resolve(pendingBoss, Drop.BUST, null);
        }
    }

    public record Tag(int id, String name, double distSq) {
    }

    private void scan(LocalPlayer player, long now) {
        ClientLevel level = Minecraft.getInstance().level;
        if (player == null || level == null) {
            mobs.clear();
            lastPos = null;
            return;
        }
        Vec3 pos = player.position();
        boolean teleported = lastPos != null && pos.distanceToSqr(lastPos) > TELEPORT * TELEPORT;
        lastPos = pos;
        java.util.List<Tag> tags = new java.util.ArrayList<>();
        AABB box = AABB.ofSize(pos, SCAN_RADIUS * 2, SCAN_RADIUS * 2, SCAN_RADIUS * 2);
        for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, box)) {
            Component custom = stand.getCustomName();
            if (custom == null) continue;
            String name = custom.getString();
            if (RevTaraDrops.bossOf(name) == null && !name.startsWith("Spawned by:") && !mobs.containsKey(stand.getId())) continue;
            tags.add(new Tag(stand.getId(), name, stand.distanceToSqr(player)));
        }
        observe(tags, teleported, now);
    }

    /** The pure step: every Revenant/Tarantula and "Spawned by" nametag present this tick. Tests replay recorded ones. */
    public void observe(List<Tag> tags, boolean teleported, long now) {
        if (teleported && !mobs.isEmpty()) log.note("§8Slayer: you teleported - nametags dropped");
        Map<Integer, String> owners = new HashMap<>();
        for (Tag tag : tags) {
            if (tag.name().startsWith("Spawned by:")) owners.put(tag.id(), tag.name().substring("Spawned by:".length()).trim());
        }
        Set<Integer> seen = new HashSet<>();
        for (Tag tag : tags) {
            String name = tag.name();
            if (name.startsWith("Spawned by:")) continue;
            Boss kind = RevTaraDrops.bossOf(name);
            Mob m = mobs.get(tag.id());
            if (m == null) {
                if (kind == null) continue;
                m = new Mob();
                m.id = tag.id();
                m.firstSeen = now;
                m.boss = kind;
                mobs.put(m.id, m);
            }
            seen.add(tag.id());
            m.distSq = tag.distSq();
            int tier = RevTaraDrops.tierOf(name);
            if (tier > 0) m.tier = tier;
            Double hp = name.contains("❤") ? DianaWatcher.hpOf(name) : null;
            if (hp != null) {
                m.hp = hp;
                m.maxHp = Math.max(m.maxHp, hp);
            }
            claim(m, owners, now);
            if (m.owned && !m.dead && hp != null && hp <= 0) {
                m.dead = true;
                killed(m.boss, m.tier, "its nametag reads 0", now, false);
            }
        }
        for (Iterator<Mob> it = mobs.values().iterator(); it.hasNext(); ) {
            Mob m = it.next();
            if (seen.contains(m.id)) continue;
            it.remove();
            if (!m.owned || m.dead || teleported) continue;
            if (now - m.firstSeen >= MIN_SEEN_MS && m.distSq <= GONE_REACH * GONE_REACH && m.hp >= 0 && m.maxHp > 0 && m.hp <= m.maxHp * 0.05) {
                m.dead = true;
                killed(m.boss, m.tier, "its nametag went at " + fmt(m.hp), now, false);
            } else {
                log.note("§8" + name(m.boss) + ": your boss's nametag went at " + (m.hp < 0 ? "?" : fmt(m.hp)) + " - not a kill");
            }
        }
    }

    private void claim(Mob m, Map<Integer, String> owners, long now) {
        if (m.owned || m.dead) return;
        if (m.owner == null) {
            for (int off : new int[]{2, 1, 3, -1, -2, -3}) {
                String o = owners.get(m.id + off);
                if (o != null) {
                    m.owner = o;
                    break;
                }
            }
        }
        if (m.owner != null && !m.ownerLooked) {
            m.ownerLooked = true;
            if (!self.isEmpty() && m.owner.toLowerCase(Locale.ROOT).equals(self.toLowerCase(Locale.ROOT))) {
                own(m, "spawned by you, " + Math.round(Math.sqrt(m.distSq)) + " blocks away");
            }
            return;
        }
        // No owner's tag after a second: the quest's own boss if it is close and the first since the quest began.
        if (m.owner == null && now - m.firstSeen >= OWNER_WAIT_MS && !oursThisQuest && questBoss == m.boss && questAt >= 0
                && m.firstSeen >= questAt && m.distSq <= FALLBACK_REACH * FALLBACK_REACH) {
            own(m, "first seen " + Math.round(Math.sqrt(m.distSq)) + " blocks away during your quest (no \"Spawned by\" seen)");
        }
    }

    private void own(Mob m, String why) {
        if (!on(m.boss)) {
            m.ownerLooked = true;
            return;
        }
        // A running quest of another slayer's: this boss is not the quest's, whatever its tag says.
        SlayerQuest.Type running = quest.current();
        if (running != null && running != typeOf(m.boss)) {
            m.ownerLooked = true;
            log.note("§8" + name(m.boss) + ": not yours - your quest is a " + running.name().toLowerCase(Locale.ROOT) + " one");
            return;
        }
        m.owned = true;
        oursThisQuest = true;
        oursBoss = m.boss;
        oursTier = m.tier;
        log.note("§7" + name(m.boss) + ": yours (tier " + (m.tier == 0 ? "?" : m.tier) + ") - " + why);
    }

    /** {@code real}: this is its death for sure (a dying Tarantula's real death), not a 0 on its nametag. */
    private void killed(Boss boss, int tier, String how, long now, boolean real) {
        // The Tarantula is not dead yet at its 0 (the class note): wait for its real death.
        if (!real && boss == Boss.TARANTULA && !(tier > 0 && tier < 5)) {
            dyingSince = now;
            dyingBoss = boss;
            dyingTier = tier;
            log.note("§7" + name(boss) + ": yours - " + how + "; it dies a few seconds later, waiting for that");
            return;
        }
        killedThisQuest = true;
        oursTier = tier;
        boolean t4 = tier > 0 && tier < 5;
        if (bestDrop != null && bestDrop.win() && now - bestAt <= 1_500) {
            if (t4 && !bestDrop.dye()) {
                log.note("§8" + name(boss) + ": your tier " + tier + " died - " + how + "; only a dye plays below tier V");
                return;
            }
            log.note("§a" + name(boss) + ": yours died - " + how + "; it dropped " + bestDrop.title);
            listener.killKnown(boss, bestDrop, bestLine);
            return;
        }
        if (t4) {
            // Tier IV: nothing opens on the kill; a dye line in the next moments plays on its own (QUEST COMPLETE).
            log.note("§8" + name(boss) + ": your tier " + tier + " died - " + how + "; it only plays for a dye");
            killedThisQuest = false;
            return;
        }
        log.note("§a" + name(boss) + ": yours died - " + how + "; opening, waiting for the drop");
        pendingSince = now;
        pendingBoss = boss;
        bestDrop = null;
        bestLine = null;
        listener.kill(boss);
    }

    /** The dying Tarantula's real death: killed as any other boss is now, by what showed it. */
    private void died(String how, long now) {
        Boss boss = dyingBoss;
        int tier = dyingTier;
        dyingSince = -1;
        killed(boss, tier, how, now, true);
    }

    private void forget() {
        dyingSince = -1;
        oursThisQuest = false;
        killedThisQuest = false;
        oursBoss = null;
        oursTier = 0;
        for (Mob m : mobs.values()) m.owned = false;
    }

    // ------------------------------------------------------------------ the pre-kill hide

    private volatile long armedUntil;

    public boolean armed() {
        return System.currentTimeMillis() < armedUntil;
    }

    /** @param pct the share of its health below which the HUD fades, 0 off */
    private void arm(double pct, long now) {
        if (pct <= 0) return;
        // A dying Tarantula is gone from the world but not dead: the HUD stays hidden until its cutscene opens.
        if (dyingSince >= 0) armedUntil = now + ARM_MS;
        for (Mob m : mobs.values()) {
            if (!m.owned || m.dead || m.distSq > ARM_RADIUS * ARM_RADIUS || m.hp < 0 || m.maxHp <= 0) continue;
            if (m.tier > 0 && m.tier < 5) continue;
            if (m.hp > m.maxHp * pct / 100.0) continue;
            if (now >= armedUntil) log.note("§7" + name(m.boss) + ": yours is at " + fmt(m.hp) + " - hiding the HUD");
            armedUntil = now + ARM_MS;
        }
    }

    private static String name(Boss b) {
        return b == null ? "Slayer" : b.title;
    }

    private static String fmt(double hp) {
        return hp >= 1e6 ? Math.round(hp / 1e5) / 10.0 + "M" : String.valueOf(Math.round(hp));
    }
}
