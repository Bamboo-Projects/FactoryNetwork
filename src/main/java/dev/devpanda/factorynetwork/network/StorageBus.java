package dev.devpanda.factorynetwork.network;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import dev.devpanda.factorynetwork.storage.ItemKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * A foreign inventory that counts toward the network store.
 *
 * <p>The storage bus, as AE2 has it — only here the program declares it and
 * not a block: {@code store kiste_1 { … }}. The reasoning is in
 * {@code speicherbus.md}.
 *
 * <p><b>Passed through and not mirrored.</b> A copy is wrong the moment
 * someone touches the chest — a player empties it, a hopper fills it, a
 * foreign machine drains it in the same tick. A job that reckons on a stale
 * copy would leave behind exactly the half stack of intermediates that
 * crafting explicitly avoids.
 *
 * <p><b>Read once per tick.</b> Passing through does not mean counting the
 * whole inventory on every question: a worker asks per tick, a display too,
 * and a job several times. The bus therefore reads when someone asks it to —
 * the controller does that per tick — and keeps the answer in between. It is
 * thus never older than one tick.
 *
 * <p>Access sits behind a {@link Supplier}: a connector can be removed, the
 * chest broken, the chunk unloaded. A fixed {@code IItemHandler} here would be
 * a pointer to something that no longer exists.
 */
public final class StorageBus {

    private final String device;
    private final long priority;
    private final Supplier<IItemHandler> access;

    /**
     * What may go in, or empty for everything.
     *
     * <p>Already resolved: the bus asks per deposit, and resolving a selection
     * against the registry on every deposit would be the most expensive way to
     * get the same answer. The controller resolves it on rebuild — at the
     * moment when the program may have changed anyway.
     */
    private final java.util.Set<ItemKey> allowed;

    /** What was in it at the last read. */
    private final Map<ItemKey, Long> contents = new LinkedHashMap<>();

    public StorageBus(String device, long priority, java.util.Collection<ItemKey> allowed,
                      Supplier<IItemHandler> access) {
        this.device = device;
        this.priority = priority;
        this.allowed = allowed == null ? java.util.Set.of() : java.util.Set.copyOf(allowed);
        this.access = access;
    }

    /**
     * Whether this kind may go in.
     *
     * <p>Without a filter everything may. <b>For taking out it does not
     * apply:</b> what is already in there belongs to the stock and is
     * reachable — to conceal it because it does not match the filter would be
     * a lie about something everyone can see, and a stock you cannot take
     * anything out of would be the worse half of that.
     */
    public boolean accepts(ItemKey item) {
        return allowed.isEmpty() || allowed.contains(item);
    }

    public String device() {
        return device;
    }

    /** Where things are stored first; the cells stand at zero. */
    public long priority() {
        return priority;
    }

    /** The access, or {@code null} if none is present right now. */
    public IItemHandler handler() {
        return access.get();
    }

    /** What was in it at the last read. */
    public Map<ItemKey, Long> contents() {
        return contents;
    }

    /**
     * Stores and returns what did not fit.
     *
     * <p>Through the machine's slots and not into a slot of its own: where
     * something belongs it knows itself — the same answer {@code move} relies
     * on too.
     *
     * <p>The remembered contents are carried along and not read afresh. A read
     * per deposit would be exactly what reading once per tick saves.
     */
    public long insert(ItemKey item, long count) {
        IItemHandler handler = access.get();
        if (handler == null || count <= 0 || !accepts(item)) {
            return count;
        }
        // The stack is built from the key and thus carries what makes up the
        // item. Before, a bare one arose here — an enchanted book arrived in
        // the chest without its enchantment.
        ItemStack rest = item.toStack(
                (int) Math.min(count, item.maxStackSize()));
        for (int slot = 0; slot < handler.getSlots() && !rest.isEmpty(); slot++) {
            rest = handler.insertItem(slot, rest, false);
        }
        long placed = count - rest.getCount();
        if (placed > 0) {
            contents.merge(item, placed, Long::sum);
        }
        return count - placed;
    }

    /**
     * How much of it would go in, without depositing anything.
     *
     * <p><b>A machine very much can try.</b> {@code insertItem} with
     * {@code simulate} is exactly that: the same question to the same slots,
     * only without consequences. That is why a storage bus can count along
     * when the network is asked how much still fits.
     *
     * <p>At most one stack, exactly as with depositing — otherwise the answer
     * would promise more than a single call delivers.
     */
    public long room(ItemKey item, long wanted) {
        IItemHandler handler = access.get();
        if (handler == null || wanted <= 0 || !accepts(item)) {
            return 0;
        }
        ItemStack rest = item.toStack(
                (int) Math.min(wanted, item.maxStackSize()));
        int offered = rest.getCount();
        for (int slot = 0; slot < handler.getSlots() && !rest.isEmpty(); slot++) {
            rest = handler.insertItem(slot, rest, true);
        }
        return offered - rest.getCount();
    }

    /**
     * Takes out and returns how much it came to.
     *
     * <p>Only from slots that carry this kind — and only as much as the
     * machine gives up. An input slot that hands nothing out is not an error,
     * but a machine that keeps its rules.
     */
    public long extract(ItemKey item, long count) {
        IItemHandler handler = access.get();
        if (handler == null || count <= 0) {
            return 0;
        }
        long taken = 0;
        for (int slot = 0; slot < handler.getSlots() && taken < count; slot++) {
            // Compare whole items, not just the identifier: otherwise a
            // request for a bare pickaxe would take the enchanted one out of
            // the chest.
            if (!item.equals(ItemKey.of(handler.getStackInSlot(slot)))) {
                continue;
            }
            taken += handler.extractItem(slot, (int) (count - taken), false).getCount();
        }
        if (taken > 0) {
            long left = contents.getOrDefault(item, 0L) - taken;
            if (left > 0) {
                contents.put(item, left);
            } else {
                contents.remove(item);
            }
        }
        return taken;
    }

    /**
     * Reads the inventory afresh.
     *
     * @return whether anything changed
     */
    public boolean refresh() {
        IItemHandler handler = access.get();
        Map<ItemKey, Long> found = new LinkedHashMap<>();
        if (handler != null) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    found.merge(ItemKey.of(stack), (long) stack.getCount(), Long::sum);
                }
            }
        }
        if (found.equals(contents)) {
            return false;
        }
        contents.clear();
        contents.putAll(found);
        return true;
    }
}
