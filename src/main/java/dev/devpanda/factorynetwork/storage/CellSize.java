package dev.devpanda.factorynetwork.storage;

/**
 * The two limits of a cell.
 *
 * <p>Items and fluids have different sizes but the same accounting: so many
 * types, so much amount. The limit is always the type — whoever throws
 * everything into one cell has it full long before the amount is reached.
 */
public interface CellSize {

    /** How many different types fit in. */
    int types();

    /** How much fits in total — items or millibuckets. */
    long amount();

    /** What is written on the cell. */
    String label();
}
