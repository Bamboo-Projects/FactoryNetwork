package dev.devpanda.factorynetwork.storage;

import dev.devpanda.factorynetwork.platform.Nbt;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

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
     * <p>An entry whose item or extra data cannot be read is not handed out;
     * the cell keeps it unchanged.
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
                    Identifier id = Identifier.tryParse(Nbt.getString(tag, "Item"));
                    // If the mod has left the pack, the entry cannot be read
                    // right now. The cell keeps it; see Loaded.
                    if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                        return null;
                    }
                    if (!tag.contains("components")) {
                        return ItemKey.bare(BuiltInRegistries.ITEM.getValue(id));
                    }
                    return net.minecraft.core.component.DataComponentPatch.CODEC
                            .parse(registries.createSerializationContext(
                                    net.minecraft.nbt.NbtOps.INSTANCE), tag.get("components"))
                            .result()
                            .map(patch -> ItemKey.of(BuiltInRegistries.ITEM.getValue(id), patch))
                            // If the extra data cannot be read — a mod is
                            // gone, a format has changed —, the entry counts
                            // as unreadable and stays in the cell as it is.
                            // Handing out the bare item instead would turn an
                            // enchanted tool into a plain one for good on the
                            // next write.
                            .orElse(null);
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

    /**
     * Thaumaturge essentia, by aspect identifier. Strings and not aspects: the
     * cell is read and written without the mod, and an aspect a pack no
     * longer has is still a name worth keeping.
     */
    public static final CellFormat<String> ESSENTIA =
            new CellFormat<>("EssentiaCell", "Amount", new Entry<>() {

                @Override
                public String read(CompoundTag tag, HolderLookup.Provider registries) {
                    String id = Nbt.getString(tag, "Aspect");
                    return id.isEmpty() ? null : id;
                }

                @Override
                public void write(CompoundTag tag, String key, HolderLookup.Provider registries) {
                    tag.putString("Aspect", key);
                }
            });

    /** The path for everything fully described by its id. */
    private static <T> Entry<T> registryEntry(Registry<T> registry, String idKey) {
        return new Entry<>() {

            @Override
            public T read(CompoundTag tag, HolderLookup.Provider registries) {
                Identifier id = Identifier.tryParse(Nbt.getString(tag, idKey));
                return id == null || !registry.containsKey(id) ? null : registry.getValue(id);
            }

            @Override
            public void write(CompoundTag tag, T key, HolderLookup.Provider registries) {
                tag.putString(idKey, registry.getKey(key).toString());
            }
        };
    }

    /**
     * What a cell holds, split by whether this game can read it.
     *
     * <p><b>An unreadable entry is kept, not dropped and not simplified.</b>
     * A mod may be missing today and back tomorrow; an enchanted pickaxe whose
     * data cannot be parsed right now is still that pickaxe. Such entries stay
     * out of the network — nobody can move what cannot be named — but they
     * keep their place in the cell and are written back untouched.
     *
     * @param contents   what can be read and therefore used
     * @param unreadable the remaining entries exactly as they were stored
     */
    public record Loaded<T>(Map<T, Long> contents, java.util.List<CompoundTag> unreadable) {
    }

    public Map<T, Long> read(ItemStack cell, HolderLookup.Provider registries) {
        return load(cell, registries).contents();
    }

    /** The contents of a cell together with what could not be read. */
    public Loaded<T> load(ItemStack cell, HolderLookup.Provider registries) {
        return load(entriesOf(cell), registries);
    }

    /** The same on the stored list itself. */
    public Loaded<T> load(ListTag entries, HolderLookup.Provider registries) {
        return CellEntries.load(entries, amountKey, entry, registries);
    }

    /** How much the unreadable entries of a cell add up to. */
    public long amountOf(java.util.List<CompoundTag> unreadable) {
        return CellEntries.amountOf(unreadable, amountKey);
    }

    /**
     * Writes the contents back into the cell.
     *
     * <p><b>What the cell holds unreadably stays in it.</b> It is taken from
     * the cell at this very moment and not handed in by the caller: a caller
     * that forgets it would delete someone's items, and there are several
     * callers.
     */
    public void write(ItemStack cell, Map<T, Long> contents, HolderLookup.Provider registries) {
        ListTag entries = save(contents, load(cell, registries).unreadable(), registries);
        CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, cell,
                tag -> tag.put(nbtKey, entries));
    }

    /** The stored list for these contents, with the unreadable entries behind them. */
    public ListTag save(Map<T, Long> contents, java.util.List<CompoundTag> unreadable,
                        HolderLookup.Provider registries) {
        return CellEntries.save(contents, unreadable, amountKey, entry, registries);
    }

    private ListTag entriesOf(ItemStack cell) {
        CustomData data = cell.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return data == null ? new ListTag() : Nbt.getList(data.copyTag(), nbtKey, Tag.TAG_COMPOUND);
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
        ListTag entries = Nbt.getList(data.copyTag(), nbtKey, Tag.TAG_COMPOUND);
        long amount = 0;
        for (int i = 0; i < entries.size(); i++) {
            amount += Nbt.getLong(Nbt.compoundAt(entries, i), amountKey);
        }
        return new Summary(entries.size(), amount);
    }

    /** How much sits inside it in total. */
    public static long total(Map<?, Long> contents) {
        return contents.values().stream().mapToLong(Long::longValue).sum();
    }
}
