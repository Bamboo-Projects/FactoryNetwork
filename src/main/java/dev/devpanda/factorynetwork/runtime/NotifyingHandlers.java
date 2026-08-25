package dev.devpanda.factorynetwork.runtime;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Inventare und Tanks, die melden, wenn das Netz selbst etwas eingelegt hat.
 *
 * <p>Gebraucht wird das für {@code device_output}: Was das Netz in ein Gerät
 * legt, darf beim nächsten Blick nicht als Ausgabe des Geräts durchgehen.
 * Die Grundlinie muss also genau dann nachgezogen werden, wenn geschrieben
 * wurde.
 *
 * <p><b>Warum die Meldung am Inventar hängt und nicht an der Aufrufstelle:</b>
 * Es schreiben viele — der Interpreter für {@code move} und {@code insert},
 * die Workerlaufzeit auf drei Wegen, und beide legen zurück, wenn der
 * Netzspeicher voll ist. Jede dieser Stellen müsste daran denken, und eine
 * vergessene wäre nicht sichtbar kaputt: Ein Ablauf, der einlegt und dann
 * wartet, weckte sich selbst. Hier hängt die Meldung an dem, was geschrieben
 * wird; wer schreibt, muss nichts davon wissen.
 *
 * <p>Gemeldet wird nur, was wirklich angekommen ist. Ein Probelauf
 * ({@code simulate}) ändert nichts und meldet nichts — sonst löste die
 * Annahme-Probe des Editors Ereignisse aus.
 */
public final class NotifyingHandlers {

    private NotifyingHandlers() {
    }

    /** Dasselbe Inventar, nur meldend. Ohne Empfänger unverändert. */
    public static IItemHandler items(IItemHandler handler, Runnable onFilled) {
        return handler == null || onFilled == null ? handler : new Items(handler, onFilled);
    }

    /** Derselbe Tank, nur meldend. Ohne Empfänger unverändert. */
    public static IFluidHandler fluids(IFluidHandler handler, Runnable onFilled) {
        return handler == null || onFilled == null ? handler : new Fluids(handler, onFilled);
    }

    private record Items(IItemHandler inner, Runnable onFilled) implements IItemHandler {

        @Override
        public int getSlots() {
            return inner.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inner.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            ItemStack rest = inner.insertItem(slot, stack, simulate);
            if (!simulate && rest.getCount() < stack.getCount()) {
                onFilled.run();
            }
            return rest;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return inner.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inner.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return inner.isItemValid(slot, stack);
        }
    }

    private record Fluids(IFluidHandler inner, Runnable onFilled) implements IFluidHandler {

        @Override
        public int getTanks() {
            return inner.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return inner.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return inner.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return inner.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            int filled = inner.fill(resource, action);
            if (action.execute() && filled > 0) {
                onFilled.run();
            }
            return filled;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return inner.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return inner.drain(maxDrain, action);
        }
    }
}
