package dev.devpanda.factorynetwork.runtime;

import dev.devpanda.factorynetwork.storage.ItemKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The two transfers by which goods go into a device and out again, everywhere.
 *
 * <p>They live here and not once each in the worker and the interpreter,
 * because both must keep the same order: <b>insert first, then extract.</b>
 * The other way round, the goods would already be out of the source when the
 * target refuses them — and a machine that gives up its fuel slot does not
 * refill it.
 *
 * <p>The order has a price, and that price is {@link #pullBack}: if the
 * source delivers less on the real transfer than it promised, the difference
 * is already in the target. It came out of nothing and has to come back out.
 */
public final class Handoffs {

    private Handoffs() {
    }

    /**
     * What a transfer moved and what was noticed along the way.
     *
     * @param moved     how much actually went from the source into the target
     * @param targetFull whether the target stopped accepting
     * @param stranded  how much sits in the target without ever having come
     *                  from the source — see {@link #pullBack}
     */
    public record Handoff(long moved, boolean targetFull, long stranded) {
    }

    /**
     * From one device into another, as much as the target takes.
     *
     * <p>The order is the same as everywhere in this codebase: insert first,
     * then extract. Whatever the source yields on the real transfer short of
     * what the dry run promised, {@link #pullBack} fetches back out of the
     * target.
     */
    public static Handoff items(IItemHandler in, IItemHandler out,
                                java.util.List<net.minecraft.world.item.Item> filter,
                                long limit) {
        long moved = 0;
        long stranded = 0;
        for (int slot = 0; slot < in.getSlots() && moved < limit; slot++) {
            ItemStack stack = in.getStackInSlot(slot);
            if (stack.isEmpty() || (!filter.isEmpty() && !filter.contains(stack.getItem()))) {
                continue;
            }
            int wanted = (int) Math.min(limit - moved, stack.getCount());
            ItemStack simulated = in.extractItem(slot, wanted, true);
            ItemStack rest = insertInto(out, simulated);
            int accepted = simulated.getCount() - rest.getCount();
            if (accepted <= 0) {
                return new Handoff(moved, true, stranded);
            }
            // What the transfer actually yields, not what the dry run
            // promised. The difference is already in the target and came out
            // of nothing.
            int taken = in.extractItem(slot, accepted, false).getCount();
            if (taken < accepted) {
                stranded += pullBack(out, ItemKey.of(simulated), accepted - taken);
            }
            moved += taken;
        }
        return new Handoff(moved, false, stranded);
    }

    /**
     * One transfer from one tank into another.
     *
     * <p>A single transfer and no loop: what an empty or unwilling tank means
     * is decided differently by the two callers — the worker stops, the
     * interpreter moves on to the next kind. That decision belongs to them,
     * not here.
     *
     * <p>The way back is narrower here than with items: a drained fluid
     * cannot be put back into the source, because a tank is not obliged to
     * accept it again. Whatever is in excess in the target is therefore
     * drained there and is gone afterwards — it never existed.
     */
    public static Handoff fluid(net.neoforged.neoforge.fluids.capability.IFluidHandler in,
                                net.neoforged.neoforge.fluids.capability.IFluidHandler out,
                                net.neoforged.neoforge.fluids.FluidStack wanted) {
        var action = net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
        var probe = net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;
        net.neoforged.neoforge.fluids.FluidStack simulated = in.drain(wanted, probe);
        if (simulated.isEmpty()) {
            return new Handoff(0, false, 0);
        }
        int accepted = out.fill(simulated, action);
        if (accepted <= 0) {
            return new Handoff(0, true, 0);
        }
        // What the transfer actually yields, not what the dry run
        // promised.
        int taken = in.drain(simulated.copyWithAmount(accepted), action).getAmount();
        if (taken >= accepted) {
            return new Handoff(taken, false, 0);
        }
        int surplus = accepted - taken;
        int pulled = out.drain(simulated.copyWithAmount(surplus), action).getAmount();
        return new Handoff(taken, false, surplus - pulled);
    }

    /** Inserts across all slots and returns what did not fit. */
    public static ItemStack insertInto(IItemHandler handler, ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !rest.isEmpty(); slot++) {
            rest = handler.insertItem(slot, rest, false);
        }
        return rest;
    }

    /**
     * Fetches back what was just inserted and not covered.
     *
     * <p>The way back for the one case in which goods would otherwise be
     * created: the target has already received, and the source then gave up
     * less. What comes out here goes <b>nowhere</b> — it never existed, and
     * putting it down somewhere would be duplication by a detour.
     *
     * <p>It need not succeed. An input slot gives nothing out, and then the
     * remainder stays put; the caller learns of it from the return value and
     * does not count it as moved. That is the lesser half of the evil: an
     * amount that sits in the device once too many, instead of one that grows
     * back every tick.
     *
     * @return how much could no longer be fetched out
     */
    public static long pullBack(IItemHandler handler, ItemKey item, long amount) {
        long left = amount;
        for (int slot = 0; slot < handler.getSlots() && left > 0; slot++) {
            // The whole item, not just the id — otherwise the enchanted
            // pickaxe would come back instead of the plain one that just
            // went in.
            if (!item.equals(ItemKey.of(handler.getStackInSlot(slot)))) {
                continue;
            }
            left -= handler.extractItem(slot, (int) Math.min(left, Integer.MAX_VALUE),
                    false).getCount();
        }
        return left;
    }
}
