package dev.devpanda.factorynetwork.compat.ars;

import dev.devpanda.factorynetwork.block.entity.DriveBlockEntity;
import dev.devpanda.factorynetwork.network.ResourceStore;

import java.util.List;
import java.util.Map;

/**
 * Where Source sits in the network while it is in transit.
 *
 * <p><b>A stopover, not storage.</b> {@code move} also routes device-to-device
 * through the network storage — without it, the same operation would need a
 * third code path, and whatever went missing in transit no one would have
 * counted. Source therefore needs a store so that it can be moved at all.
 *
 * <p><b>It survives no restart</b>, and that is by design: items, fluids and
 * chemicals sit in cells in a drive, and how much fits is decided by the cell.
 * For Source there is no cell. A network store that held any amount without a
 * cell would be free storage space — the storage is Ars Nouveau's Source Jars.
 *
 * <p><b>The upper limit is a guess.</b> Ten thousand is plenty for any
 * transport and too little to serve as storage; which number is right, a round
 * of play will tell. It is recorded as an open question in
 * {@code ressourcenarten.md}.
 */
public final class SourceBuffer implements ResourceStore {

    /** This is how much the stopover holds. See the class comment. */
    public static final long CAPACITY = 10_000;

    private long held;
    private Runnable listener = () -> { };

    @Override
    public long count(Object key) {
        return SourceAccess.KEY.equals(key) ? held : 0;
    }

    @Override
    public long room(Object key, long wanted) {
        if (!SourceAccess.KEY.equals(key) || wanted <= 0) {
            return 0;
        }
        return Math.min(wanted, CAPACITY - held);
    }

    @Override
    public long insert(Object key, long amount) {
        if (!SourceAccess.KEY.equals(key) || amount <= 0) {
            return amount;
        }
        long fits = Math.min(amount, CAPACITY - held);
        if (fits <= 0) {
            return amount;
        }
        held += fits;
        listener.run();
        return amount - fits;
    }

    @Override
    public long extract(Object key, long amount) {
        if (!SourceAccess.KEY.equals(key) || amount <= 0) {
            return 0;
        }
        long taken = Math.min(amount, held);
        if (taken <= 0) {
            return 0;
        }
        held -= taken;
        listener.run();
        return taken;
    }

    @Override
    public Map<?, Long> contents() {
        return held > 0 ? Map.of(SourceAccess.KEY, held) : Map.of();
    }

    /**
     * Drives play no role here.
     *
     * <p>There is no Source cell, and the stopover is attached to no drive —
     * it is the route, not the place.
     */
    @Override
    public void setDrives(List<DriveBlockEntity> drives) {
    }

    @Override
    public boolean hasDrives() {
        return true;
    }

    @Override
    public void setChangeListener(Runnable changed) {
        this.listener = changed == null ? () -> { } : changed;
    }
}
