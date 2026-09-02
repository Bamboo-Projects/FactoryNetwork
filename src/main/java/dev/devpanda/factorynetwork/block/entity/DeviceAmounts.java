package dev.devpanda.factorynetwork.block.entity;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * What lies in a device, summed up by type.
 *
 * <p>The baseline for {@code device_output}: if at the next look there is more
 * of a type than here, the device has put something out.
 *
 * <p><b>By type and not by slot.</b> Machines shove their contents back and
 * forth between slots — a furnace shuffles the result around, a modular
 * inventory sorts. Compared slot by slot, every reshuffle would be an output.
 * Summed up, it stays what it is: the same amount of the same type.
 *
 * <p>Inventory and tank together, for the same reason as the fingerprint for
 * {@code device_changed}: a machine can have both, and whoever waits on its
 * output wants to know about both.
 */
public record DeviceAmounts(Map<Item, Long> items, Map<Fluid, Long> fluids) {

    private static final DeviceAmounts EMPTY = new DeviceAmounts(Map.of(), Map.of());

    /** What currently lies in the device behind this connector. */
    public static DeviceAmounts of(ConnectorPart connector) {
        if (connector == null) {
            return EMPTY;
        }
        Map<Item, Long> items = new HashMap<>();
        IItemHandler handler = connector.machineInventory();
        if (handler != null) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    items.merge(stack.getItem(), (long) stack.getCount(), Long::sum);
                }
            }
        }
        Map<Fluid, Long> fluids = new HashMap<>();
        IFluidHandler tank = connector.machineTank();
        if (tank != null) {
            for (int slot = 0; slot < tank.getTanks(); slot++) {
                FluidStack stack = tank.getFluidInTank(slot);
                if (!stack.isEmpty()) {
                    fluids.merge(stack.getFluid(), (long) stack.getAmount(), Long::sum);
                }
            }
        }
        return new DeviceAmounts(items, fluids);
    }

    /**
     * Is there more of any type than before?
     *
     * <p>Only more counts. What is consumed or taken out is not an output —
     * otherwise a furnace would report every piece of coal it burns.
     */
    public boolean hasMoreThan(DeviceAmounts earlier) {
        for (Map.Entry<Item, Long> entry : items.entrySet()) {
            if (entry.getValue() > earlier.items().getOrDefault(entry.getKey(), 0L)) {
                return true;
            }
        }
        for (Map.Entry<Fluid, Long> entry : fluids.entrySet()) {
            if (entry.getValue() > earlier.fluids().getOrDefault(entry.getKey(), 0L)) {
                return true;
            }
        }
        return false;
    }
}
