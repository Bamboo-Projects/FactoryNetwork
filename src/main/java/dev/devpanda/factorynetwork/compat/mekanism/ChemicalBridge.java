package dev.devpanda.factorynetwork.compat.mekanism;

import dev.devpanda.factorynetwork.network.ResourceStore;
import dev.devpanda.factorynetwork.storage.CellView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Everything the gate needs from Mekanism, without a single Mekanism type.
 *
 * <p><b>Why an interface and not direct calls.</b> The classes that touch
 * Mekanism's API live in a source set of their own ({@code src/mekanism}),
 * which the build only compiles where Mekanism exists for that Minecraft
 * version. On 26.1.2 there is no Mekanism, the source set falls away, and
 * nothing in {@code src/main} notices: the gate asks for this interface
 * through {@code ServiceLoader} and, finding none, behaves as in a pack
 * without the mod.
 *
 * <p>Asked only after {@link FnMekanism#installed()} said yes, so the
 * implementation is never loaded in a pack without Mekanism.
 */
public interface ChemicalBridge {

    // ---- Registry -------------------------------------------------------------

    boolean has(Identifier id);

    List<Identifier> keys();

    String name(Identifier id);

    // ---- Storage ---------------------------------------------------------------

    ResourceStore newStore();

    CellView open(ItemStack cell, HolderLookup.Provider registries);

    Map<String, Long> read(ItemStack cell, HolderLookup.Provider registries);

    // ---- Machines --------------------------------------------------------------

    long amountAt(Level level, BlockPos pos, Direction side, Collection<String> ids);

    long roomFor(Level level, BlockPos pos, Direction side, String id, long amount);

    long drainInto(Level level, BlockPos pos, Direction side, Collection<String> ids,
                   ResourceStore store, long limit);

    long fillFrom(ResourceStore store, Level level, BlockPos pos, Direction side,
                  Collection<String> ids, long limit);
}
