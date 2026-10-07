package dev.devpanda.factorynetwork.compat.mekanism;

import dev.devpanda.factorynetwork.network.MachineAccess;
import dev.devpanda.factorynetwork.network.ResourceStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.Collection;

/**
 * Chemicals at a machine, behind the interface every foreign kind uses.
 *
 * <p><b>A wrapper and nothing else.</b> The three actions already existed in
 * {@link ChemicalStores}; the core used to call them by name. Now it asks the
 * kind for its access like it does for any other mod's resource, and this is
 * what the chemical kind answers with.
 *
 * <p>The keys are identifiers as text, as everywhere outside this package.
 */
public final class MekChemicalAccess implements MachineAccess {

    public static final MekChemicalAccess INSTANCE = new MekChemicalAccess();

    private MekChemicalAccess() {
    }

    @Override
    public long count(Level level, BlockPos pos, Direction side, Collection<?> keys) {
        return ChemicalStores.amountAt(level, pos, side, ids(keys));
    }

    @Override
    public long fill(ResourceStore from, Level level, BlockPos pos, Direction side,
                     Collection<?> keys, long limit) {
        return ChemicalStores.fillFrom(from, level, pos, side, ids(keys), limit);
    }

    @Override
    public long drain(Level level, BlockPos pos, Direction side,
                      Collection<?> keys, ResourceStore into, long limit) {
        return ChemicalStores.drainInto(level, pos, side, ids(keys), into, limit);
    }

    /** The kind's keys are strings; {@code ResourceKind.type()} promises it. */
    @SuppressWarnings("unchecked")
    private static Collection<String> ids(Collection<?> keys) {
        return (Collection<String>) keys;
    }
}
