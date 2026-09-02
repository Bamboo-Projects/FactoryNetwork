package dev.devpanda.factorynetwork.storage;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How a cell's contents are stored in the item.
 *
 * <p>Items and fluids sit the same way: a list of id and amount. <b>That is
 * why reading and writing live in a single place</b> — two versions would be
 * two places where stored contents can be lost, and one of them would
 * eventually gain an improvement the other lacks.
 *
 * <p><b>What sets them apart is the entry.</b> A fluid is fully described by
 * its id; an item is not — it may carry a name, be enchanted or half-used.
 * That is why there is no registry here any more, but a pair of read and
 * write.
 *
 * @param nbtKey    the name under which the list sits in the item
 * @param amountKey the name of the field holding the amount — different per
 *                  type, and therefore here: a fluid cell has always written
 *                  {@code Amount}, an item cell {@code Count}. Unifying that
 *                  makes every existing store of contents unreadable.
 * @param entry     how a single entry is read and written
 */
public record CellFormat<T>(String nbtKey, String amountKey, Entry<T> entry) {

    /** How an entry appears in the list. */
    public interface Entry<T> {

        /** What is stored here — or {@code null} if it no longer exists. */
        @Nullable T read(CompoundTag tag, HolderLookup.Provider registries);

        void write(CompoundTag tag, T key, HolderLookup.Provider registries);
    }

    /**
     * Items.
     *
     * <p>The field names are the ones from before, and {@code components} is
     * <b>optional</b> — a cell from before 28 Aug does not have it, and an
     * entry without this field is an item without its own data. Old cells
     * stay readable that way, without a migration pass being needed anywhere.
     */
    public static final CellFormat<ItemKey> ITEMS =
            new CellFormat<>("Cell", "Count", new Entry<>() {

                @Override
                public ItemKey read(CompoundTag tag, HolderLookup.Provider registries) {
                    ResourceLocation id = ResourceLocation.tryParse(tag.getString("Item"));
                    // If the mod has left the pack, the entry is gone. A
                    // stored inventory may accept that quietly.
                    if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                        return null;
                    }
                    if (!tag.contains("components")) {
                        return ItemKey.bare(BuiltInRegistries.ITEM.get(id));
                    }
                    return net.minecraft.core.component.DataComponentPatch.CODEC
                            .parse(registries.createSerializationContext(
                                    net.minecraft.nbt.NbtOps.INSTANCE), tag.get("components"))
                            .result()
                            .map(patch -> ItemKey.of(BuiltInRegistries.ITEM.get(id), patch))
                            // If the extra data cannot be read — a mod is
                            // gone, a format has changed —, the bare item
                            // remains. Better than an entry that vanishes
                            // entirely.
                            .orElseGet(() -> ItemKey.bare(BuiltInRegistries.ITEM.get(id)));
                }

                @Override
                public void write(CompoundTag tag, ItemKey key, HolderLookup.Provider registries) {
                    tag.putString("Item",
                            BuiltInRegistries.ITEM.getKey(key.item()).toString());
                    if (key.isBare()) {
                        // Do not write an empty field: a cell full of bare
                        // items looks the same as before afterwards, and the
                        // way back to an older version stays open.
                        return;
                    }
                    net.minecraft.core.component.DataComponentPatch.CODEC
                            .encodeStart(registries.createSerializationContext(
                                            net.minecraft.nbt.NbtOps.INSTANCE),
                                    key.components())
                            .result()
                            .ifPresent(written -> tag.put("components", written));
                }
            });

    /** Fluids, in millibuckets. They carry no data of their own. */
    public static final CellFormat<Fluid> FLUIDS =
            new CellFormat<>("FluidCell", "Amount",
                    registryEntry(BuiltInRegistries.FLUID, "Fluid"));

    /** The path for everything fully described by its id. */
    private static <T> Entry<T> registryEntry(Registry<T> registry, String idKey) {
        return new Entry<>() {

            @Override
            public T read(CompoundTag tag, HolderLookup.Provider registries) {
                ResourceLocation id = ResourceLocation.tryParse(tag.getString(idKey));
                return id == null || !registry.containsKey(id) ? null : registry.get(id);
            }

            @Override
            public void write(CompoundTag tag, T key, HolderLookup.Provider registries) {
                tag.putString(idKey, registry.getKey(key).toString());
            }
        };
    }

    public Map<T, Long> read(ItemStack cell, HolderLookup.Provider registries) {
        CustomData data = cell.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null) {
            return Map.of();
        }
        CompoundTag tag = data.copyTag();
        ListTag entries = tag.getList(nbtKey, Tag.TAG_COMPOUND);
        Map<T, Long> contents = new LinkedHashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag one = entries.getCompound(i);
            T key = entry.read(one, registries);
            long count = one.getLong(amountKey);
            if (key != null && count > 0) {
                // Add up rather than overwrite: two entries can fall onto
                // the same key after a format change.
                contents.merge(key, count, Long::sum);
            }
        }
        return contents;
    }

    public void write(ItemStack cell, Map<T, Long> contents, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        contents.forEach((key, count) -> {
            if (count <= 0) {
                return;
            }
            CompoundTag one = new CompoundTag();
            entry.write(one, key, registries);
            one.putLong(amountKey, count);
            entries.add(one);
        });
        CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, cell,
                tag -> tag.put(nbtKey, entries));
    }

    /**
     * How many types and how much in total — without interpreting the
     * entries.
     *
     * <p><b>For the bar on the item.</b> It runs when every inventory slot is
     * drawn and has neither registries at hand nor a reason for them: whether
     * an entry is a named sword or a bare one changes nothing about "three of
     * eight types".
     *
     * @param types  how many entries are in the list
     * @param amount what they add up to
     */
    public record Summary(int types, long amount) { }

    public Summary summarize(ItemStack cell) {
        CustomData data = cell.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null) {
            return new Summary(0, 0);
        }
        ListTag entries = data.copyTag().getList(nbtKey, Tag.TAG_COMPOUND);
        long amount = 0;
        for (int i = 0; i < entries.size(); i++) {
            amount += entries.getCompound(i).getLong(amountKey);
        }
        return new Summary(entries.size(), amount);
    }

    /** How much sits inside it in total. */
    public static long total(Map<?, Long> contents) {
        return contents.values().stream().mapToLong(Long::longValue).sum();
    }
}
