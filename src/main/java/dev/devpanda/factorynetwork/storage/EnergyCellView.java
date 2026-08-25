package dev.devpanda.factorynetwork.storage;

import net.minecraft.world.item.ItemStack;

/**
 * Eine Energiezelle als Speicher.
 *
 * <p>Sie hält ihre Ladung im Arbeitsspeicher und schreibt erst zurück, wenn
 * das Laufwerk sichert oder die Zelle herausgeht — aus demselben Grund wie
 * bei den anderen Zellen: Der Vorrat ändert sich jeden Tick, und jeden Tick
 * einen Gegenstand neu zu beschreiben ist Arbeit für nichts.
 *
 * <p>Vom Rest der Zellenwelt unterscheidet sie sich in einem Punkt, und der
 * steht schon in {@link EnergyCellTier}: <b>Es gibt keine Sorten.</b> Damit
 * fällt die ganze Rechnung weg, die {@link CellInventory} ausmacht, und übrig
 * bleiben zwei Zeilen — annehmen und abgeben.
 */
public final class EnergyCellView implements CellView {

    private final ItemStack cell;
    private final EnergyCellTier tier;
    private int charge;

    private EnergyCellView(ItemStack cell) {
        this.cell = cell;
        this.tier = EnergyCellItem.tierOf(cell);
        this.charge = tier == null ? 0 : EnergyCellItem.chargeOf(cell);
    }

    /** Ungültig, wenn dort keine Energiezelle steckt. */
    public static EnergyCellView of(ItemStack cell) {
        return new EnergyCellView(cell);
    }

    @Override
    public ItemStack stack() {
        return cell;
    }

    @Override
    public boolean isValid() {
        return tier != null;
    }

    public int stored() {
        return charge;
    }

    public int capacity() {
        return tier == null ? 0 : tier.capacity();
    }

    public int room() {
        return capacity() - charge;
    }

    /** Nimmt an, was hineinpasst, und sagt, wie viel es wurde. */
    public int fill(int amount) {
        int taken = Math.max(0, Math.min(amount, room()));
        charge += taken;
        return taken;
    }

    /** Gibt her, was da ist, und sagt, wie viel es wurde. */
    public int take(int amount) {
        int given = Math.max(0, Math.min(amount, charge));
        charge -= given;
        return given;
    }

    @Override
    public void flush() {
        EnergyCellItem.setCharge(cell, charge);
    }
}
