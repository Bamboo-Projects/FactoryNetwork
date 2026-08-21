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
        long left = count;
        List<CellInventory> cells = cells();
        for (CellInventory cell : cells) {
            if (left <= 0) {
                break;
            }
            if (cell.count(item) > 0) {
                left -= cell.insert(item, left);
                cell.flush();
            }
        }
        for (CellInventory cell : cells) {
            if (left <= 0) {
                break;
            }
            long moved = cell.insert(item, left);
            if (moved > 0) {
                left -= moved;
                cell.flush();
            }
        }
        if (left < count) {
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
        long taken = 0;
        for (CellInventory cell : cells()) {
            if (taken >= count) {
                break;
            }
            long got = cell.extract(item, count - taken);
            if (got > 0) {
                taken += got;
                cell.flush();
            }
        }
        if (taken > 0) {
            markChanged();
        }
        return taken;
    }

    public long count(Item item) {
        long total = 0;
        for (CellInventory cell : cells()) {
            total += cell.count(item);
        }
        return total;
    }

    /** Der gesamte Bestand über alle Zellen. */
    public Map<Item, Long> contents() {
        Map<Item, Long> all = new LinkedHashMap<>();
        for (CellInventory cell : cells()) {
            cell.contents().forEach((item, count) -> all.merge(item, count, Long::sum));
        }
        return all;
    }

    public int distinctTypes() {
        return contents().size();
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
        for (CellInventory cell : cells()) {
            cell.clear();
            cell.flush();
        }
        markChanged();
    }

    private void markChanged() {
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
