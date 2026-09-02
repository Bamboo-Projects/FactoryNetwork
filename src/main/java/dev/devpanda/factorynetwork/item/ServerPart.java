package dev.devpanda.factorynetwork.item;

/**
 * The three parts of a server.
 *
 * <p>A bay in the rack takes exactly one of each type. <b>Only once all
 * three are inserted is it a server</b> — a computer without memory is none,
 * and a half-equipped bay should contribute nothing. That is the decision
 * this block is really about: not "how many parts", but "how many complete
 * servers".
 */
public enum ServerPart {

    /** Processor: how many flows run concurrently. */
    CPU("cpu"),

    /** Memory: how many flows may exist at all. */
    RAM("ram"),

    /** Disk: how large the program may be. */
    DISK("disk");

    private final String prefix;

    ServerPart(String prefix) {
        this.prefix = prefix;
    }

    /** The start of the registration name, such as {@code cpu_32}. */
    public String prefix() {
        return prefix;
    }

    /**
     * The tiers of this type, from small to large.
     *
     * <p>Four times as much per tier, and the number in the name is the
     * value — as with the storage cells. Whoever builds a large tier trades
     * in not only performance but above all space: a rack has twelve bays,
     * and those are what is scarce.
     */
    public int[] tiers() {
        return switch (this) {
            case CPU -> new int[] {2, 8, 32, 128};
            case RAM -> new int[] {8, 32, 128, 512};
            case DISK -> new int[] {64, 256, 1024, 4096};
        };
    }
}
