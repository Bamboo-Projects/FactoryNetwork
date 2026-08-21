package dev.devpanda.factorynetwork.network;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Der Flüssigkeitsspeicher des Netzwerks.
 *
 * <p>Dieselbe Bauart wie beim Gegenstandsspeicher, aus demselben Grund: eine
 * Abbildung von Sorte auf Menge, kein Verbund einzelner Tanks. Wer in einem
 * großen Pack mit zwei Dutzend Flüssigkeiten arbeitet, sucht sonst bei jedem
 * Zugriff durch alle.
 *
 * <p>Gerechnet wird in Millibucket, wie überall in NeoForge — ein Eimer sind
 * 1000. Die Sprache schreibt {@code 1000 fluid:water}, und damit steht dieselbe
 * Zahl im Programm wie in jeder anderen Mod.
 */
public final class NetworkFluids {

    private static final String KEY_ENTRIES = "Entries";
    private static final String KEY_FLUID = "Fluid";
    private static final String KEY_AMOUNT = "Amount";

    private final Map<Fluid, Long> amounts = new LinkedHashMap<>();
    private Runnable onChange = () -> { };

    public void setChangeListener(Runnable listener) {
        this.onChange = listener == null ? () -> { } : listener;
    }

    /** Legt ab und liefert, was nicht hineinpasste. Zurzeit passt alles. */
    public long insert(Fluid fluid, long amount) {
        if (amount <= 0 || fluid == net.minecraft.world.level.material.Fluids.EMPTY) {
            return 0;
        }
        amounts.merge(fluid, amount, Long::sum);
        onChange.run();
        return 0;
    }

    /** Nimmt heraus und liefert, wie viel es wurde. */
    public long extract(Fluid fluid, long amount) {
        if (amount <= 0) {
            return 0;
        }
        Long available = amounts.get(fluid);
        if (available == null) {
            return 0;
        }
        long taken = Math.min(available, amount);
        if (taken >= available) {
            amounts.remove(fluid);
        } else {
            amounts.put(fluid, available - taken);
        }
        onChange.run();
        return taken;
    }

    public long count(Fluid fluid) {
        return amounts.getOrDefault(fluid, 0L);
    }

    public Map<Fluid, Long> contents() {
        return Map.copyOf(amounts);
    }

    public void clear() {
        amounts.clear();
        onChange.run();
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        amounts.forEach((fluid, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString(KEY_FLUID, BuiltInRegistries.FLUID.getKey(fluid).toString());
            entry.putLong(KEY_AMOUNT, amount);
            entries.add(entry);
        });
        tag.put(KEY_ENTRIES, entries);
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        amounts.clear();
        ListTag entries = tag.getList(KEY_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString(KEY_FLUID));
            // Wie beim Gegenstandsspeicher: Ist die Mod aus dem Pack, ist der
            // Posten weg. Ein Lagerbestand darf das still hinnehmen — anders
            // als eine Variable, mit der weitergerechnet wird.
            if (id == null || !BuiltInRegistries.FLUID.containsKey(id)) {
                continue;
            }
            long amount = entry.getLong(KEY_AMOUNT);
            if (amount > 0) {
                amounts.put(BuiltInRegistries.FLUID.get(id), amount);
            }
        }
    }
}
