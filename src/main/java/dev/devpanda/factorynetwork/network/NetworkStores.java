package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.block.entity.DriveBlockEntity;
import dev.devpanda.factorynetwork.runtime.ResourceKind;
import dev.devpanda.factorynetwork.runtime.ResourceKinds;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * All of a network's stocks, by resource kind.
 *
 * <p>Previously every place that needs a stock carried three fields and three
 * signatures: the controller, {@code WorldHost}, {@code WorkerRuntime}. A
 * fourth store would have touched all of them once more — and that was the
 * real cost, not the class itself.
 *
 * <p>Now there is one place that records which kind is stored where. Whoever
 * needs a stock uses {@link #of(ResourceKind)}; whoever means a particular
 * kind and asks its own questions — storage buses, type slots — uses the
 * named accessors.
 *
 * <p><b>Why here and not in {@code runtime}:</b> The stock belongs to the
 * network. That this class knows {@link ResourceKind} from {@code runtime} for
 * it is the price; the counter-cost would be a second enum value in the
 * network package — exactly the twin this rework abolishes.
 */
public final class NetworkStores {

    /**
     * One store per kind, and which kinds exist is what the registry says.
     *
     * <p><b>By identity, not by equality.</b> A kind is registered once and is
     * then this one thing; two entries with the same prefix are rejected by
     * the registry. That makes {@code ==} the right question, and a foreign
     * kind that overrides {@code equals} in its own idiosyncratic way can
     * confuse nothing here.
     */
    private final Map<ResourceKind, ResourceStore> byKind = new IdentityHashMap<>();

    private final NetworkStorage items;
    private final NetworkFluids fluids;

    public NetworkStores() {
        for (ResourceKind kind : ResourceKinds.all()) {
            byKind.put(kind, kind.newStore());
        }
        // The two with their own questions — storage buses, type slots. That
        // their kind supplies this store is recorded in ResourceKinds and is
        // the only place where it has to be so.
        this.items = (NetworkStorage) byKind.get(ResourceKinds.ITEM);
        this.fluids = (NetworkFluids) byKind.get(ResourceKinds.FLUID);
    }

    /**
     * Where this kind is stored.
     *
     * <p>There is no kind without a store — {@link ResourceStore#NONE} stands
     * wherever the mod for it is missing, and it honestly answers with
     * nothing. So {@code null} never comes out of here.
     */
    public ResourceStore of(ResourceKind kind) {
        ResourceStore store = byKind.get(kind);
        return store == null ? ResourceStore.NONE : store;
    }

    /** The item store, with its own questions: buses, type slots. */
    public NetworkStorage items() {
        return items;
    }

    /** The fluid store, with its own questions: type slots. */
    public NetworkFluids fluids() {
        return fluids;
    }

    /**
     * Without Mekanism, the store that can do nothing.
     *
     * <p>This is not a stopgap: in a pack without the mod there are no
     * chemicals, and a store that pretended otherwise would be a lie with
     * side effects.
     */
    public ResourceStore chemicals() {
        return of(ResourceKinds.CHEMICAL);
    }

    /**
     * Which drives hang on the network.
     *
     * <p>The same list to all: a drive carries cells of every kind, and which
     * of them a store can read it knows itself. Previously this call stood
     * three times in a row, and on the fourth someone would have forgotten
     * it.
     */
    public void setDrives(List<DriveBlockEntity> found) {
        byKind.values().forEach(store -> store.setDrives(found));
    }
}
