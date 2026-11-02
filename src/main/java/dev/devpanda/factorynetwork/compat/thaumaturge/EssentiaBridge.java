package dev.devpanda.factorynetwork.compat.thaumaturge;

import dev.devpanda.factorynetwork.network.MachineAccess;

import java.util.List;

/**
 * What the Thaumaturge side offers: the aspects there are, and access to
 * the essentia in jars and other containers.
 *
 * <p>Aspects are named by their identifier as a string, such as
 * {@code thaumaturge:ignis}: a Thaumaturge type in a signature here would
 * keep this class from loading without the mod.
 */
public interface EssentiaBridge extends MachineAccess {

    /** Every aspect, by identifier. Empty while no server runs. */
    List<String> aspects();

    /** The display name of an aspect, or its identifier. */
    String name(String id);

    /** Vis in the aura at this place. */
    double vis(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos);

    /** Flux in the aura at this place. */
    double flux(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos);

    /** The aura's base: how much vis the chunk holds when at rest. */
    double auraBase(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos);
}
