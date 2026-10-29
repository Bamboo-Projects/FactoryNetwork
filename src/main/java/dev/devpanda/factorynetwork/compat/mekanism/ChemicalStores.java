package dev.devpanda.factorynetwork.compat.mekanism;

import dev.devpanda.factorynetwork.network.ResourceStore;
import dev.devpanda.factorynetwork.storage.CellView;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The path from the core to the chemicals — and the return ticket.
 *
 * <p>This class itself carries no Mekanism type; it is the door the core goes
 * through. What lies beyond is a {@link ChemicalBridge}, entered only when
 * {@link FnMekanism#installed()} is true — and only then does the JVM load the
 * classes that need Mekanism. Those live in {@code src/mekanism}; the handler
 * arithmetic the game tests check moved there into {@code MekBridge}.
 *
 * <p>This is the same pattern as with GuideME and Jade, only one layer deeper:
 * there the mod constructor decides, here every single query does.
 */
public final class ChemicalStores {

    private ChemicalStores() {
    }

    /**
     * The chemical store for a network.
     *
     * <p>Without Mekanism, the one that can do nothing. It is not a placeholder
     * but the correct answer: in a pack without the mod there are no chemicals,
     * and a store that pretended otherwise would be a lie with side effects.
     */
    public static ResourceStore create() {
        ChemicalBridge bridge = FnMekanism.bridge();
        return bridge != null ? bridge.newStore() : ResourceStore.NONE;
    }

    /**
     * Opens a chemical cell, or {@code null} without Mekanism.
     *
     * <p>The factory {@code DriveBlockEntity} receives from outside.
     */
    public static CellView open(ItemStack cell,
                               net.minecraft.core.HolderLookup.Provider registries) {
        ChemicalBridge bridge = FnMekanism.bridge();
        return bridge != null ? bridge.open(cell, registries) : null;
    }

    /**
     * How much of these chemicals is in the machine at this location.
     *
     * <p>Without Mekanism, zero — there are no chemicals there.
     */
    public static long amountAt(net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side,
            java.util.Collection<String> ids) {
        ChemicalBridge bridge = FnMekanism.bridge();
        return bridge == null ? 0 : bridge.amountAt(level, pos, side, ids);
    }

    /**
     * How much of this chemical currently fits into the machine.
     *
     * <p>The trial before filling: a recipe whose gas doesn't fully fit should
     * not even start — otherwise the machine would be left with a half-done
     * calculation. To the outside these are only identifiers and numbers; the
     * handler stays within this package.
     */
    public static long roomFor(net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side,
            String id, long amount) {
        ChemicalBridge bridge = FnMekanism.bridge();
        return bridge == null || amount <= 0 ? 0 : bridge.roomFor(level, pos, side, id, amount);
    }

    /**
     * Pulls from a machine into the network storage.
     *
     * <p><b>Ask first, then pull.</b> Whatever the store won't take must never
     * leave the container in the first place: a gas that is out and fits
     * nowhere would be gone. The same caution as with fluids.
     *
     * @return how much arrived
     */
    public static long drainInto(net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side,
            java.util.Collection<String> ids,
            ResourceStore store, long limit) {
        ChemicalBridge bridge = FnMekanism.bridge();
        return bridge == null || limit <= 0 ? 0 : bridge.drainInto(level, pos, side, ids, store, limit);
    }

    /**
     * Fills from the network storage into a machine.
     *
     * @return how much arrived
     */
    public static long fillFrom(ResourceStore store,
            net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
            net.minecraft.core.Direction side, java.util.Collection<String> ids, long limit) {
        ChemicalBridge bridge = FnMekanism.bridge();
        return bridge == null || limit <= 0 ? 0 : bridge.fillFrom(store, level, pos, side, ids, limit);
    }

    /**
     * What is in a cell, as identifier to amount.
     *
     * <p>For the tooltip: the item always exists, even without Mekanism — then
     * the answer is empty, and the tooltip says why.
     */
    public static Map<String, Long> read(ItemStack cell,
                                        net.minecraft.core.HolderLookup.Provider registries) {
        ChemicalBridge bridge = FnMekanism.bridge();
        return bridge != null ? bridge.read(cell, registries) : new LinkedHashMap<>();
    }
}
