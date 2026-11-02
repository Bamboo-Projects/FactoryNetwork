package dev.devpanda.factorynetwork.compat.curios;

import dev.devpanda.factorynetwork.terminal.WornDevices;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Wireless devices in Curios slots.
 *
 * <p>The slot itself comes from data: {@code curios/entities} gives players
 * a charm slot, the {@code curios:charm} tag lets the devices in. This class
 * only tells the terminal where to look.
 */
public final class FnCurios {

    private FnCurios() {
    }

    /** Called from the entry point, only when Curios is loaded. */
    public static void register() {
        WornDevices.install(new WornDevices.Source() {
            @Override
            @SuppressWarnings("removal")
            public int count(Player player) {
                var inventory = CuriosApi.getCuriosInventoryOrNull(player);
                return inventory == null ? 0 : inventory.getEquippedCurios().getSlots();
            }

            @Override
            @SuppressWarnings("removal")
            public ItemStack get(Player player, int index) {
                var inventory = CuriosApi.getCuriosInventoryOrNull(player);
                return inventory == null ? ItemStack.EMPTY
                        : inventory.getEquippedCurios().getStackInSlot(index);
            }
        });
    }
}
