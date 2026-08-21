package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.block.entity.DriveBlockEntity;
import dev.devpanda.factorynetwork.storage.CellInventory;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Der Flüssigkeitsspeicher des Netzwerks.
 *
 * <p>Dieselbe Bauart wie beim Gegenstandsspeicher, aus demselben Grund: eine
 * Abbildung von Sorte auf Menge, kein Verbund einzelner Tanks. Wer in einem
 * großen Pack mit zwei Dutzend Flüssigkeiten arbeitet, sucht sonst bei jedem
 * Zugriff durch alle.
 *
 * <p><b>Der Bestand liegt in Flüssigkeitszellen</b>, wie die Gegenstände in
 * ihren. Vorher lagerte das Netz Flüssigkeiten unbegrenzt im Controller —
 * eine Ungleichheit, die niemand erklären kann: Für Eisen brauchte man ein
 * Laufwerk, für Lava nicht.
 *
 * <p>Gerechnet wird in Millibucket, wie überall in NeoForge — ein Eimer sind
 * 1000. Die Sprache schreibt {@code 1000 fluid:water}, und damit steht dieselbe
 * Zahl im Programm wie in jeder anderen Mod.
 */
public final class NetworkFluids {

    private final List<DriveBlockEntity> drives = new ArrayList<>();
    private Runnable onChange = () -> { };

    /** Der Bestand aller Zellen an einer Stelle — siehe {@link NetworkStorage}. */
    private final Map<Fluid, Long> index = new LinkedHashMap<>();
    private boolean indexValid;
    private long[] seenRevisions = new long[0];

    public void setChangeListener(Runnable listener) {
        this.onChange = listener == null ? () -> { } : listener;
    }

    /** Welche Laufwerke im Netz hängen. Setzt der Controller beim Neuaufbau. */
    public void setDrives(List<DriveBlockEntity> found) {
        drives.clear();
        drives.addAll(found);
        indexValid = false;
    }

    public boolean hasDrives() {
        return !drives.isEmpty();
    }

    private List<CellInventory<Fluid>> cells() {
        List<CellInventory<Fluid>> all = new ArrayList<>();
        for (DriveBlockEntity drive : drives) {
            all.addAll(drive.fluidInventories());
        }
        return all;
    }

    private Map<Fluid, Long> index() {
        if (!indexValid || drivesChanged()) {
            rebuildIndex();
        }
        return index;
    }

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
        for (CellInventory<Fluid> cell : cells()) {
            cell.contentsView().forEach((fluid, amount) -> index.merge(fluid, amount, Long::sum));
        }
        seenRevisions = new long[drives.size()];
        for (int i = 0; i < seenRevisions.length; i++) {
            seenRevisions[i] = drives.get(i).revision();
        }
        indexValid = true;
    }

    /**
     * Wie viel von dieser Sorte noch hineinginge.
     *
     * <p>Gebraucht, bevor ein Tank geleert wird: Was der Speicher nicht nimmt,
     * darf gar nicht erst aus dem Tank gezogen werden. Bei Gegenständen kann
     * man den Rest zurücklegen, bei Flüssigkeiten nimmt der Tank ihn
     * vielleicht nicht wieder an — und dann wäre er weg.
     */
    public long room(Fluid fluid, long wanted) {
        if (wanted <= 0 || fluid == Fluids.EMPTY) {
            return 0;
        }
        long free = 0;
        for (CellInventory<Fluid> cell : cells()) {
            free += cell.room(fluid);
            if (free >= wanted) {
                return wanted;
            }
        }
        return free;
    }

    /**
     * Legt ab und liefert, was nicht hineinpasste.
     *
     * <p>Erst in Zellen, die diese Sorte schon führen — sonst zersplittert ein
     * Bestand über alle Zellen und belegt überall einen Sortenplatz.
     */
    public long insert(Fluid fluid, long amount) {
        if (amount <= 0 || fluid == Fluids.EMPTY) {
            return 0;
        }
        Map<Fluid, Long> stock = index();
        long left = amount;
        List<CellInventory<Fluid>> cells = cells();
        for (CellInventory<Fluid> cell : cells) {
            if (left <= 0) {
                break;
            }
            if (cell.count(fluid) > 0) {
                left -= cell.insert(fluid, left);
            }
        }
        for (CellInventory<Fluid> cell : cells) {
            if (left <= 0) {
                break;
            }
            left -= cell.insert(fluid, left);
        }
        if (left < amount) {
            stock.merge(fluid, amount - left, Long::sum);
            markChanged();
        }
        return left;
    }

    /** Nimmt heraus und liefert, wie viel es wurde. */
    public long extract(Fluid fluid, long amount) {
        if (amount <= 0) {
            return 0;
        }
        Map<Fluid, Long> stock = index();
        long taken = 0;
        for (CellInventory<Fluid> cell : cells()) {
            if (taken >= amount) {
                break;
            }
            taken += cell.extract(fluid, amount - taken);
        }
        if (taken > 0) {
            long rest = stock.getOrDefault(fluid, 0L) - taken;
            if (rest > 0) {
                stock.put(fluid, rest);
            } else {
                stock.remove(fluid);
            }
            markChanged();
        }
        return taken;
    }

    public long count(Fluid fluid) {
        return index().getOrDefault(fluid, 0L);
    }

    /** Der gesamte Bestand. Eine Kopie — siehe {@link NetworkStorage#contents}. */
    public Map<Fluid, Long> contents() {
        return new LinkedHashMap<>(index());
    }

    /** Wie viele Sortenplätze insgesamt frei sind — für die Anzeige. */
    public int freeTypes() {
        int free = 0;
        for (CellInventory<Fluid> cell : cells()) {
            free += cell.freeTypes();
        }
        return free;
    }

    public void clear() {
        Map<Fluid, Long> stock = index();
        for (CellInventory<Fluid> cell : cells()) {
            cell.clear();
        }
        stock.clear();
        markChanged();
    }

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
