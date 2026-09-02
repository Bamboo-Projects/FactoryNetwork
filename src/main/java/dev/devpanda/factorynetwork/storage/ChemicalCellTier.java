package dev.devpanda.factorynetwork.storage;

import net.minecraft.util.StringRepresentable;

/**
 * The sizes of a chemical cell.
 *
 * <p>The same accounting as for items and fluids — so many types, so much
 * amount —, and the same type counts as the fluids: chemicals come in few
 * types and large amounts.
 *
 * <p><b>The names say kilo-millibuckets.</b> Mekanism reckons in millibuckets
 * like the game does for fluids, but on quite different orders of magnitude:
 * an electrolyser makes hundreds per second. Labelling a cell in buckets
 * would give four-digit numbers on every item.
 *
 * <p><b>The numbers are set, not derived</b> — like those on the server parts
 * (5.4). How they feel is shown by a round of play; they can be changed at
 * this one place.
 */
public enum ChemicalCellTier implements StringRepresentable, CellSize {

    K64("64k", 4, 64_000),
    K256("256k", 8, 256_000),
    K1024("1024k", 16, 1_024_000),
    K4096("4096k", 32, 4_096_000);

    private final String label;
    private final int types;
    private final long amount;

    ChemicalCellTier(String label, int types, long amount) {
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

    @Override
    public String getSerializedName() {
        return label;
    }
}
