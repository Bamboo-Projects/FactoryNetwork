package dev.devpanda.factorynetwork.storage;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Was in einer Zelle liegt.
 *
 * <p><b>Der Inhalt steckt im Gegenstand, nicht im Laufwerk.</b> Das ist der
 * Grund, warum eine Zelle etwas wert ist: Man zieht sie heraus, trägt sie weg
 * und steckt sie anderswo hinein — der Bestand kommt mit. Läge er im
 * Laufwerk, wäre die Zelle nur ein Schlüssel und kein Speicher.
 *
 * <p>Gerechnet wird schlüsselbasiert, wie im ganzen Netz: eine Abbildung von
 * Art auf Menge. Eine Liste von Stapeln wäre bei sechzig Arten schon eine
 * lineare Suche je Zugriff, und davon gibt es Dutzende je Tick.
 */
public final class CellContents {

    private static final String KEY_ENTRIES = "Cell";
    private static final String KEY_ITEM = "Item";
    private static final String KEY_COUNT = "Count";

    private CellContents() {
    }

    public static Map<Item, Long> read(ItemStack cell) {
        CustomData data = cell.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null) {
            return Map.of();
        }
        CompoundTag tag = data.copyTag();
        ListTag entries = tag.getList(KEY_ENTRIES, Tag.TAG_COMPOUND);
        Map<Item, Long> contents = new LinkedHashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString(KEY_ITEM));
            // Ist die Mod aus dem Pack, ist der Posten weg. Ein Lagerbestand
            // darf das still hinnehmen — hier wie beim Netzspeicher.
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                continue;
            }
            long count = entry.getLong(KEY_COUNT);
            if (count > 0) {
                contents.put(BuiltInRegistries.ITEM.get(id), count);
            }
        }
        return contents;
    }

    public static void write(ItemStack cell, Map<Item, Long> contents) {
        ListTag entries = new ListTag();
        contents.forEach((item, count) -> {
            if (count <= 0) {
                return;
            }
            CompoundTag entry = new CompoundTag();
            entry.putString(KEY_ITEM, BuiltInRegistries.ITEM.getKey(item).toString());
            entry.putLong(KEY_COUNT, count);
            entries.add(entry);
        });
        CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, cell,
                tag -> tag.put(KEY_ENTRIES, entries));
    }

    /** Wie viel insgesamt darin liegt. */
    public static long total(Map<Item, Long> contents) {
        return contents.values().stream().mapToLong(Long::longValue).sum();
    }
}
