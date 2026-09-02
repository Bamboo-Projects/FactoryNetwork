package dev.devpanda.factorynetwork.storage;

import net.minecraft.util.StringRepresentable;

/**
 * The sizes of a fluid cell.
 *
 * <p>The same accounting as for the item cells — so many types, so much
 * amount —, but different numbers: fluids come in fewer types and larger
 * amounts. Four types in the smallest are enough for water, lava and two
 * from the pack; sixty-four would be a slot nobody ever fills.
 *
 * <p><b>The names say buckets, not kilo.</b> With the item cells the number
 * in the name matches the contents, and the price follows it — a 64k costs
 * sixty-four small ones. A fluid cell "1k" with sixty-four buckets would not
 * have that honesty.
 *
 * <p>The numbers are set, not derived. They can be changed at one place if
 * the game feels different than intended.
 */
public enum FluidCellTier implements StringRepresentable, CellSize {

    B64("64", 4, 64_000),
    B256("256", 8, 256_000),
    B1024("1024", 16, 1_024_000),
    B4096("4096", 32, 4_096_000);

    private final String label;
    private final int types;
    private final long amount;

    FluidCellTier(String label, int types, long amount) {
        this.label = label;
        this.types = types;
        this.amount = amount;
    }

    @Override
    public int types() {
        return types;
    }

    /** How many millibuckets fit in total. */
    @Override
    public long amount() {
        return amount;
    }

    @Override
    public String label() {
        return label;
    }

    /** How many buckets that is — the number written on the cell. */
    public long buckets() {
        return amount / 1000;
    }

    @Override
    public String getSerializedName() {
        return label;
    }
}
