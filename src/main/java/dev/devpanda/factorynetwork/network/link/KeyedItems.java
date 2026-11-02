package dev.devpanda.factorynetwork.network.link;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * A handler that can narrow itself to the sorts asked for.
 *
 * <p>The transfer code walks slots. Before it does, it calls
 * {@link #narrow}: a chest stays as it is, a storage link becomes a view
 * holding only the matching sorts, so the walk is short however large the
 * system behind it.
 */
@SuppressWarnings("removal")
public interface KeyedItems {

    /** The slots of the matching sorts; all sorts for an empty list. */
    IItemHandler view(List<Item> items);

    static IItemHandler narrow(IItemHandler handler, List<Item> items) {
        return handler instanceof KeyedItems keyed ? keyed.view(items) : handler;
    }
}
