package dev.devpanda.factorynetwork.storage;

import net.minecraft.util.StringRepresentable;

/**
 * The sizes of the essentia cell.
 *
 * <p>Essentia comes in small amounts: a Thaumaturge jar holds 250. The
 * smallest cell holds sixteen jars, the largest a thousand; the number of
 * aspects grows with it, up to all of them.
 */
public enum EssentiaCellTier implements StringRepresentable, CellSize {

    K4("4k", 8, 4_000),
    K16("16k", 16, 16_000),
    K64("64k", 32, 64_000),
    K256("256k", 64, 256_000);

    private final String label;
    private final int types;
    private final long amount;

    EssentiaCellTier(String label, int types, long amount) {
        this.label = label;
        this.types = types;
        this.amount = amount;
    }

    @Override
    public int types() {
        return types;
    }

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
