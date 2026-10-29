package dev.devpanda.factorynetwork.compat.ars;

import dev.devpanda.factorynetwork.network.MachineAccess;

/**
 * Access to Source in machines, from the side that knows Ars Nouveau.
 *
 * <p>Only a name for {@code ServiceLoader}: the implementation lives in the
 * source set {@code src/ars}, which the build compiles only where Ars Nouveau
 * exists for that Minecraft version. Without it, {@link ArsSource} answers
 * with an access that finds nothing, as in a pack without the mod.
 */
public interface SourceBridge extends MachineAccess {
}
