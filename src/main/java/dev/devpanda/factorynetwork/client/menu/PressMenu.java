package dev.devpanda.factorynetwork.client.menu;

import dev.devpanda.factorynetwork.block.entity.PressBlockEntity;
import dev.devpanda.factorynetwork.registry.FnMenus;
import dev.devpanda.factorynetwork.storage.StorageCellItem;
import net.minecraft.core.BlockPos;
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
 * The press's window.
 *
 * <p>Three slots with fixed meaning: stamp on top, material on the left,
 * output on the right. From the output you can only take — anyone able to
 * place something there would block the next operation without understanding
 * why.
 *
 * <p>Progress and charge travel over {@link ContainerData}. That is the
 * narrow path Minecraft provides for exactly this case: a few numbers that
 * change every tick and must be current while you watch.
 */
public class PressMenu extends AbstractContainerMenu {

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_REQUIRED = 1;
    public static final int DATA_ENERGY = 2;
    public static final int DATA_CAPACITY = 3;
    private static final int DATA_SIZE = 4;

    private static final int INV_X = 8;
    private static final int INV_Y = 104;
    private static final int HOTBAR_Y = 162;
    private static final int SLOT = 18;

    private final Container container;
    private final ContainerData data;
    private final BlockPos position;

    public PressMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(),
                new SimpleContainer(PressBlockEntity.SLOTS), new SimpleContainerData(DATA_SIZE));
    }

    public PressMenu(int id, Inventory inventory, BlockPos position, Container container,
                     ContainerData data) {
        super(FnMenus.PRESS.get(), id);
        this.position = position;
        this.container = container;
        this.data = data;

        // Stamp: accepts only stamps. A wrong item there looks like a fault
        // of the machine, not one of the player.
        addSlot(new Slot(container, PressBlockEntity.SLOT_STAMP, 26, 17) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isStamp(stack);
            }
        });
        // The material slots in a row. Which slot satisfies which ingredient
        // is worked out by the recipe itself — there is no order here that
        // the player would have to keep to.
        for (int i = 0; i < PressBlockEntity.MATERIAL_SLOTS; i++) {
            addSlot(new Slot(container, PressBlockEntity.SLOT_MATERIAL + i,
                    26 + i * 18, 39) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return !isStamp(stack);
                }
            });
        }
        addSlot(new Slot(container, PressBlockEntity.SLOT_RESULT, 120, 39) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        // The upgrade slots, set apart below: what sits here does not pass
        // through — it configures.
        for (int i = 0; i < PressBlockEntity.UPGRADE_SLOTS; i++) {
            addSlot(new Slot(container, PressBlockEntity.SLOT_UPGRADE + i,
                    26 + i * 18, 67) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return dev.devpanda.factorynetwork.item.UpgradeItem
                            .upgradeOf(stack) != null;
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        INV_X + column * SLOT, INV_Y + row * SLOT));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, INV_X + column * SLOT, HOTBAR_Y));
        }
        addDataSlots(data);
    }

    /**
     * Is this a stamp?
     *
     * <p>Via the tag and not via four fixed items: a datapack may bring a
     * recipe with its own stamp, and then it too must be possible to insert.
     * The handler with which a connector feeds the press asks the same
     * question.
     */
    private static boolean isStamp(ItemStack stack) {
        return stack.is(PressBlockEntity.STAMPS);
    }

    public BlockPos position() {
        return position;
    }

    public int progress() {
        return data.get(DATA_PROGRESS);
    }

    public int required() {
        return data.get(DATA_REQUIRED);
    }

    public int energy() {
        return data.get(DATA_ENERGY);
    }

    public int capacity() {
        return Math.max(1, data.get(DATA_CAPACITY));
    }

    /**
     * Shift-click: stamp to the top, everything else into the material.
     *
     * <p>Without this distinction a stamp ends up in a material slot and is
     * consumed by the next operation — an expensive item, gone because of a
     * single click.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int maschine = PressBlockEntity.SLOTS;
        int ende = slots.size();

        if (index < maschine) {
            if (!moveItemStackTo(stack, maschine, ende, true)) {
                return ItemStack.EMPTY;
            }
        } else if (isStamp(stack)) {
            if (!moveItemStackTo(stack, PressBlockEntity.SLOT_STAMP,
                    PressBlockEntity.SLOT_STAMP + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (dev.devpanda.factorynetwork.item.UpgradeItem.upgradeOf(stack) != null) {
            // A card belongs in the upgrade slots and nowhere else. Without
            // this branch it would end up in a material slot and be something
            // there that the press tries to press.
            if (!moveItemStackTo(stack, PressBlockEntity.SLOT_UPGRADE,
                    PressBlockEntity.SLOT_UPGRADE + PressBlockEntity.UPGRADE_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, PressBlockEntity.SLOT_MATERIAL,
                PressBlockEntity.SLOT_MATERIAL + PressBlockEntity.MATERIAL_SLOTS, false)) {
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
