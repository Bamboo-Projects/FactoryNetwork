package dev.devpanda.factorynetwork.terminal;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Slots outside the inventory where a player can carry a wireless device,
 * such as Curios slots.
 *
 * <p>Filled by an adapter under {@code compat} when its mod is loaded; empty
 * otherwise. A device worn there is addressed with a negative slot number,
 * {@code -(index + 1)}, so that inventory slots keep their meaning.
 */
public final class WornDevices {

    /** What a mod with extra slots offers. */
    public interface Source {
        int count(Player player);

        ItemStack get(Player player, int index);
    }

    private static Source source;

    private WornDevices() {
    }

    public static void install(Source value) {
        source = value;
    }

    public static int count(Player player) {
        return source == null ? 0 : source.count(player);
    }

    public static ItemStack get(Player player, int index) {
        return source == null || index < 0 || index >= count(player) ? ItemStack.EMPTY
                : source.get(player, index);
    }
}
