package dev.devpanda.factorynetwork.block.entity;

import dev.devpanda.factorynetwork.client.menu.ShelfMenu;
import dev.devpanda.factorynetwork.item.ServerChassis;
import dev.devpanda.factorynetwork.item.ServerPart;
import dev.devpanda.factorynetwork.item.ServerPartItem;
import dev.devpanda.factorynetwork.network.ServerBay;
import dev.devpanda.factorynetwork.registry.FnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A server rack with twelve bays.
 *
 * <p>Each bay takes a <b>server chassis</b>, and only then do the three slots
 * beside it open up for CPU, RAM and disk. A bay contributes nothing until all
 * four are in place.
 *
 * <p>The slots lie as a flat list, four per bay: first the chassis, then the
 * three parts in the order of {@link ServerPart}. They lie the same way in the
 * screen. A list of bays that each hold four slots would be the same thing
 * with an intermediate layer — and {@link ShelfBlockEntity} keeps a flat list
 * anyway.
 *
 * <p><b>The chassis takes its parts with it.</b> When it is pulled out, they
 * move into the item; when a stocked one is inserted, they come out into the
 * slots. This makes a finished server portable without ever having to open an
 * item in the backpack.
 */
public class RackBlockEntity extends ShelfBlockEntity {

    /** This many servers fit inside. */
    public static final int BAYS = 12;

    /** CPU, RAM, disk. */
    public static final int PARTS_PER_BAY = ServerPart.values().length;

    /** And the chassis in front of them. */
    public static final int SLOTS_PER_BAY = PARTS_PER_BAY + 1;

    public static final int SLOTS = BAYS * SLOTS_PER_BAY;

    public RackBlockEntity(BlockPos pos, BlockState state) {
        super(FnBlockEntities.RACK.get(), pos, state, SLOTS);
    }

    /** Which bay a slot belongs to. */
    public static int bayOf(int slot) {
        return slot / SLOTS_PER_BAY;
    }

    /** The chassis slot of a bay — the first one. */
    public static int chassisSlot(int bay) {
        return bay * SLOTS_PER_BAY;
    }

    public static boolean isChassisSlot(int slot) {
        return Math.floorMod(slot, SLOTS_PER_BAY) == 0;
    }

    /**
     * Which kind of part belongs in this slot, or {@code null} for the
     * chassis slot.
     */
    public static ServerPart partOf(int slot) {
        int within = Math.floorMod(slot, SLOTS_PER_BAY);
        return within == 0 ? null : ServerPart.values()[within - 1];
    }

    /** The slot of a particular part in a particular bay. */
    public static int slotOf(int bay, ServerPart part) {
        return chassisSlot(bay) + 1 + part.ordinal();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return ServerChassis.is(stack) || stack.getItem() instanceof ServerPartItem;
    }

    /**
     * Each slot takes only what belongs in it — and parts only when a chassis
     * is present.
     *
     * <p>The second rule makes the chassis into what it is meant to be.
     * Without it the three slots would already be the server, and the chassis
     * would be an item you buy that changes nothing.
     */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOTS) {
            return false;
        }
        if (isChassisSlot(slot)) {
            // A stocked chassis too: it unpacks on insertion.
            return ServerChassis.is(stack);
        }
        return ServerPartItem.partOf(stack) == partOf(slot)
                && !getItem(chassisSlot(bayOf(slot))).isEmpty();
    }

    /**
     * One slot, one item.
     *
     * <p>The rack used to count stacks, and a stack of sixteen processors in
     * one slot was sixteen times the performance. That made the slots no
     * longer a limit, but a formality.
     */
    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public ShelfMenu.Layout layout() {
        return ShelfMenu.RACK;
    }

    public NonNullList<ItemStack> contents() {
        return parts();
    }

    // ---- The chassis takes with it what is inside -------------------------

    /**
     * Before a chassis leaves the slot, it packs up.
     *
     * <p>This spot is the only way out: {@code removeItem} goes through
     * {@code setItem}, and that calls in here <b>while the old item is still
     * present</b>. The caller then gets exactly this piece of sheet metal in
     * hand — with the parts inside it.
     */
    @Override
    protected void beforeSlotChange(int slot) {
        if (!isChassisSlot(slot)) {
            return;
        }
        ItemStack chassis = getItem(slot);
        if (!chassis.isEmpty()) {
            packInto(chassis, bayOf(slot));
        }
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        super.setItem(slot, stack);
        if (isChassisSlot(slot) && !stack.isEmpty()) {
            unpackFrom(stack, bayOf(slot));
        }
    }

    /**
     * Writes the three parts of a bay into the chassis and empties the slots.
     *
     * <p><b>Written directly into the list</b> and not through
     * {@code setItem}: that would call in here again, and a cleanup that calls
     * itself is a cleanup you can no longer keep track of.
     */
    private void packInto(ItemStack chassis, int bay) {
        List<ItemStack> taken = new ArrayList<>(PARTS_PER_BAY);
        boolean anything = false;
        for (ServerPart part : ServerPart.values()) {
            ItemStack stack = getItem(slotOf(bay, part));
            taken.add(stack);
            anything |= !stack.isEmpty();
        }
        if (!anything) {
            return;
        }
        ServerChassis.write(chassis, taken);
        for (ServerPart part : ServerPart.values()) {
            parts().set(slotOf(bay, part), ItemStack.EMPTY);
        }
        bumpRevision();
        setChanged();
    }

    /** And the reverse way, as soon as a chassis has been inserted. */
    private void unpackFrom(ItemStack chassis, int bay) {
        if (ServerChassis.isEmpty(chassis)) {
            return;
        }
        NonNullList<ItemStack> stored = ServerChassis.read(chassis);
        for (ServerPart part : ServerPart.values()) {
            ItemStack stack = stored.get(part.ordinal());
            // Only what belongs in it. A chassis from creative mode can
            // contain anything, and a CPU in the disk slot would be a bay
            // that looks full and does not run.
            parts().set(slotOf(bay, part),
                    ServerPartItem.partOf(stack) == part ? stack : ItemStack.EMPTY);
        }
        ServerChassis.write(chassis, List.of());
        bumpRevision();
        setChanged();
    }

    /**
     * Packs up all bays — before the rack is broken.
     *
     * <p>Afterwards no loose parts remain in the part slots, and what falls
     * out are twelve finished servers instead of forty-eight individual
     * pieces.
     */
    public void packAll() {
        for (int bay = 0; bay < BAYS; bay++) {
            ItemStack chassis = getItem(chassisSlot(bay));
            if (!chassis.isEmpty()) {
                packInto(chassis, bay);
            }
        }
    }

    // ---- What the rack carries --------------------------------------------

    /** Is there a chassis in this bay? */
    public boolean hasChassis(int bay) {
        return bay >= 0 && bay < BAYS && !getItem(chassisSlot(bay)).isEmpty();
    }

    /**
     * What sits in a bay.
     *
     * <p>Nothing without a chassis — even if, against expectation, parts lay
     * there. The rule thus stands in one place and not in three.
     */
    public ServerBay bay(int bay) {
        if (!hasChassis(bay)) {
            return ServerBay.EMPTY;
        }
        return ServerBay.of(
                getItem(slotOf(bay, ServerPart.CPU)),
                getItem(slotOf(bay, ServerPart.RAM)),
                getItem(slotOf(bay, ServerPart.DISK)));
    }

    /** The sum of the complete bays. */
    public ServerBay capacity() {
        ServerBay total = ServerBay.EMPTY;
        for (int bay = 0; bay < BAYS; bay++) {
            total = total.plus(bay(bay).contribution());
        }
        return total;
    }

    /** How many bays are running. */
    public int runningBays() {
        int count = 0;
        for (int bay = 0; bay < BAYS; bay++) {
            if (bay(bay).complete()) {
                count++;
            }
        }
        return count;
    }

    /**
     * How many bays are started and not finished.
     *
     * <p>A chassis without hardware counts here: from the outside it looks
     * like a server and is none.
     */
    public int incompleteBays() {
        int count = 0;
        for (int bay = 0; bay < BAYS; bay++) {
            if (hasChassis(bay) && !bay(bay).complete()) {
                count++;
            }
        }
        return count;
    }

    /** How many concurrent flows this rack carries. */
    public int threads() {
        return capacity().cpu();
    }
}
