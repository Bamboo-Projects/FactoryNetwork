package dev.devpanda.factorynetwork.runtime;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * Ein Inventar, das nur bestimmte Fächer zeigt.
 *
 * <p>Damit wird aus {@code brecher_1.slots(2..3)} eine Quelle für
 * {@code move}: Alles, was von außen darauf zugreift, sieht ein Gerät mit
 * genau zwei Fächern. <b>Das ist der Punkt der ganzen Übung</b> — ein Worker,
 * der nur den Ausgang leeren soll, darf den Eingang nicht mitnehmen, und der
 * Griff dafür steht im Code und nicht in der Bauform.
 *
 * <p>Über das ungeteilte Inventar der Maschine: Wer eine Fachnummer schreibt,
 * kennt sie, und ein Anschluss je Maschine soll reichen.
 *
 * <p>Nummern, die es nicht gibt, fallen beim Bauen weg. Wie viele Fächer eine
 * fremde Maschine hat, weiß erst die Welt, und ein Bereich, der darüber
 * hinausreicht, ist kein Programmfehler.
 */
public final class SlotView implements IItemHandler {

    private final IItemHandler inner;
    private final List<Integer> slots;

    private SlotView(IItemHandler inner, List<Integer> slots) {
        this.inner = inner;
        this.slots = slots;
    }

    /** Der Blick auf diese Fächer, oder {@code null} ohne Inventar. */
    public static IItemHandler of(IItemHandler inner, List<Integer> wanted) {
        if (inner == null) {
            return null;
        }
        List<Integer> valid = wanted.stream()
                .filter(slot -> slot >= 0 && slot < inner.getSlots())
                .distinct()
                .toList();
        return new SlotView(inner, valid);
    }

    @Override
    public int getSlots() {
        return slots.size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return inner.getStackInSlot(slots.get(slot));
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return inner.insertItem(slots.get(slot), stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return inner.extractItem(slots.get(slot), amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return inner.getSlotLimit(slots.get(slot));
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return inner.isItemValid(slots.get(slot), stack);
    }
}
