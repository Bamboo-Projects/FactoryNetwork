package dev.devpanda.factorynetwork.network.link;

import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;

/** {@link KeyedItems} for tanks. */
@SuppressWarnings("removal")
public interface KeyedFluids {

    IFluidHandler view(List<Fluid> fluids);

    static IFluidHandler narrow(IFluidHandler handler, List<Fluid> fluids) {
        return handler instanceof KeyedFluids keyed ? keyed.view(fluids) : handler;
    }
}
