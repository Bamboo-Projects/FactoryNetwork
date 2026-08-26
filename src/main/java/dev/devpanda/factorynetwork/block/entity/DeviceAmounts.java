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
 * Was in einem Gerät liegt, nach Art zusammengezählt.
 *
 * <p>Die Grundlinie für {@code device_output}: Ist beim nächsten Blick von
 * einer Art mehr da als hier, hat das Gerät etwas ausgegeben.
 *
 * <p><b>Nach Art und nicht nach Fach.</b> Maschinen schieben ihren Inhalt
 * zwischen Fächern hin und her — ein Ofen räumt das Ergebnis um, ein
 * Modulinventar sortiert. Fachweise verglichen wäre jedes Umräumen eine
 * Ausgabe. Zusammengezählt bleibt es, was es ist: dieselbe Menge an derselben
 * Art.
 *
 * <p>Inventar und Tank zusammen, aus demselben Grund wie beim Fingerabdruck
 * für {@code device_changed}: Eine Maschine kann beides haben, und wer auf
 * ihre Ausgabe wartet, will von beidem wissen.
 */
public record DeviceAmounts(Map<Item, Long> items, Map<Fluid, Long> fluids) {

    private static final DeviceAmounts EMPTY = new DeviceAmounts(Map.of(), Map.of());

    /** Was gerade in dem Gerät hinter diesem Anschluss liegt. */
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
     * Ist von irgendeiner Art mehr da als vorhin?
     *
     * <p>Nur mehr zählt. Was verbraucht oder herausgenommen wird, ist keine
     * Ausgabe — sonst meldete ein Ofen jedes Stück Kohle, das er verbrennt.
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
