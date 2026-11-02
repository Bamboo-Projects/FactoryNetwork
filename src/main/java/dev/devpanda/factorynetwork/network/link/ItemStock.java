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

    /** Puts in up to {@code amount}; returns how much went in. */
    long insert(ItemKey key, long amount, boolean simulate);

    /** Takes out up to {@code amount}; returns how much came out. */
    long extract(ItemKey key, long amount, boolean simulate);
}
