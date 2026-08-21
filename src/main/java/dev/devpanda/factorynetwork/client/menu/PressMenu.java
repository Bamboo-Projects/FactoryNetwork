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
 * Das Fenster der Presse.
 *
 * <p>Drei Plätze mit fester Bedeutung: Stempel oben, Material links, Ausgabe
 * rechts. Aus der Ausgabe lässt sich nur nehmen — wer dort etwas hineinlegen
 * könnte, würde den nächsten Vorgang blockieren, ohne zu verstehen warum.
 *
 * <p>Fortschritt und Ladestand reisen über {@link ContainerData}. Das ist der
 * schmale Weg, den Minecraft für genau diesen Fall vorsieht: ein paar Zahlen,
 * die sich jeden Tick ändern und beim Zuschauen aktuell sein müssen.
 */
public class PressMenu extends AbstractContainerMenu {

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_REQUIRED = 1;
    public static final int DATA_ENERGY = 2;
    public static final int DATA_CAPACITY = 3;
    private static final int DATA_SIZE = 4;

    private static final int INV_X = 8;
    private static final int INV_Y = 84;
    private static final int HOTBAR_Y = 142;
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

        // Stempel: nimmt nur Stempel an. Ein falscher Gegenstand dort sieht
        // aus wie ein Fehler der Maschine, nicht wie einer des Spielers.
        addSlot(new Slot(container, PressBlockEntity.SLOT_STAMP, 44, 17) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isStamp(stack);
            }
        });
        addSlot(new Slot(container, PressBlockEntity.SLOT_MATERIAL, 44, 53));
        addSlot(new Slot(container, PressBlockEntity.SLOT_RESULT, 116, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

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

    private static boolean isStamp(ItemStack stack) {
        return stack.getItem() == dev.devpanda.factorynetwork.registry.FnItems.STAMP_PLATE.get()
                || stack.getItem() == dev.devpanda.factorynetwork.registry.FnItems.STAMP_LOGIC.get()
                || stack.getItem() == dev.devpanda.factorynetwork.registry.FnItems.STAMP_MEMORY.get()
                || stack.getItem()
                        == dev.devpanda.factorynetwork.registry.FnItems.STAMP_NETWORK.get();
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
     * Umschalt-Klick: Stempel nach oben, alles andere ins Material.
     *
     * <p>Ohne diese Unterscheidung landet ein Stempel im Materialplatz und
     * wird beim nächsten Vorgang verbraucht — ein teurer Gegenstand, weg
     * wegen eines Klicks.
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
        } else if (!moveItemStackTo(stack, PressBlockEntity.SLOT_MATERIAL,
                PressBlockEntity.SLOT_MATERIAL + 1, false)) {
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
