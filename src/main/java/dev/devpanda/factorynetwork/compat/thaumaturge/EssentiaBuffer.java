package dev.devpanda.factorynetwork.compat.thaumaturge;

import dev.devpanda.factorynetwork.block.entity.DriveBlockEntity;
import dev.devpanda.factorynetwork.network.ResourceStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The network's essentia, until there are cells for it.
 *
 * <p>Foreign kinds move through the network store, even from jar to jar
 * (see {@code WorldHost.moveForeign}); this is that store. Small and shared
 * by all aspects: it is a way station, not a warehouse.
 */
public final class EssentiaBuffer implements ResourceStore {

    /** All aspects together. A Thaumaturge jar holds 250. */
    public static final long CAPACITY = 10_000;

    private final Map<String, Long> held = new LinkedHashMap<>();
    private long total;
    private Runnable listener = () -> { };

    @Override
    public long count(Object key) {
        return key instanceof String id ? held.getOrDefault(id, 0L) : 0;
    }

    @Override
    public long room(Object key, long wanted) {
        if (!(key instanceof String) || wanted <= 0) {
            return 0;
        }
        return Math.min(wanted, CAPACITY - total);
    }

    @Override
    public long insert(Object key, long amount) {
        if (!(key instanceof String id) || amount <= 0) {
            return amount;
        }
        long fits = Math.min(amount, CAPACITY - total);
        if (fits <= 0) {
            return amount;
        }
        held.merge(id, fits, Long::sum);
        total += fits;
        listener.run();
        return amount - fits;
    }

    @Override
    public long extract(Object key, long amount) {
        if (!(key instanceof String id) || amount <= 0) {
            return 0;
        }
        long there = held.getOrDefault(id, 0L);
        long taken = Math.min(amount, there);
        if (taken <= 0) {
            return 0;
        }
        if (taken == there) {
            held.remove(id);
        } else {
            held.put(id, there - taken);
        }
        total -= taken;
        listener.run();
        return taken;
    }

    @Override
    public Map<?, Long> contents() {
        return Map.copyOf(held);
    }

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
