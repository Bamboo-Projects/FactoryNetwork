package dev.devpanda.factorynetwork.storage;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluid;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wie der Inhalt einer Zelle im Gegenstand steht.
 *
 * <p>Gegenstände und Flüssigkeiten liegen gleich: eine Liste aus Kennung und
 * Menge. Nur die Registry und die Namen der Felder unterscheiden sich.
 * <b>Deshalb steht das Lesen und Schreiben ein einziges Mal da</b> — zwei
 * Fassungen wären zwei Orte, an denen ein Bestand verlorengehen kann, und die
 * eine bekäme irgendwann eine Verbesserung, die der anderen fehlt.
 *
 * @param registry woher die Kennungen kommen
 * @param nbtKey   unter welchem Namen die Liste im Gegenstand steht
 * @param idKey    wie das Feld mit der Kennung heißt
 * @param amountKey wie das Feld mit der Menge heißt
 */
public record CellFormat<T>(Registry<T> registry, String nbtKey, String idKey, String amountKey) {

    /** Gegenstände. Die Namen sind die von früher — alte Zellen bleiben lesbar. */
    public static final CellFormat<Item> ITEMS =
            new CellFormat<>(BuiltInRegistries.ITEM, "Cell", "Item", "Count");

    /** Flüssigkeiten, in Millibucket. */
    public static final CellFormat<Fluid> FLUIDS =
            new CellFormat<>(BuiltInRegistries.FLUID, "FluidCell", "Fluid", "Amount");

    public Map<T, Long> read(ItemStack cell) {
        CustomData data = cell.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null) {
            return Map.of();
        }
        CompoundTag tag = data.copyTag();
        ListTag entries = tag.getList(nbtKey, Tag.TAG_COMPOUND);
        Map<T, Long> contents = new LinkedHashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString(idKey));
            // Ist die Mod aus dem Pack, ist der Posten weg. Ein Lagerbestand
            // darf das still hinnehmen — hier wie beim Netzspeicher.
            if (id == null || !registry.containsKey(id)) {
                continue;
            }
            long count = entry.getLong(amountKey);
            if (count > 0) {
                contents.put(registry.get(id), count);
            }
        }
        return contents;
    }

    public void write(ItemStack cell, Map<T, Long> contents) {
        ListTag entries = new ListTag();
        contents.forEach((key, count) -> {
            if (count <= 0) {
                return;
            }
            CompoundTag entry = new CompoundTag();
            entry.putString(idKey, registry.getKey(key).toString());
            entry.putLong(amountKey, count);
            entries.add(entry);
        });
        CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, cell,
                tag -> tag.put(nbtKey, entries));
    }

    /** Wie viel insgesamt darin liegt. */
    public static long total(Map<?, Long> contents) {
        return contents.values().stream().mapToLong(Long::longValue).sum();
    }
}
