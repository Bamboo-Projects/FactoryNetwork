package dev.devpanda.factorynetwork.network;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import dev.devpanda.factorynetwork.block.entity.DriveBlockEntity;
import dev.devpanda.factorynetwork.storage.CellInventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Der Speicher des Netzwerks.
 *
 * <p><b>Schlüsselbasiert, nicht slotbasiert.</b> Das ist die eine Lehre aus
 * dem Vorprojekt: Ein Bestand, der als Liste von Slots modelliert wird, macht
 * jede Suche linear und jede Filteroperation quadratisch. Die Messung in
 * {@code docs/referenz-messung-speicherzugriff.md} zeigt, was das bei
 * zehntausend Arten kostet — 67 Millisekunden für einen einzigen Durchlauf,
 * bei einem Tickbudget von 50.
 *
 * <p><b>Der Bestand liegt in den Zellen, nicht hier.</b> Diese Klasse ist die
 * Sicht des Netzes darauf: Sie kennt die Laufwerke und verteilt zwischen
 * ihnen. Ein eigener Vorrat daneben wäre eine zweite Wahrheit, die
 * auseinanderläuft, sobald jemand eine Zelle herauszieht.
 */
public final class NetworkStorage {

    private final List<DriveBlockEntity> drives = new ArrayList<>();

    /**
     * Der Bestand aller Zellen an einer Stelle.
     *
     * <p><b>Ohne ihn zählt jede Frage alles neu.</b> Zehn Laufwerke mit je
     * zehn Zellen und vierundsechzig Arten sind sechstausend Einträge — und
     * gefragt wird oft: von jedem Worker, von jeder Bedingung, von jeder
     * Anzeige, mehrmals im selben Tick.
     *
     * <p>Eigene Ablagen und Entnahmen werden eingerechnet, nicht neu gezählt.
     * Neu gezählt wird nur, wenn jemand anders etwas getan hat — eine Zelle
     * herausgezogen etwa. Das meldet der Zählstand des Laufwerks.
     */
    private final Map<Item, Long> index = new LinkedHashMap<>();
    private boolean indexValid;
    /** Stand der Laufwerke, als der Index gebaut wurde. */
    private long[] seenRevisions = new long[0];
    /** Wird bei jeder Änderung gerufen — der Controller schickt dann gebündelt. */
    private Runnable onChange = () -> { };

    public void setChangeListener(Runnable listener) {
        this.onChange = listener == null ? () -> { } : listener;
    }

    /**
     * Welche Laufwerke im Netz hängen.
     *
     * <p>Setzt der Controller bei jedem Neuaufbau. Steht hier nichts, gibt es
     * keinen Platz — ein Netz ohne Laufwerk lagert nichts.
     */
    public void setDrives(List<DriveBlockEntity> found) {
        drives.clear();
        drives.addAll(found);
        indexValid = false;
    }

    /** Der Bestand, frisch genug. */
    private Map<Item, Long> index() {
        if (!indexValid || drivesChanged()) {
            rebuildIndex();
        }
        return index;
    }

    /**
     * Hat jemand anders an den Laufwerken gearbeitet?
     *
     * <p>Ein Vergleich von ein paar Zahlen. Die Alternative wäre, den Index
     * bei jeder Frage neu zu bauen — also genau das, was er einspart.
     */
    private boolean drivesChanged() {
        if (seenRevisions.length != drives.size()) {
            return true;
        }
        for (int i = 0; i < seenRevisions.length; i++) {
            if (seenRevisions[i] != drives.get(i).revision()) {
                return true;
            }
        }
        return false;
    }

    private void rebuildIndex() {
        index.clear();
        for (CellInventory cell : cells()) {
            cell.contentsView().forEach((item, count) -> index.merge(item, count, Long::sum));
        }
        seenRevisions = new long[drives.size()];
        for (int i = 0; i < seenRevisions.length; i++) {
            seenRevisions[i] = drives.get(i).revision();
        }
        indexValid = true;
    }

    public boolean hasDrives() {
        return !drives.isEmpty();
    }

    /** Alle Zellen aller Laufwerke, frisch gelesen. */
    private List<CellInventory> cells() {
        List<CellInventory> all = new ArrayList<>();
        for (DriveBlockEntity drive : drives) {
            all.addAll(drive.inventories());
        }
        return all;
    }

    /**
     * Legt ab und liefert, was nicht hineinpasste.
     *
     * <p>Erst in Zellen, die diese Art schon führen — sonst zersplittert ein
     * Bestand über alle Zellen und belegt überall einen Artenplatz.
     */
    public long insert(Item item, long count) {
        if (count <= 0) {
            return 0;
        }
        // Erst den Index auf Stand bringen, dann rechnen: Danach ist die
        // eigene Ablage die einzige Änderung, und die ist bekannt.
        Map<Item, Long> stock = index();
        long left = count;
        List<CellInventory> cells = cells();
        for (CellInventory cell : cells) {
            if (left <= 0) {
                break;
            }
            if (cell.count(item) > 0) {
                left -= cell.insert(item, left);
            }
        }
        for (CellInventory cell : cells) {
            if (left <= 0) {
                break;
            }
            left -= cell.insert(item, left);
        }
        if (left < count) {
            stock.merge(item, count - left, Long::sum);
            markChanged();
        }
        return left;
    }

    public long insert(ItemStack stack) {
        return stack.isEmpty() ? 0 : insert(stack.getItem(), stack.getCount());
    }

    /** Nimmt heraus und liefert, wie viel es wurde. */
    public long extract(Item item, long count) {
        if (count <= 0) {
            return 0;
        }
        Map<Item, Long> stock = index();
        long taken = 0;
        for (CellInventory cell : cells()) {
            if (taken >= count) {
                break;
            }
            taken += cell.extract(item, count - taken);
        }
        if (taken > 0) {
            long rest = stock.getOrDefault(item, 0L) - taken;
            if (rest > 0) {
                stock.put(item, rest);
            } else {
                stock.remove(item);
            }
            markChanged();
        }
        return taken;
    }

    public long count(Item item) {
        return index().getOrDefault(item, 0L);
    }

    /**
     * Der gesamte Bestand über alle Zellen.
     *
     * <p>Eine Kopie, keine Sicht: Der Bestand wird oft durchlaufen, während
     * nebenher etwas verschoben wird, und eine Sicht führte dort zu einer
     * ConcurrentModificationException an einer Stelle, die mit dem Verschieben
     * nichts zu tun hat.
     */
    public Map<Item, Long> contents() {
        return new LinkedHashMap<>(index());
    }

    public int distinctTypes() {
        return index().size();
    }

    /** Wie viele Artenplätze insgesamt frei sind — für die Anzeige. */
    public int freeTypes() {
        int free = 0;
        for (CellInventory cell : cells()) {
            free += cell.freeTypes();
        }
        return free;
    }

    public void clear() {
        Map<Item, Long> stock = index();
        for (CellInventory cell : cells()) {
            cell.clear();
        }
        stock.clear();
        markChanged();
    }

    /**
     * Meldet, dass sich etwas geändert hat.
     *
     * <p><b>Die Laufwerke müssen mit.</b> Der Bestand liegt in ihren Zellen,
     * und ohne diese Meldung weiß Minecraft nicht, dass der Chunk gesichert
     * werden muss — bei einem Laufwerk in einem anderen Chunk als der
     * Controller wäre der Bestand nach einem Neustart der von vorhin.
     */
    private void markChanged() {
        drives.forEach(DriveBlockEntity::setChanged);
        onChange.run();
    }

    /**
     * Speichern und Laden entfallen.
     *
     * <p>Der Bestand liegt in den Zellen, und die liegen in den Laufwerken —
     * beide speichern sich selbst. Ein zweiter Ort wäre eine zweite Wahrheit.
     */
    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        // Nichts zu tun.
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        // Nichts zu tun.
    }
}
