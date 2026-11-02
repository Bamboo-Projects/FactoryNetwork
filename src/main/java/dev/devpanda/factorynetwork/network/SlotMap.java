package dev.devpanda.factorynetwork.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Which slot of an open block GUI is which slot for this mod, and from which
 * sides it can be reached.
 *
 * <p>The number is the one {@code slots(n)} uses: the index in the machine's
 * side-less inventory, the same one {@code ConnectorPart.machineInventoryAll}
 * reads. A side's own numbering (a furnace offers only its result slot at the
 * bottom, as slot 0 of that side) never shows up here; that mismatch was
 * fixed once already and is not to come back through the screen.
 *
 * <p>Exact where the GUI says where its slots come from: a vanilla container
 * that is the block entity itself, or a slot on the very handler the block
 * offers. Anything else is matched by order and content and marked as an
 * estimate, because a mod that builds its screen on a copy of its inventory
 * does not tell which slot of the copy is which.
 */
public final class SlotMap {

    private SlotMap() {
    }

    /**
     * One slot of the GUI.
     *
     * @param menuSlot  index in the menu's slot list, the same on both sides
     * @param number    the slot number for {@code slots(n)}, or -1 if unknown
     * @param sides     bit per {@link Direction#get3DDataValue()}: the sides
     *                  from which a connector or hopper reaches this slot
     * @param estimated whether number or sides were guessed
     */
    public record Entry(int menuSlot, int number, int sides, boolean estimated) {
    }

    /** How far a player may stand from the block to ask about it. */
    public static final double REACH = 8.0;

    /** The slots of the open menu that belong to the block at {@code pos}. */
    public static List<Entry> of(AbstractContainerMenu menu, Level level, BlockPos pos) {
        List<Entry> entries = new ArrayList<>();
        BlockEntity block = level.getBlockEntity(pos);
        if (block == null) {
            return entries;
        }
        IItemHandler all = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        @SuppressWarnings("unchecked")
        IItemHandler[] bySide = new IItemHandler[6];
        for (Direction direction : Direction.values()) {
            bySide[direction.get3DDataValue()] = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, direction);
        }

        List<Slot> machineSlots = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory)) {
                machineSlots.add(slot);
            }
        }
        // Order is the last resort: it only says something when the GUI shows
        // exactly as many slots as the machine has.
        boolean sameCount = all != null && machineSlots.size() == all.getSlots();

        for (int i = 0; i < machineSlots.size(); i++) {
            Slot slot = machineSlots.get(i);
            int menuSlot = menu.slots.indexOf(slot);
            Entry entry = exact(slot, block, all, bySide);
            if (entry == null) {
                entry = estimated(slot, sameCount ? i : -1, all, bySide);
            }
            entries.add(new Entry(menuSlot, entry.number(), entry.sides(), entry.estimated()));
        }
        return entries;
    }

    /** The two cases in which the GUI itself says where the slot comes from. */
    private static Entry exact(Slot slot, BlockEntity block, IItemHandler all,
                               IItemHandler[] bySide) {
        if (slot.container == block && block instanceof Container container) {
            int index = slot.getContainerSlot();
            // NeoForge offers a vanilla container side-less as a whole, in
            // container order; if a mod offers something else, the number is
            // not to be trusted.
            int number = all != null && all.getSlots() == container.getContainerSize() ? index : -1;
            int sides = 0;
            for (Direction direction : Direction.values()) {
                if (bySide[direction.get3DDataValue()] == null) {
                    continue;
                }
                boolean reached = !(container instanceof WorldlyContainer worldly)
                        || contains(worldly.getSlotsForFace(direction), index);
                if (reached) {
                    sides |= 1 << direction.get3DDataValue();
                }
            }
            return new Entry(-1, number, sides, false);
        }
        if (slot instanceof SlotItemHandler handlerSlot && all != null
                && handlerSlot.getItemHandler() == all) {
            int sides = 0;
            boolean guessed = false;
            for (Direction direction : Direction.values()) {
                IItemHandler side = bySide[direction.get3DDataValue()];
                if (side == all) {
                    sides |= 1 << direction.get3DDataValue();
                } else if (side != null && reachedByContent(slot, side, all.getSlots())) {
                    sides |= 1 << direction.get3DDataValue();
                    guessed = true;
                }
            }
            return new Entry(-1, slot.getContainerSlot(), sides, guessed);
        }
        return null;
    }

    /** Everything else: by order if the counts agree, otherwise by content. */
    private static Entry estimated(Slot slot, int byOrder, IItemHandler all,
                                   IItemHandler[] bySide) {
        int number = byOrder;
        if (number < 0 && all != null) {
            number = uniqueMatch(slot, all);
        }
        int sides = 0;
        if (number >= 0) {
            for (Direction direction : Direction.values()) {
                IItemHandler side = bySide[direction.get3DDataValue()];
                if (side != null && (side == all || reachedByContent(slot, side, all.getSlots()))) {
                    sides |= 1 << direction.get3DDataValue();
                }
            }
        }
        return new Entry(-1, number, sides, true);
    }

    /**
     * Whether a side reaches this slot, judged by what it offers.
     *
     * <p>A side as large as the whole inventory is taken as the whole
     * inventory. A smaller one reaches the slot if it holds the slot's stack
     * exactly once; an empty slot cannot be told apart and counts as not
     * reached rather than as reached everywhere.
     */
    private static boolean reachedByContent(Slot slot, IItemHandler side, int allSize) {
        if (side.getSlots() == allSize) {
            return true;
        }
        return uniqueMatch(slot, side) >= 0;
    }

    /** The one index holding exactly this slot's stack, or -1. */
    private static int uniqueMatch(Slot slot, IItemHandler handler) {
        if (!slot.hasItem()) {
            return -1;
        }
        ItemStack wanted = slot.getItem();
        int found = -1;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack there = handler.getStackInSlot(i);
            if (ItemStack.isSameItemSameComponents(there, wanted) && there.getCount() == wanted.getCount()) {
                if (found >= 0) {
                    return -1;
                }
                found = i;
            }
        }
        return found;
    }

    private static boolean contains(int[] values, int wanted) {
        for (int value : values) {
            if (value == wanted) {
                return true;
            }
        }
        return false;
    }
}
