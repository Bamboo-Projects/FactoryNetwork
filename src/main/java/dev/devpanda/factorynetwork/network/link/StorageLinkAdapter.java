package dev.devpanda.factorynetwork.network.link;

import org.jetbrains.annotations.Nullable;

/**
 * One foreign storage system behind a storage link: AE2, Refined Storage, …
 *
 * <p>Each lives under {@code compat} and is registered with
 * {@link StorageLinks} only when its mod is loaded. The link block itself
 * knows none of them.
 */
public interface StorageLinkAdapter {

    /** For messages and the tooltip, such as "Applied Energistics 2". */
    String systemName();

    /** The items, or {@code null} while the system is not reachable. */
    @Nullable ItemStock items();

    /** The fluids, or {@code null} while unreachable or not supported. */
    @Nullable FluidStock fluids();

    /** Called every server tick of the link, for systems that need it. */
    default void tick() {
    }

    /** The link leaves the world: give back what the system was given. */
    void remove();
}
