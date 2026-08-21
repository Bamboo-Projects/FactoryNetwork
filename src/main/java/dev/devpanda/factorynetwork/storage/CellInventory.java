package dev.devpanda.factorynetwork.storage;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Eine Zelle als Speicher, mit ihren beiden Grenzen.
 *
 * <p>Das Einlagern kennt zwei Fälle, und der Unterschied entscheidet über das
 * Spielgefühl: Eine <b>neue Art</b> braucht einen freien Artenplatz, eine
 * <b>schon vorhandene</b> nur noch Menge. Deshalb nimmt eine volle Zelle
 * weiterhin an, was schon darin liegt — sonst müsste man beim Aufräumen jede
 * Zelle einzeln im Blick behalten.
 */
public final class CellInventory {

    private final ItemStack cell;
    private final CellTier tier;
    private final Map<Item, Long> contents;

    public CellInventory(ItemStack cell) {
        this.cell = cell;
        this.tier = StorageCellItem.tierOf(cell);
        this.contents = new LinkedHashMap<>(CellContents.read(cell));
    }

    /** Der Gegenstand, zu dem diese Sicht gehört. */
    public ItemStack stack() {
        return cell;
    }

    public boolean isValid() {
        return tier != null;
    }

    public Map<Item, Long> contents() {
        return Map.copyOf(contents);
    }

    /**
     * Derselbe Bestand ohne Kopie.
     *
     * <p>Für den Netzindex, der über alle Zellen läuft: Bei zehn Laufwerken
     * mit je zehn Zellen ist eine Kopie je Zelle hundert Kopien für eine
     * Frage. Wer die Sicht behält, während sich die Zelle ändert, sieht die
     * Änderung — das ist hier gewollt und der Grund, warum es beide gibt.
     */
    public Map<Item, Long> contentsView() {
        return java.util.Collections.unmodifiableMap(contents);
    }

    public long count(Item item) {
        return contents.getOrDefault(item, 0L);
    }

    public int usedTypes() {
        return contents.size();
    }

    public long usedAmount() {
        return CellContents.total(contents);
    }

    /** Wie viel von dieser Art noch hineinginge. */
    public long room(Item item) {
        if (!isValid()) {
            return 0;
        }
        boolean known = contents.containsKey(item);
        if (!known && contents.size() >= tier.types()) {
            return 0;
        }
        return Math.max(0, tier.amount() - usedAmount());
    }

    /** Legt ab und liefert, wie viel wirklich hineinging. */
    public long insert(Item item, long count) {
        long room = Math.min(count, room(item));
        if (room <= 0) {
            return 0;
        }
        contents.merge(item, room, Long::sum);
        return room;
    }

    /** Nimmt heraus und liefert, wie viel es wurde. */
    public long extract(Item item, long count) {
        Long available = contents.get(item);
        if (available == null || count <= 0) {
            return 0;
        }
        long taken = Math.min(available, count);
        if (taken >= available) {
            contents.remove(item);
        } else {
            contents.put(item, available - taken);
        }
        return taken;
    }

    /** Wie viele Artenplätze noch frei sind. */
    public int freeTypes() {
        return isValid() ? Math.max(0, tier.types() - contents.size()) : 0;
    }

    public void clear() {
        contents.clear();
    }

    /** Schreibt zurück in den Gegenstand. Ohne das war alles nur gedacht. */
    public void flush() {
        CellContents.write(cell, contents);
    }
}
