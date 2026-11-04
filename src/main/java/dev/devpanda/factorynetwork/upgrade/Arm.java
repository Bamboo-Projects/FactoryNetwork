package dev.devpanda.factorynetwork.upgrade;

/**
 * What a connector may do in the world beyond moving goods.
 *
 * <p>Without an arm a connector only reaches into inventories. Breaking,
 * placing and hitting are worth more than a channel and a little power, so
 * each needs its upgrade in the connector — the same answer other mods give
 * with a block breaker or a deployer, without a second block shape here.
 */
public enum Arm {

    /** attack(): hits the creatures in front of the connector. */
    COMBAT("combat_upgrade"),

    /** mine(), place() and use(): works the block in front of the connector. */
    WORK("work_upgrade");

    private final String id;

    Arm(String id) {
        this.id = id;
    }

    /** The item's registry path. */
    public String id() {
        return id;
    }
}
