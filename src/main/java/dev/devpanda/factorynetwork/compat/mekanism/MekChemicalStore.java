package dev.devpanda.factorynetwork.compat.mekanism;

import dev.devpanda.factorynetwork.block.entity.DriveBlockEntity;
import dev.devpanda.factorynetwork.network.ResourceStore;
import dev.devpanda.factorynetwork.storage.CellInventory;
import dev.devpanda.factorynetwork.storage.CellView;
import mekanism.api.chemical.Chemical;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Der Chemikalienspeicher des Netzes.
 *
 * <p>Dieselbe Rechnung wie bei Gegenständen und Flüssigkeiten: Der Bestand
 * liegt in den Zellen, diese Klasse ist die Sicht des Netzes darauf, und ein
 * Index davor beantwortet die Fragen, die oft kommen. Neu gezählt wird nur,
 * wenn jemand anders etwas getan hat — das meldet der Zählstand des Laufwerks.
 *
 * <p><b>Nach außen in Texten.</b> Die Schnittstelle {@link ResourceStore}
 * spricht über {@code "mekanism:hydrogen"}, nicht über {@code Chemical}. Das
 * ist der Preis dafür, dass der Controller sie halten kann, ohne Mekanism zu
 * kennen — und er ist klein: Zwischen Kennung und Chemikalie liegt eine
 * Registry-Suche, und die geschieht einmal je Frage, nicht je Zelle.
 */
final class MekChemicalStore implements ResourceStore {

    private final List<DriveBlockEntity> drives = new ArrayList<>();
    private Runnable onChange = () -> { };

    private final Map<Chemical, Long> index = new LinkedHashMap<>();
    private boolean indexValid;
    private long[] seenRevisions = new long[0];

    @Override
    public void setChangeListener(Runnable listener) {
        this.onChange = listener == null ? () -> { } : listener;
    }

    @Override
    public void setDrives(List<DriveBlockEntity> found) {
        drives.clear();
        drives.addAll(found);
        indexValid = false;
    }

    @Override
    public boolean hasDrives() {
        return !drives.isEmpty();
    }

    @SuppressWarnings("unchecked")
    private List<CellInventory<Chemical>> cells() {
        List<CellInventory<Chemical>> all = new ArrayList<>();
        for (DriveBlockEntity drive : drives) {
            for (CellView view : drive.chemicalCells(cell -> MekCells.open(cell,
                    drive.getLevel() == null
                            ? net.minecraft.core.RegistryAccess.EMPTY
                            : drive.getLevel().registryAccess()))) {
                // Die Fabrik oben baut nur Chemikalienzellen; was sie liefert,
                // ist per Bau eine — der Kern kann das nur nicht ausdrücken.
                all.add((CellInventory<Chemical>) view);
            }
        }
        return all;
    }

    private Map<Chemical, Long> index() {
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
        for (CellInventory<Chemical> cell : cells()) {
            cell.contentsView().forEach((chemical, amount) ->
                    index.merge(chemical, amount, Long::sum));
        }
        seenRevisions = new long[drives.size()];
        for (int i = 0; i < seenRevisions.length; i++) {
            seenRevisions[i] = drives.get(i).revision();
        }
        indexValid = true;
    }

    /**
     * Die Chemikalie hinter einer Kennung, oder {@code null}.
     *
     * <p>Der Schlüssel kommt als {@code Object} herein, weil die
     * Schnittstelle drei Arten bedient. Etwas anderes als ein Text kann hier
     * nicht ankommen — der Weg dorthin geht über {@code ResourceKind}, und
     * das Wertemodell prüft die Form schon beim Anlegen.
     */
    private Chemical chemical(Object key) {
        return key instanceof String id ? MekCells.chemical(id) : null;
    }

    @Override
    public long count(Object key) {
        Chemical chemical = chemical(key);
        return chemical == null ? 0 : index().getOrDefault(chemical, 0L);
    }

    @Override
    public Map<String, Long> contents() {
        Map<String, Long> found = new LinkedHashMap<>();
        index().forEach((chemical, amount) -> {
            if (amount > 0) {
                found.put(MekCells.idOf(chemical), amount);
            }
        });
        return found;
    }

    @Override
    public long room(Object key, long wanted) {
        Chemical chemical = chemical(key);
        if (chemical == null || wanted <= 0) {
            return 0;
        }
        long free = 0;
        for (CellInventory<Chemical> cell : cells()) {
            free += cell.room(chemical);
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
    @Override
    public long insert(Object key, long amount) {
        Chemical chemical = chemical(key);
        if (chemical == null || amount <= 0) {
            return amount;
        }
        index();
        long left = amount;
        List<CellInventory<Chemical>> cells = cells();
        for (CellInventory<Chemical> cell : cells) {
            if (left <= 0) {
                break;
            }
            if (cell.count(chemical) > 0) {
                left -= cell.insert(chemical, left);
            }
        }
        for (CellInventory<Chemical> cell : cells) {
            if (left <= 0) {
                break;
            }
            left -= cell.insert(chemical, left);
        }
        if (left < amount) {
            index.merge(chemical, amount - left, Long::sum);
            markChanged();
        }
        return left;
    }

    @Override
    public long extract(Object key, long amount) {
        Chemical chemical = chemical(key);
        if (chemical == null || amount <= 0) {
            return 0;
        }
        index();
        long taken = 0;
        for (CellInventory<Chemical> cell : cells()) {
            if (taken >= amount) {
                break;
            }
            taken += cell.extract(chemical, amount - taken);
        }
        if (taken > 0) {
            long left = index.getOrDefault(chemical, 0L) - taken;
            if (left <= 0) {
                index.remove(chemical);
            } else {
                index.put(chemical, left);
            }
            markChanged();
        }
        return taken;
    }

    private void markChanged() {
        drives.forEach(DriveBlockEntity::setChanged);
        onChange.run();
    }
}
