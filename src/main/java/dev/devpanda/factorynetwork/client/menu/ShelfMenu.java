package dev.devpanda.factorynetwork.client.menu;

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
 * The window of a shelf — drive as well as server rack.
 *
 * <p>One window for both, because it is the same action: putting components
 * into slots. What differs is two numbers and the question of what may go in
 * — and the shelf answers that itself.
 *
 * <p>Before, this was only possible by clicking on the block: in the front,
 * out the back. For stocking that is enough, but you cannot see what is
 * inside and cannot reach a particular cell.
 */
public class ShelfMenu extends AbstractContainerMenu {

    /**
     * How the slots are laid out in the window — the same numbers as in {@code gui.py}.
     *
     * <p>{@code group} and {@code gap} bundle columns into blocks: the rack
     * places four slots side by side as one bay and then leaves a gap.
     * {@code innerAfter} and {@code innerGap} add one more, smaller gap
     * within a block — after the chassis, so that the three components beside
     * it do not look like a fourth.
     *
     * <p>The width comes along with it: a rack with four slots per bay does
     * not fit into the 176 of a chest.
     */
    public record Layout(int width, int height, int columns, int rows,
                         int left, int top, int inventoryY, int hotbarY,
                         int group, int gap, int innerAfter, int innerGap) {

        /** A shelf without grouping — a plain grid. */
        public Layout(int width, int height, int columns, int rows,
                      int left, int top, int inventoryY, int hotbarY) {
            this(width, height, columns, rows, left, top, inventoryY, hotbarY, 0, 0, 0, 0);
        }

        public int slots() {
            return columns * rows;
        }

        /** Where a column begins, with the gaps already accounted for. */
        public int columnX(int column) {
            int gaps = group > 0 ? (column / group) * gap : 0;
            int inner = group > 0 && innerGap > 0 && column % group > innerAfter
                    ? innerGap : 0;
            return left + column * SLOT + gaps + inner;
        }

        public int rowY(int row) {
            return top + row * SLOT;
        }

        /** The player inventory sits centred, no matter how wide the window is. */
        public int inventoryX() {
            return (width - 9 * SLOT) / 2 + 1;
        }
    }

    /** The drive: two columns, five rows — like the bays on the front. */
    public static final Layout DRIVE = new Layout(176, 204, 2, 5, 70, 18, 121, 179);

    /**
     * The rack: twelve bays, in two columns of six.
     *
     * <p>Four slots per bay — the chassis and its three components — and the
     * indicator light behind them. Twelve rows one below another would be 216
     * pixels for the slots alone, more than a window is allowed to be tall.
     */
    public static final Layout RACK =
            new Layout(192, 222, 8, 6, 12, 18, 139, 197, 4, 12, 0, 4);

    /**
     * The transmitter mast: four slots in a row.
     *
     * <p>No groups, no indicator lights — four slots are four slots. The
     * numbers come from {@code tools/gui.py}, which draws the face and prints
     * them on its run.
     */
    public static final Layout MAST = new Layout(176, 132, 4, 1, 52, 18, 49, 107);

    private static final int SLOT = 18;

    /**
     * The layouts in the order in which they go over the wire.
     *
     * <p><b>A byte and not a yes-no.</b> As long as there were two shelves, a
     * bit was enough; with the transmitter mast there are three, and the next
     * would be the fourth. Whoever adds a layout here appends it at the end —
     * the number is written nowhere else.
     */
    private static final Layout[] KINDS = {DRIVE, RACK, MAST};

    private final Container container;
    private final Layout layout;

    public ShelfMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, KINDS[buffer.readByte()], null);
    }

    private ShelfMenu(int id, Inventory inventory, Layout layout, Container shelf) {
        super(FnMenus.SHELF.get(), id);
        this.layout = layout;
        this.container = shelf != null ? shelf : placeholder(layout, layout.slots());

        for (int row = 0; row < layout.rows(); row++) {
            for (int column = 0; column < layout.columns(); column++) {
                int slot = row * layout.columns() + column;
                addSlot(new Slot(container, slot,
                        layout.columnX(column), layout.rowY(row)) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return container.canPlaceItem(slot, stack);
                    }
                });
            }
        }
        addPlayerSlots(inventory);
    }

    /**
     * The box the client uses until the contents arrive.
     *
     * <p>It must know the same rules as the shelf on the server — otherwise
     * the client shows a processor in the storage slot until the server
     * disagrees, and that looks like a bug.
     */
    private static Container placeholder(Layout layout, int slots) {
        if (layout == DRIVE) {
            return new SimpleContainer(slots);
        }
        if (layout == MAST) {
            // At the mast the same rule applies as everywhere in the upgrade
            // system: whatever is not a module and not a card does not go in.
            return new SimpleContainer(slots) {
                @Override
                public boolean canPlaceItem(int slot, ItemStack stack) {
                    return dev.devpanda.factorynetwork.item.UpgradeItem
                            .upgradeOf(stack) != null;
                }
            };
        }
        return new SimpleContainer(slots) {
            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                if (dev.devpanda.factorynetwork.block.entity.RackBlockEntity
                        .isChassisSlot(slot)) {
                    return dev.devpanda.factorynetwork.item.ServerChassis.is(stack);
                }
                return dev.devpanda.factorynetwork.item.ServerPartItem.partOf(stack)
                        == dev.devpanda.factorynetwork.block.entity.RackBlockEntity
                                .partOf(slot);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        };
    }

    /**
     * For the server: the container itself instead of an empty box.
     *
     * <p>Takes a {@link Container} and not a {@code ShelfBlockEntity}: the
     * transmitter mast is neither of the two shelves, but holds its slots
     * just the same.
     */
    public static ShelfMenu of(int id, Inventory inventory, Container container,
                               Layout layout) {
        return new ShelfMenu(id, inventory, layout, container);
    }

    /** Which layout this window has, as a number for the wire. */
    public static int kindOf(Layout layout) {
        for (int index = 0; index < KINDS.length; index++) {
            if (KINDS[index] == layout) {
                return index;
            }
        }
        throw new IllegalArgumentException("unbekannter Zuschnitt");
    }

    public Layout layout() {
        return layout;
    }

    private void addPlayerSlots(Inventory inventory) {
        int left = layout.inventoryX();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column,
                        left + column * SLOT, layout.inventoryY() + row * SLOT));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, left + column * SLOT, layout.hotbarY()));
        }
    }

    /**
     * Shift-click moves back and forth between the shelf and the backpack.
     *
     * <p>Without it you would have to drag every cell one at a time — with
     * ten slots that is the difference between one grab and ten.
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
            // Take it out first, then move it. Otherwise moveItemStackTo
            // grabs the item before the shelf has let go of it — and a cell
            // writes its contents back at exactly that moment. The player
            // would get a copy of what was there before.
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
