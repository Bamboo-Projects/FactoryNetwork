package dev.devpanda.factorynetwork.item;

import dev.devpanda.factorynetwork.upgrade.Upgrade;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A module or a card as an item.
 *
 * <p>It carries no data: what it does is in its {@link Upgrade}, and that
 * hangs on the item type. Two range cards are the same and therefore stack.
 */
public class UpgradeItem extends Item {

    private final Upgrade upgrade;

    public UpgradeItem(Properties properties, Upgrade upgrade) {
        super(properties);
        this.upgrade = upgrade;
    }

    public Upgrade upgrade() {
        return upgrade;
    }

    /** Which upgrade sits in this stack, or {@code null}. */
    public static Upgrade upgradeOf(ItemStack stack) {
        return stack.getItem() instanceof UpgradeItem item ? item.upgrade() : null;
    }
}
