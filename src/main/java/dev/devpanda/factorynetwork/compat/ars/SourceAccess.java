package dev.devpanda.factorynetwork.compat.ars;

import dev.devpanda.factorynetwork.network.MachineAccess;
import dev.devpanda.factorynetwork.network.ResourceStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.Collection;

/**
 * How Source is read from and written to a third-party machine.
 *
 * <p><b>The proof for the second axis.</b> Ars Nouveau registers an ordinary
 * block capability — {@code BlockCapability<ISourceCap, Direction>} — in
 * exactly the form that power and fluid sit in alongside it. What is here is
 * the translation of their vocabulary into ours, and not a line of it lives in
 * the core.
 *
 * <p><b>Ask first, then pull.</b> The rule from {@link MachineAccess}:
 * whatever the network storage won't take must never leave the machine in the
 * first place. Source cannot be dropped on the ground.
 */
public final class SourceAccess implements MachineAccess {

    /** The single key of this kind — there is only one variety of Source. */
    static final String KEY = "source";

    public static final SourceAccess INSTANCE = new SourceAccess();

    private SourceAccess() {
    }

    @Override
    public long count(Level level, BlockPos pos, Direction side, Collection<?> keys) {
        if (!wanted(keys)) {
            return 0;
        }
        var cap = capAt(level, pos, side);
        return cap == null ? 0 : cap.getSource();
    }

    @Override
    public long fill(ResourceStore from, Level level, BlockPos pos, Direction side,
                     Collection<?> keys, long limit) {
        if (!wanted(keys) || limit <= 0) {
            return 0;
        }
        var cap = capAt(level, pos, side);
        if (cap == null || !cap.canReceive()) {
            return 0;
        }
        int room = cap.receiveSource(clamp(limit), true);
        if (room <= 0) {
            return 0;
        }
        long taken = from.extract(KEY, room);
        if (taken <= 0) {
            return 0;
        }
        int arrived = cap.receiveSource(clamp(taken), false);
        if (arrived < taken) {
            // Back into the store: between the trial and the act the machine
            // may have decided differently.
            from.insert(KEY, taken - arrived);
        }
        return arrived;
    }

    @Override
    public long drain(Level level, BlockPos pos, Direction side, Collection<?> keys,
                      ResourceStore into, long limit) {
        if (!wanted(keys) || limit <= 0) {
            return 0;
        }
        var cap = capAt(level, pos, side);
        if (cap == null || !cap.canExtract()) {
            return 0;
        }
        long room = into.room(KEY, limit);
        if (room <= 0) {
            return 0;
        }
        int available = cap.extractSource(clamp(room), true);
        if (available <= 0) {
            return 0;
        }
        int pulled = cap.extractSource(available, false);
        if (pulled <= 0) {
            return 0;
        }
        long stranded = into.insert(KEY, pulled);
        if (stranded > 0) {
            // Whatever doesn't fit goes back into the machine. If it no longer
            // fits there either, it is lost — which is why the trial sits
            // above.
            cap.receiveSource(clamp(stranded), false);
        }
        return pulled - stranded;
    }

    /**
     * The capability on this side, or {@code null}.
     *
     * <p>Access to the Ars Nouveau classes lives in its own method: that way
     * the JVM loads them only when someone actually asks.
     */
    private static com.hollingsworth.arsnouveau.api.source.ISourceCap capAt(
            Level level, BlockPos pos, Direction side) {
        if (!FnArs.installed() || level == null || !level.isLoaded(pos)) {
            return null;
        }
        return level.getCapability(
                com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry.SOURCE_CAPABILITY,
                pos, side);
    }

    /**
     * Is Source even meant?
     *
     * <p>An empty selection does not mean "everything" — the same rule as in
     * {@link MachineAccess#count}, and it already emptied a chest once, on
     * 26 Aug.
     */
    private static boolean wanted(Collection<?> keys) {
        return keys != null && keys.contains(KEY);
    }

    /** Ars Nouveau counts in {@code int}; our amounts are {@code long}. */
    private static int clamp(long amount) {
        return (int) Math.min(amount, Integer.MAX_VALUE);
    }
}
