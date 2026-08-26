package dev.devpanda.factorynetwork.storage;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
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
 *
 * <p>Der Typ steht offen, weil Flüssigkeiten dieselbe Rechnung brauchen. Was
 * sich unterscheidet, ist nur die Registry im {@link CellFormat} und die
 * Größe — nicht eine einzige Zeile dieser Rechnung.
 *
 * @param <T> Gegenstand oder Flüssigkeit
 */
public final class CellInventory<T> implements CellView {

    private final ItemStack cell;
    private final CellSize size;
    private final CellFormat<T> format;
    private final Map<T, Long> contents;

    private CellInventory(ItemStack cell, CellSize size, CellFormat<T> format) {
        this.cell = cell;
        this.size = size;
        this.format = format;
        this.contents = new LinkedHashMap<>(format.read(cell));
    }

    /**
     * Eine Zelle beliebiger Art.
     *
     * <p>Offen, weil es eine dritte Art gibt, die dieser Kern nicht kennen
     * darf: Chemikalien gehören Mekanism. Wer sie öffnet, bringt Größe und
     * Format selbst mit — die Rechnung mit Sorten und Mengen bleibt dieselbe.
     */
    public static <T> CellInventory<T> of(ItemStack cell, CellSize size,
                                          CellFormat<T> format) {
        return new CellInventory<>(cell, size, format);
    }

    /** Eine Gegenstandszelle. Ungültig, wenn dort keine steckt. */
    public static CellInventory<Item> ofItems(ItemStack cell) {
        return new CellInventory<>(cell, StorageCellItem.tierOf(cell), CellFormat.ITEMS);
    }

    /** Eine Flüssigkeitszelle. Ungültig, wenn dort keine steckt. */
    public static CellInventory<net.minecraft.world.level.material.Fluid> ofFluids(
            ItemStack cell) {
        return new CellInventory<>(cell, FluidCellItem.tierOf(cell), CellFormat.FLUIDS);
    }

    /** Der Gegenstand, zu dem diese Sicht gehört. */
    @Override
    public ItemStack stack() {
        return cell;
    }

    @Override
    public boolean isValid() {
        return size != null;
    }

    public Map<T, Long> contents() {
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
    public Map<T, Long> contentsView() {
        return Collections.unmodifiableMap(contents);
    }

    public long count(T key) {
        return contents.getOrDefault(key, 0L);
    }

    public int usedTypes() {
        return contents.size();
    }

    public long usedAmount() {
        return CellFormat.total(contents);
    }

    /** Wie viel von dieser Art noch hineinginge. */
    public long room(T key) {
        if (!isValid()) {
            return 0;
        }
        boolean known = contents.containsKey(key);
        if (!known && contents.size() >= size.types()) {
            return 0;
        }
        return Math.max(0, size.amount() - usedAmount());
    }

    /** Legt ab und liefert, wie viel wirklich hineinging. */
    public long insert(T key, long count) {
        long room = Math.min(count, room(key));
        if (room <= 0) {
            return 0;
        }
        contents.merge(key, room, Long::sum);
        return room;
    }

    /** Nimmt heraus und liefert, wie viel es wurde. */
    public long extract(T key, long count) {
        Long available = contents.get(key);
        if (available == null || count <= 0) {
            return 0;
        }
        long taken = Math.min(available, count);
        if (taken >= available) {
            contents.remove(key);
        } else {
            contents.put(key, available - taken);
        }
        return taken;
    }

    /** Wie viele Artenplätze noch frei sind. */
    public int freeTypes() {
        return isValid() ? Math.max(0, size.types() - contents.size()) : 0;
    }

    public void clear() {
        contents.clear();
    }

    /** Schreibt zurück in den Gegenstand. Ohne das war alles nur gedacht. */
    public void flush() {
        format.write(cell, contents);
    }
}
