package dev.devpanda.factorynetwork.storage;

import net.minecraft.util.StringRepresentable;

/**
 * The sizes of an energy cell.
 *
 * <p><b>One number, not two.</b> Item and fluid cells carry two limits — how
 * many types and how much amount —, and the types are what is scarce, what
 * drives sorting. With power there are no types. An energy cell is thus
 * plainer than its siblings, and the appeal that lies in sorting for the
 * others is entirely absent here.
 *
 * <p>That is not a shortcoming but the thing itself: a battery is a number.
 * Whoever wants more inserts a larger cell or a second one alongside. That
 * is also why this enum does not implement {@link CellSize} — one of the two
 * numbers would have to be invented.
 *
 * <p>The ladder is that of the fluid cells, fourfold per tier. The smallest
 * carries a good three times the controller buffer ({@code Power.CAPACITY})
 * — enough that inserting one makes itself felt at once, and little enough
 * that the large one still means something.
 */
public enum EnergyCellTier implements StringRepresentable {

    FE64K("64k", 64_000),
    FE256K("256k", 256_000),
    FE1024K("1024k", 1_024_000),
    FE4096K("4096k", 4_096_000);

    private final String label;
    private final int capacity;

    EnergyCellTier(String label, int capacity) {
        this.label = label;
        this.capacity = capacity;
    }

    /** How much FE fits in. */
    public int capacity() {
        return capacity;
    }

    /** What is written on the cell. */
    public String label() {
        return label;
    }

    @Override
    public String getSerializedName() {
        return label;
    }
}
