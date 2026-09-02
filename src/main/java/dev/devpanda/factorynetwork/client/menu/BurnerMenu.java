package dev.devpanda.factorynetwork.client.menu;

import dev.devpanda.factorynetwork.block.entity.BurnerBlockEntity;
import dev.devpanda.factorynetwork.registry.FnMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The burner's window.
 *
 * <p>One slot for fuel, a flame, and a bar for the reserve. There is nothing
 * else to operate — and that is the point: it is deliberately the simplest
 * machine in the mod.
 */
public class BurnerMenu extends AbstractContainerMenu {

    private static final int SLOT = 18;
    private static final int INV_Y = 84;
    private static final int HOTBAR_Y = 142;

    private final Container container;
    private final ContainerData data;

    public BurnerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, new SimpleContainer(BurnerBlockEntity.SLOTS),
                new SimpleContainerData(4));
    }

    public BurnerMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(FnMenus.BURNER.get(), id);
        this.container = container;
        this.data = data;

        addSlot(new Slot(container, BurnerBlockEntity.SLOT_FUEL, 80, 53) {
            /** Only what would also burn in a furnace. */
            @Override
            public boolean mayPlace(ItemStack stack) {
                return container.canPlaceItem(BurnerBlockEntity.SLOT_FUEL, stack);
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column,
                        8 + column * SLOT, INV_Y + row * SLOT));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * SLOT, HOTBAR_Y));
        }
        addDataSlots(data);
    }

    /** How far the flame has burned down, from zero to one. */
    public float burnFraction() {
        int total = data.get(BurnerBlockEntity.DATA_BURN_TOTAL);
        return total <= 0 ? 0.0F
                : Math.min(1.0F, data.get(BurnerBlockEntity.DATA_BURN) / (float) total);
    }

    /** How full the reserve is, from zero to one. */
    public float energyFraction() {
        int capacity = data.get(BurnerBlockEntity.DATA_CAPACITY);
        return capacity <= 0 ? 0.0F
                : Math.min(1.0F, data.get(BurnerBlockEntity.DATA_ENERGY) / (float) capacity);
    }

    public int stored() {
        return data.get(BurnerBlockEntity.DATA_ENERGY);
    }

    public int capacity() {
        return data.get(BurnerBlockEntity.DATA_CAPACITY);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }
}
