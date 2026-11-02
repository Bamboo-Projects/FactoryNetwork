package dev.devpanda.factorynetwork.network.link;

import net.minecraft.world.level.material.Fluid;

import java.util.List;
import java.util.function.ObjLongConsumer;

/** The fluids of a foreign storage system, asked by sort; see {@link ItemStock}. Amounts in mB. */
public interface FluidStock {

    void find(List<Fluid> fluids, ObjLongConsumer<Fluid> into);

    long insert(Fluid fluid, long amount, boolean simulate);

    long extract(Fluid fluid, long amount, boolean simulate);
}
