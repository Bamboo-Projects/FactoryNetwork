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
 * Wie der Inhalt einer Zelle im Gegenstand steht.
 *
 * <p>Gegenstände und Flüssigkeiten liegen gleich: eine Liste aus Kennung und
 * Menge. <b>Deshalb steht das Lesen und Schreiben ein einziges Mal da</b> —
 * zwei Fassungen wären zwei Orte, an denen ein Bestand verlorengehen kann,
 * und die eine bekäme irgendwann eine Verbesserung, die der anderen fehlt.
 *
 * <p><b>Was sie unterscheidet, ist der Eintrag.</b> Eine Flüssigkeit ist mit
 * ihrer Kennung vollständig beschrieben; ein Gegenstand nicht — er kann einen
 * Namen tragen, verzaubert oder halb verbraucht sein. Deshalb steht hier
 * keine Registry mehr, sondern ein Paar aus Lesen und Schreiben.
 *
 * @param nbtKey    unter welchem Namen die Liste im Gegenstand steht
 * @param amountKey wie das Feld mit der Menge heißt — verschieden je Sorte,
 *                  und deshalb hier: Eine Flüssigkeitszelle schreibt seit
 *                  jeher {@code Amount}, eine Gegenstandszelle {@code Count}.
 *                  Wer das vereinheitlicht, macht jeden bestehenden Bestand
 *                  unlesbar.
 * @param entry     wie ein einzelner Posten gelesen und geschrieben wird
 */
public record CellFormat<T>(String nbtKey, String amountKey, Entry<T> entry) {

    /** Wie ein Posten in der Liste steht. */
    public interface Entry<T> {

        /** Was hier steht — oder {@code null}, wenn es das nicht mehr gibt. */
        @Nullable T read(CompoundTag tag, HolderLookup.Provider registries);

        void write(CompoundTag tag, T key, HolderLookup.Provider registries);
    }

    /**
     * Gegenstände.
     *
     * <p>Die Namen der Felder sind die von früher, und {@code components} ist
     * <b>optional</b> — eine Zelle aus der Zeit vor dem 28.08. hat es nicht,
     * und ein Posten ohne dieses Feld ist ein Gegenstand ohne eigene Daten.
     * Alte Zellen bleiben damit lesbar, ohne dass irgendwo ein
     * Migrationslauf nötig wäre.
     */
    public static final CellFormat<ItemKey> ITEMS =
            new CellFormat<>("Cell", "Count", new Entry<>() {

                @Override
                public ItemKey read(CompoundTag tag, HolderLookup.Provider registries) {
                    ResourceLocation id = ResourceLocation.tryParse(tag.getString("Item"));
                    // Ist die Mod aus dem Pack, ist der Posten weg. Ein
                    // Lagerbestand darf das still hinnehmen.
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
                            // Lässt sich der Zusatz nicht lesen — eine Mod
                            // ist weg, ein Format hat sich geändert —, bleibt
                            // der nackte Gegenstand. Besser als ein Posten,
                            // der ganz verschwindet.
                            .orElseGet(() -> ItemKey.bare(BuiltInRegistries.ITEM.get(id)));
                }

                @Override
                public void write(CompoundTag tag, ItemKey key, HolderLookup.Provider registries) {
                    tag.putString("Item",
                            BuiltInRegistries.ITEM.getKey(key.item()).toString());
                    if (key.isBare()) {
                        // Kein leeres Feld schreiben: Eine Zelle voll nackter
                        // Gegenstände sieht danach aus wie vorher, und der
                        // Weg zurück auf eine ältere Fassung bleibt offen.
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

    /** Flüssigkeiten, in Millibucket. Sie tragen keine eigenen Daten. */
    public static final CellFormat<Fluid> FLUIDS =
            new CellFormat<>("FluidCell", "Amount",
                    registryEntry(BuiltInRegistries.FLUID, "Fluid"));

    /** Der Weg für alles, was mit seiner Kennung vollständig beschrieben ist. */
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
                // Zusammenzählen und nicht überschreiben: Zwei Posten können
                // nach einem Formatwechsel auf denselben Schlüssel fallen.
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
     * Wie viele Sorten und wie viel insgesamt — ohne die Posten zu deuten.
     *
     * <p><b>Für den Balken am Gegenstand.</b> Er läuft beim Zeichnen jedes
     * Inventarplatzes und hat weder Registrierungen zur Hand noch einen Grund
     * dafür: Ob ein Posten ein benanntes Schwert ist oder ein nacktes,
     * ändert an „drei von acht Sorten" nichts.
     *
     * @param types  wie viele Posten in der Liste stehen
     * @param amount was sie zusammen ergeben
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

    /** Wie viel insgesamt darin liegt. */
    public static long total(Map<?, Long> contents) {
        return contents.values().stream().mapToLong(Long::longValue).sum();
    }
}
