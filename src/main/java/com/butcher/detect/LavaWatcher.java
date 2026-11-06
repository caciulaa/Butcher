package com.butcher.detect;

import com.butcher.anim.EruptionTimeline.Creature;
import com.butcher.anim.EruptionTimeline.Drop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lava fishing's rare creatures (Lord Jawbus, Thunder, Ragnarok, the Fiery Scuttler, Plhlegblast): a kill of one
 * that is ours opens Eruption with the outcome pending, and its drop line fills it in.
 *
 * Written before any real nametag was recorded (0.2.71 records them as {@code lava_mob}), on the rules that worked
 * for Diana and the Demonlord, so <b>check them against the first records</b>:
 *
 * <ul>
 *   <li><b>Ours</b>: the creature's nametag first seen within {@link #CLAIM_REACH} blocks from
 *       {@link #CLAIM_BEFORE_MS} before to {@link #CLAIM_AFTER_MS} after its catch line (Hypixel's own, or Feesh's
 *       "has spawned!" rewrite). Only our own arm the hide.</li>
 *   <li><b>Loot-shared kills play</b> (the user, 2026-10-22: they often did not), SkyMaxx's way
 *       ({@code SeaCreatureDetector.creditedKill} / {@code unnamedShare}, MIT, read with javap): a loot share line that
 *       names the creature ("2 Thunder Shards for assisting X") is a kill by itself, nametag seen or not, and takes
 *       the creature's death within {@link #NAMED_SHARE_MS} either side as the same kill; "loot for assisting X"
 *       names nothing and only claims a lava creature's death within {@link #SHARE_MS} either side - at a Fishing
 *       Festival those lines come for every sea creature. A charm is the same as a named share. A share within
 *       {@link #SHARE_MS} of our own creature's death is that kill's. 0.2.82-0.3.17 played someone else's creature only
 *       when its win came to us; with "Loot-shared kills" off in the menu that is still so, and only our own play.</li>
 *   <li><b>The kill</b>: its HP reads 0 (Diana's and the Demonlord's both did), or its nametag goes at
 *       {@link #GONE_FRACTION} of its health or less, close by, with no teleport. A charm ("CHARM! You charmed the
 *       Thunder") is a kill too - elusive creatures are charmed, not killed, on current Hypixel.</li>
 *   <li><b>The drop</b>: the user's logs put the drop line in the same second as the kill. A win within
 *       {@link #DROP_WAIT_MS} resolves it; none, and it is a bust. A win that came first (up to
 *       {@link #EARLY_MS} before) is the outcome already.</li>
 *   <li><b>Plhlegblast only plays for the Carmine Dye</b> (the user's call): its kills open nothing, but a
 *       Carmine Dye line opens Eruption with the outcome known.</li>
 * </ul>
 *
 * Every decision is narrated into the DecisionLog.
 */
public final class LavaWatcher {

    public interface Listener {
        /** A creature of ours died: open Eruption with the outcome pending. */
        void kill(Creature creature, String why);

        /** The pending kill's outcome: a win, or {@link Drop#BUST}. */
        void resolve(Drop drop, String line);

        /** A kill whose outcome is already known (the drop line came first, or the Carmine Dye): open with it. */
        void killKnown(Creature creature, Drop drop, String line);
    }

    private final DecisionLog log;
    private final Listener listener;
    /** Whether loot-shared kills play (config.lavaLootShare), set by the client each tick. */
    public boolean lootShare = true;
    /** The player's name, set by the client each tick: a drop line naming anyone else is theirs (Finder). */
    public String self;

    public LavaWatcher(DecisionLog log, Listener listener) {
        this.log = log;
        this.listener = listener;
    }

    private static final double SCAN_RADIUS = 48, CLAIM_REACH = 20, DEATH_REACH = 32, TELEPORT = 12, ARM_RADIUS = 24;
    private static final long CLAIM_BEFORE_MS = 2_000, CLAIM_AFTER_MS = 6_000, SHARE_MS = 2_000, DROP_WAIT_MS = 3_000,
            EARLY_MS = 1_500, ARM_MS = 2_500, MIN_SEEN_MS = 300;
    /** How far apart a named loot share and its creature's death can be and still be one kill (SkyMaxx's 8 s). */
    private static final long NAMED_SHARE_MS = 8_000;
    /** Two nametags of one kind going within this of each other are one body: a Fiery Scuttler wears two (2026-10-21). */
    private static final long SAME_BODY_MS = 250;
    private static final double GONE_FRACTION = 0.05;

    private static final class Mob {
        int id;
        Creature creature;
        long firstSeen;
        boolean owned, dead, immune;
        double hp = -1, maxHp = -1, distSq;
        /** Its pack (Fiery Scuttlers only, else 0). */
        int pack;
    }

    private final Map<Integer, Mob> mobs = new HashMap<>();
    /**
     * Each followed nametag's real text, styles and all - Hypixel's level, its icons, its colours - as last seen with
     * its health on it, so the cutscene can float the creature's own nametag over it (the user, 2026-10-25).
     */
    private final Map<Integer, Component> styled = new HashMap<>();
    /** The last such nametag of each kind, and when: a loot-shared kill whose own nametag was never seen uses it. */
    private final Map<Creature, Component> lastStyled = new HashMap<>();
    private final Map<Creature, Long> lastStyledAt = new HashMap<>();
    /** The nametag of the creature whose death just counted. */
    private Component deathTag;
    private Creature deathTagOf;
    private long deathTagAt = -1;
    /** Catch lines not yet matched to a nametag: creature, when. */
    private final List<Object[]> catches = new ArrayList<>();
    private long lastShareAt = -1;
    /** What the last loot share line named: a creature, or SHARE_UNNAMED. */
    private Object lastShareWhat;
    /** Our own creature whose nametag went short of a kill, and when: a loot share for it just after makes it one. */
    private Creature ownGone;
    private long ownGoneAt = -1;
    /** Deaths of creatures not ours that no loot share has claimed yet: creature, when. */
    private final java.util.ArrayDeque<Object[]> unclaimed = new java.util.ArrayDeque<>();
    /** Named loot shares (and charms) that opened a kill before its nametag died: creature, when. That death is theirs. */
    private final List<Object[]> sharedAhead = new ArrayList<>();
    /** An unnamed loot share with no death yet: a lava creature dying just after is its kill. */
    private long unnamedAt = -1;
    private long namedShareAt = -1, ownDeathAt = -1;
    /** When each kind last died, for the creatures that wear more than one nametag. */
    private final Map<Creature, Long> lastDeathOf = new HashMap<>();

    /**
     * A Fiery Scuttler "waddles up to you, friends in tow": a pack of them, each wearing two nametags (2026-10-21
     * records: pairs of ids 4 apart, the pairs first seen within a second or two of each other). Its kill plays once
     * the last of the pack has gone (the user, 2026-10-22), with the best win any of them dropped - one cutscene per
     * pack, not one per scuttler.
     */
    private static final class Pack {
        int id;
        long joinedAt, creditedAt = -1;
        boolean own;
        Drop win;
        String winLine;
    }

    private final Map<Integer, Pack> packs = new HashMap<>();
    private int nextPack = 1;
    /** Scuttlers first seen within this of the pack's last newcomer join it. */
    private static final long PACK_JOIN_MS = 3_000;
    /** A pack whose first kill is this old plays even if some of it never died (walked off, out of range). */
    private static final long PACK_MAX_MS = 90_000;
    /** The pack of the nametag whose death is being handled. */
    private Pack dying;

    private long pendingSince = -1;
    private Creature pendingCreature;
    private Drop bestDrop;
    private String bestLine;
    private long bestAt = -1;
    /** The last creature of ours that died, and when: who a Carmine Dye with no kill pending came from. */
    private Creature lastDead;
    private long lastDeadAt = -1;
    private Vec3 lastPos;

    // ------------------------------------------------------------------ chat

    public void onChatLine(String text) {
        onChatLine(text, System.currentTimeMillis());
    }

    public void onChatLine(String text, long now) {
        if (text == null) return;
        String s = DianaWatcher.plain(text).trim();

        Creature caught = LavaDrops.ofCatch(s);
        if (caught != null) {
            catches.add(new Object[]{caught, now});
            log.note("§7Lava: you caught " + caught.title + " - claiming its nametag (\"" + s + "\")");
            for (Mob m : mobs.values()) claimByCatch(m, now);
            return;
        }

        Creature charmed = LavaDrops.ofCharm(s);
        if (charmed != null) {
            // A charm is not ownership: the user's 23:19 charm (2026-10-05) was on a party member's Thunder.
            Mob m = nearest(charmed, true);
            if (m != null && m.owned) {
                m.dead = true;
                died(charmed, "you charmed it", now, true);
            } else if (lootShare) {
                // Someone else's: a charm is a credited kill, as a named loot share is (SkyMaxx).
                if (m != null) m.dead = true;
                sharedKill(charmed, "you charmed it", now);
            } else {
                log.note("§8Lava: you charmed a " + charmed.title + " that is not yours - loot-shared kills are off");
            }
            return;
        }

        Object share = LavaDrops.ofShare(s);
        if (share != null) {
            lastShareAt = now;
            lastShareWhat = share;
            // Our own creature's nametag just went short of the 5%: a share for its kind says someone else landed the
            // last hit (the user's Thunder, 2026-10-05 23:38: gone at 2.6M, "2 Thunder Shards for assisting ..." with it).
            if (ownGone != null && now - ownGoneAt <= SHARE_MS && (share == ownGone || share == LavaDrops.SHARE_UNNAMED)) {
                Creature c = ownGone;
                ownGone = null;
                died(c, "its nametag went and a loot share for its kill came", now, true);
                return;
            }
            if (ownDeathAt >= 0 && now - ownDeathAt <= SHARE_MS) {
                log.note("§8Lava: loot share for your own creature's kill - already playing");
                return;
            }
            if (!lootShare) {
                log.note("§8Lava: loot share - loot-shared kills are off in the menu");
                return;
            }
            if (share instanceof Creature named) sharedKill(named, "a loot share for it (\"" + s + "\")", now);
            else unnamedShare(now);
            return;
        }

        if (Finder.someoneElses(s, self)) {
            log.note("§8Lava: someone else's drop - \"" + s + "\"");
            return;
        }
        Drop d = LavaDrops.of(s, self);
        if (d != null) {
            boolean better = bestDrop == null || bestDrop == Drop.BUST || (d != Drop.BUST && d.ordinal() > bestDrop.ordinal());
            if (better || now - bestAt > DROP_WAIT_MS) {
                bestDrop = d;
                bestLine = s;
                bestAt = now;
            }
            if (d == Drop.BUST) return;
            log.note("§7Lava: drop line - " + d.title + " (\"" + s + "\")");
            // A Scuttler win while a pack is being killed is the pack's: it plays when the last one goes.
            if (d.from(Creature.SCUTTLER)) {
                Pack p = creditedPack();
                if (p != null) {
                    if (p.win == null || d.ordinal() > p.win.ordinal()) {
                        p.win = d;
                        p.winLine = s;
                    }
                    log.note("§7Lava: " + d.title + " from the Scuttler pack - it plays when the last one goes");
                    return;
                }
            }
            if (pendingSince >= 0) {
                if (d.from(pendingCreature)) {
                    pendingSince = -1;
                    listener.resolve(d, s);
                } else {
                    log.note("§8Lava: " + d.title + " cannot come from " + pendingCreature.title + " - not this kill's");
                }
                return;
            }
            // The dye from any of them: plays even with no kill pending (Plhlegblast's only way in).
            if (d == Drop.CARMINE) {
                Creature from = lastDead != null && now - lastDeadAt <= 10_000 ? lastDead : Creature.PLHLEGBLAST;
                log.note("§aLava: the Carmine Dye - opening (from " + from.title + ")");
                listener.killKnown(from, d, s);
                bestDrop = null;
            }
        }
    }

    // ------------------------------------------------------------------ the world

    public void tick(LocalPlayer player, double hidePct) {
        long now = System.currentTimeMillis();
        scan(player, now);
        arm(hidePct, now);
        afterTick(now);
    }

    public void afterTick(long now) {
        if (pendingSince >= 0 && now - pendingSince > DROP_WAIT_MS) {
            pendingSince = -1;
            log.note("§7Lava: no rare drop in " + DROP_WAIT_MS / 1000 + " s after the kill - bust");
            listener.resolve(Drop.BUST, null);
        }
        catches.removeIf(u -> now - (long) u[1] > CLAIM_AFTER_MS);
        settlePacks(now);
    }

    /** One creature nametag this tick: its entity, its name, how far (squared). */
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
        List<Tag> tags = new ArrayList<>();
        AABB box = AABB.ofSize(pos, SCAN_RADIUS * 2, SCAN_RADIUS * 2, SCAN_RADIUS * 2);
        for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, box)) {
            Component custom = stand.getCustomName();
            if (custom == null) continue;
            String name = custom.getString();
            // A creature already followed stays followed whatever its nametag says: Jawbus floats up immune with no health
            // on it for a few seconds (2026-10-05 records: both stands gone at 18M, the same ids back 4 s later at 20M), and
            // dropping it from the scan made it look gone - and opened Eruption on a creature still alive.
            Creature kind = LavaDrops.ofNametag(name);
            if (kind == null && !mobs.containsKey(stand.getId())) continue;
            tags.add(new Tag(stand.getId(), name, stand.distanceToSqr(player)));
            // An immune nametag has no health on it: the last one that had is kept.
            if (kind != null) {
                styled.put(stand.getId(), custom);
                lastStyled.put(kind, custom);
                lastStyledAt.put(kind, now);
            }
        }
        observe(tags, teleported, now);
        styled.keySet().retainAll(mobs.keySet());
    }

    /** The pure step: every lava creature's nametag present this tick. */
    public void observe(List<Tag> tags, boolean teleported, long now) {
        if (teleported && !mobs.isEmpty()) log.note("§8Lava: you teleported - nametags dropped");
        Set<Integer> seen = new HashSet<>();
        for (Tag tag : tags) {
            seen.add(tag.id());
            Mob m = mobs.get(tag.id());
            // An immune nametag has no health on it: never read a number out of it.
            Double hp = LavaDrops.ofNametag(tag.name()) == null ? null : DianaWatcher.hpOf(tag.name());
            if (m == null) {
                m = new Mob();
                m.id = tag.id();
                m.creature = LavaDrops.ofNametag(tag.name());
                m.firstSeen = now;
                m.distSq = tag.distSq();
                if (hp != null) m.hp = hp;
                Double max = HellionWatcher.maxOf(tag.name());
                if (max != null) m.maxHp = max;
                mobs.put(m.id, m);
                if (m.creature == Creature.SCUTTLER) joinPack(m, now);
                claimByCatch(m, now);
            }
            m.distSq = tag.distSq();
            if (hp != null) m.hp = hp;
            boolean immune = LavaDrops.ofNametag(tag.name()) == null;
            if (immune != m.immune) {
                m.immune = immune;
                if (m.owned) log.note(immune ? "§8Lava: your " + m.creature.title + " shows no health (\"" + tag.name() + "\") - immune, not dead"
                        : "§8Lava: your " + m.creature.title + " shows its health again at " + fmt(m.hp));
            }
            if (!m.dead && hp != null && hp <= 0) {
                m.dead = true;
                death(m, "its nametag reads 0", now);
            }
        }
        for (Iterator<Mob> it = mobs.values().iterator(); it.hasNext(); ) {
            Mob m = it.next();
            if (seen.contains(m.id)) continue;
            it.remove();
            if (m.dead || teleported) continue;
            if (now - m.firstSeen < MIN_SEEN_MS || m.distSq > DEATH_REACH * DEATH_REACH) continue;
            if (m.hp >= 0 && m.maxHp > 0 && m.hp <= m.maxHp * GONE_FRACTION) {
                m.dead = true;
                death(m, "its nametag went at " + fmt(m.hp) + " of " + fmt(m.maxHp), now);
            } else if (m.owned && bestDrop != null && bestDrop.win() && bestDrop.from(m.creature) && now - bestAt <= EARLY_MS) {
                // Its own win just came: the last HP read was simply stale (a hard last hit).
                m.dead = true;
                death(m, "its nametag went at " + fmt(m.hp) + " right after its " + bestDrop.title, now);
            } else if (m.owned && lastShareAt >= 0 && now - lastShareAt <= SHARE_MS
                    && (lastShareWhat == m.creature || lastShareWhat == LavaDrops.SHARE_UNNAMED)) {
                m.dead = true;
                death(m, "its nametag went at " + fmt(m.hp) + " with a loot share for its kill", now);
            } else if (m.owned) {
                ownGone = m.creature;
                ownGoneAt = now;
                log.note("§8Lava: your " + m.creature.title + "'s nametag went at " + fmt(m.hp) + " - not a kill unless a loot share for it follows");
            }
        }
        settlePacks(now);
    }

    // ------------------------------------------------------------------ Fiery Scuttler packs

    private void joinPack(Mob m, long now) {
        Pack p = null;
        for (Pack q : packs.values()) {
            if (now - q.joinedAt <= PACK_JOIN_MS && (p == null || q.joinedAt > p.joinedAt)) p = q;
        }
        if (p == null) {
            p = new Pack();
            p.id = nextPack++;
            packs.put(p.id, p);
        }
        p.joinedAt = now;
        m.pack = p.id;
        // One of the pack is ours (claimed by the catch line): so is every friend in tow.
        if (p.own) m.owned = true;
    }

    private static boolean living(Mob m) {
        return !m.dead && (m.hp < 0 || m.hp > 0);
    }

    /** Nametags of the pack still alive and in range. */
    private int alive(Pack p) {
        int n = 0;
        for (Mob m : mobs.values()) if (m.pack == p.id && living(m)) n++;
        return n;
    }

    /** Scuttlers of the pack still alive: a body's two nametags are first seen in the same tick. */
    private int aliveBodies(Pack p) {
        Set<Long> bodies = new HashSet<>();
        for (Mob m : mobs.values()) if (m.pack == p.id && living(m)) bodies.add(m.firstSeen / 60);
        return bodies.size();
    }

    /** The pack whose kills are being counted, the newest, or null. */
    private Pack creditedPack() {
        Pack best = null;
        for (Pack p : packs.values()) if (p.creditedAt >= 0 && (best == null || p.creditedAt > best.creditedAt)) best = p;
        return best;
    }

    /** The pack a kill with no nametag of its own (a loot share, a charm) belongs to: the newest one about. */
    private Pack latestPack() {
        Pack best = null;
        for (Pack p : packs.values()) if ((alive(p) > 0 || p.creditedAt >= 0) && (best == null || p.joinedAt > best.joinedAt)) best = p;
        return best;
    }

    /** A scuttler of a pack was killed: the pack is counted, and plays once the last of it is gone (settlePacks). */
    private void credit(Pack p, String how, long now, boolean own) {
        if (p.creditedAt < 0) p.creditedAt = now;
        p.own |= own;
        // Its drop line can beat its nametag: a Scuttler win just now is the pack's.
        if (bestDrop != null && bestDrop.win() && bestDrop.from(Creature.SCUTTLER) && now - bestAt <= EARLY_MS
                && (p.win == null || bestDrop.ordinal() > p.win.ordinal())) {
            p.win = bestDrop;
            p.winLine = bestLine;
            bestDrop = null;
        }
        int left = aliveBodies(p);
        if (left > 0) log.note("§7Lava: a Fiery Scuttler of the pack died - " + how + "; " + left + " left, it plays when the last one goes");
    }

    private void settlePacks(long now) {
        for (Iterator<Pack> it = packs.values().iterator(); it.hasNext(); ) {
            Pack p = it.next();
            if (p.creditedAt < 0) {
                if (alive(p) == 0 && now - p.joinedAt > 10_000) it.remove();
                continue;
            }
            boolean timedOut = now - p.creditedAt > PACK_MAX_MS;
            if (alive(p) > 0 && !timedOut) continue;
            it.remove();
            String how = timedOut ? "its first kill was " + PACK_MAX_MS / 1000 + " s ago" : "the last of the pack is gone";
            if (p.own) ownDeathAt = now;
            lastDead = Creature.SCUTTLER;
            lastDeadAt = now;
            if (p.win != null) {
                log.note("§aLava: the Fiery Scuttler pack is dead - " + how + "; it dropped " + p.win.title);
                listener.killKnown(Creature.SCUTTLER, p.win, p.winLine);
                continue;
            }
            open(Creature.SCUTTLER, how, now, p.own ? "your " : "a loot-shared ");
        }
    }

    private void claimByCatch(Mob m, long now) {
        if (m.owned || m.dead) return;
        for (Iterator<Object[]> it = catches.iterator(); it.hasNext(); ) {
            Object[] c = it.next();
            long at = (long) c[1];
            if (c[0] != m.creature || m.firstSeen < at - CLAIM_BEFORE_MS || m.firstSeen > at + CLAIM_AFTER_MS) continue;
            if (m.distSq > CLAIM_REACH * CLAIM_REACH) continue;
            it.remove();
            m.owned = true;
            // Claiming one scuttler claims its whole pack, and any friend that joins it after.
            Pack p = m.creature == Creature.SCUTTLER ? packs.get(m.pack) : null;
            if (p != null) {
                p.own = true;
                for (Mob o : mobs.values()) if (o.pack == p.id) o.owned = true;
            }
            log.note("§7Lava: your " + m.creature.title + (p != null ? " pack" : "") + " - first seen " + Math.round(Math.sqrt(m.distSq)) + " blocks away at its catch line");
            return;
        }
    }

    /**
     * A nametag died. Ours (claimed by our catch line) opens the cutscene. Anyone else's opens only by loot share: a
     * named share or charm that already opened it takes this death as its own; an unnamed share just before claims it;
     * otherwise it waits {@link #SHARE_MS} for an unnamed share (named ones wait up to {@link #NAMED_SHARE_MS}).
     */
    private void death(Mob m, String how, long now) {
        Component tag = styled.get(m.id);
        if (tag != null) {
            deathTag = tag;
            deathTagOf = m.creature;
            deathTagAt = now;
        }
        dying = m.creature == Creature.SCUTTLER ? packs.get(m.pack) : null;
        try {
            deathOf(m, how, now);
        } finally {
            dying = null;
        }
    }

    private void deathOf(Mob m, String how, long now) {
        Long last = lastDeathOf.put(m.creature, now);
        if (last != null && now - last <= SAME_BODY_MS) {
            log.note("§8Lava: a second nametag of the same " + m.creature.title + " went - one kill");
            return;
        }
        // A named share or a charm opened this kill already, before the nametag caught up.
        for (Iterator<Object[]> it = sharedAhead.iterator(); it.hasNext(); ) {
            Object[] a = it.next();
            if (now - (long) a[1] > NAMED_SHARE_MS) {
                it.remove();
            } else if (a[0] == m.creature) {
                it.remove();
                log.note("§8Lava: the " + m.creature.title + " whose loot share opened just now died - " + how);
                if (m.owned) ownDeathAt = now;
                return;
            }
        }
        if (m.owned) {
            died(m.creature, how, now, true);
            return;
        }
        if (!lootShare) {
            log.note("§8Lava: a " + m.creature.title + " died that is not yours - " + how + "; loot-shared kills are off");
            return;
        }
        if (unnamedAt >= 0 && now - unnamedAt <= SHARE_MS) {
            unnamedAt = -1;
            died(m.creature, how + ", and a loot share came with it", now, false);
            return;
        }
        unclaimed.addLast(new Object[]{m.creature, now});
        while (unclaimed.size() > 6) unclaimed.pollFirst();
        log.note("§8Lava: a " + m.creature.title + " died that is not yours - " + how + "; it plays if a loot share for it comes");
    }

    /** A loot share naming {@code c}, or a charm of someone else's {@code c}: a kill (SkyMaxx's creditedKill). */
    private void sharedKill(Creature c, String why, long now) {
        namedShareAt = now;
        unclaimed.removeIf(u -> now - (long) u[1] > NAMED_SHARE_MS);
        boolean found = false;
        for (Iterator<Object[]> it = unclaimed.descendingIterator(); it.hasNext(); ) {
            if (it.next()[0] == c) {
                it.remove();
                found = true;
                break;
            }
        }
        if (!found) sharedAhead.add(new Object[]{c, now});
        died(c, why, now, false);
    }

    /** "loot for assisting X", naming nothing: the kill of a lava creature that died just now, or one about to. */
    private void unnamedShare(long now) {
        if (namedShareAt >= 0 && now - namedShareAt <= SHARE_MS) return;
        unclaimed.removeIf(u -> now - (long) u[1] > SHARE_MS);
        Object[] u = unclaimed.pollLast();
        if (u != null) {
            died((Creature) u[0], "it died and a loot share came", now, false);
            return;
        }
        unnamedAt = now;
    }

    /** A kill to play: ours ({@code own}), or one loot share credits to us. */
    private void died(Creature c, String how, long now, boolean own) {
        if (own) ownDeathAt = now;
        if (c == Creature.SCUTTLER) {
            Pack p = dying != null ? dying : latestPack();
            if (p != null) {
                credit(p, how, now, own);
                return;
            }
        }
        if (lastDead == c && now - lastDeadAt < 1_000) return;
        lastDead = c;
        lastDeadAt = now;
        String whose = own ? "your " : "a loot-shared ";
        if (c == Creature.PLHLEGBLAST) {
            log.note("§7Lava: " + whose + "Plhlegblast died - " + how + "; it only plays for the Carmine Dye");
            if (own && bestDrop == Drop.CARMINE && now - bestAt <= EARLY_MS) listener.killKnown(c, Drop.CARMINE, bestLine);
            return;
        }
        open(c, how, now, whose);
    }

    /** Opens the kill: known if its win came just before, else pending until its drop line. */
    private void open(Creature c, String how, long now, String whose) {
        if (bestDrop != null && bestDrop.win() && bestDrop.from(c) && now - bestAt <= EARLY_MS) {
            log.note("§aLava: " + whose + c.title + " died - " + how + "; it dropped " + bestDrop.title);
            listener.killKnown(c, bestDrop, bestLine);
            bestDrop = null;
            return;
        }
        log.note("§aLava: " + whose + c.title + " died - " + how + "; opening, waiting for the drop");
        pendingSince = now;
        pendingCreature = c;
        bestDrop = null;
        bestLine = null;
        listener.kill(c, how);
    }

    /**
     * The real nametag of a {@code c} that just died - its own, or (a loot share whose nametag was never followed) the
     * last of its kind seen in the last half-minute - or null.
     */
    public Component nametagFor(Creature c) {
        long now = System.currentTimeMillis();
        if (deathTag != null && deathTagOf == c && now - deathTagAt < 15_000) return deathTag;
        Long at = lastStyledAt.get(c);
        return at != null && now - at < 30_000 ? lastStyled.get(c) : null;
    }

    private Mob nearest(Creature c, boolean alive) {
        Mob best = null;
        for (Mob m : mobs.values()) {
            if (m.creature != c || (alive && m.dead)) continue;
            if (best == null || m.distSq < best.distSq) best = m;
        }
        return best;
    }

    // ------------------------------------------------------------------ the pre-kill hide

    private volatile long armedUntil;

    /** Whether a creature of ours is nearly dead: the HUD and chat fade (Diana's rule). Cheap, any thread. */
    public boolean armed() {
        return System.currentTimeMillis() < armedUntil;
    }

    /** Whether the hide is armed at {@code now} (for the offline tests, which run on their own clock). */
    public boolean armedAt(long now) {
        return now < armedUntil;
    }

    /** Arms the hide for any creature of ours at or below {@code pct}% of its health. Public for the offline tests. */
    public void arm(double pct, long now) {
        if (pct <= 0) return;
        // Only our own: hiding the HUD for every party member's creature hid it half the time.
        for (Mob m : mobs.values()) {
            if (m.dead || !m.owned || m.creature == Creature.PLHLEGBLAST) continue;
            // A scuttler pack hides the HUD only for its last one.
            Pack p = m.creature == Creature.SCUTTLER ? packs.get(m.pack) : null;
            if (p != null && aliveBodies(p) > 1) continue;
            if (m.distSq > ARM_RADIUS * ARM_RADIUS || now - m.firstSeen < MIN_SEEN_MS) continue;
            if (m.hp < 0 || m.maxHp <= 0 || m.hp > m.maxHp * pct / 100.0) continue;
            if (now >= armedUntil) log.note("§7Lava: " + m.creature.title + " is at " + fmt(m.hp) + " - hiding the HUD");
            armedUntil = now + ARM_MS;
        }
    }

    private static String fmt(double hp) {
        return hp >= 1e6 ? Math.round(hp / 1e5) / 10.0 + "M" : hp >= 1e3 ? Math.round(hp / 100) / 10.0 + "k" : String.valueOf(Math.round(hp));
    }
}
