package dev.devpanda.factorynetwork.network;

import dev.devpanda.factorynetwork.block.entity.DriveBlockEntity;

import java.util.List;
import java.util.Map;

/**
 * Die Sicht des Netzes auf einen Bestand.
 *
 * <p>Gegenstände, Flüssigkeiten und Chemikalien liegen alle in Zellen in
 * Laufwerken, und die Sicht darauf war dreimal dieselbe Klasse mit anderen
 * Typen: ein Index über allen Zellen, zwei Durchläufe beim Ablegen, ein
 * Vergleich der Laufwerksstände beim Nachsehen. Hier stehen die Fragen
 * einmal; die Antworten stehen in {@link NetworkStorage},
 * {@link NetworkFluids} und im Chemikalienspeicher unter
 * {@code compat/mekanism}.
 *
 * <p><b>Der Schlüssel ist ein {@code Object}</b>, wie im Wertemodell: ein
 * {@code Item}, ein {@code Fluid} oder — bei einer Chemikalie — die Kennung
 * als Text. Ein gemeinsamer Obertyp gäbe es nur, wenn alle drei aus derselben
 * Hand kämen, und ein {@link String} kommt aus keiner. Welche Form zu welcher
 * Art gehört, sagt {@code ResourceKind.type()}; wer eine falsche hereingibt,
 * bekommt eine {@link ClassCastException} und keine stille Null.
 *
 * <p><b>Warum eine Schnittstelle und nicht nur eine gemeinsame Oberklasse:</b>
 * Der Chemikalienspeicher fasst Mekanism-Typen an. Eine Klasse, die das im
 * Kern täte, ließe sich in einem Pack ohne Mekanism nicht laden, und mit ihr
 * fiele der ganze Controller. Deshalb steht hier die Frage und die Antwort
 * dort — und deshalb gibt es {@link #NONE}.
 */
public interface ResourceStore {

    /** Wie viel davon im Netz liegt. Stück bei Gegenständen, sonst Millibucket. */
    long count(Object key);

    /**
     * Wie viel davon noch hineinginge.
     *
     * <p>Gebraucht, <b>bevor</b> ein Behälter geleert wird: Was der Speicher
     * nicht nimmt, darf gar nicht erst herauskommen. Bei Gegenständen kann man
     * den Rest zurücklegen; ein Gas, das draußen ist und dessen Behälter es
     * inzwischen nicht mehr annimmt, wäre weg.
     */
    long room(Object key, long wanted);

    /** Lagert ein und meldet, was <b>nicht</b> hineinpasste. */
    long insert(Object key, long amount);

    /** Entnimmt und meldet, wie viel wirklich kam. */
    long extract(Object key, long amount);

    /**
     * Der ganze Bestand.
     *
     * <p>Eine Kopie und keine Sicht: Der Bestand wird oft durchlaufen, während
     * nebenher etwas verschoben wird. Die Schlüssel haben die Form, die zur
     * Art gehört — deshalb steht hier {@code ?} und nicht {@code Object}: Wer
     * die Art kennt, darf die Karte typisiert entgegennehmen.
     */
    Map<?, Long> contents();

    /** Welche Laufwerke im Netz hängen. Setzt der Controller beim Neuaufbau. */
    void setDrives(List<DriveBlockEntity> drives);

    /** Ob überhaupt Platz da ist. Ohne Laufwerk lagert ein Netz nichts. */
    boolean hasDrives();

    /** Wird gerufen, wenn sich etwas geändert hat. */
    void setChangeListener(Runnable listener);

    /**
     * Ein Speicher, den es nicht gibt.
     *
     * <p>Er nimmt nichts an, gibt nichts her und hat keine Laufwerke. Das ist
     * keine Notlösung, sondern die Wahrheit über ein Pack ohne die Mod, die
     * diese Ressourcenart mitbringt.
     */
    ResourceStore NONE = new ResourceStore() {

        @Override
        public long count(Object key) {
            return 0;
        }

        @Override
        public long room(Object key, long wanted) {
            return 0;
        }

        @Override
        public long insert(Object key, long amount) {
            return amount;
        }

        @Override
        public long extract(Object key, long amount) {
            return 0;
        }

        @Override
        public Map<?, Long> contents() {
            return Map.of();
        }

        @Override
        public void setDrives(List<DriveBlockEntity> drives) {
        }

        @Override
        public boolean hasDrives() {
            return false;
        }

        @Override
        public void setChangeListener(Runnable listener) {
        }
    };
}
