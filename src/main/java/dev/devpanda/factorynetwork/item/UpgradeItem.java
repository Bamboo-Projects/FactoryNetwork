package dev.devpanda.factorynetwork.item;

import dev.devpanda.factorynetwork.upgrade.Upgrade;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Ein Modul oder eine Karte als Gegenstand.
 *
 * <p>Er trägt keine Daten: Was er tut, steht in seinem {@link Upgrade}, und
 * das hängt an der Gegenstandsart. Zwei Reichweitenkarten sind dasselbe und
 * stapeln sich deshalb.
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

    /** Welcher Ausbau in diesem Stapel steckt, oder {@code null}. */
    public static Upgrade upgradeOf(ItemStack stack) {
        return stack.getItem() instanceof UpgradeItem item ? item.upgrade() : null;
    }
}
