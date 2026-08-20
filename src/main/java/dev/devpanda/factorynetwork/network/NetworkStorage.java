package dev.devpanda.factorynetwork.network;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Der Massenspeicher des Netzwerks.
 *
 * <p><b>Schlüsselbasiert, nicht slotbasiert.</b> Das ist die eine Lehre aus
 * dem Vorprojekt, die hier von Anfang an eingebaut ist: Ein Bestand, der als
 * Liste von Slots modelliert wird, macht jede Suche linear und jede
 * Filteroperation quadratisch. Die Messung in
 * {@code docs/referenz-messung-speicherzugriff.md} zeigt, was das bei
 * zehntausend Arten kostet — 67 Millisekunden für einen einzigen Durchlauf,
 * bei einem Tickbudget von 50.
 *
 * <p>Deshalb steht hier eine Abbildung von Art auf Menge. Nachschlagen kostet
 * konstante Zeit, unabhängig davon, wie viel im Netz liegt.
 */
public final class NetworkStorage {

    private static final String KEY_ENTRIES = "Entries";
    private static final String KEY_ITEM = "Item";
    private static final String KEY_COUNT = "Count";

    private final Map<Item, Long> amounts = new LinkedHashMap<>();
    /** Wird bei jeder Änderung gerufen — der Controller schickt dann gebündelt. */
    private Runnable onChange = () -> { };

    public void setChangeListener(Runnable listener) {
        this.onChange = listener == null ? () -> { } : listener;
    }

    /** Legt ab und liefert, was nicht hineinpasste. Zurzeit passt alles. */
    public long insert(Item item, long count) {
        if (count <= 0) {
            return 0;
        }
        amounts.merge(item, count, Long::sum);
        onChange.run();
        return 0;
    }

    public long insert(ItemStack stack) {
        return insert(stack.getItem(), stack.getCount());
    }

    /** Entnimmt höchstens {@code count} und liefert, wie viel es wurde. */
    public long extract(Item item, long count) {
        Long available = amounts.get(item);
        if (available == null || count <= 0) {
            return 0;
        }
        long taken = Math.min(available, count);
        if (taken >= available) {
            amounts.remove(item);
        } else {
            amounts.put(item, available - taken);
        }
        onChange.run();
        return taken;
    }

    /** Wie viel von einer Art da ist. Konstante Zeit — darauf kommt es an. */
    public long count(Item item) {
        return amounts.getOrDefault(item, 0L);
    }

    public Map<Item, Long> contents() {
        return Map.copyOf(amounts);
    }

    public int distinctTypes() {
        return amounts.size();
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        amounts.forEach((item, count) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString(KEY_ITEM, BuiltInRegistries.ITEM.getKey(item).toString());
            entry.putLong(KEY_COUNT, count);
            entries.add(entry);
        });
        tag.put(KEY_ENTRIES, entries);
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        amounts.clear();
        ListTag entries = tag.getList(KEY_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString(KEY_ITEM));
            // Wurde eine Mod aus dem Pack genommen, gibt es ihre Gegenstände
            // nicht mehr. Die Registry liefert dafür Luft — solche Einträge
            // würden sich still ansammeln. In AllTheMods-Packs kommen und
            // gehen Mods ständig, das ist der Normalfall und kein Sonderfall.
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(id);
            long count = entry.getLong(KEY_COUNT);
            if (count > 0) {
                amounts.put(item, count);
            }
        }
    }
}
