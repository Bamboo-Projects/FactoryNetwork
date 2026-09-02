package dev.devpanda.factorynetwork.network;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who is currently editing which file of a project.
 *
 * <p><b>Without this, two players overwrite each other silently.</b> Both send
 * the whole draft, and whoever types last wins — even over a file they never
 * had open. The other one notices when their work is gone.
 *
 * <p><b>Per file and not per project.</b> Two people on a factory almost
 * always work on different pieces; locking the whole project would mean one
 * waits even though nothing conflicts.
 *
 * <p><b>A lock is taken by writing, not by opening.</b> Whoever only looks at
 * a file should not block it — and no one should have to remember to release
 * it again. It expires on its own once nothing has come in for a while.
 *
 * <p><b>Without any tie to Minecraft.</b> What is needed is an identifier and
 * a name; whoever brings those is all the same to it. This lets the rule be
 * checked in ordinary tests — a {@code ServerPlayer} would need a world, a
 * server and half a game.
 */
public final class FileLocks {

    /** A lock stays in force this long after the last write. */
    private static final long TIMEOUT_TICKS = 20L * 60;

    private record Holder(UUID player, String name, long touched) {
    }

    private final Map<String, Holder> holders = new HashMap<>();

    /**
     * May this player write to this file?
     *
     * <p>Yes, if it is free, belongs to them, or the lock has expired. In the
     * first and last case they take it in doing so.
     */
    public boolean claim(String file, UUID player, String name, long now) {
        Holder holder = holders.get(file);
        if (holder != null && !holder.player().equals(player)
                && now - holder.touched() < TIMEOUT_TICKS) {
            return false;
        }
        holders.put(file, new Holder(player, name, now));
        return true;
    }

    /**
     * Who currently holds this file, or {@code null}.
     *
     * <p>For "request editing": whoever wants to knock needs someone to knock
     * on. An expired lock does not count — then the file is free, and the
     * requester should simply take it.
     */
    public UUID holderOf(String file, long now) {
        Holder holder = holders.get(file);
        if (holder == null || now - holder.touched() >= TIMEOUT_TICKS) {
            return null;
        }
        return holder.player();
    }

    /**
     * Releases everything that belongs to this player.
     *
     * <p>On closing the terminal. Waiting for the timeout would mean someone
     * else stands for a minute in front of a file that no one has open
     * anymore.
     */
    public void release(UUID player) {
        holders.values().removeIf(holder -> holder.player().equals(player));
    }

    /**
     * Who holds which file, from the perspective of a particular player.
     *
     * <p>Your own locks are not in it: the fact that you are writing yourself
     * is not news.
     */
    public Map<String, String> othersFor(UUID player, long now) {
        Map<String, String> shown = new LinkedHashMap<>();
        holders.forEach((file, holder) -> {
            if (!holder.player().equals(player) && now - holder.touched() < TIMEOUT_TICKS) {
                shown.put(file, holder.name());
            }
        });
        return shown;
    }
}
