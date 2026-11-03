package dev.devpanda.factorynetwork.network.link;

import dev.devpanda.factorynetwork.storage.ItemKey;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.function.ObjLongConsumer;

/**
 * The items of a foreign storage system, asked by sort.
 *
 * <p>What a storage link offers in place of slots. An ME network with fifty
 * thousand sorts as slots would be searched slot by slot on every transfer;
 * asked by sort, the cost stays with what is moved.
 */
public interface ItemStock {

    /**
     * Hands over every sort whose item is one of {@code items}, with its
     * amount; every sort at all for an empty list.
     */
    void find(List<Item> items, ObjLongConsumer<ItemKey> into);

    /**
     * Like {@link #find}, but hands over each sort in the foreign system's
     * own form; {@link #keyOf} turns it into an {@link ItemKey}.
     *
     * <p>For the slot view: a filter on an item with thousands of variants
     * finds thousands of sorts, and a transfer usually reads one or two of
     * them. Building a key means copying a stack and hashing its data, so it
     * waits until a slot is actually looked at.
     */
    default void findRaw(List<Item> items, ObjLongConsumer<Object> into) {
        find(items, into::accept);
    }

    /** The key of a sort handed over by {@link #findRaw}. */
    default ItemKey keyOf(Object raw) {
        return (ItemKey) raw;
    }

    /** Puts in up to {@code amount}; returns how much went in. */
    long insert(ItemKey key, long amount, boolean simulate);

    /** Takes out up to {@code amount}; returns how much came out. */
    long extract(ItemKey key, long amount, boolean simulate);
}
