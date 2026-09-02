package dev.devpanda.factorynetwork.storage;

import net.minecraft.util.StringRepresentable;

/**
 * The sizes of a storage cell.
 *
 * <p><b>Two limits instead of a byte reckoning.</b> Applied Energistics
 * counts bytes: each type costs eight up front, then one byte holds eight
 * items. That grew over time and is understood by hardly anyone — you only
 * notice that a cell is full sooner than the number in its name suggests.
 *
 * <p>Here both limits are out in the open: how many <b>types</b> and how many
 * <b>items</b>. The appeal stays the same, because the types are what is
 * scarce — whoever throws everything into one cell has it full long before
 * the amount is reached. That is exactly what drives sorting.
 */
public enum CellTier implements StringRepresentable, CellSize {

    K1("1k", 8, 8_000),
    K4("4k", 16, 32_000),
    K16("16k", 32, 128_000),
    K64("64k", 64, 512_000);

    private final String label;
    private final int types;
    private final long amount;

    CellTier(String label, int types, long amount) {
        this.label = label;
        this.types = types;
        this.amount = amount;
    }

    /** How many different types fit in. */
    @Override
    public int types() {
        return types;
    }

    /** How many items fit in total. */
    @Override
    public long amount() {
        return amount;
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
