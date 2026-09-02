package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.block.entity.DriveBlockEntity;

import java.util.List;
import java.util.Map;

/**
 * The network's view of a stock.
 *
 * <p>Items, fluids and chemicals all sit in cells in drives, and the view of
 * them was three times the same class with different types: an index over all
 * cells, two passes when storing, a comparison of the drive states when
 * checking. Here the questions stand once; the answers stand in
 * {@link NetworkStorage}, {@link NetworkFluids} and in the chemical store
 * under {@code compat/mekanism}.
 *
 * <p><b>The key is an {@code Object}</b>, as in the value model: an
 * {@code Item}, a {@code Fluid} or — for a chemical — the identifier as text.
 * A common supertype would exist only if all three came from the same hand,
 * and a {@link String} comes from none. Which form belongs to which kind is
 * told by {@code ResourceKind.type()}; whoever passes in a wrong one gets a
 * {@link ClassCastException} and not a silent zero.
 *
 * <p><b>Why an interface and not just a shared superclass:</b> the chemical
 * store touches Mekanism types. A class that did so at its core could not be
 * loaded in a pack without Mekanism, and it would take the whole controller
 * down with it. That is why the question lives here and the answer there — and
 * why {@link #NONE} exists.
 */
public interface ResourceStore {

    /** How much of it is in the network. In pieces for items, otherwise millibuckets. */
    long count(Object key);

    /**
     * How much of it would still go in.
     *
     * <p>Needed <b>before</b> a tank is emptied: what the store does not take
     * must not come out in the first place. With items the rest can be put
     * back; a gas that is out and whose tank no longer takes it back by then
     * would be gone.
     */
    long room(Object key, long wanted);

    /** Stores it and reports what did <b>not</b> fit. */
    long insert(Object key, long amount);

    /** Extracts and reports how much really came. */
    long extract(Object key, long amount);

    /**
     * The whole stock.
     *
     * <p>A copy and not a view: the stock is often iterated while something is
     * moved on the side. The keys have the form that belongs to the kind —
     * that is why {@code ?} stands here and not {@code Object}: whoever knows
     * the kind may take the map typed.
     */
    Map<?, Long> contents();

    /** Which drives hang on the network. Set by the controller on rebuild. */
    void setDrives(List<DriveBlockEntity> drives);

    /** Whether there is any room at all. Without a drive a network stores nothing. */
    boolean hasDrives();

    /** Called when something has changed. */
    void setChangeListener(Runnable listener);

    /**
     * A store that does not exist.
     *
     * <p>It accepts nothing, gives nothing out and has no drives. This is not
     * a stopgap, but the truth about a pack without the mod that brings this
     * resource kind.
     */
    ResourceStore NONE = new ResourceStore() {

        @Override
        public long count(Object key) {
            return 0;
        }

        @Override
        public long room(Object key, long wanted) {
            return 0;
        }

        @Override
        public long insert(Object key, long amount) {
            return amount;
        }

        @Override
        public long extract(Object key, long amount) {
            return 0;
        }

        @Override
        public Map<?, Long> contents() {
            return Map.of();
        }

        @Override
        public void setDrives(List<DriveBlockEntity> drives) {
        }

        @Override
        public boolean hasDrives() {
            return false;
        }

        @Override
        public void setChangeListener(Runnable listener) {
        }
    };
}
