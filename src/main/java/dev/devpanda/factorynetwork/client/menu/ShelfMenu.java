package dev.devpanda.factorynetwork.client.menu;

import dev.devpanda.factorynetwork.block.entity.ShelfBlockEntity;
import dev.devpanda.factorynetwork.registry.FnMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Das Fenster eines Regals — Laufwerk wie Serverschrank.
 *
 * <p>Ein Fenster für beide, weil es dieselbe Handlung ist: Bauteile in Plätze
 * stecken. Was sich unterscheidet, sind zwei Zahlen und die Frage, was
 * hineindarf — und die beantwortet das Regal selbst.
 *
 * <p>Vorher ging es nur über Klicken am Block: vorne rein, hinten raus. Zum
 * Bestücken reicht das, aber man sieht nicht, was drinsteckt, und kommt an
 * eine bestimmte Zelle nicht heran.
 */
public class ShelfMenu extends AbstractContainerMenu {

    /** So liegen die Plätze im Fenster — dieselben Zahlen wie in {@code gui.py}. */
    public record Layout(int columns, int rows, int left, int top,
                         int inventoryY, int hotbarY, int height) {

        public int slots() {
            return columns * rows;
        }
    }

    /** Das Laufwerk: zwei Spalten, fünf Reihen — wie die Schächte an der Front. */
    public static final Layout DRIVE = new Layout(2, 5, 70, 18, 121, 179, 204);

    /** Der Schrank: acht Einschübe nebeneinander, wie an seiner Front. */
    public static final Layout RACK = new Layout(8, 1, 16, 18, 49, 107, 132);

    private static final int SLOT = 18;

    private final Container container;
    private final Layout layout;
    private final boolean drive;

    public ShelfMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBoolean(), null);
    }

    private ShelfMenu(int id, Inventory inventory, boolean drive, Container shelf) {
        super(FnMenus.SHELF.get(), id);
        this.drive = drive;
        this.layout = drive ? DRIVE : RACK;
        this.container = shelf != null ? shelf : new SimpleContainer(layout.slots());

        for (int row = 0; row < layout.rows(); row++) {
            for (int column = 0; column < layout.columns(); column++) {
                int slot = row * layout.columns() + column;
                addSlot(new Slot(container, slot,
                        layout.left() + column * SLOT, layout.top() + row * SLOT) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return container.canPlaceItem(slot, stack);
                    }
                });
            }
        }
        addPlayerSlots(inventory);
    }

    /** Für den Server: das Regal selbst statt einer leeren Kiste. */
    public static ShelfMenu of(int id, Inventory inventory, ShelfBlockEntity shelf) {
        return new ShelfMenu(id, inventory, shelf.layout() == DRIVE, shelf);
    }

    public Layout layout() {
        return layout;
    }

    private void addPlayerSlots(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column,
                        8 + column * SLOT, layout.inventoryY() + row * SLOT));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * SLOT, layout.hotbarY()));
        }
    }

    /**
     * Umschalt-Klick schiebt zwischen Regal und Rucksack hin und her.
     *
     * <p>Ohne das müsste man jede Zelle einzeln ziehen — bei zehn Plätzen ist
     * das der Unterschied zwischen einem Griff und zehn.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int shelfSlots = layout.slots();
        if (index < shelfSlots) {
            // Erst herausnehmen, dann verschieben. moveItemStackTo greift
            // sonst den Gegenstand ab, bevor das Regal ihn losgelassen hat —
            // und eine Zelle schreibt ihren Bestand genau in dem Moment
            // zurück. Der Spieler bekäme eine Kopie von vorhin.
            ItemStack taken = container.removeItem(index, stack.getCount());
            if (!moveItemStackTo(taken, shelfSlots, slots.size(), true)) {
                container.setItem(index, taken);
                return ItemStack.EMPTY;
            }
            if (!taken.isEmpty()) {
                container.setItem(index, taken);
            }
            return copy;
        }
        if (!moveItemStackTo(stack, 0, shelfSlots, false)) {
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
